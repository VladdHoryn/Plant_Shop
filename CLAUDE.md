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

The cluster is managed by Argo CD from this repository (`https://github.com/VladdHoryn/Plant_Shop.git`, public; app code and config in one repo). Lab 6 continues on branch **`lab/DevOps_lab_6`** (not pushed yet). The Applications' `targetRevision` still points at `lab/DevOps_lab_5`: switch it to `lab/DevOps_lab_6` in **both** `bootstrap/root.yaml` and `argocd-apps/*.yaml`, push, then re-apply `bootstrap/root.yaml` by hand once (root watches its own branch, so it cannot move itself). Images: Docker Hub `vladyslavv648/plantshop-<module>:<tag>` (1.0.0 for every module, plus 1.1.0 for catalog-service), built on the host Docker and pushed.

```
bootstrap/root.yaml              root Application (App of Apps), the only thing ever applied by hand
argocd-apps/plantshop.yaml       Application "plantshop" -> charts/plantshop + values-minikube.yaml, namespace plantshop
argocd-apps/argo-rollouts.yaml   (Lab 6) Application for the argo-rollouts Helm chart, namespace argo-rollouts
charts/plantshop/                the Lab 4 chart adapted for Argo CD; values-minikube.yaml is the environment
docs/lab5-defense.md             Lab 5 defense runbook; docs/DevOps_lab_5.md is the Lab 5 report
```

Rules:
- **Git is the only source of truth.** Never `helm install/upgrade` or `kubectl apply` the chart; change values in `charts/plantshop/values-minikube.yaml`, commit, push to the lab branch, then `argocd app get plantshop --refresh`. Roll back with `git revert`, not `argocd app rollback` (auto-sync would undo it). `helm list -n plantshop` is empty by design.
- `plantshop` has automated sync with `prune: true` and `selfHeal: true`: manual `kubectl scale/edit` is reverted within seconds. Rollout actions (`promote`, `abort`, `retry`) are the exception: they change the Rollout's status, not its spec, so Argo CD does not undo them.
- **No secrets in Git.** `existingSecret: plantshop-secret` makes the chart skip its Secret; that Secret (`DB_USERNAME`, `DB_PASSWORD`) is created by hand in namespace `plantshop`. Recreating it with a new password requires wiping the DB PVCs.
- A release changes only `catalogService.image.tag` (each service can override the global `image.tag`).
- Argo CD runs Helm `pre-upgrade` hooks as PreSync on every sync and ignores `helm test`, so `values-minikube.yaml` disables both (`hooks.preUpgradeCheck.enabled`, `tests.enabled`); the Ingress is disabled too (no address → Progressing forever). Access is via port-forward: Argo CD `svc/argocd-server 8080:443`, app `svc/plantshop-api-gateway 8081:80`.
- Changes to `charts/plantshop` should be checked with `helm lint charts/plantshop --strict -f charts/plantshop/values-minikube.yaml` and `helm template`; bump `version` in `Chart.yaml` on every chart change.
- Tooling: `~/bin/argocd.exe` (CLI), `~/bin/helm`, `~/bin/kubectl`, minikube `--cpus=4 --memory=7g` (Docker Desktop has 7.6 GB). Every Spring pod requests 320Mi (limit 640Mi), so memory is tight with extra catalog replicas.

## Lab 6: progressive delivery with Argo Rollouts (current)

Goal: replace the plain Deployment update of **catalog-service** with a Rollout that can stop on an intermediate step, be checked, then promoted or aborted. Only catalog-service gets a Rollout; every other service stays a Deployment. It is the natural target: the only module with released versions, read-mostly, and `GET /api/catalog/version` (`/info` also shows the pod) tells versions apart.

1. **Controller via Argo CD.** `argocd-apps/argo-rollouts.yaml`: Application from `https://argoproj.github.io/argo-helm`, chart `argo-rollouts`, exact `targetRevision` (from `helm search repo argo/argo-rollouts`), destination namespace `argo-rollouts`, `CreateNamespace=true`, automated prune/selfHeal, annotation `argocd.argoproj.io/sync-wave: "-1"`. Commit it **alone** and wait for `argocd app get argo-rollouts` Healthy and the CRDs (`kubectl get crd | grep argoproj`) before committing the Rollout. Add `retry` (`limit: 5`, `backoff: {duration: 10s, factor: 2, maxDuration: 3m}`) to `argocd-apps/plantshop.yaml` so a sync that ran before the CRDs heals itself.
2. **kubectl plugin.** Windows: download `kubectl-argo-rollouts-windows-amd64` from the argo-rollouts GitHub releases, save as `~/bin/kubectl-argo-rollouts.exe`; check with `kubectl argo rollouts version`.
3. **Rollout in the chart.** Wrap `templates/catalog-service-deployment.yaml` in `{{- if not .Values.catalogService.rollout.enabled }}` and add `templates/catalog-service-rollout.yaml` (`argoproj.io/v1alpha1`, `kind: Rollout`) with the same name, labels, selector and an **unchanged copy of the pod template**; `strategy:` is `toYaml .Values.catalogService.rollout.strategy`. Use this chart's helpers (`plantshop.componentName`, `plantshop.labels`, `plantshop.selectorLabels` with `dict "root" . "component" "catalog-service"`), not the `myapp.*` names from the instructions. Deployment-only fields (`strategy.rollingUpdate`, `kubernetes.io/change-cause`) do not belong in the Rollout. Defaults (`rollout.enabled: false`, empty strategy) go in `values.yaml`; enable it in `values-minikube.yaml` with `catalogService.replicaCount` ≥ 4. The first switch deletes the Deployment and creates the Rollout, so a short outage there is expected. A Deployment and a Rollout with the same selector must never coexist.
4. **Strategy: canary (planned).** In `values-minikube.yaml`, e.g. `replicaCount: 5`, steps `setWeight: 20` → `pause: {}` (manual promote) → `setWeight: 60` → `pause: {duration: 30s}` → `setWeight: 80` → `pause: {duration: 30s}`. Without a traffic router the weight is the pod ratio. Why not blue-green (to explain at the defense): api-gateway reaches catalog-service through **Eureka** (`lb://catalog-service`), not through the Kubernetes Service, so blue-green preview pods would register in Eureka and get real user traffic before promote; it would also double the memory. If blue-green is chosen anyway: add `catalog-service-service-preview.yaml`, `activeService: plantshop-catalog-service`, `previewService: plantshop-catalog-service-preview`, `autoPromotionEnabled: false`, plus `ignoreDifferences` on Service `/spec/selector` if Argo CD shows it OutOfSync.
5. **Release via Git.** Build and push catalog images first: 1.2.0 (release), 1.3.0 (abort), one spare, each with `--build-arg APP_VERSION=<v>`. Change `catalogService.image.tag`, push, `argocd app get plantshop --refresh`. Watch with `kubectl argo rollouts get rollout plantshop-catalog-service -n plantshop --watch`, `kubectl argo rollouts dashboard` (http://localhost:3100) and Argo CD (Health `Suspended` on a pause). Show the version split with a request loop **inside the cluster** (a port-forward hits a single pod): `kubectl -n plantshop run curl --rm -it --image=curlimages/curl -- sh -c 'while true; do curl -s http://plantshop-catalog-service:<port>/api/catalog/version; echo; sleep 0.5; done'`. Then `kubectl argo rollouts promote plantshop-catalog-service -n plantshop` (or Argo CD UI: Rollout ⋮ → Resume / Promote-Full / Abort / Retry).
6. **Abort.** New tag (1.3.0), push, wait for the first pause, `kubectl argo rollouts abort plantshop-catalog-service -n plantshop`: traffic returns to the stable ReplicaSet, the Rollout is Degraded, and Argo CD shows **Synced + Degraded** (the cluster spec matches Git, which still says 1.3.0, but the release was cancelled). Finish with `git revert <1.3.0 commit>`, push, refresh → Healthy on 1.2.0. (`kubectl argo rollouts retry rollout ...` reruns the same release if the problem was fixed outside the image.)
7. **Deliverables:** lab report `docs/DevOps_lab_6.md` and defense runbook `docs/lab6-defense.md`, in the style of the Lab 5 docs. Live defense order: release commit → pause (Suspended) → promote → Healthy; next release → abort → Synced + Degraded → `git revert`.

Pitfalls: `no matches for kind "Rollout"` / SyncFailed means the app synced before the CRDs existed (wait for argo-rollouts Healthy and Sync, or rely on `retry`). A Rollout starts only on a pod template change (image, env, labels), not on `replicas` or strategy changes. Both versions share the one catalog DB during a canary, so a release must not carry a Flyway migration the old version cannot run against.

## Lab branches

DevOps course labs: `k8s-lab` (Lab 3), `helm-lab` (Lab 4), `lab/DevOps_lab_5` (Lab 5), `lab/DevOps_lab_6` (Lab 6, current, branched from Lab 5). `gitops-lab` is an older attempt at Lab 5, not used by the cluster. Branches `lab/Lab_*` belong to a different (software quality) course. `scripts/k8s-demo.sh` and `scripts/helm-demo.sh` record Lab 3/4 demos into `docs/`.
