# Лабораторна робота №6. Progressive delivery: canary з Argo Rollouts

**Дисципліна:** Технології DevOps
**Проєкт:** Plant Shop — інтернет-магазин рослин на мікросервісах (Java 21, Spring Boot 3.5, Spring Cloud, PostgreSQL, Kafka)
**Студент:** Горин Владислав (GitHub `VladdHoryn`, Docker Hub `vladyslavv648`)
**Репозиторій:** https://github.com/VladdHoryn/Plant_Shop, гілка `lab/DevOps_lab_6`

---

## 1. Мета роботи

Замінити звичайне оновлення `Deployment` (rolling update) на **поступовий реліз**, який можна
зупинити на проміжному кроці, перевірити, довести до кінця (`promote`) або скасувати (`abort`).
Усе це в межах GitOps з ЛР-5: і контролер, і застосунок встановлюються з Git через Argo CD, а реліз
і відкат робляться комітами.

Обрана стратегія: **canary** для сервісу `catalog-service` (обґрунтування в розділі 4.4).

## 2. Середовище

| Компонент | Значення |
|---|---|
| Кластер | minikube v1.34, драйвер docker, 4 CPU / 7 ГБ |
| Argo CD | namespace `argocd`, офіційний `install.yaml` (stable), CLI `~/bin/argocd.exe` v3.6.1 |
| Argo Rollouts | Helm-чарт `argo/argo-rollouts` **2.43.12** (контролер **v1.10.2**), namespace `argo-rollouts`, встановлений через Argo CD |
| Плагін kubectl | `kubectl-argo-rollouts` v1.10.2 (`~/bin/kubectl-argo-rollouts.exe`, Windows amd64) |
| Образи | Docker Hub `vladyslavv648/plantshop-catalog-service`: 1.0.0, 1.1.0 (з ЛР-5), **1.2.0, 1.3.0, 1.4.0** (нові) |
| Гілка | `lab/DevOps_lab_6` (відгалужена від `lab/DevOps_lab_5`), вона ж `targetRevision` обох Application |

## 3. Архітектура рішення

```
bootstrap/root.yaml  (kubectl apply вручну, один раз)
        │  стежить за argocd-apps/ у гілці lab/DevOps_lab_6
        ▼
argocd-apps/
 ├── argo-rollouts.yaml   sync-wave -1 ─► Helm-чарт argo-rollouts 2.43.12 ─► ns argo-rollouts
 │                                          (контролер + CRD Rollout, AnalysisTemplate, ...)
 └── plantshop.yaml       (retry 5×)    ─► charts/plantshop + values-minikube.yaml ─► ns plantshop
                                              │
                                              ├── 11 Deployment (інші сервіси, БД, Kafka)
                                              └── Rollout plantshop-catalog-service (4 поди, canary)
```

Хто чим керує:

- **Argo CD** відповідає за *spec*: рендерить чарт і приводить кластер до Git (зокрема образ у Rollout).
- **Контролер Argo Rollouts** відповідає за *процес*: бачить новий pod template у Rollout, створює
  новий ReplicaSet і проходить кроки canary (масштабує ReplicaSet-и, ставить паузи).
- **Людина / CLI** дає команди процесу: `promote`, `abort`, `retry`. Вони змінюють *status* Rollout,
  а не spec, тому `selfHeal` Argo CD їх не скасовує.

### Нові й змінені файли

| Файл | Що зроблено |
|---|---|
| `argocd-apps/argo-rollouts.yaml` | **новий**: Application для Helm-чарту argo-rollouts |
| `argocd-apps/plantshop.yaml` | `targetRevision: lab/DevOps_lab_6`, додано `syncPolicy.retry` |
| `bootstrap/root.yaml` | `targetRevision: lab/DevOps_lab_6` |
| `charts/plantshop/templates/catalog-service-rollout.yaml` | **новий**: шаблон `Rollout` |
| `charts/plantshop/templates/_catalog-service-pod.tpl` | **новий**: спільний pod template для Deployment і Rollout |
| `charts/plantshop/templates/catalog-service-deployment.yaml` | обгорнуто в `if not .Values.catalogService.rollout.enabled` |
| `charts/plantshop/values.yaml` | `catalogService.rollout: {enabled: false, strategy: {}}` |
| `charts/plantshop/values-minikube.yaml` | 4 репліки, `rollout.enabled: true`, кроки canary |
| `charts/plantshop/Chart.yaml` | `version: 0.3.0` |

### Історія комітів ЛР-6

```
a334f8c Add Argo Rollouts controller via Argo CD (Lab 6, step 1)
8e0a6da Replace catalog-service Deployment with an Argo Rollout (Lab 6, steps 2-3)
98e9a25 Release catalog-service 1.2.0 (canary)
ab07d1e Release catalog-service 1.3.0 (canary, to be aborted)
350b63f Revert "Release catalog-service 1.3.0 (canary, to be aborted)"
```

Кожну синхронізацію Argo CD прив'язав до коміту (`status.history` Application `plantshop`):

```
0 a334f8c  07:39:05Z   контролер
1 8e0a6da  07:46:39Z   Deployment → Rollout
2 98e9a25  07:54:26Z   реліз 1.2.0
3 ab07d1e  08:04:14Z   реліз 1.3.0 (abort)
4 350b63f  08:09:22Z   git revert → 1.2.0
```

## 4. Хід виконання

### 4.1 Підготовка кластера

Minikube виявився перествореним (порожній кластер), тому середовище ЛР-5 відновлено тими ж командами:

```bash
kubectl create namespace argocd
kubectl apply -n argocd --server-side --force-conflicts \
  -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
kubectl create namespace plantshop
kubectl -n plantshop create secret generic plantshop-secret \
  --from-literal=DB_USERNAME=plantshop \
  --from-literal=DB_PASSWORD="$(head -c 32 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 24)"
```

Пароль БД згенеровано й одразу передано в kubectl. Ніде не виводився, у Git не потрапив.

### 4.2 Контролер Argo Rollouts через Argo CD (п. 1a)

Версія чарту:

```bash
helm repo add argo https://argoproj.github.io/argo-helm
helm search repo argo/argo-rollouts
# argo/argo-rollouts  2.43.12  v1.10.2  A Helm chart for Argo Rollouts
```

`argocd-apps/argo-rollouts.yaml`:

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: argo-rollouts
  namespace: argocd
  annotations:
    argocd.argoproj.io/sync-wave: "-1"     # раніше за plantshop
spec:
  project: default
  source:
    repoURL: https://argoproj.github.io/argo-helm
    chart: argo-rollouts
    targetRevision: 2.43.12
    helm:
      valuesObject:
        dashboard:
          enabled: false        # дашборд запускається локально: kubectl argo rollouts dashboard
  destination:
    server: https://kubernetes.default.svc
    namespace: argo-rollouts
  syncPolicy:
    automated: {prune: true, selfHeal: true}
    syncOptions:
      - CreateNamespace=true
      - ServerSideApply=true    # CRD Rollout завеликий для анотації client-side apply
```

Відмінності від прикладу з інструкції:

- **`ServerSideApply=true`.** CRD Argo Rollouts великі. При звичайному (client-side) apply у кожен
  об'єкт записується анотація `last-applied-configuration` з повною копією маніфесту, а для великих CRD
  вона може перевищити ліміт анотацій 256 КБ (`metadata.annotations: Too long`). Server-side apply
  таку анотацію не пише. Це запобіжний захід, з ним синхронізація пройшла з першого разу.
- **Дашборд у кластері вимкнено.** Він не потрібен постійно, і в minikube обмаль пам'яті.
  Для демонстрації його запускає плагін: `kubectl argo rollouts dashboard` → http://localhost:3100.

До `argocd-apps/plantshop.yaml` додано повторні спроби. Якщо `plantshop` синхронізується раніше, ніж
з'являться CRD (`no matches for kind "Rollout"`), він не застрягне в `SyncFailed`:

```yaml
  syncPolicy:
    automated: {prune: true, selfHeal: true}
    retry:
      limit: 5
      backoff: {duration: 10s, factor: 2, maxDuration: 3m}
```

Контролер додано **окремим комітом** (`a334f8c`). Обидва Application переведено на гілку
`lab/DevOps_lab_6`. `root` сам себе на іншу гілку перевести не може (він читає `argocd-apps/` зі своєї
`targetRevision`), тому `bootstrap/root.yaml` після push застосовано вручну:

```bash
git push -u origin lab/DevOps_lab_6
kubectl apply -f bootstrap/root.yaml
```

Результат:

```
$ kubectl get applications -n argocd
NAME            SYNC STATUS   HEALTH STATUS
argo-rollouts   Synced        Healthy
plantshop       Synced        Healthy
root            Synced        Healthy

$ kubectl -n argo-rollouts get pods
argo-rollouts-549696496f-l466h   1/1   Running     # 2 репліки, одна з них лідер
argo-rollouts-549696496f-z56lh   1/1   Running

$ kubectl get crd | grep argoproj
analysisruns.argoproj.io
analysistemplates.argoproj.io
clusteranalysistemplates.argoproj.io
experiments.argoproj.io
rollouts.argoproj.io                 # + applications/applicationsets/appprojects від Argo CD
```

### 4.3 Плагін kubectl (п. 1b)

Windows: файл `kubectl-argo-rollouts-windows-amd64` з релізу v1.10.2 збережено як
`~/bin/kubectl-argo-rollouts.exe`. Kubectl знаходить плагін за назвою `kubectl-<plugin>` у `PATH`.

```bash
curl -sSLf -o ~/bin/kubectl-argo-rollouts.exe \
  https://github.com/argoproj/argo-rollouts/releases/download/v1.10.2/kubectl-argo-rollouts-windows-amd64
kubectl argo rollouts version
# kubectl-argo-rollouts: v1.10.2+be8a622
```

### 4.4 Вибір стратегії: canary

Обрано **canary**. Причини, специфічні для Plant Shop:

1. **Трафік до catalog-service йде через Eureka, а не через Kubernetes Service.** api-gateway
   маршрутизує `/api/catalog/**` на `lb://catalog-service`, тобто вибирає екземпляр зі списку Eureka.
   Blue-green тримається на тому, що preview-поди не отримують користувацького трафіку, доки
   `activeService` не перемкне селектор. Але в нас preview-поди при старті зареєструвалися б у Eureka
   і **одразу отримали б реальний трафік** через gateway, а перемикання Service нічого б не змінило.
   Canary такої суперечності не має: частку трафіку визначає частка подів, а gateway через Eureka
   дає саме цю частку. Це перевірено вимірюванням (розділ 4.6).
2. **Пам'ять.** Blue-green на час релізу тримає два повні комплекти подів (4 + 4 JVM), а minikube
   має 7 ГБ, з яких ~4,5 ГБ уже зайнято. Canary з `maxSurge: 1` додає щонайбільше один под.
3. **Ризик.** Каталог — сервіс, яким користуються всі (ціни для замовлень теж беруться з нього через
   Feign). Canary спочатку показує нову версію лише 25% запитів, і реліз можна зупинити до того, як
   проблему побачать усі.

Обмеження canary, про яке треба пам'ятати: на час релізу **одночасно працюють дві версії з однією БД**.
Тому реліз не може містити Flyway-міграцію, з якою стара версія не працює (наприклад, перейменування
колонки). Такі зміни роблять у два релізи (expand → contract).

### 4.5 Rollout у Helm-чарті (п. 2–3)

**Шаблон пода винесено в спільний хелпер.** Інструкція пропонує скопіювати блок `template:` з
`deployment.yaml` у `rollout.yaml`. Замість копії обидва шаблони включають один хелпер
`plantshop.catalogService.podTemplate` (`_catalog-service-pod.tpl`). Так Deployment і Rollout не
можуть розійтися: зміна змінної оточення чи проби потрапляє в обидва.

`templates/catalog-service-rollout.yaml`:

```yaml
{{- if .Values.catalogService.rollout.enabled }}
{{- $c := .Values.catalogService }}
{{- $component := "catalog-service" }}
apiVersion: argoproj.io/v1alpha1
kind: Rollout
metadata:
  name: {{ include "plantshop.componentName" (dict "root" . "component" $component) }}
  namespace: {{ .Release.Namespace }}
  labels:
    {{- include "plantshop.labels" (dict "root" . "component" $component) | nindent 4 }}
spec:
  replicas: {{ $c.replicaCount }}
  {{- with $c.progressDeadlineSeconds }}
  progressDeadlineSeconds: {{ . }}
  {{- end }}
  revisionHistoryLimit: 3
  minReadySeconds: {{ .Values.rollingUpdate.minReadySeconds }}
  selector:
    matchLabels:
      {{- include "plantshop.selectorLabels" (dict "root" . "component" $component) | nindent 6 }}
  template:
    {{- include "plantshop.catalogService.podTemplate" . | nindent 4 }}
  strategy:
    {{- required "catalogService.rollout.strategy must define canary or blueGreen" $c.rollout.strategy | toYaml | nindent 4 }}
{{- end }}
```

`templates/catalog-service-deployment.yaml` тепер рендериться лише тоді, коли Rollout вимкнено
(`{{- if not .Values.catalogService.rollout.enabled }} ... {{- end }}`). Deployment і Rollout з одним
селектором ніколи не існують разом, інакше два контролери керували б одними подами.

`values.yaml` (за замовчуванням вимкнено: dev/prod-оточення з ЛР-4 не потребують CRD):

```yaml
catalogService:
  rollout:
    enabled: false
    strategy: {}
```

`values-minikube.yaml`:

```yaml
catalogService:
  replicaCount: 4          # кожен крок 25% = рівно ще один под нової версії
  progressDeadlineSeconds: 120
  rollout:
    enabled: true
    strategy:
      canary:
        maxSurge: 1        # під час кроку щонайбільше +1 под (пам'ять)
        maxUnavailable: 0  # жодного кроку зі зменшенням готових подів
        steps:
          - setWeight: 25  # 1 з 4 подів на новій версії
          - pause: {}      # ручна пауза: чекати promote
          - setWeight: 50
          - pause: {duration: 30s}
          - setWeight: 75
          - pause: {duration: 30s}
```

Вимоги лабораторної виконано: 4 репліки (не менше 4), три кроки `setWeight`, одна ручна пауза
`pause: {}`, дві паузи з таймером.

Чому 4 репліки, а не 5, як у прикладі: при 4 подах ваги 25/50/75 дають цілу кількість подів
(1/2/3), тож співвідношення в `kubectl argo rollouts get` і в циклі запитів збігається з вагою точно.
П'ята JVM не вмістилася б у пам'ять під час кроку з `maxSurge`.

**Перевірка чарту:**

```bash
helm lint charts/plantshop --strict -f charts/plantshop/values-minikube.yaml   # 0 chart(s) failed
helm lint charts/plantshop --strict -f charts/plantshop/values-prod.yaml       # 0 chart(s) failed
```

Порівняно рендер до і після змін (без контрольних сум, мітки версії чарту й випадкового пароля):

- `values-prod.yaml`, `values-dev.yaml` (Rollout вимкнено): **ідентичні**, за винятком порожнього
  документа з коментарями від вимкненого `rollout.yaml`;
- pod template у Rollout порівняно зі старим Deployment: **0 рядків відмінностей**.

**Перехід Deployment → Rollout** (коміт `8e0a6da`). Argo CD видалив Deployment (`prune`) і створив
Rollout. Перший Rollout одразу стає stable без кроків canary, бо попередньої версії в нього немає.
Поди catalog-service перестворилися, тож на цьому кроці короткий простій очікуваний:

```
$ kubectl -n plantshop get deploy plantshop-catalog-service
Error from server (NotFound): deployments.apps "plantshop-catalog-service" not found

$ kubectl argo rollouts get rollout plantshop-catalog-service -n plantshop
Status:          ✔ Healthy
Strategy:        Canary
  Step:          6/6
Images:          vladyslavv648/plantshop-catalog-service:1.1.0 (stable)
Replicas:        Desired 4  Current 4  Updated 4  Ready 4  Available 4
```

Argo CD має вбудовану перевірку стану (health check) для `Rollout`. У дереві застосунку видно
Rollout → ReplicaSet-и → Pod-и, а Health показує `Progressing`, `Suspended` (пауза), `Healthy` або
`Degraded`.

### 4.6 Образи нових версій

```bash
for v in 1.2.0 1.3.0 1.4.0; do
  docker build --build-arg SERVICE=catalog-service --build-arg APP_VERSION=$v \
    -t vladyslavv648/plantshop-catalog-service:$v .
  docker push vladyslavv648/plantshop-catalog-service:$v
done
```

`APP_VERSION` оголошено лише в runtime-стадії Dockerfile, тож jar зібрано один раз, а образи
відрізняються одним шаром. Версію віддає `GET /api/catalog/version`. Призначення: **1.2.0** для
релізу з promote, **1.3.0** для abort, **1.4.0** запасний (для захисту).

### 4.7 Реліз через Git: 1.1.0 → 1.2.0 (п. 4a)

```bash
# values-minikube.yaml: catalogService.image.tag "1.1.0" → "1.2.0"
git commit -am "Release catalog-service 1.2.0 (canary)" && git push        # 98e9a25
kubectl -n argocd annotate application plantshop argocd.argoproj.io/refresh=normal --overwrite
#   (те саме, що argocd app get plantshop --refresh або кнопка Refresh)
```

Rollout зупинився на ручній паузі:

```
$ kubectl argo rollouts get rollout plantshop-catalog-service -n plantshop
Status:          ॥ Paused
Message:         CanaryPauseStep
Strategy:        Canary
  Step:          1/6
  SetWeight:     25
  ActualWeight:  25
Images:          vladyslavv648/plantshop-catalog-service:1.1.0 (stable)
                 vladyslavv648/plantshop-catalog-service:1.2.0 (canary)
Replicas:        Desired 4  Current 4  Updated 1  Ready 4  Available 4

⟳ plantshop-catalog-service                            Rollout     ॥ Paused
├──# revision:2
│  └──⧉ plantshop-catalog-service-5fc48ff468           ReplicaSet  ✔ Healthy  canary
│     └──□ plantshop-catalog-service-5fc48ff468-2785b  Pod         ✔ Running  ready:1/1
└──# revision:1
   └──⧉ plantshop-catalog-service-869cbbf65            ReplicaSet  ✔ Healthy  stable
      ├──□ ...-bkspl   Pod  ✔ Running
      ├──□ ...-qtxt6   Pod  ✔ Running
      └──□ ...-wp2tt   Pod  ✔ Running

Argo CD: Synced / Suspended
```

**Відповіді обох версій з циклу запитів усередині кластера.** Запити треба робити з пода:
`kubectl port-forward svc/...` з'єднується з одним подом і розподілу не покаже.

```bash
kubectl -n plantshop run curl --rm -i --restart=Never --image=curlimages/curl -- sh -c \
  'for i in $(seq 1 40); do curl -s http://plantshop-catalog-service:8081/api/catalog/version; echo; done | sort | uniq -c'
```

| Шлях запиту | 1.1.0 | 1.2.0 |
|---|---|---|
| Service `plantshop-catalog-service:8081` (kube-proxy) | 30 | 10 |
| `plantshop-api-gateway` → Eureka → catalog | 30 | 10 |

Обидва шляхи дали рівно 25%. Без traffic router (Istio, NGINX тощо) **вага canary = частка подів**,
і gateway, який балансує між екземплярами в Eureka, дає ту саму частку.

**Promote на ручній паузі:**

```bash
kubectl argo rollouts promote plantshop-catalog-service -n plantshop
```

Далі Rollout пройшов решту кроків сам (журнал фаз, знятий під час релізу):

| Час | Крок | Фаза | Готових / оновлених подів |
|---|---|---|---|
| 09:58:52 | setWeight 50 | Progressing | 4 / 2 (+1 под за `maxSurge`) |
| 09:59:29 | pause 30s | Paused | 4 / 2 |
| 10:00:00 | setWeight 75 | Progressing | 4 / 3 |
| 10:00:31 | pause 30s | Paused | 4 / 3 |
| 10:01:03 | 100% | Progressing | 4 / 4 |
| 10:01:34 | — | **Healthy** | 4 / 4 |

Від promote до Healthy минуло ~2 хв 40 с: дві паузи по 30 с плюс старт JVM і `minReadySeconds: 15`
на кожному кроці.

```
Status:          ✔ Healthy
Images:          vladyslavv648/plantshop-catalog-service:1.2.0 (stable)
├──# revision:2   ReplicaSet ✔ Healthy  stable   (4 поди)
└──# revision:1   ReplicaSet • ScaledDown

Argo CD: Synced / Healthy
40 запитів через gateway: 40 × {"version":"1.2.0"}
```

### 4.8 Скасування релізу: 1.2.0 → 1.3.0 → abort (п. 5)

```bash
# tag "1.2.0" → "1.3.0"
git commit -am "Release catalog-service 1.3.0 (canary, to be aborted)" && git push   # ab07d1e
# Rollout: Paused, Step 1/6, 1.2.0 (stable) + 1.3.0 (canary), Argo CD: Synced / Suspended
kubectl argo rollouts abort plantshop-catalog-service -n plantshop
```

Одразу після abort контролер повертає stable ReplicaSet до 4 подів. Через `maxUnavailable: 0` він
спочатку піднімає 4-й под stable і лише потім гасить canary, тож кілька секунд `ActualWeight` ще 25.
Після цього:

```
$ kubectl argo rollouts get rollout plantshop-catalog-service -n plantshop
Status:          ✖ Degraded
Message:         RolloutAborted: Rollout aborted update to revision 3
Strategy:        Canary
  Step:          0/6
  SetWeight:     0
  ActualWeight:  0
Images:          vladyslavv648/plantshop-catalog-service:1.2.0 (stable)
Replicas:        Desired 4  Current 4  Updated 0  Ready 4  Available 4

├──# revision:3   ...-5f5684f74c   ReplicaSet • ScaledDown  canary   (1.3.0)
├──# revision:2   ...-5fc48ff468   ReplicaSet ✔ Healthy     stable   (1.2.0, 4 поди)
└──# revision:1   ...-869cbbf65    ReplicaSet • ScaledDown           (1.1.0)

$ kubectl get application plantshop -n argocd
plantshop   Synced   Degraded

40 запитів через gateway: 40 × {"version":"1.2.0"}
$ kubectl -n plantshop get rollout plantshop-catalog-service -o jsonpath='{.spec.template.spec.containers[0].image}'
vladyslavv648/plantshop-catalog-service:1.3.0
```

**Чому Synced + Degraded.**

- **Synced.** Sync у Argo CD порівнює *маніфест* з Git і *spec* у кластері. У Git тег 1.3.0, і в
  spec Rollout теж 1.3.0 (останній рядок вище). Вони збігаються.
- **Degraded.** Health береться зі *status* Rollout. Контролер зафіксував, що реліз до revision 3
  скасовано (`RolloutAborted`), і працює stable 1.2.0, тобто бажаний стан (1.3.0) не досягнуто.
- Отже, кластер **не зламаний**: користувачі отримують робочу версію 1.2.0. Degraded сигналізує, що Git
  описує версію, від якої відмовились. Виправляти треба Git, а не кластер.

**Завершення відкату через Git:**

```bash
git revert ab07d1e && git push          # 350b63f: tag "1.3.0" → "1.2.0"
kubectl -n argocd annotate application plantshop argocd.argoproj.io/refresh=normal --overwrite
```

```
після 6 с: rollout=Healthy  app=Synced/Healthy

Status:          ✔ Healthy
Images:          vladyslavv648/plantshop-catalog-service:1.2.0 (stable)
├──# revision:4   ...-5fc48ff468   ReplicaSet ✔ Healthy  stable   ← той самий ReplicaSet, що був revision:2
├──# revision:3   ...-5f5684f74c   ReplicaSet • ScaledDown
└──# revision:1   ...-869cbbf65    ReplicaSet • ScaledDown

40 запитів через gateway: 40 × {"version":"1.2.0"}
```

Відкат зайняв **6 секунд**, без жодного нового пода. Pod template з тегом 1.2.0 дає той самий хеш
(`5fc48ff468`), а ReplicaSet з таким хешем уже існує і є stable. Тому контролер лише перенумерував
його в revision 4 і одразу позначив Rollout Healthy, без кроків canary.

Чому не `argocd app rollback` і не `kubectl argo rollouts undo`: з автосинхронізацією Argo CD одразу
поверне стан із Git, тобто знову 1.3.0. Джерело правди — Git, і відкат має бути комітом.

Альтернатива revert: якщо проблему виправлено поза образом (наприклад, у конфігурації чи в залежному
сервісі), той самий реліз можна перезапустити командою
`kubectl argo rollouts retry rollout plantshop-catalog-service -n plantshop`.

### 4.9 Стан після виконання

```
$ kubectl get applications -n argocd
argo-rollouts   Synced   Healthy
plantshop       Synced   Healthy
root            Synced   Healthy

catalog-service: Rollout, 4/4, образ 1.2.0 (stable)
Пам'ять minikube: ~5,4 ГБ з 7 ГБ
```

## 5. Проблеми і як їх вирішено

| Проблема | Причина | Рішення |
|---|---|---|
| Кластер порожній перед початком роботи | minikube перестворено | Argo CD і `plantshop-secret` відновлено командами ЛР-5 (розділ 4.1) |
| Ризик `SyncFailed` на великих CRD | анотація client-side apply може перевищити 256 КБ | `ServerSideApply=true` в Application argo-rollouts |
| Ризик `no matches for kind "Rollout"` | `plantshop` може синхронізуватися раніше за CRD | контролер окремим комітом, `sync-wave: "-1"`, `retry` в `plantshop.yaml` |
| `root` не бачить нової гілки | `root` читає `argocd-apps/` зі своєї `targetRevision` | змінено `targetRevision` у `bootstrap/root.yaml` і повторно застосовано вручну |
| Пам'ять для 4+ JVM | minikube 7 ГБ, ~250 МБ на под catalog | 4 репліки (а не 5), `maxSurge: 1` |
| Розходження Deployment і Rollout при копіюванні шаблону | дві копії pod template | спільний хелпер `_catalog-service-pod.tpl` |
| Зайвий рядок із пробілами після `template:` | хелпер починався з переносу рядка | `-}}` у кінці останньої змінної хелпера |

## 6. Контрольні питання (для захисту)

1. **Чим Rollout відрізняється від Deployment?** Той самий pod template і selector, але замість
   `strategy.rollingUpdate` є `canary`/`blueGreen` з кроками, паузами й аналізом. Керує ним окремий
   контролер Argo Rollouts, а не kube-controller-manager. Реліз можна зупинити, перевірити, довести до
   кінця або скасувати.
2. **Чому canary, а не blue-green?** Див. 4.4: Eureka обходить Kubernetes Service, тож preview у
   blue-green отримував би трафік до перемикання; до того ж canary потребує менше пам'яті.
3. **Як розподіляється трафік без Istio/NGINX?** Пропорційно кількості подів: 1 з 4 = 25%. Тому
   кількість реплік підібрано так, щоб ваги ділились націло.
4. **Чому цикл запитів запускається всередині кластера?** `kubectl port-forward svc/...` вибирає один
   под і тримає з'єднання з ним, тому розподілу не видно.
5. **Що таке `pause: {}` і `pause: {duration: 30s}`?** Перша чекає ручного `promote` нескінченно,
   друга продовжує реліз сама через 30 с.
6. **Що робить `promote`? А `promote --full`?** `promote` знімає поточну паузу і переходить до
   наступного кроку. `--full` пропускає всі кроки, що залишились, і одразу робить 100%.
7. **Чому після abort Argo CD показує Synced + Degraded?** Synced: spec у кластері збігається з Git
   (тег 1.3.0). Degraded: status Rollout = aborted, працює stable-версія, бажаний стан не досягнуто.
8. **Чому відкат через `git revert`, а не `argocd app rollback`?** Git — джерело правди. З
   автосинхронізацією Argo CD одразу повернув би стан із Git. Revert також лишає слід в історії.
9. **Чому `promote`/`abort` не скасовуються через `selfHeal`?** Вони змінюють status Rollout (і
   ReplicaSet-и, якими володіє контролер), а не spec, що описаний у Git.
10. **Чому відкат на 1.2.0 зайняв 6 секунд?** Pod template 1.2.0 має той самий хеш, що й наявний
    stable ReplicaSet. Контролер не створює нових подів, а лише робить цей ReplicaSet поточною ревізією.
11. **Коли Rollout НЕ стартує після зміни values?** Коли не змінюється pod template: зміна `replicas`
    чи кроків стратегії не створює нової ревізії.
12. **Як контролер потрапив у кластер?** Як ще один Application у `argocd-apps/` (App of Apps). Ручних
    `helm install` немає. `sync-wave: -1` синхронізує його раніше за застосунок.
13. **Який ризик canary для цього проєкту?** Дві версії одночасно працюють з однією БД `catalog`.
    Міграції Flyway мають бути зворотно сумісні (expand → contract).
14. **Що додати в реальному проєкті?** `AnalysisTemplate` (автоматичний promote/abort за метриками
    Prometheus: частка 5xx, латентність), traffic router для точних ваг незалежно від кількості подів,
    Argo CD Image Updater або CI, що комітить тег.

## 7. Висновки

Оновлення `catalog-service` переведено з rolling update Deployment на **canary-реліз через Argo
Rollouts**. Контролер встановлено з Git як окремий Application. Rollout вмикається параметром у
values і повністю замінює Deployment, а pod template спільний для обох. Реліз 1.2.0 зроблено
комітом. На ручній паузі виміряно розподіл 30/10 (25%) як через Service, так і через api-gateway.
Після `promote` реліз пройшов кроки 50% і 75% з таймерами й став Healthy. Реліз 1.3.0 скасовано
`abort` на першому кроці: трафік повернувся на 1.2.0, Argo CD показав Synced + Degraded, і відкат
завершено `git revert` за 6 секунд без перезапуску подів. Секрети в Git не потрапили, ручних змін
spec у кластері немає.
