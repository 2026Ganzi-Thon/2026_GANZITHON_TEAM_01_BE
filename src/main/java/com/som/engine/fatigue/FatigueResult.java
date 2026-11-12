package com.som.engine.fatigue;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/** 피로 계산 결과. A는 이 값을 가공하지 않고 그대로 저장·전달한다. */
public record FatigueResult(Integer score, Integer level, BigDecimal woolDisplayKg, FatigueStatus status,
                            String statusText, String subText, String assetKey, List<String> usedSources,
                            String algorithmVersion, OffsetDateTime calculatedAt) {}
