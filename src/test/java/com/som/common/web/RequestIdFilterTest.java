package com.som.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RequestIdFilterTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    RequestIdProperties props;

    private String responseId(MvcResult result) {
        return result.getResponse().getHeader(props.header());
    }

    private void assertIsUuid(String id) {
        assertThat(id).isNotNull();
        assertThat(UUID.fromString(id).toString()).isEqualTo(id);
    }

    @Test
    void 헤더가_없으면_UUID를_발급해_요청속성_MDC_응답헤더에_같은_값을_넣는다() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/ping")).andExpect(status().isOk()).andReturn();

        String id = responseId(result);
        assertIsUuid(id);
        mockMvc.perform(get("/test/ping").header(props.header(), id))
                .andExpect(jsonPath("$.attribute").value(id))
                .andExpect(jsonPath("$.mdc").value(id));
    }

    @Test
    void 요청속성과_MDC와_응답헤더는_같은_발급값이다() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/ping")).andReturn();

        String id = responseId(result);
        assertIsUuid(id);
        assertThat(result.getResponse().getContentAsString())
                .contains("\"attribute\":\"" + id + "\"")
                .contains("\"mdc\":\"" + id + "\"");
    }

    @Test
    void 유효한_헤더는_그대로_재사용한다() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/ping").header(props.header(), "abc-123"))
                .andExpect(jsonPath("$.attribute").value("abc-123"))
                .andExpect(jsonPath("$.mdc").value("abc-123"))
                .andReturn();

        assertThat(responseId(result)).isEqualTo("abc-123");
    }

    @Test
    void 최대길이와_같은_헤더는_재사용한다() throws Exception {
        String exact = "a".repeat(props.maxLength());
        MvcResult result = mockMvc.perform(get("/test/ping").header(props.header(), exact)).andReturn();

        assertThat(responseId(result)).isEqualTo(exact);
    }

    @ParameterizedTest
    @ValueSource(strings = {"a b", "<script>", "abc\ndef", "abc\r\nX-Injected: 1", "", "가나다", "abc_123"})
    void 형식_위반_헤더는_새_UUID로_교체하고_원래값을_반환하지_않는다(String bad) throws Exception {
        MvcResult result = mockMvc.perform(get("/test/ping").header(props.header(), bad)).andReturn();

        String id = responseId(result);
        assertIsUuid(id);
        assertThat(id).isNotEqualTo(bad);
        if (!bad.isEmpty()) {
            assertThat(result.getResponse().getContentAsString()).doesNotContain(bad);
        }
    }

    @Test
    void 최대길이를_넘는_헤더는_새_UUID로_교체한다() throws Exception {
        String tooLong = "a".repeat(props.maxLength() + 1);
        MvcResult result = mockMvc.perform(get("/test/ping").header(props.header(), tooLong)).andReturn();

        String id = responseId(result);
        assertIsUuid(id);
        assertThat(result.getResponse().getContentAsString()).doesNotContain(tooLong);
    }

    @Test
    void 요청이_끝나면_MDC에_requestId가_남지_않는다() throws Exception {
        mockMvc.perform(get("/test/ping").header(props.header(), "abc-123")).andExpect(status().isOk());

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void 설정값은_yml에서_온다() {
        assertThat(props.header()).isEqualTo("X-Request-ID");
        assertThat(props.maxLength()).isEqualTo(64);
    }

    @Test
    void 필터를_거치지_않은_요청은_current가_null() {
        assertThat(RequestIds.current(new MockHttpServletRequest())).isNull();
    }
}
