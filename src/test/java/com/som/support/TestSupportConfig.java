package com.som.support;

import com.som.engine.fatigue.mock.MockFatigueEngine;
import com.som.engine.insight.mock.MockInsightEngine;
import com.som.engine.routine.mock.MockRoutineEngine;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 여러 테스트가 같은 Spring 컨텍스트를 공유하도록 공용 테스트 빈을 한곳에 둔다(@Import(TestSupportConfig.class)).
 * 컨텍스트가 늘면 DB 접속 수가 늘어난다(spring.properties 캐시 상한 참고).
 */
@TestConfiguration
public class TestSupportConfig {

    /** Clock 타입으로도 주입된다(@Primary로 TimeConfig의 시스템 시계를 대신한다). */
    @Bean
    @Primary
    public MutableClock testClock() {
        return new MutableClock();
    }

    /** 실제 Mock 엔진을 감싸 장애를 흉내 내고 마지막 요청을 기록한다. */
    @Bean
    @Primary
    public SwitchableFatigueEngine switchableFatigueEngine(MockFatigueEngine delegate) {
        return new SwitchableFatigueEngine(delegate);
    }

    @Bean
    @Primary
    public SwitchableInsightEngine switchableInsightEngine(MockInsightEngine delegate) {
        return new SwitchableInsightEngine(delegate);
    }

    @Bean
    @Primary
    public SwitchableRoutineEngine switchableRoutineEngine(MockRoutineEngine delegate) {
        return new SwitchableRoutineEngine(delegate);
    }
}
