package com.som.routine.domain;

/** 저장되는 실행 상태. 기록이 없으면 API에서 NOT_STARTED로 보여준다. COMPLETED는 최종 상태다. */
public enum RoutineSessionStatus { STARTED, COMPLETED, SKIPPED }
