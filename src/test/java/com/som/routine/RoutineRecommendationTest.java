package com.som.routine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.common.error.ApiException;
import com.som.common.error.ErrorCode;
import com.som.engine.routine.RoutineRecommendation;
import com.som.routine.service.RoutineRecommendationService;
import com.som.routine.service.TodayRecommendation;
import com.som.support.ApiTestSupport;
import com.som.support.MutableClock;
import com.som.support.SwitchableRoutineEngine;
import com.som.support.TestSupportConfig;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** 하루 루틴 추천(engine 결과만, 실패 시 마지막 정상 값 + isStale). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
class RoutineRecommendationTest {

    static final List<String> CATALOG = List.of("BREATHING", "SHOULDER_ROLL", "SEATED_TWIST", "WRIST_ARM_STRETCH");
    static final List<String> REVERSED = List.of("WRIST_ARM_STRETCH", "SEATED_TWIST", "SHOULDER_ROLL", "BREATHING");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    @Autowired SwitchableRoutineEngine engine;
    @Autowired RoutineRecommendationService recommendations;

    @AfterEach
    void cleanUp() {
        clock.reset();
        engine.reset();
        ApiTestSupport.deleteUsers(jdbc, "rrc");
    }

    private Long user(String loginId) throws Exception {
        ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, loginId);
        return jdbc.queryForObject("SELECT id FROM users WHERE login_id = ?", Long.class, loginId);
    }

    @Test
    void 처음_조회하면_engine_결과를_저장하고_같은_날은_다시_부르지_않는다() throws Exception {
        Long id = user("rrc01");
        engine.override(r -> new RoutineRecommendation(REVERSED, "test"));

        TodayRecommendation first = recommendations.today(id);
        TodayRecommendation second = recommendations.today(id);

        assertThat(first.routineIds()).containsExactlyElementsOf(REVERSED);
        assertThat(first.isStale()).isFalse();
        assertThat(second).isEqualTo(first);
        assertThat(engine.requests()).hasSize(1);
        assertThat(engine.requests().get(0).catalogIds()).containsExactlyElementsOf(CATALOG);
    }

    @Test
    void engine이_모르는_ID_중복_누락을_주면_실패로_보고_이전_날_추천을_isStale로_쓴다() throws Exception {
        Long id = user("rrc02");
        clock.set("2026-11-10T05:00:00Z");
        engine.override(r -> new RoutineRecommendation(REVERSED, "test"));
        recommendations.today(id);

        clock.set("2026-11-11T05:00:00Z");
        for (List<String> bad : List.of(
                List.of("BREATHING", "SHOULDER_ROLL", "SEATED_TWIST", "UNKNOWN"),
                List.of("BREATHING", "BREATHING", "SEATED_TWIST", "WRIST_ARM_STRETCH"),
                List.of("BREATHING", "SHOULDER_ROLL"))) {
            engine.override(r -> new RoutineRecommendation(bad, "bad"));
            TodayRecommendation result = recommendations.today(id);
            assertThat(result.routineIds()).containsExactlyElementsOf(REVERSED);
            assertThat(result.isStale()).isTrue();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM routine_recommendations WHERE user_id = ?",
                Integer.class, id)).isEqualTo(1);
    }

    @Test
    void engine이_실패하고_이전_추천도_없으면_503() throws Exception {
        Long id = user("rrc03");
        engine.fail(true);

        assertThatThrownBy(() -> recommendations.today(id))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code()).isEqualTo(ErrorCode.ENGINE_UNAVAILABLE));
    }

    @Test
    void 다른_사용자의_추천은_쓰이지_않는다() throws Exception {
        Long a = user("rrc04a");
        Long b = user("rrc04b");
        engine.override(r -> new RoutineRecommendation(REVERSED, "test"));
        recommendations.today(a);

        engine.reset();
        engine.fail(true);
        assertThatThrownBy(() -> recommendations.today(b)).isInstanceOf(ApiException.class);
    }
}
