# Лабораторна робота №7. Observability: метрики, логи, дашборди й відкат за метриками

**Дисципліна:** Технології DevOps
**Проєкт:** Plant Shop — інтернет-магазин рослин на мікросервісах (Java 21, Spring Boot 3.5, Spring Cloud, PostgreSQL, Kafka)
**Студент:** Горин Владислав (GitHub `VladdHoryn`, Docker Hub `vladyslavv648`)
**Репозиторій:** https://github.com/VladdHoryn/Plant_Shop, гілка `lab/DevOps_lab_7`

---

## 1. Мета роботи

Зробити поведінку застосунку **видимою**: метрики (Prometheus), логи (Loki) і дашборд (Grafana),
і використати ці дані, щоб **реліз сам відкотився**, якщо нова версія сипле помилками
(Argo Rollouts + AnalysisTemplate з запитом до Prometheus).

Вихідні дані: проєкт під керуванням Argo CD (ЛР-5) з canary-релізами `catalog-service` через
Argo Rollouts (ЛР-6). Усе нове теж встановлюється з Git через Argo CD.

---

## 2. Теорія

### 2.1 Monitoring vs observability, три стовпи

**Моніторинг** відповідає на заздалегідь відомі питання («чи живий сервіс?», «чи CPU > 80%?»).
**Observability (спостережуваність)** — властивість системи, яка дозволяє з її зовнішніх сигналів
відповісти на *нові*, заздалегідь не передбачені питання («чому саме у версії 1.6.0 повільніше
відповідає `/plants/{id}`?»). Три стовпи (three pillars):

| Сигнал | Що це | Інструмент у роботі | Сильна сторона |
|---|---|---|---|
| **Метрики** | числові часові ряди (лічильники, гістограми) з мітками | Prometheus | дешево зберігати, агрегувати, алертити; «скільки» і «як швидко» |
| **Логи** | дискретні події з контекстом (рядок/JSON) | Loki | деталі конкретного запиту; «що саме сталося» |
| **Трейси** | шлях одного запиту через сервіси (spans) | (не в цій роботі: Tempo/Jaeger) | «де саме в ланцюжку повільно» |

Метрики показують, **що** щось не так (сплеск 5xx), логи — **що саме** (які запити, який под, яка
версія). Дашборд поєднує обидва.

### 2.2 Методи RED і USE, «золоті сигнали»

- **RED** (для сервісів, що обробляють запити): **R**ate — запити за секунду, **E**rrors — частка
  помилок, **D**uration — розподіл затримки (p50/p95/p99). Перші три панелі дашборда — це RED.
- **USE** (для ресурсів): **U**tilization, **S**aturation, **E**rrors — CPU, пам'ять подів.
- **Four golden signals** (Google SRE): latency, traffic, errors, saturation = RED + насичення.

### 2.3 Prometheus

**Prometheus** — time-series база і система збору метрик за **pull-моделлю**: він сам періодично
(scrape interval, тут 15 с) робить HTTP GET на ендпоінт метрик кожної цілі (`/metrics`, у Spring —
`/actuator/prometheus`) і зберігає отримані значення з часовою міткою.

Формат експозиції (text format):
```
# HELP http_server_requests_seconds
# TYPE http_server_requests_seconds histogram
http_server_requests_seconds_count{method="GET",outcome="SUCCESS",status="200",uri="/api/catalog/plants"} 1532
http_server_requests_seconds_sum{...} 12.84
http_server_requests_seconds_bucket{...,le="0.05"} 1490
```

Часовий ряд = **ім'я метрики + унікальний набір міток** (labels). Кожна нова комбінація значень
міток — окремий ряд у пам'яті й на диску.

**Типи метрик:**

| Тип | Поведінка | Приклад | Як читати |
|---|---|---|---|
| Counter | тільки зростає (скидається при рестарті) | `http_server_requests_seconds_count` | завжди через `rate()` / `increase()` |
| Gauge | довільне значення вгору/вниз | `container_memory_working_set_bytes`, `jvm_memory_used_bytes` | як є, `avg/max` |
| Histogram | лічильники по «кошиках» `le` (≤ межі) + `_sum` + `_count` | `http_server_requests_seconds_bucket` | `histogram_quantile()` по `rate()` кошиків |
| Summary | квантилі, пораховані на клієнті | — | не агрегується між подами, тому тут не використовується |

**Чому гістограма, а не summary:** квантиль з summary рахує кожен под окремо, і «середнє p95 по
подах» математично не є p95 сервісу. Кошики гістограми — лічильники, їх можна підсумувати по
всіх подах (`sum by (le)`), і тоді `histogram_quantile` дає справжній p95 сервісу.

**Кардинальність.** Кількість рядів = добуток кількості значень усіх міток. Мітка з `id`
користувача або сирим шляхом (`/api/catalog/plants/1`, `/plants/2`, ...) створює новий ряд на
кожен запит і «вбиває» Prometheus (пам'ять, повільні запити). Тому мітки — лише з малою
кількістю значень: метод, **шаблон маршруту** (`/api/catalog/plants/{id}`), код статусу.
Spring Micrometer саме так і робить: тег `uri` — це шаблон з `@GetMapping`, а для запитів без
маршруту — `UNKNOWN`/`NOT_FOUND`.

**Іменування** (prometheus.io/docs/practices/naming): `snake_case`, базові одиниці (секунди,
байти) в імені (`_seconds`, `_bytes`), лічильники з суфіксом `_total` (у Micrometer-гістограми
таймера — `_count`).

### 2.4 PromQL — мінімум для захисту

| Конструкція | Значення |
|---|---|
| `metric{label="x", other=~"re.*", status!~"5.."}` | instant vector: вибір рядів за мітками (`=`, `!=`, `=~` regex, `!~`) |
| `metric[1m]` | range vector: усі точки за останню хвилину |
| `rate(counter[1m])` | середня швидкість зростання лічильника за секунду за 1 хв (враховує скидання при рестарті) |
| `increase(counter[5m])` | приріст за 5 хв (= rate × 300) |
| `sum by (status) (...)` | агрегація зі збереженням мітки `status`; `without (pod)` — навпаки |
| `a / b` | ділення векторів з однаковими мітками (vector matching) |
| `histogram_quantile(0.95, sum by (le) (rate(x_bucket[1m])))` | p95 з гістограми |
| `topk(3, ...)`, `avg`, `max`, `count` | інші агрегації |

Головне правило: **спочатку `rate`, потім `sum`** (rate від суми лічильників різних подів дає
сміття при рестарті одного з них).

Запити цієї роботи (namespace `plantshop`, сервіс `plantshop-catalog-service`, `C` нижче — це
`namespace="plantshop",service="plantshop-catalog-service",uri!~"/actuator.*"`):
```promql
# RPS
sum(rate(http_server_requests_seconds_count{C}[1m]))
# RPS за статусом
sum by (status) (rate(http_server_requests_seconds_count{C}[1m]))
# частка 5xx (0..1)
sum(rate(http_server_requests_seconds_count{C,status=~"5.."}[1m]))
  / sum(rate(http_server_requests_seconds_count{C}[1m]))
# p95 затримки (секунди)
histogram_quantile(0.95, sum by (le) (rate(http_server_requests_seconds_bucket{C}[1m])))
# 5xx по подах
sum by (pod) (rate(http_server_requests_seconds_count{C,status=~"5.."}[1m]))
# CPU (ядра) і пам'ять подів
sum by (pod) (rate(container_cpu_usage_seconds_total{namespace="plantshop",container=""}[1m]))
sum by (pod) (container_memory_working_set_bytes{namespace="plantshop",container=""})
# чи ціль жива (1 = UP)
up{namespace="plantshop"}
```
cAdvisor віддає ряд на рівні **пода** (cgroup пода, мітка `container=""`) і, зазвичай, ще ряди
на кожен **контейнер** (`container="catalog-service"`); підсумовувати обидва — означає рахувати
двічі. В інструкції вибрано контейнерні ряди (`container!=""`), але minikube з драйвером docker
(cri-dockerd) віддає **лише рядки рівня пода** (без мітки `container`), тому тут використано
`container=""` — підсумок по поду, який є в будь-якому кластері.
`uri!~"/actuator.*"` відкидає probe-запити Kubernetes і сам скрейп Prometheus.

### 2.5 Prometheus Operator і kube-prometheus-stack

**kube-prometheus-stack** (Helm-чарт prometheus-community) ставить одним релізом:

- **Prometheus Operator** — контролер, який керує Prometheus через CRD;
- **Prometheus** (StatefulSet `prometheus-kps-prometheus`) і **Alertmanager**;
- **Grafana** з sidecar-ом, що підвантажує дашборди і datasource-и з ConfigMap;
- **kube-state-metrics** (стан об'єктів Kubernetes: репліки, поди, Rollout...) і **node-exporter**
  (метрики вузла); метрики контейнерів (CPU/пам'ять) Prometheus бере з **cAdvisor** у kubelet;
- готові правила алертів і дашборди для самого кластера.

**CRD оператора:**

| CRD | Призначення |
|---|---|
| `Prometheus`, `Alertmanager` | опис самих серверів (оператор робить з них StatefulSet) |
| **`ServiceMonitor`** | «скрейпи endpoints Service-ів з такими мітками, на такому порту, такий шлях» |
| `PodMonitor` | те саме, але напряму по подах, без Service |
| **`PrometheusRule`** | правила запису і алертів (оператор кладе їх у конфіг Prometheus) |

Ланцюжок: `ServiceMonitor.spec.selector` → знаходить Service за мітками → бере його
**Endpoints/EndpointSlice** (IP усіх готових подів) → порт з **ім'ям** `http` → кожен под стає
окремою ціллю (target) з мітками `namespace`, `service`, `pod`, `container`, `job`, `endpoint`.
Тому порт у Service **обов'язково має ім'я**, і воно має збігатися з `port:` у ServiceMonitor.

`serviceMonitorSelectorNilUsesHelmValues: false` — без цього Prometheus з чарту вибирає лише
ServiceMonitor-и з міткою `release: kps`; з `false` — усі в кластері (так само для PodMonitor і
PrometheusRule).

`ServerSideApply=true` в Application: CRD оператора (особливо `Prometheus`) більші за 256 КБ,
а клієнтський `kubectl apply` зберігає весь об'єкт в анотації `last-applied-configuration`, що має
ліміт 262 144 байти. Server-side apply цієї анотації не потребує.

`grafana.admin.existingSecret`: чарт Grafana інакше генерує випадковий пароль під час рендерингу
(`randAlphaNum`). Argo CD рендерить шаблони на кожній синхронізації, тож Secret змінювався б
постійно (вічний OutOfSync). До того ж пароль у Git — порушення правила «no secrets in Git».

### 2.6 Loki і Grafana Alloy

**Loki** — система зберігання логів «як Prometheus, але для логів»: індексує **лише мітки**
(`namespace`, `pod`, `app`, ...), а сам текст рядків зберігає стиснутими чанками. Тому мітки
мають бути з малою кардинальністю (не можна робити міткою `status` чи `trace_id`), а пошук по
вмісту — це фільтр під час запиту.

Режими розгортання: **SingleBinary/monolithic** (один процес робить усе: distributor, ingester,
querier, compactor — для навчання й малих обсягів), *SimpleScalable* (read/write/backend),
*Distributed* (мікросервіси). Тут — SingleBinary з `storage: filesystem` (PVC), `auth_enabled:
false` (один tenant), schema `v13` з індексом `tsdb`. `loki-gateway` — nginx перед Loki,
єдина точка для запису (`/loki/api/v1/push`) і читання.

**Grafana Alloy** — агент збору телеметрії (наступник Promtail і Grafana Agent), конфіг на мові
River/Alloy syntax — граф компонентів:
```
discovery.kubernetes (role=pod) → discovery.relabel (мітки namespace/pod/app/component)
   → loki.source.kubernetes (читає логи через Kubernetes API, як kubectl logs)
   → loki.write (push у loki-gateway)
```
Одна репліка Deployment (а не DaemonSet з hostPath), бо читає логи через API-сервер — для
minikube з одним вузлом цього досить.

### 2.7 LogQL

```logql
{namespace="plantshop", component="catalog-service"}                 # stream selector (лише мітки!)
{namespace="plantshop"} |= "injected"                                 # фільтр рядка: |= містить, != не містить, |~ regex
{namespace="plantshop"} | json                                        # парсер: поля JSON стають мітками (log.level → log_level)
{namespace="plantshop"} | json | status >= 500                        # фільтр по полю (числове порівняння)
{namespace="plantshop"} | json | duration_ms > 200 | line_format "{{.route}} {{.duration_ms}}ms"
sum by (pod) (count_over_time({namespace="plantshop"} | json | status >= 500 [1m]))   # метрика з логів
sum by (version) (rate({component="catalog-service"} | json | status >= 500 [1m]))
```
Запит LogQL має бути хоча б з одним матчером мітки в `{}`; спочатку вибираються потоки за
індексом міток, далі по конвеєру (`|`) — фільтри, парсери, форматування. `count_over_time`,
`rate` перетворюють логи на метрики.

### 2.8 Структуровані (JSON) логи

Один JSON-об'єкт на рядок у **stdout** (12-factor: застосунок не пише файли, логи збирає
платформа). Переваги: не треба regex для розбору, поля (`status`, `route`, `duration_ms`,
`version`) одразу доступні в LogQL через `| json`, рівень логування — окреме поле.

У Spring Boot ≥ 3.4: `logging.structured.format.console: ecs` (Elastic Common Schema; є ще
`logstash` і `gelf`). Поля: `@timestamp`, `log.level`, `message`, `service.name`,
`service.version`, `process.thread.name`, `log.logger`, плюс **усі ключі MDC** (Mapped Diagnostic
Context — потоколокальна мапа SLF4J) як окремі JSON-поля. Саме так access-лог додає `method`,
`route`, `status`, `duration_ms`, `version`.

### 2.9 Grafana і «dashboards as code»

Grafana — візуалізація поверх datasource-ів (тут Prometheus `uid: prometheus` і Loki `uid: loki`).
Дашборд — це JSON (панелі, запити, одиниці, пороги). Щоб він теж жив у Git: JSON лежить у чарті,
шаблон кладе його в **ConfigMap з міткою `grafana_dashboard: "1"`**, а **sidecar** Grafana
(контейнер `grafana-sc-dashboard`) стежить за такими ConfigMap у всіх namespace
(`searchNamespace: ALL`) і пише файли в каталог provisioning. Видалений у UI дашборд з'являється
знову, змінений у UI — перезаписується з Git. Правка: змінити в UI → Export → JSON → коміт.

### 2.10 Алерти: PrometheusRule і Alertmanager

Правило алерту = PromQL-вираз + `for` (скільки часу умова має бути істинною). Стани:
**Inactive** → **Pending** (умова істинна, але `for` ще не минув) → **Firing** (Prometheus
надсилає алерт в **Alertmanager**, який групує, дедуплікує, глушить (silence/inhibit) і
маршрутизує в канали: email, Slack, webhook). `for` захищає від разових сплесків.

### 2.11 Progressive delivery з аналізом (Argo Rollouts)

У ЛР-6 рішення «просувати canary далі чи ні» приймала людина (`pause: {}` + `promote`). Тепер
його приймають **метрики**:

| Об'єкт | Роль |
|---|---|
| **AnalysisTemplate** | шаблон перевірки: метрики, провайдер (Prometheus), запит, `successCondition`/`failureCondition`, `interval`, `count`, `failureLimit`, `inconclusiveLimit` |
| **AnalysisRun** | конкретний запуск шаблону, створюється контролером на кроці `analysis` (як Job для CronJob) |
| `ClusterAnalysisTemplate` | те саме, але на весь кластер |

Варіанти підключення: **крок `analysis`** у `canary.steps` (блокує кроки до результату),
**background analysis** (`canary.analysis` — працює паралельно з усіма кроками починаючи з
`startingStep`), для blue-green — `prePromotionAnalysis` (до перемикання) і
`postPromotionAnalysis` (після).

Результати вимірювань: **Successful** (умова виконана), **Failed** (не виконана), **Error**
(провайдер не відповів / запит повернув не те — напр. порожній результат), **Inconclusive**.
Якщо кількість Failed > `failureLimit` → AnalysisRun **Failed** → Rollout **автоматично abort**:
canary-ReplicaSet масштабується до 0, увесь трафік на stable, Rollout у стані **Degraded**.

Важливо: 0/0 = **NaN**, і `NaN >= 0.95` хибне — без трафіку аналіз провалиться навіть для
доброї версії. Тому під час релізу потрібне фонове навантаження.

**Чому abort не повертає Git автоматично.** Abort змінює *status* Rollout, а *spec* (образ, env)
досі як у Git, тому Argo CD показує **Synced + Degraded**. Спроба знову стане «новою» лише якщо
змінити spec; правильне завершення в GitOps — `git revert` поганого коміту. Повернення до
попереднього pod template дає той самий pod-template-hash, тож Rollout просто знову вважає
stable ReplicaSet поточним — без нових подів, за секунди.

**Canary vs blue-green для аналізу.** У canary аналіз іде на *реальному* трафіку частини
користувачів (тут 25%) — дешево за ресурсами, але частина користувачів бачить помилки. У
blue-green аналізується preview-сервіс (окремий трафік), користувачі поганої версії не бачать,
але треба вдвічі більше подів і окреме навантаження на preview. Тут обрано canary (ЛР-6:
api-gateway ходить у catalog через Eureka, а не через Service, тому preview-поди blue-green
однаково отримали б реальний трафік).

### 2.12 Порядок синхронізації і sync waves

Анотація `argocd.argoproj.io/sync-wave: "-1"` на Application-ах `kps`, `loki`, `argo-rollouts`
змушує root створити їх раніше за `plantshop` (хвиля 0). CRD `ServiceMonitor`,
`PrometheusRule`, `AnalysisTemplate` мають існувати до того, як Argo CD застосує об'єкти цих
типів із чарту plantshop; інакше «no matches for kind», який лікує `syncPolicy.retry`. Тому стек
моніторингу додано окремим комітом і дочекано Healthy, а вже потім — ServiceMonitor у чарт.

---

## 3. Середовище

| Компонент | Значення |
|---|---|
| Кластер | minikube v1.37.0, Kubernetes v1.34.0, драйвер docker, **4 CPU / 10 ГБ** (Docker Desktop / WSL2 з лімітом 12 ГБ у `%UserProfile%\.wslconfig`) |
| Argo CD | namespace `argocd`, `stable/install.yaml` (server-side apply), CLI `~/bin/argocd.exe` |
| Argo Rollouts | чарт `argo-rollouts` 2.43.12 (контролер v1.10.2), з ЛР-6 |
| kube-prometheus-stack | чарт **93.3.0**: Prometheus v3.16.0, Prometheus Operator v0.95.2, Alertmanager v0.35.0, Grafana, kube-state-metrics v2.20.0, node-exporter v1.12.1 |
| Loki | чарт `grafana-community/loki` **21.2.3** (Loki 3.7.10), SingleBinary + filesystem |
| Alloy | чарт `grafana/alloy` **1.16.0** (Alloy v1.22.0), Deployment з 1 реплікою |
| Застосунок | catalog-service **1.5.0 / 1.6.0 / 1.7.0** (метрики + JSON-логи + FAIL_RATE), config-service **1.1.0** (конфіг catalog для ЛР-7) |
| Гілка | `lab/DevOps_lab_7` (від `lab/DevOps_lab_6`), вона ж `targetRevision` у `root` і `plantshop` |

## 4. Архітектура рішення

```
bootstrap/root.yaml ──► argocd-apps/ (гілка lab/DevOps_lab_7)
 ├── argo-rollouts.yaml  wave -1 ─► ns argo-rollouts (контролер, CRD Rollout/AnalysisTemplate)
 ├── kps.yaml            wave -1 ─► ns monitoring: Prometheus, Alertmanager, Grafana, operator,
 │                                   kube-state-metrics, node-exporter (CRD ServiceMonitor/PrometheusRule)
 ├── loki.yaml           wave -1 ─► ns monitoring: loki-0 (SingleBinary), loki-gateway
 ├── alloy.yaml                  ─► ns monitoring: alloy (читає логи всіх подів через API)
 └── plantshop.yaml              ─► ns plantshop: чарт 0.4.x
        ├── Rollout catalog-service (4 поди) ── /actuator/prometheus ◄── scrape 15s ── Prometheus
        │        stdout JSON ──► Alloy ──► loki-gateway ──► Loki ◄── Grafana (Explore, дашборд)
        ├── ServiceMonitor, PrometheusRule, AnalysisTemplate, ConfigMap дашборда (grafana_dashboard="1")
        └── інші сервіси, БД, Kafka

Реліз:  git push ─► Argo CD ─► Rollout: setWeight 25 ─► pause 30s ─► analysis (AnalysisRun ─► Prometheus)
                                    ├─ Successful ─► 50% ─► 75% ─► 100%, Healthy
                                    └─ Failed     ─► abort: увесь трафік на stable, Degraded ─► git revert
```

### Нові й змінені файли

| Файл | Що зроблено |
|---|---|
| `argocd-apps/kps.yaml`, `loki.yaml`, `alloy.yaml` | **нові**: Application-и стеку моніторингу (версії зафіксовані) |
| `bootstrap/root.yaml`, `argocd-apps/plantshop.yaml` | `targetRevision: lab/DevOps_lab_7` |
| `catalog-service/pom.xml` | `micrometer-registry-prometheus` |
| `catalog-service/.../observability/RequestLoggingInterceptor.java` | **новий**: access-лог (MDC: method, route, status, duration_ms, version), рівень ERROR для 5xx |
| `catalog-service/.../observability/FailureInjectionInterceptor.java` | **новий**: `FAIL_RATE` — частка `/api/**` запитів, що отримують 500 |
| `catalog-service/.../observability/ObservabilityConfig.java` | **новий**: реєструє обидва interceptor-и для `/api/**` (не для `/actuator/**`) |
| `catalog-service/src/test/.../observability/*Test.java` | **нові**: 8 unit-тестів (маршрут = шаблон, MDC очищується, поріг FAIL_RATE) |
| `config-service/.../configurations/catalog-service.yml` | експозиція `prometheus`, гістограма `http.server.requests` (5 мс…5 с), `logging.structured.format.console: ecs`, `plantshop.fail-rate: ${FAIL_RATE:0}`, вимкнено банер |
| `charts/plantshop/templates/catalog-service-servicemonitor.yaml` | **новий**: ServiceMonitor (`port: http`, `/actuator/prometheus`, 15 с, `podTargetLabels: rollouts-pod-template-hash`) |
| `charts/plantshop/templates/catalog-service-analysistemplate.yaml` | **новий**: AnalysisTemplate `plantshop-catalog-service-success-rate` |
| `charts/plantshop/templates/catalog-service-prometheusrule.yaml` | **новий**: алерт `CatalogHighErrorRate` (5xx > 5% протягом 1 хв) |
| `charts/plantshop/templates/grafana-dashboard.yaml` + `dashboards/plantshop.json` | **нові**: дашборд як код (ConfigMap `plantshop-dashboard`, мітка `grafana_dashboard: "1"`) |
| `charts/plantshop/templates/_catalog-service-pod.tpl` | `env` з `catalogService.env`; checksum лише по `data` ConfigMap |
| `charts/plantshop/values.yaml` | `catalogService.env`, `rollout.analysis.*`, `metrics.*`, `alerts.*` (за замовчуванням вимкнено) |
| `charts/plantshop/values-minikube.yaml` | catalog 1.6.0, config 1.1.0, `FAIL_RATE: "0"`, крок `analysis` замість ручної паузи, `progressDeadlineSeconds: 300`, `probes.startupFailureThreshold: 72` |

### Історія комітів ЛР-7

| Коміт | Зміст |
|---|---|
| `3e458fb` | стек моніторингу (kps, loki, alloy), перехід на гілку `lab/DevOps_lab_7` |
| `6c0afc7` | catalog-service: метрики, JSON-логи, FAIL_RATE (+ тести) |
| `b8a7c81` | чарт 0.4.0: ServiceMonitor, дашборд, AnalysisTemplate, PrometheusRule; catalog 1.5.0 |
| `20fde26` | чарт 0.4.1: запити CPU/пам'яті на рівні пода; checksum ConfigMap лише по `data` |
| `d72a4a2` | minikube: startup probe до 6 хв |
| `2f16bf2` | **хороший реліз** 1.5.0 → 1.6.0 (AnalysisRun Successful) |
| `ea87e65` | **поганий реліз** `FAIL_RATE: "0.5"` (AnalysisRun Failed, auto-abort) |
| `5526e63` | `git revert` поганого релізу |

## 5. Хід виконання

### 5.1 Стек моніторингу через Argo CD (п. 1)

Кластер перестворено з 10 ГБ (див. розділ 6, проблема 1), потім вручну відновлено лише те, чого
немає в Git:
```bash
minikube delete && minikube start --cpus=4 --memory=10g
kubectl create namespace argocd
kubectl apply -n argocd --server-side --force-conflicts \
  -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
kubectl create namespace plantshop
kubectl -n plantshop create secret generic plantshop-secret \
  --from-literal=DB_USERNAME=plantshop --from-literal=DB_PASSWORD=<згенерований>
kubectl create namespace monitoring
kubectl -n monitoring create secret generic grafana-admin \
  --from-literal=admin-user=admin --from-literal=admin-password=<згенерований>
kubectl apply -f bootstrap/root.yaml
```
Версії чартів узято з `helm search repo` (93.3.0, 21.2.3, 1.16.0). Перевірка `helm template`
показала, що чарт Loki 21.x має JSON-схему значень і **не має ключа `minio`** (помилка
`additional properties 'minio' not allowed`), тому його прибрано з `loki.yaml`.

Результат (≈5 хв після `kubectl apply -f bootstrap/root.yaml`):
```
NAME            SYNC STATUS   HEALTH STATUS
alloy           Synced        Healthy
argo-rollouts   Synced        Healthy
kps             Synced        Healthy
loki            Synced        Healthy
plantshop       Synced        Healthy
root            Synced        Healthy
```
У `monitoring` працюють: `alertmanager-kps-alertmanager-0`, `alloy`, `kps-grafana` (3/3: grafana +
sidecar дашбордів + sidecar datasource-ів), `kps-kube-state-metrics`, `kps-operator`,
`kps-prometheus-node-exporter`, `loki-0`, `loki-gateway`, `prometheus-kps-prometheus-0`.
Grafana має три datasource-и: Prometheus (`uid: prometheus`), Alertmanager, Loki (`uid: loki`).

### 5.2 Метрики застосунку (п. 2)

Spring Boot Actuator + Micrometer дають готовий таймер `http_server_requests_seconds`: `_count`
(лічильник запитів), `_sum`, `_bucket` (гістограма) з мітками `method`, `uri` (шаблон маршруту,
низька кардинальність), `status`, `outcome`, `exception`, `error`. Гістограму ввімкнено
`percentiles-histogram`, межі обмежено 5 мс…5 с (менше кошиків). Рядів
`http_server_requests_seconds_bucket` у namespace — **768** (4 поди × кілька маршрутів × кошики),
тобто кардинальність під контролем.

Ціль у Prometheus (Status → Targets, пул `serviceMonitor/plantshop/plantshop-catalog-service/0`):
```
http://10.244.0.31:8081/actuator/prometheus   up
http://10.244.0.32:8081/actuator/prometheus   up
http://10.244.0.33:8081/actuator/prometheus   up
http://10.244.0.34:8081/actuator/prometheus   up
```
Запит до метрики (фонове навантаження ≈16 RPS):
```
sum by (uri, status) (rate(http_server_requests_seconds_count{namespace="plantshop",service="plantshop-catalog-service",uri!~"/actuator.*"}[1m]))
{status="200", uri="/api/catalog/categories"}  7.69
{status="200", uri="/api/catalog/plants"}      7.84
```

### 5.3 Логи (п. 3)

`logging.structured.format.console: ecs` — кожен рядок stdout є JSON-об'єктом. Access-лог
(`RequestLoggingInterceptor`) кладе поля запиту в MDC, тому вони стають полями JSON. Реальний
рядок з Loki:
```json
{"@timestamp":"2026-11-19T10:51:58.016220581Z","log":{"level":"INFO","logger":"http.access"},
 "process":{"pid":1,"thread":{"name":"http-nio-8081-exec-2"}},
 "service":{"name":"catalog-service","version":"1.5.0","node":{}},"message":"request",
 "duration_ms":"4.6","route":"/api/catalog/plants","method":"GET","version":"1.5.0","status":"200",
 "ecs":{"version":"8.11"}}
```
Мітки в Loki: `namespace`, `pod`, `container`, `app`, `component`, `instance`, `job`,
`service_name`. Оскільки в чарті `app.kubernetes.io/name=plantshop` стоїть на всіх подах, в Alloy
додано мітку `component` (з `app.kubernetes.io/component`). Запити в Grafana → Explore → Loki:
```logql
{namespace="plantshop", component="catalog-service"} | json
{namespace="plantshop", component="catalog-service"} | json | status >= 500
{namespace="plantshop", component="catalog-service"} | json | log_level="ERROR"
{namespace="plantshop", component="catalog-service"} | json | duration_ms > 50 | line_format "{{.route}} {{.duration_ms}}ms"
```

### 5.4 Дашборд (п. 4)

Дашборд «Plant Shop: catalog-service» (`uid: plantshop-catalog`) має 10 панелей: RPS за статусом,
частка 5xx (поріг 5%), p95 (загальний і по маршрутах), 5xx/с по подах, RPS по ReplicaSet
(`rollouts_pod_template_hash`), success rate з тим самим запитом, що в AnalysisTemplate (поріг 95%),
CPU по подах, пам'ять по подах, 5xx-рядки логів по подах (метрика з Loki), панель логів помилок
(Loki, `line_format` з подом, версією, маршрутом, статусом). Діапазон 15 хв, автооновлення 10 с.

JSON лежить у `charts/plantshop/dashboards/plantshop.json`, ConfigMap `plantshop-dashboard` з
міткою `grafana_dashboard: "1"` доставляє Argo CD, sidecar Grafana його підвантажує. Перевірка,
що джерело правди — Git:
- видалити дашборд в UI/API неможливо: `provisioned dashboard cannot be deleted`;
- `kubectl -n plantshop delete configmap plantshop-dashboard` → Argo CD (selfHeal) відновив
  ConfigMap за **1 с**, дашборд знову з'явився в Grafana за **3 с**.

### 5.5 Навантаження (п. 5)

Фон: `kubectl create deployment load --image=curlimages/curl -- sh -c 'while true; do curl ... /plants; curl ... /categories; sleep 0.1; done'` (≈16 RPS).
Тест: `kubectl run hey --rm -i --restart=Never --image=williamyeh/hey -- -z 60s -c 20 http://plantshop-catalog-service.plantshop:8081/api/catalog/plants`

| Метрика | До | Під час `hey` |
|---|---|---|
| RPS (Prometheus) | 16.2 | **541** (`hey`: 531 req/s, 31 892 відповіді 200) |
| p95 (Prometheus) | 5.4 мс | **80 мс** (`hey`: p95 86 мс) |
| CPU catalog, ядра | ≈0.07 | **1.92** |

Друга зміна на вибір: `kubectl -n plantshop delete pod <catalog pod>`. На панелях CPU/пам'яті
зникає один под і з'являється новий, а Rollout сам відновлює 4 репліки. Інший варіант — реліз
через Git (новий ReplicaSet на панелі «RPS by ReplicaSet»).

### 5.6 Автоматичний відкат за метриками (п. 6)

**AnalysisTemplate** (рендер з чарту):
```yaml
metrics:
  - name: success-rate
    interval: 30s
    count: 4
    failureLimit: 1
    successCondition: result[0] >= 0.95
    provider:
      prometheus:
        address: http://kps-prometheus.monitoring:9090
        query: |
          sum(rate(http_server_requests_seconds_count{namespace="plantshop",service="plantshop-catalog-service",uri!~"/actuator.*",status!~"5.."}[1m]))
          /
          sum(rate(http_server_requests_seconds_count{namespace="plantshop",service="plantshop-catalog-service",uri!~"/actuator.*"}[1m]))
```
**Кроки canary:** `setWeight 25` → `pause 30s` → `analysis` → `setWeight 50` → `pause 20s` →
`setWeight 75` → `pause 20s`. Ручну паузу ЛР-6 прибрано: рішення приймає аналіз.

**Хороший реліз** (`2f16bf2`, тег 1.5.0 → 1.6.0) під фоновим навантаженням:
```
13:21:53 Progressing step=0
13:22:31 Paused      step=1          (25%, pause 30s)
13:22:58 Progressing step=2          AnalysisRun ...-79c7799b64-2-2: Running
13:24:30 Progressing step=3          AnalysisRun: Successful (4 вимірювання ✔)
13:24:57 Paused step=4 → 13:25:57 Paused step=6 → 13:26:51 Healthy step=7
```
**Поганий реліз** (`ea87e65`, `FAIL_RATE: "0.5"`):
```
13:27:08 Progressing step=0
13:27:46 Paused      step=1 CanaryPauseStep
13:28:13 Progressing step=2          AnalysisRun ...-8bb5bb5f9-3-2: Running
13:28:46 Degraded    step=0 RolloutAborted: Rollout aborted update to revision 3:
         Step-based analysis phase error/failed: Metric "success-rate" assessed Failed
         due to failed (2) > failureLimit (1)
```
Виміряні значення (`kubectl -n plantshop describe analysisrun ...-8bb5bb5f9-3-2`):
`[0.9154]` Failed, `[0.8560]` Failed. Два збої > `failureLimit: 1`, тож AnalysisRun Failed через
≈35 с після старту аналізу, і контролер сам зробив abort. Argo CD показує **Synced + Degraded**.

Що видно в даних:
- Prometheus, 5xx за 5 хв по подах — **лише один под**:
  `{pod="plantshop-catalog-service-8bb5bb5f9-t44dq", rollouts_pod_template_hash="8bb5bb5f9"} 176`;
- Loki, `sum by (pod, version) (count_over_time({namespace="plantshop", component="catalog-service"} | json | status >= 500 [5m]))`
  → `{pod="plantshop-catalog-service-8bb5bb5f9-t44dq", version="1.6.0"} 180`, тобто помилки лише з
  пода нового ReplicaSet (хеш `8bb5bb5f9`); версія та сама 1.6.0, бо змінився тільки env;
- після abort Rollout спершу повертає stable до 4 подів (без traffic router вага = частка подів),
  потім прибирає canary-под.

**Завершення через Git:** `git revert ea87e65` (`5526e63`) → pod template знову збігається зі
stable ReplicaSet → Rollout **Healthy через 9 с**, нових подів не створено.

**Алерт (п. 6d).** `PrometheusRule plantshop-catalog-service-alerts` завантажено в Prometheus
(Status → Rules, Alerts: `CatalogHighErrorRate`). Під час поганого релізу частка 5xx була вищою
за 5% лише ≈40 с, а `for: 1m`, тож abort стається раніше, ніж алерт перейде з Pending у Firing.
Щоб показати Firing: тимчасово `catalogService.rollout.analysis.enabled: false` + `FAIL_RATE: "0.5"`
(реліз дійде до 100%, 5xx ≈ 50%) → через ≈1–2 хв Firing у Prometheus → Alerts і в Alertmanager
(`port-forward svc/kps-alertmanager 9093`) → `git revert` обох комітів.

## 6. Проблеми і як їх вирішено

1. **Кластер «не стартує»: образи тягнуться по 30 хв, JVM не проходять startup probe, БД
   рестартують, load average 60.** Причина: `minikube start --memory=10g` на **наявному** профілі
   не змінює ліміт пам'яті, а бере старе значення з `~/.minikube/profiles/minikube/config.json`
   (7168 МБ). Контейнер minikube працював на межі 7 ГБ (`docker stats`: 6.97 GiB / 7 GiB), ядро
   постійно викидало сторінки кешу й перечитувало файли з диска (1.5 ГБ/с у `vmstat`). Рішення:
   `minikube delete` + `minikube start --cpus=4 --memory=10g` (перевірка: `docker stats minikube`
   → `/ 10GiB`). Після цього Argo CD піднявся за 1.5 хв, увесь стек — за 5 хв.
2. **Docker Desktop мав лише 7.6 ГБ.** WSL2 за замовчуванням бере 50% RAM. Створено
   `%UserProfile%\.wslconfig` з `[wsl2] memory=12GB processors=8`, далі `wsl --shutdown` і
   перезапуск Docker Desktop.
3. **Панелі CPU/пам'яті порожні.** Запит з інструкції `container!=""` нічого не повертав: у minikube
   (cri-dockerd) cAdvisor віддає ряди лише на рівні пода. Замінено на `container=""`.
4. **Підвищення версії чарту перезапускало всі сервіси.** Анотація `checksum/config` хешувала весь
   рендер ConfigMap разом з міткою `helm.sh/chart: plantshop-<version>`. Тепер хешується лише
   `.data` (`fromYaml ... .data | toJson | sha256sum`).
5. **Одночасний старт 7 JVM не вкладався в startup probe (3 хв).** Для minikube
   `probes.startupFailureThreshold: 72` (до 6 хв) і `progressDeadlineSeconds: 300` для catalog
   (крок canary тепер ще й чекає аналіз).
6. **`minio` у values Loki** — див. 5.1.
7. **Grafana: дашборд не видаляється.** Це очікувано для provisioned-дашборда; відновлення з Git
   показується через видалення ConfigMap (5.4).

## 7. Контрольні питання (для захисту)

1. **Pull чи push у Prometheus? Чому pull?** Pull: Prometheus сам скрейпить цілі. Він знає список
   цілей (service discovery), бачить недоступну ціль (`up == 0`), а застосунку не треба знати адресу
   Prometheus. Push лише для короткоживучих job-ів (Pushgateway).
2. **Чим counter відрізняється від gauge? Навіщо `rate()`?** Counter тільки зростає й скидається при
   рестарті; його абсолютне значення нічого не каже, важлива швидкість. `rate` рахує за секунду і
   коректно обробляє скидання.
3. **Чому гістограма, а не summary, для p95?** Кошики гістограми можна підсумувати по подах
   (`sum by (le)`), квантилі summary — ні.
4. **Що таке кардинальність і як її контролювати?** Кількість унікальних комбінацій міток = кількість
   рядів. Мітки — лише з обмеженими значеннями: шаблон маршруту `uri`, а не сирий шлях з id.
5. **Як Prometheus знаходить поди застосунку?** ServiceMonitor → Service за мітками → Endpoints →
   поди, порт за ім'ям `http`. Оператор генерує з ServiceMonitor конфіг scrape.
6. **Навіщо `serviceMonitorSelectorNilUsesHelmValues: false`?** Інакше Prometheus бере лише
   ServiceMonitor-и з міткою `release: kps`.
7. **Навіщо `ServerSideApply=true`?** CRD оператора більші за ліміт анотації
   `last-applied-configuration` (256 КБ).
8. **Чому пароль Grafana в Secret, створеному вручну?** No secrets in Git, до того ж чарт генерує
   випадковий пароль при кожному рендері, а Argo CD рендерить на кожну синхронізацію.
9. **Чим Loki відрізняється від Elasticsearch?** Індексує лише мітки, не текст: дешевше зберігати,
   повнотекстовий пошук — фільтром під час запиту. Тому `status` — поле після `| json`, а не мітка.
10. **Що робить Alloy?** Знаходить поди (`discovery.kubernetes`), перейменовує мітки
    (`discovery.relabel`), читає логи через Kubernetes API (`loki.source.kubernetes`), пушить у Loki.
11. **Навіщо JSON-логи?** Поля доступні без regex (`| json`), рівень — окреме поле, з логів легко
    рахувати метрики.
12. **Як дашборд потрапляє в Grafana з Git?** ConfigMap з міткою `grafana_dashboard: "1"`; sidecar
    шукає їх в усіх namespace і кладе JSON у provisioning.
13. **Що таке AnalysisTemplate і AnalysisRun?** Шаблон перевірки і конкретний її запуск на кроці
    `analysis`. `interval` 30 с, `count` 4, `failureLimit` 1: два збої → Failed → abort.
14. **Чому без трафіку аналіз падає?** 0/0 = NaN, а `NaN >= 0.95` хибне (або результат порожній → Error).
15. **Чому після abort Argo CD Synced, а не OutOfSync?** Abort змінює status Rollout, а spec досі
    збігається з Git. Тому потрібен `git revert`; повернення на stable-хеш миттєве.
16. **Чому аналіз по всьому сервісу ловить поганий canary?** 1 под з 4 віддає 50% помилок →
    ≈12.5% по сервісу → success rate ≈ 0.87 < 0.95. Точніше — фільтрувати по
    `rollouts_pod_template_hash` canary (мітка є завдяки `podTargetLabels`).
17. **Canary vs blue-green для аналізу?** Див. розділ 2.11.
18. **Чому алерт не встигає спрацювати під час демо?** `for: 1m`, а abort стається за ~35 с аналізу.
19. **RED і USE?** Див. розділ 2.2.

## 8. Висновки

Застосунок став спостережуваним: Prometheus знімає RED-метрики catalog-service через
ServiceMonitor, JSON-логи збирає Alloy в Loki, дашборд як код доставляє Argo CD. Увесь стек
моніторингу встановлено з Git через Argo CD (App of Apps). У canary-релізі з ЛР-6 рішення
«далі чи назад» тепер приймають метрики. Хороша версія пройшла AnalysisRun (Successful) і дійшла
до 100% без участі людини. Погану (`FAIL_RATE=0.5`) контролер скасував автоматично через ≈35 с
аналізу (success rate 0.92 і 0.86 < 0.95); помилки отримав лише один canary-под (25% подів), а
відкат завершено `git revert` за 9 с без нових подів.
