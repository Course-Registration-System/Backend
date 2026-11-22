package com.crs.backend.enrollment;

import com.crs.backend.enrollment.dto.TimetableLecture;
import com.crs.backend.lecture.dto.TimeSlotResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 시간표(REG-04)의 SQL. 읽기만 합니다. 신청상태가 '신청'이고 개설 상태인 강의만 봅니다. */
@Repository
public class TimetableRepository {

    private static final String ENROLLED_OPEN =
            "e.std_no = ? AND e.enrl_stat = 'ENROLLED' AND l.sem_no = ? AND l.lec_stat = 'OPEN'";

    private final JdbcTemplate jdbc;

    public TimetableRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<TimetableLecture> findLectures(String stdNo, int semNo) {
        Map<Integer, List<TimeSlotResponse>> slots = TimeSlots.load(jdbc, """
                enrollment e
                  JOIN lecture l ON l.lec_no = e.lec_no
                  JOIN lecture_time_slot t ON t.lec_no = e.lec_no
                """, ENROLLED_OPEN, stdNo, semNo);
        return jdbc.query(
                """
                SELECT l.lec_no, l.crs_cd, c.crs_nm, l.sect_no, c.crdt, c.cmpl_type, u.usr_nm AS prof_nm
                  FROM enrollment e
                  JOIN lecture l ON l.lec_no = e.lec_no
                  JOIN course c ON c.crs_cd = l.crs_cd
                  JOIN user_account u ON u.usr_no = l.prof_no
                 WHERE %s
                 ORDER BY l.lec_no
                """.formatted(ENROLLED_OPEN),
                (rs, rowNum) -> new TimetableLecture(
                        rs.getInt("lec_no"),
                        rs.getString("crs_cd"),
                        rs.getString("crs_nm"),
                        rs.getInt("sect_no"),
                        rs.getBigDecimal("crdt"),
                        rs.getString("cmpl_type"),
                        rs.getString("prof_nm"),
                        slots.getOrDefault(rs.getInt("lec_no"), List.of())),
                stdNo,
                semNo);
    }

    public boolean semesterExists(int semNo) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM semester WHERE sem_no = ?)", Boolean.class, semNo));
    }

    public Optional<Integer> findCurrentSemester() {
        return jdbc.queryForList("SELECT sem_no FROM semester WHERE cur_sem_yn = 'Y'", Integer.class).stream()
                .findFirst();
    }
}
