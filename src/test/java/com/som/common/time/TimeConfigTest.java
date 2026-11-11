package com.som.common.time;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TimeConfigTest {

    @Autowired
    Clock clock;

    @Value("${som.time.zone}")
    String configuredZone;

    @Test
    void clock_빈은_설정된_서비스_시간대를_쓴다() {
        assertThat(clock.getZone().getId()).isEqualTo(configuredZone).isEqualTo("Asia/Seoul");
    }
}
