package com.som.state.service;

import java.time.LocalDate;

/**
 * 그날 털 깎임 연출이 있었는지(마음 정리 완료 등). 연출 값일 뿐 score·level을 바꾸지 않는다(CRITICAL).
 * 털 깎임 이벤트 테이블은 3단계(journal)에서 만든다. 그때까지는 NoWoolTrimQuery가 항상 false를 돌려준다.
 */
public interface WoolTrimQuery {
    boolean trimmedOn(Long userId, LocalDate date);
}
