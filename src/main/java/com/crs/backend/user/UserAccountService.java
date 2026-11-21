package com.crs.backend.user;

import com.crs.backend.common.BusinessException;
import com.crs.backend.common.ErrorCode;
import com.crs.backend.common.auth.Role;
import com.crs.backend.user.dto.PasswordResetRequest;
import com.crs.backend.user.dto.UserCreateRequest;
import com.crs.backend.user.dto.UserResponse;
import com.crs.backend.user.dto.UserUpdateRequest;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사용자 계정 관리(USR-02). 학생·교수 계정만 다루고, 계정은 지우지 않고 비활성화합니다. */
@Service
public class UserAccountService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    public UserAccountService(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<UserResponse> search(Role role, String keyword) {
        String trimmed = keyword == null || keyword.isBlank() ? null : keyword.strip();
        return userAccountRepository.search(role, trimmed);
    }

    @Transactional
    public UserResponse create(UserCreateRequest request) {
        if (request.role() == Role.ADMIN) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "관리자 계정은 등록할 수 없습니다.");
        }
        if (request.role() == Role.STUDENT && request.yrLvl() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "학생은 학년이 필요합니다.");
        }
        if (Passwords.tooLong(request.password())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "비밀번호가 너무 깁니다.");
        }
        if (userAccountRepository.existsByUsrNo(request.usrNo())) {
            throw new BusinessException(ErrorCode.DUPLICATE_USER_NO);
        }
        checkDepartment(request.deptNo());

        // 사용자(공통 정보)를 먼저 넣고 역할별 정보를 넣는다. 둘 중 하나라도 실패하면 함께 취소된다
        userAccountRepository.insertAccount(
                request.usrNo(),
                passwordEncoder.encode(request.password()),
                request.usrNm(),
                request.email(),
                request.role());
        if (request.role() == Role.STUDENT) {
            StdStat stdStat = request.stdStat() == null ? StdStat.ATTENDING : request.stdStat();
            userAccountRepository.insertStudent(request.usrNo(), request.deptNo(), request.yrLvl(), stdStat);
        } else {
            userAccountRepository.insertProfessor(request.usrNo(), request.deptNo(), request.office());
        }
        return getUser(request.usrNo());
    }

    @Transactional
    public UserResponse update(String usrNo, UserUpdateRequest request) {
        UserResponse current = getUser(usrNo);
        boolean student = current.role() == Role.STUDENT;
        if (student && (request.yrLvl() == null || request.stdStat() == null)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "학생은 학년과 학적상태가 필요합니다.");
        }
        checkDepartment(request.deptNo());

        userAccountRepository.updateAccount(usrNo, request.usrNm(), request.email());
        if (student) {
            userAccountRepository.updateStudent(usrNo, request.deptNo(), request.yrLvl(), request.stdStat());
        } else {
            userAccountRepository.updateProfessor(usrNo, request.deptNo(), request.office());
        }
        return getUser(usrNo);
    }

    /** 비밀번호를 잊은 학생·교수에게 관리자가 새 비밀번호를 정해 줍니다. */
    @Transactional
    public void resetPassword(String usrNo, PasswordResetRequest request) {
        getUser(usrNo);
        if (Passwords.tooLong(request.newPassword())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "비밀번호가 너무 깁니다.");
        }
        userAccountRepository.updatePassword(usrNo, passwordEncoder.encode(request.newPassword()));
    }

    @Transactional
    public UserResponse deactivate(String usrNo) {
        getUser(usrNo);
        userAccountRepository.deactivate(usrNo);
        return getUser(usrNo);
    }

    private UserResponse getUser(String usrNo) {
        return userAccountRepository.findUser(usrNo).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    private void checkDepartment(int deptNo) {
        if (!userAccountRepository.departmentExists(deptNo)) {
            throw new BusinessException(ErrorCode.DEPARTMENT_NOT_FOUND);
        }
    }
}
