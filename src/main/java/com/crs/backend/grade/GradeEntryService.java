package com.crs.backend.grade;

import com.crs.backend.academic.SchedulePolicy;
import com.crs.backend.common.BusinessException;
import com.crs.backend.common.ErrorCode;
import com.crs.backend.common.auth.CurrentUser;
import com.crs.backend.course.CmplType;
import com.crs.backend.course.EvalType;
import com.crs.backend.grade.GradeEntryRepository.LectureRow;
import com.crs.backend.grade.GradeEntryRepository.StudentRow;
import com.crs.backend.grade.dto.GradeInput;
import com.crs.backend.grade.dto.GradeRatio;
import com.crs.backend.grade.dto.GradeSaveRequest;
import com.crs.backend.grade.dto.GradeSheetResponse;
import com.crs.backend.grade.dto.GradeSheetStudent;
import com.crs.backend.grade.dto.GradeWeights;
import com.crs.backend.lecture.LecStat;
import com.crs.backend.lecture.LectureAccess;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 성적 입력·수정(GRD-01). 교수와 권한 있는 조교가 성적 입력 기간에 수강생 점수를 저장하면 총점·등급을 계산하고, 모두 입력한 뒤 제출합니다.
 * 성적표 조회는 기간과 관계없습니다.
 */
@Service
public class GradeEntryService {

    private static final String PASS = "P";
    private static final String FAIL = "F";
    private static final Set<CmplType> MAJOR = Set.of(CmplType.MAJOR_BASIC, CmplType.MAJOR_REQ, CmplType.MAJOR_ELEC);
    /** 상대평가 대상이 되는 최소 수강인원 */
    private static final int RELATIVE_MIN_ENROLLED = 10;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

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

    /** 점수 저장. 넣은 학생만 바뀌며, 한 명이라도 실패하면 아무것도 저장하지 않습니다. */
    @Transactional
    public GradeSheetResponse save(CurrentUser user, int lecNo, GradeSaveRequest request) {
        Set<String> seen = new HashSet<>();
        for (GradeInput input : request.grades()) {
            if (!seen.add(input.stdNo())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "같은 학번을 두 번 넣었습니다: " + input.stdNo());
            }
        }

        // ①② 강의를 잠그고 권한·기간·상태 확인
        LectureRow lecture = lockEditable(user, lecNo);
        if (lecture.evalType() == EvalType.GRADE && lecture.midWt() == null) {
            throw new BusinessException(ErrorCode.WEIGHTS_NOT_SET);
        }

        // ③ 수강생과 기존 점수. 평가 방식은 수강인원으로 정해진다
        List<StudentRow> students = gradeEntryRepository.findStudents(lecNo);
        Map<String, StudentRow> byStdNo =
                students.stream().collect(Collectors.toMap(StudentRow::stdNo, Function.identity()));
        EvalMode mode = evalMode(lecture, students.size());

        // ④~⑥ 모든 학생을 먼저 검사·계산하고, 다 통과하면 저장한다
        List<GradeToSave> toSave = new ArrayList<>();
        for (GradeInput input : request.grades()) {
            StudentRow student = byStdNo.get(input.stdNo());
            if (student == null) {
                throw new BusinessException(ErrorCode.NOT_ENROLLED_STUDENT, "이 강의의 수강생이 아닙니다: " + input.stdNo());
            }
            toSave.add(mode == EvalMode.PASS_FAIL ? passFail(input) : scored(lecture, mode, student, input));
        }
        for (GradeToSave g : toSave) {
            gradeEntryRepository.save(
                    lecNo, g.stdNo(), g.midScr(), g.finScr(), g.hwScr(), g.attScr(), g.totScr(), g.ltrGrd());
        }

        // ⑦ 미입력·제출 → 입력중(제출 뒤 고치면 다시 제출해야 한다)
        if (lecture.grdStat() == GrdStat.NOT_ENTERED || lecture.grdStat() == GrdStat.SUBMITTED) {
            gradeEntryRepository.updateGrdStat(lecNo, GrdStat.ENTERING);
        }
        return reload(lecNo);
    }

    /** 제출. 모든 수강생이 입력되고, 상대평가 강의는 등급 비율 안이어야 합니다. */
    @Transactional
    public GradeSheetResponse submit(CurrentUser user, int lecNo) {
        LectureRow lecture = lockEditable(user, lecNo);
        if (lecture.grdStat() == GrdStat.SUBMITTED) {
            throw new BusinessException(ErrorCode.GRADE_ALREADY_SUBMITTED);
        }

        // 수강생이 없는 강의는 입력중이 될 수 없으므로 미입력에서 바로 제출한다
        List<StudentRow> students = gradeEntryRepository.findStudents(lecNo);
        EvalMode mode = evalMode(lecture, students.size());
        if (!students.stream().allMatch(s -> entered(mode, s))) {
            throw new BusinessException(ErrorCode.GRADE_INCOMPLETE);
        }
        if (mode == EvalMode.RELATIVE && !withinRatio(ratio(lecture, students))) {
            throw new BusinessException(ErrorCode.GRADE_RATIO_EXCEEDED);
        }

        gradeEntryRepository.updateGrdStat(lecNo, GrdStat.SUBMITTED);
        return reload(lecNo);
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

    /** 저장할 학생 한 명의 값(검사·계산을 마친 것). */
    private record GradeToSave(
            String stdNo,
            BigDecimal midScr,
            BigDecimal finScr,
            BigDecimal hwScr,
            BigDecimal attScr,
            Integer totScr,
            String ltrGrd) {}

    // P/F 과목: 점수 없이 P 또는 F(비우면 미입력)
    private static GradeToSave passFail(GradeInput input) {
        boolean hasScore =
                input.midScr() != null || input.finScr() != null || input.hwScr() != null || input.attScr() != null;
        String grade = input.ltrGrd();
        if (hasScore || (grade != null && !PASS.equals(grade) && !FAIL.equals(grade))) {
            throw new BusinessException(ErrorCode.GRADE_INPUT_MISMATCH);
        }
        return new GradeToSave(input.stdNo(), null, null, null, null, null, grade);
    }

    // 등급제 과목: 네 항목이 다 있으면 총점·등급 계산. 등급 낮추기는 상대평가 강의에서 기준표 등급 이하로만
    private static GradeToSave scored(LectureRow lecture, EvalMode mode, StudentRow student, GradeInput input) {
        if (PASS.equals(input.ltrGrd())) {
            throw new BusinessException(ErrorCode.GRADE_INPUT_MISMATCH);
        }
        LetterGrade requested = null;
        if (input.ltrGrd() != null) {
            requested = LetterGrade.of(input.ltrGrd())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT, "없는 등급입니다: " + input.ltrGrd()));
        }

        BigDecimal mid = input.midScr();
        BigDecimal fin = input.finScr();
        BigDecimal hw = input.hwScr();
        BigDecimal att = input.attScr();
        Integer total = null;
        LetterGrade grade = null;
        if (mid != null && fin != null && hw != null && att != null) {
            total = totalScore(lecture, mid, fin, hw, att);
            grade = LetterGrade.fromScore(total, student.retake());
        }

        if (requested != null && requested != grade) {
            // 기준표 등급과 다르면 조정: 점수가 다 있고, 상대평가 강의이고, 기준표보다 낮아야 한다
            if (grade == null || mode != EvalMode.RELATIVE || requested.compareTo(grade) < 0) {
                throw new BusinessException(ErrorCode.GRADE_ADJUST_NOT_ALLOWED);
            }
            grade = requested;
        }

        return new GradeToSave(input.stdNo(), mid, fin, hw, att, total, grade == null ? null : grade.code());
    }

    /**
     * 총점 = 각 점수 × 반영 비율의 합 ÷ 100, 소수점 아래 버림. 점수가 소수 셋째 자리까지라 BigDecimal 로 십진 계산해 오차가 없다(double
     * 이면 89.999… 처럼 경계에서 등급이 바뀔 수 있음).
     */
    private static int totalScore(LectureRow lecture, BigDecimal mid, BigDecimal fin, BigDecimal hw, BigDecimal att) {
        BigDecimal weighted = mid.multiply(BigDecimal.valueOf(lecture.midWt()))
                .add(fin.multiply(BigDecimal.valueOf(lecture.finWt())))
                .add(hw.multiply(BigDecimal.valueOf(lecture.hwWt())))
                .add(att.multiply(BigDecimal.valueOf(lecture.attWt())));
        return weighted.divide(HUNDRED, 0, RoundingMode.FLOOR).intValueExact();
    }

    /** 응답용 점수 표기: DB 의 92.500 → 92.5, 99.000 → 99. 값은 그대로다 */
    private static BigDecimal display(BigDecimal score) {
        if (score == null) {
            return null;
        }
        BigDecimal stripped = score.stripTrailingZeros();
        return stripped.scale() < 0 ? stripped.setScale(0) : stripped;
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

    // 상대평가 인원 한도: A 40%·A+B 80%, 교직은 A 50% 만. 소수점 아래 버림
    private static GradeRatio ratio(LectureRow lecture, List<StudentRow> students) {
        int enrolled = students.size();
        List<LetterGrade> grades = students.stream()
                .map(StudentRow::ltrGrd)
                .flatMap(code -> LetterGrade.of(code).stream())
                .toList();
        int aCount = (int) grades.stream().filter(LetterGrade::isA).count();
        int abCount = (int) grades.stream().filter(g -> g.isA() || g.isB()).count();
        if (lecture.cmplType() == CmplType.TEACHING) {
            return new GradeRatio(enrolled, aCount, enrolled * 50 / 100, abCount, null);
        }
        return new GradeRatio(enrolled, aCount, enrolled * 40 / 100, abCount, enrolled * 80 / 100);
    }

    private static boolean withinRatio(GradeRatio ratio) {
        return ratio.aCount() <= ratio.aLimit() && (ratio.abLimit() == null || ratio.abCount() <= ratio.abLimit());
    }

    /** 입력을 마쳤는지: 등급제는 네 항목 모두, P/F 는 P·F */
    private static boolean entered(EvalMode mode, StudentRow s) {
        if (mode == EvalMode.PASS_FAIL) {
            return s.ltrGrd() != null;
        }
        return s.midScr() != null && s.finScr() != null && s.hwScr() != null && s.attScr() != null;
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
        GradeWeights weights = lecture.evalType() == EvalType.GRADE && lecture.midWt() != null
                ? new GradeWeights(lecture.midWt(), lecture.finWt(), lecture.hwWt(), lecture.attWt())
                : null;
        GradeRatio ratio = mode == EvalMode.RELATIVE ? ratio(lecture, students) : null;
        List<GradeSheetStudent> rows = students.stream()
                .map(s -> new GradeSheetStudent(
                        s.stdNo(),
                        s.usrNm(),
                        s.retake(),
                        display(s.midScr()),
                        display(s.finScr()),
                        display(s.hwScr()),
                        display(s.attScr()),
                        s.totScr(),
                        s.ltrGrd(),
                        mode == EvalMode.PASS_FAIL
                                ? null
                                : LetterGrade.of(s.ltrGrd())
                                        .map(LetterGrade::point)
                                        .orElse(null),
                        entered(mode, s)))
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
                ratio,
                rows);
    }
}
