package com.example.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Конфигурация для геокодинга.
 */
@Configuration
public class GeocodingConfig {

    @Bean
    public RestTemplate restTemplate(
            @Value("${integration.http.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${integration.http.read-timeout-ms:7000}") int readTimeoutMs
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.max(500, connectTimeoutMs));
        requestFactory.setReadTimeout(Math.max(1000, readTimeoutMs));
        return new RestTemplate(requestFactory);
    }

}
