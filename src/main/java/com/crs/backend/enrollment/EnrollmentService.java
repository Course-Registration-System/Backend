package com.crs.backend.enrollment;

import com.crs.backend.academic.SchedulePolicy;
import com.crs.backend.common.BusinessException;
import com.crs.backend.common.ErrorCode;
import com.crs.backend.common.auth.CurrentUser;
import com.crs.backend.common.auth.Role;
import com.crs.backend.enrollment.EnrollmentRepository.LectureInfo;
import com.crs.backend.enrollment.EnrollmentRepository.Prerequisite;
import com.crs.backend.enrollment.EnrollmentRepository.StudentState;
import com.crs.backend.enrollment.dto.EnrollmentResponse;
import com.crs.backend.enrollment.dto.EnrollmentStatusResponse;
import com.crs.backend.grade.GradeQuery;
import com.crs.backend.lecture.LecStat;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수강신청·취소(REG-03)와 관리자 신청 현황 관리(REG-05). 학생 신청과 관리자 추가는 같은 검사를 거치고, 관리자는 정원이 찬 강의에도
 * 정원을 늘려 넣을 수 있습니다.
 *
 * <p>학생 → 강의 순으로 잠근 뒤 검사합니다. 학생 잠금이 트랜잭션의 첫 읽기여야 합니다. MySQL(REPEATABLE READ)은 첫 일반 읽기 시점의
 * 스냅숏을 끝까지 쓰므로, 잠금 전에 다른 것을 읽으면 같은 학생의 다른 신청이 방금 넣은 기록을 못 보고 시간·학점 검사가 통과될 수 있습니다.
 */
@Service
public class EnrollmentService {

    /** 좋은 등급부터. 뒤로 갈수록 낮은 등급입니다. */
    private static final List<String> GRADE_ORDER =
            List.of("A+", "A0", "A-", "B+", "B0", "B-", "C+", "C0", "C-", "D+", "D0", "D-", "F");

    private static final int MAX_RETAKE_COURSES = 10;

    private final EnrollmentRepository enrollmentRepository;
    private final CartRepository cartRepository;
    private final SchedulePolicy schedulePolicy;
    private final GradeQuery gradeQuery;
    private final Clock clock;

    public EnrollmentService(
            EnrollmentRepository enrollmentRepository,
            CartRepository cartRepository,
            SchedulePolicy schedulePolicy,
            GradeQuery gradeQuery,
            Clock clock) {
        this.enrollmentRepository = enrollmentRepository;
        this.cartRepository = cartRepository;
        this.schedulePolicy = schedulePolicy;
        this.gradeQuery = gradeQuery;
        this.clock = clock;
    }

    // ---- 학생(REG-03)

    public List<EnrollmentResponse> list(CurrentUser user) {
        return enrollmentRepository.findEnrolled(requireStudent(user), registrationSemester());
    }

    @Transactional
    public EnrollmentResponse enroll(CurrentUser user, int lecNo) {
        String stdNo = requireStudent(user);
        StudentState student = lockStudent(stdNo, ErrorCode.FORBIDDEN);
        int semNo = registrationSemester();
        checkStudentPeriod(semNo, student);
        LectureInfo lecture = lockLecture(lecNo);
        if (lecture.semNo() != semNo) {
            throw new BusinessException(ErrorCode.OTHER_SEMESTER_LECTURE);
        }
        register(stdNo, student, lecNo, lecture, ProcType.STUDENT);
        return enrollmentRepository.findEnrolled(stdNo, semNo).stream()
                .filter(enrollment -> enrollment.lecNo() == lecNo)
                .findFirst()
                .orElseThrow();
    }

    // ⑪ 취소
    @Transactional
    public void cancel(CurrentUser user, int lecNo) {
        String stdNo = requireStudent(user);
        StudentState student = lockStudent(stdNo, ErrorCode.FORBIDDEN);
        int semNo = registrationSemester();
        checkStudentPeriod(semNo, student);
        LectureInfo lecture = lockLecture(lecNo);
        if (lecture.semNo() != semNo) {
            throw new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND);
        }
        unregister(stdNo, lecNo, ProcType.STUDENT);
    }

    // ---- 관리자(REG-05)

    // ①② 강의별 현황. 정정 기간이 끝나도 조회는 된다
    public EnrollmentStatusResponse status(int lecNo) {
        LectureInfo lecture = enrollmentRepository
                .findLecture(lecNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.LECTURE_NOT_FOUND));
        return new EnrollmentStatusResponse(
                lecNo,
                lecture.cap(),
                lecture.enrlCnt(),
                Math.max(lecture.cap() - lecture.enrlCnt(), 0),
                enrollmentRepository.findStudents(lecNo));
    }

    // ③~⑤ 관리자 추가. 학생 신청과 같은 검사를 거치고, 정원이 차 있으면 정원을 1 늘려 넣는다
    @Transactional
    public void addByAdmin(int lecNo, String stdNo) {
        StudentState student = lockStudent(stdNo, ErrorCode.USER_NOT_FOUND);
        LectureInfo lecture = lockLecture(lecNo);
        checkAdminPeriod(lecture.semNo());
        register(stdNo, student, lecNo, lecture, ProcType.ADMIN);
    }

    // ⑥ 관리자 취소
    @Transactional
    public void cancelByAdmin(int lecNo, String stdNo) {
        lockStudent(stdNo, ErrorCode.USER_NOT_FOUND);
        LectureInfo lecture = lockLecture(lecNo);
        checkAdminPeriod(lecture.semNo());
        unregister(stdNo, lecNo, ProcType.ADMIN);
    }

    // ---- 폐강(ACA-03)

    /**
     * 폐강한 강의의 신청을 모두 관리자 처리로 취소하고 장바구니에서도 뺍니다. 강의 개설 관리가 강의 행을 잠근 트랜잭션 안에서 부릅니다.
     */
    @Transactional
    public void clearClosedLecture(int lecNo, LocalDateTime now) {
        enrollmentRepository.cancelAll(lecNo, now);
        cartRepository.deleteByLecture(lecNo);
    }

    // ---- 공통 검사와 저장

    /** 처리 내용 ②~⑨. 학생과 강의를 잠근 뒤 부릅니다. 정원이 찼을 때 학생은 막고, 관리자는 정원을 늘립니다. */
    private void register(String stdNo, StudentState student, int lecNo, LectureInfo lecture, ProcType procType) {
        int semNo = lecture.semNo();
        StudentStatusRule.check(stdNo, student.stdStat(), enrollmentRepository.isSeasonal(semNo));
        if (lecture.lecStat() == LecStat.CLOSED) {
            throw new BusinessException(ErrorCode.LECTURE_CLOSED);
        }
        // ③ 같은 과목(다른 분반 포함)
        if (enrollmentRepository.enrolledInCourse(stdNo, semNo, lecture.crsCd())) {
            throw new BusinessException(ErrorCode.ALREADY_ENROLLED_COURSE);
        }
        boolean retake = checkRetake(stdNo, lecture); // ④
        boolean full = lecture.enrlCnt() >= lecture.cap(); // ⑤
        if (full && procType == ProcType.STUDENT) {
            throw new BusinessException(ErrorCode.LECTURE_FULL);
        }
        enrollmentRepository
                .findTimeConflict(stdNo, semNo, lecNo)
                .ifPresent(
                        other -> { // ⑥
                            throw new BusinessException(ErrorCode.TIME_CONFLICT, other + " 강의와 시간이 겹칩니다.");
                        });
        checkCredits(stdNo, semNo, lecture); // ⑦
        checkPrerequisites(stdNo, lecture.crsCd()); // ⑧

        // ⑨ 저장. 취소한 기록이 있으면 새로 만들지 않고 되살린다
        LocalDateTime now = LocalDateTime.now(clock);
        if (enrollmentRepository.findEnrlStat(stdNo, lecNo).isPresent()) {
            enrollmentRepository.revive(stdNo, lecNo, retake, procType, now);
        } else {
            enrollmentRepository.insert(stdNo, lecNo, retake, procType, now);
        }
        if (full) {
            enrollmentRepository.raiseCapForOneMore(lecNo);
        }
        enrollmentRepository.addEnrlCnt(lecNo, 1);
    }

    private void unregister(String stdNo, int lecNo, ProcType procType) {
        if (enrollmentRepository.cancel(stdNo, lecNo, procType, LocalDateTime.now(clock)) == 0) {
            throw new BusinessException(ErrorCode.ENROLLMENT_NOT_FOUND);
        }
        enrollmentRepository.addEnrlCnt(lecNo, -1);
    }

    /**
     * 이미 확정 성적이 있는 과목이면 재수강입니다. 가장 최근 확정 성적이 C+ 이하(P/F 과목은 F)여야 하고, 재수강 과목은 10과목까지입니다.
     */
    private boolean checkRetake(String stdNo, LectureInfo lecture) {
        // 채플은 학기마다 다시 듣는 과목이라 재수강이 아니다(재수강 표시·10과목 집계 제외)
        if (lecture.chapel()) {
            return false;
        }
        Optional<String> previous = gradeQuery.latestConfirmedGrade(stdNo, lecture.crsCd());
        if (previous.isEmpty()) {
            return false;
        }
        String grade = previous.get();
        boolean allowed = lecture.passFail() ? "F".equals(grade) : rank(grade) >= rank("C+");
        if (!allowed) {
            throw new BusinessException(ErrorCode.RETAKE_NOT_ALLOWED);
        }
        boolean newCourse = !enrollmentRepository.retakenCourse(stdNo, lecture.crsCd());
        if (newCourse && enrollmentRepository.countRetakenCourses(stdNo) >= MAX_RETAKE_COURSES) {
            throw new BusinessException(ErrorCode.RETAKE_LIMIT_EXCEEDED);
        }
        return true;
    }

    /** 채플 학점은 최대학점 계산에 넣지 않습니다. */
    private void checkCredits(String stdNo, int semNo, LectureInfo lecture) {
        if (lecture.chapel()) {
            return;
        }
        BigDecimal total = enrollmentRepository.sumCredits(stdNo, semNo).add(lecture.crdt());
        if (total.compareTo(BigDecimal.valueOf(schedulePolicy.maxCredit(stdNo, semNo))) > 0) {
            throw new BusinessException(ErrorCode.CREDIT_EXCEEDED);
        }
    }

    /** 확정 성적이 최소취득등급 이상이어야 합니다. 선수과목이 P/F 과목이면 P를 받았어야 합니다. */
    private void checkPrerequisites(String stdNo, String crsCd) {
        for (Prerequisite prerequisite : enrollmentRepository.findPrerequisites(crsCd)) {
            String grade =
                    gradeQuery.latestConfirmedGrade(stdNo, prerequisite.crsCd()).orElse(null);
            // 최소취득등급에 F 는 올 수 없으므로 F 는 언제나 미이수다
            boolean met = grade != null
                    && (prerequisite.passFail()
                            ? "P".equals(grade)
                            : rank(grade) >= 0 && rank(grade) <= rank(prerequisite.minGrade()));
            if (!met) {
                throw new BusinessException(
                        ErrorCode.PREREQUISITE_NOT_MET, "선수과목 " + prerequisite.crsNm() + "을(를) 이수하지 않았습니다.");
            }
        }
    }

    private static int rank(String grade) {
        return GRADE_ORDER.indexOf(grade);
    }

    // ---- 기간·대상

    private String requireStudent(CurrentUser user) {
        if (!user.is(Role.STUDENT)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return user.usrNo();
    }

    private int registrationSemester() {
        return schedulePolicy
                .registrationSemester()
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_REGISTRATION_SEMESTER));
    }

    private StudentState lockStudent(String stdNo, ErrorCode notFound) {
        return enrollmentRepository.lockStudent(stdNo).orElseThrow(() -> new BusinessException(notFound));
    }

    private LectureInfo lockLecture(int lecNo) {
        return enrollmentRepository
                .lockLecture(lecNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.LECTURE_NOT_FOUND));
    }

    /** 학생은 본인 학년 수강신청일, 전체 수강신청일, 정정 기간에만 신청·취소합니다. */
    private void checkStudentPeriod(int semNo, StudentState student) {
        if (!schedulePolicy.enrollOpen(semNo, student.yrLvl())) {
            throw new BusinessException(ErrorCode.ENROLL_CLOSED);
        }
    }

    /**
     * 관리자는 학년과 상관없이 첫 수강신청일부터 신청 종료(정정 종료, 정정이 없으면 마지막 수강신청일 종료) 전까지 추가·취소합니다. 수강신청
     * 일정이 있으면 신청 종료도 있으므로, 시작했는데 종료가 비는 경우는 없습니다.
     */
    private void checkAdminPeriod(int semNo) {
        LocalDateTime now = LocalDateTime.now(clock);
        boolean started = schedulePolicy
                .enrollStart(semNo)
                .map(start -> !now.isBefore(start))
                .orElse(false);
        boolean over = schedulePolicy
                .registrationEnd(semNo)
                .map(end -> !now.isBefore(end))
                .orElse(false);
        if (!started || over) {
            throw new BusinessException(ErrorCode.ENROLL_CLOSED);
        }
    }
}
