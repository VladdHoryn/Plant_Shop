---
owner: VladdHoryn
status: published
review_date: 2027-02-20
tags:
  - api
---

# API: як інтегруватися

Усі запити йдуть через API gateway `http://localhost:8080` (Docker Compose) і маршрутизуються за префіксом:
`/api/catalog/**`, `/api/inventory/**`, `/api/orders/**`, `/api/payments/**`, `/api/users/**`,
`/api/marketing/**`, `/api/analytics/**`. Формат — JSON.

| Що потрібно | Де | Джерело |
|---|---|---|
| Формальний контракт клієнтського шляху (каталог → замовлення → трекінг) | [Довідник API (Swagger UI)](reference.md), файл [`openapi.yaml`](openapi.yaml) | **API-first**: контракт написано до/поза кодом, код перевіряють на відповідність ([ADR-0005](../architecture/adr/0005-api-first-openapi-contract.md)) |
| Повний перелік ендпоінтів усіх сервісів з контрактами поведінки | [SDD, розділ 5](../architecture/sdd.md#5-api-контракти) | CI перевіряє, що кожен ендпоінт контролерів є в SDD |
| Схеми всіх ендпоінтів сервісу | `http://localhost:<порт>/swagger-ui.html` кожного сервісу | **code-first**: springdoc генерує з анотацій під час роботи |
| Приватність, згоди, демо-акаунти | [API приватності](privacy.md) | |
| Внутрішня будова класів | [Javadoc](javadoc.md) | генерується з коментарів коду |

## Версіонування контракту

`info.version` у `openapi.yaml` — SemVer: MAJOR — зміна, що ламає клієнтів (видалене поле, змінений тип, новий
обов'язковий параметр); MINOR — нова операція або необов'язкове поле; PATCH — лише описи та приклади. Кожна зміна
контракту — запис у [CHANGELOG](../developer/changelog.md) у тому самому pull request.

## Формат помилок

Усі маршрути повертають один об'єкт `Error` (схема в контракті). Помилки, які формують самі сервіси
(`GlobalExceptionHandler`), — RFC 9457 Problem Details з типом `application/problem+json`; помилки фреймворку
(зламаний JSON, неправильний тип параметра, gateway без живого екземпляра) мають поля `timestamp`, `error`, `path`
і тип `application/json`. Поле `status` є завжди. Повна уніфікація формату — [ADR-0006](../architecture/adr/0006-uniform-problem-detail-errors.md)
(статус Proposed).

| Код | Коли | Додаткові поля |
|---|---|---|
| 400 | валідація тіла, невідома або неактивна рослина, зламаний JSON, неправильний тип параметра | `errors` — поле → повідомлення |
| 404 | рослини або замовлення з таким id немає | |
| 409 | бракує товару на момент перевірки | `plantIds` |
| 503 | catalog/inventory/payment-service недоступні, або gateway не знайшов екземпляр сервісу | |

## Спробувати через gateway

```bash
# Каталог: фільтри за категорією, рівнем догляду, ціною; пагінація
curl "http://localhost:8080/api/catalog/plants?careLevel=EASY&maxPrice=25&page=0&size=5"
curl  http://localhost:8080/api/catalog/categories

# Залишки
curl http://localhost:8080/api/inventory/1
curl -X PUT http://localhost:8080/api/inventory/1 -H "Content-Type: application/json" -d '{"quantity": 50}'

# Замовлення (відповідь 202, статус PENDING)
curl -X POST http://localhost:8080/api/orders -H "Content-Type: application/json" -d '{
  "customerEmail": "jane@example.com",
  "shippingAddress": "12 Fern Street, Kyiv",
  "paymentMethod": "CARD",
  "items": [ {"plantId": 1, "quantity": 2}, {"plantId": 3, "quantity": 1} ]
}'

# За мить замовлення CONFIRMED, залишок списано
curl http://localhost:8080/api/orders/1/tracking     # статус замовлення + платежу + наступний крок
curl http://localhost:8080/api/orders/1
curl http://localhost:8080/api/payments/order/1
curl "http://localhost:8080/api/orders?email=jane@example.com"
```

Щоб побачити шлях відмови, поповніть залишок рослини і замовте її більше ніж на 10 000 (наприклад,
120 × Japanese Maple, рослина 8): платіж відхилено, замовлення `CANCELLED`, резерв знято.

## Клієнтський SDK

Java-клієнт генерується з контракту, а не пишеться вручну:

```bash
./scripts/api-sdk.sh        # openapi-generator 7.14.0 -> sdk-client/ (не комітиться, відтворюється з контракту)
```
