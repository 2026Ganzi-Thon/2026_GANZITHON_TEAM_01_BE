package com.som.settings;

import static com.som.support.ApiTestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.support.ApiTestSupport;
import com.som.support.TestSupportConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 도움 받을 곳(A_SPEC §10). URL은 확인 전이라 비어 있다. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
class HelpResourceTest {

    static final String HELP = "/api/v1/help-resources";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        ApiTestSupport.deleteUsers(jdbc, "hlp");
    }

    @Test
    void 도움_받을_곳_4건을_순서대로_주고_링크_URL은_비어_있다() throws Exception {
        MockHttpSession s = ApiTestSupport.signupWithRequiredConsents(mockMvc, objectMapper, "hlp01");

        MvcResult result = mockMvc.perform(get(HELP).session(s)).andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode list = json(objectMapper, result);
        assertThat(list).hasSize(4);
        assertThat(list.get(0).path("name").asText()).isEqualTo("정신 건강 복지 센터");
        assertThat(list.get(0).path("actionType").asText()).isEqualTo("LINK");
        assertThat(list.get(0).path("actionValue").isNull()).isTrue();
        assertThat(list.get(1).path("actionValue").isNull()).isTrue();
        assertThat(list.get(2).path("actionType").asText()).isEqualTo("TEL");
        assertThat(list.get(2).path("actionValue").asText()).isEqualTo("1350");
        assertThat(list.get(3).path("name").asText()).isEqualTo("자살예방상담전화");
        assertThat(list.get(3).path("actionValue").asText()).isEqualTo("109");
    }

    @Test
    void 필수_약관_동의_전에도_볼_수_있다() throws Exception {
        MockHttpSession s = ApiTestSupport.signup(mockMvc, objectMapper, "hlp02");

        assertThat(mockMvc.perform(get(HELP).session(s)).andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void 미인증은_401() throws Exception {
        assertThat(mockMvc.perform(get(HELP)).andReturn().getResponse().getStatus()).isEqualTo(401);
    }
}
