// com.example.core.converter.JsonbConverter.java
package com.example.core.converter;

import com.example.core.model.ServiceZone;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.List;

@Converter
public class JsonbConverter implements AttributeConverter<List<ServiceZone.Coordinate>, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(List<ServiceZone.Coordinate> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Не удалось сериализовать координаты в JSON", e);
        }
    }

    @Override
    public List<ServiceZone.Coordinate> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(dbData, objectMapper.getTypeFactory()
                    .constructCollectionType(List.class, ServiceZone.Coordinate.class));
        } catch (Exception e) {
            throw new RuntimeException("Не удалось десериализовать JSON в координаты", e);
        }
    }
}