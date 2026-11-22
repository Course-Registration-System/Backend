package com.crs.backend.lecture;

import com.crs.backend.common.BusinessException;
import com.crs.backend.common.ErrorCode;
import com.crs.backend.common.auth.CurrentUser;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** {@link LectureAccess} 구현. 교수는 담당교수 여부, 학생은 조교배정 여부(졸업생, 그 강의 수강생 제외)로 판단합니다. */
@Component
public class TeachingLectureAccess implements LectureAccess {

    private final LectureTeachingRepository lectureTeachingRepository;

    public TeachingLectureAccess(LectureTeachingRepository lectureTeachingRepository) {
        this.lectureTeachingRepository = lectureTeachingRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canManage(CurrentUser user, int lecNo) {
        if (user == null) {
            return false;
        }
        return switch (user.role()) {
            case PROFESSOR -> lectureTeachingRepository.isProfessorOf(lecNo, user.usrNo());
            case STUDENT -> lectureTeachingRepository.isAssistantOf(lecNo, user.usrNo());
            // 관리자 기능은 /api/admin/** 에서 따로 권한을 본다
            case ADMIN -> false;
        };
    }

    @Override
    @Transactional(readOnly = true)
    public void checkManage(CurrentUser user, int lecNo) {
        if (!canManage(user, lecNo)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
