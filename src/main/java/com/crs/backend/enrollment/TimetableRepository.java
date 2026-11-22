package com.crs.backend.enrollment;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 시간표(REG-04)의 학기 확인 SQL. 강의 목록은 {@link EnrollmentRepository#findEnrolled} 를 씁니다. */
@Repository
public class TimetableRepository {

    private final JdbcTemplate jdbc;

    public TimetableRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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
