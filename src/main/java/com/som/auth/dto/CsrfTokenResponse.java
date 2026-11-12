package com.som.auth.dto;

/** CSRF 토큰 안내. 상태 변경 요청 시 headerName 헤더에 token(또는 XSRF-TOKEN 쿠키 값)을 넣는다. */
public record CsrfTokenResponse(String headerName, String token) {}
