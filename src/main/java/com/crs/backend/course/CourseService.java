package com.crs.backend.course;

import com.crs.backend.common.BusinessException;
import com.crs.backend.common.ErrorCode;
import com.crs.backend.course.CourseRepository.CourseRow;
import com.crs.backend.course.CourseRepository.PrerequisiteRow;
import com.crs.backend.course.dto.CourseCreateRequest;
import com.crs.backend.course.dto.CourseResponse;
import com.crs.backend.course.dto.CourseUpdateRequest;
import com.crs.backend.course.dto.PrerequisiteRequest;
import com.crs.backend.course.dto.PrerequisiteResponse;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 과목 관리(ACA-02). 과목 등록·수정과 선수과목 지정. 과목은 삭제하지 않습니다. */
@Service
public class CourseService {

    private static final BigDecimal CHAPEL_CREDIT = new BigDecimal("0.5");
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private final CourseRepository courseRepository;

    public CourseService(CourseRepository courseRepository) {
        this.courseRepository = courseRepository;
    }

    @Transactional(readOnly = true)
    public List<CourseResponse> list(Integer deptNo, String cmplType, String keyword) {
        List<CourseRow> rows = courseRepository.findRows(deptNo, parseCmplType(cmplType), keyword);
        Map<String, List<PrerequisiteResponse>> prerequisites =
                prerequisitesOf(rows.stream().map(row -> row.course().crsCd()).toList());
        return rows.stream()
                .map(row ->
                        toResponse(row, prerequisites.getOrDefault(row.course().crsCd(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CourseResponse get(String crsCd) {
        CourseRow row =
                courseRepository.findRow(crsCd).orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));
        return toResponse(row, prerequisitesOf(List.of(crsCd)).getOrDefault(crsCd, List.of()));
    }

    @Transactional
    public CourseResponse create(CourseCreateRequest request) {
        // 기본 정보 검사
        EvalType evalType = evalTypeOrDefault(request.evalType(), request.cmplType());
        validateCourse(request.crdt(), request.cmplType(), evalType, request.deptNo());
        if (courseRepository.exists(request.crsCd())) {
            throw new BusinessException(ErrorCode.DUPLICATE_COURSE_CODE);
        }

        // 선수과목 검사는 저장 전에 끝낸다. 새 과목을 선수과목으로 가진 과목은 아직 없으므로 순환은 생길 수 없다
        List<PrerequisiteCourse> prerequisites =
                toPrerequisites(request.crsCd(), request.prerequisites() == null ? List.of() : request.prerequisites());

        // 저장
        try {
            courseRepository.insert(new Course(
                    request.crsCd(),
                    request.deptNo(),
                    request.crsNm(),
                    request.crdt(),
                    request.cmplType(),
                    evalType,
                    request.rcmdYrLvl()));
        } catch (DuplicateKeyException e) {
            // 존재 확인과 저장 사이에 다른 요청이 같은 코드로 먼저 등록한 경우
            throw new BusinessException(ErrorCode.DUPLICATE_COURSE_CODE);
        }
        courseRepository.insertPrerequisites(prerequisites);
        return get(request.crsCd());
    }

    /**
     * 개설된 강의가 하나라도 있는 과목(폐강 포함)은 과목명·권장학년만 바꿀 수 있습니다. 학점·이수구분·평가방식은 평점·최대학점·선수과목·상대평가
     * 판단이 조회 시점의 과목 값으로 계산하므로, 바꾸면 지난 학기 결과와 진행 중인 학기의 신청·성적이 달라집니다. 학과도 같이 고정합니다.
     */
    @Transactional
    public CourseResponse update(String crsCd, CourseUpdateRequest request) {
        // 과목 행을 잠가, 확인하는 동안 이 과목으로 강의가 새로 개설되지 않게 한다(강의 INSERT 의 외래키 검사가 이 행에 공유 잠금을 건다)
        Course current = courseRepository
                .findForUpdate(crsCd)
                .orElseThrow(() -> new BusinessException(ErrorCode.COURSE_NOT_FOUND));

        // 수정은 evalType 이 필수라 기본값을 채우지 않는다
        EvalType evalType = request.evalType();
        validateCourse(request.crdt(), request.cmplType(), evalType, request.deptNo());

        boolean fixedValueChanged = current.deptNo() != request.deptNo()
                || current.crdt().compareTo(request.crdt()) != 0
                || current.cmplType() != request.cmplType()
                || current.evalType() != evalType;
        if (fixedValueChanged && courseRepository.hasLecture(crsCd)) {
            throw new BusinessException(ErrorCode.COURSE_IN_USE);
        }

        courseRepository.update(new Course(
                crsCd,
                request.deptNo(),
                request.crsNm(),
                request.crdt(),
                request.cmplType(),
                evalType,
                request.rcmdYrLvl()));
        return get(crsCd);
    }

    @Transactional
    public CourseResponse replacePrerequisites(String crsCd, List<PrerequisiteRequest> requests) {
        // 선수과목 변경은 한 번에 하나씩 처리한다. 관계 전체를 잠그고 최신 값으로 읽어야, 동시에 들어온 A→B 와 B→A(또는 더 긴 순환을
        // 이루는 두 변경)가 서로를 못 본 채 둘 다 순환 검사를 통과하는 일이 없다. 다른 조회보다 먼저 잠가야 최신 값을 읽는다
        List<PrerequisiteCourse> allPrerequisites = courseRepository.lockAllPrerequisites();

        if (!courseRepository.exists(crsCd)) {
            throw new BusinessException(ErrorCode.COURSE_NOT_FOUND);
        }
        List<PrerequisiteCourse> prerequisites = toPrerequisites(crsCd, requests);
        checkNoCycle(crsCd, prerequisites, allPrerequisites);

        courseRepository.deletePrerequisites(crsCd);
        courseRepository.insertPrerequisites(prerequisites);
        return get(crsCd);
    }

    // 학점 0.5 단위, 채플 규칙, 학과 존재
    private void validateCourse(BigDecimal crdt, CmplType cmplType, EvalType evalType, int deptNo) {
        if (crdt.multiply(TWO).stripTrailingZeros().scale() > 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "crdt: 0.5 단위여야 합니다.");
        }
        if (cmplType == CmplType.CHAPEL && (crdt.compareTo(CHAPEL_CREDIT) != 0 || evalType != EvalType.PASS_FAIL)) {
            throw new BusinessException(ErrorCode.INVALID_CHAPEL_COURSE);
        }
        if (!courseRepository.departmentExists(deptNo)) {
            throw new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND);
        }
    }

    // 평가방식을 정하지 않으면 등급제. 채플은 P/F 로만 평가하므로 채플이면 P/F
    private static EvalType evalTypeOrDefault(EvalType evalType, CmplType cmplType) {
        if (evalType != null) {
            return evalType;
        }
        return cmplType == CmplType.CHAPEL ? EvalType.PASS_FAIL : EvalType.GRADE;
    }

    // 목록 안 중복, 자기 자신, 없는 과목을 검사하고 최소취득등급 기본값을 채운다
    private List<PrerequisiteCourse> toPrerequisites(String crsCd, List<PrerequisiteRequest> requests) {
        Set<String> codes = new LinkedHashSet<>();
        for (PrerequisiteRequest request : requests) {
            if (!codes.add(request.prereqCrsCd())) {
                throw new BusinessException(
                        ErrorCode.INVALID_INPUT, "prerequisites: 같은 과목이 두 번 있습니다. " + request.prereqCrsCd());
            }
        }
        if (codes.contains(crsCd)) {
            throw new BusinessException(ErrorCode.PREREQUISITE_SELF);
        }

        Set<String> existing = courseRepository.findExistingCodes(codes);
        List<String> missing =
                codes.stream().filter(code -> !existing.contains(code)).toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.COURSE_NOT_FOUND, "선수과목으로 지정한 과목을 찾을 수 없습니다: " + String.join(", ", missing));
        }

        return requests.stream()
                .map(request -> new PrerequisiteCourse(
                        crsCd,
                        request.prereqCrsCd(),
                        request.minReqGrd() == null ? PrerequisiteCourse.DEFAULT_MIN_REQ_GRD : request.minReqGrd()))
                .toList();
    }

    /**
     * 새 선수과목 목록으로 바꾼 뒤 순환이 생기는지 검사합니다. 업무기술서는 두 과목이 서로를 선수과목으로 갖는 것만 금지하지만, 순환에 든 과목은 어느
     * 것도 먼저 이수할 수 없으므로 길이와 관계없이 막습니다. 새 선수과목 P 에서 선수과목을 따라가 crsCd 에 닿으면 crsCd → P → … → crsCd
     * 순환입니다.
     */
    private void checkNoCycle(
            String crsCd, List<PrerequisiteCourse> prerequisites, List<PrerequisiteCourse> allPrerequisites) {
        // 과목 → 그 과목의 선수과목들. crsCd 의 기존 선수과목은 바뀔 것이므로 뺀다
        Map<String, List<String>> graph = new HashMap<>();
        for (PrerequisiteCourse edge : allPrerequisites) {
            if (!edge.crsCd().equals(crsCd)) {
                graph.computeIfAbsent(edge.crsCd(), key -> new ArrayList<>()).add(edge.prereqCrsCd());
            }
        }

        for (PrerequisiteCourse prerequisite : prerequisites) {
            List<String> path = findPath(graph, prerequisite.prereqCrsCd(), crsCd);
            if (path != null) {
                throw new BusinessException(
                        ErrorCode.PREREQUISITE_CYCLE, "선수과목이 순환합니다: " + crsCd + " → " + String.join(" → ", path));
            }
        }
    }

    // from 에서 to 까지 가는 경로(from, …, to). 없으면 null
    private static List<String> findPath(Map<String, List<String>> graph, String from, String to) {
        Map<String, String> previous = new HashMap<>();
        Deque<String> queue = new ArrayDeque<>();
        previous.put(from, null);
        queue.add(from);
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (current.equals(to)) {
                List<String> path = new ArrayList<>();
                for (String node = to; node != null; node = previous.get(node)) {
                    path.add(node);
                }
                Collections.reverse(path);
                return path;
            }
            for (String next : graph.getOrDefault(current, List.of())) {
                if (!previous.containsKey(next)) {
                    previous.put(next, current);
                    queue.add(next);
                }
            }
        }
        return null;
    }

    private static CmplType parseCmplType(String cmplType) {
        if (cmplType == null || cmplType.isBlank()) {
            return null;
        }
        try {
            return CmplType.valueOf(cmplType);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "cmplType: 이수구분 코드가 아닙니다. " + cmplType);
        }
    }

    private Map<String, List<PrerequisiteResponse>> prerequisitesOf(List<String> crsCds) {
        return courseRepository.findPrerequisiteRows(crsCds).stream()
                .collect(Collectors.groupingBy(
                        PrerequisiteRow::crsCd,
                        Collectors.mapping(
                                row -> new PrerequisiteResponse(row.prereqCrsCd(), row.prereqCrsNm(), row.minReqGrd()),
                                Collectors.toList())));
    }

    private static CourseResponse toResponse(CourseRow row, List<PrerequisiteResponse> prerequisites) {
        Course course = row.course();
        return new CourseResponse(
                course.crsCd(),
                course.crsNm(),
                course.deptNo(),
                row.deptNm(),
                course.crdt(),
                course.cmplType(),
                course.evalType(),
                course.rcmdYrLvl(),
                prerequisites);
    }
}
