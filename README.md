# Plant Shop — e-commerce microservices for selling plants

Java 21 · Spring Boot 3.5 · Spring Cloud 2025.0 · PostgreSQL + Flyway · Kafka

| Service           | Port | Responsibility                                                       |
|-------------------|------|----------------------------------------------------------------------|
| config-service    | 8888 | Spring Cloud Config server; all service config lives in `config-service/src/main/resources/configurations` |
| discovery-service | 8761 | Eureka service registry                                              |
| api-gateway       | 8080 | Single entry point, routes `/api/**` to services via Eureka          |
| catalog-service   | 8081 | Plants and categories (search, filter, CRUD)                         |
| inventory-service | 8082 | Stock levels and per-order reservations                              |
| order-service     | 8083 | Order placement, saga orchestration                                  |
| payment-service   | 8084 | Simulated payment provider (declines amounts above `payment.max-amount`) |

Infrastructure (docker-compose): Kafka `9092`, Kafka UI `http://localhost:8090`, plus one PostgreSQL container per service (below).

## Database & Flyway

Every business service has its **own PostgreSQL container and database** (database-per-service). Inside it,
Flyway creates the service's schema and applies its migrations on startup (`spring.flyway.schemas` +
`create-schemas`). Hibernate runs with `ddl-auto: validate`, so the migrations are the only source of the schema.

| Service           | DB container   | Host port | Database       | Schema      | Migrations (`src/main/resources/db/migration`) |
|-------------------|----------------|-----------|----------------|-------------|------------------------------------------------|
| catalog-service   | `catalog-db`   | 5433      | `catalog_db`   | `catalog`   | `V1__create_catalog_tables.sql`, `V2__seed_catalog.sql` (4 categories, 10 plants) |
| inventory-service | `inventory-db` | 5434      | `inventory_db` | `inventory` | `V1__create_inventory_tables.sql`, `V2__seed_inventory.sql` (stock for plants 1–10) |
| order-service     | `order-db`     | 5435      | `order_db`     | `orders`    | `V1__create_order_tables.sql`                  |
| payment-service   | `payment-db`   | 5436      | `payment_db`   | `payments`  | `V1__create_payment_tables.sql`                |

Credentials for all databases: `plantshop` / `plantshop`. Data lives in named volumes (`catalog-db-data`, ...);
`docker compose down -v` wipes it and Flyway re-creates everything on the next start.

To change the schema, add a new `V<n>__description.sql` file. Never edit a migration that has already been applied.

## Order flow (saga over Kafka)

```
POST /api/orders ──► order-service: price lookup (Feign → catalog) + stock pre-check (Feign → inventory)
                     saves order PENDING ──► [order-created]
inventory-service ── reserves stock ──► [inventory-reserved]  or  [inventory-failed] ──► order CANCELLED
payment-service ──── charges ─────────► [payment-completed]   or  [payment-failed]
   payment-completed ──► order CONFIRMED, inventory commits the reservation (stock deducted)
   payment-failed ─────► order CANCELLED, inventory releases the reservation
```

Creating a plant in the catalog publishes `plant-created`; inventory-service then creates an empty stock row for it.
Events are published only after the DB transaction commits (`@TransactionalEventListener`), and consumers
ignore redelivered events.

## Running everything with Docker Compose

Prerequisite: Docker Desktop running (no local JDK needed — images are built with a multi-stage `Dockerfile`).

```bash
docker compose up -d --build     # build 7 service images and start all 13 containers
docker compose ps                # watch the health status
docker compose logs -f order-service
docker compose down              # stop (add -v to also delete the databases)
```

Startup order is enforced by health checks: databases + Kafka → config-service → discovery-service →
business services and api-gateway. The first build takes a few minutes (Maven dependencies are cached
for later builds); services are ready about a minute after the containers start.

One `Dockerfile` at the root builds every module: `docker build --build-arg SERVICE=catalog-service .`

## Running the services from the IDE

Prerequisites: JDK 21 (`JAVA_HOME` set). Start only the infrastructure in Docker:

```bash
docker compose up -d catalog-db inventory-db order-db payment-db kafka kafka-ui
./mvnw clean package            # Windows: mvnw.cmd clean package
```

Then start the services **in this order** (each in its own terminal, or as IntelliJ run configurations):

```bash
java -jar config-service/target/config-service-1.0.0-SNAPSHOT.jar
java -jar discovery-service/target/discovery-service-1.0.0-SNAPSHOT.jar
java -jar catalog-service/target/catalog-service-1.0.0-SNAPSHOT.jar
java -jar inventory-service/target/inventory-service-1.0.0-SNAPSHOT.jar
java -jar order-service/target/order-service-1.0.0-SNAPSHOT.jar
java -jar payment-service/target/payment-service-1.0.0-SNAPSHOT.jar
java -jar api-gateway/target/api-gateway-1.0.0-SNAPSHOT.jar
```

Eureka dashboard: http://localhost:8761. Each business service exposes Swagger UI at
`http://localhost:<port>/swagger-ui.html`.

Environment overrides: `CONFIG_SERVER_URL`, `EUREKA_URL`, `KAFKA_BOOTSTRAP_SERVERS`, `DB_HOST`, `DB_PORT`,
`DB_USERNAME`, `DB_PASSWORD`.

## Try it (through the gateway)

```bash
# Browse plants: filter by category, care level, max price; paginated
curl "http://localhost:8080/api/catalog/plants?careLevel=EASY&maxPrice=25&page=0&size=5"
curl  http://localhost:8080/api/catalog/categories

# Stock
curl http://localhost:8080/api/inventory/1
curl -X PUT http://localhost:8080/api/inventory/1 -H "Content-Type: application/json" -d '{"quantity": 50}'

# Place an order (returns 202 with status PENDING)
curl -X POST http://localhost:8080/api/orders -H "Content-Type: application/json" -d '{
  "customerEmail": "jane@example.com",
  "shippingAddress": "12 Fern Street, Kyiv",
  "paymentMethod": "CARD",
  "items": [ {"plantId": 1, "quantity": 2}, {"plantId": 3, "quantity": 1} ]
}'

# A moment later the order is CONFIRMED and stock is deducted
curl http://localhost:8080/api/orders/1
curl http://localhost:8080/api/payments/order/1
curl "http://localhost:8080/api/orders?email=jane@example.com"
```

To see the failure path, restock a plant generously and order more than 10 000 worth of it
(e.g. 120 × Japanese Maple, plant 8). The payment is declined, the order ends `CANCELLED`, and the reservation is released.

## Tests

`./mvnw test` runs the Mockito unit tests for the service layer of catalog, inventory, order and payment.
