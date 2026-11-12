package com.som.state.controller;

import com.som.auth.web.CurrentUserId;
import com.som.state.dto.InsightResponse;
import com.som.state.service.InsightService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 기간 기록(홈 이번 주 변화). 필수 약관 게이트 대상. 사용자는 세션의 @CurrentUserId로만 정한다. */
@RestController
@RequestMapping("/api/v1/insights")
public class InsightController {

    private final InsightService insightService;

    public InsightController(InsightService insightService) {
        this.insightService = insightService;
    }

    @GetMapping("/week")
    InsightResponse week(@CurrentUserId Long userId) {
        return insightService.week(userId);
    }
}
