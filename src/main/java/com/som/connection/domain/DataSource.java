package com.som.connection.domain;

/**
 * 선택 연동 소스(명세 §5). API 경로값이며 모르는 값은 400 VALIDATION_ERROR.
 * '안 보는 것'(위치·이동 경로, 앱 내용·메시지·검색 기록, 일정 제목·내용·참석자, 다른 건강 기록)은 소스로 추가하지 마라.
 */
public enum DataSource {
    SLEEP,
    STEPS,
    SCREEN_TIME,
    CALENDAR_PERSONAL,
    CALENDAR_WORK
}
