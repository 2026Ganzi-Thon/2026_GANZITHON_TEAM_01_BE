package com.som.state.service;

import java.time.LocalDate;
import org.springframework.stereotype.Component;

/** 털 깎임 이벤트가 아직 없는 단계의 기본 구현. 3단계에서 wool_trim_events 기반 구현으로 교체한다. */
@Component
public class NoWoolTrimQuery implements WoolTrimQuery {

    @Override
    public boolean trimmedOn(Long userId, LocalDate date) {
        return false;
    }
}
