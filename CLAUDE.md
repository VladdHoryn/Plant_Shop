# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Plant Shop: e-commerce microservices for selling plants. Java 21, Spring Boot 3.5, Spring Cloud 2025.0, PostgreSQL + Flyway, Kafka. Maven multi-module (root `pom.xml` is the parent; modules: config-service, discovery-service, api-gateway, catalog-service, inventory-service, order-service, payment-service). `README.md` holds the full ports table, deployment walkthroughs and Helm values reference.

Stale `user-service/target` and `secure-logging/target` folders may exist in the working tree: build leftovers from other branches, not modules here.

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

A local `.git/hooks/pre-commit` (installed from the software-quality course branch `lab/Lab_7`) blocks commits of Java files unless `scripts/cc-check.sh` + `tools/lizard` (Lizard in Docker) can run; they exist only on `lab/Lab_7`. To commit Java here, extract them untracked (`git archive lab/Lab_7 scripts/cc-check.sh tools/lizard | tar -x`), commit, then delete them. Do not use `--no-verify`.

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

## GitOps (Lab 5 base, Argo CD)

The cluster is managed by Argo CD from this repository (`https://github.com/VladdHoryn/Plant_Shop.git`, public; app code and config in one repo). Both Applications (`root`, `plantshop`) track branch **`lab/DevOps_lab_6`**. Moving to another branch means changing `targetRevision` in `bootstrap/root.yaml` and `argocd-apps/plantshop.yaml`, pushing, then re-applying `bootstrap/root.yaml` by hand (root reads `argocd-apps/` from its own branch, so it cannot move itself). Images: Docker Hub `vladyslavv648/plantshop-<module>:<tag>` (1.0.0 for every module; catalog-service also has 1.1.0–1.4.0), built on the host Docker and pushed. If minikube was recreated, Argo CD (`stable/install.yaml`, server-side apply) and `plantshop-secret` must be reinstalled by hand before `kubectl apply -f bootstrap/root.yaml` (commands in `docs/DevOps_lab_6.md` §4.1).

```
bootstrap/root.yaml              root Application (App of Apps), the only thing ever applied by hand
argocd-apps/plantshop.yaml       Application "plantshop" -> charts/plantshop + values-minikube.yaml, namespace plantshop
argocd-apps/argo-rollouts.yaml   (Lab 6) Application for the argo-rollouts Helm chart, namespace argo-rollouts
charts/plantshop/                the Lab 4 chart adapted for Argo CD; values-minikube.yaml is the environment
docs/lab5-defense.md             Lab 5 defense runbook; docs/DevOps_lab_5.md is the Lab 5 report
docs/lab6-defense.md             Lab 6 defense runbook; docs/DevOps_lab_6.md is the Lab 6 report
```

Rules:
- **Git is the only source of truth.** Never `helm install/upgrade` or `kubectl apply` the chart; change values in `charts/plantshop/values-minikube.yaml`, commit, push to the lab branch, then `argocd app get plantshop --refresh`. Roll back with `git revert`, not `argocd app rollback` (auto-sync would undo it). `helm list -n plantshop` is empty by design.
- `plantshop` has automated sync with `prune: true` and `selfHeal: true`: manual `kubectl scale/edit` is reverted within seconds. Rollout actions (`promote`, `abort`, `retry`) are the exception: they change the Rollout's status, not its spec, so Argo CD does not undo them.
- **No secrets in Git.** `existingSecret: plantshop-secret` makes the chart skip its Secret; that Secret (`DB_USERNAME`, `DB_PASSWORD`) is created by hand in namespace `plantshop`. Recreating it with a new password requires wiping the DB PVCs.
- A release changes only `catalogService.image.tag` (each service can override the global `image.tag`).
- Argo CD runs Helm `pre-upgrade` hooks as PreSync on every sync and ignores `helm test`, so `values-minikube.yaml` disables both (`hooks.preUpgradeCheck.enabled`, `tests.enabled`); the Ingress is disabled too (no address → Progressing forever). Access is via port-forward: Argo CD `svc/argocd-server 8080:443`, app `svc/plantshop-api-gateway 8081:80`.
- Changes to `charts/plantshop` should be checked with `helm lint charts/plantshop --strict -f charts/plantshop/values-minikube.yaml` and `helm template`; bump `version` in `Chart.yaml` on every chart change.
- Tooling: `~/bin/argocd.exe` (CLI), `~/bin/helm`, `~/bin/kubectl`, minikube `--cpus=4 --memory=7g` (Docker Desktop has 7.6 GB). Every Spring pod requests 320Mi (limit 640Mi), so memory is tight with extra catalog replicas.

## Lab 6: progressive delivery with Argo Rollouts (done)

**catalog-service** is deployed as an Argo Rollout with a canary strategy; every other service stays a Deployment. Report: `docs/DevOps_lab_6.md`; live-demo commands: `docs/lab6-defense.md`.

- **Controller:** `argocd-apps/argo-rollouts.yaml`, Helm chart `argo-rollouts` 2.43.12 (controller v1.10.2) from `https://argoproj.github.io/argo-helm`, namespace `argo-rollouts`, `sync-wave: "-1"`, `ServerSideApply=true` (large CRDs), in-cluster dashboard disabled. `argocd-apps/plantshop.yaml` has `syncPolicy.retry` so a sync that ran before the CRDs heals itself. kubectl plugin: `~/bin/kubectl-argo-rollouts.exe` v1.10.2.
- **Chart:** `templates/catalog-service-rollout.yaml` renders when `catalogService.rollout.enabled`; `catalog-service-deployment.yaml` renders only otherwise (never both with one selector). Both include the shared pod template `plantshop.catalogService.podTemplate` from `templates/_catalog-service-pod.tpl`: edit the pod there, not in either kind. `rollout.strategy` is passed through `toYaml` as the Rollout's `spec.strategy`. Defaults in `values.yaml` are `rollout.enabled: false` (dev/prod values need no CRDs). Chart version 0.3.0.
- **Strategy (`values-minikube.yaml`):** `replicaCount: 4`, canary `maxSurge: 1`, `maxUnavailable: 0`, steps `setWeight 25` → `pause: {}` → `50` → `pause 30s` → `75` → `pause 30s`. With 4 pods each step is exactly one more new pod; memory (≈5.4 of 7 GB used) does not fit more. Canary over blue-green because api-gateway reaches catalog through **Eureka** (`lb://catalog-service`), so blue-green preview pods would get real traffic before promote; there is no traffic router, so weight = pod share (measured 30/10 at 25% both via the Service and via the gateway).
- **Release:** change only the 4-space `    tag:` line under `catalogService` in `values-minikube.yaml`, commit, push, refresh. A Rollout starts only on a pod template change. Watch: `kubectl argo rollouts get rollout plantshop-catalog-service -n plantshop --watch`; Argo CD Health is `Suspended` on a pause. Show the split from a pod inside the cluster (port-forward pins one pod): `kubectl -n plantshop run curl --rm -it --image=curlimages/curl -- sh -c 'while true; do curl -s http://plantshop-api-gateway/api/catalog/version; echo; sleep 0.5; done'` (prefix `MSYS_NO_PATHCONV=1` in Git Bash if the path gets mangled). `kubectl argo rollouts promote|abort|retry rollout ...` act on status, so selfHeal does not undo them.
- **Abort semantics:** after `abort` the Rollout is Degraded on the stable version and Argo CD shows **Synced + Degraded** (spec still matches Git). Finish with `git revert` of the release commit; going back to the stable tag reuses the existing ReplicaSet (same pod-template hash), so it is Healthy in seconds with no new pods.
- Git history of the lab: `a334f8c` controller, `8e0a6da` Deployment → Rollout, `98e9a25` release 1.2.0, `ab07d1e` release 1.3.0 (aborted), `350b63f` its revert. Current state: catalog 1.2.0, all Applications Synced/Healthy; the defense demo releases 1.4.0 (promote) then 1.3.0 (abort + revert).
- Both versions share the one catalog DB during a canary, so a release must not carry a Flyway migration the old version cannot run against.

## Lab branches

DevOps course labs: `k8s-lab` (Lab 3), `helm-lab` (Lab 4), `lab/DevOps_lab_5` (Lab 5), `lab/DevOps_lab_6` (Lab 6, current, branched from Lab 5). `gitops-lab` is an older attempt at Lab 5, not used by the cluster. Branches `lab/Lab_*` belong to a different (software quality) course. `scripts/k8s-demo.sh` and `scripts/helm-demo.sh` record Lab 3/4 demos into `docs/`.
