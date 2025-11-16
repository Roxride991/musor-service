package com.example.core.service;

import com.example.core.model.ServiceZone;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Сервис геокодинга: преобразование адреса в координаты.
 * Работает ТОЛЬКО с Яндекс.Геокодером. Без fallback.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeocodingService {

    private static final String YANDEX_GEOCODER_URL = "https://geocode-maps.yandex.ru/1.x";

    private final RestTemplate restTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Value("${geocoder.yandex.api-key}")
    private String yandexApiKey;

    /**
     * Преобразует адрес в координаты через Яндекс.Геокодер.
     * Результат кэшируется (см. CacheConfig).
     *
     * @param address адрес для геокодинга
     * @return координаты (lat, lng)
     * @throws IllegalArgumentException если геокодинг невозможен
     */
    @Cacheable(value = "geocoding", key = "#address?.trim()?.toLowerCase()")
    public ServiceZone.Coordinate getCoordinates(String address) {
        if (address == null || address.trim().isEmpty()) {
            throw new IllegalArgumentException("Адрес не может быть пустым");
        }

        if (yandexApiKey == null || yandexApiKey.trim().isEmpty()) {
            throw new IllegalStateException("API-ключ Яндекс.Геокодера не настроен (geocoder.yandex.api-key)");
        }

        return getCoordinatesFromYandex(address.trim());
    }

    private ServiceZone.Coordinate getCoordinatesFromYandex(String cleanAddress) {
        try {
            // 🔒 Шаг 1: корректное URL-кодирование адреса
            String encodedAddress = URLEncoder.encode(cleanAddress, StandardCharsets.UTF_8);

            // 🔗 Шаг 2: безопасная сборка URI с build(true)
            URI uri = org.springframework.web.util.UriComponentsBuilder
                    .fromHttpUrl(YANDEX_GEOCODER_URL)
                    .queryParam("apikey", yandexApiKey.trim())
                    .queryParam("geocode", encodedAddress) // уже закодировано!
                    .queryParam("format", "json")
                    .queryParam("results", 1)
                    .build(true) // ← НЕ перекодировать параметры повторно
                    .toUri();

            // 📝 Шаг 3: логируем ТОЛЬКО адрес, НЕ URI (защита от утечки API-ключа)
            log.debug("Выполняется геокодинг адреса: '{}'", cleanAddress);

            // 📡 Шаг 4: выполнение запроса
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("User-Agent", "MusorService/1.0");
            org.springframework.http.HttpEntity<Void> entity = new org.springframework.http.HttpEntity<>(headers);

            org.springframework.http.ResponseEntity<String> response = restTemplate.exchange(
                    uri,
                    org.springframework.http.HttpMethod.GET,
                    entity,
                    String.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("Геокодер вернул статус: {} для адреса: {}", response.getStatusCode(), cleanAddress);
                throw new IllegalArgumentException("Геокодер недоступен или вернул ошибку");
            }

            // 📥 Шаг 5: парсинг ответа
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode featureMember = root.path("response")
                    .path("GeoObjectCollection")
                    .path("featureMember");

            if (!featureMember.isArray() || featureMember.isEmpty()) {
                log.warn("Адрес не найден в геокодере: {}", cleanAddress);
                throw new IllegalArgumentException("Адрес не найден в геокодере: " + cleanAddress);
            }

            String pos = featureMember.get(0)
                    .path("GeoObject")
                    .path("Point")
                    .path("pos")
                    .asText();

            if (pos == null || pos.isBlank()) {
                log.warn("Пустые координаты для адреса: {}", cleanAddress);
                throw new IllegalArgumentException("Не удалось получить координаты для адреса: " + cleanAddress);
            }

            String[] coords = pos.split(" ");
            if (coords.length != 2) {
                log.warn("Неверный формат координат (ожидалось 'lng lat'): '{}'", pos);
                throw new IllegalArgumentException("Неверный формат координат от геокодера");
            }

            // ⚠️ Яндекс возвращает: "долгота широта" → сначала lng, потом lat
            double longitude = Double.parseDouble(coords[0]);
            double latitude = Double.parseDouble(coords[1]);

            log.info("Адрес '{}' успешно геокодирован: lat={}, lng={}", cleanAddress, latitude, longitude);
            return new ServiceZone.Coordinate(latitude, longitude);

        } catch (IllegalArgumentException e) {
            // Пробрасываем бизнес-ошибки как есть
            throw e;
        } catch (Exception e) {
            // Логируем техническую ошибку, но не раскрываем детали клиенту
            log.error("Критическая ошибка при геокодинге адреса: {}", cleanAddress, e);
            throw new IllegalStateException("Сервис геокодинга временно недоступен", e);
        }
    }
}