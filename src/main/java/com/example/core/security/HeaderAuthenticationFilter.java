package com.example.core.security;

import com.example.core.model.User;
import com.example.core.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Slf4j
@Component
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    private final UserRepository userRepository;

    public HeaderAuthenticationFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String requestPath = request.getRequestURI();
        
        // Логируем все заголовки для диагностики
        log.info("HeaderAuthenticationFilter: Request to {}", requestPath);
        log.info("HeaderAuthenticationFilter: All headers:");
        request.getHeaderNames().asIterator().forEachRemaining(headerName -> {
            String headerValue = request.getHeader(headerName);
            log.info("  {} = {}", headerName, headerValue);
        });
        
        // Пробуем разные варианты имени заголовка (регистр может быть важен)
        String userIdHeader = request.getHeader("X-User-Id");
        if (userIdHeader == null) {
            userIdHeader = request.getHeader("x-user-id");
        }
        if (userIdHeader == null) {
            userIdHeader = request.getHeader("X-USER-ID");
        }

        log.info("HeaderAuthenticationFilter: X-User-Id header value: '{}' (length: {})", 
                userIdHeader, userIdHeader != null ? userIdHeader.length() : 0);

        if (userIdHeader != null && !userIdHeader.isBlank()) {
            try {
                Long userId = Long.parseLong(userIdHeader.trim());
                User user = userRepository.findById(userId).orElse(null);

                if (user != null) {
                    // Создаём Authentication без ролей (если вы не используете @PreAuthorize)
                    // Но чтобы @AuthenticationPrincipal работал — этого достаточно
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(
                                    user,      // principal
                                    null,      // credentials
                                    Collections.emptyList() // authorities (можно оставить пустым, если не используете hasRole)
                            );

                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    log.info("HeaderAuthenticationFilter: ✅ Authentication set for user ID: {} (role: {})", userId, user.getUserRole());
                } else {
                    log.error("HeaderAuthenticationFilter: ❌ User with ID {} not found in database. Request path: {}", userId, requestPath);
                    // Сохраняем информацию о том, что пользователь не найден, для обработчика ошибок
                    request.setAttribute("USER_NOT_FOUND", userId);
                }
            } catch (NumberFormatException e) {
                log.error("HeaderAuthenticationFilter: ❌ Invalid X-User-Id header value: {}. Request path: {}", userIdHeader, requestPath);
                request.setAttribute("INVALID_USER_ID", userIdHeader);
            }
        } else {
            log.warn("HeaderAuthenticationFilter: ⚠️ X-User-Id header is missing or empty. Request path: {}", requestPath);
        }

        filterChain.doFilter(request, response);
    }
}