package com.crs.backend.grade;

import java.util.Arrays;
import java.util.Optional;

/**
 * 등급제 과목의 등급 기준표(업무기술서 GRD-01, 코드에 고정). 높은 등급부터 선언하므로 선언 순서가 곧 등급 순서입니다. P/F 과목의 P·F 는 여기에
 * 없습니다(F 는 등급제 F 와 같은 문자지만 평점을 매기지 않습니다).
 *
 * <p>평점은 {@link GradePoint#sql} 과 값이 같아야 합니다(테스트로 확인).
 */
public enum LetterGrade {
    A_PLUS("A+", 97, 4.5),
    A_ZERO("A0", 94, 4.3),
    A_MINUS("A-", 90, 4.0),
    B_PLUS("B+", 87, 3.5),
    B_ZERO("B0", 84, 3.3),
    B_MINUS("B-", 80, 3.0),
    C_PLUS("C+", 77, 2.5),
    C_ZERO("C0", 74, 2.3),
    C_MINUS("C-", 70, 2.0),
    D_PLUS("D+", 67, 1.5),
    D_ZERO("D0", 64, 1.3),
    D_MINUS("D-", 60, 1.0),
    F("F", 0, 0.0);

    private final String code;
    private final int minScore;
    private final double point;

    LetterGrade(String code, int minScore, double point) {
        this.code = code;
        this.minScore = minScore;
        this.point = point;
    }

    /** DB·응답에 쓰는 표기(A+, A0, …, F). */
    public String code() {
        return code;
    }

    public double point() {
        return point;
    }

    /** A등급(A+·A0·A-). 상대평가 A 비율에 셉니다. */
    public boolean isA() {
        return compareTo(A_MINUS) <= 0;
    }

    /** B등급(B+·B0·B-). */
    public boolean isB() {
        return compareTo(A_MINUS) > 0 && compareTo(B_MINUS) <= 0;
    }

    /** 총점(0~100)에 해당하는 기준표 등급. */
    public static LetterGrade fromScore(int totalScore) {
        for (LetterGrade grade : values()) {
            if (totalScore >= grade.minScore) {
                return grade;
            }
        }
        return F;
    }

    /** 기준표 등급. 재수강이면 A- 를 넘지 않습니다. */
    public static LetterGrade fromScore(int totalScore, boolean retake) {
        LetterGrade grade = fromScore(totalScore);
        return retake && grade.compareTo(A_MINUS) < 0 ? A_MINUS : grade;
    }

    /** 표기(A+ 등)로 찾습니다. 없는 표기면 비어 있습니다. */
    public static Optional<LetterGrade> of(String code) {
        return Arrays.stream(values()).filter(g -> g.code.equals(code)).findFirst();
    }
}
