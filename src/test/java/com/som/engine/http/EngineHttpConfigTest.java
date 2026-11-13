package com.som.engine.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.som.engine.chat.ChatEngine;
import com.som.engine.data.EngineDataEraser;
import com.som.engine.fatigue.FatigueEngine;
import com.som.engine.insight.InsightEngine;
import com.som.engine.routine.RoutineEngine;
import com.som.engine.routine.WoolTrimEngine;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class EngineHttpConfigTest {

    final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(EngineHttpConfig.class);

    @Test
    void real_모드면_모든_engine_인터페이스가_HTTP_구현이다() {
        runner.withPropertyValues("som.engine.mode=real", "som.engine.url=http://127.0.0.1:8000",
                        "som.engine.token=test-token")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(FatigueEngine.class)).isInstanceOf(HttpEngine.class);
                    assertThat(context.getBean(InsightEngine.class)).isInstanceOf(HttpEngine.class);
                    assertThat(context.getBean(RoutineEngine.class)).isInstanceOf(HttpEngine.class);
                    assertThat(context.getBean(ChatEngine.class)).isInstanceOf(HttpEngine.class);
                    assertThat(context.getBean(EngineDataEraser.class)).isInstanceOf(HttpEngine.class);
                    assertThat(context.getBean(WoolTrimEngine.class)).isInstanceOf(HttpEngine.class);
                });
    }

    @Test
    void mock_모드나_설정이_없으면_HTTP_구현을_만들지_않는다() {
        runner.withPropertyValues("som.engine.mode=mock")
                .run(context -> assertThat(context).doesNotHaveBean(HttpEngine.class));
        runner.run(context -> assertThat(context).doesNotHaveBean(HttpEngine.class));
    }

    @Test
    void real_모드인데_토큰이_비었거나_주소가_잘못되면_시작하지_않는다() {
        runner.withPropertyValues("som.engine.mode=real", "som.engine.url=http://127.0.0.1:8000",
                        "som.engine.token=")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("som.engine.mode=real", "som.engine.url=http://u:p@127.0.0.1:8000",
                        "som.engine.token=test-token")
                .run(context -> assertThat(context).hasFailed());
    }
}
