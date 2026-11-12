package com.som.demo;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 데모 시드 설정(application-demo.yml).
 *
 * @param password 환경변수 SOM_DEMO_PASSWORD에서만 온다. 비어 있으면 시드하지 않는다
 */
@ConfigurationProperties(prefix = "som.demo")
public record DemoProperties(String loginId, String password, List<Day> days) {

    /** @param offset 오늘로부터 며칠 전(0 = 오늘). sleepOnset이 sleepWake보다 늦으면 전날 밤으로 본다 */
    public record Day(int offset, String mood, String sleepOnset, String sleepWake, Integer steps) {}

    public DemoProperties {
        days = days == null ? List.of() : List.copyOf(days);
    }

    @Override
    public String toString() {
        return "DemoProperties[loginId=" + loginId + ", days=" + days.size() + "]";
    }
}
