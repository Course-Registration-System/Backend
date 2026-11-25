package com.crs.backend.enrollment;

import com.crs.backend.academic.SchedulePolicy;
import com.crs.backend.common.BusinessException;
import com.crs.backend.common.ErrorCode;
import com.crs.backend.common.auth.CurrentUser;
import com.crs.backend.common.auth.Role;
import com.crs.backend.enrollment.CartRepository.LectureState;
import com.crs.backend.enrollment.dto.CartItemResponse;
import com.crs.backend.enrollment.dto.CartResponse;
import com.crs.backend.lecture.LecStat;
import com.crs.backend.user.StdStat;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 장바구니 관리(REG-02). 시간 중복·학점 초과는 여기서 보지 않고 수강신청(REG-03) 때 검사합니다. */
@Service
public class CartService {

    private final CartRepository cartRepository;
    private final SchedulePolicy schedulePolicy;
    private final Clock clock;

    public CartService(CartRepository cartRepository, SchedulePolicy schedulePolicy, Clock clock) {
        this.cartRepository = cartRepository;
        this.schedulePolicy = schedulePolicy;
        this.clock = clock;
    }

    // ⑥ 조회. 수강신청·정정 기간에도 장바구니 목록에서 바로 신청하므로 신청 대상 학기 동안 볼 수 있다
    public CartResponse get(CurrentUser user) {
        String stdNo = requireStudent(user);
        return view(stdNo, registrationSemester());
    }

    // ①~⑤ 담기
    @Transactional
    public CartResponse add(CurrentUser user, int lecNo) {
        String stdNo = requireStudent(user);
        int semNo = registrationSemester();
        checkCartOpen(semNo);
        checkStudentStatus(stdNo, semNo);

        LectureState lecture = cartRepository
                .lockLectureForShare(lecNo)
                .orElseThrow(() -> new BusinessException(ErrorCode.LECTURE_NOT_FOUND));
        if (lecture.semNo() != semNo) {
            throw new BusinessException(ErrorCode.OTHER_SEMESTER_LECTURE);
        }
        if (lecture.lecStat() == LecStat.CLOSED) {
            throw new BusinessException(ErrorCode.LECTURE_CLOSED);
        }
        // 같은 강의를 동시에 두 번 담아도 기본키(학번, 강의번호)가 막는다
        try {
            cartRepository.insert(stdNo, lecNo, LocalDateTime.now(clock));
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_CART);
        }
        return view(stdNo, semNo);
    }

    // ⑦ 삭제
    @Transactional
    public void remove(CurrentUser user, int lecNo) {
        String stdNo = requireStudent(user);
        checkCartOpen(registrationSemester());
        if (cartRepository.delete(stdNo, lecNo) == 0) {
            throw new BusinessException(ErrorCode.CART_NOT_FOUND);
        }
    }

    private CartResponse view(String stdNo, int semNo) {
        List<CartItemResponse> items = cartRepository.findItems(stdNo, semNo);
        BigDecimal credits = items.stream()
                .filter(item -> !"CHAPEL".equals(item.cmplType()))
                .map(CartItemResponse::crdt)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CartResponse(semNo, items, credits, schedulePolicy.maxCredit(stdNo, semNo));
    }

    private String requireStudent(CurrentUser user) {
        if (!user.is(Role.STUDENT)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return user.usrNo();
    }

    private int registrationSemester() {
        return schedulePolicy
                .registrationSemester()
                .orElseThrow(() -> new BusinessException(ErrorCode.NO_REGISTRATION_SEMESTER));
    }

    private void checkCartOpen(int semNo) {
        if (!schedulePolicy.cartOpen(semNo)) {
            throw new BusinessException(ErrorCode.CART_CLOSED);
        }
    }

    private void checkStudentStatus(String stdNo, int semNo) {
        StdStat stat = cartRepository.findStdStat(stdNo).orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
        StudentStatusRule.check(stdNo, stat, cartRepository.isSeasonal(semNo));
    }
}
