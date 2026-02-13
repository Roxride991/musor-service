# Stage 1: Сборка JAR
FROM gradle:8.14.3-jdk21 AS builder

WORKDIR /app

# Копируем зависимости
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
COPY gradlew ./
RUN chmod +x ./gradlew

# Копируем исходники
COPY src ./src

# Собираем JAR
RUN ./gradlew bootJar -x test

# Stage 2: Запуск
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
