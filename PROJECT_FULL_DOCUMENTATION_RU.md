# Полная документация проекта `core`

Дата актуализации: 19.02.2026  
Проект: backend-сервис на Spring Boot для сервиса вывоза мусора (клиенты, курьеры, админ, подписки, заказы, Telegram/SMS авторизация).

## 1) Назначение проекта

Проект реализует REST API для:
- регистрации и логина пользователей;
- работы с заказами на вывоз;
- работы с подписками (автопланирование вывозов);
- управления зонами обслуживания (полигон на карте);
- геокодинга адресов (Яндекс);
- авторизации через Telegram-бота по подтвержденному номеру;
- базовой платежной логики (MVP-заглушка).

Основная бизнес-идея:
- клиент оформляет подписку с адресом, слотом и интервалом (ежедневно/через день);
- система автоматически создает следующий заказ по расписанию;
- курьеры берут опубликованные заказы и ведут их по статусам;
- админ управляет статусами/зоной обслуживания.

## 2) Технологический стек

### Ядро
- Java 21
- Spring Boot 3.4.1
- Spring Web
- Spring Data JPA
- Spring Security
- Spring Validation
- Lombok

### База/хранилища
- PostgreSQL 15
- Flyway (миграции)
- Redis (кэш)

### Безопасность/аутентификация
- JWT (`io.jsonwebtoken` / JJWT)
- BCrypt (`PasswordEncoder`)
- Telegram session flow + HMAC подпись

### Интеграции
- Яндекс Геокодер API
- SMS.ru API

### Документация и тесты
- springdoc-openapi (Swagger UI)
- JUnit 5 + Spring Test + Mockito
- Postman regression + E2E коллекция

## 3) Архитектура и структура кода

Слои:
- `controller` — REST endpoints
- `service` — бизнес-логика
- `repository` — доступ к БД
- `model` — JPA-сущности и enum
- `dto` — запросы/ответы API
- `security` — JWT сервис и фильтр
- `config` — security/cors/cache/web/env/rest-template
- `exception` — глобальный обработчик ошибок

Точка входа:
- `src/main/java/com/example/core/CoreApplication.java`
- Перед стартом загружается `.env` через `EnvFileLoader`.

## 4) Доменная модель (сущности)

### `User` (`users`)
- поля: `phone`, `name`, `password`, `role`, `banned`, `phoneVerified`, `telegramId`, `lastLogin` и др.
- реализует `UserDetails`.
- роли: `CLIENT`, `COURIER`, `ADMIN`.

### `Order` (`orders`)
- клиент, курьер (опционально), подписка (опционально), адрес, время вывоза, комментарий, статус.
- статус:  
`PUBLISHED`, `ACCEPTED`, `ON_THE_WAY`, `PICKED_UP`, `COMPLETED`, `CANCELLED_BY_CUSTOMER`, `CANCELLED_BY_COURIER`.

### `Subscription` (`subscriptions`)
- пользователь, план, даты старта/окончания, цена, статус;
- сервисные поля: адрес/координаты, слот, `cadenceDays`, `nextPickupAt`;
- контроль лимитов: `totalAllowedOrders`, `usedOrders`;
- пауза: `pauseStartedAt`, `pausedDaysUsed`.

### `ServiceZone` (`service_zones`)
- активная зона обслуживания с названием и полигоном координат (`jsonb`).

### `Payment` (`payments`)
- тип, статус, сумма, `externalId`, связь с `order`/`subscription`.
- в текущем MVP оплата имитируется (мок-режим).

### `TelegramLoginSession` (`telegram_login_sessions`)
- `session_id`, статус (`PENDING/VERIFIED/REJECTED/EXPIRED`), TTL, привязка к пользователю/телефону, timestamp и т.д.

## 5) API: полный список методов

Ниже указаны HTTP-методы, URL и доступ.

### 5.1 Auth (`/api/auth`)

1. `POST /api/auth/send-code`  
Публичный. Отправка OTP по SMS.

2. `POST /api/auth/register`  
Публичный. Регистрация после успешной OTP-проверки.  
`ADMIN` зарегистрировать через этот endpoint нельзя.

3. `POST /api/auth/login`  
Публичный. Логин по телефону/паролю, выдача JWT.

### 5.2 Telegram Auth (`/api/auth/telegram`)

1. `POST /api/auth/telegram/init`  
Публичный. Создание одноразовой login-сессии (TTL), возврат `start_url`.

2. `GET /api/auth/telegram/status?session_id=...&consume=false|true`  
Публичный. Проверка статуса Telegram-сессии.  
При `consume=true` и `VERIFIED` — одноразовая выдача JWT.

3. `POST /api/auth/telegram/verify`  
Публичный для сервисного вызова от бота.  
Принимает `session_id`, `phone`, `telegram_user_id`, `timestamp`, `signature`.

### 5.3 Users (`/api/users`)

1. `GET /api/users/me`  
Требует JWT. Профиль текущего пользователя.

2. `PATCH /api/users/me/name`  
Требует JWT. Обновление имени.

### 5.4 Orders (`/api/orders`)

1. `POST /api/orders`  
JWT, только `CLIENT`. Создать заказ (разовый/по подписке).

2. `GET /api/orders/address/suggestions?q=...&limit=...`  
JWT. Подсказки адресов через геокодер.

3. `GET /api/orders`  
JWT.  
- `CLIENT`: свои заказы  
- `COURIER`: доступные + активные  
- `ADMIN`: все заказы

4. `GET /api/orders/available`  
JWT, `COURIER`. Доступные заказы.

5. `GET /api/orders/active`  
JWT, `COURIER`. Активные заказы курьера.

6. `GET /api/orders/stats`  
JWT, `COURIER`. Статистика (available/active).

7. `GET /api/orders/{id}`  
JWT. Доступ зависит от роли и владения заказом.

8. `DELETE /api/orders/{id}/cancel`  
JWT, `CLIENT`. Отмена своего заказа.

9. `POST /api/orders/{id}/accept`  
JWT, `COURIER`. Взять заказ.

10. `PATCH /api/orders/{id}/status`  
JWT, `COURIER`. Обновить статус заказа.

11. `PATCH /api/orders/admin/{id}/status`  
JWT, `ADMIN`. Админское изменение статуса.

### 5.5 Subscriptions (`/api/subscriptions`)

1. `POST /api/subscriptions`  
JWT, `CLIENT`. Создание подписки.

2. `GET /api/subscriptions`  
JWT. Все подписки пользователя.

3. `GET /api/subscriptions/active`  
JWT. Подписки для управления (ACTIVE + PAUSED).

4. `DELETE /api/subscriptions/{id}`  
JWT. Отмена подписки.

5. `PATCH /api/subscriptions/{id}/pause`  
JWT. Пауза подписки.

6. `PATCH /api/subscriptions/{id}/resume`  
JWT. Возобновление.

7. `PATCH /api/subscriptions/{id}/skip-next`  
JWT. Пропуск ближайшего вывоза.

8. `PATCH /api/subscriptions/{id}/address`  
JWT. Изменение адреса подписки.

9. `PATCH /api/subscriptions/{id}/slot`  
JWT. Изменение временного слота.

10. `PATCH /api/subscriptions/{id}/extend`  
JWT. Продление подписки.

### 5.6 Service Zones (`/api/zones`)

1. `GET /api/zones/active`  
Публичный. Получение активной зоны обслуживания.

2. `POST /api/zones`  
JWT, только `ADMIN`. Установка активной зоны.  
Деактивация старых зон выполняется атомарно (транзакция SERIALIZABLE).

## 6) Ключевая бизнес-логика

### 6.1 Регистрация/логин по телефону
- OTP отправляется через SMS.ru.
- OTP хранится в памяти процесса (`ConcurrentHashMap`), TTL 10 минут.
- ограничение: повторная отправка не чаще 60 секунд.
- при регистрации:
  - проверяется OTP,
  - пароль хешируется BCrypt,
  - роль `ADMIN` запрещена через публичную регистрацию.

### 6.2 Telegram авторизация
- сайт создает `session_id` и дает ссылку на бота (`t.me/.../start`).
- бот подтверждает контакт и вызывает `/api/auth/telegram/verify`.
- backend проверяет:
  - формат `session_id`,
  - TTL сессии,
  - `timestamp` (clock skew),
  - HMAC подпись.
- телефон нормализуется и принимается только RU формат `+7XXXXXXXXXX`.
- сессия одноразовая: после `consume=true` помечается `consumed`.

### 6.3 Логика заказов
- создание заказа доступно только `CLIENT`.
- адрес обязательно в активной зоне обслуживания (point-in-polygon).
- `pickupTime`:
  - минимум +1 час от текущего времени,
  - максимум 7 дней вперед,
  - только слоты: 08-11, 13-16, 19-21.
- при заказе по подписке учитывается лимит вывозов.
- при отменах происходит восстановление счетчика использованных вывозов подписки.

### 6.4 Логика подписок
- пользователь не может иметь одновременно новую ACTIVE/PAUSED подписку.
- планы:
  - `WEEKLY` (по факту период 14 дней),
  - `MONTHLY` (30 дней),
  - `QUARTERLY` (90 дней),
  - `YEARLY` (364 дня).
- `cadenceDays`: 1 (ежедневно) или 2 (через день).
- число разрешенных вывозов считается формулой по датам и cadence.
- автопланирование следующего заказа делает `SubscriptionSchedulingService`.
- пауза:
  - суммарный лимит паузы: 15% срока подписки,
  - после resume срок подписки продлевается на использованные pause-дни.
- доступны действия: pause, resume, skip-next, extend, change address/slot.

### 6.5 Курьерский поток
- курьер видит доступные заказы, берет заказ, меняет статус в допустимой последовательности:
  - `ACCEPTED -> ON_THE_WAY -> PICKED_UP -> COMPLETED`
  - отдельный сценарий `CANCELLED_BY_COURIER` на допустимых этапах.

### 6.6 Админский поток
- админ управляет зонами обслуживания.
- админ может менять статусы заказов через `/api/orders/admin/{id}/status`.

## 7) Защита и безопасность

### 7.1 Аутентификация и роли
- stateless JWT.
- фильтр `JwtAuthenticationFilter` извлекает пользователя из БД по `userId` claim.
- запрет доступа забаненным пользователям.
- проверка phone verification для клиентских чувствительных endpoint’ов (`/api/orders`, `/api/subscriptions`, `/api/payments`), кроме адресных подсказок.

### 7.2 Авторизация запросов
- публичные: `/api/auth/**`, `/api/zones/active`, Swagger.
- остальные endpoint’ы — только с JWT.
- внутри контроллеров/сервисов дополнительно проверяется роль (`CLIENT/COURIER/ADMIN`).

### 7.3 Пароли и токены
- пароли — BCrypt.
- JWT подписывается секретом (`JWT_SECRET`, минимум 32 байта).
- для local/test есть fallback JWT secret (не для production).

### 7.4 Telegram security
- HMAC-SHA256 подпись `session_id + phone + telegram_user_id + timestamp`.
- TTL сессии и ограничение skew timestamp.
- сессия одноразовая (`consumed_at`).
- в логах телефон маскируется.

### 7.5 CORS/CSRF
- CORS разрешен для:
  - `http://localhost:5173`
  - `https://musoren-front.vercel.app`
- CSRF отключен (REST + JWT).

### 7.6 Антигонки / консистентность
- пессимистическая блокировка:
  - `OrderRepository.findByIdWithLock` (принятие заказа),
  - `SubscriptionRepository.findByIdWithLock` (планирование подписок).
- для активной зоны используется транзакция SERIALIZABLE и массовая деактивация прошлых зон.

## 8) Интеграции

### 8.1 PostgreSQL
- основная БД.
- локально: `localhost:5433`, база `musor_service`, user `musor_user`.

### 8.2 Flyway
- миграции в `src/main/resources/db/migration`.
- присутствуют миграции `V2..V6`.

### 8.3 Redis
- используется для кэш-менеджера (`RedisCacheManager`), TTL по умолчанию 24 часа.

### 8.4 Yandex Geocoder
- координаты по адресу;
- адресные подсказки;
- без fallback-провайдера.

### 8.5 SMS.ru
- отправка OTP в процессе регистрации.

### 8.6 Telegram Bot
- login session flow через backend endpoint `/api/auth/telegram/*`.

## 9) Конфигурация и переменные окружения

Основной конфиг:
- `src/main/resources/application.yaml`

Ключевые env:
- `SERVER_PORT` (default `8082`)
- `SPRING_PROFILES_ACTIVE` (`local`/`prod`)
- `JWT_SECRET`
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD`
- `YANDEX_GEOCODER_API_KEY`
- `SMS_RU_API_ID`
- `TELEGRAM_BOT_USERNAME`
- `TELEGRAM_AUTH_HMAC_SECRET`
- `TELEGRAM_LOGIN_SESSION_TTL_SECONDS`
- `TELEGRAM_AUTH_MAX_CLOCK_SKEW_SECONDS`

Особенность:
- `CoreApplication` загружает локальный `.env` через `EnvFileLoader` (если файл есть).

## 10) Профили запуска

### `local`
- JPA: `ddl-auto: update`
- SQL-логи включены (`show-sql: true`)
- Flyway включен (`validate-on-migrate: false`)

### `prod`
- JPA: `ddl-auto: validate`
- SQL-логи выключены
- Flyway включен

## 11) Docker и локальный запуск

### Инфраструктура (docker compose)
Файл: `docker/docker-compose.yml`
- `postgres-musor` (PostgreSQL 15), порт `5433`
- `redis` (redis:7-alpine), порт `6379`

Запуск:
1. `cd docker`
2. `docker compose up -d`

### Backend
1. из корня проекта: `./gradlew bootRun`
2. API: `http://localhost:8082`
3. Swagger UI: `http://localhost:8082/swagger-ui/index.html`

## 12) Тесты

Набор тестов есть и покрывает критичные куски:
- `SubscriptionServiceTest`:
  - активные + paused подписки,
  - лимит паузы 15%,
  - корректный resume,
  - запрет новой подписки при paused.
- `OrderServiceTest`:
  - восстановление лимита подписки при отмене заказа.
- `DtoMapperTest`:
  - пакетная загрузка payments без N+1 на каждый заказ.
- `GlobalExceptionHandlerTest`:
  - обработка и FieldError, и ObjectError.
- `UserTest`:
  - null-safe поведение `banned`.
- `SubscriptionPlanTest`:
  - описание/параметры планов.
- `CoreApplicationTests`:
  - smoke context load.

## 13) Postman

Папка: `postman/`
- regression + full E2E collection:
  - `Core_API_Regression_2026.postman_collection.json`
  - `Core_Local_Env_2026.postman_environment.json`
- README: `postman/README.md` с пошаговым прогоном.

## 14) Важные текущие ограничения (честно по коду)

1. OTP хранится в памяти приложения, а не в Redis/БД (после рестарта коды теряются).  
2. Есть два CORS-конфига (`CorsConfig` и CORS внутри `SecurityConfig`) — рабоче, но избыточно.  
3. `PaymentService` сейчас MVP-заглушка (авто-success), без реального платежного провайдера.  
4. В `local/test` допустим fallback JWT secret (для production обязательно задать `JWT_SECRET`).  

## 15) Быстрый справочник по ролям

### CLIENT
- регистрация/логин;
- профиль;
- создание/отмена заказа;
- управление своей подпиской;
- Telegram login.

### COURIER
- просмотр доступных заказов;
- принятие заказа;
- обновление статусов своих заказов;
- просмотр своей статистики.

### ADMIN
- изменение статусов заказов через admin endpoint;
- управление активной зоной обслуживания.

---

Если нужно, можно сделать вторую версию этого файла в формате:
- OpenAPI-style endpoint table (request/response examples),
- ER-диаграмма БД,
- sequence-диаграммы по 3 основным флоу (SMS auth, Telegram auth, Subscription scheduling).
