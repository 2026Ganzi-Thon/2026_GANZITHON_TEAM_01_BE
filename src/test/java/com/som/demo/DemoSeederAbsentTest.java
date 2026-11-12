package com.som.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.som.support.TestSupportConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** demo 프로필이 아니면 데모 시드가 돌지 않는다(공용 테스트 컨텍스트를 그대로 쓴다). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
class DemoSeederAbsentTest {

    @Autowired ApplicationContext context;

    @Test
    void 기본_프로필에는_DemoSeeder_빈이_없다() {
        assertThat(context.getBeanNamesForType(DemoSeeder.class)).isEmpty();
    }
}
