# Тестирование Security

## Текущая реализация

Security настроен для MVP-версии и **полностью работоспособен** для тестирования:

1. **Фильтр аутентификации** (`HeaderAuthenticationFilter`) извлекает пользователя по заголовку `X-User-Id`
2. **SecurityConfig** требует аутентификацию для всех эндпоинтов кроме `/api/auth/**`
3. **@AuthenticationPrincipal** автоматически извлекает `User` из SecurityContext

## Как это работает

### 1. Публичные эндпоинты (без аутентификации)
- `POST /api/auth/register` - регистрация
- `POST /api/auth/login` - вход
- `GET /v3/api-docs/**` - Swagger документация
- `GET /swagger-ui/**` - Swagger UI

### 2. Защищённые эндпоинты (требуют заголовок X-User-Id)

Все остальные эндпоинты требуют:
- Заголовок `X-User-Id` с ID пользователя
- Пользователь должен существовать в БД

**Пример запроса:**
```
GET /api/orders
Headers:
  X-User-Id: 1
```

### 3. Поведение при отсутствии аутентификации

Если заголовок `X-User-Id` отсутствует или пользователь не найден:
- Возвращается **401 Unauthorized**
- Ответ пустой (стандартное поведение Spring Security)

## Тестирование в Postman

### ✅ Правильный запрос
```
POST /api/orders
Headers:
  Content-Type: application/json
  X-User-Id: 1
Body:
  {
    "address": "г. Оренбург, ул. Ленина, д. 1",
    "pickupTime": "2025-11-06T14:00:00+03:00",
    "lat": 51.7687,
    "lng": 55.1017
  }
```
**Результат:** 201 Created (заказ создан)

### ❌ Неправильный запрос (без заголовка)
```
POST /api/orders
Headers:
  Content-Type: application/json
Body:
  { ... }
```
**Результат:** 401 Unauthorized

### ❌ Неправильный запрос (несуществующий пользователь)
```
POST /api/orders
Headers:
  Content-Type: application/json
  X-User-Id: 999
Body:
  { ... }
```
**Результат:** 401 Unauthorized

## Проверка работы Security

### Тест 1: Доступ без аутентификации
```
GET http://localhost:8080/api/orders
```
**Ожидается:** 401 Unauthorized

### Тест 2: Доступ с валидным пользователем
```
GET http://localhost:8080/api/orders
Headers:
  X-User-Id: 1
```
**Ожидается:** 200 OK (список заказов или пустой массив)

### Тест 3: Доступ к публичному эндпоинту
```
POST http://localhost:8080/api/auth/register
Body:
  {
    "phone": "+79051234567",
    "name": "Тест",
    "role": "CLIENT"
  }
```
**Ожидается:** 201 Created (пользователь создан)

## Проверка ролей в контроллерах

Контроллеры проверяют роли программно:

```java
if (currentUser.getUserRole() != UserRole.CLIENT) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
}
```

**Примеры:**
- Создание заказа требует `CLIENT` → 403 Forbidden для COURIER/ADMIN
- Принятие заказа требует `COURIER` → 403 Forbidden для CLIENT/ADMIN
- Установка зоны требует `ADMIN` → 403 Forbidden для CLIENT/COURIER

## Что можно улучшить в будущем

1. **JWT токены** вместо заголовка X-User-Id
2. **Method-level security** с аннотациями `@PreAuthorize("hasRole('CLIENT')")`
3. **UserDetailsService** для стандартной интеграции со Spring Security
4. **Обработка ошибок** с JSON-ответами вместо стандартных 401/403

## Текущий статус

✅ **Security полностью работоспособен для тестирования**
- Фильтр работает корректно
- Защита эндпоинтов работает
- @AuthenticationPrincipal извлекает User
- Все эндпоинты можно тестировать через Postman

**Можно смело тестировать все сценарии!**

