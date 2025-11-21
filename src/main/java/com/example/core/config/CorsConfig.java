package com.example.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOriginPatterns(
                                "http://localhost:5173",
                                "https://musoren-front.vercel.app"
                        )
                        .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                        .allowedHeaders("*")
                        .exposedHeaders("Authorization")
                        .allowCredentials(true)
                        .maxAge(3600); // кэшировать preflight на 1 час

                // Для всех остальных endpoints (на всякий случай)
                registry.addMapping("/**")
                        .allowedOriginPatterns(
                                "http://localhost:5173",
                                "https://musoren-front.vercel.app"
                        )
                        .allowedMethods("OPTIONS") // только OPTIONS для preflight
                        .allowedHeaders("*")
                        .allowCredentials(true)
                        .maxAge(3600);
            }
        };
    }
}