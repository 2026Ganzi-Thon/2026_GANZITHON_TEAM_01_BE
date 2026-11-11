package com.som.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.som.common.error.ErrorCode;
import com.som.common.web.RequestIdProperties;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.Cookie.SameSite;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    private static final String PROTECTED = "/api/v1/me";
    private static final String CSRF = "/api/v1/auth/csrf";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    RequestIdProperties requestIdProps;

    @Autowired
    ServerProperties serverProps;

    /** 공통 에러 형식 {"error":{code,message,requestId}}이고 requestId가 응답 헤더와 같은지 확인한다. */
    private void expectError(RequestBuilder request, int status, ErrorCode code) throws Exception {
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().is(status))
                .andExpect(jsonPath("$", aMapWithSize(1)))
                .andExpect(jsonPath("$.error", aMapWithSize(3)))
                .andExpect(jsonPath("$.error.code").value(code.name()))
                .andExpect(jsonPath("$.error.message").value(code.defaultMessage()))
                .andReturn();
        assertThat(result.getResponse().getContentType()).startsWith("application/json");
        String headerId = result.getResponse().getHeader(requestIdProps.header());
        assertThat(headerId).isNotBlank();
        assertThat((String) JsonPath.read(result.getResponse().getContentAsString(), "$.error.requestId"))
                .isEqualTo(headerId);
    }

    @Test
    void 미인증_보호경로_GET은_401_UNAUTHORIZED_공통형식() throws Exception {
        expectError(get(PROTECTED), 401, ErrorCode.UNAUTHORIZED);
    }

    @Test
    void 미인증_401에도_보낸_요청ID가_그대로_실린다() throws Exception {
        mockMvc.perform(get(PROTECTED).header(requestIdProps.header(), "rid-401"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.requestId").value("rid-401"));
    }

    @Test
    void 미인증_없는_경로도_존재여부를_드러내지_않고_401() throws Exception {
        expectError(get("/api/v1/no-such-path"), 401, ErrorCode.UNAUTHORIZED);
    }

    @Test
    void 미인증_csrf_조회는_200이고_헤더이름_토큰_XSRF쿠키를_준다() throws Exception {
        MvcResult result = mockMvc.perform(get(CSRF))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", aMapWithSize(2)))
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isNotBlank();
        assertThat(cookie.isHttpOnly()).isFalse(); // SPA가 읽어 헤더로 보낸다
    }

    @Test
    @WithMockUser
    void 인증사용자가_CSRF토큰_없이_POST하면_403_CSRF_INVALID() throws Exception {
        expectError(post(PROTECTED), 403, ErrorCode.CSRF_INVALID);
    }

    @Test
    void 미인증사용자가_CSRF토큰_없이_POST해도_403_CSRF_INVALID() throws Exception {
        expectError(post(PROTECTED), 403, ErrorCode.CSRF_INVALID);
    }

    @Test
    @WithMockUser
    void 쿠키와_다른_토큰을_보내면_403_CSRF_INVALID() throws Exception {
        Cookie cookie = mockMvc.perform(get(CSRF)).andReturn().getResponse().getCookie("XSRF-TOKEN");
        expectError(post(PROTECTED).cookie(cookie).header("X-XSRF-TOKEN", "forged-token"),
                403, ErrorCode.CSRF_INVALID);
    }

    @Test
    @WithMockUser
    void 쿠키값을_헤더로_보내면_CSRF단계를_통과한다() throws Exception {
        Cookie cookie = mockMvc.perform(get(CSRF)).andReturn().getResponse().getCookie("XSRF-TOKEN");
        int status = mockMvc.perform(post("/api/v1/no-such-path").cookie(cookie)
                        .header("X-XSRF-TOKEN", cookie.getValue()))
                .andReturn().getResponse().getStatus();
        assertThat(status).isNotEqualTo(403).isEqualTo(404);
    }

    @Test
    @WithMockUser
    void 응답본문의_토큰을_헤더로_보내도_CSRF단계를_통과한다() throws Exception {
        MvcResult issued = mockMvc.perform(get(CSRF)).andReturn();
        Cookie cookie = issued.getResponse().getCookie("XSRF-TOKEN");
        String body = issued.getResponse().getContentAsString();
        String headerName = JsonPath.read(body, "$.headerName");
        String token = JsonPath.read(body, "$.token");

        int status = mockMvc.perform(post("/api/v1/no-such-path").cookie(cookie).header(headerName, token))
                .andReturn().getResponse().getStatus();
        assertThat(status).isNotEqualTo(403).isEqualTo(404);
    }

    @Test
    void 인증불필요_경로_auth와_error는_401이_아니다() throws Exception {
        assertThat(mockMvc.perform(get("/api/v1/auth/no-such")).andReturn().getResponse().getStatus())
                .isNotEqualTo(401);
    }

    @Test
    void 세션_쿠키_설정은_yml대로_바인딩된다() {
        ServerProperties.Servlet servlet = serverProps.getServlet();
        assertThat(servlet.getSession().getTimeout()).isEqualTo(Duration.ofMinutes(30));
        assertThat(servlet.getSession().getCookie().getHttpOnly()).isTrue();
        assertThat(servlet.getSession().getCookie().getSecure()).isTrue();
        assertThat(servlet.getSession().getCookie().getSameSite()).isEqualTo(SameSite.LAX);
    }
}
