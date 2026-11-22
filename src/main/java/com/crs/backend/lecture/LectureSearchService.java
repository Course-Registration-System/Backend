package com.crs.backend.lecture;

import com.crs.backend.academic.SchedulePolicy;
import com.crs.backend.common.BusinessException;
import com.crs.backend.common.ErrorCode;
import com.crs.backend.lecture.LectureSearchRepository.LectureRow;
import com.crs.backend.lecture.dto.LectureSearchItem;
import com.crs.backend.lecture.dto.LectureSearchResponse;
import com.crs.backend.lecture.dto.LectureSearchSlot;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 강의 검색(REG-01). 학생·교수·조교·관리자 모두 씁니다. 담기·신청 버튼은 화면이 역할을 보고 숨깁니다. */
@Service
public class LectureSearchService {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;

    private static final Set<String> CMPL_TYPES =
            Set.of("MAJOR_BASIC", "MAJOR_REQ", "MAJOR_ELEC", "GEN_REQ", "GEN_ELEC", "TEACHING", "FREE_ELEC", "CHAPEL");

    private final LectureSearchRepository repository;
    private final SchedulePolicy schedulePolicy;

    public LectureSearchService(LectureSearchRepository repository, SchedulePolicy schedulePolicy) {
        this.repository = repository;
        this.schedulePolicy = schedulePolicy;
    }

    @Transactional(readOnly = true)
    public LectureSearchResponse search(
            Integer semNo,
            Integer deptNo,
            Integer yrLvl,
            String cmplType,
            String crsNm,
            String profNm,
            Integer page,
            Integer size) {
        int pageNo = page == null ? 0 : page;
        int pageSize = size == null ? DEFAULT_SIZE : size;
        if (pageNo < 0 || pageSize < 1 || pageSize > MAX_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "page 는 0 이상, size 는 1~" + MAX_SIZE + " 입니다.");
        }
        if (yrLvl != null && (yrLvl < 1 || yrLvl > 4)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "학년은 1~4 입니다.");
        }
        String type = blankToNull(cmplType);
        if (type != null && !CMPL_TYPES.contains(type)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이수구분 값이 올바르지 않습니다.");
        }

        var condition = new LectureSearchCondition(
                semNo == null ? targetSemester() : semNo, deptNo, yrLvl, type, blankToNull(crsNm), blankToNull(profNm));
        long total = repository.count(condition);
        List<LectureRow> rows = repository.findPage(condition, pageNo, pageSize);
        Map<Integer, List<LectureSearchSlot>> slots =
                repository.findSlots(rows.stream().map(LectureRow::lecNo).toList());
        List<LectureSearchItem> items = rows.stream()
                .map(row -> row.withSlots(slots.getOrDefault(row.lecNo(), List.of())))
                .toList();
        return new LectureSearchResponse(condition.semNo(), pageNo, pageSize, total, items);
    }

    // 학기를 비우면 신청 대상 학기(D24), 그것도 없으면(정정까지 끝남) 현재 학기
    private int targetSemester() {
        return schedulePolicy
                .registrationSemester()
                .or(repository::findCurrentSemester)
                .orElseThrow(() -> new BusinessException(ErrorCode.SEMESTER_NOT_FOUND));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
