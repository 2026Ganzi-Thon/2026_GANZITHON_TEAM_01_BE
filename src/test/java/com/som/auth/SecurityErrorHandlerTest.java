package com.som.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.som.common.error.ErrorCode;
import com.som.common.web.RequestIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.security.web.csrf.MissingCsrfTokenException;

class SecurityErrorHandlerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecurityErrorHandler handler = new SecurityErrorHandler(objectMapper);

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestIdFilter.ATTRIBUTE, "rid-1");
        return request;
    }

    private JsonNode error(MockHttpServletResponse response) throws Exception {
        assertThat(response.getContentType()).startsWith("application/json");
        JsonNode root = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(root.size()).isEqualTo(1);
        assertThat(root.get("error").size()).isEqualTo(3);
        assertThat(root.get("error").get("requestId").asText()).isEqualTo("rid-1");
        return root.get("error");
    }

    @Test
    void 인증없음은_401_UNAUTHORIZED() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.commence(request(), response, new InsufficientAuthenticationException("internal detail"));

        assertThat(response.getStatus()).isEqualTo(401);
        JsonNode error = error(response);
        assertThat(error.get("code").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(response.getContentAsString()).doesNotContain("internal detail");
    }

    @Test
    void CSRF_누락은_403_CSRF_INVALID() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.handle(request(), response, new MissingCsrfTokenException("secret-token"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(error(response).get("code").asText()).isEqualTo("CSRF_INVALID");
        assertThat(response.getContentAsString()).doesNotContain("secret-token");
    }

    @Test
    void CSRF_불일치는_403_CSRF_INVALID() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.handle(request(), response, new InvalidCsrfTokenException(
                new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "expected-secret"), "actual-secret"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(error(response).get("code").asText()).isEqualTo("CSRF_INVALID");
        assertThat(response.getContentAsString()).doesNotContain("expected-secret").doesNotContain("actual-secret");
    }

    @Test
    void 그밖의_인가거부는_403_FORBIDDEN() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.handle(request(), response, new AccessDeniedException("internal detail"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(error(response).get("code").asText()).isEqualTo("FORBIDDEN");
        assertThat(response.getContentAsString()).doesNotContain("internal detail");
    }

    @Test
    void CSRF_INVALID는_403이다() {
        assertThat(ErrorCode.CSRF_INVALID.status().value()).isEqualTo(403);
        assertThat(ErrorCode.CSRF_INVALID.defaultMessage()).isNotBlank();
    }
}
