package com.crs.backend.support;

import javax.sql.DataSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * 통합 테스트 기반 클래스. MySQL 8.4 컨테이너 하나를 테스트 실행 전체에서 같이 씁니다.
 *
 * <p>운영과 같은 순서로 준비합니다. 컨테이너 첫 기동 때 01-users.sh 가 계정을 만들고, 앱이 뜰 때 Flyway 가
 * migrator 계정으로 스키마와 권한을 적용합니다. 테스트 코드는 런타임 계정 app 으로 접속하므로 권한 밖의 SQL 은
 * 운영처럼 거부됩니다.
 */
@SpringBootTest
public abstract class IntegrationTest {

    private static final String MIGRATOR_PASSWORD = "migrator-test";
    private static final String APP_PASSWORD = "app-test";
    private static final String READONLY_PASSWORD = "readonly-test";

    private static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4")
            .withDatabaseName("crs")
            .withEnv("MIGRATOR_DB_PASSWORD", MIGRATOR_PASSWORD)
            .withEnv("APP_DB_PASSWORD", APP_PASSWORD)
            .withEnv("READONLY_DB_PASSWORD", READONLY_PASSWORD)
            .withEnv("TZ", "Asia/Seoul")
            .withCommand("--character-set-server=utf8mb4", "--collation-server=utf8mb4_0900_ai_ci")
            .withCopyFileToContainer(
                    MountableFile.forHostPath("docker/mysql/initdb/01-users.sh", 0755),
                    "/docker-entrypoint-initdb.d/01-users.sh");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "app");
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
        registry.add("spring.flyway.user", () -> "migrator");
        registry.add("spring.flyway.password", () -> MIGRATOR_PASSWORD);
    }

    /** 지정한 계정(root, app, readonly)으로 접속하는 DataSource. 권한별 동작을 확인할 때 씁니다. */
    protected static DataSource dataSourceAs(String user) {
        String password = switch (user) {
            case "root" -> MYSQL.getPassword();
            case "app" -> APP_PASSWORD;
            case "readonly" -> READONLY_PASSWORD;
            default -> throw new IllegalArgumentException(user);
        };
        return new DriverManagerDataSource(MYSQL.getJdbcUrl(), user, password);
    }
}
