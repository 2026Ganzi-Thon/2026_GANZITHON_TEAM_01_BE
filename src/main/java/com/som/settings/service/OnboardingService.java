package com.som.settings.service;

import com.som.common.error.ApiException;
import com.som.common.error.ErrorCode;
import com.som.settings.dto.OnboardingAnswers;
import com.som.settings.repository.OnboardingRepository;
import java.util.Set;
import org.springframework.stereotype.Service;

/** 온보딩 5문항(A_SPEC §4). 모든 문항은 비워 둘 수 있고, 저장은 전체 교체다. */
@Service
public class OnboardingService {

    static final Set<Integer> AVAILABLE_MINUTES = Set.of(3, 5, 10, 30);

    private final OnboardingRepository answers;

    public OnboardingService(OnboardingRepository answers) {
        this.answers = answers;
    }

    public OnboardingAnswers get(Long userId) {
        return answers.find(userId).orElseGet(OnboardingAnswers::empty);
    }

    public OnboardingAnswers save(Long userId, OnboardingAnswers body) {
        if (body.availableMinutes() != null && !AVAILABLE_MINUTES.contains(body.availableMinutes())) {
            throw new ApiException(ErrorCode.VALIDATION_ERROR, "availableMinutes: 3, 5, 10, 30 중 하나여야 합니다");
        }
        answers.save(userId, body);
        return body;
    }
}
