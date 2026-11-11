package com.som.common.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 테스트 전용: 요청 처리 중의 요청 ID(요청 속성·MDC)를 그대로 돌려준다. */
@RestController
class RequestIdTestController {

    @GetMapping("/test/ping")
    Map<String, String> ping(HttpServletRequest request) {
        return Map.of(
                "attribute", String.valueOf(RequestIds.current(request)),
                "mdc", String.valueOf(MDC.get(RequestIdFilter.MDC_KEY)));
    }
}
