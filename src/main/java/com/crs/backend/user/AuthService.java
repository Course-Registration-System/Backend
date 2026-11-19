package com.crs.backend.user;

import com.crs.backend.common.BusinessException;
import com.crs.backend.common.ErrorCode;
import com.crs.backend.common.auth.CurrentUser;
import com.crs.backend.common.auth.JwtProvider;
import com.crs.backend.common.auth.Role;
import com.crs.backend.user.dto.LoginRequest;
import com.crs.backend.user.dto.LoginResponse;
import com.crs.backend.user.dto.MyInfoResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** 로그인(USR-01). */
@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public AuthService(
            UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder, JwtProvider jwtProvider) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProvider = jwtProvider;
    }

    public LoginResponse login(LoginRequest request) {
        // 계정이 없을 때와 비밀번호가 틀릴 때 같은 오류로 처리
        UserAccount account = userAccountRepository
                .findByUsrNo(request.usrNo())
                .filter(found -> !Passwords.tooLong(request.password())
                        && passwordEncoder.matches(request.password(), found.pwd()))
                .orElseThrow(() -> new BusinessException(ErrorCode.LOGIN_FAILED));

        // 비활성 계정 막기
        if (account.acctStat() == AcctStat.INACTIVE) {
            throw new BusinessException(ErrorCode.ACCOUNT_INACTIVE);
        }

        // 토큰 발급
        String token = jwtProvider.issue(new CurrentUser(account.usrNo(), account.role()));
        return new LoginResponse(token, toMyInfo(account));
    }

    public MyInfoResponse myInfo(CurrentUser user) {
        UserAccount account = userAccountRepository
                .findByUsrNo(user.usrNo())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
        return toMyInfo(account);
    }

    // 학생일때 조교 여부 확인
    private MyInfoResponse toMyInfo(UserAccount account) {
        boolean ta = account.role() == Role.STUDENT && userAccountRepository.isTeachingAssistant(account.usrNo());
        return new MyInfoResponse(account.usrNo(), account.usrNm(), account.role(), ta);
    }
}
