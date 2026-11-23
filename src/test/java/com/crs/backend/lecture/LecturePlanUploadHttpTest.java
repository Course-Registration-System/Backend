package com.crs.backend.lecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.crs.backend.common.auth.JwtProvider;
import com.crs.backend.common.auth.Role;
import com.crs.backend.support.Bearer;
import com.crs.backend.support.IntegrationTest;
import com.crs.backend.support.SeedFixtures.Account;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.FileSystemUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * 강의계획서 업로드를 실제 서버(Tomcat)로 확인합니다. MockMvc 는 multipart 를 직접 만들어 넣어서, PUT 요청의 multipart 해석과
 * 크기 한도(spring.servlet.multipart.max-file-size → 413)는 실제 서버에서만 확인할 수 있습니다. 서버 요청은 테스트 트랜잭션 밖에서
 * 커밋되므로 끝나면 계획서 경로를 시드 값으로 되돌립니다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = "crs.plan.dir=" + LecturePlanUploadHttpTest.PLAN_DIR)
class LecturePlanUploadHttpTest extends IntegrationTest {

    static final String PLAN_DIR = "build/test-plan-files-http";

    /** 시드: 2027-1 컴파일러(교수 100002). T0 에는 장바구니 시작 전이라 올릴 수 있다 */
    private static final int NEXT_TERM = 197;

    @LocalServerPort
    int port;

    @Autowired
    JwtProvider jwtProvider;

    @Autowired
    JdbcTemplate jdbc;

    private String seedPlanFile;

    @BeforeAll
    static void clearFiles() throws IOException {
        FileSystemUtils.deleteRecursively(Path.of(PLAN_DIR));
    }

    @AfterEach
    void restore() {
        if (seedPlanFile != null) {
            jdbc.update("UPDATE lecture SET plan_file = ? WHERE lec_no = ?", seedPlanFile, NEXT_TERM);
        }
    }

    @Test
    void 실제_서버에서_PUT_multipart로_올리고_10MB를_넘으면_413() {
        seedPlanFile = jdbc.queryForObject("SELECT plan_file FROM lecture WHERE lec_no = ?", String.class, NEXT_TERM);
        byte[] pdf = "%PDF-1.7\n실제 서버".getBytes(StandardCharsets.UTF_8);

        ResponseEntity<String> ok = upload(pdf);
        assertThat(ok.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(200));
        assertThat(ok.getBody()).contains("\"hasPlanFile\":true");

        byte[] big = new byte[10 * 1024 * 1024 + 1];
        System.arraycopy(pdf, 0, big, 0, pdf.length);
        ResponseEntity<String> tooLarge = upload(big);
        assertThat(tooLarge.getStatusCode()).isEqualTo(HttpStatusCode.valueOf(413));
        assertThat(tooLarge.getBody()).contains("PLAN_FILE_TOO_LARGE");
    }

    private ResponseEntity<String> upload(byte[] bytes) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        parts.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return "plan.pdf";
            }
        });
        return RestClient.create("http://localhost:" + port)
                .put()
                .uri("/api/teaching/lectures/{lecNo}/plan/file", NEXT_TERM)
                .header(HttpHeaders.AUTHORIZATION, Bearer.of(jwtProvider, Account.DEMO_PROFESSOR, Role.PROFESSOR))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts)
                .retrieve()
                .onStatus(status -> true, (request, response) -> {})
                .toEntity(String.class);
    }
}
