package com.crs.backend.academic;

/**
 * 정규학기 최대학점 규칙의 숫자(ACA-01, #51). 한 학년(다닌 정규학기 2개)에 {@link #YEAR_TOTAL}학점을 나눠 씁니다.
 *
 * <p>1학년은 학기 {@link #FIRST_YEAR_TERM}학점까지, 평점 가산이 없습니다. 2학년 이상은 학기 {@link #BASE_TERM}학점에 남긴 학점과 평점
 * 가산({@link #GPA_BONUS})을 더하고 {@link #TERM_CAP}학점을 넘지 않습니다.
 */
final class MaxCreditRule {

    /** 한 학년 합계. 1학년 두 번째 학기 최대학점 = 이 값 − 첫 학기 수강 학점. */
    static final int YEAR_TOTAL = 38;

    /** 1학년 학기 최대학점. 산정 결과가 없는 1학년의 기본값이기도 합니다. */
    static final int FIRST_YEAR_TERM = 22;

    /** 2학년 이상 학기 기본 학점. 산정 결과가 없는 2학년 이상의 기본값이기도 합니다. */
    static final int BASE_TERM = 19;

    /** 직전 정규학기 평점 4.0 이상이면 더하는 학점(2학년부터). */
    static final int GPA_BONUS = 3;

    /** 2학년 이상 학기 상한. */
    static final int TERM_CAP = 24;

    private MaxCreditRule() {}
}
