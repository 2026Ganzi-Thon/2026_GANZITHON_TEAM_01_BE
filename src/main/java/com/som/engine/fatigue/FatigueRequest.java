package com.som.engine.fatigue;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

/**
 * 피로 계산 입력. userId 대신 서버 발급 가명 ID(subjectKey)를 쓴다.
 * signals에는 동의한 소스의 값만 들어가고, 결측은 항목이 없다. 마음 정리 기록은 넣지 않는다.
 *
 * @param checkin 그날 체크인이 없으면 null
 */
public record FatigueRequest(String subjectKey, LocalDate localDate, ZoneId timezone, CheckinInput checkin,
                             List<SignalInput> signals, Set<String> consentedSources) {

    @Override
    public String toString() {
        return "FatigueRequest[localDate=" + localDate + ", signals=" + (signals == null ? 0 : signals.size()) + "]";
    }
}
