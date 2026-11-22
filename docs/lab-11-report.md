# Звіт з лабораторної роботи №11

**Тема:** Documentation as Code: корпоративна база знань, статична генерація сайту та CI/CD-автоматизація
документування (MkDocs, ADR, OpenAPI).
**Об'єкт:** Plant Shop — мікросервісний інтернет-магазин рослин (Java 21, Spring Boot 3.5, Spring Cloud, Kafka,
PostgreSQL), архітектурний паспорт ЛР№10 (SDD, ISD, ADR-0001…0003).
**Гілка:** `lab/Lab_11`. **Сирі результати:** `docs/lab-11/`. **Теорія для захисту:** [`lab-11.md`](lab-11.md).
**Сайт документації:** <https://vladdhoryn.github.io/Plant_Shop/>

Усі числа звіту взято з файлів `docs/lab-11/` або з комітів; поруч із кожним числом вказано джерело.

## Зміст

- [1. Чотирирівнева піраміда документації і mkdocs.yml](#1-чотирирівнева-піраміда-документації-і-mkdocsyml)
- [2. База знань: front matter, CODEOWNERS, кущ ADR, граф Obsidian](#2-база-знань-front-matter-codeowners-кущ-adr-граф-obsidian)
- [3. API-контракт: OpenAPI, Swagger UI, Spectral, SDK, дрейф](#3-api-контракт-openapi-swagger-ui-spectral-sdk-дрейф)
- [4. CI/CD-конвеєр і краш-тест FAIL → PASS](#4-cicd-конвеєр-і-краш-тест-fail--pass)
- [5. Регламент безперервного супроводу](#5-регламент-безперервного-супроводу)
- [6. Посилання на опублікований сайт](#6-посилання-на-опублікований-сайт)
- [7. Лог валідації ШІ-Техліда](#7-лог-валідації-ші-техліда)
- [8. Висновки](#8-висновки)
- [9. Перелік скріншотів](#9-перелік-скріншотів)

---

## 1. Чотирирівнева піраміда документації і mkdocs.yml

### 1.1. Що було до ЛР№11

Після ЛР№10 знання вже жили в Markdown у репозиторії, але без збірки й навігації: `README.md` мав 512 рядків і
змішував усі рівні (Kubernetes-runbook, Helm-values, приватність, запуск з IDE, приклади `curl`), таблиця «API at a
glance» дублювала SDD 5.1, а SDD, ISD, регламент і handover лежали пласко в `docs/` разом зі звітами
лабораторних.

### 1.2. Схема репозиторію після реструктуризації

```text
Plant_Shop/
├── README.md                         РІВЕНЬ 1 «Що це і як запустити?» — 5 хв: призначення, quick start,
│                                     карта піраміди, таблиця Definition of Done (512 → 69 рядків)
├── CHANGELOG.md                      журнал змін (Keep a Changelog), рендериться на сайті
├── mkdocs.yml                        конфігурація бази знань: nav = піраміда, мова uk, плагіни, strict
├── overrides/main.html               плашка «Паспорт документа» з front matter на кожній сторінці
├── tools/mkdocs/                     закріплений інструментарій (Dockerfile + requirements.txt)
├── .spectral.yaml                    правила якості контракту API
├── .markdownlint-cli2.yaml           правила стилю Markdown
├── .obsidian/                        налаштування vault і графа Obsidian
├── .github/
│   ├── CODEOWNERS                    обов'язковий рецензент для docs/, контракту, конвеєра
│   └── workflows/docs.yml            CI/CD документації
└── docs/
    ├── index.md                      головна сайту = README (include, не копія)
    ├── developer/                    РІВЕНЬ 2 «Як приєднатися до розробки?»
    │   ├── CONTRIBUTING.md           робочий процес, docs-or-don't-merge, версії документації
    │   ├── local-setup.md            Compose, БД і Flyway, запуск з IDE, тести, документація локально
    │   ├── coding-standards.md       формат файлів, структура сервісу, пороги CC/покриття, закріплені інструменти
    │   ├── maintenance.md            регламент супроводу (перенесено з docs/)
    │   ├── handover.md               handover-пакет (перенесено з docs/)
    │   └── changelog.md              рендерить ../../CHANGELOG.md
    ├── api/                          РІВЕНЬ 3 «Як інтегруватися?»
    │   ├── index.md                  точки входу, формат помилок, приклади запитів, SDK
    │   ├── openapi.yaml              API-first контракт — єдине джерело істини
    │   ├── reference.md              Swagger UI, що рендерить openapi.yaml
    │   ├── privacy.md                API приватності (перенесено з README)
    │   └── javadoc.md                варіативний пункт: Javadoc з коментарів коду
    ├── architecture/                 РІВЕНЬ 4 «Чому система влаштована саме так?»
    │   ├── sdd.md, isd.md            паспорт ЛР№10 (перенесено з docs/)
    │   ├── deployment.md             runbook Kubernetes і Helm (перенесено з README)
    │   ├── audience-coverage.md      матриця покриття аудиторій
    │   └── adr/                      кущ рішень README + 0001…0006
    ├── tags.md                       сторінка тегів (ті самі теги, що в Obsidian)
    ├── sdd.md, isd.md, maintenance.md, handover.md   старі адреси: status: deprecated + переспрямування
    └── lab-*.md, *-report.md, …      документація процесу: у Git, але поза базою знань (exclude_docs)
```

Розподіл колишнього README без дублювання фактів (коміт `65656ac`):

| Розділ старого README | Новий рівень і файл | Чому там |
|---|---|---|
| What it does, Quick start | README (рівень 1) | відповідь «що це і як запустити» |
| Documentation map, Onboarding path | README → «Documentation» (піраміда), CONTRIBUTING | навігація по рівнях |
| API at a glance | **видалено**: повний перелік — SDD 5.1, формальний контракт — `openapi.yaml` | дублював SDD 5.1 (порушення SSOT) |
| Services, Order flow (saga) | **видалено**: SDD 2, 2.1, 3 | дублювали SDD |
| Database & Flyway, Docker Compose, IDE, Tests | `developer/local-setup.md` | робоче місце розробника |
| Kubernetes, Helm | `architecture/deployment.md` | експлуатація (поруч з ISD) |
| Privacy controls | `api/privacy.md` | API для інтеграції |
| Try it | `api/index.md` | приклади запитів API |

### 1.3. Лістинг mkdocs.yml (ключові частини)

```yaml
site_name: Plant Shop — база знань
site_url: https://vladdhoryn.github.io/Plant_Shop/
docs_dir: docs
exclude_docs: |              # документація процесу не входить до бази знань
  lab-*.md
  lab-*/
  *-report.md
  reports/
  complexity/
  privacy-demo.md
  goodhart-demo.md
  gqm-metrics.md
  .obsidian/

strict: true
validation:
  nav:
    omitted_files: warn        # orphan-сторінка = попередження = помилка в strict
    not_found: warn
    absolute_links: warn
  links:
    not_found: warn            # мертве посилання
    anchors: warn              # посилання на неіснуючий заголовок
    absolute_links: warn
    unrecognized_links: warn

theme:
  name: material
  language: uk
  custom_dir: overrides

plugins:
  - search:
      lang: [ru, en]           # lunr не має українського стемера
  - tags
  - include-markdown           # docs/index.md = README.md, changelog.md = CHANGELOG.md
  - swagger-ui-tag             # docs/api/reference.md = docs/api/openapi.yaml
  - mermaid2                   # C4 і sequence-діаграми SDD/ISD
  - git-revision-date-localized:
      type: date
      locale: uk
      timezone: Europe/Kyiv
      enable_creation_date: false
      fallback_to_build_date: true
      enable_git_follow: true
      strict: false            # git-особливості (неcommit-нута сторінка) не є дефектом документації

nav:
  - "1 · Що це і як запустити": index.md
  - "2 · Розробникам":
      - developer/CONTRIBUTING.md
      - developer/local-setup.md
      - developer/coding-standards.md
      - developer/maintenance.md
      - developer/handover.md
      - developer/changelog.md
  - "3 · API":
      - api/index.md
      - api/reference.md
      - api/privacy.md
      - api/javadoc.md
  - "4 · Архітектура":
      - architecture/sdd.md
      - architecture/isd.md
      - architecture/deployment.md
      - architecture/audience-coverage.md
      - "ADR":
          - architecture/adr/README.md
          - architecture/adr/0001-database-per-service-postgresql-flyway.md
          - …                    # 0002 … 0006
  - "Теги": tags.md
  - "Архів (deprecated)":
      - sdd.md
      - isd.md
      - maintenance.md
      - handover.md
```

Повний файл — `mkdocs.yml` у корені (також `markdown_extensions`: `admonition`, `pymdownx.superfences` з
кастомною fence для Mermaid, `toc` зі `slugify`, що зберігає кирилицю в якорях як на GitHub).

Інженерні рішення, яких немає в завданні, але без яких вимоги не виконуються чесно:

| Рішення | Причина |
|---|---|
| `validation.nav.omitted_files: warn` | у MkDocs 1.6 сторінка поза nav за замовчуванням лише `INFO`; без цього `--strict` **не** зупинив би orphan-документ |
| `toc.slugify` з `pymdownx.slugs.slugify(case: lower)` | стандартний slugify викидає кирилицю; посилання на кшталт `sdd.md#4-модель-даних` працюють однаково на GitHub, в Obsidian і на сайті |
| Docker-образ `tools/mkdocs` замість `pip install` на хості | Python на машині розробника не встановлено, а правила репозиторію забороняють `pip install` на хості; ті самі версії з `requirements.txt` ставить CI |
| `exclude_docs` для звітів лабораторних | звіти — документація процесу, їх не редагують, тож їхні історичні посилання не можна вимагати валідними |

### 1.4. Локальна збірка

`./scripts/docs-build.sh` (той самий `mkdocs build --strict`, що в CI) — `docs-build: PASS (strict)`; сайт
відображає всі 29 сторінок продукту: 4 діаграми Mermaid у SDD, Swagger UI з контрактом, плашки паспорта.
Перевірка, що strict справді працює, до краш-тесту: тимчасово додане мертве посилання і неіснуючий якір дали
`Aborted with 2 warnings in strict mode!`, exit code 1 (розділ 4.3).

> 📸 SCREENSHOT: головна сторінка сайту (`./scripts/docs-build.sh serve` → `http://localhost:8000/Plant_Shop/`)
> з вкладками навігації «1 · Що це і як запустити / 2 · Розробникам / 3 · API / 4 · Архітектура» і плашкою
> «Паспорт документа».

---

## 2. База знань: front matter, CODEOWNERS, кущ ADR, граф Obsidian

### 2.1. Front matter-паспорти

Паспорт є в усіх 29 документах продукту — 0 без паспорта (`docs/lab-11/review-audit-today.txt`). Приклади:

`docs/architecture/sdd.md` (архітектурний документ, цикл 180 днів; дата з регламенту ЛР№10):

```yaml
---
owner: VladdHoryn
status: published
review_date: 2027-05-13
tags:
  - architecture
  - api
---
```

`docs/architecture/adr/0006-uniform-problem-detail-errors.md` (ADR зі статусом Proposed → сторінка `in-review`):

```yaml
---
owner: VladdHoryn
status: in-review
review_date: 2027-02-20
tags:
  - architecture
  - adr
  - api
---
```

`docs/sdd.md` (стара адреса, переведена в deprecated замість видалення):

```yaml
---
owner: VladdHoryn
status: deprecated
review_date: 2027-05-21
superseded_by: architecture/sdd.md
tags:
  - archive
---
```

Як заповнено `review_date` (з урахуванням аудиту актуальності ЛР№10): у ЛР№10 поріг «підозрілий» — понад 90 днів
без оновлення, «застарілий» — понад 180. Тому експлуатаційні та розробницькі документи мають цикл 90 днів,
архітектурні — 180. Документи, переглянуті в ЛР№10 2026-11-14, зберегли дати регламенту ЛР№10:
2026-11-14 + 90 = **2027-02-12**, 2026-11-14 + 180 = **2027-05-13**. Нові документи ЛР№11 від 2026-11-22:
2026-11-22 + 90 = **2027-02-20**, 2026-11-22 + 180 = **2027-05-21**.

Паспорт не дублюється: з SDD, ISD, матриці аудиторій і handover прибрано рядки «Власник» і «Останній
перегляд» таблиць ЛР№10, а таблицю власників із `maintenance.md` замінено правилом «паспорт — у front matter»;
зведену таблицю друкує `./scripts/doc-review-audit.sh`. Дату останньої зміни сторінки сайт бере з Git (плагін
`git-revision-date-localized`), а не з тексту.

### 2.2. CODEOWNERS

```text
# Product documentation: the knowledge base, its configuration and the contract of the public API
/docs/                          @VladdHoryn
/README.md                      @VladdHoryn
/CHANGELOG.md                   @VladdHoryn
/mkdocs.yml                     @VladdHoryn
/overrides/                     @VladdHoryn
/docs/api/openapi.yaml          @VladdHoryn
/.spectral.yaml                 @VladdHoryn
/.markdownlint-cli2.yaml        @VladdHoryn

# Accepted architecture decisions are immutable: only the architect may add or supersede them
/docs/architecture/adr/         @VladdHoryn

# The documentation pipeline itself
/.github/workflows/docs.yml     @VladdHoryn
/.github/CODEOWNERS             @VladdHoryn
```

Щоб «зміна документації без рев'ю власника фізично не зливалась», у налаштуваннях репозиторію для `master`
потрібне правило захисту гілки з «Require a pull request before merging» → «Require review from Code Owners».
Обмеження: у репозиторії з одним учасником власник не може схвалити власний PR, тому для повної демонстрації
потрібен другий учасник.

> 📸 SCREENSHOT: Settings → Branches (або Rules) → правило для `master` з увімкненим «Require review from Code
> Owners».

### 2.3. Кущ ADR

| № | Рішення | Статус запису | `status` сторінки | Зв'язки в кущі |
|---|---|---|---|---|
| 0001 | Окрема БД PostgreSQL на сервіс, схема лише через Flyway | Accepted | published | — |
| 0002 | Сага замовлення як хореографія подій Kafka | Accepted (доповнено 0003) | published | залежить від 0001 |
| 0003 | Синхронне читання платежу для трекінгу | Accepted | published | доповнює 0002 |
| 0004 | Документація як код: MkDocs Material, strict, GitHub Pages | Proposed (умова прийняття — перша публікація сайту, розділ 4.5) | in-review | пов'язаний з 0005 |
| 0005 | Контракт клієнтського API пишеться першим (OpenAPI) і є SSOT | Accepted | published | посилається на 0003, 0004 |
| 0006 | Єдиний формат помилок RFC 9457 | Proposed | in-review | знайдено під час 0005 |

Канон Лекції 9 у проєкті:

- **наскрізна нумерація** `NNNN-назва.md`, номери не перевикористовуються;
- **статуси** Proposed → Accepted → Superseded/Deprecated описано в `adr/README.md` разом із відповідністю статусу
  сторінки (`in-review` / `published` / `deprecated`);
- **незмінність:** нові рішення ЛР№11 — нові записи 0004–0006, а не правки 0001–0003. У 0001–0003 змінено лише
  шлях у рядку «Пов'язані документи» (`../../sdd.md` → `../sdd.md`, бо SDD переміщено), у 0002 вставлено порожній
  рядок перед списком (вимога markdownlint, рендер не змінився), і додано front matter (метадані сторінки). Текст
  рішень, статуси й дати не змінено — це видно в `git show 65656ac -- docs/architecture/adr/`.
- **Superseded:** у системі поки немає рішення, яке замінило б інше; штучно «замінювати» ADR лише заради
  статусу я не став. Перехід статусу показано на ADR-0004 (Proposed → Accepted окремим комітом).

### 2.4. Граф знань Obsidian

Репозиторій відкривається як vault: `.obsidian/app.json` (`useMarkdownLinks: true`, відносні посилання, ігнор
`site/`, `tools/`, звітів), `.obsidian/graph.json` (фільтр на документи продукту, показ тегів, кольорові групи
за тегами `#adr`, `#architecture`, `#api`, `#ops`, `#developer`, `#archive`).

Посилання між документами — звичайні відносні Markdown-посилання, а не `[[wikilinks]]`. Обґрунтування: Obsidian
будує граф з обох однаково, але `mkdocs build --strict` перевіряє лише Markdown-посилання; мертвий wikilink
обійшов би правило «не збирається — не їде», а Notion wikilinks не розуміє.

Зв'язність перевірено не лише скріншотом, а й скриптом `scripts/doc-link-graph.sh` (ті самі ребра: відносні
посилання між документами + include):

| Запуск | Документів | Ребер | Островів | Файл |
|---|---|---|---|---|
| перший | 29 | 64 | **2**: `docs/architecture/audience-coverage.md` (0 зв'язків), `docs/tags.md` (0) | `docs/lab-11/knowledge-graph-islands-before.txt` |
| після виправлення | 29 | 72 | **0** — `RESULT: PASS - the knowledge base is connected` | `docs/lab-11/knowledge-graph.md` (з Mermaid-графом) |

Виправлення: у паспорт матриці аудиторій додано рядок «Пов'язані документи» з посиланнями на README, SDD, ISD,
runbook, API, local-setup і handover (документ перелічував їх лише як текст); сторінка тегів посилається на
регламент супроводу.

> 📸 SCREENSHOT: Obsidian → Graph view на vault репозиторію: зв'язний граф без ізольованих вузлів, кущ ADR
> (помаранчеві вузли `#adr`) пов'язаний із SDD, ISD і документами розробника, видно вузли тегів.

---

## 3. API-контракт: OpenAPI, Swagger UI, Spectral, SDK, дрейф

### 3.1. Що описує контракт

`docs/api/openapi.yaml` — OpenAPI **3.0.3**, `info.version: 1.0.0` (SemVer), 741 рядок. Шість операцій
клієнтського шляху — усі реальні маршрути коду (перевірка 2 `doc-drift-check.sh`: «contract operations: 6,
missing in code: 0», `docs/lab-11/drift-check.txt`), включно з ендпоінтом краш-тесту ЛР№10:

| operationId | Маршрут | Контролер | Відповіді |
|---|---|---|---|
| `searchPlants` | `GET /api/catalog/plants` | `PlantController.search` | 200, 400, 503 |
| `getPlant` | `GET /api/catalog/plants/{id}` | `PlantController.findById` | 200, 400, 404, 503 |
| `createOrder` | `POST /api/orders` | `OrderController.create` | 202, 400, 409, 503 |
| `listCustomerOrders` | `GET /api/orders?email=` | `OrderController.findByCustomer` | 200, 400, 503 |
| `getOrder` | `GET /api/orders/{id}` | `OrderController.findById` | 200, 400, 404, 503 |
| `trackOrder` | `GET /api/orders/{id}/tracking` | `OrderTrackingController.track` (ЛР№10, ADR-0003) | 200, 400, 404, 503 |

Схеми `components.schemas` (13): `Error`, `CareLevel`, `LightRequirement`, `Category`, `Plant`, `PageMetadata`,
`PlantPage`, `PaymentMethod`, `OrderStatus`, `OrderItemRequest`, `CreateOrderRequest`, `OrderItem`, `Order`,
`OrderTracking` — з `required`, `enum` (`CareLevel`, `LightRequirement`, `PaymentMethod`, `OrderStatus`,
`paymentStatus`) і `example` у кожній. Спільні відповіді `components.responses`: `BadRequest`, `NotFound`,
`OutOfStock`, `ServiceUnavailable` — усі зі схемою `Error`.

Звідки взято кожен факт контракту: поля — з record-класів DTO (`PlantResponse`, `OrderResponse`,
`OrderTrackingResponse`, …), обмеження — з анотацій валідації (`@Email`, `@Size(max = 500)`, `@Positive`,
`@NotEmpty`), коди — з `@ResponseStatus` і `GlobalExceptionHandler`, тексти помилок у прикладах — з конструкторів
винятків (`"Order with id 42 not found"`, `"Not enough stock for plants [8]"`), приклади каталогу — з
`V2__seed_catalog.sql`, формат сторінки — з `@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)`.

### 3.2. Лістинг openapi.yaml (скорочено: одна операція повністю, спільні відповіді, схема помилки)

```yaml
openapi: 3.0.3
info:
  title: Plant Shop Customer API
  version: 1.0.0
  # description, contact, license — у файлі
servers:
  - url: http://localhost:8080
    description: API gateway of the local Docker Compose stack
paths:
  /api/orders/{id}/tracking:
    get:
      tags: [Orders]
      operationId: trackOrder
      summary: Track an order
      description: |
        "Where is my order" in one request (added in Lab 10, ADR-0003): the order status from order-service and
        the payment status read synchronously from payment-service. `paymentStatus` is `NOT_STARTED` until the
        saga reaches the payment step. If payment-service is down the answer is 503 while the order itself stays
        available through `GET /api/orders/{id}`.
      parameters:
        - $ref: "#/components/parameters/OrderId"
      responses:
        "200":
          description: Order and payment status.
          content:
            application/json:
              schema:
                $ref: "#/components/schemas/OrderTracking"
              examples:
                confirmed:
                  summary: Paid order
                  value:
                    orderId: 1
                    orderStatus: CONFIRMED
                    paymentStatus: COMPLETED
                    transactionId: 3f6b2c1e-8a4d-4c1b-9e7a-2d5f0b6c9a41
                    failureReason: null
                    nextStep: Paid, being prepared for shipment
                cancelled:
                  summary: Payment declined above the limit
                  value:
                    orderId: 2
                    orderStatus: CANCELLED
                    paymentStatus: FAILED
                    transactionId: null
                    failureReason: Amount exceeds the limit of 10000
                    nextStep: Cancelled, nothing will be charged or shipped
        "400":
          $ref: "#/components/responses/BadRequest"
        "404":
          $ref: "#/components/responses/NotFound"
        "503":
          $ref: "#/components/responses/ServiceUnavailable"
  # /api/catalog/plants, /api/catalog/plants/{id}, /api/orders (POST, GET), /api/orders/{id} — у файлі

components:
  responses:
    NotFound:
      description: No resource with this id.
      content:
        application/problem+json:
          schema:
            $ref: "#/components/schemas/Error"
          example:
            type: about:blank
            title: Not Found
            status: 404
            detail: Order with id 42 not found
            instance: /api/orders/42
    OutOfStock:
      description: Not enough stock for the listed plants at the moment of the check.
      content:
        application/problem+json:
          schema:
            $ref: "#/components/schemas/Error"
          example:
            type: about:blank
            title: Conflict
            status: 409
            detail: "Not enough stock for plants [8]"
            instance: /api/orders
            plantIds: [8]
    # BadRequest і ServiceUnavailable мають два media type: application/problem+json (сервіс)
    # і application/json (фреймворк / gateway) — обидва зі схемою Error

  schemas:
    Error:
      type: object
      description: |
        The one error object of every route. Service errors fill the RFC 9457 fields; framework errors fill
        `timestamp`, `error` and `path`. Clients should rely on `status` and show `detail` when present.
      required: [status]
      properties:
        type: {type: string, example: about:blank}
        title: {type: string, example: Not Found}
        status: {type: integer, format: int32, minimum: 400, maximum: 599, example: 404}
        detail: {type: string, example: Order with id 42 not found}
        instance: {type: string, example: /api/orders/42}
        errors: {type: object, additionalProperties: {type: string}}
        plantIds: {type: array, items: {type: integer, format: int64}}
        timestamp: {type: string}
        error: {type: string}
        path: {type: string}
        requestId: {type: string}
      # у файлі кожна властивість має description і example

    OrderTracking:
      type: object
      required: [orderId, orderStatus, paymentStatus, nextStep]
      properties:
        orderId: {type: integer, format: int64, example: 1}
        orderStatus: {$ref: "#/components/schemas/OrderStatus"}
        paymentStatus:
          type: string
          enum: [NOT_STARTED, COMPLETED, FAILED]
          example: COMPLETED
        transactionId: {type: string, nullable: true}
        failureReason: {type: string, nullable: true}
        nextStep: {type: string, example: Paid, being prepared for shipment}
      example: {orderId: 1, orderStatus: CONFIRMED, paymentStatus: COMPLETED, nextStep: "Paid, …"}
```

**Узгоджена схема помилок — і чесність щодо неї.** Під час опису помилок виявилось, що сервіси повертають їх у
двох форматах: `GlobalExceptionHandler` — RFC 9457 Problem Details (`application/problem+json`), а помилки, які
формує сам Spring або gateway, — `{timestamp, status, error, path}` (`application/json`). Підтверджено на живому
API: `GET /api/orders/abc` → `400`, `Content-Type: application/json`,
`{"timestamp":…,"status":400,"error":"Bad Request","path":"/api/orders/abc"}`. Тому `Error` — один спільний об'єкт
для всіх маршрутів з обов'язковим `status` і двома наборами необов'язкових полів, а повну уніфікацію винесено в
ADR-0006 (Proposed): просте `spring.mvc.problemdetails.enabled: true` змінило б поведінку валідації (вбудований
обробник з `@Order(0)` перехопив би `MethodArgumentNotValidException` і зникло б поле `errors`).

### 3.3. Swagger UI і «Try it out»

Сторінка `docs/api/reference.md` містить лише тег `<swagger-ui src="openapi.yaml"/>` (плагін
`mkdocs-swagger-ui-tag`). Валідацію контракту за схемою OAS 3.0 автоматично виконує Spectral (базовий набір
`spectral:oas`, 0 errors, розділ 3.4); для ручної перевірки в Swagger Editor:
`docker run --rm -p 8088:8080 swaggerapi/swagger-editor` (http://localhost:8088) → File → Import file → `docs/api/openapi.yaml`.

> 📸 SCREENSHOT: Swagger Editor з імпортованим `docs/api/openapi.yaml`: ліворуч контракт, праворуч
> «Plant Shop Customer API 1.0.0», без панелі помилок валідації.

«Try it out» виконано на сторінці сайту проти запущеного стеку (`docker compose up -d --build`): операція
`trackOrder`, `id = 1` → запит `http://localhost:8080/api/orders/1/tracking`, відповідь **200**:

```json
{
  "orderId": 1,
  "orderStatus": "CONFIRMED",
  "paymentStatus": "COMPLETED",
  "transactionId": "fa1f39bd-b06c-4f6a-a430-78ef04a49e84",
  "failureReason": null,
  "nextStep": "Paid, being prepared for shipment"
}
```

Запит зі сторінки (`localhost:8000` або `vladdhoryn.github.io`) на `localhost:8080` дозволяє CORS-конфігурація
gateway (`globalcors`, `allowed-origins: "*"`), змін коду не знадобилось.

> 📸 SCREENSHOT: сторінка «3 · API → Довідник API» сайту, розгорнута операція `GET /api/orders/{id}/tracking`
> після «Try it out» → Execute: видно Request URL, Code 200 і тіло відповіді.

> 📸 SCREENSHOT: розділ API зібраного сайту — бокова навігація рівня 3 і заголовок «Plant Shop Customer API 1.0.0
> OAS 3.0».

### 3.4. Лінтинг Spectral

Правила `.spectral.yaml` (усі — `error`, поріг 0 errors):

| Правило | Вимога завдання |
|---|---|
| `extends: spectral:oas` (recommended) | валідність OAS, operationId, теги, приклади за схемою |
| `operation-description: error` | кожна операція має непорожній `description` |
| `plantshop-schema-example` | кожна схема — хоча б один `example` |
| `plantshop-error-response-shared` + `plantshop-error-schema` | коди помилок описано узгоджено: кожна 4xx/5xx — `$ref` на спільну відповідь, а та — на `Error` |
| `plantshop-operation-error-codes` | кожна операція має хоча б одну 4xx і одну 5xx |

Результат (`docs/lab-11/spectral-lint.txt`):

```text
$ docker run --rm -v "$PWD:/work" -w /work stoplight/spectral:6.15.0 lint docs/api/openapi.yaml --ruleset .spectral.yaml --fail-severity error -D
No results with a severity of 'error' found!
exit code: 0
```

Повторний запуск без `-D` (показ усіх рівнів) теж не виводить жодного результату, тобто **0 errors і 0
warnings** — обґрунтовувати попередження не потрібно.

Доказ, що правила справді ловлять дефекти, — самоперевірка на копії контракту з трьома внесеними дефектами
(`docs/lab-11/spectral-ruleset-selftest.txt`):

```text
 146:15  error  plantshop-error-response-shared  #/paths/~1api~1catalog~1plants~1%7Bid%7D/get/responses/404 must be a $ref to #/components/responses/...
 222:20  error  operation-description            Operation "description" must be present and non-empty string.
 588:22  error  plantshop-schema-example         Schema OrderItemRequest.example has no example.
✖ 3 problems (3 errors, 0 warnings, 0 infos, 0 hints)
exit code: 1
```

### 3.5. Генерація клієнтського SDK

```bash
./scripts/api-sdk.sh
# = docker run openapitools/openapi-generator-cli:v7.14.0 generate -i docs/api/openapi.yaml -g java -o sdk-client
#   --additional-properties=library=native,groupId=com.plantshop,artifactId=plantshop-api-client,...
```

Результат (`docs/lab-11/sdk-generation.txt`): генерація — exit code 0; згенеровано класи API `CatalogApi`,
`OrdersApi` і 14 моделей (`Order`, `OrderTracking`, `Error`, …); SDK зібрано `./mvnw -f sdk-client/pom.xml
package` → `plantshop-api-client-1.0.0.jar` (exit code 0). Публічні методи SDK збігаються з `operationId`
контракту:

```text
public Plant getPlant(Long id)
public PlantPage searchPlants(String name, Long categoryId, CareLevel careLevel, BigDecimal maxPrice, Integer page, Integer size)
public Order createOrder(CreateOrderRequest createOrderRequest)
public Order getOrder(Long id)
public List<Order> listCustomerOrders(String email)
public OrderTracking trackOrder(Long id)
```

`sdk-client/` не комітиться (`.gitignore`): це похідний артефакт контракту, друга копія порушила б SSOT.

### 3.6. Single Source of Truth: чому довідка не може розійтися з контрактом

| Представлення | Як отримано | Копія контракту? |
|---|---|---|
| сторінка «Довідник API» | Swagger UI читає `site/api/openapi.yaml` — той самий файл, скопійований збіркою | ні |
| SDK | генерується з `openapi.yaml` | ні |
| contract-тест | Schemathesis читає `openapi.yaml` | ні |
| SDD 5 | лише посилається на контракт і довідку | ні |

У `reference.md` немає жодного рядка про маршрути, версію чи формат відповідей — отже змінити їх можна лише в
`openapi.yaml`, і наступна збірка покаже зміну на сайті.

### 3.7. Контроль контрактного дрейфу: Schemathesis проти живого API

Стек запущено `docker compose up -d --build`, створено одне замовлення, потім
`./scripts/api-contract-test.sh` (Schemathesis 4.1.0, `--max-examples 50 --seed 11`, перевірки
`not_a_server_error`, `status_code_conformance`, `content_type_conformance`, `response_schema_conformance`).

**Запуск 1** (`docs/lab-11/contract-test-run1.txt`): 470 тест-кейсів, **4 збої у 2 операціях — дефекти, знайдені
contract-тестом**:

| Операція | Запит | Задокументовано | Фактично | Причина |
|---|---|---|---|---|
| `createOrder` | `{"…", "items": [null]}` | 202 / 400 / 409 / 503 | **500** `Internal Server Error` | `@NotEmpty List<@Valid OrderItemRequest>` пропускає `null`-елемент → `NullPointerException` в `OrderService.create` |
| `searchPlants` | `page=339360973&size=197` | 200 / 400 / 503 | **500** | `InvalidDataAccessApiUsageException: Page offset exceeds Integer.MAX_VALUE` |

За підходом API-first джерелом істини є контракт, тому виправлено **код** (коміт `96cfd32`):
`List<@NotNull @Valid OrderItemRequest>` у `CreateOrderRequest` → `400 {"errors":{"items[0]":"must not be null"}}`;
`PlantController.search` відхиляє `page × size > Integer.MAX_VALUE` з 400. Регресійні тести:
`CreateOrderRequestValidationTest`, `PlantControllerTest` (усі тести catalog- і order-service зелені, інтеграційні
order/user-service — теж). Документацію оновлено в тому самому коміті: CHANGELOG → Fixed, опис `searchPlants`.

**Запуск 2** після виправлення (`docs/lab-11/contract-test-run2.txt`):

```text
Test Phases:
  ✅ Examples
  ✅ Coverage
  ✅ Fuzzing
Test cases:
  557 generated, 557 passed
========================== No issues found in 32.79s ===========================
exit code: 0
```

Попередження Schemathesis (не збої): «3 operations repeatedly returned 404» — випадкові `id` не існують, тож
`404` (задокументований) — очікувана відповідь; фаза Examples використовує `id = 1` з контракту, який існує, і
перевіряє 200-відповіді проти схем. «Schema validation mismatch» — більшість згенерованих негативних даних
API відхиляє з 400, що й задокументовано.

**Дрейф у зворотному напрямку** (поле є в коді, але не в контракті — Schemathesis цього не бачить, бо схеми
дозволяють додаткові поля): `scripts/openapi_code_first_diff.py` порівнює схеми контракту з code-first
специфікацією springdoc `/v3/api-docs` catalog- і order-service (`docs/lab-11/contract-vs-springdoc.txt`):
9 пар схем, усі поля збігаються, `RESULT: PASS - 0 schema(s) differ`.

### 3.8. Варіативний пункт (API-проєкт): Javadoc

`./scripts/docs-javadoc.sh` → `./mvnw javadoc:aggregate` генерує довідник з Javadoc-коментарів усіх модулів
(пакети `com.plantshop.catalog`, `config`, `discovery`, `gateway`, `inventory`, `logging`, `order`, `payment`,
`user`); у CI він копіюється в `site/javadoc/` і публікується окремим розділом бази знань «3 · API → Javadoc».

> 📸 SCREENSHOT: опублікований Javadoc `https://vladdhoryn.github.io/Plant_Shop/javadoc/` зі списком пакетів.

---

## 4. CI/CD-конвеєр і краш-тест FAIL → PASS

### 4.1. Лістинг .github/workflows/docs.yml

```yaml
name: docs

on:
  push:
  pull_request:
  workflow_dispatch:

permissions:
  contents: read                     # мінімальні права за замовчуванням

concurrency:
  group: docs-${{ github.ref }}
  cancel-in-progress: true

jobs:
  build:
    name: lint and strict build
    runs-on: ubuntu-24.04
    timeout-minutes: 15
    steps:
      # 1. Checkout з повною історією: дати сторінок з git log
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
      # 2. Python для MkDocs
      - uses: actions/setup-python@v5
        with:
          python-version: "3.12"
          cache: pip
          cache-dependency-path: tools/mkdocs/requirements.txt
      # 3. mkdocs-material і плагіни — ті самі закріплені версії, що в локальному Docker-образі
      - name: Install MkDocs Material and plugins
        run: pip install -r tools/mkdocs/requirements.txt
      # 4. Стиль Markdown
      - name: markdownlint
        run: docker run --rm -v "$PWD:/workdir" davidanson/markdownlint-cli2:v0.18.1
      # 5. Якість контракту API: 0 errors або job падає
      - name: Spectral lint of docs/api/openapi.yaml
        run: >
          docker run --rm -v "$PWD:/work" -w /work stoplight/spectral:6.15.0
          lint docs/api/openapi.yaml --ruleset .spectral.yaml --fail-severity error
      # 6. Безперервний аудит актуальності: прострочені review_date у Step Summary (звіт)
      - name: review_date audit
        run: ./scripts/doc-review-audit.sh | tee -a "$GITHUB_STEP_SUMMARY"
      # 7. Strict-збірка: будь-яке попередження = exit code 1, конвеєр зупиняється тут
      - name: mkdocs build --strict
        run: mkdocs build --strict
      # 8. Варіативний пункт: Javadoc поруч із сайтом
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "21"
          cache: maven
      - name: Javadoc
        run: |
          ./scripts/docs-javadoc.sh
          cp -r target/reports/apidocs site/javadoc
      - uses: actions/upload-pages-artifact@v3
        with:
          path: site

  # 9. Публікація: лише зелений build push-у в master або lab/Lab_11
  deploy:
    needs: build
    if: github.event_name == 'push' && (github.ref == 'refs/heads/master' || github.ref == 'refs/heads/lab/Lab_11')
    runs-on: ubuntu-24.04
    timeout-minutes: 10
    permissions:
      pages: write                   # права на публікацію — лише цьому job
      id-token: write
    environment:
      name: github-pages
      url: ${{ steps.deployment.outputs.page_url }}
    steps:
      - id: deployment
        uses: actions/deploy-pages@v4
```

Порядок стадій відповідає завданню: checkout → Python → mkdocs-material → markdownlint → strict-збірка →
деплой; Spectral і аудит `review_date` додано між лінтером і збіркою, Javadoc — після збірки. Конвеєр також
описано в ISD, розділ 6 (код змінився — документацію оновлено в тому самому PR).

### 4.2. Перший запуск

Перший push гілки (`c9dad21`, три коміти бази знань і конвеєра) — запуск
[45027899372](https://github.com/VladdHoryn/Plant_Shop/actions/runs/45027899372): job «lint and strict build» —
**success** на всіх кроках (markdownlint, Spectral, review_date audit, `mkdocs build --strict`, Javadoc,
upload-pages-artifact). Job `deploy` — failure з причиною, що не стосується документації:
`Failed to create deployment (status: 404) … Ensure GitHub Pages has been enabled` — у репозиторії ще не було
ввімкнено GitHub Pages (Settings → Pages → Source: GitHub Actions). Після ввімкнення наступний зелений запуск
публікує сайт. Паралельні workflow на тому самому коміті: `docs-currency` (drift-check) — success,
`code-metrics` — success.

### 4.3. Краш-тест: FAIL

Дефект — **мертве посилання** на неіснуючу сторінку. У `docs/developer/CONTRIBUTING.md`, розділ робочого процесу,
додано посилання на «чек-лист релізу» `../architecture/release-checklist.md`, якого не існує. markdownlint такий
дефект пропускає (синтаксис Markdown коректний), тому падіння доводить саме дію strict-збірки.

Коміт: `docs(ci): inject broken reference into strict build, FAIL` (`e92558f`).

Локальне відтворення (`docs/lab-11/strict-build-fail.txt`):

```text
$ ./scripts/docs-lint.sh   # markdownlint + Spectral: the defect is valid Markdown
== 1. markdownlint
Summary: 0 error(s)
== 2. Spectral (docs/api/openapi.yaml)
No results with a severity of 'error' found!
docs-lint: PASS

$ ./scripts/docs-build.sh  # mkdocs build --strict
WARNING -  Doc file 'developer/CONTRIBUTING.md' contains a link '../architecture/release-checklist.md', but the target 'architecture/release-checklist.md' is not found among documentation files.
Aborted with 1 warnings in strict mode!
docs-build: FAIL, exit code 1
```

У GitHub Actions — запуск [45029784217](https://github.com/VladdHoryn/Plant_Shop/actions/runs/45029784217)
(`docs/lab-11/ci-fail-run.txt`): job «lint and strict build» — **failure**; кроки markdownlint, Spectral,
review_date audit — success (дефект синтаксично коректний), крок **`mkdocs build --strict` — failure**,
наступні кроки (Javadoc, upload-pages-artifact) — skipped; job **`deploy` — skipped**, тобто дефектна версія не
опублікована. Анотація job-а: `Process completed with exit code 1.` Паралельні `docs-currency` і `code-metrics` на
цьому коміті — success (вони не перевіряють посилання), що показує: саме strict-збірка, а не інші перевірки,
зупинила доставку.

> 📸 SCREENSHOT: GitHub → Actions → workflow `docs`, запуск на коміті FAIL: червоний job «lint and strict
> build», розгорнутий крок `mkdocs build --strict` з рядком `WARNING - Doc file 'developer/CONTRIBUTING.md'
> contains a link '../architecture/release-checklist.md'…`, `Aborted with 1 warnings in strict mode!` і
> `Error: Process completed with exit code 1.`; job `deploy` — skipped.

### 4.4. Відновлення: PASS

Посилання на неіснуючу сторінку видалено. Коміт: `docs(ci): restore strict build pipeline, PASS` (`e1c855c`).
Локально (`docs/lab-11/strict-build-pass.txt`): markdownlint 0 errors, `docs-build: PASS (strict)`, exit code 0.

У GitHub Actions — запуск [45029969461](https://github.com/VladdHoryn/Plant_Shop/actions/runs/45029969461)
(`docs/lab-11/ci-pass-run.txt`): job «lint and strict build» — **success** на всіх кроках, включно з
`mkdocs build --strict`, Javadoc і upload-pages-artifact. Конвеєр документації відновлено.

Job `deploy` на цьому запуску — failure з тієї ж причини, що й у першому запуску:
`Failed to create deployment (status: 404) … Ensure GitHub Pages has been enabled`; на момент запуску
`GET /repos/VladdHoryn/Plant_Shop/pages` повертав 404, тобто GitHub Pages у репозиторії ще не ввімкнено
(налаштування репозиторію, а не дефект документації чи workflow). **Стан на момент написання звіту: публікацію
не виконано.** Щоб завершити: Settings → Pages → Source: «GitHub Actions», потім на сторінці запуску
45029969461 «Re-run failed jobs» — перезапуститься лише `deploy` з уже зібраним артефактом PASS-коміту.

> 📸 SCREENSHOT: GitHub → Actions → workflow `docs`, запуск на коміті PASS: зелені jobs «lint and strict build»
> і «deploy» з URL `https://vladdhoryn.github.io/Plant_Shop/`.

> 📸 SCREENSHOT: задеплоєний сайт `https://vladdhoryn.github.io/Plant_Shop/developer/CONTRIBUTING/`: адреса в
> рядку браузера і дата «Останнє оновлення» внизу сторінки (плагін git-revision-date-localized).

### 4.5. Прийняття ADR-0004

ADR-0004 має умову прийняття «перший зелений конвеєр `docs.yml` з публікацією». Збірка вже зелена, але
публікації ще не було (розділ 4.4), тому ADR-0004 **лишається Proposed** — приймати рішення раніше за виконання
його умови було б нечесно. Після першого успішного `deploy` змінюється лише рядок статусу (Proposed → Accepted)
і `status` сторінки (`in-review` → `published`) окремим комітом; текст рішення не змінюється.

---

## 5. Регламент безперервного супроводу

### 5.1. Чек-лист docs-or-don't-merge (Definition of Done у CONTRIBUTING.md)

Опубліковано в `docs/developer/CONTRIBUTING.md` → «Definition of Done: docs-or-don't-merge»; той самий чек-лист —
у `.github/pull_request_template.md`. PR, що змінює публічну поведінку (ендпоінт, параметр, поле, формат
відповіді, подію Kafka, конфігурацію, змінні, розгортання, архітектурне рішення), не зливається без:

- оновлення `docs/` за таблицею «Що оновлювати при зміні коду» регламенту супроводу;
- оновлення `docs/api/openapi.yaml` і `info.version` за SemVer (для клієнтського шляху) і Spectral 0 errors;
- запису в `CHANGELOG.md` → `Unreleased`;
- нового ADR, якщо змінилось рішення (старий — `Superseded`);
- актуального front matter змінених сторінок; застаріла сторінка — `deprecated`, не видаляється;
- зелених `doc-drift-check.sh` і strict-збірки.

Таблиця документаційних перевірок Definition of Done у README:

| What is checked | Tool | Where it runs | Fails when |
|---|---|---|---|
| API contract | Spectral (`.spectral.yaml`) | CI `docs / lint`, `./scripts/docs-lint.sh` | an operation has no description, a schema has no example, an error response is not `Error` |
| Markup | markdownlint (`.markdownlint-cli2.yaml`) | CI, `./scripts/docs-lint.sh` | Markdown style rules are broken |
| Links and navigation | `mkdocs build --strict` | CI, `./scripts/docs-build.sh` | a dead link or anchor, a page outside `nav`, a broken page |
| Currency | `review_date` in front matter | CI → `./scripts/doc-review-audit.sh` | report: overdue pages are listed in the job summary |
| Code ↔ documentation | `./scripts/doc-drift-check.sh` | CI `docs-currency / doc-drift` | an endpoint is not in the SDD, an OpenAPI operation does not exist in code, the startup order differs from the ISD |
| Ownership | `.github/CODEOWNERS` | GitHub review | a change in `docs/` has no approval of the document owner |

Перший прохід markdownlint по документах продукту знайшов 6 порушень (4 задовгі рядки, блок коду без мови,
список без порожнього рядка) — виправлено до коміту; фінальний результат — `Linting: 29 file(s)`,
`Summary: 0 error(s)` (`docs/lab-11/markdownlint.txt`).

### 5.2. Автоматизований аудит review_date

`scripts/doc-review-audit.sh` читає front matter кожного документа продукту і порівнює `review_date` з датою
запуску; у CI таблиця потрапляє в Step Summary кожного запуску — аудит актуальності став безперервним замість
разового `doc-currency.sh` ЛР№10.

| Дата аудиту | Документів | OVERDUE | Без паспорта | Deprecated | Файл |
|---|---|---|---|---|---|
| 2026-11-22 (сьогодні) | 29 | 0 | 0 | 4 | `docs/lab-11/review-audit-today.txt` |
| 2027-03-01 (прогноз через 99 днів) | 29 | **16** | 0 | 4 | `docs/lab-11/review-audit-2027-03-01.txt` |

Перелік прострочених на 2027-03-01 — усі документи з циклом 90 днів: README, CHANGELOG, ISD, runbook
розгортання, 4 сторінки API, 6 сторінок розробника, головна сторінка, ADR-0006 (`review_date` 2027-02-12 або
2027-02-20 < 2027-03-01). Архітектурні документи (цикл 180 днів, 2027-05-13 / 2027-05-21) ще в межах циклу.
`STRICT=1` перетворює звіт на блокування (exit code 1); у CI свідомо лишено режим звіту, щоб прострочена дата
перегляду не блокувала термінове виправлення коду.

> 📸 SCREENSHOT: GitHub → Actions → запуск `docs` → Summary: таблиця «Documentation review audit» з колонками
> Document / Owner / Status / review_date / Days left / Signal.

### 5.3. Деприкація застарілих сторінок

Застарілі сторінки не видаляються. Чотири старі адреси паспорта ЛР№10 (на них посилаються звіти лабораторних,
які не можна редагувати) стали сторінками `status: deprecated` з `superseded_by`, банером `DEPRECATED` і
посиланням на актуальний документ; вони в розділі навігації «Архів (deprecated)» (коміт `8ecd82d`):

| Стара адреса | `superseded_by` |
|---|---|
| `docs/sdd.md` | `architecture/sdd.md` |
| `docs/isd.md` | `architecture/isd.md` |
| `docs/maintenance.md` | `developer/maintenance.md` |
| `docs/handover.md` | `developer/handover.md` |

Переміщення закомічено окремо від заглушок (`65656ac`, потім `8ecd82d`), тому Git розпізнав перейменування і
`git log --follow docs/architecture/sdd.md` показує історію з ЛР№10 (`c24ee0f`, `7e82c94`); `doc-currency.sh` тепер
читає історію документів з `--follow`.

### 5.4. Теги версій документації

Мажорні версії документації зафіксовано анотованими тегами (`git tag -n1 -l 'docs-v*'`):

| Тег | Коміт | Що це за знімок |
|---|---|---|
| `docs-v1.0` | `1715a1b` | архітектурний паспорт ЛР№10: SDD, ISD, ADR-0001…0003, регламент, handover — до бази знань MkDocs |
| `docs-v2.0` | `e1c855c` (PASS) | база знань ЛР№11: піраміда, front matter, ADR-0004…0006, контракт API 1.0.0, конвеєр зі strict-збіркою; мажорна, бо змінились адреси документів |

Відновлення знімка, що відповідав релізу: `git checkout docs-v1.0` (і `./scripts/docs-build.sh` для версій, де
є `mkdocs.yml`). Теги поки лише локальні: їх публікує окремий `git push origin docs-v1.0 docs-v2.0`.

---

## 6. Посилання на опублікований сайт

**<https://vladdhoryn.github.io/Plant_Shop/>** — адреса, на яку job `deploy` публікує сайт після кожного зеленого push-у в
`lab/Lab_11` або `master`.

| Розділ | Адреса |
|---|---|
| Рівень 1 (README) | <https://vladdhoryn.github.io/Plant_Shop/> |
| Рівень 2 | <https://vladdhoryn.github.io/Plant_Shop/developer/CONTRIBUTING/> |
| Рівень 3, Swagger UI | <https://vladdhoryn.github.io/Plant_Shop/api/reference/> |
| Javadoc | <https://vladdhoryn.github.io/Plant_Shop/javadoc/> |
| Рівень 4, ADR | <https://vladdhoryn.github.io/Plant_Shop/architecture/adr/> |

---

## 7. Лог валідації ШІ-Техліда

TODO: заповнити за інструкцією викладача (протокол проходження краш-тесту).

---

## 8. Висновки

1. Документацію Plant Shop розкладено за чотирирівневою пірамідою без дублювання фактів: README скорочено з 512 до
   69 рядків, кожен колишній розділ перенесено на свій рівень або видалено як дублікат SDD. База знань MkDocs
   Material збирається локально і в CI в strict-режимі; 29 сторінок продукту мають front matter-паспорт, граф
   знань зв'язний (72 ребра, 0 островів — після виправлення двох знайдених), ADR ведуться за каноном
   (6 записів, статуси, незмінність).
2. Контракт клієнтського API `openapi.yaml` 1.0.0 описує 6 реальних операцій, проходить Spectral з 0 errors і
   0 warnings, з нього генерується SDK і рендериться довідка (SSOT). Contract-тест Schemathesis знайшов дві
   розбіжності коду з контрактом (500 замість 400); за API-first виправлено код, після чого 557 з 557 тест-кейсів
   пройшли, а схеми контракту збігаються з code-first springdoc по всіх полях. Знайдено і задокументовано
   неоднорідність формату помилок (ADR-0006).
3. Конвеєр `docs.yml` реалізує правило «не збирається — не їде»: краш-тест з мертвим посиланням дав Exit Code 1 і
   зупинив публікацію; виправлення повернуло зелену strict-збірку (публікація чекає ввімкнення GitHub Pages). Супровід автоматизовано:
   docs-or-don't-merge у CONTRIBUTING і шаблоні PR, таблиця DoD у README, безперервний аудит `review_date` у CI,
   деприкація замість видалення, теги версій документації.

---

## 9. Перелік скріншотів

1. Головна сторінка сайту з вкладками піраміди і плашкою «Паспорт документа» (розділ 1.4).
2. Правило захисту `master` з «Require review from Code Owners» (розділ 2.2).
3. Граф Obsidian: зв'язний кущ ADR, теги, без ізольованих вузлів (розділ 2.4).
4. Swagger Editor з контрактом без помилок валідації (розділ 3.3).
5. Swagger UI на сайті: `trackOrder` після «Try it out» → Execute, Code 200 (розділ 3.3).
6. Розділ API зібраного сайту: навігація рівня 3 і заголовок контракту 1.0.0 (розділ 3.3).
7. Опублікований Javadoc зі списком пакетів (розділ 3.8).
8. Червоний job FAIL-коміту з текстом strict-помилки і `exit code 1`, deploy skipped (розділ 4.3).
9. Зелений конвеєр PASS-коміту: build і deploy з URL сайту (розділ 4.4).
10. Задеплоєний сайт з адресою і датою «Останнє оновлення» (розділ 4.4).
11. Step Summary з таблицею аудиту `review_date` (розділ 5.2).
