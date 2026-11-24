package com.crs.backend.user;

/**
 * 사용자번호(로그인ID)는 숫자만 쓰고 자리수로 신분을 나눕니다: 관리자(정보화팀) 5자리, 교수 6자리, 학부생 8자리, 대학원생 10자리(V6).
 *
 * <p>대학원생도 학생(student) 행으로 두지만 조교로만 씁니다. 장바구니·수강신청·최대학점 산정·진급·졸업 대상이 아닙니다.
 */
public final class UserNo {

    private UserNo() {}

    public static boolean isGraduate(String stdNo) {
        return stdNo.length() == 10;
    }
}
