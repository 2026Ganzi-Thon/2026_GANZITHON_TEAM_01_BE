package com.som.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.som.support.ApiTestSupport;
import com.som.support.TestSupportConfig;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** 데모 계정 시드(demo 프로필, 비밀번호는 환경변수로만). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "demo"})
@Import(TestSupportConfig.class)
@TestPropertySource(properties = {"som.demo.login-id=demotest1", "som.demo.password=demo-pass-123"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DemoSeederTest {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired DemoSeeder seeder;

    @AfterAll
    void cleanUp() {
        ApiTestSupport.deleteUsers(jdbc, "demotest");
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " t JOIN users u ON u.id = t.user_id "
                + "WHERE u.login_id = 'demotest1'", Integer.class);
    }

    @Test
    void 데모_계정으로_로그인할_수_있고_7일치_상태와_연결_2개가_있다() throws Exception {
        assertThat(mockMvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"demotest1\",\"password\":\"demo-pass-123\"}")).andReturn().getResponse()
                .getStatus()).isEqualTo(200);
        assertThat(count("fatigue_snapshots")).isEqualTo(7);
        assertThat(count("checkins")).isEqualTo(7);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM connections c JOIN users u ON u.id = c.user_id "
                + "WHERE u.login_id = 'demotest1' AND c.status = 'CONNECTED'", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT provider) FROM daily_signals d JOIN users u "
                + "ON u.id = d.user_id WHERE u.login_id = 'demotest1'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT MIN(provider) FROM daily_signals d JOIN users u ON u.id = d.user_id "
                + "WHERE u.login_id = 'demotest1'", String.class)).isEqualTo("DEMO");
    }

    @Test
    void 두_번_실행해도_행이_늘지_않는다() throws Exception {
        int before = count("daily_signals") + count("checkins") + count("user_consents");

        seeder.run(null);

        assertThat(count("daily_signals") + count("checkins") + count("user_consents")).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE login_id = 'demotest1'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void 비밀번호가_없으면_계정을_만들지_않는다() throws Exception {
        seeder.seed(new DemoProperties("demotest2", null, seeder.properties().days()));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users WHERE login_id = 'demotest2'", Integer.class))
                .isZero();
    }
}
