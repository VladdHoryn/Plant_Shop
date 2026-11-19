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

The cluster is managed by Argo CD from this repository (`https://github.com/VladdHoryn/Plant_Shop.git`, public; app code and config in one repo). Both Applications (`root`, `plantshop`) track branch **`lab/DevOps_lab_7`** (Lab 7). Moving to another branch means changing `targetRevision` in `bootstrap/root.yaml` and `argocd-apps/plantshop.yaml`, pushing, then re-applying `bootstrap/root.yaml` by hand (root reads `argocd-apps/` from its own branch, so it cannot move itself). Images: Docker Hub `vladyslavv648/plantshop-<module>:<tag>` (1.0.0 for every module; catalog-service also has 1.1.0–1.7.0, config-service 1.1.0), built on the host Docker and pushed. If minikube was recreated, Argo CD (`stable/install.yaml`, server-side apply), `plantshop-secret` and `grafana-admin` must be recreated by hand before `kubectl apply -f bootstrap/root.yaml` (commands in `docs/lab7-defense.md` §1).

```
bootstrap/root.yaml              root Application (App of Apps), the only thing ever applied by hand
argocd-apps/plantshop.yaml       Application "plantshop" -> charts/plantshop + values-minikube.yaml, namespace plantshop
argocd-apps/argo-rollouts.yaml   (Lab 6) Application for the argo-rollouts Helm chart, namespace argo-rollouts
charts/plantshop/                the Lab 4 chart adapted for Argo CD; values-minikube.yaml is the environment
docs/lab5-defense.md             Lab 5 defense runbook; docs/DevOps_lab_5.md is the Lab 5 report
docs/lab6-defense.md             Lab 6 defense runbook; docs/DevOps_lab_6.md is the Lab 6 report
argocd-apps/kps.yaml, loki.yaml, alloy.yaml   (Lab 7) monitoring stack, namespace monitoring
docs/lab7-defense.md             Lab 7 defense runbook (incl. cluster recreation); docs/DevOps_lab_7.md is the Lab 7 report + theory
```

Rules:
- **Git is the only source of truth.** Never `helm install/upgrade` or `kubectl apply` the chart; change values in `charts/plantshop/values-minikube.yaml`, commit, push to the lab branch, then `argocd app get plantshop --refresh`. Roll back with `git revert`, not `argocd app rollback` (auto-sync would undo it). `helm list -n plantshop` is empty by design.
- `plantshop` has automated sync with `prune: true` and `selfHeal: true`: manual `kubectl scale/edit` is reverted within seconds. Rollout actions (`promote`, `abort`, `retry`) are the exception: they change the Rollout's status, not its spec, so Argo CD does not undo them.
- **No secrets in Git.** `existingSecret: plantshop-secret` makes the chart skip its Secret; that Secret (`DB_USERNAME`, `DB_PASSWORD`) is created by hand in namespace `plantshop`. Recreating it with a new password requires wiping the DB PVCs.
- A release changes only `catalogService.image.tag` (each service can override the global `image.tag`).
- Argo CD runs Helm `pre-upgrade` hooks as PreSync on every sync and ignores `helm test`, so `values-minikube.yaml` disables both (`hooks.preUpgradeCheck.enabled`, `tests.enabled`); the Ingress is disabled too (no address → Progressing forever). Access is via port-forward: Argo CD `svc/argocd-server 8080:443`, app `svc/plantshop-api-gateway 8081:80`.
- Changes to `charts/plantshop` should be checked with `helm lint charts/plantshop --strict -f charts/plantshop/values-minikube.yaml` and `helm template`; bump `version` in `Chart.yaml` on every chart change.
- Tooling: `~/bin/argocd.exe` (CLI), `~/bin/helm`, `~/bin/kubectl`, minikube `--cpus=4 --memory=10g` (WSL2 limited to 12 GB in `%UserProfile%\.wslconfig`; host has 15.7 GB, so close IntelliJ during demos). **`minikube start --memory=...` on an existing profile keeps the old limit**: check `docker stats --no-stream minikube` shows `/ 10GiB`, otherwise `minikube delete` first; a 7 GiB limit makes the whole cluster thrash (slow pulls, failed startup probes). Every Spring pod requests 320Mi (limit 640Mi), so memory is tight with extra catalog replicas.

## Lab 6: progressive delivery with Argo Rollouts (done)

**catalog-service** is deployed as an Argo Rollout with a canary strategy; every other service stays a Deployment. Report: `docs/DevOps_lab_6.md`; live-demo commands: `docs/lab6-defense.md`.

- **Controller:** `argocd-apps/argo-rollouts.yaml`, Helm chart `argo-rollouts` 2.43.12 (controller v1.10.2) from `https://argoproj.github.io/argo-helm`, namespace `argo-rollouts`, `sync-wave: "-1"`, `ServerSideApply=true` (large CRDs), in-cluster dashboard disabled. `argocd-apps/plantshop.yaml` has `syncPolicy.retry` so a sync that ran before the CRDs heals itself. kubectl plugin: `~/bin/kubectl-argo-rollouts.exe` v1.10.2.
- **Chart:** `templates/catalog-service-rollout.yaml` renders when `catalogService.rollout.enabled`; `catalog-service-deployment.yaml` renders only otherwise (never both with one selector). Both include the shared pod template `plantshop.catalogService.podTemplate` from `templates/_catalog-service-pod.tpl`: edit the pod there, not in either kind. `rollout.strategy` is passed through `toYaml` as the Rollout's `spec.strategy`. Defaults in `values.yaml` are `rollout.enabled: false` (dev/prod values need no CRDs). Chart version 0.3.0.
- **Strategy (`values-minikube.yaml`):** `replicaCount: 4`, canary `maxSurge: 1`, `maxUnavailable: 0`, steps `setWeight 25` → `pause: {}` → `50` → `pause 30s` → `75` → `pause 30s`. With 4 pods each step is exactly one more new pod; memory (≈5.4 of 7 GB used) does not fit more. Canary over blue-green because api-gateway reaches catalog through **Eureka** (`lb://catalog-service`), so blue-green preview pods would get real traffic before promote; there is no traffic router, so weight = pod share (measured 30/10 at 25% both via the Service and via the gateway).
- **Release:** change only the 4-space `    tag:` line under `catalogService` in `values-minikube.yaml`, commit, push, refresh. A Rollout starts only on a pod template change. Watch: `kubectl argo rollouts get rollout plantshop-catalog-service -n plantshop --watch`; Argo CD Health is `Suspended` on a pause. Show the split from a pod inside the cluster (port-forward pins one pod): `kubectl -n plantshop run curl --rm -it --image=curlimages/curl -- sh -c 'while true; do curl -s http://plantshop-api-gateway/api/catalog/version; echo; sleep 0.5; done'` (prefix `MSYS_NO_PATHCONV=1` in Git Bash if the path gets mangled). `kubectl argo rollouts promote|abort|retry rollout ...` act on status, so selfHeal does not undo them.
- **Abort semantics:** after `abort` the Rollout is Degraded on the stable version and Argo CD shows **Synced + Degraded** (spec still matches Git). Finish with `git revert` of the release commit; going back to the stable tag reuses the existing ReplicaSet (same pod-template hash), so it is Healthy in seconds with no new pods.
- Git history of the lab: `a334f8c` controller, `8e0a6da` Deployment → Rollout, `98e9a25` release 1.2.0, `ab07d1e` release 1.3.0 (aborted), `350b63f` its revert; the defense demo then added `f056c2d`…`f25ca87`. State at the end of Lab 6: catalog 1.2.0, all Applications Synced/Healthy.
- Both versions share the one catalog DB during a canary, so a release must not carry a Flyway migration the old version cannot run against.

## Lab 7: observability and metric-based rollback (done)

Goal: metrics, JSON logs and a Grafana dashboard for the app, and a canary of catalog-service that aborts itself when an `AnalysisTemplate` (Prometheus success rate ≥ 95%) fails. Task: 1) kube-prometheus-stack + Loki (single binary) + Grafana Alloy as Argo CD Applications, all pods in `monitoring` Running; 2) `/metrics` (request counter by method/route/status + duration histogram, low-cardinality labels) and a `ServiceMonitor` in the chart, target UP; 3) JSON logs to stdout (time, level, message, method, route, status, duration), 5xx filter in LogQL; 4) dashboard (RPS, 5xx share, p95, pod CPU/memory, Loki logs panel) stored as JSON in the chart, ConfigMap labelled `grafana_dashboard: "1"`; 5) load generator + one more visible change (replicas via Git, deleted pod, error or slow endpoint); 6) AnalysisTemplate in the canary, `FAIL_RATE` bad behaviour, bad release via Git → AnalysisRun Failed → auto abort → `git revert`; 6d optional `PrometheusRule` (5xx > 5%) Firing. Defense: live demo + an arbitrary PromQL/LogQL query from the teacher. Write the report to `docs/DevOps_lab_7.md` and the demo runbook to `docs/lab7-defense.md`, like Labs 5/6.

**State (2026-11-19).** All 6 Applications Synced/Healthy on `lab/DevOps_lab_7`; catalog 1.6.0, `FAIL_RATE: "0"`, chart 0.4.1. Report + theory: `docs/DevOps_lab_7.md`; live-demo runbook: `docs/lab7-defense.md` (defense releases 1.7.0, already pushed and pre-pulled, then `FAIL_RATE=0.5` → auto-abort → `git revert`). Rehearsal commits: `2f16bf2` good release 1.6.0 (AnalysisRun Successful), `ea87e65` bad release (Failed, values 0.92/0.86, abort ≈35 s into analysis), `5526e63` revert (Healthy in 9 s). Measured under `hey -c 20`: 541 RPS, p95 80 ms, catalog CPU 1.9 cores. `.scannerwork/` is a SonarScanner leftover of the other course: never commit it.

**Resources.** minikube 4 CPU / 10 GB (see Tooling), ≈7.4 GiB used with everything running. Cluster recreation: `docs/lab7-defense.md` §1 (Argo CD, `plantshop-secret`, `grafana-admin`, root). `values-minikube.yaml` sets `probes.startupFailureThreshold: 72` (7 JVMs starting together need > 3 min) and catalog `progressDeadlineSeconds: 300`. `checksum/config` hashes only the ConfigMap `.data`, so a chart version bump no longer restarts every pod.

**1. Monitoring stack (`argocd-apps/`, one Application per chart, versions pinned from `helm search repo`):**
- `kps.yaml`: `kube-prometheus-stack` from `https://prometheus-community.github.io/helm-charts`, `fullnameOverride: kps` (services `kps-prometheus:9090`, `kps-grafana:80`, `kps-alertmanager:9093`), namespace `monitoring`, `sync-wave: "-1"`, `CreateNamespace=true` + **`ServerSideApply=true`** (huge CRDs). Values: `kubeEtcd/kubeControllerManager/kubeScheduler/kubeProxy.enabled: false`; `prometheusOperator.admissionWebhooks.enabled: false`, `tls.enabled: false`; `prometheus.prometheusSpec.retention: 2d` and `serviceMonitorSelectorNilUsesHelmValues`, `podMonitorSelectorNilUsesHelmValues`, `ruleSelectorNilUsesHelmValues: false` (pick up ServiceMonitors/rules without a `release` label); `grafana.admin.existingSecret: grafana-admin` (`userKey: admin-user`, `passwordKey: admin-password`), `grafana.sidecar.dashboards.searchNamespace: ALL`, `grafana.additionalDataSources`: Loki, `uid: loki`, `url: http://loki-gateway.monitoring`.
- `loki.yaml`: chart `loki` from **`https://grafana-community.github.io/helm-charts`** (community-maintained since March 2026), `deploymentMode: SingleBinary`, `loki.auth_enabled: false`, `commonConfig.replication_factor: 1`, `storage.type: filesystem`, schema `from "2024-01-01"`, `tsdb`, `filesystem`, `v13`, index `index_`/24h; `singleBinary.replicas: 1`, `read/write/backend.replicas: 0`; `chunksCache`, `resultsCache`, `lokiCanary`, `test`, `minio` disabled. `sync-wave: "-1"`.
- `alloy.yaml`: chart `alloy` from `https://grafana.github.io/helm-charts`, `controller: {type: deployment, replicas: 1}`, config in `alloy.configMap.content`: `discovery.kubernetes "pods"` → `discovery.relabel` (`namespace`, `pod`, `app` ← `app_kubernetes_io_name`) → `loki.source.kubernetes` → `loki.write` to `http://loki-gateway.monitoring/loki/api/v1/push`. **In this chart `app.kubernetes.io/name` is `plantshop` on every pod**, so also map `__meta_kubernetes_pod_label_app_kubernetes_io_component` to a `component` label (e.g. `component="catalog-service"`).
- Grafana password is never in Git (the chart would also regenerate a random one on every Argo CD render): `kubectl create namespace monitoring` and `kubectl -n monitoring create secret generic grafana-admin --from-literal=admin-user=admin --from-literal=admin-password=<password>`.
- Helm repos for `helm search repo`: `prometheus-community`, `grafana-community`, `grafana` (plus the existing `argo`).
- Commit the stack **separately** and wait for `kps`, `loki`, `alloy` Synced/Healthy (3–7 min) before adding a ServiceMonitor to the plantshop chart (its CRD must exist; `plantshop` has `syncPolicy.retry` anyway). Access: `kubectl -n monitoring port-forward svc/kps-grafana 3000:80`, `svc/kps-prometheus 9090:9090`, `svc/kps-alertmanager 9093`; check names with `kubectl -n monitoring get svc`.

**2–3. App changes (Spring Boot 3.5.6; scope: catalog-service, the Rollout; other services optional).**
- Metrics: add `io.micrometer:micrometer-registry-prometheus` to `catalog-service/pom.xml` (actuator is already there). Expose with `management.endpoints.web.exposure.include: health,info,prometheus` and `management.metrics.distribution.percentiles-histogram.http.server.requests: true`. Endpoint `/actuator/prometheus`; metrics `http_server_requests_seconds_count` / `_bucket` with labels `method`, `uri` (route template like `/api/catalog/plants/{id}`, low cardinality by design), `status`, `outcome`. Exclude actuator traffic in queries: `uri!~"/actuator.*"`.
- Config is centralized: these keys go into `config-service/src/main/resources/configurations/` (`application.yml` for all services or `catalog-service.yml`), which is **baked into the config-service image**. So a config change means a new `vladyslavv648/plantshop-config-service:<tag>` image and `configService.image.tag` in `values-minikube.yaml`; catalog pods read config only at startup (a new catalog release restarts them).
- JSON logs: `logging.structured.format.console: ecs` (Spring Boot ≥ 3.4). Per-request fields (method, route, status, `duration_ms`, version) come from a request-logging `HandlerInterceptor`/filter that puts them into MDC (ECS JSON includes MDC keys) and logs one line per request; skip `/actuator/**`. Level is in `log.level` (after `| json` in LogQL: `log_level`).
- `FAIL_RATE`: env var (default `0`) read by catalog-service; a `HandlerInterceptor` (so the `uri` tag is still the route template) returns 500 for that share of non-actuator requests. The pod template gets `env` from values: add `{{- with $c.env }}{{ toYaml . | nindent 8 }}{{- end }}` under `env:` in `_catalog-service-pod.tpl` (indent as in that file). Changing it changes the pod template → starts a Rollout, which is the "bad release via Git".
- Java needs a new catalog image (e.g. `1.5.0`, with `APP_VERSION`) built on host Docker and pushed; add unit tests for the interceptor; commit with the `cc-check.sh` workaround from Build & test.

**Chart changes (`charts/plantshop`, bump `version` to 0.4.0, defaults off in `values.yaml`, on in `values-minikube.yaml`):**
- `templates/catalog-service-servicemonitor.yaml` (if `catalogService.metrics.enabled`): selector = catalog `selectorLabels`, endpoint `port: http`, `path: /actuator/prometheus`, `interval: 15s`. The catalog Service port is already named `http`. Prometheus adds `namespace`, `service` (`plantshop-catalog-service`), `pod`, `job` labels. Optional: `podTargetLabels: [rollouts-pod-template-hash]` to analyse only canary pods.
- `templates/catalog-service-dashboard.yaml`: ConfigMap with label `grafana_dashboard: "1"`, data `{{ .Files.Get "dashboards/plantshop.json" | indent 4 }}`; the exported JSON lives in `charts/plantshop/dashboards/`. Build the dashboard in Grafana, export (Share → Export → Save to file), commit, delete the hand-made one and see it return from Git.
- `templates/catalog-service-analysistemplate.yaml` (if `catalogService.rollout.analysis.enabled`): name `plantshop-catalog-service-success-rate`, `interval: 30s`, `count: 4`, `failureLimit: 1`, `successCondition: result[0] >= 0.95`, Prometheus `address: http://kps-prometheus.monitoring:9090`, query `sum(rate(http_server_requests_seconds_count{<sel>,uri!~"/actuator.*",status!~"5.."}[1m])) / sum(rate(http_server_requests_seconds_count{<sel>,uri!~"/actuator.*"}[1m]))` with `<sel>` from values, e.g. `namespace="plantshop",service="plantshop-catalog-service"`. Argo Rollouts `{{args.x}}` must be escaped for Helm as `{{ "{{args.x}}" }}`.
- Strategy in `values-minikube.yaml`: replace the manual `pause: {}` with `setWeight 25` → `pause 30s` (metrics accumulate) → `analysis` (templateName above) → `setWeight 50/75` with short pauses. With 4 pods and `FAIL_RATE: "0.5"` on the one canary pod, ≈ 12.5% errors → below 95% → AnalysisRun Failed → abort. Analysis lasts ~2 min; keep an eye on `progressDeadlineSeconds: 120` during it.
- Optional `templates/catalog-service-prometheusrule.yaml`: alert on 5xx share > 5% `for: 1m`; to see Firing it may need `rollout.analysis.enabled: false` and a full bad release.

**4–5. Queries and load (namespace is `plantshop`, not the instructions' `myapp`):**
```
sum(rate(http_server_requests_seconds_count{namespace="plantshop",uri!~"/actuator.*"}[1m]))                     # RPS
sum(rate(http_server_requests_seconds_count{namespace="plantshop",status=~"5.."}[1m])) / sum(rate(http_server_requests_seconds_count{namespace="plantshop"}[1m]))
histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{namespace="plantshop",uri!~"/actuator.*"}[1m])))
sum by (pod) (rate(http_server_requests_seconds_count{namespace="plantshop",status=~"5.."}[1m]))                # 5xx per pod
sum by (pod) (rate(container_cpu_usage_seconds_total{namespace="plantshop",container=""}[1m]))
sum by (pod) (container_memory_working_set_bytes{namespace="plantshop",container=""})
{namespace="plantshop", component="catalog-service"} | json | status >= 500                                       # LogQL
```
Load from namespace `default`, hitting catalog directly (`http://plantshop-catalog-service.plantshop:8081/api/catalog/plants`) or via `http://plantshop-api-gateway.plantshop/api/catalog/plants`: `kubectl run hey --rm -it --restart=Never --image=williamyeh/hey -- -z 3m -c 20 <url>`; background: `kubectl create deployment load --image=curlimages/curl -- sh -c 'while true; do curl -s -o /dev/null <url>; sleep 0.05; done'` (`MSYS_NO_PATHCONV=1` in Git Bash). Without traffic the analysis is 0/0 = NaN and fails even for a good version.

**6. Demo flow:** Grafana (last 15 min, refresh 10s), `kubectl argo rollouts get rollout plantshop-catalog-service -n plantshop --watch` and background load open first. Good release (tag change) → AnalysisRun Successful. Bad release: set `FAIL_RATE: "0.5"` in `values-minikube.yaml`, push, refresh → setWeight 25 → AnalysisRun Failed → Rollout Degraded on stable (Argo CD Synced + Degraded) → `kubectl -n plantshop get analysisrun`, `describe analysisrun <name>` → 5xx only from one pod on the dashboard, Loki error lines only from the new ReplicaSet's pods → `git revert <commit>` and push.

## Lab branches

DevOps course labs: `k8s-lab` (Lab 3), `helm-lab` (Lab 4), `lab/DevOps_lab_5` (Lab 5), `lab/DevOps_lab_6` (Lab 6), `lab/DevOps_lab_7` (Lab 7, current, branched from Lab 6). `gitops-lab` is an older attempt at Lab 5, not used by the cluster. Branches `lab/Lab_*` belong to a different (software quality) course. `scripts/k8s-demo.sh` and `scripts/helm-demo.sh` record Lab 3/4 demos into `docs/`.
