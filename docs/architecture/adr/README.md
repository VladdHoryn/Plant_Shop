---
owner: VladdHoryn
status: published
review_date: 2027-05-13
tags:
  - architecture
  - adr
---

# Architecture Decision Records

Кожне значуще архітектурне рішення — окремий файл `NNNN-коротка-назва.md`. Запис фіксується в момент
ухвалення і **не переписується**: якщо рішення змінюється, старий запис отримує статус
`Superseded by ADR-NNNN`, а нове рішення — новий номер.

Життєвий цикл запису: `Proposed` (запропоновано, обговорюється) → `Accepted` (діє) → `Deprecated` (більше не
застосовується) або `Superseded by ADR-NNNN` (замінено новим записом). Після `Accepted` змінюється лише рядок
статусу; текст рішення не переписується. Front matter `status` описує стан **сторінки** в базі знань і
відповідає статусу запису так: Proposed → `in-review`, Accepted → `published`, Deprecated / Superseded →
`deprecated`.

| № | Рішення | Статус | Дата |
|---|---|---|---|
| [0001](0001-database-per-service-postgresql-flyway.md) | Окрема БД PostgreSQL на сервіс, схема лише через Flyway | Accepted | 2026-10-14 |
| [0002](0002-kafka-choreography-saga.md) | Сага замовлення як хореографія подій Kafka | Accepted (доповнено 0003) | 2026-10-14 |
| [0003](0003-synchronous-payment-read-for-tracking.md) | Синхронне читання платежу для трекінгу замовлення (доповнює 0002) | Accepted | 2026-11-14 |
| [0004](0004-documentation-as-code-mkdocs.md) | Документація як код: MkDocs Material, strict-збірка, GitHub Pages | Proposed | 2026-11-22 |
| [0005](0005-api-first-openapi-contract.md) | Контракт клієнтського API пишеться першим (OpenAPI) і є єдиним джерелом істини | Accepted | 2026-11-22 |
| [0006](0006-uniform-problem-detail-errors.md) | Єдиний формат помилок RFC 9457 для всіх відповідей | Proposed | 2026-11-22 |

## Шаблон

```markdown
# ADR-NNNN: <рішення одним реченням>

| Поле | Значення |
|---|---|
| Номер | NNNN |
| Статус | Proposed / Accepted / Deprecated / Superseded by ADR-MMMM |
| Дата рішення | YYYY-MM-DD |
| Автор / власник | ... |
| Зачіпає | модулі, файли |

## Контекст
Яка проблема і які сили (вимоги, обмеження) діють.

## Розглянуті альтернативи
Таблиця: варіант, переваги, недоліки.

## Рішення
Що обрано і які правила з цього випливають.

## Наслідки
Позитивні, негативні, обмеження для розробників, пов'язані документи.
```
