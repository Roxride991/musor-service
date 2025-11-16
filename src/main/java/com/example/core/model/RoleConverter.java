package com.example.core.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Конвертер для хранения роли пользователя в БД как строки и
 * восстановления значения перечисления при чтении.
 */
@Converter(autoApply = false)
public class RoleConverter implements AttributeConverter<UserRole, String> {

    @Override
    public String convertToDatabaseColumn(UserRole attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public UserRole convertToEntityAttribute(String dbData) {
        return dbData == null ? null : UserRole.valueOf(dbData);
    }
}


