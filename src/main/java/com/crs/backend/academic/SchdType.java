package com.crs.backend.academic;

/** 일정유형. 선언 순서가 한 학기 안의 진행 순서입니다(장바구니 → 수강신청 → 정정 → 성적입력). */
public enum SchdType {
    CART,
    ENROLL,
    ADJUST,
    GRADE
}
