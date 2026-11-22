package com.crs.backend.enrollment;

import com.crs.backend.common.BusinessException;
import com.crs.backend.common.ErrorCode;
import com.crs.backend.user.StdStat;

/** 장바구니(REG-02)와 수강신청(REG-03)이 같이 쓰는 학적상태 규칙. */
final class StudentStatusRule {

    private StudentStatusRule() {}

    /** 정규학기는 재학생만, 계절학기는 재학·휴학생. 졸업생은 안 됩니다. */
    static void check(StdStat stat, boolean seasonal) {
        boolean allowed = stat == StdStat.ATTENDING || (stat == StdStat.ON_LEAVE && seasonal);
        if (!allowed) {
            throw new BusinessException(ErrorCode.STUDENT_STATUS_NOT_ALLOWED);
        }
    }
}
