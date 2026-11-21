package com.crs.backend.academic.dto;

import java.util.Map;

/**
 * 최대학점 산정 결과. graduated 는 이번 실행에서 졸업 처리된 학생 수(계절학기는 0), counts 는 최대학점별 학생 수입니다.
 *
 * @param elapsedMs 산정에 걸린 시간(밀리초). 성능 실험에서 씁니다
 */
public record MaxCreditResult(
        int semNo, boolean regular, int graduated, Map<Integer, Integer> counts, long elapsedMs) {}
