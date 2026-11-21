package com.crs.backend.academic;

import com.crs.backend.academic.dto.MaxCreditItem;
import com.crs.backend.academic.dto.MaxCreditResult;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 진급·졸업 처리와 최대학점 산정(ACA-01 ⑤). /api/admin/** 이라 관리자만 들어옵니다. */
@RestController
@RequestMapping("/api/admin/semesters/{semNo}/max-credits")
public class MaxCreditController {

    private final MaxCreditService maxCreditService;

    public MaxCreditController(MaxCreditService maxCreditService) {
        this.maxCreditService = maxCreditService;
    }

    /** 산정 실행. 정규학기면 진급·졸업을 먼저 처리합니다. 다시 실행할 수 있습니다. */
    @PostMapping
    public MaxCreditResult calculate(@PathVariable int semNo) {
        return maxCreditService.calculate(semNo);
    }

    @GetMapping
    public List<MaxCreditItem> list(@PathVariable int semNo) {
        return maxCreditService.list(semNo);
    }
}
