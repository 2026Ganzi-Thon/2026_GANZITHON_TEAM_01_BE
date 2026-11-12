package com.som.routine.controller;

import com.som.auth.web.CurrentUserId;
import com.som.routine.dto.RoutineSessionResponse;
import com.som.routine.dto.RoutinesTodayResponse;
import com.som.routine.service.RoutineSessionService;
import com.som.routine.service.RoutineSessionService.Action;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 루틴 오늘 화면·실행 기록. 필수 약관 게이트 대상. 사용자는 세션의 @CurrentUserId로만 정한다. */
@RestController
@RequestMapping("/api/v1/routines")
public class RoutineController {

    private final RoutineSessionService routineService;

    public RoutineController(RoutineSessionService routineService) {
        this.routineService = routineService;
    }

    @GetMapping("/today")
    RoutinesTodayResponse today(@CurrentUserId Long userId) {
        return routineService.today(userId);
    }

    @PostMapping("/{routineId}/start")
    RoutineSessionResponse start(@CurrentUserId Long userId, @PathVariable String routineId) {
        return routineService.act(userId, routineId, Action.START);
    }

    @PostMapping("/{routineId}/complete")
    RoutineSessionResponse complete(@CurrentUserId Long userId, @PathVariable String routineId) {
        return routineService.act(userId, routineId, Action.COMPLETE);
    }

    @PostMapping("/{routineId}/skip")
    RoutineSessionResponse skip(@CurrentUserId Long userId, @PathVariable String routineId) {
        return routineService.act(userId, routineId, Action.SKIP);
    }
}
