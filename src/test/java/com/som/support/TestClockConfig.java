package com.som.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** 여러 테스트가 같은 컨텍스트를 공유하도록 공용 시계 설정을 둔다(@Import(TestClockConfig.class)). */
@TestConfiguration
public class TestClockConfig {

    /** Clock 타입으로도 주입된다(@Primary로 TimeConfig의 시스템 시계를 대신한다). */
    @Bean
    @Primary
    public MutableClock testClock() {
        return new MutableClock();
    }
}
