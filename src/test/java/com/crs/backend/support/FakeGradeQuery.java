package com.crs.backend.support;

import com.crs.backend.grade.GradeQuery;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 테스트용 GradeQuery. 성적 모듈의 실제 구현이 들어오면 지우고, 수강신청 테스트는 시드 성적으로 바꿉니다.
 *
 * <p>정한 성적만 돌려줍니다. 테스트가 끝나면 reset() 으로 비웁니다.
 */
@Component
public class FakeGradeQuery implements GradeQuery {

    private final Map<String, String> grades = new ConcurrentHashMap<>();

    public void put(String stdNo, String crsCd, String grade) {
        grades.put(stdNo + "/" + crsCd, grade);
    }

    public void reset() {
        grades.clear();
    }

    @Override
    public Optional<String> latestConfirmedGrade(String stdNo, String crsCd) {
        return Optional.ofNullable(grades.get(stdNo + "/" + crsCd));
    }
}
