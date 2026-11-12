package com.som.consent;

import com.som.auth.web.CurrentUserId;
import com.som.consent.dto.ConsentChangeRequest;
import com.som.consent.dto.ConsentsResponse;
import com.som.consent.service.ConsentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 필수 약관 동의 조회·변경. 사용자는 세션의 @CurrentUserId로만 정한다. */
@RestController
@RequestMapping("/api/v1/me/consents")
public class ConsentController {

    private final ConsentService consentService;

    public ConsentController(ConsentService consentService) {
        this.consentService = consentService;
    }

    @GetMapping
    ConsentsResponse get(@CurrentUserId Long userId) {
        return consentService.get(userId);
    }

    /** "전체 동의"는 프론트가 3개를 모두 담아 보낸다(별도 플래그 없음). */
    @PatchMapping
    ConsentsResponse change(@CurrentUserId Long userId, @Valid @RequestBody ConsentChangeRequest body) {
        return consentService.change(userId, body);
    }
}
