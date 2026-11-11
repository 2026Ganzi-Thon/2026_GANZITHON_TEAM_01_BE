package com.som.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.som.common.web.RequestIdProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    RequestIdProperties props;

    /**
     * 상태 코드·error.code를 확인하고, 형식이 {"error":{code,message,requestId}}뿐이며
     * requestId가 X-Request-ID 응답 헤더와 같은지 검증한다. 응답 본문을 돌려준다.
     */
    private String expectError(RequestBuilder request, int status, ErrorCode code) throws Exception {
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().is(status))
                .andExpect(jsonPath("$", aMapWithSize(1)))
                .andExpect(jsonPath("$.error", aMapWithSize(3)))
                .andExpect(jsonPath("$.error.code").value(code.name()))
                .andExpect(jsonPath("$.error.message").isNotEmpty())
                .andReturn();
        String headerId = result.getResponse().getHeader(props.header());
        assertThat(headerId).isNotBlank();
        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"requestId\":\"" + headerId + "\"");
        return body;
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String body) {
        return builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void ErrorCode는_명세의_상태코드와_기본문구를_갖는다() {
        assertThat(ErrorCode.VALIDATION_ERROR.status().value()).isEqualTo(400);
        assertThat(ErrorCode.MALFORMED_REQUEST.status().value()).isEqualTo(400);
        assertThat(ErrorCode.UNAUTHORIZED.status().value()).isEqualTo(401);
        assertThat(ErrorCode.FORBIDDEN.status().value()).isEqualTo(403);
        assertThat(ErrorCode.CONSENT_REQUIRED.status().value()).isEqualTo(403);
        assertThat(ErrorCode.NOT_FOUND.status().value()).isEqualTo(404);
        assertThat(ErrorCode.METHOD_NOT_ALLOWED.status().value()).isEqualTo(405);
        assertThat(ErrorCode.CONFLICT.status().value()).isEqualTo(409);
        assertThat(ErrorCode.RATE_LIMITED.status().value()).isEqualTo(429);
        assertThat(ErrorCode.INTERNAL_ERROR.status().value()).isEqualTo(500);
        assertThat(ErrorCode.ENGINE_UNAVAILABLE.status().value()).isEqualTo(503);
        assertThat(ErrorCode.values()).hasSize(11);
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.defaultMessage()).isNotBlank();
        }
    }

    @Test
    void ErrorResponse_of는_코드이름과_메시지와_요청ID를_담는다() {
        ErrorResponse response = ErrorResponse.of(ErrorCode.NOT_FOUND, "없음", "rid-1");
        assertThat(response.error().code()).isEqualTo("NOT_FOUND");
        assertThat(response.error().message()).isEqualTo("없음");
        assertThat(response.error().requestId()).isEqualTo("rid-1");
    }

    @Test
    void 요청ID_헤더를_보내면_에러응답_requestId도_같은_값이다() throws Exception {
        mockMvc.perform(get("/test/errors/api").header(props.header(), "rid-abc-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.requestId").value("rid-abc-1"));
    }

    @Test
    void DTO_검증_실패는_400_VALIDATION_ERROR이고_거부값을_노출하지_않는다() throws Exception {
        String body = expectError(
                json(post("/test/errors/signup"), "{\"loginId\":\"user1\",\"password\":\"secret-value-123\"}"),
                400, ErrorCode.VALIDATION_ERROR);
        assertThat(body).doesNotContain("secret-value-123").contains("password");
    }

    @Test
    void 모르는_enum_값은_400_VALIDATION_ERROR이고_값을_노출하지_않는다() throws Exception {
        String body = expectError(json(post("/test/errors/mood"), "{\"mood\":\"HAPPY\"}"),
                400, ErrorCode.VALIDATION_ERROR);
        assertThat(body).doesNotContain("HAPPY").doesNotContain("Mood").contains("mood");
    }

    @Test
    void 메서드_파라미터_검증_실패는_400_VALIDATION_ERROR이고_거부값을_노출하지_않는다() throws Exception {
        String body = expectError(get("/test/errors/param").param("name", "toolong-value"),
                400, ErrorCode.VALIDATION_ERROR);
        assertThat(body).doesNotContain("toolong-value").contains("name");
    }

    @Test
    void ConstraintViolationException은_400_VALIDATION_ERROR이고_거부값을_노출하지_않는다() throws Exception {
        String body = expectError(json(post("/test/errors/violation"), "{\"password\":\"secret-value-123\"}"),
                400, ErrorCode.VALIDATION_ERROR);
        assertThat(body).doesNotContain("secret-value-123").contains("password");
    }

    @Test
    void 타입_불일치_파라미터는_400_VALIDATION_ERROR이고_값을_노출하지_않는다() throws Exception {
        String body = expectError(get("/test/errors/typed").param("days", "abc-xyz"),
                400, ErrorCode.VALIDATION_ERROR);
        assertThat(body).doesNotContain("abc-xyz").contains("days");
    }

    @Test
    void 필수_파라미터_누락은_400_VALIDATION_ERROR() throws Exception {
        String body = expectError(get("/test/errors/typed"), 400, ErrorCode.VALIDATION_ERROR);
        assertThat(body).contains("days");
    }

    @Test
    void JSON_문법_오류는_400_MALFORMED_REQUEST이고_원문을_노출하지_않는다() throws Exception {
        String body = expectError(json(post("/test/errors/mood"), "{\"mood\": secret-raw"),
                400, ErrorCode.MALFORMED_REQUEST);
        assertThat(body).doesNotContain("secret-raw");
    }

    @Test
    void 빈_바디는_400_MALFORMED_REQUEST() throws Exception {
        expectError(json(post("/test/errors/mood"), ""), 400, ErrorCode.MALFORMED_REQUEST);
    }

    @Test
    void ApiException은_그_코드와_기본문구로_응답한다() throws Exception {
        expectError(get("/test/errors/api"), 409, ErrorCode.CONFLICT);
        mockMvc.perform(get("/test/errors/api"))
                .andExpect(jsonPath("$.error.message").value(ErrorCode.CONFLICT.defaultMessage()));
    }

    @Test
    void ApiException에_메시지를_지정하면_그_메시지로_응답한다() throws Exception {
        expectError(get("/test/errors/api-message"), 403, ErrorCode.CONSENT_REQUIRED);
        mockMvc.perform(get("/test/errors/api-message"))
                .andExpect(jsonPath("$.error.message").value("필수 약관 동의가 필요합니다."));
    }

    @Test
    void 처리되지_않은_예외는_500_INTERNAL_ERROR이고_내부정보를_노출하지_않는다() throws Exception {
        String body = expectError(get("/test/errors/boom"), 500, ErrorCode.INTERNAL_ERROR);
        assertThat(body)
                .doesNotContain("boom-internal-detail")
                .doesNotContain("IllegalStateException")
                .doesNotContain("java.")
                .doesNotContain("ErrorTestController")
                .doesNotContain("at com.");
    }

    @Test
    void 없는_경로는_404_NOT_FOUND() throws Exception {
        expectError(get("/test/errors/no-such-path"), 404, ErrorCode.NOT_FOUND);
    }

    @Test
    void 잘못된_메서드는_405_METHOD_NOT_ALLOWED() throws Exception {
        expectError(delete("/test/errors/api"), 405, ErrorCode.METHOD_NOT_ALLOWED);
    }
}
