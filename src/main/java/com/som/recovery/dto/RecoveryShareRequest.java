package com.som.recovery.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 공유할 회복 팁 한 줄. 원문이 로그에 찍히지 않게 toString을 가린다. */
public record RecoveryShareRequest(@NotBlank @Size(max = 200) String message) {
    @Override
    public String toString() {
        return "RecoveryShareRequest[redacted]";
    }
}
