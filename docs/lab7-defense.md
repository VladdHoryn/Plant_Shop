# ЛР-7: шпаргалка до захисту (observability і відкат за метриками)

Репозиторій: `https://github.com/VladdHoryn/Plant_Shop.git`, гілка `lab/DevOps_lab_7`
(вона ж `targetRevision` у `root` і `plantshop`). Повний звіт і теорія: `docs/DevOps_lab_7.md`.
Усі команди виконуються в **Git Bash з кореня репозиторію**; `argocd`, `kubectl`,
`kubectl argo rollouts`, `helm`, `minikube` лежать у `~/bin`.

| Що | Значення |
|---|---|
| Rollout / Service | `plantshop-catalog-service` (порт 8081), namespace `plantshop` |
| AnalysisTemplate | `plantshop-catalog-service-success-rate` (success rate ≥ 0.95, 4 × 30 с, failureLimit 1) |
| Prometheus / Grafana / Alertmanager / Loki | `svc/kps-prometheus:9090`, `svc/kps-grafana:80`, `svc/kps-alertmanager:9093`, `svc/loki-gateway:80` (namespace `monitoring`) |
| Дашборд | Grafana → Dashboards → **Plant Shop: catalog-service** |
| Стан перед захистом | catalog **1.6.0**, `FAIL_RATE: "0"`, усі 6 Application Synced / Healthy |
| Образ для «хорошого» релізу | `vladyslavv648/plantshop-catalog-service:1.7.0` (уже в Docker Hub і в minikube) |

План показу: **ціль у Prometheus** → **навантаження і дашборд** → **логи в Loki** → **хороший реліз
1.7.0** (AnalysisRun Successful) → **поганий реліз `FAIL_RATE=0.5`** (AnalysisRun Failed → abort →
помилки одного пода на дашборді й у Loki → `git revert`).

---

## 1. Підготовка (за 15–20 хв до захисту)

0. Закрити важкі програми (IntelliJ IDEA, Excel): minikube бере 10 ГБ з 16.
1. Запустити **Docker Desktop**, дочекатися «Engine running».
2. Кластер:
   ```bash
   export PATH="$HOME/bin:$PATH"
   minikube start                                  # профіль уже має --cpus=4 --memory=10g
   docker stats --no-stream minikube               # має бути "/ 10GiB"
   kubectl -n argocd get applications              # alloy, argo-rollouts, kps, loki, plantshop, root: Synced/Healthy
   kubectl -n monitoring get pods                  # усі Running
   kubectl -n plantshop get pods                   # усі 1/1 Running (JVM стартують 3–5 хв)
   ```
   Якщо `plantshop` довго Unknown/OutOfSync: `kubectl -n argocd annotate application plantshop argocd.argoproj.io/refresh=normal --overwrite`.

   **Якщо minikube видалено** (`minikube status` → «No such container»), відновлення займає ≈10 хв:
   ```bash
   minikube start --cpus=4 --memory=10g            # для НОВОГО профілю; на наявному --memory ігнорується!
   kubectl create namespace argocd
   kubectl apply -n argocd --server-side --force-conflicts -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
   kubectl create namespace plantshop
   kubectl -n plantshop create secret generic plantshop-secret --from-literal=DB_USERNAME=plantshop --from-literal=DB_PASSWORD="$(openssl rand -hex 16)"
   kubectl create namespace monitoring
   kubectl -n monitoring create secret generic grafana-admin --from-literal=admin-user=admin --from-literal=admin-password="$(openssl rand -hex 12)"
   kubectl -n argocd wait --for=condition=Ready pod --all --timeout=600s
   kubectl apply -f bootstrap/root.yaml
   minikube ssh -- docker pull vladyslavv648/plantshop-catalog-service:1.7.0   # щоб реліз не чекав pull
   ```
3. Git і кластер у вихідному стані:
   ```bash
   git status && git pull                          # чисто, гілка lab/DevOps_lab_7
   grep -n -A1 'name: FAIL_RATE' charts/plantshop/values-minikube.yaml   # value: "0"
   grep -n '^    tag:' charts/plantshop/values-minikube.yaml              # catalogService tag "1.6.0"
   kubectl argo rollouts get rollout plantshop-catalog-service -n plantshop   # Healthy, 1.6.0
   ```
4. Відкрити термінали (Git Bash, у кожному `export PATH="$HOME/bin:$PATH"`):

   | # | Термінал | Команда |
   |---|---|---|
   | T1 | Grafana | `kubectl -n monitoring port-forward svc/kps-grafana 3000:80` → http://localhost:3000 |
   | T2 | Prometheus | `kubectl -n monitoring port-forward svc/kps-prometheus 9090:9090` → http://localhost:9090 |
   | T3 | Argo CD | `kubectl -n argocd port-forward svc/argocd-server 8080:443` → https://localhost:8080 |
   | T4 | Стан Rollout (не закривати) | `kubectl argo rollouts get rollout plantshop-catalog-service -n plantshop --watch` |
   | T5 | Робочий: git, kubectl | — |
   | (T6) | Alertmanager, за бажанням | `kubectl -n monitoring port-forward svc/kps-alertmanager 9093` |

   Паролі:
   ```bash
   kubectl -n monitoring get secret grafana-admin -o jsonpath='{.data.admin-password}' | base64 -d; echo    # Grafana, логін admin
   kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d; echo # Argo CD, логін admin
   ```
5. **Фонове навантаження** (T5; без нього аналіз = NaN і падає навіть для доброї версії):
   ```bash
   MSYS_NO_PATHCONV=1 kubectl create deployment load --image=curlimages/curl -- sh -c 'while true; do curl -s -o /dev/null http://plantshop-catalog-service.plantshop:8081/api/catalog/plants; curl -s -o /dev/null http://plantshop-catalog-service.plantshop:8081/api/catalog/categories; sleep 0.1; done'
   ```
6. Grafana: відкрити дашборд **Plant Shop: catalog-service**, діапазон **Last 15 minutes**, refresh **10s**.

---

## 2. Демонстрація

### 2.1 Стек у monitoring і Argo CD (п. 1)
```bash
kubectl -n argocd get applications
kubectl -n monitoring get pods
cat argocd-apps/kps.yaml | head -30          # показати: chart, targetRevision, ServerSideApply, existingSecret
```
Пояснити: по одному Application на чарт, версії зафіксовані, wave -1, пароль Grafana у Secret поза Git.

### 2.2 Ціль у Prometheus і запит до метрики (п. 2)
Prometheus → **Status → Targets** → `serviceMonitor/plantshop/plantshop-catalog-service/0`: 4 цілі **UP**
(`http://<pod-ip>:8081/actuator/prometheus`). Потім **Graph**:
```promql
sum by (uri, status) (rate(http_server_requests_seconds_count{namespace="plantshop",service="plantshop-catalog-service",uri!~"/actuator.*"}[1m]))
```
Показати файли: `charts/plantshop/templates/catalog-service-servicemonitor.yaml` (порт `http`, шлях
`/actuator/prometheus`) і сирі метрики:
```bash
MSYS_NO_PATHCONV=1 kubectl exec deploy/load -- curl -s http://plantshop-catalog-service.plantshop:8081/actuator/prometheus | grep '^http_server_requests_seconds_count'
```

### 2.3 Навантаження і дашборд (п. 4–5)
```bash
MSYS_NO_PATHCONV=1 kubectl run hey --rm -it --restart=Never --image=williamyeh/hey -- -z 2m -c 20 http://plantshop-catalog-service.plantshop:8081/api/catalog/plants
```
На дашборді: RPS ≈16 → ≈500, p95 ≈5 мс → ≈80 мс, CPU catalog-подів росте (очікування з репетиції:
541 RPS, p95 80 мс, 1.9 ядра). **Друга зміна:** `kubectl -n plantshop delete pod <один catalog-под>` →
на панелях CPU/пам'яті зникає под і з'являється новий (Rollout відновлює 4 репліки).

Дашборд як код: показати `charts/plantshop/dashboards/plantshop.json`, `templates/grafana-dashboard.yaml`
(мітка `grafana_dashboard: "1"`), і що він повертається з Git:
```bash
kubectl -n plantshop delete configmap plantshop-dashboard     # Argo CD (selfHeal) відновлює за ~1 с, дашборд знову в Grafana
```
(Видалити його в UI Grafana не дасть: provisioned dashboard.)

### 2.4 Логи в Loki (п. 3)
Grafana → **Explore** → джерело **Loki**:
```logql
{namespace="plantshop", component="catalog-service"} | json
{namespace="plantshop", component="catalog-service"} | json | status >= 500
{namespace="plantshop", component="catalog-service"} | json | duration_ms > 50 | line_format "{{.route}} {{.duration_ms}}ms"
```
Розкрити один рядок: поля `@timestamp`, `log_level`, `message`, `method`, `route`, `status`, `duration_ms`, `version`.

### 2.5 Хороший реліз 1.7.0 → AnalysisRun Successful
```bash
sed -i 's/^    tag: "1.6.0"/    tag: "1.7.0"/' charts/plantshop/values-minikube.yaml
git diff
git commit -am "Release catalog-service 1.7.0" && git push
argocd app get plantshop --refresh >/dev/null 2>&1 || kubectl -n argocd annotate application plantshop argocd.argoproj.io/refresh=normal --overwrite
```
T4: `setWeight 25` → Paused (30 с) → AnalysisRun Running → **Successful** → 50 → 75 → **Healthy** (≈5 хв).
```bash
kubectl -n plantshop get analysisrun
```

### 2.6 Поганий реліз → автоматичний abort (п. 6)
```bash
sed -i '/name: FAIL_RATE/{n;s/value: "0"/value: "0.5"/}' charts/plantshop/values-minikube.yaml
git diff                                        # value: "0" → "0.5"
git commit -am "Release catalog-service with FAIL_RATE=0.5" && git push
kubectl -n argocd annotate application plantshop argocd.argoproj.io/refresh=normal --overwrite
```
Очікувано (з репетиції): ≈40 с старт canary-пода → pause 30 с → AnalysisRun Running → через ≈35 с
**Failed** (значення ≈0.92, ≈0.86 < 0.95) → `RolloutAborted ... failed (2) > failureLimit (1)` → **Degraded**,
трафік на stable. Argo CD: **Synced + Degraded**.
```bash
kubectl -n plantshop get analysisrun
kubectl -n plantshop describe analysisrun <ім'я з ...-N-2>   # Measurements: Value [0.91..], Phase Failed; Message
```
Показати:
- дашборд: «5xx error share» сплеск, «5xx per second by pod» — **лише один под** (canary), «Success rate» нижче 95%;
- Loki: `{namespace="plantshop", component="catalog-service"} | json | status >= 500` → рядки тільки з пода
  нового ReplicaSet (хеш у назві пода); по подах:
  `sum by (pod) (count_over_time({namespace="plantshop", component="catalog-service"} | json | status >= 500 [5m]))`.

Завершити відкат через Git:
```bash
git revert --no-edit HEAD && git push
kubectl -n argocd annotate application plantshop argocd.argoproj.io/refresh=normal --overwrite
```
Rollout Healthy за секунди (той самий pod-template-hash, що в stable ReplicaSet, нових подів немає).

### 2.7 (За бажанням) Алерт Firing
Під час кроку 2.6 abort випереджає `for: 1m`. Щоб показати Firing: у `values-minikube.yaml` поставити
`catalogService.rollout.analysis.enabled: false` (і прибрати крок `analysis` зі steps) + `FAIL_RATE: "0.5"`,
комітнути, дочекатися 100% → Prometheus → **Alerts**: `CatalogHighErrorRate` Pending → **Firing** (≈1–2 хв),
Alertmanager http://localhost:9093 → `git revert` комітів.

---

## 3. Після захисту
```bash
kubectl delete deployment load
minikube stop
```
Якщо демо додало коміти 1.7.0 / FAIL_RATE / revert — вони залишаються в історії (так і має бути в GitOps).
Наступного разу «хороший» реліз можна робити на 1.6.0 або зібрати новий тег.

---

## 4. Запити, які можуть попросити написати

PromQL (`C` = `namespace="plantshop",service="plantshop-catalog-service",uri!~"/actuator.*"`):
```promql
up{namespace="plantshop"}                                                         # стан цілей
sum(rate(http_server_requests_seconds_count{C}[1m]))                              # RPS
sum by (status) (rate(http_server_requests_seconds_count{C}[1m]))                 # RPS за статусом
sum by (uri) (rate(http_server_requests_seconds_count{C}[1m]))                    # RPS за маршрутом
sum(rate(http_server_requests_seconds_count{C,status=~"5.."}[1m])) / sum(rate(http_server_requests_seconds_count{C}[1m]))   # частка 5xx
histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{C}[1m])))                  # p95
histogram_quantile(0.99, sum by (le, uri) (rate(http_server_requests_seconds_bucket{C}[5m])))             # p99 по маршрутах
sum(rate(http_server_requests_seconds_sum{C}[1m])) / sum(rate(http_server_requests_seconds_count{C}[1m])) # середня затримка
sum by (pod) (rate(http_server_requests_seconds_count{C,status=~"5.."}[1m]))      # 5xx по подах
sum by (rollouts_pod_template_hash) (rate(http_server_requests_seconds_count{C}[1m]))   # трафік stable vs canary
sum(increase(http_server_requests_seconds_count{C}[10m]))                         # кількість запитів за 10 хв
sum by (pod) (rate(container_cpu_usage_seconds_total{namespace="plantshop",container=""}[1m]))   # CPU, ядра
sum by (pod) (container_memory_working_set_bytes{namespace="plantshop",container=""})            # пам'ять
topk(3, sum by (pod) (container_memory_working_set_bytes{namespace="plantshop",container=""}))   # 3 найбільші
sum by (pod) (jvm_memory_used_bytes{namespace="plantshop",area="heap"})          # heap JVM
kube_deployment_status_replicas_available{namespace="plantshop"}                 # з kube-state-metrics
```
LogQL:
```logql
{namespace="plantshop"}                                                           # усі логи namespace
{namespace="plantshop", component="catalog-service"} |= "request"                 # фільтр рядка
{namespace="plantshop", component="catalog-service"} | json | log_level="ERROR"
{namespace="plantshop", component="catalog-service"} | json | status >= 500
{namespace="plantshop", component="catalog-service"} | json | route="/api/catalog/plants" | duration_ms > 20
sum by (pod) (count_over_time({namespace="plantshop", component="catalog-service"} | json | status >= 500 [1m]))
sum by (status) (rate({namespace="plantshop", component="catalog-service"} | json | __error__="" [1m]))
sum by (version) (count_over_time({namespace="plantshop", component="catalog-service"} | json | status >= 500 [5m]))
```
Примітка: `status` у логах — поле після `| json`, а не мітка Loki (мітки: `namespace`, `pod`, `container`,
`app`, `component`). У чарті `app="plantshop"` на всіх подах, тому сервіс обирається через `component`.

## 5. Типові проблеми під час показу

| Симптом | Причина / що робити |
|---|---|
| Поди Pending / JVM рестартують, load високий | `docker stats minikube` показує `/ 7GiB`? Профіль зі старою пам'яттю: `minikube delete` + start з `--memory=10g` (розділ 1) |
| AnalysisRun Error / Failed для хорошої версії | немає трафіку (NaN): перевірити `kubectl get deploy load`; перевірити запит вручну в Prometheus |
| Rollout Degraded `ProgressDeadlineExceeded` | canary-под не встиг стати Ready за 300 с; дочекатися — Rollout продовжить сам |
| Порожні логи в Explore | вибрано не Loki або діапазон часу; спершу `{namespace="plantshop"}` |
| port-forward обірвався | перезапустити відповідну команду T1–T3 |
| Argo CD не бачить новий коміт | `kubectl -n argocd annotate application plantshop argocd.argoproj.io/refresh=normal --overwrite` |
