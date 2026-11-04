# Лабораторна робота №5. GitOps: розгортання проєкту через Argo CD

**Дисципліна:** Технології DevOps
**Проєкт:** Plant Shop — інтернет-магазин рослин на мікросервісах (Java 21, Spring Boot 3.5, Spring Cloud, PostgreSQL, Kafka)
**Студент:** Горин Владислав (GitHub `VladdHoryn`, Docker Hub `vladyslavv648`)
**Репозиторій:** https://github.com/VladdHoryn/Plant_Shop, гілка `lab/DevOps_lab_5`

---

## 1. Мета роботи

Перевести розгортання застосунку з ручного `helm install` (ЛР-4) на модель GitOps: стан кластера
описується лише в Git, а Argo CD сам приводить кластер до цього опису. Показати:

- розгортання через патерн **App of Apps** (один ручний `kubectl apply` на весь проєкт);
- **реліз нової версії** лише комітом у Git;
- **дрейф** конфігурації і його автоматичне виправлення (`selfHeal`);
- стан **Synced + Degraded** при неіснуючому образі та **відкат через `git revert`**.

## 2. Середовище

| Компонент | Значення |
|---|---|
| Кластер | minikube v1.34 (Kubernetes), драйвер docker, 4 CPU / 7 ГБ (максимум, який дає Docker Desktop з 7,6 ГБ) |
| Argo CD | встановлений у namespace `argocd` (офіційний `install.yaml`, гілка stable), CLI `~/bin/argocd.exe` v3.6.1 |
| Helm | лише для `helm lint` / `helm template` локально; у кластері релізів Helm немає |
| Реєстр образів | Docker Hub, публічні репозиторії `vladyslavv648/plantshop-<module>` |
| Репозиторій конфігурації | той самий, що й код: `Plant_Shop.git`, гілка `lab/DevOps_lab_5` |

**Варіант репозиторію.** Інструкція допускає два варіанти: окремий репозиторій `myapp-gitops` або
той самий репозиторій застосунку. Обрано **той самий репозиторій** (структура тека в теку однакова).
CI-пайплайна в проєкті немає, тож обмежувати його `paths-ignore` не потрібно. Якщо його додадуть,
шляхи `bootstrap/`, `argocd-apps/` і `charts/` треба виключити з тригерів збірки образу.

## 3. Архітектура рішення

```
                 git push (зміна values-minikube.yaml / argocd-apps/*.yaml)
  Розробник ─────────────────────────────────────────────►  GitHub: Plant_Shop.git @ lab/DevOps_lab_5
                                                                  │  опитування ~3 хв або Refresh
                                                                  ▼
  kubectl apply -f bootstrap/root.yaml (один раз)        ┌──────────────── Argo CD (ns argocd) ───────────────┐
  ──────────────────────────────────────────────────────►│ Application root  → path argocd-apps/              │
                                                         │      └─ створює Application plantshop              │
                                                         │ Application plantshop → path charts/plantshop      │
                                                         │      + values-minikube.yaml → helm template        │
                                                         └───────────────────────┬────────────────────────────┘
                                                                                 │ kubectl apply (sync), prune, selfHeal
                                                                                 ▼
                                                  namespace plantshop: 12 Deployment, 12 Service, 5 PVC, ConfigMap
                                                  + Secret plantshop-secret (створений вручну, НЕ з Git)
                                                                                 │ docker pull
                                                                                 ▼
                                                  Docker Hub: vladyslavv648/plantshop-<module>:1.0.0 / 1.1.0
```

Склад застосунку (усе в namespace `plantshop`):

- платформні сервіси: `config-service` (Spring Cloud Config), `discovery-service` (Eureka), `api-gateway`;
- бізнес-сервіси: `catalog-service`, `inventory-service`, `order-service`, `payment-service`;
- 4 PostgreSQL (база на сервіс) і Kafka (сага замовлення).

### Структура файлів GitOps

```
Plant_Shop/                         (гілка lab/DevOps_lab_5)
├── bootstrap/
│   └── root.yaml                   кореневий Application, застосовується вручну один раз
├── argocd-apps/
│   └── plantshop.yaml              Application plantshop (керується root)
├── charts/
│   └── plantshop/                  копія чарту з ЛР-4 (helm/plantshop), адаптована під Argo CD
│       ├── Chart.yaml              version 0.2.0 (у ЛР-4 — 0.1.0)
│       ├── values.yaml             значення за замовчуванням
│       ├── values-dev.yaml, values-prod.yaml   середовища з ЛР-4 (лишились для helm install)
│       ├── values-minikube.yaml    СЕРЕДОВИЩЕ ARGO CD — єдиний файл, який змінюється при релізах
│       └── templates/
├── helm/plantshop/                 чарт ЛР-4 без змін
└── catalog-service/ ...            код застосунку
```

## 4. Хід виконання

### 4.1 Підготовка

- Гілку `lab/DevOps_lab_5` створено від коміту ЛР-4 (`1532a21 lab_4 commit`), де є `helm/plantshop`;
  назва відповідає шаблону `lab/*`.
- У кластері лишилися залишки попередньої спроби (гілка `gitops-lab`): Application `root`, що дивився на
  `gitops-lab`, і namespace `plantshop` зі старим Secret. Їх видалено (`kubectl -n argocd delete application root`,
  `kubectl delete namespace plantshop`), щоб Argo CD стартував начисто і нові паролі не конфліктували
  зі старими PVC. Helm-релізів ЛР-4 у кластері не було (`helm list -A` порожній).

### 4.2 Версія застосунку (APP_VERSION)

Механізм версії вже існував з ЛР-4: спільний `Dockerfile` приймає `--build-arg APP_VERSION` і записує
його в змінну оточення образу, а `config-service/.../configurations/catalog-service.yml` передає її в
`plantshop.version: ${APP_VERSION:dev}`. Його використано, а не продубльовано. Додано лише ендпоінт:

```java
// catalog-service/src/main/java/com/plantshop/catalog/controller/InfoController.java
@GetMapping("/version")
public Map<String, String> version() {
    return Map.of("version", version);   // version = ${plantshop.version} = APP_VERSION
}
```

`GET /api/catalog/version` через api-gateway повертає `{"version":"1.0.0"}`. Додано юніт-тест
`InfoControllerTest` (2 тести). `./mvnw -pl catalog-service test` — усі тести пройшли (4/4).

Версія **закладається в образ під час збірки**, а не задається чартом. Тому зміна тегу образу в Git
автоматично змінює і версію, яку бачить клієнт: тег і версія не можуть розійтися.

### 4.3 Образи в Docker Hub

Зібрано на Docker хоста й опубліковано:

```bash
for s in config-service discovery-service api-gateway catalog-service inventory-service order-service payment-service; do
  docker build --build-arg SERVICE=$s --build-arg APP_VERSION=1.0.0 -t vladyslavv648/plantshop-$s:1.0.0 .
  docker push vladyslavv648/plantshop-$s:1.0.0
done
docker build --build-arg SERVICE=catalog-service --build-arg APP_VERSION=1.1.0 -t vladyslavv648/plantshop-catalog-service:1.1.0 .
docker push vladyslavv648/plantshop-catalog-service:1.1.0
```

Разом 8 образів: 7 × `1.0.0` + `catalog-service:1.1.0` (перевірено: `APP_VERSION=1.1.0` в образі).
Репозиторії Docker Hub публічні, тому `imagePullSecrets` не потрібні.

### 4.4 Чарт для Argo CD: відмінності від ЛР-4

| Що змінено | Де | Навіщо |
|---|---|---|
| Параметр `existingSecret` | `values.yaml`, `_helpers.tpl` (`plantshop.secretName`), `secret.yaml` | Якщо заданий, шаблон Secret не рендериться, а **всі** посилання (4 БД `secretKeyRef`, 4 сервіси `envFrom.secretRef`, hook-Job) ідуть на Secret, створений вручну. Усі посилання проходять через один helper, тож змінено одне місце |
| Прибрано `database.password` з `values.yaml` | `values.yaml` | Паролів у values-файлах немає зовсім |
| `progressDeadlineSeconds` | `values.yaml`, `catalog-service-deployment.yaml` | Для catalog-service `120` с замість стандартних 600: зламаний реліз стає `Degraded` за ~2 хв |
| `tests.enabled` | `values.yaml`, `tests/test-api.yaml` | Можливість вимкнути pod `helm test` |
| `version: 0.2.0` | `Chart.yaml` | Чарт змінився — версію чарту піднято |

Перевизначення тегу на рівні сервісу **вже було в чарті ЛР-4** (`<service>.image.tag` має пріоритет над
глобальним `image.tag`, далі `appVersion`), тож додавати його не довелося. Реліз і поганий тег стосуються
лише `catalogService.image.tag`. HPA у чарті немає, тому вимикати нічого.

Ключовий фрагмент (`templates/_helpers.tpl` і `templates/secret.yaml`):

```yaml
{{- define "plantshop.secretName" -}}
{{- .Values.existingSecret | default (include "plantshop.componentName" (dict "root" . "component" "db-secret")) }}
{{- end }}

# secret.yaml
{{- if not .Values.existingSecret }}
... Secret з lookup / randAlphaNum ...
{{- end }}
```

**Середовище `values-minikube.yaml`** (стан перед захистом):

```yaml
existingSecret: plantshop-secret          # Secret створено вручну, у Git його немає
image:
  tag: "1.0.0"
  pullPolicy: IfNotPresent
configService:    { replicaCount: 1, image: { repository: vladyslavv648/plantshop-config-service } }
discoveryService: { replicaCount: 1, image: { repository: vladyslavv648/plantshop-discovery-service } }
apiGateway:
  replicaCount: 1
  image: { repository: vladyslavv648/plantshop-api-gateway }
  service: { type: ClusterIP }            # доступ через port-forward
catalogService:
  replicaCount: 1
  progressDeadlineSeconds: 120
  image:
    repository: vladyslavv648/plantshop-catalog-service
    tag: "1.0.0"                          # власний тег сервісу — саме його змінює реліз
inventoryService / orderService / paymentService: 1 репліка, образи vladyslavv648/...
ingress:   { enabled: false }
hooks:     { preUpgradeCheck: { enabled: false } }
tests:     { enabled: false }
```

(У файлі кожен сервіс записаний розгорнуто, тут скорочено.)

**Чому вимкнені Ingress, hook і test:**

- **Ingress.** Без `minikube tunnel` Ingress не отримує адресу, і Argo CD вважає його `Progressing`
  назавжди: застосунок ніколи не стане `Healthy`. Доступ через `kubectl port-forward`.
- **pre-upgrade hook** (з ЛР-4: Job перевіряє версії Flyway у 4 БД). Argo CD перетворює Helm-хуки
  `pre-upgrade` на `PreSync` і виконує їх **перед кожною** синхронізацією, зокрема першою, коли БД ще
  не існують. Job впав би і заблокував перший sync.
- **helm test.** Argo CD хуки `test` не запускає, тож pod тесту лише заважав би.
- **Secret з `lookup`.** У ЛР-4 Secret генерував випадковий пароль через `lookup` + `randAlphaNum`.
  Argo CD рендерить чарт через `helm template` без доступу до кластера: `lookup` повертає порожнє, і
  пароль був би **новим при кожному рендері**. Це постійний OutOfSync і зламані БД. `existingSecret`
  прибирає цю проблему повністю.

Перевірка:

```
$ helm lint charts/plantshop --strict -f charts/plantshop/values-minikube.yaml
1 chart(s) linted, 0 chart(s) failed
$ helm template plantshop charts/plantshop -f charts/plantshop/values-minikube.yaml | grep '^kind:' | sort | uniq -c
      1 kind: ConfigMap
     12 kind: Deployment
      5 kind: PersistentVolumeClaim
     12 kind: Service                  # Secret, Job, Pod тесту, Ingress — відсутні
```

Чарт і далі працює як звичайний Helm-чарт ЛР-4 (`helm lint ... -f values-prod.yaml` проходить, без
`existingSecret` рендериться власний Secret).

### 4.5 Встановлення Argo CD і секрет

Argo CD уже був встановлений у кластері попередньою спробою:

```bash
kubectl create namespace argocd
kubectl apply -n argocd --server-side --force-conflicts \
  -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
kubectl -n argocd port-forward svc/argocd-server 8080:443
argocd admin initial-password -n argocd
argocd login localhost:8080 --username admin --insecure
argocd account update-password
kubectl -n argocd delete secret argocd-initial-admin-secret
```

Пароль admin змінено, `argocd-initial-admin-secret` видалено (`kubectl -n argocd get secret
argocd-initial-admin-secret` → `NotFound`). CLI залогінений.

Секрет БД створено вручну. Пароль згенеровано й одразу передано в kubectl, ніде не виведено і в Git не потрапив:

```bash
kubectl create namespace plantshop
kubectl -n plantshop create secret generic plantshop-secret \
  --from-literal=DB_USERNAME=plantshop \
  --from-literal=DB_PASSWORD="$(head -c 32 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 24)"
```

Argo CD цей Secret не видалить навіть з `prune: true`: він не створений з Git і не має мітки
відстеження Argo CD.

### 4.6 App of Apps

`argocd-apps/plantshop.yaml` (початковий стан, **без selfHeal**):

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: plantshop                 # = назва Helm-релізу → об'єкти plantshop-<component>
  namespace: argocd
spec:
  project: default
  source:
    repoURL: https://github.com/VladdHoryn/Plant_Shop.git
    targetRevision: lab/DevOps_lab_5
    path: charts/plantshop
    helm:
      valueFiles:
        - values-minikube.yaml    # відносно каталогу чарту
  destination:
    server: https://kubernetes.default.svc
    namespace: plantshop
  syncPolicy:
    automated:
      prune: true
    syncOptions:
      - CreateNamespace=true
```

`bootstrap/root.yaml` стежить за каталогом `argocd-apps/` (`destination.namespace: argocd`,
`automated: {prune: true, selfHeal: true}`).

```bash
git push origin lab/DevOps_lab_5
kubectl apply -f bootstrap/root.yaml          # єдиний ручний apply
```

Результат (через ~4 хв, поки стартують JVM):

```
$ argocd app list
NAME              NAMESPACE  STATUS  HEALTH   SYNCPOLICY  PATH              TARGET
argocd/plantshop  plantshop  Synced  Healthy  Auto-Prune  charts/plantshop  lab/DevOps_lab_5
argocd/root       argocd     Synced  Healthy  Auto-Prune  argocd-apps       lab/DevOps_lab_5

$ helm list -n plantshop
NAME	NAMESPACE	REVISION	UPDATED	STATUS	CHART	APP VERSION      ← порожньо: Helm лише рендерить шаблони

$ kubectl -n plantshop get secret
plantshop-secret   Opaque   2                                      ← єдиний Secret, створений вручну
```

Застосунок з першого разу став `Synced/Healthy`, правки чарту не знадобилися: усі причини зависання
(Ingress, PreSync-hook, `lookup`) усунено заздалегідь (п. 4.4).

### 4.7 Реліз через Git: 1.0.0 → 1.1.0

```bash
kubectl -n plantshop port-forward svc/plantshop-api-gateway 8081:80
curl localhost:8081/api/catalog/version                  # {"version":"1.0.0"}
# values-minikube.yaml: catalogService.image.tag "1.0.0" → "1.1.0"
git commit -m "Release catalog-service 1.1.0" charts/plantshop/values-minikube.yaml && git push
argocd app get plantshop --refresh                       # Sync Status: OutOfSync from lab/DevOps_lab_5 (dcca840)
kubectl -n plantshop rollout status deploy/plantshop-catalog-service
curl localhost:8081/api/catalog/version                  # {"version":"1.1.0"}
```

Після Refresh Argo CD побачив новий коміт (OutOfSync) і автоматично синхронізувався. Deployment
провів rolling update (`maxSurge: 1`, `maxUnavailable: 0`), тож сервіс не переривався.

### 4.8 Дрейф і selfHeal

**Без selfHeal:**

```
$ kubectl -n plantshop scale deployment plantshop-catalog-service --replicas=3
$ argocd app get plantshop --refresh
Sync Status:        OutOfSync from lab/DevOps_lab_5 (dcca840)
apps  Deployment  plantshop  plantshop-catalog-service  OutOfSync  Progressing
$ kubectl -n plantshop get deploy plantshop-catalog-service       # через 50 с
plantshop-catalog-service   3/3     3            3
```

Argo CD **виявив** розбіжність (OutOfSync), але **не виправив** її: автосинхронізація реагує лише на
нові коміти, а ручні зміни в кластері виправляє тільки `selfHeal`.

**Увімкнення selfHeal комітом** у `argocd-apps/plantshop.yaml`:

```yaml
  syncPolicy:
    automated:
      prune: true
      selfHeal: true    # revert manual changes in the cluster
```

Після push кореневий `root` (у нього самого `selfHeal: true`) оновив Application `plantshop`.
Argo CD одразу повернув репліки до 1:

```
$ kubectl -n argocd get application plantshop -o jsonpath='{.spec.syncPolicy.automated}'
{"prune":true,"selfHeal":true}
$ kubectl -n plantshop get deploy plantshop-catalog-service
plantshop-catalog-service   1/1     1            1
```

**Повторний scale з selfHeal:**

```
--- +0s   plantshop-catalog-service   1/3   3     1
--- +3s   plantshop-catalog-service   1/1   1     1      ← повернуто за ~3 секунди
--- +15s  plantshop-catalog-service   1/1   1     1
```

### 4.9 Synced + Degraded і відкат через git revert

```bash
# tag "1.1.0" → "9.9.9" (образу не існує)
git commit -m "Release catalog-service 9.9.9 (broken)" charts/plantshop/values-minikube.yaml && git push
argocd app get plantshop --refresh
```

Через ~2 хв (`progressDeadlineSeconds: 120`):

```
Sync Status:        Synced to lab/DevOps_lab_5 (9d84cd4)
Health Status:      Degraded
apps   Deployment   plantshop  plantshop-catalog-service    Synced  Degraded

$ kubectl -n plantshop get pods -l app.kubernetes.io/component=catalog-service
plantshop-catalog-service-6b7994c775-tkjnn   0/1     ImagePullBackOff   0          2m9s
plantshop-catalog-service-7d9b646dc6-ghsmr   1/1     Running            0          4m47s
$ curl localhost:8081/api/catalog/version
{"version":"1.1.0"}                       ← старий под живий завдяки maxUnavailable: 0
```

**Synced**: кластер точно відповідає Git. **Degraded**: сам опис у Git несправний (образу немає).
Це дві незалежні осі стану.

Відкат: повертаємо **опис**, а не кластер:

```
$ git revert --no-edit 9d84cd4 && git push
$ argocd app get plantshop --refresh      → Synced / Healthy, под ImagePullBackOff видалено
$ curl localhost:8081/api/catalog/version → {"version":"1.1.0"}

$ git log --oneline -8
c1a1d2a Revert "Release catalog-service 9.9.9 (broken)"
10babd2 Add Lab 5 instructions
9d84cd4 Release catalog-service 9.9.9 (broken)
0413858 Enable selfHeal for plantshop
dcca840 Release catalog-service 1.1.0
70c1bdb Add GET /api/catalog/version backed by APP_VERSION
d49d353 Deploy plantshop via Argo CD (App of Apps)
1532a21 lab_4 commit

$ argocd app history plantshop
ID  DATE                           REVISION
0   2026-11-04 21:58:49 +0200 EET  lab/DevOps_lab_5 (70c1bdb)   початкове розгортання
1   2026-11-04 22:01:58 +0200 EET  lab/DevOps_lab_5 (dcca840)   реліз 1.1.0
2   2026-11-04 22:03:54 +0200 EET  lab/DevOps_lab_5 (0413858)   selfHeal повернув репліки
3   2026-11-04 22:04:36 +0200 EET  lab/DevOps_lab_5 (9d84cd4)   зламаний тег 9.9.9
4   2026-11-04 22:09:59 +0200 EET  lab/DevOps_lab_5 (c1a1d2a)   revert
```

Кожна синхронізація прив'язана до коміту, тож історія Git є журналом змін кластера.
`argocd app rollback` тут не підходить: з автосинхронізацією Argo CD одразу повернув би стан із Git.

### 4.10 Стан перед захистом

Окремим комітом тег повернуто на `1.0.0`, щоб на захисті показати реліз 1.0.0 → 1.1.0 ще раз:

```
$ argocd app list                 → root і plantshop: Synced / Healthy
$ kubectl -n argocd get application plantshop -o jsonpath={.spec.syncPolicy}
{"automated":{"prune":true,"selfHeal":true},"syncOptions":["CreateNamespace=true"]}
$ kubectl -n plantshop get deploy → усі 12 READY 1, усі сервіси на образах vladyslavv648/...:1.0.0
$ curl localhost:8081/api/catalog/version → {"version":"1.0.0"}
```

У Docker Hub лежать обидві версії (1.0.0 для всіх модулів і 1.1.0 для catalog-service).
Сценарій демонстрації: [`docs/lab5-defense.md`](lab5-defense.md).

## 5. Проблеми і як їх вирішено

| Проблема | Причина | Рішення |
|---|---|---|
| Застаріла `root` у кластері дивилась на гілку `gitops-lab` | Попередня спроба лаби | Видалено Application `root` і namespace `plantshop`, потім застосовано новий `root.yaml` |
| Випадковий пароль у Secret змінювався б при кожному рендері | `lookup` не працює в `helm template` (Argo CD) | `existingSecret` + Secret, створений вручну |
| PreSync-hook заблокував би перший sync | Argo CD виконує `pre-upgrade` хуки як PreSync перед кожною синхронізацією | `hooks.preUpgradeCheck.enabled: false` |
| Застосунок зависав би в `Progressing` | Ingress без адреси (немає `minikube tunnel`) | `ingress.enabled: false`, доступ через port-forward |
| `git revert` відмовлявся працювати | В індексі лишалися застейджені файли іншої лабораторної | Індекс очищено (PDF інструкції закомічено, сторонній файл знято зі stage), далі `git commit <шлях>` лише для `values-minikube.yaml` |
| Один запит дав 500 одразу після заміни пода | Кеш Eureka в api-gateway кілька секунд тримає IP старого пода; з 1 реплікою повтор GET нікуди не йде | Не баг GitOps: через ~30 с усі запити 200. На захисті зачекати перед curl |
| Docker Desktop має лише 7,6 ГБ, а рекомендовано 8 ГБ | Обмеження машини | minikube `--cpus=4 --memory=7g`: усього стеку (12 подів + Argo CD) вистачає |

## 6. Контрольні питання (для захисту)

1. **Що таке GitOps?** Git — єдине джерело правди про бажаний стан. Агент у кластері (Argo CD) постійно
   порівнює кластер з Git і приводить його у відповідність (pull-модель), замість того щоб CI пушив зміни
   в кластер командами.
2. **Push vs pull.** У push-моделі CI має облікові дані кластера і виконує `kubectl/helm`. У pull-моделі
   кластер сам тягне конфігурацію з Git, облікові дані кластера назовні не виходять, а дрейф виявляється автоматично.
3. **Sync Status vs Health Status.** Sync — чи збігається кластер з Git (Synced/OutOfSync). Health — чи
   працюють ресурси (Healthy/Progressing/Degraded/Missing). Synced + Degraded = кластер точно як у Git,
   але в Git описано несправний стан.
4. **prune.** Видаляє з кластера ресурси, які прибрали з Git. Без нього вони лишаються «сиротами».
5. **selfHeal.** Повертає ручні зміни живих ресурсів до стану з Git. Без нього Argo CD лише показує OutOfSync.
6. **App of Apps.** Кореневий Application, який розгортає інші Application з каталогу. Вручну
   застосовується лише `root.yaml`; новий компонент (Argo Rollouts, моніторинг у ЛР-6/7) = новий файл в `argocd-apps/`.
7. **Чому `helm list` порожній?** Argo CD не встановлює Helm-реліз, а рендерить чарт (`helm template`) і
   застосовує маніфести сам. Назва Application стає назвою «релізу» для шаблонів (`.Release.Name`).
8. **Чому відкат через `git revert`, а не `argocd app rollback`?** З автосинхронізацією rollback одразу
   перетреться станом із Git. `git revert` змінює саме джерело правди й лишає слід в історії.
9. **Як зберігаються секрети?** Не в Git: `existingSecret` вказує на Secret, створений вручну. Prune його
   не видаляє, бо Argo CD його не відстежує. Повноцінні рішення: Sealed Secrets, SOPS, External Secrets.
10. **Як Argo CD дізнається про коміт?** Опитує репозиторій раз на ~3 хв. Refresh (або вебхук) — одразу.
11. **Навіщо `progressDeadlineSeconds: 120`?** Deployment позначає rollout як невдалий
    (`ProgressDeadlineExceeded`) через 2 хв замість 10, і Argo CD показує `Degraded` швидше.
12. **Чому реліз змінює лише `catalogService.image.tag`?** Сервісний тег має пріоритет над глобальним
    `image.tag`, тож реліз чи зламаний тег зачіпають тільки один Deployment, а не весь стек.

## 7. Висновки

Розгортання Plant Shop переведено на GitOps. Кластер описується файлами в гілці `lab/DevOps_lab_5`, а
Argo CD за патерном App of Apps розгортає 30 об'єктів з одного ручного `kubectl apply -f bootstrap/root.yaml`.

Продемонстровано:

- реліз нової версії одним комітом (1.0.0 → 1.1.0);
- виявлення дрейфу (OutOfSync) і його автоматичне виправлення після ввімкнення `selfHeal`;
- різницю між Sync і Health на неіснуючому образі (Synced + Degraded);
- відкат через `git revert` з відстеженням кожної синхронізації до коміту в `argocd app history`.

Секрети в Git не потрапили. Чарт ЛР-4 доопрацьовано мінімально: `existingSecret`, `progressDeadlineSeconds`,
вимикання test-хука. Решта несумісностей з Argo CD (PreSync-hook, Ingress) знята через values середовища.
