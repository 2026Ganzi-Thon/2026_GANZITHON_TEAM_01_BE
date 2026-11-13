package com.som.engine.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.som.engine.chat.CardType;
import com.som.engine.chat.ChatMode;
import com.som.engine.chat.ChatRequest;
import com.som.engine.chat.ChatResult;
import com.som.engine.chat.ChatRole;
import com.som.engine.chat.ChatTurn;
import com.som.engine.chat.RiskLevel;
import com.som.engine.chat.WeekSummary;
import com.som.engine.fatigue.CheckinInput;
import com.som.engine.fatigue.FatigueRequest;
import com.som.engine.fatigue.FatigueResult;
import com.som.engine.fatigue.FatigueStatus;
import com.som.engine.fatigue.SignalInput;
import com.som.engine.insight.DailyState;
import com.som.engine.insight.InsightRequest;
import com.som.engine.insight.InsightResult;
import com.som.engine.routine.RoutineRecommendation;
import com.som.engine.routine.RoutineRequest;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** B(FastAPI) 계약: backend-b README의 요청·응답 예시 기준. */
class HttpEngineTest {

    static final String BASE = "http://engine.test";
    static final LocalDate DAY = LocalDate.parse("2026-11-12");

    MockRestServiceServer server;
    final HttpEngine engine = engine();

    HttpEngine engine() {
        RestClient.Builder builder = EngineHttpConfig.configure(RestClient.builder(), URI.create(BASE), "test-token");
        server = MockRestServiceServer.bindTo(builder).build();
        return new HttpEngine(builder.build());
    }

    static void json(MockRestServiceServer server, String path, String request, String response) {
        server.expect(requestTo(BASE + path))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andExpect(content().json(request, JsonCompareMode.STRICT))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }

    @Test
    void 피로_계산은_분_단위_신호만_null_없이_보내고_B가_더_준_필드는_무시한다() {
        json(server, "/v1/fatigue/calculate", """
                {"subjectKey":"s-1","localDate":"2026-11-12","timezone":"Asia/Seoul",
                 "checkin":{"mood":"TIRED","stressors":["WORK"]},
                 "signals":[{"source":"SLEEP","metric":"SLEEP_MINUTES","value":420,"unit":"min"}],
                 "consentedSources":["SLEEP"],"recentSignals":[]}""", """
                {"score":68,"level":3,"woolDisplayKg":2.4,"status":"CALCULATED",
                 "statusText":"피로가 쌓였어요","subText":"잠시 쉬어갈 시간을 마련해봐요.",
                 "assetKey":"sheep_level_3","usedSources":["CHECKIN","SLEEP"],"baselineStatus":"PERSONAL",
                 "changes":[{"source":"SLEEP","metric":"SLEEP_MINUTES","deltaMinutes":-60,"futureField":1}],
                 "algorithmVersion":"b-mvp-0.2","calculatedAt":"2026-11-12T21:00:00+09:00","futureField":true}""");

        FatigueResult result = engine.calculate(new FatigueRequest("s-1", DAY, ZoneId.of("Asia/Seoul"),
                new CheckinInput("TIRED", List.of("WORK"), null), List.of(
                        new SignalInput("SLEEP", "SLEEP_MINUTES", new BigDecimal("420"), null, "min", null),
                        new SignalInput("SLEEP", "SLEEP_ONSET", null,
                                OffsetDateTime.parse("2026-11-12T01:00:00+09:00"), null, null)),
                Set.of("SLEEP")));

        server.verify();
        assertThat(result.status()).isEqualTo(FatigueStatus.CALCULATED);
        assertThat(result.score()).isEqualTo(68);
        assertThat(result.level()).isEqualTo(3);
        assertThat(result.woolDisplayKg()).isEqualByComparingTo("2.4");
        assertThat(result.changes()).singleElement().satisfies(c -> assertThat(c.deltaMinutes()).isEqualTo(-60));
        assertThat(result.calculatedAt().toInstant()).isEqualTo("2026-11-12T12:00:00Z");
    }

    @Test
    void 근거가_없으면_null_결과를_그대로_돌려준다() {
        json(server, "/v1/fatigue/calculate", """
                {"subjectKey":"s-1","localDate":"2026-11-12","timezone":"Asia/Seoul","signals":[],
                 "consentedSources":[],"recentSignals":[]}""", """
                {"score":null,"level":null,"woolDisplayKg":null,"status":"INSUFFICIENT_DATA",
                 "usedSources":[],"algorithmVersion":"b","calculatedAt":"2026-11-12T21:00:00+09:00"}""");

        FatigueResult result = engine.calculate(new FatigueRequest("s-1", DAY, ZoneId.of("Asia/Seoul"), null,
                List.of(), Set.of()));

        server.verify();
        assertThat(result.status()).isEqualTo(FatigueStatus.INSUFFICIENT_DATA);
        assertThat(result.score()).isNull();
        assertThat(result.level()).isNull();
        assertThat(result.woolDisplayKg()).isNull();
    }

    @Test
    void 기간_요약() {
        json(server, "/v1/insights/summarize", """
                {"subjectKey":"s-1","from":"2026-11-10","to":"2026-11-12","consentedSources":[],
                 "days":[{"localDate":"2026-11-10","level":2,"score":40,"woolDisplayKg":1.6,"changes":[]},
                         {"localDate":"2026-11-11","changes":[]}]}""", """
                {"averageLevel":2.0,"recordedDays":1,"changeSummary":null,"heaviestDate":"2026-11-10",
                 "heaviestReason":null,"woolWeightKg":1.6,"statusText":"조금 지쳐 있어요","subText":"s",
                 "dailyLevels":[{"localDate":"2026-11-10","level":2}],"changes":[]}""");

        InsightResult result = engine.summarize(new InsightRequest("s-1", DAY.minusDays(2), DAY, List.of(
                new DailyState(DAY.minusDays(2), 2, 40, new BigDecimal("1.6")),
                new DailyState(DAY.minusDays(1), null, null, null))));

        server.verify();
        assertThat(result.averageLevel()).isEqualByComparingTo("2.0");
        assertThat(result.recordedDays()).isEqualTo(1);
        assertThat(result.heaviestDate()).isEqualTo(DAY.minusDays(2));
        assertThat(result.woolWeightKg()).isEqualByComparingTo("1.6");
    }

    @Test
    void 루틴_추천() {
        json(server, "/v1/routines/recommend", """
                {"subjectKey":"s-1","localDate":"2026-11-12","level":3,"supportNeed":"SMALL_ACTION",
                 "catalogIds":["BREATHING","WRIST_ARM_STRETCH"],"completedRecently":[],"catalogDurations":{}}""", """
                {"routineIds":["WRIST_ARM_STRETCH","BREATHING"],"reason":"r",
                 "items":[{"routineId":"WRIST_ARM_STRETCH","durationSec":30}]}""");

        RoutineRecommendation result = engine.recommend(new RoutineRequest("s-1", DAY, 3, "SMALL_ACTION",
                List.of("BREATHING", "WRIST_ARM_STRETCH"), List.of()));

        server.verify();
        assertThat(result.routineIds()).containsExactly("WRIST_ARM_STRETCH", "BREATHING");
        assertThat(result.reason()).isEqualTo("r");
    }

    @Test
    void 대화는_원문이_없으면_text를_빼고_보낸다() {
        json(server, "/v1/chat/respond", """
                {"subjectKey":"s-1","mode":"REPLY","localDate":"2026-11-12",
                 "recentMessages":[{"role":"SOMI","text":"안녕"},{"role":"USER"}],
                 "weekSummary":{"averageLevel":2.5,"recordedDays":2,"changes":[]},"consentedSources":[]}""", """
                {"text":"잠시 쉬어가요.","riskLevel":"NONE","futureField":"x",
                 "cards":[{"type":"ROUTINE_RECOMMENDATION","routineId":"BREATHING","durationSec":60,
                           "title":null,"body":"b"}]}""");

        ChatResult result = engine.respond(new ChatRequest("s-1", ChatMode.REPLY, DAY, null,
                List.of(new ChatTurn(ChatRole.SOMI, "안녕"), new ChatTurn(ChatRole.USER, null)), null,
                new WeekSummary(new BigDecimal("2.5"), 2), Set.of()));

        server.verify();
        assertThat(result.text()).isEqualTo("잠시 쉬어가요.");
        assertThat(result.riskLevel()).isEqualTo(RiskLevel.NONE);
        assertThat(result.cards()).singleElement().satisfies(card -> {
            assertThat(card.type()).isEqualTo(CardType.ROUTINE_RECOMMENDATION);
            assertThat(card.routineId()).isEqualTo("BREATHING");
        });
    }

    @Test
    void 털_깎임은_마음_정리_완료만_보낸다() {
        json(server, "/v1/routines/wool-trim", """
                {"subjectKey":"s-1","localDate":"2026-11-12","journalCompleted":true}""", """
                {"shouldPlay":true,"animationKey":"wool_trim","message":"털 한 뭉치를 덜어냈어요!"}""");

        assertThat(engine.shouldPlay("s-1", DAY)).isTrue();
        server.verify();
    }

    @Test
    void 처리물_삭제는_subjectKey를_경로에_인코딩해_DELETE한다() {
        server.expect(requestTo(BASE + "/v1/subjects/a%2Fb"))
                .andExpect(method(HttpMethod.DELETE))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));

        engine.eraseSubject("a/b");

        server.verify();
    }

    @Test
    void 비정상_상태코드는_예외이고_메시지에_응답_본문을_싣지_않는다() {
        server.expect(requestTo(BASE + "/v1/routines/recommend"))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"code\":\"VALIDATION_ERROR\",\"echo\":\"secret-payload\"}}"));

        assertThatThrownBy(() -> engine.recommend(new RoutineRequest("s-1", DAY, null, null,
                List.of("BREATHING"), List.of())))
                .isInstanceOfSatisfying(EngineHttpException.class, e -> {
                    assertThat(e.status()).isEqualTo(422);
                    assertThat(e.getMessage()).doesNotContain("secret-payload");
                });
    }
}
