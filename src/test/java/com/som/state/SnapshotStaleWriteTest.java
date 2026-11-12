package com.som.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.state.service.SnapshotService;
import com.som.support.ApiTestSupport;
import com.som.support.SwitchableFatigueEngine;
import com.som.support.TestSupportConfig;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
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

/**
 * 재계산이 겹칠 때 이전 입력으로 만든 결과가 나중에 저장돼 남으면 안 된다(CRITICAL: 철회한 소스 계산 제외).
 * 철회 전에 시작한 계산(수면 포함)이 철회 후 재계산보다 늦게 끝나도, 최종 스냅샷에는 수면이 없어야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
class SnapshotStaleWriteTest {

    static final LocalDate TODAY = LocalDate.of(2026, 11, 11);

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired SwitchableFatigueEngine engine;
    @Autowired SnapshotService snapshots;

    @AfterEach
    void cleanUp() {
        engine.reset();
        ApiTestSupport.deleteUsers(jdbc, "ssw");
    }

    @Test
    void 철회_전에_시작한_계산이_늦게_끝나도_철회한_소스는_최종_스냅샷에_없다() throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "ssw01");
        mockMvc.perform(post("/api/v1/me/connections/SLEEP").session(s).with(csrf())).andReturn();
        mockMvc.perform(post("/api/v1/signals").session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"source":"SLEEP","localDate":"2026-11-11","entries":[
                          {"metric":"SLEEP_ONSET","valueAt":"2026-11-11T01:00:00+09:00"},
                          {"metric":"SLEEP_WAKE","valueAt":"2026-11-11T05:00:00+09:00"}]}
                        """)).andReturn();
        Long userId = jdbc.queryForObject("SELECT id FROM users WHERE login_id = 'ssw01'", Long.class);

        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        engine.pauseNext(entered, release);
        CompletableFuture<?> slow = CompletableFuture.runAsync(() -> snapshots.recalculate(userId, TODAY));
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue(); // 수면 포함 입력으로 계산 중

        // 철회: 수면 데이터 삭제 + 커밋 후 재계산(수면 없음)이 먼저 저장된다
        mockMvc.perform(delete("/api/v1/me/connections/SLEEP").session(s).with(csrf())).andReturn();
        release.countDown();
        slow.get(10, TimeUnit.SECONDS);

        String used = jdbc.queryForObject("SELECT used_sources FROM fatigue_snapshots WHERE user_id = ? "
                + "AND local_date = ?", String.class, userId, TODAY);
        assertThat(used).doesNotContain("SLEEP");
    }
}
