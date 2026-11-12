package com.som.engine.chat;

import com.som.engine.fatigue.CheckinInput;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * 대화 입력. userId 대신 subjectKey. 마음 정리 기록은 넣지 않는다.
 *
 * @param userText       사용자 원문. 원문 전송이 꺼져 있거나 GREETING이면 null
 * @param recentMessages 최근 대화(오래된 순)
 * @param todayCheckin   오늘 체크인(없으면 null)
 * @param weekSummary    이번 주 요약(못 구했으면 null)
 */
public record ChatRequest(String subjectKey, ChatMode mode, LocalDate localDate, String userText,
                          List<ChatTurn> recentMessages, CheckinInput todayCheckin, WeekSummary weekSummary,
                          Set<String> consentedSources) {

    @Override
    public String toString() {
        return "ChatRequest[mode=" + mode + ", localDate=" + localDate + "]";
    }
}
