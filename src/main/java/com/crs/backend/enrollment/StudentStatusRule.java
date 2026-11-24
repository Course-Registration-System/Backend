package com.crs.backend.enrollment;

import com.crs.backend.common.BusinessException;
import com.crs.backend.common.ErrorCode;
import com.crs.backend.user.StdStat;
import com.crs.backend.user.UserNo;

/** 장바구니(REG-02)와 수강신청(REG-03, 관리자 추가 REG-05 포함)이 같이 쓰는 신청 자격 규칙. */
final class StudentStatusRule {

    private StudentStatusRule() {}

    /** 학부생만. 정규학기는 재학생만, 계절학기는 재학·휴학생. 졸업생은 안 됩니다. 대학원생(학번 10자리)은 조교로만 씁니다. */
    static void check(String stdNo, StdStat stat, boolean seasonal) {
        if (UserNo.isGraduate(stdNo)) {
            throw new BusinessException(ErrorCode.GRADUATE_STUDENT_NOT_ALLOWED);
        }
        boolean allowed = stat == StdStat.ATTENDING || (stat == StdStat.ON_LEAVE && seasonal);
        if (!allowed) {
            throw new BusinessException(ErrorCode.STUDENT_STATUS_NOT_ALLOWED);
        }
    }
}
