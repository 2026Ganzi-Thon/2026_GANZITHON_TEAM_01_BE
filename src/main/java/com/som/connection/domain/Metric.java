package com.som.connection.domain;

/**
 * 연동 지표(명세 §5 저장 형태). 여기 정의된 것만 받는다. 단위는 서버가 정한다.
 * '안 보는 것'(일정 제목·내용·참석자, 위치·이동 경로, 앱 이름·내용·메시지·검색 기록, 다른 건강 기록)이나
 * 세부 로그(분 단위 원시 기록, 앱별·일정별 값)를 담는 지표는 추가하지 마라.
 */
public enum Metric {
    SLEEP_ONSET(DataSource.SLEEP, ValueKind.TIME, null, false, true),
    SLEEP_WAKE(DataSource.SLEEP, ValueKind.TIME, null, false, true),
    /** ONSET·WAKE로 서버가 파생한다. 클라이언트 입력 불가. */
    SLEEP_MINUTES(DataSource.SLEEP, ValueKind.NUMBER, "min", false, false),
    STEPS_DAILY(DataSource.STEPS, ValueKind.NUMBER, "count", false, true),
    /** 시간대(0~23)별 총 사용 시간. 앱별 값은 받지 않는다. */
    SCREEN_TIME_MINUTES(DataSource.SCREEN_TIME, ValueKind.NUMBER, "min", true, true),
    CALENDAR_EVENT_COUNT(null, ValueKind.NUMBER, "count", false, true),
    CALENDAR_BUSY_MINUTES(null, ValueKind.NUMBER, "min", false, true),
    CALENDAR_AVERAGE_GAP_MINUTES(null, ValueKind.NUMBER, "min", false, true);

    public enum ValueKind { NUMBER, TIME }

    private final DataSource source; // null = 캘린더 2종 공용
    private final ValueKind valueKind;
    private final String unit;
    private final boolean hourly;
    private final boolean clientWritable;

    Metric(DataSource source, ValueKind valueKind, String unit, boolean hourly, boolean clientWritable) {
        this.source = source;
        this.valueKind = valueKind;
        this.unit = unit;
        this.hourly = hourly;
        this.clientWritable = clientWritable;
    }

    public boolean belongsTo(DataSource candidate) {
        if (source == null) {
            return candidate == DataSource.CALENDAR_PERSONAL || candidate == DataSource.CALENDAR_WORK;
        }
        return source == candidate;
    }

    public ValueKind valueKind() { return valueKind; }
    public String unit() { return unit; }
    public boolean hourly() { return hourly; }
    public boolean clientWritable() { return clientWritable; }
}
