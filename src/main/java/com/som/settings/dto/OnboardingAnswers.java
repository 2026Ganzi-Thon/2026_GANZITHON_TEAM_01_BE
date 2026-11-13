package com.som.settings.dto;

import com.som.settings.domain.NotificationSlot;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record OnboardingAnswers(LifeStage lifeStage, @Size(max = 6) Set<@NotNull FatigueSign> fatigueSigns,
                                @Size(max = 7) Set<@NotNull RecoveryStyle> recoveryStyles,
                                Integer availableMinutes, NotificationSlot preferredContactTime) {
    public enum LifeStage { EMPLOYED_UNDER_1Y, EMPLOYED_1_3Y, JOB_SEEKING, OTHER }
    public enum FatigueSign { IRRITABLE, TIRED_AFTER_REST, SLEEP_CHANGE, SOCIAL_BURDEN, CANT_FOCUS, NOT_SURE }
    public enum RecoveryStyle { SLEEP_WELL, MOVE_BODY, QUIET_ALONE, MUSIC, WRITE, VIDEO_GAME, MEET_PEOPLE }

    public static OnboardingAnswers empty() {
        return new OnboardingAnswers(null, null, null, null, null);
    }
}
