package com.som.settings.controller;

import com.som.auth.web.CurrentUserId;
import com.som.common.error.ApiException;
import com.som.common.error.ErrorCode;
import com.som.settings.dto.OnboardingAnswers;
import com.som.settings.repository.OnboardingRepository;
import jakarta.validation.Valid;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/onboarding")
public class OnboardingController {

    private final OnboardingRepository answers;

    public OnboardingController(OnboardingRepository answers) {
        this.answers = answers;
    }

    @GetMapping
    OnboardingAnswers get(@CurrentUserId Long userId) {
        return answers.find(userId).orElseGet(OnboardingAnswers::empty);
    }

    @PutMapping
    OnboardingAnswers put(@CurrentUserId Long userId, @Valid @RequestBody OnboardingAnswers body) {
        if (body.availableMinutes() != null && !Set.of(3, 5, 10, 30).contains(body.availableMinutes())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "availableMinutes: 3, 5, 10, 30 중 하나여야 합니다");
        }
        answers.save(userId, body);
        return body;
    }
}
