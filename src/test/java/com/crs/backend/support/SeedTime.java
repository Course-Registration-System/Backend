package com.crs.backend.support;

import java.sql.Connection;
import javax.sql.DataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * 시드 데이터의 시점. 테스트에서 {@code @TestPropertySource(properties = "crs.clock.fixed-at=" + SeedTime.T2)} 처럼
 * 앱의 현재 시각을 고릅니다. 각 시점에 열린 기간과 확인할 사례는 docs/seed-data.md 에 있습니다.
 */
public final class SeedTime {

    private SeedTime() {}

    /** 기준. 열린 기간 없음(기본값). */
    public static final String T0 = "2027-02-10T18:00:00";

    /** 2학년 수강신청 기간. */
    public static final String T1 = "2027-02-11T11:00:00";

    /** 정정 기간(전 학년). */
    public static final String T2 = "2027-03-04T11:00:00";

    /** 2027-1 성적 입력 기간. {@link #applyT3(DataSource)} 로 오버레이를 먼저 적용해야 합니다. */
    public static final String T3 = "2027-06-20T10:00:00";

    /**
     * 기본 시드를 T3 상태로 옮깁니다. 테스트 트랜잭션 안에서 부르면 테스트가 끝날 때 함께 롤백됩니다.
     */
    public static void applyT3(DataSource dataSource) {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/scenario/t3_grade.sql"));
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }
}
