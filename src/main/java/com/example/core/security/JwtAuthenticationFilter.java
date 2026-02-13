package com.example.core.security;

import com.example.core.model.User;
import com.example.core.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);

            if (jwtService.validate(token)) {
                try {
                    Long userId = jwtService.getUserIdFromToken(token);
                    log.debug("JWT authentication - User ID: {}", userId);

                    User user = userRepository.findById(userId).orElse(null);

                    if (user != null) {
                        if (user.isBanned()) {
                            log.warn("Banned user attempted access: {}", userId);
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.getWriter().write("{\"error\": \"Account banned\"}");
                            return;
                        }

                        // Проверяем верификацию телефона
                        if (requiresPhoneVerification(request) && !user.isPhoneVerified()) {
                            log.warn("Phone not verified for user: {}", userId);
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.getWriter().write("{\"error\": \"Phone verification required\"}");
                            return;
                        }

                        List<GrantedAuthority> authorities = new ArrayList<>();
                        authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getUserRole().name()));

                        Authentication auth = new UsernamePasswordAuthenticationToken(
                                user, null, authorities
                        );
                        SecurityContextHolder.getContext().setAuthentication(auth);

                        log.debug("User authenticated: {} ({})", user.getPhone(), user.getUserRole());
                    } else {
                        log.warn("User not found for ID: {}", userId);
                    }
                } catch (Exception e) {
                    log.error("Error processing JWT token", e);
                }
            } else {
                log.debug("Invalid JWT token");
            }
        } else {
            log.debug("No JWT token found for request: {}", request.getRequestURI());
        }

        chain.doFilter(request, response);
    }

    /**
     * Проверяет, требует ли endpoint верификации телефона
     */
    private boolean requiresPhoneVerification(HttpServletRequest request) {
        String uri = request.getRequestURI();

        // Endpoints, которые требуют верификации телефона
        List<String> protectedEndpoints = List.of(
                "/api/orders",
                "/api/orders/create",
                "/api/subscriptions",
                "/api/payments"
        );

        return protectedEndpoints.stream()
                .anyMatch(uri::startsWith);
    }
}
