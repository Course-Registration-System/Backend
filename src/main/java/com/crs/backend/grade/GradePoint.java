package com.crs.backend.grade;

/**
 * 등급 → 평점 환산(업무분석서 등급표, 4.5 만점). 평점을 계산하는 모든 SQL 이 이 식을 가져다 씁니다. 여러 곳에 따로 쓰면 한쪽만
 * 고쳐지는 일이 생기기 때문입니다(D10).
 */
public final class GradePoint {

    private GradePoint() {}

    /** 등급 컬럼을 평점으로 바꾸는 SQL 식. P 와 NULL 은 0 이므로, 평점을 낼 때는 등급제 과목만 골라서 써야 합니다. */
    public static String sql(String gradeColumn) {
        return "CASE " + gradeColumn
                + " WHEN 'A+' THEN 4.5 WHEN 'A0' THEN 4.3 WHEN 'A-' THEN 4.0"
                + " WHEN 'B+' THEN 3.5 WHEN 'B0' THEN 3.3 WHEN 'B-' THEN 3.0"
                + " WHEN 'C+' THEN 2.5 WHEN 'C0' THEN 2.3 WHEN 'C-' THEN 2.0"
                + " WHEN 'D+' THEN 1.5 WHEN 'D0' THEN 1.3 WHEN 'D-' THEN 1.0"
                + " ELSE 0 END";
    }
}
