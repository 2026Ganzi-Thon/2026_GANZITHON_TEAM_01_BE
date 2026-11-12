package com.som.security;

import static com.som.support.ApiTestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.support.ApiTestSupport;
import com.som.support.TestSupportConfig;
import com.som.support.UserDataSeeder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** A_SPEC §16 보안 출시 게이트. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class SecurityGateTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Value("${server.servlet.session.timeout}") String sessionTimeout;

    @AfterEach
    void cleanUp() {
        ApiTestSupport.deleteUsers(jdbc, "sgt");
    }

    @Test
    void 만료된_세션은_401이고_세션_만료가_설정돼_있다() throws Exception {
        assertThat(sessionTimeout).isNotBlank();
        // 서버 세션이 만료되면 그 세션에는 SecurityContext가 없다 = 새 빈 세션과 같다
        MockHttpSession expired = new MockHttpSession();
        expired.setMaxInactiveInterval(1);
        for (String path : List.of("/api/v1/me", "/api/v1/state/today", "/api/v1/chat/dates?month=2026-11")) {
            assertThat(mockMvc.perform(get(path).session(expired)).andReturn().getResponse().getStatus())
                    .as(path).isEqualTo(401);
        }
    }

    @Test
    void 로그에_비밀번호_세션ID_대화_마음정리_연동값이_남지_않는다(CapturedOutput output) throws Exception {
        MockHttpSession s = UserDataSeeder.seedAll(mockMvc, objectMapper, "sgt01");
        mockMvc.perform(post("/api/v1/chat/messages").session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("text", "chat-secret-4471")))).andReturn();
        mockMvc.perform(post("/api/v1/signals").session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"source\":\"STEPS\",\"localDate\":\"2026-11-11\","
                        + "\"entries\":[{\"metric\":\"STEPS_DAILY\",\"value\":98765}]}")).andReturn();
        mockMvc.perform(post("/api/v1/journal").session(s).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"text\":\"journal-secret-8812\"}")).andReturn();
        mockMvc.perform(post("/api/v1/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"loginId\":\"sgt01\",\"password\":\"wrongpass999\"}")).andReturn();

        String logs = output.getAll();
        assertThat(logs).doesNotContain(ApiTestSupport.PASSWORD).doesNotContain("wrongpass999")
                .doesNotContain(s.getId()).doesNotContain("chat-secret-4471").doesNotContain("journal-secret-8812")
                .doesNotContain("98765");
    }

    @Test
    void 설정_파일에_비밀값이_없고_env는_git에서_제외된다() throws Exception {
        Pattern secretKey = Pattern.compile("(?i)^\\s*(password|secret|token|api-key)\\s*:\\s*(.+)$");
        for (Path yml : List.of(Path.of("src/main/resources/application.yml"),
                Path.of("src/test/resources/application-test.yml"))) {
            for (String line : Files.readAllLines(yml, StandardCharsets.UTF_8)) {
                var m = secretKey.matcher(line);
                if (m.find()) {
                    assertThat(m.group(2).trim()).as(yml + ": " + m.group(1)).startsWith("${");
                }
            }
        }
        assertThat(Files.readAllLines(Path.of(".gitignore"), StandardCharsets.UTF_8)).anyMatch(l -> l.trim().equals(".env"));
        assertThat(Files.readString(Path.of(".env.example"), StandardCharsets.UTF_8))
                .doesNotContainPattern("(?m)^SOM_DB_PASSWORD=.+$");
    }

    @Test
    void 도움_기관은_전화번호_숫자만_링크는_비었거나_https() throws Exception {
        MockHttpSession s = ApiTestSupport.signup(mockMvc, objectMapper, "sgt02");
        for (JsonNode r : json(objectMapper, mockMvc.perform(get("/api/v1/help-resources").session(s)).andReturn())) {
            String type = r.path("actionType").asText();
            JsonNode value = r.path("actionValue");
            if (type.equals("TEL")) {
                assertThat(value.asText()).matches("\\d+");
            } else {
                assertThat(value.isNull() || value.asText().startsWith("https://")).as(r.toString()).isTrue();
            }
        }
    }

    @Test
    void 두_계정_접근_제어_B는_A의_어떤_리소스도_볼_수_없다() throws Exception {
        MockHttpSession a = UserDataSeeder.seedAll(mockMvc, objectMapper, "sgt03a");
        MockHttpSession b = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "sgt03b");
        Long aId = UserDataSeeder.userId(jdbc, "sgt03a");
        Long aCard = jdbc.queryForObject("SELECT id FROM chat_messages WHERE user_id = ? "
                + "AND message_type = 'ROUTINE_RECOMMENDATION' LIMIT 1", Long.class, aId);
        String aCursor = json(objectMapper, mockMvc.perform(get("/api/v1/chat/messages?date=2026-11-11&size=1")
                .session(a)).andReturn()).path("nextCursor").asText();

        assertThat(status(b, get("/api/v1/checkins?date=2026-11-11&userId=" + aId))).isEqualTo(404);
        assertThat(json(objectMapper, call(b, get("/api/v1/journal?date=2026-11-11")))).isEmpty();
        assertThat(json(objectMapper, call(b, get("/api/v1/chat/messages?date=2026-11-11&cursor=" + aCursor)))
                .path("items")).noneMatch(m -> m.path("role").asText().equals("USER"));
        assertThat(status(b, post("/api/v1/chat/recommendations/" + aCard + "/response").with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"response\":\"DECLINED\"}"))).isEqualTo(404);
        assertThat(json(objectMapper, call(b, get("/api/v1/me/connections")))).allSatisfy(c ->
                assertThat(c.path("status").asText()).isEqualTo("NOT_CONNECTED"));
        assertThat(json(objectMapper, call(b, get("/api/v1/insights?days=7"))).path("recordedDays").asInt()).isZero();
        assertThat(json(objectMapper, call(b, get("/api/v1/routines/today"))).path("journalUnlocked").asBoolean())
                .isFalse();
        assertThat(json(objectMapper, call(b, get("/api/v1/me/notification-settings"))).path("enabled").asBoolean())
                .isFalse();
        assertThat(json(objectMapper, call(b, get("/api/v1/me"))).path("loginId").asText()).isEqualTo("sgt03b");
        assertThat(status(b, get("/api/v1/me/deletion-status"))).isEqualTo(404);
    }

    @Test
    void OpenAPI_문서에_삭제_탈퇴_경로가_있다() throws Exception {
        JsonNode paths = json(objectMapper, mockMvc.perform(get("/v3/api-docs")).andReturn()).path("paths");
        assertThat(paths.path("/api/v1/me").has("delete")).isTrue();
        assertThat(paths.path("/api/v1/me/data").has("delete")).isTrue();
        assertThat(paths.has("/api/v1/me/deletion-status")).isTrue();
    }

    private MvcResult call(MockHttpSession s,
                           org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req)
            throws Exception {
        MvcResult r = mockMvc.perform(req.session(s)).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        return r;
    }

    private int status(MockHttpSession s,
                       org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req) throws Exception {
        return mockMvc.perform(req.session(s)).andReturn().getResponse().getStatus();
    }
}
