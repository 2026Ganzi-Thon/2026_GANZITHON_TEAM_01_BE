package com.som.auth;

import com.som.auth.repository.UserRepository;
import com.som.auth.service.LoginUserDetailsService;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * 세션 기반 인증 + CSRF 쿠키 토큰(ADR-003). 인증 경계와 401·403 형식을 정한다.
 * 로그인·로그아웃은 AuthController(JSON)가 처리한다. formLogin·기본 logout은 쓰지 않는다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** 인증 없이 호출할 수 있는 공개 API(명시 목록). OpenAPI 문서에서도 공통 401을 붙이지 않는다. */
    public static final List<String> PUBLIC_API_PATHS = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/signup",
            "/api/v1/auth/logout",
            "/api/v1/auth/csrf");

    private static final String[] PUBLIC_INFRA_PATHS = {
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html",
            "/error"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityErrorHandler errorHandler) throws Exception {
        http
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_API_PATHS.toArray(String[]::new)).permitAll()
                        .requestMatchers(PUBLIC_INFRA_PATHS).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(errorHandler)
                        .accessDeniedHandler(errorHandler));
        return http.build();
    }

    /**
     * loginId + 비밀번호(Argon2id) 인증. UserDetailsService는 빈으로 노출하지 않는다.
     * 없는 아이디도 더미 해시 비교를 거치고(타이밍 차이 완화) BadCredentials로 통일된다.
     */
    @Bean
    AuthenticationManager authenticationManager(UserRepository users, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(new LoginUserDetailsService(users));
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
