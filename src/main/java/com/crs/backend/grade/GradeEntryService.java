package com.crs.backend.grade;

import com.crs.backend.academic.SchedulePolicy;
import com.crs.backend.common.BusinessException;
import com.crs.backend.common.ErrorCode;
import com.crs.backend.common.auth.CurrentUser;
import com.crs.backend.course.CmplType;
import com.crs.backend.course.EvalType;
import com.crs.backend.grade.GradeEntryRepository.LectureRow;
import com.crs.backend.grade.GradeEntryRepository.StudentRow;
import com.crs.backend.grade.dto.GradeBands;
import com.crs.backend.grade.dto.GradeCountRequest;
import com.crs.backend.grade.dto.GradeInput;
import com.crs.backend.grade.dto.GradeSaveRequest;
import com.crs.backend.grade.dto.GradeSheetResponse;
import com.crs.backend.grade.dto.GradeSheetStudent;
import com.crs.backend.grade.dto.GradeWeights;
import com.crs.backend.lecture.LecStat;
import com.crs.backend.lecture.LectureAccess;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 성적 입력·수정(GRD-01). 교수와 권한 있는 조교는 성적 입력 기간에 등급제 과목은 원점수, P/F 과목은 P·F 만 저장합니다. 등급제 과목은 저장할
 * 때마다 강의 전체(원점수가 다 있는 학생)의 최종점수·등급을 다시 계산합니다. 원점수 총점은 tot_scr, 최종점수는 fnl_scr 에 둡니다. 성적표 조회는
 * 기간과 관계없습니다.
 *
 * <ul>
 *   <li>절대평가: 최종점수 = 원점수 총점(소수점 버림)
 *   <li>상대평가: 원점수 총점(소수 그대로) 석차로 A군(1 ~ A 인원)·B군(~ A+B 인원)·그 외로 나누고, 군 안에서 Max − (First − 원점수 총점) ×
 *       (Max − Min) ÷ (First − Last) 를 반올림(0.5 올림)한 값이 최종점수. A군 100~90, B군 89~80, 그 외는 버림 총점과 79 중 작은 값
 *   <li>재수강은 최종점수 93, 등급 A- 상한
 * </ul>
 *
 * <p>근거: 업무분석서 GRD-01, 상대평가 성적 처리 설계안(2026-11-24 합의).
 */
@Service
public class GradeEntryService {

    private static final Set<CmplType> MAJOR = Set.of(CmplType.MAJOR_BASIC, CmplType.MAJOR_REQ, CmplType.MAJOR_ELEC);
    /** 상대평가 대상이 되는 최소 수강인원 */
    private static final int RELATIVE_MIN_ENROLLED = 10;

    private static final int A_MIN = 90;
    private static final int A_MAX = 100;
    private static final int B_MIN = 80;
    private static final int B_MAX = 89;
    /** 상대평가에서 A군·B군 밖 학생의 최종점수 상한 */
    private static final int OTHER_MAX = 79;
    /** 재수강 최종점수 상한(A- 구간 꼭대기) */
    private static final int RETAKE_MAX = 93;

    private final GradeEntryRepository gradeEntryRepository;
    private final LectureAccess lectureAccess;
    private final SchedulePolicy schedulePolicy;

    public GradeEntryService(
            GradeEntryRepository gradeEntryRepository, LectureAccess lectureAccess, SchedulePolicy schedulePolicy) {
        this.gradeEntryRepository = gradeEntryRepository;
        this.lectureAccess = lectureAccess;
        this.schedulePolicy = schedulePolicy;
    }

    /** 성적표 조회. 없는 강의는 권한보다 먼저 404 입니다. */
    @Transactional(readOnly = true)
    public GradeSheetResponse sheet(CurrentUser user, int lecNo) {
        LectureRow lecture = gradeEntryRepository
                .findLecture(lecNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.LECTURE_NOT_FOUND));
        lectureAccess.checkManage(user, lecNo);
        return toSheet(lecture, gradeEntryRepository.findStudents(lecNo));
    }

    /**
     * 성적 저장. 넣은 학생만 바뀌며, 한 명이라도 실패하면 아무것도 저장하지 않습니다. 등급제 과목은 원점수만 받고 강의 전체를 다시 계산하며, P/F
     * 과목은 점수 없이 P·F 만 받습니다. 저장하면 입력중이 됩니다.
     */
    @Transactional
    public GradeSheetResponse save(CurrentUser user, int lecNo, GradeSaveRequest request) {
        Set<String> seen = new HashSet<>();
        for (GradeInput input : request.grades()) {
            if (!seen.add(input.stdNo())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "같은 학번을 두 번 넣었습니다: " + input.stdNo());
            }
        }

        LectureRow lecture = lockEditable(user, lecNo);
        boolean passFail = lecture.evalType() == EvalType.PASS_FAIL;
        if (!passFail) {
            requireWeights(lecture);
        }

        // 모든 학생을 먼저 확인한 뒤 저장한다
        Set<String> enrolled = gradeEntryRepository.findStudents(lecNo).stream()
                .map(StudentRow::stdNo)
                .collect(Collectors.toSet());
        for (GradeInput input : request.grades()) {
            if (!enrolled.contains(input.stdNo())) {
                throw new BusinessException(ErrorCode.NOT_ENROLLED_STUDENT, "이 강의의 수강생이 아닙니다: " + input.stdNo());
            }
            if (passFail && input.hasScore()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "P/F 과목은 점수 없이 P 또는 F 만 입력합니다: " + input.stdNo());
            }
            if (!passFail && input.ltrGrd() != null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "등급제 과목은 등급을 넣지 않고 원점수만 입력합니다: " + input.stdNo());
            }
        }

        for (GradeInput input : request.grades()) {
            if (passFail) {
                gradeEntryRepository.savePassFail(lecNo, input.stdNo(), input.ltrGrd());
            } else {
                BigDecimal raw = rawTotal(lecture, input.midScr(), input.finScr(), input.hwScr(), input.attScr());
                gradeEntryRepository.saveRawScores(
                        lecNo,
                        input.stdNo(),
                        input.midScr(),
                        input.finScr(),
                        input.hwScr(),
                        input.attScr(),
                        raw == null ? null : floor(raw));
            }
        }
        if (!passFail) {
            recalculate(lecture);
        }
        toEntering(lecture);
        return reload(lecNo);
    }

    /**
     * 상대평가 등급 인원 저장. A 인원은 0 ~ A 한도, A+B 인원은 A 인원 ~ A+B 한도이고 null 은 한도까지입니다. 저장하면 강의 전체를 다시
     * 계산하고 입력중이 됩니다.
     */
    @Transactional
    public GradeSheetResponse saveCounts(CurrentUser user, int lecNo, GradeCountRequest request) {
        LectureRow lecture = lockEditable(user, lecNo);
        int enrolled = gradeEntryRepository.findStudents(lecNo).size();
        if (evalMode(lecture, enrolled) != EvalMode.RELATIVE) {
            throw new BusinessException(ErrorCode.GRADE_NOT_RELATIVE);
        }
        requireWeights(lecture); // 다시 계산할 때 원점수 총점을 반영 비율로 낸다
        GradeBands limits = bands(lecture, enrolled);
        int aCnt = request.aCnt() != null ? request.aCnt() : limits.aLimit();
        int abCnt = request.abCnt() != null ? request.abCnt() : limits.abLimit();
        if (aCnt > limits.aLimit() || abCnt > limits.abLimit() || aCnt > abCnt) {
            throw new BusinessException(ErrorCode.GRADE_COUNT_OUT_OF_RANGE);
        }

        gradeEntryRepository.saveCounts(lecNo, request.aCnt(), request.abCnt());
        recalculate(gradeEntryRepository.findLecture(lecNo).orElseThrow());
        toEntering(lecture);
        return reload(lecNo);
    }

    /** 제출. 모든 수강생이 입력돼 있어야 하고, 상대평가 강의는 군 경계에 원점수 총점이 같은 학생이 없어야 합니다. */
    @Transactional
    public GradeSheetResponse submit(CurrentUser user, int lecNo) {
        LectureRow lecture = lockEditable(user, lecNo);
        if (lecture.grdStat() == GrdStat.SUBMITTED) {
            throw new BusinessException(ErrorCode.GRADE_ALREADY_SUBMITTED);
        }

        // 수강생이 없는 강의는 계산할 것이 없어 미입력에서 바로 제출한다
        List<StudentRow> students = gradeEntryRepository.findStudents(lecNo);
        if (!students.isEmpty()) {
            boolean passFail = lecture.evalType() == EvalType.PASS_FAIL;
            if (!passFail) {
                requireWeights(lecture);
            }
            if (!students.stream().allMatch(s -> entered(lecture, s))) {
                throw new BusinessException(ErrorCode.GRADE_INCOMPLETE);
            }
            if (!passFail) {
                for (Result result : grade(lecture, students, true)) {
                    gradeEntryRepository.saveResult(
                            lecNo, result.stdNo(), result.totScr(), result.fnlScr(), result.ltrGrd());
                }
            }
        }

        gradeEntryRepository.updateGrdStat(lecNo, GrdStat.SUBMITTED);
        return reload(lecNo);
    }

    /** 계산한 학생 한 명의 원점수 총점·최종점수·등급. */
    private record Result(String stdNo, int totScr, int fnlScr, String ltrGrd) {}

    /** 원점수 총점(소수 그대로)과 함께 다루는 학생. */
    private record Ranked(StudentRow student, BigDecimal rawTotal) {}

    // 등급제 강의 전체를 다시 계산한다. 원점수가 빈 학생은 최종점수·등급이 비어 있다
    private void recalculate(LectureRow lecture) {
        gradeEntryRepository.clearResults(lecture.lecNo());
        for (Result result : grade(lecture, gradeEntryRepository.findStudents(lecture.lecNo()), false)) {
            gradeEntryRepository.saveResult(
                    lecture.lecNo(), result.stdNo(), result.totScr(), result.fnlScr(), result.ltrGrd());
        }
    }

    /**
     * 원점수가 다 있는 학생의 원점수 총점 → 최종점수 → 등급. 한도와 평가 방식은 수강인원 전체로 정합니다. rejectTie 이면 군 경계 동점을 막고, 아니면
     * 동점 학생은 학번 순으로 놓고 계산합니다(저장은 되고 제출만 막힘).
     */
    private static List<Result> grade(LectureRow lecture, List<StudentRow> students, boolean rejectTie) {
        int enrolled = students.size();
        List<Ranked> ranked = students.stream()
                .filter(s -> entered(lecture, s))
                .map(s -> new Ranked(s, rawTotal(lecture, s)))
                .sorted(Comparator.comparing(Ranked::rawTotal).reversed())
                .toList();

        int[] fnl = new int[ranked.size()];
        if (evalMode(lecture, enrolled) == EvalMode.RELATIVE) {
            GradeBands bands = bands(lecture, enrolled);
            // 수강인원이 줄어 저장된 인원이 한도를 넘으면 한도까지만 준다
            int aCnt = Math.min(bands.aCnt() != null ? bands.aCnt() : bands.aLimit(), bands.aLimit());
            int abCnt =
                    Math.max(aCnt, Math.min(bands.abCnt() != null ? bands.abCnt() : bands.abLimit(), bands.abLimit()));
            if (rejectTie) {
                checkNoTieAtBoundary(ranked, aCnt);
                checkNoTieAtBoundary(ranked, abCnt);
            }
            convert(ranked, 0, aCnt, A_MIN, A_MAX, fnl);
            convert(ranked, aCnt, abCnt, B_MIN, B_MAX, fnl);
            for (int i = abCnt; i < ranked.size(); i++) {
                fnl[i] = Math.min(floor(ranked.get(i).rawTotal()), OTHER_MAX);
            }
        } else {
            for (int i = 0; i < ranked.size(); i++) {
                fnl[i] = floor(ranked.get(i).rawTotal());
            }
        }

        List<Result> results = new ArrayList<>();
        for (int i = 0; i < ranked.size(); i++) {
            StudentRow s = ranked.get(i).student();
            int score = s.retake() ? Math.min(fnl[i], RETAKE_MAX) : fnl[i];
            results.add(new Result(
                    s.stdNo(),
                    floor(ranked.get(i).rawTotal()),
                    score,
                    LetterGrade.fromScore(score, s.retake()).code()));
        }
        return results;
    }

    /** 석차 from ~ to-1 (0부터) 학생을 한 군으로 보고 군 안 최종점수를 out 에 넣습니다. */
    private static void convert(List<Ranked> ranked, int from, int to, int min, int max, int[] out) {
        int end = Math.min(to, ranked.size());
        if (from >= end) {
            return;
        }
        BigDecimal first = ranked.get(from).rawTotal();
        BigDecimal last = ranked.get(end - 1).rawTotal();
        for (int i = from; i < end; i++) {
            out[i] = groupScore(ranked.get(i).rawTotal(), first, last, min, max);
        }
    }

    /**
     * 군 안 최종점수 = Round(Max − (First − 원점수 총점) × (Max − Min) ÷ (First − Last)), 0.5 올림. 분자·분모를 그대로 나눠 반올림하므로
     * 오차가 없습니다. 군이 한 명이거나 First = Last 이면 버림 총점을 Min ~ Max 안으로 맞춥니다.
     */
    private static int groupScore(BigDecimal raw, BigDecimal first, BigDecimal last, int min, int max) {
        if (first.compareTo(last) == 0) {
            return Math.min(max, Math.max(min, floor(raw)));
        }
        BigDecimal spread = first.subtract(last);
        BigDecimal numerator = spread.multiply(BigDecimal.valueOf(max))
                .subtract(first.subtract(raw).multiply(BigDecimal.valueOf(max - min)));
        return numerator.divide(spread, 0, RoundingMode.HALF_UP).intValueExact();
    }

    /** 군 경계(limit 번째와 그다음)에 원점수 총점이 같으면 교수가 원점수로 순위를 정해야 한다 */
    private static void checkNoTieAtBoundary(List<Ranked> ranked, int limit) {
        if (limit <= 0 || limit >= ranked.size()) {
            return;
        }
        BigDecimal boundary = ranked.get(limit - 1).rawTotal();
        if (boundary.compareTo(ranked.get(limit).rawTotal()) != 0) {
            return;
        }
        String tied = ranked.stream()
                .filter(r -> r.rawTotal().compareTo(boundary) == 0)
                .map(r -> r.student().stdNo())
                .sorted()
                .collect(Collectors.joining(", "));
        throw new BusinessException(
                ErrorCode.GRADE_TIE_AT_BOUNDARY,
                ErrorCode.GRADE_TIE_AT_BOUNDARY.message() + " (총점 "
                        + display(boundary).toPlainString() + ": " + tied + ")");
    }

    // 저장·제출 공통: 강의 행 잠금(트랜잭션의 첫 조회) → 404 → 403 → 폐강 → 기간 → 확정
    private LectureRow lockEditable(CurrentUser user, int lecNo) {
        LectureRow lecture = gradeEntryRepository
                .lockLecture(lecNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.LECTURE_NOT_FOUND));
        lectureAccess.checkManage(user, lecNo);
        if (lecture.lecStat() == LecStat.CLOSED) {
            throw new BusinessException(ErrorCode.LECTURE_CLOSED);
        }
        if (!schedulePolicy.gradeOpen(lecture.semNo())) {
            throw new BusinessException(ErrorCode.GRADE_PERIOD_CLOSED);
        }
        if (lecture.grdStat() == GrdStat.CONFIRMED) {
            throw new BusinessException(ErrorCode.GRADE_CONFIRMED);
        }
        return lecture;
    }

    private void toEntering(LectureRow lecture) {
        if (lecture.grdStat() != GrdStat.ENTERING) {
            gradeEntryRepository.updateGrdStat(lecture.lecNo(), GrdStat.ENTERING);
        }
    }

    /** 등급제 과목은 반영 비율로 원점수 총점을 낸다(P/F 과목은 비율이 없다, LEC-03) */
    private static void requireWeights(LectureRow lecture) {
        if (lecture.midWt() == null) {
            throw new BusinessException(ErrorCode.WEIGHTS_NOT_SET);
        }
    }

    /**
     * 원점수 총점 = 각 원점수 × 반영 비율의 합 ÷ 100, 소수 그대로. 원점수가 소수 셋째 자리까지라 BigDecimal 로 십진 계산해 오차가 없다(double
     * 이면 89.999… 처럼 경계에서 등급이 바뀔 수 있음). 네 항목 중 하나라도 없거나 비율이 없으면 null.
     */
    private static BigDecimal rawTotal(LectureRow lecture, StudentRow s) {
        return rawTotal(lecture, s.midScr(), s.finScr(), s.hwScr(), s.attScr());
    }

    private static BigDecimal rawTotal(
            LectureRow lecture, BigDecimal mid, BigDecimal fin, BigDecimal hw, BigDecimal att) {
        if (mid == null || fin == null || hw == null || att == null || lecture.midWt() == null) {
            return null;
        }
        return mid.multiply(BigDecimal.valueOf(lecture.midWt()))
                .add(fin.multiply(BigDecimal.valueOf(lecture.finWt())))
                .add(hw.multiply(BigDecimal.valueOf(lecture.hwWt())))
                .add(att.multiply(BigDecimal.valueOf(lecture.attWt())))
                .movePointLeft(2);
    }

    /** 소수점 아래 버림(tot_scr, 절대평가 최종점수) */
    private static int floor(BigDecimal value) {
        return value.setScale(0, RoundingMode.FLOOR).intValueExact();
    }

    /** 입력됐는지: 등급제는 네 항목 원점수가 모두 있고, P/F 는 P·F 가 있음 */
    private static boolean entered(LectureRow lecture, StudentRow s) {
        if (lecture.evalType() == EvalType.PASS_FAIL) {
            return s.ltrGrd() != null;
        }
        return s.midScr() != null && s.finScr() != null && s.hwScr() != null && s.attScr() != null;
    }

    private static EvalMode evalMode(LectureRow lecture, int enrolled) {
        if (lecture.evalType() == EvalType.PASS_FAIL) {
            return EvalMode.PASS_FAIL;
        }
        boolean majorEnglish = MAJOR.contains(lecture.cmplType()) && lecture.engLec();
        if (enrolled < RELATIVE_MIN_ENROLLED || majorEnglish || lecture.relGrdExempt()) {
            return EvalMode.ABSOLUTE;
        }
        return EvalMode.RELATIVE;
    }

    /** 등급 인원 한도: A 40%·A+B 80%, 교직은 A 50%·A+B 수강인원. 소수점 아래 버림 */
    private static GradeBands bands(LectureRow lecture, int enrolled) {
        if (lecture.cmplType() == CmplType.TEACHING) {
            return new GradeBands(enrolled, enrolled * 50 / 100, enrolled, lecture.aCnt(), lecture.abCnt());
        }
        return new GradeBands(enrolled, enrolled * 40 / 100, enrolled * 80 / 100, lecture.aCnt(), lecture.abCnt());
    }

    private GradeSheetResponse reload(int lecNo) {
        LectureRow lecture = gradeEntryRepository.findLecture(lecNo).orElseThrow();
        return toSheet(lecture, gradeEntryRepository.findStudents(lecNo));
    }

    private GradeSheetResponse toSheet(LectureRow lecture, List<StudentRow> students) {
        EvalMode mode = evalMode(lecture, students.size());
        boolean editable = lecture.lecStat() == LecStat.OPEN
                && lecture.grdStat() != GrdStat.CONFIRMED
                && schedulePolicy.gradeOpen(lecture.semNo());
        GradeWeights weights = lecture.midWt() == null
                ? null
                : new GradeWeights(lecture.midWt(), lecture.finWt(), lecture.hwWt(), lecture.attWt());
        GradeBands bands = mode == EvalMode.RELATIVE ? bands(lecture, students.size()) : null;
        List<GradeSheetStudent> rows = students.stream()
                .map(s -> {
                    BigDecimal raw = rawTotal(lecture, s);
                    return new GradeSheetStudent(
                            s.stdNo(),
                            s.usrNm(),
                            s.retake(),
                            display(s.midScr()),
                            display(s.finScr()),
                            display(s.hwScr()),
                            display(s.attScr()),
                            raw == null ? null : floor(raw),
                            s.fnlScr(),
                            s.ltrGrd(),
                            mode == EvalMode.PASS_FAIL
                                    ? null
                                    : LetterGrade.of(s.ltrGrd())
                                            .map(LetterGrade::point)
                                            .orElse(null),
                            entered(lecture, s));
                })
                .toList();
        return new GradeSheetResponse(
                lecture.lecNo(),
                lecture.crsCd(),
                lecture.crsNm(),
                lecture.sectNo(),
                lecture.grdStat(),
                editable,
                mode,
                weights,
                bands,
                rows);
    }

    /** 응답용 점수 표기: DB 의 92.500 → 92.5, 99.000 → 99. 값은 그대로다 */
    private static BigDecimal display(BigDecimal score) {
        if (score == null) {
            return null;
        }
        BigDecimal stripped = score.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
    }
}
