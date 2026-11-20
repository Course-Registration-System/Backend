package com.crs.backend.grade;

import java.util.Optional;
import org.springframework.stereotype.Service;

/** {@link GradeQuery} 의 구현. 성적이 확정된 강의만 이수 이력으로 봅니다(입력중·제출 상태의 점수는 바뀔 수 있으므로). */
@Service
public class ConfirmedGradeQuery implements GradeQuery {

    private final GradeQueryRepository repository;

    public ConfirmedGradeQuery(GradeQueryRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<String> latestConfirmedGrade(String stdNo, String crsCd) {
        return repository.findLatestConfirmedGrade(stdNo, crsCd);
    }
}
