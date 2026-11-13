package com.som.recovery;

import static com.som.support.ApiTestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.support.ApiTestSupport;
import com.som.support.TestSupportConfig;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
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

/** 회복 팁 익명 공유: 작성자는 응답에 드러나지 않고, 지우기는 본인 글만, 기록 삭제·탈퇴에 포함된다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
@ExtendWith(OutputCaptureExtension.class)
class RecoveryShareTest {

    static final String SECRET = "산책하니 좀 나아졌어요 share-secret-5521";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        ApiTestSupport.deleteUsers(jdbc, "rcs");
    }

    private MvcResult share(MockHttpSession s, String message) throws Exception {
        return mockMvc.perform(post("/api/v1/recovery-shares").session(s).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("message", message)))).andReturn();
    }

    private JsonNode list(MockHttpSession s) throws Exception {
        return json(objectMapper, mockMvc.perform(get("/api/v1/recovery-shares").session(s)).andReturn());
    }

    private JsonNode find(JsonNode items, long id) {
        for (JsonNode item : items) {
            if (item.path("id").asLong() == id) {
                return item;
            }
        }
        return null;
    }

    private int remove(MockHttpSession s, long id) throws Exception {
        return mockMvc.perform(delete("/api/v1/recovery-shares/" + id).session(s).with(csrf())).andReturn()
                .getResponse().getStatus();
    }

    @Test
    void 공유하면_201이고_다른_사용자에게는_작성자_정보_없이_보인다() throws Exception {
        MockHttpSession a = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "rcs01");
        MockHttpSession b = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "rcs02");

        MvcResult created = share(a, "  산책하니 좀 나아졌어요  ");
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        long id = json(objectMapper, created).path("id").asLong();

        JsonNode mine = find(list(a), id);
        assertThat(mine.path("message").asText()).isEqualTo("산책하니 좀 나아졌어요");
        assertThat(mine.path("mine").asBoolean()).isTrue();

        JsonNode theirs = find(list(b), id);
        assertThat(theirs.path("mine").asBoolean()).isFalse();
        assertThat(theirs.fieldNames()).toIterable().containsExactlyInAnyOrder("id", "message", "mine");
    }

    @Test
    void 다른_사용자의_글은_지울_수_없고_본인은_지울_수_있다() throws Exception {
        MockHttpSession a = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "rcs03");
        MockHttpSession b = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "rcs04");
        long id = json(objectMapper, share(a, "물 한 잔 마시기")).path("id").asLong();

        assertThat(remove(b, id)).isEqualTo(404);
        assertThat(find(list(a), id)).isNotNull();

        assertThat(remove(a, id)).isEqualTo(204);
        assertThat(find(list(a), id)).isNull();
        assertThat(remove(a, id)).isEqualTo(404);
    }

    @Test
    void 빈_글_200자_초과_줄바꿈은_400이고_저장되지_않는다() throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "rcs05");

        assertThat(share(s, "   ").getResponse().getStatus()).isEqualTo(400);
        assertThat(share(s, "가".repeat(201)).getResponse().getStatus()).isEqualTo(400);
        assertThat(share(s, "첫 줄\n둘째 줄").getResponse().getStatus()).isEqualTo(400);
        assertThat(share(s, "가".repeat(200)).getResponse().getStatus()).isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM recovery_shares r JOIN users u ON u.id = r.user_id "
                + "WHERE u.login_id = 'rcs05'", Integer.class)).isEqualTo(1);
    }

    @Test
    void 미인증은_401_CSRF_없으면_403() throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "rcs06");

        assertThat(mockMvc.perform(get("/api/v1/recovery-shares")).andReturn().getResponse().getStatus())
                .isEqualTo(401);
        assertThat(mockMvc.perform(post("/api/v1/recovery-shares").session(s)
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"x\"}")).andReturn()
                .getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void 기록_삭제와_탈퇴에_공유글도_포함된다() throws Exception {
        MockHttpSession a = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "rcs07");
        MockHttpSession b = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "rcs08");
        long first = json(objectMapper, share(a, "기록 삭제 대상")).path("id").asLong();

        mockMvc.perform(delete("/api/v1/me/data").session(a).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":true}")).andReturn();
        assertThat(find(list(b), first)).isNull();

        long second = json(objectMapper, share(a, "탈퇴 삭제 대상")).path("id").asLong();
        mockMvc.perform(delete("/api/v1/me").session(a).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"confirm\":true}")).andReturn();
        assertThat(find(list(b), second)).isNull();
    }

    @Test
    void 공유글_원문은_로그에_남지_않는다(CapturedOutput output) throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "rcs09");

        share(s, SECRET);
        share(s, SECRET + "\n");

        assertThat(output.getAll()).doesNotContain("share-secret-5521");
    }
}
