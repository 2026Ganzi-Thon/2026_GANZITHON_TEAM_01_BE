package com.som.state.controller;

import com.som.auth.web.CurrentUserId;
import com.som.state.dto.TodayStateResponse;
import com.som.state.service.StateService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 홈 상태. 필수 약관 게이트 대상. 사용자는 세션의 @CurrentUserId로만 정한다. */
@RestController
@RequestMapping("/api/v1/state")
public class StateController {

    private final StateService stateService;

    public StateController(StateService stateService) {
        this.stateService = stateService;
    }

    @GetMapping("/today")
    TodayStateResponse today(@CurrentUserId Long userId) {
        return stateService.today(userId);
    }
}
