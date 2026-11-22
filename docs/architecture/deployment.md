---
owner: VladdHoryn
status: published
review_date: 2027-02-20
tags:
  - architecture
  - ops
---

# Deployment runbook: Kubernetes and Helm

Operational commands for the two cluster deployments. Topology, environments, startup order, release and rollback
policy are specified in the [ISD](isd.md); this page is the step-by-step runbook (moved from the README in Lab 11,
kept in English as it was written).

## Running on Kubernetes (minikube)

All manifests are in [`k8s/`](https://github.com/VladdHoryn/Plant_Shop/tree/master/k8s),
one object per file, everything in the `plantshop` namespace
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

> **Known gap:** user-service and user-db were added after this deployment and are not in `k8s/` or the Helm
> chart yet, so `/api/users/**`, `/api/marketing/**` and `/api/analytics/**` do not work on Kubernetes. Use Docker
> Compose for the privacy features. Tracked as risk R1 in [ISD](isd.md#11-відомі-ризики-інфраструктури).

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
to [`docs/k8s-report.md`](https://github.com/VladdHoryn/Plant_Shop/blob/master/docs/k8s-report.md).

### Removing everything

```bash
kubectl delete -f k8s/          # or: kubectl delete namespace plantshop
```

minikube's storage provisioner names its directories after `<namespace>/<pvc>` and does not always remove them.
If the next deployment fails with `password authentication failed`, the databases found old data created with a
different password: run `minikube ssh -- sudo rm -rf /tmp/hostpath-provisioner/plantshop` and apply again.

## Helm chart

[`helm/plantshop`](https://github.com/VladdHoryn/Plant_Shop/tree/master/helm/plantshop) packages the same application as
`k8s/`: the same 32 objects, one template
per object, with names built from the release name (`<release>-plantshop-<component>`, or `<release>-<component>`
when the release name already contains "plantshop"), so the chart can be installed several times in one cluster.
The Namespace is not a template; `helm install --create-namespace` creates it.

```text
helm/plantshop/
├── Chart.yaml            name, description, version, appVersion
├── values.yaml           defaults = development environment
├── values-dev.yaml       dev, pinned explicitly
├── values-prod.yaml      prod overrides
└── templates/
    ├── _helpers.tpl      names, labels, image, init container and probe templates
    ├── NOTES.txt         how to reach the application after install
    ├── configmap.yaml, secret.yaml, api-gateway-ingress.yaml
    ├── <service>-deployment.yaml, <service>-service.yaml      (7 Spring services)
    ├── <db>-db-deployment.yaml, -service.yaml, -pvc.yaml      (4 PostgreSQL)
    ├── kafka-deployment.yaml, -service.yaml, -pvc.yaml
    ├── pre-upgrade-check-job.yaml                              (hook)
    └── tests/test-api.yaml                                     (helm test)
```

### Install

```bash
# images (once): every service as 1.0.0 and 1.1.0, built into minikube
./scripts/build-images.sh

# development (equals the defaults)
helm install plantshop helm/plantshop -n plantshop-dev --create-namespace \
  -f helm/plantshop/values-dev.yaml

# production-like; the DB password comes from outside the repository
helm install plantshop helm/plantshop -n plantshop-prod --create-namespace \
  -f helm/plantshop/values-prod.yaml --set database.password="$PLANTSHOP_DB_PASSWORD"

helm test plantshop -n plantshop-dev           # release test
helm uninstall plantshop -n plantshop-dev      # removes every object of the release
```

`NOTES.txt` prints the access commands after the install (NodePort, Ingress or LoadBalancer, depending on the values).

Preview without a cluster: `helm template plantshop helm/plantshop -f helm/plantshop/values-prod.yaml`.
Lint: `helm lint helm/plantshop --strict -f helm/plantshop/values-prod.yaml`.

### `version` vs `appVersion`

| Field | Value | Meaning in this project |
|---|---|---|
| `version` | `0.1.0` | Version of the **chart**: templates, helpers and default values. Bumped whenever the chart changes, even if the application does not (e.g. a new probe or a new value). It names the package `plantshop-0.1.0.tgz` and goes into the `helm.sh/chart` label. |
| `appVersion` | `"1.0.0"` | Version of the **application** the chart deploys by default: it is the image tag of every service when `image.tag` and `<service>.image.tag` are empty, and the `app.kubernetes.io/version` label. A new application release (new images) bumps it, and usually the chart version too. |

So chart `0.1.0` can deploy application `1.0.0` (default) or `1.1.0` (`--set image.tag=1.1.0`) without changing.

### Value precedence

From lowest to highest; a later source overrides only the keys it sets, maps are merged:

1. `values.yaml` inside the chart (defaults, dev)
2. `-f` / `--values` files, **in the order given**: in `-f values-prod.yaml -f values-dev.yaml` dev wins
3. `--set`, `--set-string`, `--set-file` (in the order given), always above every `-f` file

Example: `catalogService.replicaCount` is `2` in values.yaml, `3` with `-f values-prod.yaml`, and `5` with
`-f values-prod.yaml --set catalogService.replicaCount=5`. On `helm upgrade`, values from the previous revision are
**not** kept unless `--reuse-values` is given, so pass the environment file every time.

### Environments

| | dev (`values.yaml` / `values-dev.yaml`) | prod (`values-prod.yaml`) |
|---|---|---|
| image tag | `1.0.0` (= appVersion) | `1.1.0` |
| replicas: api-gateway / catalog-service | 2 / 2 | 3 / 3 |
| Spring resources (requests → limits) | 100m, 320Mi → 1 CPU, 640Mi | 200m, 320Mi → 2 CPU, 768Mi |
| PostgreSQL / Kafka resources | small | larger limits, bigger Kafka heap |
| gateway access | NodePort Service + Ingress `plantshop.local` | LoadBalancer Service, Ingress disabled |
| JVM | C1 only (fast startup) | full JIT |
| shop name | `Plant Shop (dev)` | `Plant Shop` |

### Values

| Parameter | Default (dev) | Description |
|---|---|---|
| `nameOverride` / `fullnameOverride` | `""` | replace the chart name / the whole `<release>-plantshop` prefix |
| `image.repositoryPrefix` | `plantshop` | images are `<prefix>/<service>` |
| `image.tag` | `""` → appVersion | tag of every Spring service |
| `image.pullPolicy` | `IfNotPresent` | images are built into minikube, never pulled |
| `resources` | 100m/320Mi → 1/640Mi | default resources of each Spring service |
| `probes.startupFailureThreshold` | `36` | × 5 s = time a JVM may take to start |
| `rollingUpdate.maxSurge` / `.maxUnavailable` / `.minReadySeconds` | `1` / `0` / `15` | rolling update of the Spring services |
| `config.shopName` | `Plant Shop (dev)` | ConfigMap `SHOP_NAME` |
| `config.shopCurrency` | `UAH` | ConfigMap `SHOP_CURRENCY` |
| `config.paymentMaxAmount` | `10000` | ConfigMap `PAYMENT_MAX_AMOUNT`: larger payments are declined |
| `config.javaToolOptions` | `-XX:MaxRAMPercentage=70 ...` | ConfigMap `JAVA_TOOL_OPTIONS` |
| `database.username` | `plantshop` | Secret `DB_USERNAME` |
| `database.password` | `""` | Secret `DB_PASSWORD`. Never stored in the repository: pass with `--set`. Empty = keep the password already in the release's Secret, or generate a random one on the first install |
| `<service>.replicaCount` | 2 for `apiGateway`, `catalogService`; 1 for the rest | `<service>` = `configService`, `discoveryService`, `apiGateway`, `catalogService`, `inventoryService`, `orderService`, `paymentService` |
| `<service>.image.repository` / `.tag` | `""` | per-service override of the image |
| `<service>.port` | 8888, 8761, 8080, 8081 … 8084 | container port |
| `<service>.service.type` / `.port` | `ClusterIP`, same as the container port | Service of the component |
| `<service>.resources` | `{}` | per-service override of `resources` |
| `apiGateway.service.type` / `.port` | `NodePort` / `80` | entry point of the application |
| `apiGateway.service.nodePort` | `""` | fixed node port; empty = assigned by Kubernetes, so two releases never clash |
| `apiGateway.preStopSleepSeconds` | `5` | time to drain the gateway pod from the Service endpoints |
| `ingress.enabled` | `true` | create the Ingress |
| `ingress.className` / `.host` / `.annotations` | `nginx` / `plantshop.local` / `{}` | Ingress settings |
| `postgres.image.repository` / `.tag` | `postgres` / `16-alpine` | image of the 4 databases |
| `postgres.resources` | 50m/96Mi → 500m/256Mi | resources of each database |
| `postgres.persistence.enabled` | `true` | PVC per database; `false` = emptyDir |
| `postgres.persistence.size` / `.storageClass` | `1Gi` / `""` (default class) | |
| `kafka.image.repository` / `.tag` | `apache/kafka` / `3.9.0` | |
| `kafka.clusterId` / `.heapOpts` / `.numPartitions` | fixed id / `-Xms256m -Xmx256m` / `3` | broker settings |
| `kafka.resources` | 100m/384Mi → 1/768Mi | |
| `kafka.persistence.enabled` / `.size` / `.storageClass` | `true` / `1Gi` / `""` | PVC for the Kafka log directory |
| `waitImage.repository` / `.tag` | `busybox` / `1.36` | init containers that wait for dependencies |
| `hooks.preUpgradeCheck.enabled` | `true` | pre-upgrade/pre-rollback hook, see below |
| `tests.image.repository` / `.tag` | `curlimages/curl` / `8.10.1` | image of the `helm test` pod |

### Hook and release test (elective task: option b)

- **`pre-upgrade-check-job.yaml`** is a `pre-upgrade,pre-rollback` hook. Before Helm changes anything, a Job
  connects to all four databases with the release's credentials and prints the Flyway schema version of each.
  If a database is unreachable, the Job fails and Helm aborts the upgrade, leaving the running version untouched.
- **`tests/test-api.yaml`** is a `test` hook. `helm test` calls the gateway Service like a client: categories,
  a page of plants, the stock of plant 1, and `/api/catalog/info`, whose version must equal this release's
  catalog-service image tag.

Both are deleted when they succeed (`hook-succeeded`) because Helm never removes hook objects on `helm uninstall`.
A failed one is kept so you can read its log: `kubectl -n <ns> logs job/<release>-plantshop-pre-upgrade-check`
or `kubectl -n <ns> logs <release>-plantshop-test-api`.

### Demo and report

`./scripts/helm-demo.sh` runs the whole lab and records every command with its output in
[`docs/helm-report.md`](https://github.com/VladdHoryn/Plant_Shop/blob/master/docs/helm-report.md):

- the environment, and the Lab 3 check and removal;
- dev vs prod with `helm template`, and value precedence;
- lint, the comparison with `k8s/`, and the install;
- `helm list`, `status` and `get values`, and where the release state is stored;
- upgrades via `--set` and via a values file, history and rollback;
- the hook and the test, including an upgrade blocked by the hook;
- `helm uninstall`.

The committed `docs/helm-report.md` is **incomplete** (the recorded run stopped after the first step) and is marked
deprecated; run the script again to get a full transcript.
