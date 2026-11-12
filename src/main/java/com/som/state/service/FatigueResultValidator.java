package com.som.state.service;

import com.som.engine.fatigue.FatigueResult;
import com.som.engine.fatigue.FatigueStatus;
import java.math.BigDecimal;

/**
 * engine 피로 결과의 형식 검증. 어긋나면 저장하지 않는다(값을 고치거나 채우지 않는다).
 * CALCULATED: score 0~100, level 1~4, woolDisplayKg 양수, calculatedAt 있음.
 * INSUFFICIENT_DATA: score·level·woolDisplayKg 모두 null.
 */
public final class FatigueResultValidator {

    private FatigueResultValidator() {}

    public static boolean isValid(FatigueResult result) {
        if (result == null || result.status() == null || result.calculatedAt() == null) {
            return false;
        }
        if (result.status() == FatigueStatus.INSUFFICIENT_DATA) {
            return result.score() == null && result.level() == null && result.woolDisplayKg() == null;
        }
        return result.score() != null && result.score() >= 0 && result.score() <= 100
                && result.level() != null && result.level() >= 1 && result.level() <= 4
                && result.woolDisplayKg() != null && result.woolDisplayKg().compareTo(BigDecimal.ZERO) > 0;
    }
}
