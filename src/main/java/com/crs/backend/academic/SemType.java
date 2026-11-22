package com.crs.backend.academic;

/** 학기구분. 1학기·2학기는 정규학기, 여름·겨울은 계절학기입니다. 선언 순서가 한 해 안의 순서입니다. */
public enum SemType {
    FIRST,
    SUMMER,
    SECOND,
    WINTER;

    public boolean regular() {
        return this == FIRST || this == SECOND;
    }
}
