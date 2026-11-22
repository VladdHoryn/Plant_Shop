---
owner: VladdHoryn
status: published
review_date: 2027-02-20
tags:
  - developer
  - ops
---

# Локальне середовище розробника

Як запустити систему на своїй машині для розробки. Чому компоненти стартують саме в такому порядку і які
змінні оточення вони читають — у [ISD, розділи 4–5](../architecture/isd.md#4-середовища-та-змінні-оточення);
розгортання в Kubernetes і Helm — у [runbook розгортання](../architecture/deployment.md).

## Передумови

| Інструмент | Версія | Навіщо |
|---|---|---|
| Git | будь-яка сучасна | клон репозиторію, хук `pre-commit` |
| Docker Desktop | запущений | Compose-стек, Testcontainers в інтеграційних тестах, Lizard у хуку, MkDocs/Spectral/markdownlint |
| JDK | 21 (`JAVA_HOME`) | лише для запуску з IDE і `./mvnw`; для Compose не потрібен |

Python і Node.js на хості не потрібні: інструменти аналізу та документації працюють у закріплених Docker-образах
каталогу `tools/` (див. [стандарти коду](coding-standards.md#інструменти-закріплені-за-версією)).

## Увесь стек у Docker Compose

Образи збираються багатоетапним `Dockerfile`, локальний JDK не потрібен.

```bash
docker compose up -d --build     # зібрати 8 образів сервісів і запустити всі 15 контейнерів
docker compose ps                # стан healthcheck-ів
docker compose logs -f order-service
docker compose down              # зупинити (з -v — також видалити бази даних)
```

Порядок запуску забезпечують healthcheck-и: бази даних і Kafka → config-service → discovery-service → бізнес-сервіси
та api-gateway. Перша збірка триває кілька хвилин (залежності Maven кешуються); сервіси готові приблизно через
хвилину після старту контейнерів. Один `Dockerfile` у корені збирає будь-який модуль:
`docker build --build-arg SERVICE=catalog-service .`

Допоміжні адреси: Kafka `localhost:9092`, Kafka UI <http://localhost:8090>, Eureka <http://localhost:8761>.

## Бази даних і Flyway

Кожен бізнес-сервіс має власний контейнер PostgreSQL (рішення — [ADR-0001](../architecture/adr/0001-database-per-service-postgresql-flyway.md),
модель даних — [SDD, розділ 4](../architecture/sdd.md#4-модель-даних)). Для локального підключення з IDE:

| Сервіс | Контейнер БД | Порт на хості | База | Схема |
|---|---|---|---|---|
| catalog-service | `catalog-db` | 5433 | `catalog_db` | `catalog` |
| inventory-service | `inventory-db` | 5434 | `inventory_db` | `inventory` |
| order-service | `order-db` | 5435 | `order_db` | `orders` |
| payment-service | `payment-db` | 5436 | `payment_db` | `payments` |
| user-service | `user-db` | 5437 | `user_db` | `users` |

Облікові дані всіх баз: `plantshop` / `plantshop` (лише для лабораторного середовища). Дані живуть в іменованих
томах (`catalog-db-data`, …); `docker compose down -v` стирає їх, і Flyway створює все заново при наступному
старті. Щоб змінити схему, додайте новий файл `V<n>__description.sql`; уже застосовану міграцію не редагують.

## Запуск сервісів з IDE

У Docker запускається лише інфраструктура:

```bash
docker compose up -d catalog-db inventory-db order-db payment-db user-db kafka kafka-ui
./mvnw clean package            # Windows: mvnw.cmd clean package
```

Потім сервіси **в такому порядку** (кожен в окремому терміналі або як run configuration IntelliJ):

```bash
java -jar config-service/target/config-service-1.0.0-SNAPSHOT.jar
java -jar discovery-service/target/discovery-service-1.0.0-SNAPSHOT.jar
java -jar catalog-service/target/catalog-service-1.0.0-SNAPSHOT.jar
java -jar inventory-service/target/inventory-service-1.0.0-SNAPSHOT.jar
java -jar payment-service/target/payment-service-1.0.0-SNAPSHOT.jar
java -jar order-service/target/order-service-1.0.0-SNAPSHOT.jar
java -jar user-service/target/user-service-1.0.0-SNAPSHOT.jar
java -jar api-gateway/target/api-gateway-1.0.0-SNAPSHOT.jar
```

Кожен бізнес-сервіс відкриває згенеровану з коду (code-first) довідку springdoc:
`http://localhost:<порт>/swagger-ui.html`. Контракт публічного API замовлень — у [довіднику API](../api/reference.md).

Перевизначення оточення: `CONFIG_SERVER_URL`, `EUREKA_URL`, `KAFKA_BOOTSTRAP_SERVERS`, `DB_HOST`, `DB_PORT`,
`DB_USERNAME`, `DB_PASSWORD`.

## Тести

`./mvnw test` запускає модульні тести Mockito (catalog, inventory, order, payment, user-service), тести
`secure-logging` та інтеграційні тести `*IntegrationTest` user-service і order-service. Інтеграційні тести
піднімають PostgreSQL через Testcontainers, тому Docker має бути запущений.

```bash
./mvnw test -pl payment-service -am -Dtest=PaymentServiceTest -Dsurefire.failIfNoSpecifiedTests=false
```

`-am` потрібен, бо кожен сервіс залежить від модуля `secure-logging`.

## Документація локально

```bash
./scripts/docs-lint.sh            # markdownlint + Spectral + аудит review_date
./scripts/docs-build.sh           # mkdocs build --strict -> site/ (те саме, що в CI)
./scripts/docs-build.sh serve     # попередній перегляд на http://localhost:8000
./scripts/doc-drift-check.sh      # код ↔ SDD / OpenAPI / ISD
```
