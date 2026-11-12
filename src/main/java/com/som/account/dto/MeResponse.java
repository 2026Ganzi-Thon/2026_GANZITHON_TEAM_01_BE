package com.som.account.dto;

import java.time.OffsetDateTime;

/** 본인 계정 정보. 내부 id·subjectKey·passwordHash·authProvider는 넣지 마라. */
public record MeResponse(String loginId, boolean requiredConsentsCompleted, OffsetDateTime createdAt) {}
