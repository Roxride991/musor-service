# Stage 1: Сборка JAR
FROM gradle:8.14.3-jdk17 AS builder

WORKDIR /app

# Копируем зависимости
COPY build.gradle.kts settings.gradle.kts ./

# Если используешь gradle.properties
# COPY gradle.properties ./

# Копируем исходники
COPY src ./src

# Собираем JAR
RUN ./gradlew bootJar -x test

# Stage 2: Запуск
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Копируем JAR из builder
COPY --from=builder /app/build/libs/*.jar app.jar

# Открываем порт
EXPOSE 8080

# Запуск
ENTRYPOINT ["java", "-jar", "app.jar"]