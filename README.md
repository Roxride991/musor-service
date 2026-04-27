# Core Backend Service

Backend-сервис для платформы вывоза бытовых отходов: регистрация пользователей, создание разовых заказов, управление подписками, платежи, уведомления, Telegram-auth и административные операции.

Проект приведён к более production-ready виду:

- контроллеры стали тоньше и делегируют orchestration в service/facade слой
- API не отдаёт JPA entities напрямую
- добавлены единые DTO и mapper-слой
- реализована централизованная обработка ошибок через `@RestControllerAdvice`
- усилены Flyway-миграции: теперь есть базовая `V1` для чистого поднятия БД
- добавлены pageable-ответы для уведомлений и платежей
- обновлены unit-тесты и добавлен интеграционный тест

## Стек технологий

- Java 21
- Spring Boot 3.4
- Spring Web
- Spring Security + JWT
- Spring Data JPA
- PostgreSQL
- Redis
- Flyway
- Bean Validation
- springdoc-openapi / Swagger UI
- Micrometer + Prometheus
- JUnit 5 + Mockito + Spring Boot Test
- Docker / Docker Compose

## Архитектура

Проект использует слоистую архитектуру:

- `controller` — HTTP endpoints, валидация входа, статусы ответов
- `service` — бизнес-логика и application/facade orchestration
- `repository` — доступ к данным через Spring Data JPA
- `model` — JPA-сущности домена
- `dto` — request/response модели API
- `mapper` — преобразование сущностей в DTO
- `exception` — кастомные исключения и глобальный error handling
- `config` — security, cache, web, monitoring, geocoding config

### Текущая структура

```text
src/main/java/com/example/core
├── config
├── controller
├── converter
├── dto
├── exception
├── filter
├── mapper
├── model
├── monitoring
├── repository
├── security
├── service
└── util
```

### Основные бизнес-сценарии

- клиент может зарегистрироваться, войти и создавать заказы
- клиент может оформить подписку на регулярный вывоз
- курьер может принимать и исполнять заказы
- администратор может управлять dispatch-политикой, смотреть BI-метрики и запускать reconciliation
- платежи поддерживают one-time и subscription flow
- уведомления хранятся как отдельная сущность и доступны через API

## Ключевые улучшения после рефакторинга

1. Контроллеры больше не собирают бизнес-правила вручную.
   HTTP-слой делегирует работу в `AuthFacadeService`, `PaymentFacadeService`, `SubscriptionFacadeService`, `ServiceZoneService`.

2. Ошибки унифицированы.
   Добавлены `ApiException`, `BadRequestException`, `UnauthorizedException`, `ForbiddenOperationException`, `ConflictException`, `ResourceNotFoundException`, `ServiceUnavailableException`.

3. Появился отдельный `mapper`-слой.
   Старый `dto.mapper.DtoMapper` удалён, вместо него используется `EntityDtoMapper`.

4. База данных стала воспроизводимой.
   Добавлена `V1__Create_core_schema.sql`, а локальный профиль теперь использует `ddl-auto=validate` вместо `update`.

5. API-ответы стали стабильнее.
   Для ошибок используется единый JSON-формат, для списков уведомлений и платежей — `PageResponse<T>`.

## Запуск локально

### 1. Поднять инфраструктуру

```bash
docker compose -f docker/docker-compose.yml up -d
```

Контейнеры:

- PostgreSQL: `localhost:5433`
- Redis: `localhost:6379`

### 2. Подготовить переменные окружения

Минимально нужны:

```bash
export SPRING_PROFILES_ACTIVE=local
export DB_URL=jdbc:postgresql://localhost:5433/musor_service
export DB_USERNAME=musor_user
export DB_PASSWORD=musor123
export JWT_SECRET=replace-with-32-bytes-secret
```

Опционально:

- `YANDEX_GEOCODER_API_KEY`
- `SMS_RU_API_ID`
- `REDIS_HOST`
- `REDIS_PORT`
- `PAYMENTS_PROVIDER`
- `PAYMENTS_WEBHOOK_TOKEN`

### 3. Запустить приложение

```bash
./gradlew bootRun
```

Приложение стартует на:

- API: `http://localhost:8082`
- Swagger UI: `http://localhost:8082/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8082/v3/api-docs`

## Запуск в Docker

### Сборка образа

```bash
docker build -t core-backend .
```

### Запуск контейнера

```bash
docker run --rm -p 8082:8082 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_URL=jdbc:postgresql://host.docker.internal:5433/musor_service \
  -e DB_USERNAME=musor_user \
  -e DB_PASSWORD=musor123 \
  -e JWT_SECRET=replace-with-32-bytes-secret \
  core-backend
```

## Миграции

Flyway-миграции лежат в:

```text
src/main/resources/db/migration
```

Теперь цепочка начинается с:

- `V1__Create_core_schema.sql`

Это важно, потому что новый инстанс PostgreSQL можно поднять с нуля без зависимости от `hibernate.ddl-auto=update`.

## Тесты

Запуск всех тестов:

```bash
./gradlew test
```

В проекте есть:

- unit-тесты сервисов и facade-слоя
- тест global exception handler
- интеграционный MVC-тест `UserControllerIntegrationTest`
- `SpringBootTest` smoke-test загрузки контекста

## Основные API endpoints

### Auth

- `POST /api/auth/send-code`
- `POST /api/auth/register`
- `POST /api/auth/login`

### Users

- `GET /api/users/me`
- `PATCH /api/users/me`

### Orders

- `POST /api/orders`
- `GET /api/orders`
- `GET /api/orders/{id}`
- `PATCH /api/orders/{id}/status`
- `DELETE /api/orders/{id}/cancel`

### Subscriptions

- `POST /api/subscriptions`
- `GET /api/subscriptions`
- `DELETE /api/subscriptions/{id}`
- `PATCH /api/subscriptions/{id}/pause`
- `PATCH /api/subscriptions/{id}/resume`
- `PATCH /api/subscriptions/{id}/reschedule`

### Payments

- `POST /api/orders/{orderId}/payments`
- `POST /api/subscriptions/{subscriptionId}/payments`
- `GET /api/payments?page=0&size=20`
- `GET /api/payments/{paymentId}`
- `POST /api/payments/{paymentId}/sync`
- `POST /api/payments/webhooks/yookassa`

### Notifications

- `GET /api/notifications?page=0&size=20`
- `PATCH /api/notifications/{notificationId}/read`

### Service Zones

- `GET /api/zones/active`
- `POST /api/zones`

## Примеры запросов

### Отправить SMS-код

```bash
curl -X POST http://localhost:8082/api/auth/send-code \
  -H "Content-Type: application/json" \
  -d '{
    "phone": "+79990000000"
  }'
```

Ответ:

```json
{
  "message": "Код отправлен"
}
```

### Зарегистрировать клиента

```bash
curl -X POST http://localhost:8082/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "phone": "+79990000000",
    "name": "Тимур",
    "password": "Password123",
    "role": "CLIENT",
    "code": "1234"
  }'
```

Ответ:

```json
{
  "id": 1,
  "phone": "+79990000000",
  "name": "Тимур",
  "role": "CLIENT"
}
```

### Войти и получить JWT

```bash
curl -X POST http://localhost:8082/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "phone": "+79990000000",
    "password": "Password123"
  }'
```

Ответ:

```json
{
  "id": 1,
  "phone": "+79990000000",
  "name": "Тимур",
  "role": "CLIENT",
  "token": "eyJhbGciOi..."
}
```

### Создать заказ

```bash
curl -X POST http://localhost:8082/api/orders \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{
    "address": "Оренбург, ул. Ленина, 1",
    "pickupTime": "2026-04-28T08:30:00+05:00",
    "comment": "Оставить у ворот"
  }'
```

### Создать подписку

```bash
curl -X POST http://localhost:8082/api/subscriptions \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{
    "plan": "MONTHLY",
    "address": "Оренбург, ул. Чкалова, 10",
    "pickupSlot": "SLOT_8_11",
    "startDate": "2026-04-29",
    "cadenceDays": 2
  }'
```

### Инициализировать платёж по заказу

```bash
curl -X POST http://localhost:8082/api/orders/10/payments \
  -H "Authorization: Bearer <JWT>" \
  -H "Content-Type: application/json" \
  -d '{
    "returnUrl": "http://localhost:5173/profile/orders/10",
    "description": "Оплата разового заказа"
  }'
```

### Получить paged-список уведомлений

```bash
curl -X GET "http://localhost:8082/api/notifications?page=0&size=20" \
  -H "Authorization: Bearer <JWT>"
```

Ответ:

```json
{
  "content": [
    {
      "id": 11,
      "type": "ORDER_STATUS",
      "channel": "IN_APP",
      "status": "READ",
      "title": "Заказ создан",
      "message": "Заказ №42 успешно создан"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

## Формат ошибки

Все ошибки приводятся к единому виду:

```json
{
  "timestamp": "2026-04-27T18:30:00+05:00",
  "status": 400,
  "code": "validation_failed",
  "error": "Validation Error",
  "message": "Ошибка валидации данных",
  "path": "/api/auth/register",
  "details": {
    "phone": "Неверный формат телефона"
  }
}
```

## Что ещё можно улучшить

- постепенно перевести workflow-эндпоинты заказов на более строгий REST-стиль для командных операций
- переименовать пакет `model` в `entity` для ещё более явной структуры
- добавить Testcontainers для интеграционных тестов PostgreSQL/Flyway
- выделить отдельные `OrderFacadeService` и `AdminFacadeService`, чтобы дополнительно разгрузить крупные контроллеры

## Команды для ревьюера

```bash
./gradlew test
./gradlew bootRun
docker compose -f docker/docker-compose.yml up -d
```
