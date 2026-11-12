package com.som.auth.dto;

/** 가입 성공 응답. id·subjectKey는 내보내지 않는다. */
public record SignupResponse(String loginId) {}
