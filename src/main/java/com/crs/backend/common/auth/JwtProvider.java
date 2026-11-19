package com.crs.backend.common.auth;

import com.crs.backend.common.BusinessException;
import com.crs.backend.common.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 로그인 토큰(JWT)을 만들고 검증합니다. 토큰에는 사용자번호와 역할만 담습니다. */
@Component
public class JwtProvider {

    private static final String ROLE = "role";

    private final SecretKey key;
    private final Duration validity;

    public JwtProvider(@Value("${crs.jwt.secret}") String secret, @Value("${crs.jwt.valid-hours}") long validHours) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.validity = Duration.ofHours(validHours);
    }

    public String issue(CurrentUser user) {
        // 시연용 앱 시계(Clock)가 아니라 실제 시각을 쓴다. 시계를 옮겨도 토큰 만료가 따라 움직이면 안 되기 때문이다.
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.usrNo())
                .claim(ROLE, user.role().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(validity)))
                .signWith(key)
                .compact();
    }

    /** 위조됐거나 만료된 토큰이면 UNAUTHENTICATED 로 거절합니다. */
    public CurrentUser parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return new CurrentUser(claims.getSubject(), Role.valueOf(claims.get(ROLE, String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
    }
}
