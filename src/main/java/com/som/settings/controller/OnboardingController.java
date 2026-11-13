package com.som.settings.controller;

import com.som.auth.web.CurrentUserId;
import com.som.settings.dto.OnboardingAnswers;
import com.som.settings.service.OnboardingService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/onboarding")
public class OnboardingController {

    private final OnboardingService onboarding;

    public OnboardingController(OnboardingService onboarding) {
        this.onboarding = onboarding;
    }

    @GetMapping
    OnboardingAnswers get(@CurrentUserId Long userId) {
        return onboarding.get(userId);
    }

    @PutMapping
    OnboardingAnswers put(@CurrentUserId Long userId, @Valid @RequestBody OnboardingAnswers body) {
        return onboarding.save(userId, body);
    }
}
