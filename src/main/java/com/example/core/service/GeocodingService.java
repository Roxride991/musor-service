package com.example.core.service;

import com.example.core.model.ServiceZone;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Сервис геокодинга: преобразование адреса в координаты.
 * Работает ТОЛЬКО с Яндекс.Геокодером. Без fallback.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeocodingService {

    private static final String YANDEX_GEOCODER_URL = "https://geocode-maps.yandex.ru/1.x";
    private static final int MAX_RESULTS = 10;

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
    public ServiceZone.Coordinate getCoordinates(String address) {
        String cleanAddress = normalizeQuery(address, "Адрес не может быть пустым");
        JsonNode featureMember = fetchFeatureMembers(cleanAddress, 1);

        if (!featureMember.isArray() || featureMember.isEmpty()) {
            log.warn("Адрес не найден в геокодере: {}", cleanAddress);
            throw new IllegalArgumentException("Адрес не найден в геокодере: " + cleanAddress);
        }

        ServiceZone.Coordinate coordinate = parseCoordinate(featureMember.get(0), cleanAddress);
        log.info("Адрес '{}' успешно геокодирован: lat={}, lng={}", cleanAddress, coordinate.getLat(), coordinate.getLng());
        return coordinate;
    }

    /**
     * Возвращает список адресных подсказок вместе с координатами.
     */
    public List<AddressSuggestion> suggestAddresses(String query, int limit) {
        String cleanQuery = query == null ? "" : query.trim();
        if (cleanQuery.length() < 3) {
            return List.of();
        }

        int safeLimit = Math.max(1, Math.min(limit, MAX_RESULTS));
        JsonNode featureMember = fetchFeatureMembers(cleanQuery, safeLimit);
        if (!featureMember.isArray() || featureMember.isEmpty()) {
            return List.of();
        }

        List<AddressSuggestion> suggestions = new ArrayList<>();
        for (JsonNode item : featureMember) {
            try {
                ServiceZone.Coordinate coordinate = parseCoordinate(item, cleanQuery);
                String suggestionAddress = extractAddress(item);
                if (suggestionAddress.isBlank()) {
                    continue;
                }

                suggestions.add(new AddressSuggestion(
                        suggestionAddress,
                        coordinate.getLat(),
                        coordinate.getLng()
                ));
                if (suggestions.size() >= safeLimit) {
                    break;
                }
            } catch (IllegalArgumentException ignored) {
                // Пропускаем "битые" элементы, но сохраняем остальные подсказки
            }
        }

        return suggestions;
    }

    private JsonNode fetchFeatureMembers(String query, int results) {
        try {
            if (!hasText(yandexApiKey)) {
                throw new IllegalStateException("Yandex geocoder API key is not configured");
            }

            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);

            UriComponentsBuilder builder = UriComponentsBuilder
                    .fromHttpUrl(YANDEX_GEOCODER_URL)
                    .queryParam("geocode", encodedQuery)
                    .queryParam("format", "json")
                    .queryParam("results", Math.max(1, results))
                    .queryParam("apikey", yandexApiKey.trim());

            URI uri = builder.build(true).toUri();

            log.debug("Выполняется геокодинг запроса: '{}'", query);

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
                log.warn("Геокодер вернул статус: {} для запроса: {}", response.getStatusCode(), query);
                throw new IllegalArgumentException("Геокодер недоступен или вернул ошибку");
            }

            JsonNode root = objectMapper.readTree(response.getBody());
            return root.path("response")
                    .path("GeoObjectCollection")
                    .path("featureMember");
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Критическая ошибка при геокодинге запроса: {}", query, e);
            throw new IllegalStateException("Сервис геокодинга временно недоступен", e);
        }
    }

    private ServiceZone.Coordinate parseCoordinate(JsonNode featureMemberItem, String queryForErrors) {
        String pos = featureMemberItem
                .path("GeoObject")
                .path("Point")
                .path("pos")
                .asText();

        if (pos == null || pos.isBlank()) {
            throw new IllegalArgumentException("Не удалось получить координаты для: " + queryForErrors);
        }

        String[] coords = pos.split(" ");
        if (coords.length != 2) {
            throw new IllegalArgumentException("Неверный формат координат от геокодера");
        }

        // Яндекс возвращает: "долгота широта"
        double longitude = Double.parseDouble(coords[0]);
        double latitude = Double.parseDouble(coords[1]);
        return new ServiceZone.Coordinate(latitude, longitude);
    }

    private String extractAddress(JsonNode featureMemberItem) {
        JsonNode geoObject = featureMemberItem.path("GeoObject");
        String address = geoObject
                .path("metaDataProperty")
                .path("GeocoderMetaData")
                .path("text")
                .asText("");

        if (address.isBlank()) {
            address = geoObject.path("name").asText("");
        }

        return address == null ? "" : address.trim();
    }

    private String normalizeQuery(String query, String emptyMessage) {
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException(emptyMessage);
        }
        return query.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public record AddressSuggestion(String address, double lat, double lng) {
    }
}
