package com.som.auth;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
class ApiCorsConfig {
    @Bean
    UrlBasedCorsConfigurationSource apiCorsSource(@Value("${som.cors.allowed-origins}") String origins) {
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(Arrays.stream(origins.split(",")).map(String::strip)
                .filter(origin -> !origin.isEmpty()).map(ApiCorsConfig::validateOrigin).distinct().toList());
        cors.setAllowCredentials(true);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "Idempotency-Key", "X-Request-ID"));
        cors.setExposedHeaders(List.of("X-Request-ID", "Retry-After"));
        cors.setMaxAge(600L);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    private static String validateOrigin(String origin) {
        URI uri = URI.create(origin);
        if (uri.getHost() == null || uri.getRawUserInfo() != null || !uri.getRawPath().isEmpty()
                || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme())
                && List.of("localhost", "127.0.0.1").contains(uri.getHost())))) {
            throw new IllegalArgumentException("CORS origins must be exact HTTPS origins or local HTTP origins");
        }
        return origin;
    }
}
