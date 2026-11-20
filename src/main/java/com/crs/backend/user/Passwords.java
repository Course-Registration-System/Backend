package com.crs.backend.user;

import java.nio.charset.StandardCharsets;

/** BCrypt 는 72바이트까지만 받습니다. 한글은 한 글자가 3바이트라 글자 수가 아니라 바이트로 셉니다. */
final class Passwords {

    private static final int MAX_BYTES = 72;

    private Passwords() {}

    static boolean tooLong(String raw) {
        return raw.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES;
    }
}
