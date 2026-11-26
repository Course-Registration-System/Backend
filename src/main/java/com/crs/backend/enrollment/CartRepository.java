package com.crs.backend.enrollment;

import com.crs.backend.enrollment.dto.CartItemResponse;
import com.crs.backend.lecture.LecStat;
import com.crs.backend.lecture.dto.TimeSlotResponse;
import com.crs.backend.user.StdStat;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 장바구니(REG-02)의 SQL. 강의·과목·학기·학생은 읽기만 합니다. */
@Repository
public class CartRepository {

    /** 담기 전에 확인할 강의 상태. */
    public record LectureState(int semNo, LecStat lecStat) {}

    private final JdbcTemplate jdbc;

    public CartRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---- 조회

    /** 담은 시각 순입니다. 학생이 순서를 정하는 기능은 두지 않습니다(REG-02). */
    public List<CartItemResponse> findItems(String stdNo, int semNo) {
        Map<Integer, List<TimeSlotResponse>> slots = findSlots(stdNo, semNo);
        return jdbc.query(
                """
                SELECT l.lec_no, l.crs_cd, c.crs_nm, l.sect_no, c.crdt, c.cmpl_type, u.usr_nm AS prof_nm,
                       l.cap, l.enrl_cnt,
                       (SELECT COUNT(*) FROM course_cart o WHERE o.lec_no = l.lec_no) AS cart_cnt
                  FROM course_cart k
                  JOIN lecture l ON l.lec_no = k.lec_no
                  JOIN course c ON c.crs_cd = l.crs_cd
                  JOIN user_account u ON u.usr_no = l.prof_no
                 WHERE k.std_no = ? AND l.sem_no = ?
                 ORDER BY k.reg_dtm, k.lec_no
                """,
                (rs, rowNum) -> new CartItemResponse(
                        rs.getInt("lec_no"),
                        rs.getString("crs_cd"),
                        rs.getString("crs_nm"),
                        rs.getInt("sect_no"),
                        rs.getBigDecimal("crdt"),
                        rs.getString("cmpl_type"),
                        rs.getString("prof_nm"),
                        rs.getInt("cap"),
                        rs.getInt("enrl_cnt"),
                        rs.getInt("cart_cnt"),
                        slots.getOrDefault(rs.getInt("lec_no"), List.of())),
                stdNo,
                semNo);
    }

    /** 담은 학점 합. 수강신청(REG-03)의 최대학점 비교와 같게 채플과 최대학점 제외 과목은 넣지 않습니다. */
    public BigDecimal sumCredits(String stdNo, int semNo) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(c.crdt), 0)
                  FROM course_cart k
                  JOIN lecture l ON l.lec_no = k.lec_no
                  JOIN course c ON c.crs_cd = l.crs_cd
                 WHERE k.std_no = ? AND l.sem_no = ?
                   AND c.cmpl_type <> 'CHAPEL' AND c.max_crdt_exmp_yn = 'N'
                """, BigDecimal.class, stdNo, semNo);
    }

    private Map<Integer, List<TimeSlotResponse>> findSlots(String stdNo, int semNo) {
        return TimeSlots.load(jdbc, """
                course_cart k
                  JOIN lecture l ON l.lec_no = k.lec_no
                  JOIN lecture_time_slot t ON t.lec_no = k.lec_no
                """, "k.std_no = ? AND l.sem_no = ?", stdNo, semNo);
    }

    public Optional<StdStat> findStdStat(String stdNo) {
        return jdbc.queryForList("SELECT std_stat FROM student WHERE std_no = ?", String.class, stdNo).stream()
                .map(StdStat::valueOf)
                .findFirst();
    }

    public boolean isSeasonal(int semNo) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT sem_type IN ('SUMMER', 'WINTER') FROM semester WHERE sem_no = ?", Boolean.class, semNo));
    }

    /**
     * 강의 행을 공유 잠금으로 읽습니다. 폐강은 같은 행을 FOR UPDATE 로 잠근 뒤 장바구니를 지우므로, 폐강 확인과 담기 사이에 폐강이
     * 끼어들어 폐강 강의가 장바구니에 남는 일이 없습니다.
     */
    public Optional<LectureState> lockLectureForShare(int lecNo) {
        return jdbc
                .query(
                        "SELECT sem_no, lec_stat FROM lecture WHERE lec_no = ? FOR SHARE",
                        (rs, rowNum) ->
                                new LectureState(rs.getInt("sem_no"), LecStat.valueOf(rs.getString("lec_stat"))),
                        lecNo)
                .stream()
                .findFirst();
    }

    // ---- 쓰기

    public void insert(String stdNo, int lecNo, LocalDateTime now) {
        jdbc.update("INSERT INTO course_cart (std_no, lec_no, reg_dtm) VALUES (?, ?, ?)", stdNo, lecNo, now);
    }

    /** 폐강: 그 강의를 모든 학생의 장바구니에서 뺍니다. */
    public void deleteByLecture(int lecNo) {
        jdbc.update("DELETE FROM course_cart WHERE lec_no = ?", lecNo);
    }

    /** 지운 건수를 돌려줍니다. */
    public int delete(String stdNo, int lecNo) {
        return jdbc.update("DELETE FROM course_cart WHERE std_no = ? AND lec_no = ?", stdNo, lecNo);
    }
}
