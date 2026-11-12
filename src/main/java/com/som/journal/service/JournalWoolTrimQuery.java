package com.som.journal.service;

import com.som.journal.repository.WoolTrimEventRepository;
import com.som.state.service.WoolTrimQuery;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/** state/today.woolTrimmedToday의 근거: 그날 털 깎임 이벤트가 있는지(연출 값, 점수 불변). */
@Component
public class JournalWoolTrimQuery implements WoolTrimQuery {

    private final WoolTrimEventRepository woolTrims;

    public JournalWoolTrimQuery(WoolTrimEventRepository woolTrims) {
        this.woolTrims = woolTrims;
    }

    @Override
    public boolean trimmedOn(Long userId, LocalDate date) {
        return woolTrims.exists(userId, date);
    }
}
