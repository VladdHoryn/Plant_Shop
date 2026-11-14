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
sum by (pod) (rate(container_cpu_usage_seconds_total{namespace="plantshop",container!=""}[1m]))
sum by (pod) (container_memory_working_set_bytes{namespace="plantshop",container!=""})
# чи ціль жива (1 = UP)
up{namespace="plantshop"}
```
`container!=""` відкидає агрегований по поду ряд cAdvisor (без контейнера), щоб не рахувати двічі.
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
