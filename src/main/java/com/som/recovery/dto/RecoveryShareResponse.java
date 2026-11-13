package com.som.recovery.dto;

/** 작성자 정보 없이 글과 '내 글인지'만 돌려준다. */
public record RecoveryShareResponse(long id, String message, boolean mine) {}
