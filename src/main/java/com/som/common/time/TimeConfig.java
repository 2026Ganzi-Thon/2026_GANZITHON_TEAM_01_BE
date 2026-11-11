package com.som.common.time;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 현재 시각의 단일 출처. 모든 기능은 이 Clock(또는 ServiceDates)으로만 시간을 얻는다 (ADR-007). */
@Configuration
@EnableConfigurationProperties(TimeProperties.class)
public class TimeConfig {

    @Bean
    public Clock clock(TimeProperties props) {
        return Clock.system(props.zone());
    }
}
