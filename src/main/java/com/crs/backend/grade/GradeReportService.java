package com.crs.backend.grade;

import com.crs.backend.common.BusinessException;
import com.crs.backend.common.ErrorCode;
import com.crs.backend.common.auth.CurrentUser;
import com.crs.backend.common.auth.Role;
import com.crs.backend.grade.GradeReportRepository.Row;
import com.crs.backend.grade.dto.GradeReportResponse;
import com.crs.backend.grade.dto.GradeRow;
import com.crs.backend.grade.dto.SemesterGrades;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 성적 조회(GRD-03). 평점과 학점은 저장하지 않고 조회할 때 계산합니다.
 *
 * <ul>
 *   <li>평점 = Σ 학점×평점 ÷ Σ 학점. 등급제 과목만 넣고(F 포함), P/F 과목(채플 포함)은 뺍니다.
 *   <li>학기 평점은 그 학기 성적 그대로 내고, 누적 평점에서만 재수강 이전 성적을 뺍니다(D2).
 *   <li>누적 이수 학점은 재수강 이전 성적, F, 채플을 빼고 P 는 넣습니다.
 *   <li>평점은 소수 둘째 자리에서 반올림해 보여 줍니다(D4).
 * </ul>
 */
@Service
public class GradeReportService {

    private final GradeReportRepository repository;

    public GradeReportService(GradeReportRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public GradeReportResponse report(CurrentUser user) {
        if (!user.is(Role.STUDENT)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        List<Row> rows = repository.findConfirmed(user.usrNo());

        Map<Integer, List<Row>> bySemester = new LinkedHashMap<>();
        rows.forEach(row -> bySemester
                .computeIfAbsent(row.semNo(), key -> new ArrayList<>())
                .add(row));
        List<SemesterGrades> semesters = bySemester.values().stream()
                .map(semRows -> new SemesterGrades(
                        semRows.get(0).semNo(),
                        semRows.get(0).yr(),
                        semRows.get(0).semType(),
                        semRows.stream().map(GradeReportService::toGradeRow).toList(),
                        gpa(semRows, row -> true)))
                .toList();

        BigDecimal earned = rows.stream()
                .filter(row -> row.counted() && !"F".equals(row.ltrGrd()) && !"CHAPEL".equals(row.cmplType()))
                .map(Row::crdt)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new GradeReportResponse(semesters, gpa(rows, Row::counted), earned);
    }

    private static BigDecimal gpa(List<Row> rows, Predicate<Row> include) {
        BigDecimal credits = BigDecimal.ZERO;
        BigDecimal points = BigDecimal.ZERO;
        for (Row row : rows) {
            if (row.gradePoint() != null && include.test(row)) {
                credits = credits.add(row.crdt());
                points = points.add(row.crdt().multiply(row.gradePoint()));
            }
        }
        return credits.signum() == 0 ? null : points.divide(credits, 2, RoundingMode.HALF_UP);
    }

    private static GradeRow toGradeRow(Row row) {
        return new GradeRow(
                row.lecNo(),
                row.crsCd(),
                row.crsNm(),
                row.crdt(),
                row.cmplType(),
                row.evalType(),
                row.ltrGrd(),
                row.gradePoint(),
                row.retake(),
                row.counted());
    }
}
