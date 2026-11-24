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
 *   <li>상대평가: 원점수 총점(소수 그대로) 석차로 A 구역(⌊n×40%⌋위까지)·B 구역(⌊n×80%⌋위까지)·나머지로 나누고, 구역마다 상한만 둔다. A 구역은
 *       버림 총점 그대로, B 구역은 89(B+), 나머지는 79(C+) 를 넘지 않는다. 점수를 올리지 않으므로 A 는 0명일 수 있다. 교직은 A 구역 ⌊n×50%⌋,
 *       나머지는 89
 *   <li>재수강은 최종점수 93, 등급 A- 상한
 * </ul>
 *
 * <p>교수·조교는 등급이나 인원을 넣지 않습니다. 등급을 바꾸려면 원점수를 고칩니다. 같은 원점수 총점인데 구역이 갈려 최종점수가 달라지면 저장하지
 * 않습니다(교수가 원점수로 순위를 정함). 근거: 업무분석서 GRD-01.
 */
@Service
public class GradeEntryService {

    private static final Set<CmplType> MAJOR = Set.of(CmplType.MAJOR_BASIC, CmplType.MAJOR_REQ, CmplType.MAJOR_ELEC);
    /** 상대평가 대상이 되는 최소 수강인원 */
    private static final int RELATIVE_MIN_ENROLLED = 10;

    /** 상대평가 B 구역 최종점수 상한(B+ 꼭대기) */
    private static final int B_ZONE_MAX = 89;
    /** 상대평가 나머지 구역 최종점수 상한(C+ 꼭대기) */
    private static final int OTHER_ZONE_MAX = 79;
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
     * 성적 저장. 넣은 학생만 바뀌며, 한 명이라도 실패하면 아무것도 저장하지 않습니다. 등급제 과목은 원점수만 받고 강의 전체를 다시 계산하며(같은
     * 원점수 총점인데 구역이 갈리면 저장하지 않음), P/F 과목은 점수 없이 P·F 만 받습니다. 저장하면 입력중이 됩니다.
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

    /** 제출. 모든 수강생이 입력돼 있어야 합니다. 최종점수·등급은 저장할 때와 같은 계산으로 다시 냅니다. */
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
                for (Result result : grade(lecture, students)) {
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
        for (Result result : grade(lecture, gradeEntryRepository.findStudents(lecture.lecNo()))) {
            gradeEntryRepository.saveResult(
                    lecture.lecNo(), result.stdNo(), result.totScr(), result.fnlScr(), result.ltrGrd());
        }
    }

    /**
     * 원점수가 다 있는 학생의 원점수 총점 → 최종점수 → 등급. 구역과 평가 방식은 수강인원 전체로 정합니다. 같은 원점수 총점인데 구역이 갈려
     * 최종점수가 달라지면 GRADE_TIE_AT_BOUNDARY 입니다.
     */
    private static List<Result> grade(LectureRow lecture, List<StudentRow> students) {
        int enrolled = students.size();
        List<Ranked> ranked = students.stream()
                .filter(s -> entered(lecture, s))
                .map(s -> new Ranked(s, rawTotal(lecture, s)))
                .sorted(Comparator.comparing(Ranked::rawTotal).reversed())
                .toList();

        int[] fnl = new int[ranked.size()];
        GradeBands bands = evalMode(lecture, enrolled) == EvalMode.RELATIVE ? bands(lecture, enrolled) : null;
        for (int i = 0; i < ranked.size(); i++) {
            fnl[i] = Math.min(floor(ranked.get(i).rawTotal()), zoneMax(bands, i));
        }
        checkNoSplitTie(ranked, fnl);

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

    /** 석차 rank(0부터)의 최종점수 상한. 절대평가(bands 가 null)와 A 구역은 상한이 없다(100). 교직은 A 구역 밖이 모두 89 */
    private static int zoneMax(GradeBands bands, int rank) {
        if (bands == null || rank < bands.aLimit()) {
            return 100;
        }
        if (bands.abLimit() == null || rank < bands.abLimit()) {
            return B_ZONE_MAX;
        }
        return OTHER_ZONE_MAX;
    }

    /** 원점수 총점이 같은 학생은 최종점수도 같아야 한다. 구역 경계에서 갈리면 교수가 원점수로 순위를 정해야 한다(재수강 상한 전 값으로 비교) */
    private static void checkNoSplitTie(List<Ranked> ranked, int[] fnl) {
        for (int i = 1; i < ranked.size(); i++) {
            BigDecimal boundary = ranked.get(i).rawTotal();
            if (boundary.compareTo(ranked.get(i - 1).rawTotal()) != 0 || fnl[i] == fnl[i - 1]) {
                continue;
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

    /** 상대평가 구역 끝 석차: A 40%·A+B 80%, 교직은 A 50% 만. 소수점 아래 버림 */
    private static GradeBands bands(LectureRow lecture, int enrolled) {
        if (lecture.cmplType() == CmplType.TEACHING) {
            return new GradeBands(enrolled, enrolled * 50 / 100, null);
        }
        return new GradeBands(enrolled, enrolled * 40 / 100, enrolled * 80 / 100);
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
