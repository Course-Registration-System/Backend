package com.crs.backend.grade;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link GradeQuery} 의 구현. 성적이 확정된 강의만 이수 이력으로 봅니다(입력중·제출 상태의 점수는 바뀔 수 있으므로).
 *
 * <p>부르는 쪽 트랜잭션 밖에서 읽습니다(NOT_SUPPORTED). 이 조회가 트랜잭션의 첫 일반 SELECT 가 되면 MySQL 이 그 시점에 읽기 스냅숏을
 * 고정해, 뒤에서 행을 잠가도 다른 트랜잭션이 커밋한 행이 보이지 않기 때문입니다(SchedulePolicy 와 같은 이유).
 */
@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = true)
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
