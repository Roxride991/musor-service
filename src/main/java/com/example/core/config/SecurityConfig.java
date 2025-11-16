package com.example.core.config;

import com.example.core.security.HeaderAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Конфигурация безопасности: Stateless-сессии, разрешённые публичные пути и
 * фильтр подстановки пользователя из заголовка X-User-Id.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Бин для хеширования паролей с помощью BCrypt. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Строит цепочку фильтров безопасности и регистрирует наш HeaderAuthenticationFilter. */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, HeaderAuthenticationFilter headerAuthFilter) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/api/zones/active").permitAll() // Получение активной зоны - публичный эндпоинт
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json;charset=UTF-8");
                            
                            // Проверяем, почему аутентификация не прошла
                            Long userIdNotFound = (Long) request.getAttribute("USER_NOT_FOUND");
                            String invalidUserId = (String) request.getAttribute("INVALID_USER_ID");
                            
                            String message;
                            if (userIdNotFound != null) {
                                message = String.format("Пользователь с ID %d не найден в базе данных. Проверьте ID или зарегистрируйте пользователя через POST /api/auth/register", userIdNotFound);
                            } else if (invalidUserId != null) {
                                message = String.format("Неверный формат заголовка X-User-Id: %s. Ожидается числовое значение (ID пользователя)", invalidUserId);
                            } else {
                                message = "Требуется заголовок X-User-Id с ID пользователя. Добавьте заголовок X-User-Id в запрос.";
                            }
                            
                            // Экранируем кавычки для JSON
                            String escapedMessage = message.replace("\"", "\\\"");
                            String jsonResponse = String.format("{\"error\":\"Unauthorized\",\"message\":\"%s\"}", escapedMessage);
                            response.getWriter().write(jsonResponse);
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json;charset=UTF-8");
                            response.getWriter().write("{\"error\":\"Forbidden\",\"message\":\"Недостаточно прав доступа\"}");
                        })
                )
                .addFilterBefore(headerAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}