package com.som.engine.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;

/** real 모드: B(FastAPI)를 JSON over HTTP로 부르는 클라이언트 구성(ADR-001). */
@Configuration
@ConditionalOnProperty(prefix = "som.engine", name = "mode", havingValue = "real")
public class EngineHttpConfig {

    @Bean
    HttpEngine httpEngine(@Value("${som.engine.url}") URI url, @Value("${som.engine.token}") String token) {
        return new HttpEngine(configure(RestClient.builder(), url, token).build());
    }

    /** 테스트도 같은 구성을 쓰도록 분리했다. */
    static RestClient.Builder configure(RestClient.Builder builder, URI url, String token) {
        if (url.getHost() == null || !("http".equals(url.getScheme()) || "https".equals(url.getScheme()))
                || url.getUserInfo() != null || url.getQuery() != null || url.getFragment() != null) {
            throw new IllegalArgumentException("som.engine.url must be an HTTP(S) service address");
        }
        if (token == null || token.isBlank() || token.chars().anyMatch(c -> c < 33 || c > 126)) {
            throw new IllegalArgumentException("som.engine.token must be set in real mode");
        }
        // JDK HttpClient는 EngineGateway가 타임아웃 때 거는 인터럽트에 반응해 호출을 끝낸다
        HttpClient transport = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(1))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(transport);
        requests.setReadTimeout(Duration.ofSeconds(14));
        return builder.baseUrl(url.toString())
                .requestFactory(requests)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .messageConverters(converters -> {
                    converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
                    converters.add(new MappingJackson2HttpMessageConverter(objectMapper()));
                })
                // 기본 처리기는 예외 메시지에 응답 본문을 싣는다 → 상태 코드만 남긴다(로그에 원문 금지)
                .defaultStatusHandler(status -> status.isError(), (request, response) -> {
                    throw new EngineHttpException(response.getStatusCode().value());
                });
    }

    /**
     * 앱 전역 ObjectMapper(모르는 필드 거부)와 따로 둔다. B가 응답에 필드를 더해도 A가 깨지지 않게 모르는 필드는
     * 무시하고, null 필드는 보내지 않아 B의 기본값을 쓰게 한다(B는 null 목록을 422로 거부한다).
     */
    static ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
    }
}
