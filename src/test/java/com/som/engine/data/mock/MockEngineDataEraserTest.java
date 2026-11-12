package com.som.engine.data.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.som.engine.data.EngineDataEraser;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MockEngineDataEraserTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MockEngineDataEraser.class);

    @Test
    void 예외_없이_끝나고_여러_번_불러도_같다() {
        MockEngineDataEraser eraser = new MockEngineDataEraser();
        assertThatCode(() -> {
            eraser.eraseSubject("subject-1");
            eraser.eraseSubject("subject-1");
        }).doesNotThrowAnyException();
    }

    @Test
    void mode가_real이면_Mock_빈이_없다() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(EngineDataEraser.class));
        runner.withPropertyValues("som.engine.mode=real").run(ctx -> assertThat(ctx).doesNotHaveBean(EngineDataEraser.class));
    }
}
