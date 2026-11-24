package com.crs.backend.support;

import java.sql.Connection;
import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * 시드 데이터의 시점과 오버레이. 테스트에서 {@code @TestPropertySource(properties = "crs.clock.fixed-at=" + SeedTime.T1)}
 * 로 앱의 현재 시각을 고르고, 그 시점의 데이터는 {@link #atT1(DataSource)} 처럼 테스트 트랜잭션 안에서 만듭니다. 각 시점에서
 * 확인할 사례는 docs/seed-data.md 에 있습니다.
 */
public final class SeedTime {

    private SeedTime() {}

    /** 2026-2 성적 입력 기간(기본 시드). */
    public static final String T0 = "2026-12-21T10:00:00";

    /** 2026-2 성적 입력이 끝난 뒤의 성적 확정 시각(GRD-02). 오버레이 없이 기본 시드 그대로. tools/seed/build_seed.py 의 TC 와 같습니다. */
    public static final String TC = "2026-12-28T10:00:00";

    /** 2027-1 2학년 수강신청일. {@link #atT1(DataSource)} 필요. */
    public static final String T1 = "2027-02-11T10:05:00";

    /** 2027-1 정정 기간. {@link #atT2(DataSource)} 필요. */
    public static final String T2 = "2027-03-04T11:00:00";

    /** 성적 마감: 2026-2·2026 겨울 확정, 진급·졸업·복학, 신입생 계정, 2027-1 최대학점. */
    public static final String S1 = "s1_grade_close";

    /** 신청일까지: 장바구니, 4·3학년 수강신청. */
    public static final String S2 = "s2_enroll_day";

    /** 정정 기간까지: 2·1학년 수강신청, 현재학기 2027-1로 전환. */
    public static final String S3 = "s3_adjust";

    /** 기본 시드를 T1 상태로 옮깁니다. */
    public static void atT1(DataSource dataSource) {
        apply(dataSource, S1, S2);
    }

    /** 기본 시드를 T2 상태로 옮깁니다. */
    public static void atT2(DataSource dataSource) {
        apply(dataSource, S1, S2, S3);
    }

    /** 오버레이를 순서대로 적용합니다. 테스트 트랜잭션 안에서 부르면 테스트가 끝날 때 함께 롤백됩니다. */
    public static void apply(DataSource dataSource, String... scenarios) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            for (String scenario : scenarios) {
                ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/scenario/" + scenario + ".sql"));
            }
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }
}
