package com.som.auth.dto;

/** 로그인 성공 응답. 토큰은 본문으로 주지 않는다(세션 쿠키만, ADR-003). */
public record LoginResponse(String loginId) {}
