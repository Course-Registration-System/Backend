package com.crs.backend.lecture;

import com.crs.backend.common.BusinessException;
import com.crs.backend.common.ErrorCode;
import com.crs.backend.common.auth.CurrentUser;
import com.crs.backend.common.auth.Role;
import com.crs.backend.lecture.TeachingAssistantRepository.LectureOwnerRow;
import com.crs.backend.lecture.TeachingAssistantRepository.StudentStatusRow;
import com.crs.backend.lecture.dto.TeachingAssistantListResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 조교 관리(LEC-04). 담당 교수만 조교를 보고 추가·삭제합니다. 조교에게도 권한을 주는 LectureAccess 대신 담당 교수인지 직접 확인합니다(업무기술서
 * "조교 추가·삭제는 담당 교수만 할 수 있다").
 */
@Service
public class TeachingAssistantService {

    private static final String INACTIVE = "INACTIVE";
    private static final String GRADUATED = "GRADUATED";

    private final TeachingAssistantRepository teachingAssistantRepository;

    public TeachingAssistantService(TeachingAssistantRepository teachingAssistantRepository) {
        this.teachingAssistantRepository = teachingAssistantRepository;
    }

    @Transactional(readOnly = true)
    public TeachingAssistantListResponse list(CurrentUser user, int lecNo) {
        checkProfessor(user, lecNo);
        return listOf(lecNo);
    }

    @Transactional
    public TeachingAssistantListResponse add(CurrentUser user, int lecNo, String stdNo) {
        LectureOwnerRow lecture = checkProfessor(user, lecNo);

        // 폐강 강의에는 운영할 일이 없다(폐강은 정정 기간 종료 전까지만, ACA-03)
        if (lecture.lecStat() == LecStat.CLOSED) {
            throw new BusinessException(ErrorCode.LECTURE_CLOSED);
        }
        checkNotConfirmed(lecture);

        // 학생 확인: 활성 계정이고 졸업생이 아니어야 한다(졸업생은 로그인과 성적 조회만, USR-02). 휴학생은 된다
        StudentStatusRow student = teachingAssistantRepository
                .findStudentStatus(stdNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (INACTIVE.equals(student.acctStat()) || GRADUATED.equals(student.stdStat())) {
            throw new BusinessException(ErrorCode.ASSISTANT_NOT_ELIGIBLE);
        }

        // 그 강의의 수강생은 조교가 될 수 없다(자기 성적 처리 금지). 배정 뒤 수강생이 되는 경우는 LectureAccess 가 막는다
        if (teachingAssistantRepository.isEnrolled(lecNo, stdNo)) {
            throw new BusinessException(ErrorCode.ASSISTANT_IS_ENROLLED);
        }

        // 같은 학생을 한 강의에 두 번 배정할 수 없다
        if (teachingAssistantRepository.exists(lecNo, stdNo)) {
            throw new BusinessException(ErrorCode.DUPLICATE_ASSISTANT);
        }
        try {
            teachingAssistantRepository.insert(new TeachingAssistantAssignment(lecNo, stdNo));
        } catch (DuplicateKeyException e) {
            // 확인과 저장 사이에 같은 조교를 먼저 추가한 요청이 있는 경우
            throw new BusinessException(ErrorCode.DUPLICATE_ASSISTANT);
        }
        return listOf(lecNo);
    }

    @Transactional
    public TeachingAssistantListResponse remove(CurrentUser user, int lecNo, String stdNo) {
        checkNotConfirmed(checkProfessor(user, lecNo));
        if (teachingAssistantRepository.delete(lecNo, stdNo) == 0) {
            throw new BusinessException(ErrorCode.ASSISTANT_NOT_FOUND);
        }
        return listOf(lecNo);
    }

    // 없는 강의는 404, 그 강의의 담당 교수가 아니면 403(조교·다른 교수·학생·관리자)
    private LectureOwnerRow checkProfessor(CurrentUser user, int lecNo) {
        LectureOwnerRow lecture = teachingAssistantRepository
                .findLectureOwner(lecNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.LECTURE_NOT_FOUND));
        if (user.role() != Role.PROFESSOR || !lecture.profNo().equals(user.usrNo())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return lecture;
    }

    // 조교 일(강의계획서 LEC-03, 성적 입력 GRD-01)은 성적이 확정되면 끝난다. 지난 학기라도 확정 전이면 성적 입력이 남아 있어
    // 학기가 아니라 확정으로 판단한다. 확정 뒤에는 배정 기록이 그 강의의 조교 이력이라 삭제도 막는다
    private static void checkNotConfirmed(LectureOwnerRow lecture) {
        if (lecture.gradeConfirmed()) {
            throw new BusinessException(ErrorCode.ASSISTANT_LECTURE_FINISHED);
        }
    }

    private TeachingAssistantListResponse listOf(int lecNo) {
        return new TeachingAssistantListResponse(lecNo, teachingAssistantRepository.findAssistants(lecNo));
    }
}
