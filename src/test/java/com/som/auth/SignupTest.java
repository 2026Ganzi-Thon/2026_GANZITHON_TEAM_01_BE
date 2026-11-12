package com.som.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.auth.domain.User;
import com.som.auth.repository.UserRepository;
import com.som.common.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class SignupTest {

    static final Instant FIXED = Instant.parse("2026-11-07T14:00:00Z"); // KST 23:00
    static final String SIGNUP = "/api/v1/auth/signup";
    static final String LOGIN = "/api/v1/auth/login";
    static final String ME = "/test/auth/me";
    static final String PASSWORD = "abcde12345";

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED, ZoneId.of("Asia/Seoul"));
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository users;
    @Autowired ObjectMapper objectMapper;

    @AfterEach
    void cleanUp() {
        users.findAll().stream().filter(u -> u.getLoginId().startsWith("su")).forEach(users::delete);
    }

    private String body(String loginId, String password, String confirm) throws Exception {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("loginId", loginId);
        map.put("password", password);
        map.put("passwordConfirm", confirm);
        return objectMapper.writeValueAsString(map);
    }

    private MvcResult signup(MockHttpSession session, String json) throws Exception {
        return mockMvc.perform(post(SIGNUP).session(session).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andReturn();
    }

    private MvcResult signup(String loginId, String password, String confirm) throws Exception {
        return signup(new MockHttpSession(), body(loginId, password, confirm));
    }

    private JsonNode error(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("error");
    }

    @Test
    void 정상_가입이면_201_loginId만_응답하고_Argon2_해시가_저장되며_바로_로그인된다(CapturedOutput output)
            throws Exception {
        MockHttpSession session = new MockHttpSession();
        String before = session.getId();

        MvcResult result = signup(session, body("suok01", PASSWORD, PASSWORD));

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.size()).isEqualTo(1);
        assertThat(json.path("loginId").asText()).isEqualTo("suok01");
        String raw = result.getResponse().getContentAsString();
        assertThat(raw).doesNotContain("subjectKey").doesNotContain("passwordHash").doesNotContain("\"id\"");

        User user = users.findByLoginId("suok01").orElseThrow();
        assertThat(user.getPasswordHash()).startsWith("$argon2id$").doesNotContain(PASSWORD);
        assertThat(user.getSubjectKey()).isNotBlank();
        assertThat(user.getCreatedAt().toInstant()).isEqualTo(FIXED);
        assertThat(user.getLastLoginAt().toInstant()).isEqualTo(FIXED);

        // 자동 로그인: 세션 ID가 바뀌고 같은 세션으로 보호 엔드포인트 호출 성공
        assertThat(session.getId()).isNotEqualTo(before);
        mockMvc.perform(get(ME).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(user.getId()));

        assertThat(output.getAll()).doesNotContain(PASSWORD);
    }

    @ParameterizedTest
    @CsvSource({"sua,400", "su4a,201", "su34567890123,201", "su345678901234,400"})
    void 아이디_길이_경계_3_4_13_14자(String loginId, int expected) throws Exception {
        MvcResult result = signup(loginId, PASSWORD, PASSWORD);
        assertThat(result.getResponse().getStatus()).isEqualTo(expected);
        if (expected == 400) {
            assertThat(error(result).path("code").asText()).isEqualTo("VALIDATION_ERROR");
            assertThat(error(result).path("message").asText()).contains("loginId:").doesNotContain(loginId);
        }
    }

    @ParameterizedTest
    @CsvSource({"supw09,abcde1234,400", "supw10,abcde12345,201", "supw12,abcde1234567,201",
            "supw13,abcde12345678,400"})
    void 비밀번호_길이_경계_9_10_12_13자(String loginId, String password, int expected) throws Exception {
        MvcResult result = signup(loginId, password, password);
        assertThat(result.getResponse().getStatus()).isEqualTo(expected);
        if (expected == 400) {
            assertThat(error(result).path("code").asText()).isEqualTo("VALIDATION_ERROR");
            assertThat(error(result).path("message").asText()).contains("password:").doesNotContain(password);
        }
    }

    @ParameterizedTest
    @CsvSource({"SuUpper1,abcde12345", "suletter,abcdefghij", "sudigit1,1234567890"})
    void 대문자_아이디와_영문만_숫자만_비밀번호는_400이고_자동변환하지_않는다(String loginId, String password)
            throws Exception {
        MvcResult result = signup(loginId, password, password);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(error(result).path("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(error(result).path("message").asText()).doesNotContain(password);
        assertThat(users.existsByLoginId(loginId.toLowerCase())).isFalse();
    }

    @Test
    void 비밀번호_앞뒤_공백은_자르지_않는다() throws Exception {
        String padded = " abcd1234 "; // 공백 포함 10자. trim하면 8자가 되어 거부될 것
        MvcResult result = signup("sutrim1", padded, padded);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        MvcResult trimmed = mockMvc.perform(post(LOGIN).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("loginId", "sutrim1", "password", "abcd1234"))))
                .andReturn();
        assertThat(trimmed.getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void 비밀번호_확인_불일치는_400이고_응답에_비밀번호가_없다(CapturedOutput output) throws Exception {
        MvcResult result = signup("sumis01", PASSWORD, "zyxwv98765");

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(error(result).path("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(error(result).path("message").asText()).isEqualTo("passwordConfirm: 비밀번호가 일치하지 않습니다");
        String raw = result.getResponse().getContentAsString();
        assertThat(raw).doesNotContain(PASSWORD).doesNotContain("zyxwv98765");
        assertThat(output.getAll()).doesNotContain(PASSWORD).doesNotContain("zyxwv98765");
        assertThat(users.existsByLoginId("sumis01")).isFalse();
    }

    @Test
    void 필드가_빠지면_400() throws Exception {
        MvcResult result = signup(new MockHttpSession(), "{}");
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(error(result).path("code").asText()).isEqualTo("VALIDATION_ERROR");
    }

    @Test
    void 중복_아이디는_409_CONFLICT() throws Exception {
        assertThat(signup("sudup01", PASSWORD, PASSWORD).getResponse().getStatus()).isEqualTo(201);

        MvcResult again = signup("sudup01", "other12345", "other12345");

        assertThat(again.getResponse().getStatus()).isEqualTo(409);
        assertThat(error(again).path("code").asText()).isEqualTo(ErrorCode.CONFLICT.name());
        assertThat(error(again).path("message").asText()).isEqualTo("이미 사용 중인 아이디예요.");
    }

    @ParameterizedTest
    @CsvSource({"authProvider,\"GOOGLE\"", "subjectKey,\"abc\"", "userId,1", "email,\"a@b.c\""})
    void 바디에_추가_필드가_있으면_400(String field, String value) throws Exception {
        String json = "{\"loginId\":\"suextra1\",\"password\":\"" + PASSWORD + "\",\"passwordConfirm\":\""
                + PASSWORD + "\",\"" + field + "\":" + value + "}";

        MvcResult result = signup(new MockHttpSession(), json);

        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(error(result).path("code").asText()).isEqualTo("VALIDATION_ERROR");
        assertThat(error(result).path("message").asText()).isEqualTo(field + ": 허용되지 않는 필드입니다");
        assertThat(users.existsByLoginId("suextra1")).isFalse();
    }

    @Test
    void CSRF_없으면_403_CSRF_INVALID() throws Exception {
        mockMvc.perform(post(SIGNUP).contentType(MediaType.APPLICATION_JSON)
                        .content(body("sucsrf1", PASSWORD, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value(ErrorCode.CSRF_INVALID.name()));
        assertThat(users.existsByLoginId("sucsrf1")).isFalse();
    }

    @Test
    void 가입한_자격으로_로그인할_수_있다() throws Exception {
        assertThat(signup("sulogin1", PASSWORD, PASSWORD).getResponse().getStatus()).isEqualTo(201);

        mockMvc.perform(post(LOGIN).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("loginId", "sulogin1", "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("sulogin1"));
    }

    @Test
    void 다른_사용자로_가입해도_각자_세션은_자기_userId만_본다() throws Exception {
        MockHttpSession a = new MockHttpSession();
        MockHttpSession b = new MockHttpSession();
        assertThat(signup(a, body("suusera", PASSWORD, PASSWORD)).getResponse().getStatus()).isEqualTo(201);
        assertThat(signup(b, body("suuserb", PASSWORD, PASSWORD)).getResponse().getStatus()).isEqualTo(201);
        Long idA = users.findByLoginId("suusera").orElseThrow().getId();
        Long idB = users.findByLoginId("suuserb").orElseThrow().getId();

        mockMvc.perform(get(ME).session(a).param("userId", String.valueOf(idB)))
                .andExpect(jsonPath("$.userId").value(idA));
        mockMvc.perform(get(ME).session(b).param("userId", String.valueOf(idA)))
                .andExpect(jsonPath("$.userId").value(idB));
    }
}
