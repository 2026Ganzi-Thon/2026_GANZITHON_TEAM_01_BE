package com.som.journal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.engine.routine.WoolTrimEngine;
import com.som.support.ApiTestSupport;
import com.som.support.TestSupportConfig;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 마음 정리 털 깎임을 B(WoolTrimEngine)에 물을 때: 기록은 먼저 커밋하고, B 호출은 트랜잭션 밖에서 한다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestSupportConfig.class, JournalWoolTrimEngineTest.StubConfig.class})
class JournalWoolTrimEngineTest {

    static final List<String> CATALOG = List.of("BREATHING", "SHOULDER_ROLL", "SEATED_TWIST", "WRIST_ARM_STRETCH");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired StubTrimEngine engine;

    @AfterEach
    void cleanUp() {
        engine.reset();
        ApiTestSupport.deleteUsers(jdbc, "jwt");
    }

    /** B 응답을 바꿔 가며 쓰고, 불릴 때마다 '그 사용자의 마음 정리가 다른 연결에서 보이는지'를 기록한다. */
    static class StubTrimEngine implements WoolTrimEngine {
        final JdbcTemplate jdbc;
        final AtomicReference<Boolean> answer = new AtomicReference<>(true);
        final AtomicReference<RuntimeException> failure = new AtomicReference<>();
        final List<Integer> committedEntriesSeen = new CopyOnWriteArrayList<>();

        StubTrimEngine(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
        }

        @Override
        public Boolean shouldPlay(String subjectKey, LocalDate localDate) {
            committedEntriesSeen.add(jdbc.queryForObject("""
                    SELECT count(*) FROM journal_entries je JOIN users u ON u.id = je.user_id
                    WHERE u.subject_key = ?""", Integer.class, subjectKey));
            if (failure.get() != null) {
                throw failure.get();
            }
            return answer.get();
        }

        void reset() {
            answer.set(true);
            failure.set(null);
            committedEntriesSeen.clear();
        }
    }

    @TestConfiguration
    static class StubConfig {
        @Bean
        StubTrimEngine stubTrimEngine(JdbcTemplate jdbc) {
            return new StubTrimEngine(jdbc);
        }
    }

    private MockHttpSession unlocked(String loginId) throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, loginId);
        for (String id : CATALOG) {
            mockMvc.perform(post("/api/v1/routines/" + id + "/complete").session(s).with(csrf())).andReturn();
        }
        return s;
    }

    private JsonNode write(MockHttpSession s) throws Exception {
        var result = mockMvc.perform(post("/api/v1/journal").session(s).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"emotion\":\"TIRED\"}")).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private int trimEvents(String loginId) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM wool_trim_events w JOIN users u ON u.id = w.user_id WHERE u.login_id = ?""",
                Integer.class, loginId);
    }

    @Test
    void B를_부를_때는_마음_정리가_이미_커밋되어_있다() throws Exception {
        MockHttpSession s = unlocked("jwt01");

        JsonNode body = write(s);

        assertThat(engine.committedEntriesSeen).containsExactly(1);
        assertThat(body.get("woolTrimmedToday").asBoolean()).isTrue();
        assertThat(trimEvents("jwt01")).isEqualTo(1);
    }

    @Test
    void B가_연출하지_않겠다고_하면_기록만_남고_털_깎임은_없다() throws Exception {
        MockHttpSession s = unlocked("jwt02");
        engine.answer.set(false);

        JsonNode body = write(s);

        assertThat(body.get("woolTrimmedToday").asBoolean()).isFalse();
        assertThat(trimEvents("jwt02")).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM journal_entries je JOIN users u ON u.id = je.user_id WHERE u.login_id = ?""",
                Integer.class, "jwt02")).isEqualTo(1);
    }

    @Test
    void B가_실패해도_기록은_저장되고_연출은_기본으로_보여준다() throws Exception {
        MockHttpSession s = unlocked("jwt03");
        engine.failure.set(new IllegalStateException("engine down"));

        JsonNode body = write(s);

        assertThat(body.get("woolTrimmedToday").asBoolean()).isTrue();
        assertThat(trimEvents("jwt03")).isEqualTo(1);
    }

    @Test
    void 이미_깎은_날에는_B를_다시_부르지_않는다() throws Exception {
        MockHttpSession s = unlocked("jwt04");

        write(s);
        JsonNode second = write(s);

        assertThat(engine.committedEntriesSeen).hasSize(1);
        assertThat(second.get("woolTrimmedToday").asBoolean()).isTrue();
        assertThat(trimEvents("jwt04")).isEqualTo(1);
    }
}
