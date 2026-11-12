package com.som.common.web;

import com.som.auth.SecurityConfig;
import com.som.auth.web.CurrentUserId;
import com.som.common.error.ErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc 자동 생성 문서 설정(ADR-008, A-09). 정적 YAML·JSON 문서를 따로 만들지 않는다.
 * 보안 스킴은 설명용이다: 실제 인증은 세션 쿠키, 상태 변경 요청은 CSRF 헤더가 필요하다(ADR-003).
 */
@Configuration
public class OpenApiConfig {

    private static final String ERROR_SCHEMA = "ErrorResponse";
    private static final String ERROR_REF = "#/components/schemas/" + ERROR_SCHEMA;
    private static final Map<String, String> COMMON_ERRORS = Map.of(
            "401", "로그인이 필요합니다",
            "403", "권한·CSRF·필수 동의 부족",
            "500", "서버 오류");

    @Bean
    OpenAPI somOpenApi(ObjectProvider<BuildProperties> buildProperties) {
        BuildProperties build = buildProperties.getIfAvailable();
        Components components = new Components()
                .addSecuritySchemes("sessionCookie", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.COOKIE).name("JSESSIONID")
                        .description("로그인 후 서버가 발급하는 세션 쿠키(HttpOnly)"))
                .addSecuritySchemes("csrfHeader", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER).name("X-XSRF-TOKEN")
                        .description("상태 변경 요청에 XSRF-TOKEN 쿠키 값(또는 GET /api/v1/auth/csrf의 token)을 넣는다"));
        // ErrorResponse와 내부 ErrorBody 스키마를 레코드에서 생성해 등록한다(문서·실제 응답 일치).
        @SuppressWarnings("rawtypes")
        Map<String, Schema> errorSchemas = ModelConverters.getInstance().readAll(ErrorResponse.class);
        errorSchemas.forEach(components::addSchemas);
        return new OpenAPI()
                .info(new Info().title("SOM API").version(build != null ? build.getVersion() : "unknown"))
                .components(components);
    }

    static {
        // 세션에서 채우는 인자이므로 문서의 요청 파라미터로 노출하지 않는다.
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUserId.class);
    }

    /** 모든 operation에 공통 에러 응답(401·403·500)을 ErrorResponse 스키마로 붙인다. */
    @Bean
    OperationCustomizer commonErrorResponses() {
        return (operation, handlerMethod) -> {
            if (operation.getResponses() == null) {
                operation.setResponses(new ApiResponses());
            }
            COMMON_ERRORS.forEach((status, description) -> operation.getResponses().putIfAbsent(status,
                    new ApiResponse().description(description).content(new Content().addMediaType(
                            org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                            new MediaType().schema(new Schema<>().$ref(ERROR_REF))))));
            return operation;
        };
    }

    /** 인증 없이 허용하는 공개 API(SecurityConfig.PUBLIC_API_PATHS)에서는 "로그인이 필요합니다" 401을 뺀다. */
    @Bean
    OpenApiCustomizer publicPathsWithoutUnauthorized() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, item) -> {
                if (SecurityConfig.PUBLIC_API_PATHS.contains(path)) {
                    item.readOperations().forEach(operation -> operation.getResponses().remove("401"));
                }
            });
        };
    }
}
