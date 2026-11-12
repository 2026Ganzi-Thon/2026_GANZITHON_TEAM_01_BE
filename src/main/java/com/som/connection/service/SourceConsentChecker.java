package com.som.connection.service;

import com.som.connection.domain.DataSource;
import java.util.Set;

/** 다른 기능(연동 수집·engine 입력)이 쓰는 선택 연동 동의 조회. 현재(최신 이력) 허용 상태인 소스만 동의로 본다. */
public interface SourceConsentChecker {
    boolean isConsented(Long userId, DataSource source);

    Set<DataSource> consentedSources(Long userId);
}
