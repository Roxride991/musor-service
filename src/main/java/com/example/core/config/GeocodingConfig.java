package com.example.core.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Конфигурация для геокодинга.
 */
@Configuration
public class GeocodingConfig {

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    // Удаляем создание ObjectMapper - Spring Boot автоматически настраивает его
    // с поддержкой Java 8 time API через spring-boot-starter-web
    // Если нужен отдельный ObjectMapper для геокодинга, используйте @Qualifier
}

