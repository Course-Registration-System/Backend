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
                .map(semRows -> {
                    List<GradeRow> grades = semRows.stream().map(Row::grade).toList();
                    Row first = semRows.get(0);
                    return new SemesterGrades(
                            first.semNo(), first.yr(), first.semType(), grades, gpa(grades, g -> true));
                })
                .toList();

        List<GradeRow> all = rows.stream().map(Row::grade).toList();
        BigDecimal earned = all.stream()
                .filter(g -> g.counted() && !"F".equals(g.ltrGrd()) && !"CHAPEL".equals(g.cmplType()))
                .map(GradeRow::crdt)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new GradeReportResponse(semesters, gpa(all, GradeRow::counted), earned);
    }

    private static BigDecimal gpa(List<GradeRow> grades, Predicate<GradeRow> include) {
        BigDecimal credits = BigDecimal.ZERO;
        BigDecimal points = BigDecimal.ZERO;
        for (GradeRow g : grades) {
            if (g.gradePoint() != null && include.test(g)) {
                credits = credits.add(g.crdt());
                points = points.add(g.crdt().multiply(g.gradePoint()));
            }
        }
        return credits.signum() == 0 ? null : points.divide(credits, 2, RoundingMode.HALF_UP);
    }
}
