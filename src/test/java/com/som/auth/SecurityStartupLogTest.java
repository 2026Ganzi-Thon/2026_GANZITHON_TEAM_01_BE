package com.som.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.som.SomApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.core.userdetails.UserDetailsService;

/** 캐시된 테스트 컨텍스트가 아니라 새로 기동해 기동 로그를 직접 확인한다. */
@ExtendWith(OutputCaptureExtension.class)
class SecurityStartupLogTest {

    @Test
    void 기동시_기본_인메모리_사용자와_생성된_비밀번호가_없다(CapturedOutput output) {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(SomApplication.class)
                .main(SomApplication.class)
                .profiles("test")
                .properties("server.port=0")
                .run()) {
            assertThat(context.getBeanNamesForType(UserDetailsService.class)).isEmpty();
        }
        assertThat(output.getAll()).containsIgnoringCase("Started SomApplication");
        assertThat(output.getAll()).doesNotContainIgnoringCase("generated security password");
    }
}
