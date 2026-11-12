package com.som.consent.service;

/** 다른 서비스(약관 게이트 등)가 쓰는 필수 약관 완료 여부 조회. 3개 모두 현재 동의 상태일 때만 true. */
public interface RequiredConsentChecker {
    boolean isCompleted(Long userId);
}
