package com.som.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** springdoc 자동 생성 문서가 실제 응답 형식과 일치하는지 확인한다(A-09). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocsTest {

    private static final String DOCS = "/v3/api-docs";
    private static final List<String> PUBLIC = List.of(
            "/api/v1/auth/login", "/api/v1/auth/signup", "/api/v1/auth/logout", "/api/v1/auth/csrf");

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    private JsonNode docs() throws Exception {
        String body = mockMvc.perform(get(DOCS))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    /** $ref("#/components/schemas/X")면 해당 스키마로 따라간다. */
    private JsonNode resolve(JsonNode docs, JsonNode schema) {
        JsonNode ref = schema.get("$ref");
        if (ref == null) {
            return schema;
        }
        String name = ref.asText().substring("#/components/schemas/".length());
        return docs.path("components").path("schemas").path(name);
    }

    private static List<String> fieldNames(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private JsonNode errorBodySchema(JsonNode docs) {
        JsonNode errorResponse = docs.path("components").path("schemas").path("ErrorResponse");
        return resolve(docs, errorResponse.path("properties").path("error"));
    }

    @Test
    void 미인증_api_docs_200_JSON() throws Exception {
        docs();
    }

    @Test
    void info_title은_SOM_API이고_버전이_있다() throws Exception {
        JsonNode info = docs().path("info");
        assertThat(info.path("title").asText()).isEqualTo("SOM API");
        assertThat(info.path("version").asText()).isNotBlank();
    }

    @Test
    void ErrorResponse_스키마에_error_code_message_requestId가_있다() throws Exception {
        JsonNode docs = docs();
        JsonNode errorResponse = docs.path("components").path("schemas").path("ErrorResponse");
        assertThat(errorResponse.isMissingNode()).isFalse();
        assertThat(fieldNames(errorResponse.path("properties"))).containsExactly("error");
        assertThat(fieldNames(errorBodySchema(docs).path("properties")))
                .containsExactlyInAnyOrder("code", "message", "requestId");
    }

    @Test
    void paths에_csrf_경로가_있다() throws Exception {
        assertThat(docs().path("paths").has("/api/v1/auth/csrf")).isTrue();
    }

    @Test
    void paths에_체크인_상태_기록_경로가_있다() throws Exception {
        assertThat(fieldNames(docs().path("paths"))).contains(
                "/api/v1/checkins", "/api/v1/state/today", "/api/v1/insights/week", "/api/v1/insights");
    }

    @Test
    void 모든_operation에_403_500_ErrorResponse가_붙고_401은_인증필요_operation에만_붙는다() throws Exception {
        JsonNode docs = docs();
        JsonNode paths = docs.path("paths");
        assertThat(paths.size()).isPositive();
        paths.fields().forEachRemaining(path -> path.getValue().forEach(operation -> {
            boolean isPublic = PUBLIC.contains(path.getKey());
            for (String status : List.of("401", "403", "500")) {
                JsonNode response = operation.path("responses").path(status);
                String label = path.getKey() + " " + status;
                if (status.equals("401") && isPublic) {
                    assertThat(response.isMissingNode()).as(label).isTrue();
                    continue;
                }
                assertThat(response.path("content").path("application/json").path("schema").path("$ref").asText())
                        .as(label).isEqualTo("#/components/schemas/ErrorResponse");
            }
        }));
    }

    @Test
    void 로그인_operation에는_공통_401이_없다() throws Exception {
        JsonNode login = docs().path("paths").path("/api/v1/auth/login").path("post");
        assertThat(login.isMissingNode()).isFalse();
        assertThat(login.path("responses").has("401")).isFalse();
    }

    @Test
    void securitySchemes에_세션_쿠키와_CSRF_헤더가_있다() throws Exception {
        JsonNode schemes = docs().path("components").path("securitySchemes");
        JsonNode session = schemes.path("sessionCookie");
        assertThat(session.path("type").asText()).isEqualTo("apiKey");
        assertThat(session.path("in").asText()).isEqualTo("cookie");
        assertThat(session.path("name").asText()).isEqualTo("JSESSIONID");
        JsonNode csrf = schemes.path("csrfHeader");
        assertThat(csrf.path("type").asText()).isEqualTo("apiKey");
        assertThat(csrf.path("in").asText()).isEqualTo("header");
        assertThat(csrf.path("name").asText()).isEqualTo("X-XSRF-TOKEN");
    }

    @Test
    void 문서의_ErrorResponse_속성과_실제_401_응답_키가_같다() throws Exception {
        JsonNode docs = docs();
        String body = mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        JsonNode actual = objectMapper.readTree(body);

        JsonNode errorResponse = docs.path("components").path("schemas").path("ErrorResponse");
        assertThat(fieldNames(actual))
                .containsExactlyInAnyOrderElementsOf(fieldNames(errorResponse.path("properties")));
        assertThat(fieldNames(actual.path("error")))
                .containsExactlyInAnyOrderElementsOf(fieldNames(errorBodySchema(docs).path("properties")));
    }

    @Test
    void 테스트_전용_컨트롤러_경로는_문서에_없다() throws Exception {
        List<String> paths = fieldNames(docs().path("paths"));
        assertThat(paths).isNotEmpty().noneMatch(p -> p.startsWith("/test"));
    }
}
