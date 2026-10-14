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

## Running on Kubernetes (minikube)

All manifests are in [`k8s/`](k8s), one object per file, everything in the `plantshop` namespace
(`00-namespace.yaml` sorts first so a single `kubectl apply` creates it before the rest).

| Component | Objects | Notes |
|---|---|---|
| config-service, discovery-service | Deployment (1 replica) + ClusterIP Service | Eureka runs standalone, so it stays at one replica |
| api-gateway | Deployment (2) + **NodePort** Service `30080` + Ingress `plantshop.local` | the entry point from the host |
| catalog-service | Deployment (2) + ClusterIP Service | used for the scaling / update / rollback demo; `GET /api/catalog/info` shows version, pod name and ConfigMap values |
| inventory-, order-, payment-service | Deployment (1) + ClusterIP Service | |
| `<service>-db` ×4 | Deployment (PostgreSQL 16, `Recreate`) + Service + **PVC** 1Gi | database per service, schema created by Flyway |
| kafka | Deployment (KRaft single node, `Recreate`) + Service + **PVC** 1Gi | |
| shared | ConfigMap `plantshop-config`, Secret `plantshop-db-secret` | |

Every Spring Deployment has CPU/memory requests and limits, a startup probe and liveness/readiness probes
(`/actuator/health/liveness` and `/readiness`), and an init container that waits for its dependencies
(Kubernetes has no `depends_on`). Updates use `RollingUpdate` with `maxSurge: 1`, `maxUnavailable: 0` and
`minReadySeconds: 15`, so a new pod is registered in Eureka before an old one is removed. The gateway retries
GET requests on another instance, which keeps the API available during a rolling update.

### Deployment order

```bash
# 1. Cluster (once); 6 GB is enough for the whole stack
minikube start --driver=docker --cpus=4 --memory=6g
minikube addons enable metrics-server
minikube addons enable ingress
kubectl get nodes
kubectl get pods -n kube-system

# 2. Images: built straight into minikube's Docker, tagged with versions (never "latest").
#    All services as 1.0.0 plus catalog-service 1.1.0 for the update demo.
./scripts/build-images.sh                  # PowerShell: .\scripts\build-images.ps1

# 3. Everything at once (about 1.5 min until all pods are Ready)
kubectl apply -f k8s/
kubectl -n plantshop get pods -w

# 4. Open the application
minikube service api-gateway -n plantshop --url       # NodePort, prints http://127.0.0.1:<port>
#    or: kubectl -n plantshop port-forward svc/api-gateway 8080:80   -> http://localhost:8080
#    or: Ingress — add "127.0.0.1 plantshop.local" to the hosts file, run "minikube tunnel",
#        then open http://plantshop.local/api/catalog/plants
```

Useful URLs (through the gateway): `/api/catalog/plants`, `/api/catalog/categories`, `/api/catalog/info`,
`/api/inventory/1`, `/api/orders`.

### ConfigMap `plantshop-config`

Injected into every Spring service with `envFrom`; the services read them through the `${...}` placeholders
in `config-service/src/main/resources/configurations`.

| Key | Value | Used for |
|---|---|---|
| `CONFIG_SERVER_URL` | `http://config-service:8888` | where services load their configuration from |
| `EUREKA_URL` | `http://discovery-service:8761/eureka` | service registry |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka:9092` | Kafka broker |
| `DB_PORT` | `5432` | PostgreSQL port (the host comes from `DB_HOST`, set per Deployment) |
| `SHOP_NAME` | `Plant Shop on Kubernetes` | shown by `/api/catalog/info` |
| `SHOP_CURRENCY` | `UAH` | shown by `/api/catalog/info` |
| `PAYMENT_MAX_AMOUNT` | `10000` | payment-service declines larger payments |
| `JAVA_TOOL_OPTIONS` | `-XX:MaxRAMPercentage=70 -XX:+UseSerialGC ...` | small JVM footprint for a laptop cluster |

### Secret `plantshop-db-secret`

| Key | Used by |
|---|---|
| `DB_USERNAME` | `POSTGRES_USER` of the 4 databases; `DB_USERNAME` of the services |
| `DB_PASSWORD` | `POSTGRES_PASSWORD` of the 4 databases; `DB_PASSWORD` of the services |

The values are lab-only demo credentials. Check them inside a container with
`kubectl -n plantshop exec deploy/catalog-service -- printenv DB_USERNAME DB_PASSWORD SHOP_NAME`.

### Lifecycle cheat sheet

```bash
kubectl -n plantshop delete pod <catalog-service-pod>                       # self-healing
kubectl -n plantshop scale deploy/catalog-service --replicas=3              # scaling
for i in $(seq 6); do curl -s localhost:8080/api/catalog/info; echo; done   # which pod answered
kubectl -n plantshop set image deploy/catalog-service catalog-service=plantshop/catalog-service:1.1.0
kubectl -n plantshop annotate deploy/catalog-service kubernetes.io/change-cause="Update image to 1.1.0" --overwrite
kubectl -n plantshop rollout status deploy/catalog-service
kubectl -n plantshop rollout history deploy/catalog-service
kubectl -n plantshop rollout undo deploy/catalog-service                    # back to the previous revision
```

`./scripts/k8s-demo.sh` runs the whole demonstration (deploy, self-healing, scaling, rolling update with an
availability probe, rollback, `ImagePullBackOff` diagnostics and the fix) and saves every command with its output
to [`docs/k8s-report.md`](docs/k8s-report.md).

### Removing everything

```bash
kubectl delete -f k8s/          # or: kubectl delete namespace plantshop
```

minikube's storage provisioner names its directories after `<namespace>/<pvc>` and does not always remove them.
If the next deployment fails with `password authentication failed`, the databases found old data created with a
different password: run `minikube ssh -- sudo rm -rf /tmp/hostpath-provisioner/plantshop` and apply again.

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
