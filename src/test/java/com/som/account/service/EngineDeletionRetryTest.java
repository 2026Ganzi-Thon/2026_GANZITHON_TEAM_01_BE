package com.som.account.service;

import static com.som.support.ApiTestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.support.ApiTestSupport;
import com.som.support.MutableClock;
import com.som.support.SwitchableEngineDataEraser;
import com.som.support.TestSupportConfig;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** engine 처리물 삭제 실패 → ENGINE_PENDING으로 남기고, 시각이 되면 다시 시도해 COMPLETED로 바꾼다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
class EngineDeletionRetryTest {

    static final String CONFIRM = "{\"confirm\":true}";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired SwitchableEngineDataEraser engineEraser;
    @Autowired DeletionService deletions;

    @AfterEach
    void cleanUp() {
        clock.reset();
        engineEraser.reset();
        ApiTestSupport.deleteUsers(jdbc, "edr");
        jdbc.update("DELETE FROM deletion_requests");
    }

    private String subjectKey(String loginId) {
        return jdbc.queryForObject("SELECT subject_key FROM users WHERE login_id = ?", String.class, loginId);
    }

    private void deleteData(MockHttpSession s) throws Exception {
        mockMvc.perform(delete("/api/v1/me/data").session(s).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(CONFIRM)).andReturn();
    }

    private String status(MockHttpSession s) throws Exception {
        return json(objectMapper, mockMvc.perform(get("/api/v1/me/deletion-status").session(s)).andReturn())
                .path("status").asText();
    }

    private void advance(Duration duration) {
        clock.set(clock.instant().plus(duration));
    }

    @Test
    void 실패한_삭제는_재시도_시각이_되기_전에는_다시_부르지_않는다() throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "edr01");
        engineEraser.fail(true);
        deleteData(s);
        engineEraser.reset();

        deletions.retryPending();

        assertThat(engineEraser.erased()).isEmpty();
        assertThat(status(s)).isEqualTo("ENGINE_PENDING");
    }

    @Test
    void 시각이_되면_다시_시도하고_성공하면_COMPLETED이며_식별값을_지운다() throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "edr02");
        String key = subjectKey("edr02");
        engineEraser.fail(true);
        deleteData(s);
        engineEraser.reset();

        advance(Duration.ofMinutes(1));
        deletions.retryPending();

        assertThat(engineEraser.erased()).containsExactly(key);
        assertThat(status(s)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM deletion_requests WHERE pending_subject_key IS NOT NULL",
                Integer.class)).isZero();

        advance(Duration.ofMinutes(10));
        deletions.retryPending();
        assertThat(engineEraser.erased()).hasSize(1);
    }

    @Test
    void 재시도도_실패하면_다음_시도를_뒤로_미룬다() throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "edr03");
        engineEraser.fail(true);
        deleteData(s);

        advance(Duration.ofMinutes(1));
        deletions.retryPending();
        int afterFirstRetry = engineEraser.erased().size();
        deletions.retryPending();

        assertThat(engineEraser.erased()).hasSize(afterFirstRetry);
        assertThat(status(s)).isEqualTo("ENGINE_PENDING");
    }

    @Test
    void 탈퇴한_계정의_삭제도_계정이_없어진_뒤_재시도된다() throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "edr04");
        String key = subjectKey("edr04");
        engineEraser.fail(true);
        mockMvc.perform(delete("/api/v1/me").session(s).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(CONFIRM)).andReturn();
        engineEraser.reset();

        advance(Duration.ofMinutes(1));
        deletions.retryPending();

        assertThat(engineEraser.erased()).containsExactly(key);
        assertThat(jdbc.queryForObject("SELECT status FROM deletion_requests", String.class)).isEqualTo("COMPLETED");
    }

    @Test
    void 성공한_삭제는_재시도_대상이_아니다() throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "edr05");
        deleteData(s);
        engineEraser.reset();

        advance(Duration.ofMinutes(10));
        deletions.retryPending();

        assertThat(engineEraser.erased()).isEmpty();
        assertThat(status(s)).isEqualTo("COMPLETED");
    }
}
