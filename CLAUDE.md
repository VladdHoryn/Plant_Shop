# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Plant Shop: e-commerce microservices for selling plants. Java 21, Spring Boot 3.5, Spring Cloud 2025.0, PostgreSQL + Flyway, Kafka. Maven multi-module (root `pom.xml` is the parent; modules: config-service, discovery-service, api-gateway, catalog-service, inventory-service, order-service, payment-service). `README.md` holds the full ports table, deployment walkthroughs and Helm values reference.

## Build & test

On this Windows machine `JAVA_HOME` is not set globally. In Git Bash:

```bash
export JAVA_HOME="/c/Users/user/.jdks/corretto-21.0.12.1"; export PATH="$JAVA_HOME/bin:$PATH"
./mvnw clean package                                   # build all jars
./mvnw test                                            # all unit tests
./mvnw -pl order-service test                          # one module
./mvnw -pl order-service test -Dtest=OrderServiceTest  # one class (add #methodName for one method)
./mvnw -pl catalog-service -am package -DskipTests     # one service plus what it depends on
```

Tests are plain Mockito/JUnit unit tests (no Spring context), so they need neither Docker nor the config server. There is no linter configured.

Docker Desktop is often not running: start it before `docker compose`, image builds or minikube. Python and jq are not installed.

## Running

- **Docker Compose (full stack):** `docker compose up -d --build`. Startup order is enforced by health checks: DBs + Kafka → config-service → discovery-service → business services + gateway.
- **From the IDE:** `docker compose up -d catalog-db inventory-db order-db payment-db kafka kafka-ui`, then start jars in order: config → discovery → business services → api-gateway.
- **Kubernetes, Lab 3:** raw manifests in `k8s/` (`kubectl apply -f k8s/`).
- **Helm, Lab 4:** `helm/plantshop` (images `plantshop/<module>` built into minikube by `scripts/build-images.sh`). Lint: `helm lint helm/plantshop --strict -f helm/plantshop/values-prod.yaml`.
- **Argo CD, Lab 5:** see GitOps below; this is the current way the cluster is managed.
- A single root `Dockerfile` builds any module: `docker build --build-arg SERVICE=<module> --build-arg APP_VERSION=<v> .`. `APP_VERSION` is baked into the image and shown by `GET /api/catalog/version` and `/api/catalog/info`. A new module must be added to the Dockerfile's `COPY .../pom.xml` list and to `<modules>` in the root pom.

## Architecture

**Configuration is centralized.** Each service's own `src/main/resources/application.yml` contains only its name and `spring.config.import: configserver:...`. The real config (ports, datasource, Flyway, Kafka serializers, gateway routes) lives in `config-service/src/main/resources/configurations/<service>.yml`, with shared settings in `configurations/application.yml`. Environment overrides (`CONFIG_SERVER_URL`, `EUREKA_URL`, `KAFKA_BOOTSTRAP_SERVERS`, `DB_HOST`, `DB_PORT`, `DB_USERNAME`, `DB_PASSWORD`, `APP_VERSION`) enter through `${...}` placeholders there; on Kubernetes they come from the chart's ConfigMap and the DB Secret.

**Routing:** api-gateway (Spring Cloud Gateway, WebFlux) routes `/api/catalog|inventory|orders|payments/**` to `lb://<service>` via Eureka and retries failed GETs on another instance.

**Database per service, schema by Flyway.** Each business service has its own PostgreSQL instance and a dedicated schema (`catalog`, `inventory`, `orders`, `payments`) created by Flyway. Hibernate is `ddl-auto: validate`, so migrations in `src/main/resources/db/migration` are the only source of the schema. Add a new `V<n>__*.sql`; never edit an applied migration; no docker init SQL scripts.

**Order saga over Kafka (choreography):**
```
POST /api/orders → order-service (Feign: catalog prices, inventory pre-check) saves PENDING → [order-created]
inventory-service reserves stock → [inventory-reserved] | [inventory-failed] → order CANCELLED
payment-service charges → [payment-completed] → order CONFIRMED, inventory commits the reservation
                        | [payment-failed]    → order CANCELLED, inventory releases the reservation
catalog [plant-created] → inventory creates an empty stock row
```
- Events are published from `*EventPublisher` classes via `@TransactionalEventListener` (after DB commit). Consumers must stay idempotent.
- No shared library: **each service has its own copy of the event records** and a `KafkaTopics` class. Kafka JSON uses logical type ids (`spring.json.type.mapping`) configured per service in the config-service YAML, so changing an event means updating producer, every consumer, and the mappings on both sides.

**Per-service package layout** (`com.plantshop.<service>`): `controller`, `service`, `repository`, `entity`, `dto`, `mapper` (hand-written static mappers), `event`, `exception`, `client` (Feign, order-service only). Lombok throughout.

## GitOps (Lab 5, Argo CD)

The cluster is managed by Argo CD from this repository (`https://github.com/VladdHoryn/Plant_Shop.git`), branch **`gitops-lab`** (the Applications' `targetRevision`). Images: Docker Hub `vladyslavv648/plantshop-<module>:<tag>`.

```
bootstrap/root.yaml              root Application (App of Apps), the only thing ever applied by hand
argocd-apps/plantshop.yaml       Application "plantshop" -> charts/plantshop + values-minikube.yaml, namespace plantshop
charts/plantshop/                copy of the Lab 4 chart adapted for Argo CD; values-minikube.yaml is the environment
```

Rules:
- **Git is the only source of truth.** Never `helm install/upgrade` or `kubectl apply` the chart; change values in `charts/plantshop/values-minikube.yaml`, commit, push to `gitops-lab`, then `argocd app get plantshop --refresh`. Roll back with `git revert`, not `argocd app rollback` (auto-sync would undo it). `helm list -n plantshop` is empty by design.
- `plantshop` has automated sync with `prune: true` and `selfHeal: true`: manual `kubectl scale/edit` is reverted within seconds.
- **No secrets in Git.** `existingSecret: plantshop-secret` makes the chart skip its Secret; that Secret (`DB_USERNAME`, `DB_PASSWORD`) is created by hand in namespace `plantshop`. Recreating it with a new password requires wiping the DB PVCs.
- A release changes only `catalogService.image.tag` (each service can override the global `image.tag`). catalog-service has `progressDeadlineSeconds: 120`, so a bad tag shows as Degraded in ~2 min.
- Argo CD runs Helm `pre-upgrade` hooks as PreSync on every sync and ignores `helm test`, so `values-minikube.yaml` disables both (`hooks.preUpgradeCheck.enabled`, `tests.enabled`); the Ingress is disabled too (no address → Progressing forever). Access is via port-forward: Argo CD `svc/argocd-server 8080:443`, app `svc/plantshop-api-gateway 8081:80`.
- Changes to `charts/plantshop` should be checked with `helm lint charts/plantshop --strict -f charts/plantshop/values-minikube.yaml` and `helm template`.
- Tooling: `~/bin/argocd.exe` (CLI), minikube `--cpus=4 --memory=7g` (Docker Desktop has 7.6 GB). Defense runbook: `docs/lab5-defense.md`.

## Lab branches

DevOps course labs: `k8s-lab` (Lab 3), `helm-lab` (Lab 4), `gitops-lab` (Lab 5). Branches `lab/Lab_*` belong to a different (software quality) course. `scripts/k8s-demo.sh` and `scripts/helm-demo.sh` record Lab 3/4 demos into `docs/`.
