package com.som.account.dto;

/** 삭제·탈퇴 확인(실수 방지). confirm=true가 아니면 400. */
public record DeletionConfirmRequest(Boolean confirm) {}
