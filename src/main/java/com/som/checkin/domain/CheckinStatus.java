package com.som.checkin.domain;

/** 세 문항이 모두 비면 SKIPPED(체크인 건너뜀 — 피로 추정에 쓰지 않는다), 하나라도 있으면 SUBMITTED. */
public enum CheckinStatus { SUBMITTED, SKIPPED }
