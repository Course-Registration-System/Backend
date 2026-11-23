package com.crs.backend.academic;

import com.crs.backend.academic.dto.ScheduleRequest;
import com.crs.backend.academic.dto.ScheduleResponse;
import com.crs.backend.academic.dto.SemesterRequest;
import com.crs.backend.academic.dto.SemesterResponse;
import com.crs.backend.common.BusinessException;
import com.crs.backend.common.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 학기·학사일정 관리(ACA-01 ①~④). 진급·졸업과 최대학점 산정(⑤)은 따로 둡니다. */
@Service
public class SemesterService {

    private final SemesterRepository semesterRepository;
    private final Clock clock;

    public SemesterService(SemesterRepository semesterRepository, Clock clock) {
        this.semesterRepository = semesterRepository;
        this.clock = clock;
    }

    public List<SemesterResponse> list() {
        return semesterRepository.findAll();
    }

    // ① 학기 등록. 현재 학기 지정은 changeCurrent 로 따로 한다
    @Transactional
    public SemesterResponse create(SemesterRequest request) {
        if (semesterRepository.exists(request.yr(), request.semType(), 0)) {
            throw new BusinessException(ErrorCode.DUPLICATE_SEMESTER);
        }
        int semNo = semesterRepository.insert(request.yr(), request.semType());
        return get(semNo);
    }

    /** 연도·학기구분 수정. 일정이 하나라도 시작된 학기는 장바구니·신청 이력이 있을 수 있어 막습니다. */
    @Transactional
    public SemesterResponse update(int semNo, SemesterRequest request) {
        lock(semNo);
        if (semesterRepository.anyScheduleStarted(semNo, now())) {
            throw new BusinessException(ErrorCode.SEMESTER_IN_USE);
        }
        if (semesterRepository.exists(request.yr(), request.semType(), semNo)) {
            throw new BusinessException(ErrorCode.DUPLICATE_SEMESTER);
        }
        semesterRepository.update(semNo, request.yr(), request.semType());
        return get(semNo);
    }

    @Transactional
    public SemesterResponse changeCurrent(int semNo) {
        get(semNo);
        semesterRepository.changeCurrent(semNo);
        return get(semNo);
    }

    public List<ScheduleResponse> schedules(int semNo) {
        get(semNo);
        return semesterRepository.findSchedules(semNo);
    }

    // ②~④ 일정 등록: 학기 행을 잠근 뒤 기간·시작 시각·겹침·순서를 검사하고 저장한다
    @Transactional
    public ScheduleResponse addSchedule(int semNo, ScheduleRequest request) {
        lock(semNo);
        if (!request.startDtm().isBefore(request.endDtm())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "시작일시는 종료일시보다 빨라야 합니다.");
        }
        // 시작일시가 지난 일정은 넣는 즉시 장바구니·신청 판정이 바뀌고, 시작된 일정은 지울 수도 없어서 막는다
        if (!request.startDtm().isAfter(now())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "시작일시가 지난 일정은 등록할 수 없습니다.");
        }
        for (ScheduleResponse other : semesterRepository.findSchedulesSharingYear(semNo, request.tgtYrLvl())) {
            checkAgainst(request, other);
        }
        checkSeasonalGrade(get(semNo), request);
        int schdNo = semesterRepository.insertSchedule(
                semNo, request.schdType(), request.tgtYrLvl(), request.startDtm(), request.endDtm());
        return semesterRepository.findSchedule(schdNo).orElseThrow();
    }

    /** 시작일시가 지나지 않은 일정만 지웁니다. */
    @Transactional
    public void deleteSchedule(int schdNo) {
        ScheduleResponse schedule = findSchedule(schdNo);
        lock(schedule.semNo());
        if (!now().isBefore(schedule.startDtm())) {
            throw new BusinessException(ErrorCode.SCHEDULE_STARTED);
        }
        semesterRepository.deleteSchedule(schdNo);
    }

    /**
     * 대상학년이 맞물리는 다른 일정과 비교합니다. 전 학년 일정은 모든 학년과 맞물립니다(D7).
     *
     * <p>같은 유형이면 기간이 겹치면 안 됩니다. 다른 유형이면 장바구니 → 수강신청 → 정정 → 성적입력 순서에서 앞 일정의 종료가 뒤 일정의
     * 시작보다 늦으면 안 됩니다.
     */
    private void checkAgainst(ScheduleRequest request, ScheduleResponse other) {
        int order = request.schdType().compareTo(other.schdType());
        if (order == 0) {
            if (request.startDtm().isBefore(other.endDtm()) && other.startDtm().isBefore(request.endDtm())) {
                throw new BusinessException(ErrorCode.SCHEDULE_OVERLAP, describe(other) + "과 기간이 겹칩니다.");
            }
            return;
        }
        if (order > 0 && request.startDtm().isBefore(other.endDtm())) {
            throw new BusinessException(ErrorCode.SCHEDULE_ORDER, describe(other) + "이 끝난 뒤에 시작해야 합니다.");
        }
        if (order < 0 && other.startDtm().isBefore(request.endDtm())) {
            throw new BusinessException(ErrorCode.SCHEDULE_ORDER, describe(other) + "이 시작하기 전에 끝나야 합니다.");
        }
    }

    /**
     * 계절학기 성적입력은 다음 정규학기 장바구니 시작 전에 끝나야 합니다(장바구니가 없으면 수강신청 시작). 정규학기 최대학점 산정이 계절학기
     * 성적 확정을 기다리기 때문입니다. 앞뒤 학기가 아직 없거나 일정이 없으면 검사하지 않습니다.
     */
    private void checkSeasonalGrade(SemesterResponse semester, ScheduleRequest request) {
        int targetOrd = MaxCreditService.order(semester);
        if (!semester.regular() && request.schdType() == SchdType.GRADE) {
            semesterRepository
                    .findNextRegularCartStart(targetOrd)
                    .filter(cartStart -> request.endDtm().isAfter(cartStart))
                    .ifPresent(cartStart -> {
                        throw new BusinessException(
                                ErrorCode.SCHEDULE_ORDER, "계절학기 성적입력은 다음 정규학기 장바구니 시작(" + cartStart + ") 전에 끝나야 합니다.");
                    });
        }
        if (semester.regular() && (request.schdType() == SchdType.CART || request.schdType() == SchdType.ENROLL)) {
            semesterRepository
                    .findPreviousSeasonalGradeEnd(targetOrd)
                    .filter(gradeEnd -> request.startDtm().isBefore(gradeEnd))
                    .ifPresent(gradeEnd -> {
                        throw new BusinessException(
                                ErrorCode.SCHEDULE_ORDER, "직전 계절학기 성적입력(" + gradeEnd + "까지)이 끝난 뒤에 시작해야 합니다.");
                    });
        }
    }

    private static String describe(ScheduleResponse schedule) {
        String name = switch (schedule.schdType()) {
            case CART -> "장바구니";
            case ENROLL -> "수강신청";
            case ADJUST -> "정정";
            case GRADE -> "성적입력";
        };
        String target = schedule.tgtYrLvl() == null ? "전 학년" : schedule.tgtYrLvl() + "학년";
        return target + " " + name + " 기간(" + schedule.startDtm() + " ~ " + schedule.endDtm() + ")";
    }

    private void lock(int semNo) {
        if (!semesterRepository.lock(semNo)) {
            throw new BusinessException(ErrorCode.SEMESTER_NOT_FOUND);
        }
    }

    private SemesterResponse get(int semNo) {
        return semesterRepository
                .findById(semNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.SEMESTER_NOT_FOUND));
    }

    private ScheduleResponse findSchedule(int schdNo) {
        return semesterRepository
                .findSchedule(schdNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.SCHEDULE_NOT_FOUND));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
