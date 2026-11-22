package com.crs.backend.academic;

import com.crs.backend.academic.dto.MaxCreditItem;
import com.crs.backend.grade.GradePoint;
import com.crs.backend.user.StdStat;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 진급·졸업 처리와 학기별 최대학점 산정(ACA-01 ⑤). 학생마다 돌지 않고 문장 하나로 전원을 처리합니다.
 *
 * <p>이수 내역은 신청상태가 '신청'이고 성적이 확정된 강의만 봅니다. 학기 순서는 sem_no 가 아니라 연도와 학기구분으로 정하며, 산정
 * 대상 학기보다 앞선 학기만 셉니다(:targetOrd).
 */
@Repository
public class MaxCreditRepository {

    /** 학기 순서값. 연도×10 + (1학기 1, 여름 2, 2학기 3, 겨울 4). {@link SemType} 선언 순서와 같습니다. */
    static final String SEM_ORD = "(s.yr * 10 + FIELD(s.sem_type, 'FIRST', 'SUMMER', 'SECOND', 'WINTER'))";

    // 대상 학기 앞의 확정된 신청 내역. 같은 과목을 여러 번 들었으면 가장 최근 시도가 rn = 1
    private static final String HISTORY = """
            SELECT e.std_no, s.sem_no, s.sem_type, c.crdt, c.cmpl_type, g.ltr_grd,
                   ROW_NUMBER() OVER (PARTITION BY e.std_no, l.crs_cd ORDER BY %s DESC) AS rn
              FROM enrollment e
              JOIN lecture l ON l.lec_no = e.lec_no
              JOIN semester s ON s.sem_no = l.sem_no
              JOIN course c ON c.crs_cd = l.crs_cd
              LEFT JOIN grade g ON g.std_no = e.std_no AND g.lec_no = e.lec_no
             WHERE e.enrl_stat = 'ENROLLED'
               AND l.grd_stat = 'CONFIRMED'
               AND %s < :targetOrd
            """.formatted(SEM_ORD, SEM_ORD);

    // 학생별 다닌 정규학기 수, 이수 학점(채플 제외, F 제외, 최근 시도만), 채플 P 학기 수(계절학기 포함)
    private static final String PROGRESS = """
            SELECT std_no,
                   COUNT(DISTINCT CASE WHEN sem_type IN ('FIRST', 'SECOND') THEN sem_no END) AS regular_cnt,
                   COALESCE(SUM(CASE WHEN rn = 1 AND cmpl_type <> 'CHAPEL' AND ltr_grd <> 'F' THEN crdt END), 0)
                       AS earned_crdt,
                   COUNT(DISTINCT CASE WHEN cmpl_type = 'CHAPEL' AND ltr_grd = 'P' THEN sem_no END) AS chapel_cnt
              FROM (%s) h
             GROUP BY std_no
            """.formatted(HISTORY);

    // 최대학점을 정할 평점의 등급제 과목 학점 합과 학점×평점 합. 평점 = pts / crd
    // 마지막으로 다닌 정규학기(D1) 하나를 본다. 다닌 정규학기가 2개(2학년 1학기)면 1학년 두 학기를 합쳐 학점 가중 평균을 낸다
    private static final String LAST_REGULAR_GPA = """
            WITH reg AS (
                SELECT e.std_no, %s AS sem_ord, c.crdt, c.eval_type, g.ltr_grd
                  FROM enrollment e
                  JOIN lecture l ON l.lec_no = e.lec_no
                  JOIN semester s ON s.sem_no = l.sem_no
                  JOIN course c ON c.crs_cd = l.crs_cd
                  JOIN grade g ON g.std_no = e.std_no AND g.lec_no = e.lec_no
                 WHERE e.enrl_stat = 'ENROLLED'
                   AND l.grd_stat = 'CONFIRMED'
                   AND s.sem_type IN ('FIRST', 'SECOND')
                   AND %s < :targetOrd
            ), terms AS (
                SELECT std_no, MAX(sem_ord) AS last_ord, COUNT(DISTINCT sem_ord) AS term_cnt FROM reg GROUP BY std_no
            )
            SELECT r.std_no,
                   SUM(CASE WHEN r.eval_type = 'GRADE' THEN r.crdt END) AS crd,
                   SUM(CASE WHEN r.eval_type = 'GRADE' THEN r.crdt * (%s) END) AS pts
              FROM reg r
              JOIN terms t ON t.std_no = r.std_no
             WHERE t.term_cnt = 2 OR r.sem_ord = t.last_ord
             GROUP BY r.std_no
            """.formatted(SEM_ORD, SEM_ORD, GradePoint.sql("r.ltr_grd"));

    private final NamedParameterJdbcTemplate jdbc;

    public MaxCreditRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 대상 학기나 그 뒤 학기에 이미 시작된 일정이 있는지. 뒤 학기 일정이 시작됐으면 대상 학기는 지난 학기입니다. */
    public boolean anyScheduleStartedFrom(int targetOrd, LocalDateTime now) {
        Boolean started = jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM academic_schedule a JOIN semester s ON s.sem_no = a.sem_no WHERE "
                        + SEM_ORD + " >= :targetOrd AND a.start_dtm <= :now)",
                new MapSqlParameterSource("targetOrd", targetOrd).addValue("now", Timestamp.valueOf(now)),
                Boolean.class);
        return Boolean.TRUE.equals(started);
    }

    /**
     * 직전 정규학기부터 대상 학기 바로 앞까지(사이의 계절학기 포함) 신청한 학생이 있는데 성적이 확정되지 않은 개설 강의. 직전 정규학기가 없으면
     * 대상 학기 앞의 모든 학기를 봅니다. 예: "183 데이터베이스(1분반)"
     */
    public List<String> findUnconfirmedLectures(int targetOrd) {
        return jdbc.queryForList(
                """
                SELECT CONCAT(l.lec_no, ' ', c.crs_nm, '(', l.sect_no, '분반)')
                  FROM lecture l
                  JOIN course c ON c.crs_cd = l.crs_cd
                  JOIN semester s ON s.sem_no = l.sem_no
                 WHERE %s < :targetOrd
                   AND %s >= COALESCE((SELECT MAX(%s) FROM semester s
                                        WHERE s.sem_type IN ('FIRST', 'SECOND') AND %s < :targetOrd), 0)
                   AND l.lec_stat = 'OPEN'
                   AND l.grd_stat <> 'CONFIRMED'
                   AND EXISTS (SELECT 1 FROM enrollment e WHERE e.lec_no = l.lec_no AND e.enrl_stat = 'ENROLLED')
                 ORDER BY l.lec_no
                """.formatted(SEM_ORD, SEM_ORD, SEM_ORD, SEM_ORD),
                new MapSqlParameterSource("targetOrd", targetOrd),
                String.class);
    }

    /**
     * 학년 = min(4, 다닌 정규학기 수 ÷ 2 버림 + 1). 재학·휴학생 전원을 이력에서 다시 계산합니다(D25). 관리자가 USR-02 로 고친 학년도 이때
     * 이력 기준으로 다시 바뀝니다.
     */
    public void recalculateYearLevels(int targetOrd) {
        jdbc.update("""
                UPDATE student st
                  LEFT JOIN (%s) p ON p.std_no = st.std_no
                   SET st.yr_lvl = LEAST(4, FLOOR(COALESCE(p.regular_cnt, 0) / 2) + 1)
                 WHERE st.std_stat IN ('ATTENDING', 'ON_LEAVE')
                """.formatted(PROGRESS), new MapSqlParameterSource("targetOrd", targetOrd));
    }

    /**
     * 졸업 요건(D26)을 채운 재학생을 졸업 처리하고 그 수를 돌려줍니다. 요건: 다닌 정규학기 8개 이상, 채플을 뺀 이수 학점 130 이상, 채플 P 학기
     * 6개 이상. 졸업은 되돌리지 않습니다.
     */
    public int graduate(int targetOrd) {
        return jdbc.update("""
                UPDATE student st
                  JOIN (%s) p ON p.std_no = st.std_no
                   SET st.std_stat = 'GRADUATED'
                 WHERE st.std_stat = 'ATTENDING'
                   AND p.regular_cnt >= 8
                   AND p.earned_crdt >= 130
                   AND p.chapel_cnt >= 6
                """.formatted(PROGRESS), new MapSqlParameterSource("targetOrd", targetOrd));
    }

    /** 그 학기 최대학점을 모두 지웁니다. 다시 산정할 때 먼저 지워서, 이번 대상에서 빠진 학생(그사이 휴학·졸업)의 지난 값이 남지 않게 합니다. */
    public void deleteBySemester(int semNo) {
        jdbc.update(
                "DELETE FROM semester_maximum_credit WHERE sem_no = :semNo", new MapSqlParameterSource("semNo", semNo));
    }

    /**
     * 정규학기 최대학점. 재학생 전원, 마지막으로 다닌 정규학기 평점(2학년 1학기는 1학년 두 학기 평균)이 4.0 이상이면 21, 아니면 18(성적·평점
     * 없음 포함, D3). 평점은 나누지 않고 학점×평점 합 ≥ 4.0×학점 합으로 비교해 반올림 오차를 없앱니다(D4). 계절학기 성적은 보지 않습니다.
     */
    public void insertRegular(int semNo, int targetOrd) {
        jdbc.update(
                """
                INSERT INTO semester_maximum_credit (std_no, sem_no, max_crdt)
                SELECT st.std_no, :semNo,
                       CASE WHEN p.crd > 0 AND p.pts >= 4.0 * p.crd THEN 21 ELSE 18 END
                  FROM student st
                  LEFT JOIN (%s) p ON p.std_no = st.std_no
                 WHERE st.std_stat = 'ATTENDING'
                """.formatted(LAST_REGULAR_GPA),
                new MapSqlParameterSource("semNo", semNo).addValue("targetOrd", targetOrd));
    }

    /** 계절학기 최대학점. 재학생 6, 휴학생 3. */
    public void insertSeasonal(int semNo) {
        jdbc.update("""
                INSERT INTO semester_maximum_credit (std_no, sem_no, max_crdt)
                SELECT st.std_no, :semNo, CASE st.std_stat WHEN 'ATTENDING' THEN 6 ELSE 3 END
                  FROM student st
                 WHERE st.std_stat IN ('ATTENDING', 'ON_LEAVE')
                """, new MapSqlParameterSource("semNo", semNo));
    }

    /** 최대학점별 학생 수. 예: {18=12, 21=3} */
    public Map<Integer, Integer> countByMaxCredit(int semNo) {
        Map<Integer, Integer> counts = new TreeMap<>();
        jdbc.query(
                "SELECT max_crdt, COUNT(*) AS cnt FROM semester_maximum_credit WHERE sem_no = :semNo GROUP BY max_crdt",
                new MapSqlParameterSource("semNo", semNo),
                rs -> {
                    counts.put(rs.getInt("max_crdt"), rs.getInt("cnt"));
                });
        return counts;
    }

    public List<MaxCreditItem> findAll(int semNo) {
        return jdbc.query(
                """
                SELECT m.std_no, u.usr_nm, st.yr_lvl, st.std_stat, m.max_crdt
                  FROM semester_maximum_credit m
                  JOIN student st ON st.std_no = m.std_no
                  JOIN user_account u ON u.usr_no = m.std_no
                 WHERE m.sem_no = :semNo
                 ORDER BY m.std_no
                """,
                new MapSqlParameterSource("semNo", semNo),
                (rs, rowNum) -> new MaxCreditItem(
                        rs.getString("std_no"),
                        rs.getString("usr_nm"),
                        rs.getInt("yr_lvl"),
                        StdStat.valueOf(rs.getString("std_stat")),
                        rs.getInt("max_crdt")));
    }
}
