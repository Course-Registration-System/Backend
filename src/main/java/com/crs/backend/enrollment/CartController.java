package com.crs.backend.enrollment;

import com.crs.backend.common.auth.CurrentUser;
import com.crs.backend.enrollment.dto.CartAddRequest;
import com.crs.backend.enrollment.dto.CartResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 장바구니(REG-02). 학생 전용이며, 학기는 서버가 신청 대상 학기로 정합니다. */
@RestController
@RequestMapping("/api/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public CartResponse get(CurrentUser user) {
        return cartService.get(user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CartResponse add(CurrentUser user, @Valid @RequestBody CartAddRequest request) {
        return cartService.add(user, request.lecNo());
    }

    @DeleteMapping("/{lecNo}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(CurrentUser user, @PathVariable int lecNo) {
        cartService.remove(user, lecNo);
    }
}
