package com.crs.backend.user;

import com.crs.backend.common.auth.Role;
import java.util.regex.Pattern;

/**
 * 사용자번호(로그인ID) 형식(USR-01·02). 숫자만 쓰고 자리수로 신분을 나눕니다: 관리자(정보화팀) 5자리, 교수 6자리, 학부생 8자리, 대학원생
 * 10자리. DB 는 V5 의 ck_user_account_usr_no 가 같은 규칙을 검사합니다.
 *
 * <p>대학원생도 학생(student) 행으로 두지만 조교로만 씁니다. 장바구니·수강신청·최대학점 산정·진급·졸업 대상이 아닙니다.
 */
public final class UserNo {

    public static final int GRADUATE_LENGTH = 10;

    /** SQL 에서 학부생만 고를 때 쓰는 조건. 예: {@code UserNo.undergraduateSql("st.std_no")} */
    public static String undergraduateSql(String column) {
        return "CHAR_LENGTH(" + column + ") <> " + GRADUATE_LENGTH;
    }

    private static final Pattern STUDENT = Pattern.compile("[0-9]{8}|[0-9]{10}");
    private static final Pattern PROFESSOR = Pattern.compile("[0-9]{6}");
    private static final Pattern ADMIN = Pattern.compile("[0-9]{5}");

    private UserNo() {}

    public static boolean matches(String usrNo, Role role) {
        Pattern pattern = switch (role) {
            case STUDENT -> STUDENT;
            case PROFESSOR -> PROFESSOR;
            case ADMIN -> ADMIN;
        };
        return usrNo != null && pattern.matcher(usrNo).matches();
    }

    public static boolean isGraduate(String stdNo) {
        return stdNo != null && stdNo.length() == GRADUATE_LENGTH;
    }
}
