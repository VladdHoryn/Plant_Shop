---
owner: VladdHoryn
status: published
review_date: 2027-02-20
tags:
  - developer
---

# Стандарти коду

Правила, які діють у всьому репозиторії. Більшість із них перевіряє автоматика, тому тут — що саме перевіряється
і де.

## Формат файлів

| Правило | Де задано |
|---|---|
| UTF-8, LF, фінальний перевід рядка, без хвостових пробілів | `.editorconfig`, `.gitattributes` (LF однаковий на Windows і Linux, щоб метрики не залежали від ОС) |
| Java: відступ 4 пробіли, рядок ≤ 120 символів | `.editorconfig` |
| YAML/JSON: відступ 2 пробіли | `.editorconfig` |
| Markdown: GitHub-таблиці, блоки коду з мовою, без HTML-розмітки (працює в Obsidian і Notion) | `.markdownlint-cli2.yaml` |

## Структура сервісу

`controller` → `service` → `repository` (Spring Data JPA), а також `entity`, `dto` (record-класи), `mapper`
(ручні `final`-класи зі статичними методами, без MapStruct), `exception` (`GlobalExceptionHandler` як
`@RestControllerAdvice`), `event`, `client` (Feign). Lombok використовується скрізь. Колаборатори
впроваджуються через конструктор (`final`-поля, без польового `@Autowired`).

## Межі складності та якості

| Метрика | Поріг | Чим перевіряється |
|---|---|---|
| Цикломатична складність методу | CC ≤ 10 | хук `pre-commit` і CI `code-metrics` (Lizard) |
| Розмір методу | ≤ 60 NLOC | CI `code-metrics` |
| Покриття `secure-logging` | ≥ 80 % (JaCoCo) | CI `quality-gate` |
| Mutation score `secure-logging` | ≥ 65 % (PIT) | CI `quality-gate` |

Якщо метод перевищує CC 10, усталене рішення в проєкті — розділити логіку або замінити ланцюжок умов таблицею
відповідності (приклади: `PaymentService`, `WateringPlanner`).

## Дані, події, журнали

- Схему БД змінюють лише новою міграцією Flyway `V<n>__description.sql`; застосовану міграцію не редагують
  (`ddl-auto: validate`).
- Подію публікують з `@TransactionalEventListener` (після commit); слухач ідемпотентний. Нова або перейменована
  подія змінює всі копії record-класу в сервісах і `spring.json.type.mapping` у config-service.
- Персональні дані в журналах маскує `secure-logging`; секрети — лише через змінні оточення.

## Інструменти, закріплені за версією

Аналізатори та генератори запускаються в Docker-образах з точними версіями, щоб результат змінювався лише разом
із кодом, а не тому, що інструмент оновився.

| Інструмент | Версія | Образ / файл |
|---|---|---|
| Lizard (CC, NLOC) | 1.24.3 | `tools/lizard/Dockerfile` |
| MkDocs + Material + плагіни | 1.6.1 / 9.7.6 | `tools/mkdocs/requirements.txt` |
| Spectral | 6.15.0 | `stoplight/spectral:6.15.0` |
| markdownlint-cli2 | 0.18.1 | `davidanson/markdownlint-cli2:v0.18.1` |
| OpenAPI Generator | 7.14.0 | `openapitools/openapi-generator-cli:v7.14.0` |
| Schemathesis | 4.1.0 | `schemathesis/schemathesis:v4.1.0` |
