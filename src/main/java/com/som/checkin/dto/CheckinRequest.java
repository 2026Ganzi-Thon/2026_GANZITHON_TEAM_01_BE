package com.som.checkin.dto;

import com.som.checkin.domain.Mood;
import com.som.checkin.domain.Stressor;
import com.som.checkin.domain.SupportNeed;
import java.util.List;

/**
 * POST /api/v1/checkins 바디. 세 문항 모두 생략·null 가능('다음에 하기'). 날짜·userId는 받지 않는다(서버가 정한다).
 * 정의되지 않은 필드(localDate·userId·memo 등)는 400(엄격 JSON).
 */
public record CheckinRequest(Mood mood, List<Stressor> stressors, SupportNeed supportNeed) {

    /** stressors 생략 = 빈 목록. 멱등 해시가 생략/빈 목록에 따라 달라지지 않게 정규화한다. */
    public CheckinRequest normalized() {
        return new CheckinRequest(mood, stressors == null ? List.of() : stressors, supportNeed);
    }
}
