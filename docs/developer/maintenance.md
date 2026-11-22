---
owner: VladdHoryn
status: published
review_date: 2027-02-20
tags:
  - developer
  - ops
---

# Регламент підтримки документації (Documentation Maintenance Regime)

| Поле | Значення |
|---|---|
| Цикл перегляду регламенту | 90 днів |
| Чек-лист автора PR | [CONTRIBUTING → Definition of Done](CONTRIBUTING.md#definition-of-done-docs-or-dont-merge) |

## 1. Головне правило

> **Зміна коду = зміна документації.** Жодне злиття в `master` не проходить, якщо змінено поведінку, контракт,
> компонент або розгортання, а зачеплені документи не оновлено в тому самому pull request.

Правило забезпечується чотирма рівнями:

| Рівень | Що перевіряє | Як |
|---|---|---|
| Автоматично в CI: код ↔ документація | кожен REST-ендпоінт контролерів є в SDD (розділ 5); кожна операція `docs/api/openapi.yaml` існує в контролері; `depends_on` кожного контейнера `docker-compose.yml` збігається з таблицею запуску ISD (розділ 5) | job `doc-drift` у `.github/workflows/docs-currency.yml` → `./scripts/doc-drift-check.sh` |
| Автоматично в CI: якість документації | контракт API (Spectral, 0 errors), розмітка (markdownlint), мертві посилання, якорі і сторінки поза навігацією (`mkdocs build --strict`) | jobs `lint` і `build` у `.github/workflows/docs.yml`; червоний job блокує злиття і публікацію сайту |
| Звіт у CI | прострочені `review_date`; вік документів і кількість комітів коду після останнього оновлення документа | `./scripts/doc-review-audit.sh` і `./scripts/doc-currency.sh` → GitHub Step Summary |
| Людина (рецензент PR) | усе, що скрипти не бачать: зміст опису, діаграми, ADR, RTO/RPO | власник з `.github/CODEOWNERS`, чек-лист `.github/pull_request_template.md` |

## 2. Паспорт документа: власник, статус, наступний перегляд

Власник і дата перегляду живуть **у самому документі** — у front matter, а не в окремій таблиці (окрема таблиця
в ЛР№10 дублювала факти і могла розійтися з документами):

```yaml
---
owner: VladdHoryn          # відповідальний за актуальність; він же рецензент у CODEOWNERS
status: published          # draft / in-review / published / deprecated
review_date: 2027-02-20    # дата наступного обов'язкового перегляду
tags: [architecture]       # architecture / api / ops / developer / adr / overview - теги графа Obsidian і сайту
superseded_by: architecture/sdd.md   # лише для status: deprecated - куди переспрямувати читача
---
```

Цикли перегляду з аудиту ЛР№10 (документ без оновлення понад 90 днів — підозрілий, понад 180 — ймовірно
застарілий): експлуатаційна і розробницька документація (README, ISD, developer, API) — 90 днів, архітектурна
(SDD, ADR, матриця аудиторій) — 180 днів. ADR при перегляді не переписується: перевіряється лише, чи рішення
досі діє.

Перелік усіх документів з власниками і датами друкує `./scripts/doc-review-audit.sh` (у CI — у Step Summary
job-а `lint`); прострочені сторінки позначені `OVERDUE`. Заступник власника (backup owner) для всіх документів:
**вакантно** — це single point of knowledge, див. [handover](handover.md#3-декларація-bus-factor).

Застаріла сторінка **не видаляється**: вона отримує `status: deprecated`, `superseded_by` і банер `DEPRECATED` з
посиланням на актуальний документ, лишається в розділі «Архів (deprecated)» навігації (так зроблено для старих
адрес `docs/sdd.md`, `docs/isd.md`, `docs/maintenance.md`, `docs/handover.md`).

Звіти лабораторних (`docs/*-report.md`, `docs/lab-*.md`, `docs/reports/`, `FINANCIAL_LEDGER.md`) — документація
процесу: вони фіксують факт у часі, не мають циклу перегляду, не оновлюються і не входять до сайту
(`exclude_docs` у `mkdocs.yml`). Якщо такий звіт вводить в оману щодо поточної системи, він позначається банером
`DEPRECATED` з посиланням на актуальний документ (так зроблено для `docs/k8s-report.md` і `docs/helm-report.md`).

## 3. Що оновлювати при зміні коду

| Змінено в коді | Оновити |
|---|---|
| `*/controller/*.java` (новий, перейменований, видалений ендпоінт, змінений формат відповіді) | SDD 5.1/5.2; `docs/api/openapi.yaml` + `info.version` (SemVer), якщо це клієнтський шлях (каталог, замовлення, трекінг); `CHANGELOG.md`; README «What it does», якщо змінилась поведінка для користувача |
| `*/client/*.java` (новий Feign-клієнт, нова синхронна залежність) | SDD 2 (C4: стрілка), SDD 3 (залежності компонента); ISD 5, якщо змінився порядок запуску |
| `*/event/*`, `spring.json.type.mapping` | SDD 2.1, SDD 5.3; ADR-0002, якщо змінюється стиль інтеграції (новий ADR) |
| `*/exception/GlobalExceptionHandler.java` | схема `Error` і відповіді 4xx/5xx у `docs/api/openapi.yaml`; `docs/api/index.md` «Формат помилок» |
| `*/db/migration/V*.sql` | SDD 4 |
| новий модуль / сервіс | SDD 2, 3, 4, 5; ISD 1, 3, 4, 5; `docs/developer/local-setup.md`; `docs/lab-10/doc-map.txt` аудиту |
| `docker-compose.yml`, `Dockerfile`, `k8s/`, `helm/` | ISD 1, 3, 5, 7, 8; `docs/architecture/deployment.md` |
| `.github/workflows/*`, `scripts/git-hooks/*` | ISD 6 |
| змінні в `config-service/.../configurations/*.yml` | ISD 4 |
| архітектурне рішення змінено | новий ADR з новим номером; старий — статус `Superseded by ADR-NNNN` |
| нова сторінка документації | `nav` у `mkdocs.yml` (інакше strict-збірка впаде), front matter-паспорт, `docs/lab-10/doc-map.txt` |

## 4. Процедура перегляду

1. `./scripts/doc-drift-check.sh` — має бути `RESULT: PASS`.
2. `./scripts/docs-lint.sh` і `./scripts/docs-build.sh` — 0 помилок, strict-збірка зелена.
3. `./scripts/doc-review-audit.sh` — які сторінки прострочені; `./scripts/doc-currency.sh` — «Lag» = 0 або всі
   коміти з «Lag» не зачіпають описане в документі.
4. Прочитати документ і звірити твердження з кодом (зразок — `docs/lab-10/claim-verification.txt`).
5. Оновити `review_date` (дата перегляду + цикл); якщо документ застарів — `status: deprecated` і `superseded_by`;
   закомітити як `docs(maintenance): review <документ>`.
6. Мажорна зміна документації — анотований тег `docs-vX.Y` і запис у `CHANGELOG.md`.
