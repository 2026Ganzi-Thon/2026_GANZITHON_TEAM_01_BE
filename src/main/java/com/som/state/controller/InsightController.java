package com.som.state.controller;

import com.som.auth.web.CurrentUserId;
import com.som.common.error.ApiException;
import com.som.common.error.ErrorCode;
import com.som.state.InsightProperties;
import com.som.state.dto.InsightResponse;
import com.som.state.service.InsightService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 기간 기록(홈 이번 주 변화, 마이 7/30일). 필수 약관 게이트 대상. 사용자는 세션의 @CurrentUserId로만 정한다. */
@RestController
@RequestMapping("/api/v1/insights")
@EnableConfigurationProperties(InsightProperties.class)
public class InsightController {

    private final InsightService insightService;
    private final InsightProperties properties;

    public InsightController(InsightService insightService, InsightProperties properties) {
        this.insightService = insightService;
        this.properties = properties;
    }

    @GetMapping("/week")
    InsightResponse week(@CurrentUserId Long userId) {
        return insightService.week(userId);
    }

    @GetMapping
    InsightResponse recent(@CurrentUserId Long userId, @RequestParam int days) {
        if (!properties.allowedDays().contains(days)) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "days: " + properties.allowedDays() + " 중 하나여야 합니다");
        }
        return insightService.recent(userId, days);
    }
}
