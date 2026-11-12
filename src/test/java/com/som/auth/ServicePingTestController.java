package com.som.auth;

import com.som.auth.web.CurrentUserId;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 테스트 전용 보호 서비스 API: 필수 약관 게이트(RequiredConsentGateFilter) 검증용. */
@RestController
class ServicePingTestController {

    @GetMapping("/api/v1/test-service/ping")
    Map<String, Object> ping(@CurrentUserId Long userId) {
        return Map.of("ok", true);
    }
}
