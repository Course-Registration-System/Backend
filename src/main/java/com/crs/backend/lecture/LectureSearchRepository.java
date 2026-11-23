package com.crs.backend.lecture;

import com.crs.backend.lecture.dto.LectureSearchItem;
import com.crs.backend.lecture.dto.LectureSearchSlot;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 강의 검색(REG-01)의 SQL. 강의·과목·교수·강의시간·강의실은 다른 업무의 테이블이라 읽기만 합니다.
 *
 * <p>한 강의에 강의시간이 여러 칸이라, 강의 목록(한 페이지)과 그 강의들의 강의시간을 두 번 나눠 읽습니다. JOIN 한 번으로 읽으면 페이지 나누기가
 * 강의가 아니라 칸 단위로 잘립니다.
 */
@Repository
public class LectureSearchRepository {

    private static final String FROM_WHERE = """
              FROM lecture l
              JOIN course c ON c.crs_cd = l.crs_cd
              JOIN user_account u ON u.usr_no = l.prof_no
             WHERE l.sem_no = :semNo AND l.lec_stat = 'OPEN'
               AND (:deptNo IS NULL OR c.dept_no = :deptNo)
               AND (:yrLvl IS NULL OR c.rcmd_yr_lvl = :yrLvl OR c.rcmd_yr_lvl IS NULL)
               AND (:cmplType IS NULL OR c.cmpl_type = :cmplType)
               AND (:crsNm IS NULL OR c.crs_nm LIKE :crsNm)
               AND (:profNm IS NULL OR u.usr_nm LIKE :profNm)
            """;

    private final NamedParameterJdbcTemplate namedJdbc;

    public LectureSearchRepository(NamedParameterJdbcTemplate namedJdbc) {
        this.namedJdbc = namedJdbc;
    }

    public long count(LectureSearchCondition condition) {
        Long count = namedJdbc.queryForObject("SELECT COUNT(*)" + FROM_WHERE, params(condition), Long.class);
        return count == null ? 0 : count;
    }

    /** 과목코드·분반 순으로 한 페이지. 강의시간은 비어 있고 {@link #findSlots(List)} 로 채웁니다. */
    public List<LectureRow> findPage(LectureSearchCondition condition, int page, int size) {
        var params = params(condition).addValue("limit", size).addValue("offset", page * size);
        return namedJdbc.query(
                """
                SELECT l.lec_no, l.crs_cd, c.crs_nm, l.sect_no, c.crdt, c.cmpl_type, c.eval_type, c.rcmd_yr_lvl,
                       u.usr_nm AS prof_nm, l.eng_lec_yn, l.cap, l.enrl_cnt, l.plan_file
                """ + FROM_WHERE + " ORDER BY l.crs_cd, l.sect_no LIMIT :limit OFFSET :offset",
                params,
                (rs, rowNum) -> new LectureRow(
                        rs.getInt("lec_no"),
                        rs.getString("crs_cd"),
                        rs.getString("crs_nm"),
                        rs.getInt("sect_no"),
                        rs.getBigDecimal("crdt"),
                        rs.getString("cmpl_type"),
                        rs.getString("eval_type"),
                        rs.getObject("rcmd_yr_lvl", Integer.class),
                        rs.getString("prof_nm"),
                        "Y".equals(rs.getString("eng_lec_yn")),
                        rs.getInt("cap"),
                        rs.getInt("enrl_cnt"),
                        rs.getString("plan_file")));
    }

    /** 강의번호별 강의시간(요일·시작시각 순). */
    public Map<Integer, List<LectureSearchSlot>> findSlots(List<Integer> lecNos) {
        Map<Integer, List<LectureSearchSlot>> slots = new HashMap<>();
        if (lecNos.isEmpty()) {
            return slots;
        }
        namedJdbc.query("""
                SELECT t.lec_no, t.dow, t.start_tm, t.end_tm, t.cls_type, t.room_cd, r.room_nm
                  FROM lecture_time_slot t
                  LEFT JOIN classroom r ON r.room_cd = t.room_cd
                 WHERE t.lec_no IN (:lecNos)
                 ORDER BY FIELD(t.dow, 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'), t.start_tm
                """, new MapSqlParameterSource("lecNos", lecNos), rs -> {
            ClsType clsType = ClsType.valueOf(rs.getString("cls_type"));
            String roomNm = rs.getString("room_nm");
            slots.computeIfAbsent(rs.getInt("lec_no"), key -> new ArrayList<>())
                    .add(new LectureSearchSlot(
                            Dow.valueOf(rs.getString("dow")),
                            rs.getObject("start_tm", LocalTime.class),
                            rs.getObject("end_tm", LocalTime.class),
                            clsType,
                            rs.getString("room_cd"),
                            clsType.place(roomNm)));
        });
        return slots;
    }

    private static MapSqlParameterSource params(LectureSearchCondition condition) {
        return new MapSqlParameterSource()
                .addValue("semNo", condition.semNo())
                .addValue("deptNo", condition.deptNo())
                .addValue("yrLvl", condition.yrLvl())
                .addValue("cmplType", condition.cmplType())
                .addValue("crsNm", contains(condition.crsNm()))
                .addValue("profNm", contains(condition.profNm()));
    }

    // 부분 검색. 사용자가 입력한 %·_ 는 글자 그대로 찾는다
    private static String contains(String keyword) {
        if (keyword == null) {
            return null;
        }
        String escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    /** 강의 한 줄(강의시간 제외). */
    public record LectureRow(
            int lecNo,
            String crsCd,
            String crsNm,
            int sectNo,
            java.math.BigDecimal crdt,
            String cmplType,
            String evalType,
            Integer rcmdYrLvl,
            String profNm,
            boolean engLec,
            int cap,
            int enrlCnt,
            String planFile) {

        public LectureSearchItem withSlots(List<LectureSearchSlot> slots) {
            int remaining = Math.max(0, cap - enrlCnt);
            return new LectureSearchItem(
                    lecNo,
                    crsCd,
                    crsNm,
                    sectNo,
                    crdt,
                    cmplType,
                    evalType,
                    rcmdYrLvl,
                    profNm,
                    engLec,
                    cap,
                    enrlCnt,
                    remaining,
                    remaining == 0,
                    planFile,
                    slots);
        }
    }
}
