# Postman: Core API Regression + Full E2E (2026)

## Файлы
- `Core_API_Regression_2026.postman_collection.json`
- `Core_Local_Env_2026.postman_environment.json`

Старые файлы удалены:
- `Core_API_Full_Test_Pack.postman_collection.json`
- `Core_Local_Current_Config.postman_environment.json`

## Что теперь есть
1. `00..08` — регрессионные API/security/negative тесты.
2. `09 Full E2E Cycle` — полный бизнес-цикл:
- регистрация по номеру (SMS OTP) -> логин -> профиль -> подписка -> заказ -> отмена,
- вход через Telegram WebApp/бот -> профиль -> подписка -> заказ,
- optional courier lifecycle до `COMPLETED`.

## Обязательные переменные environment
- `baseUrl` (обычно `http://localhost:8082`)
- `telegramBotToken` (должен совпадать с `TELEGRAM_BOT_TOKEN` backend; если в backend токен пустой, оставь пустым)

## Для полного цикла по номеру (OTP)
Заполни перед запуском папки `09 Full E2E Cycle`:
- `phoneRegPhone` — твой номер в формате `+7XXXXXXXXXX`
- `phoneRegOtpCode` — код из SMS (после шага `Phone Flow: Send OTP`)
- опционально `phoneRegName`, `phoneRegPassword`

Важно: шаг регистрации по номеру может вернуть `400`, если пользователь уже существует.
Это не блокер — следующий шаг `Phone Flow: Login` продолжает сценарий.

## Для полного цикла заказа до COMPLETED (optional)
Заполни:
- `runCourierLifecycle=true`
- `courierPhone`, `courierPassword`

Если `runCourierLifecycle=false`, сценарий Telegram завершится через клиентскую отмену заказа.

## Для авто-настройки зоны обслуживания (optional)
Чтобы заказы гарантированно создавались в E2E, заполни:
- `adminPhone`, `adminPassword`

Тогда шаги:
- `E2E Optional: Admin login`
- `E2E Optional: Set active zone as admin`
настроят зону автоматически.

## Рекомендуемый запуск
1. Подними backend локально (`local` профиль).
2. Импортируй collection и environment.
3. Выбери environment `Core Local Env 2026`.
4. Для общего регресса запускай всю коллекцию.
5. Для полного бизнес-цикла запускай только папку `09 Full E2E Cycle`.

## Почему не должно быть `JSONError: No data, empty input`
- динамические тела (`phoneFlowOrderPayload`, `tgFlowOrderPayload`) всегда формируются в `pre-request`;
- optional шаги автоматически `skip`, если не заполнены prerequisites;
- JSON в коллекции валиден и проверен.
