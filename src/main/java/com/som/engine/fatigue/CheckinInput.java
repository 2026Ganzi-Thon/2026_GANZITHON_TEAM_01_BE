package com.som.engine.fatigue;

import java.util.List;

/** 체크인 3문항(enum 이름 문자열). '다음에 하기'로 넘긴 문항은 null, stressors는 빈 목록. */
public record CheckinInput(String mood, List<String> stressors, String supportNeed) {

    /** 세 문항 모두 답이 없으면 true — 체크인 없음과 같게 본다. */
    public boolean isEmpty() {
        return mood == null && (stressors == null || stressors.isEmpty()) && supportNeed == null;
    }
}
