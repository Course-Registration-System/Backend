package com.crs.backend.enrollment;

import com.crs.backend.lecture.ClsType;
import com.crs.backend.lecture.Dow;
import com.crs.backend.lecture.dto.TimeSlotResponse;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

/** 장바구니·수강신청 목록에 붙일 강의시간을 강의번호별로 읽습니다. */
final class TimeSlots {

    private TimeSlots() {}

    /**
     * from 은 lecture_time_slot t 를 포함하는 FROM 절, where 는 조건입니다. 둘 다 이 패키지 안의 고정 문자열만 넘깁니다.
     */
    static Map<Integer, List<TimeSlotResponse>> load(JdbcTemplate jdbc, String from, String where, Object... args) {
        String sql = """
                SELECT t.lec_no, t.dow, t.start_tm, t.end_tm, t.cls_type, t.room_cd, r.room_nm
                  FROM %s
                  LEFT JOIN classroom r ON r.room_cd = t.room_cd
                 WHERE %s
                 ORDER BY FIELD(t.dow, 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'), t.start_tm
                """.formatted(from, where);
        Map<Integer, List<TimeSlotResponse>> slots = new HashMap<>();
        jdbc.query(
                sql,
                rs -> {
                    slots.computeIfAbsent(rs.getInt("lec_no"), key -> new ArrayList<>())
                            .add(new TimeSlotResponse(
                                    Dow.valueOf(rs.getString("dow")),
                                    rs.getObject("start_tm", LocalTime.class),
                                    rs.getObject("end_tm", LocalTime.class),
                                    ClsType.valueOf(rs.getString("cls_type")),
                                    rs.getString("room_cd"),
                                    rs.getString("room_nm")));
                },
                args);
        return slots;
    }
}
