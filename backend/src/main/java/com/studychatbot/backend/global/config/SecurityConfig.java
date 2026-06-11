package com.studychatbot.backend.global.config;

import com.studychatbot.backend.global.jwt.JwtAuthenticationEntryPoint;
import com.studychatbot.backend.global.jwt.JwtAuthenticationFilter;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    // CORS 허용 origin을 환경변수(APP_CORS_ALLOWED_ORIGINS, 쉼표 구분)로 주입받는다.
    // 환경변수가 없으면 로컬 Vite 개발 서버(5173)를 기본 허용 → 로컬 개발 그대로 동작.
    // 배포에서는 Vercel 프론트 도메인을 넣어 덮어쓴다.
    @Value("${app.cors.allowed-origins:http://localhost:5173}")
    private String allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 허용 origin은 allowedOrigins 환경변수에서 주입 (쉼표로 다중 origin 지정 가능).
    // allowCredentials(true)이므로 와일드카드("*")는 불가 → 명시적 origin 목록을 쓴다.
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            // JWT 사용 → 서버 세션 불필요
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 미인증 요청에 403 대신 401을 반환(기본 Http403ForbiddenEntryPoint 대체).
            // 401=미인증→프론트가 토큰 재발급, 403=권한 없음(ForbiddenException)으로 구분
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(jwtAuthenticationEntryPoint))
            .authorizeHttpRequests(auth -> auth
                // SSE(SseEmitter)는 응답 후 ASYNC 디스패치로 필터 체인을 재진입한다.
                // 이때 디스패치 스레드엔 SecurityContext가 없어 AuthorizationFilter가
                // Access Denied를 던지므로, 최초 REQUEST에서 이미 인증된 ASYNC 재진입은 허용한다.
                // (ASYNC 디스패치는 컨테이너 내부에서만 발생 → 외부 위조 불가, 안전)
                .dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()
                .requestMatchers("/api/auth/**").permitAll()
                // Spring MVC가 에러를 /error로 포워드할 때 Security가 재차 막지 않도록 허용
                .requestMatchers("/error").permitAll()
                // Swagger UI 자체 리소스 — 인증 없이 접근 허용
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/swagger-resources/**").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
