# ЛР-6: шпаргалка до захисту (canary з Argo Rollouts)

Репозиторій: `https://github.com/VladdHoryn/Plant_Shop.git`, гілка `lab/DevOps_lab_6`
(вона ж `targetRevision` у `root` і `plantshop`). Повний звіт: `docs/DevOps_lab_6.md`.
Усі команди виконуються в **Git Bash з кореня репозиторію**. `argocd`, `kubectl`, `kubectl argo rollouts`
беруться з `~/bin`.

Імена, які знадобляться:

| Що | Значення |
|---|---|
| Rollout / Service | `plantshop-catalog-service` (порт 8081), namespace `plantshop` |
| Gateway | `plantshop-api-gateway` (порт 80) |
| Образи | `vladyslavv648/plantshop-catalog-service:1.0.0 … 1.4.0` (усі вже в Docker Hub) |
| Стан перед захистом | тег catalog-service **1.2.0**, Rollout Healthy, усі Application Synced / Healthy |

План показу: **реліз 1.4.0** (пауза → promote → Healthy), потім **реліз 1.3.0** (пауза → abort →
Synced + Degraded → `git revert`).

---

## 1. Підготовка (за 10–15 хв до захисту)

1. Запустити **Docker Desktop**, дочекатися «Engine running».
2. Кластер:
   ```bash
   export PATH="$HOME/bin:$PATH"
   minikube start                                  # профіль уже має --cpus=4 --memory=7g
   kubectl get applications -n argocd              # argo-rollouts, plantshop, root: Synced / Healthy
   kubectl -n plantshop get pods                   # усі 1/1 Running (JVM стартують 2–3 хв)
   kubectl argo rollouts version                   # v1.10.2
   ```
   Якщо `plantshop` довго `Unknown`/`Progressing` після старту (Argo CD ще не бачить github.com):
   `kubectl -n argocd annotate application plantshop argocd.argoproj.io/refresh=normal --overwrite`.
3. Перевірити, що в Git і в кластері 1.2.0:
   ```bash
   git status                                      # чисто, гілка lab/DevOps_lab_6
   git pull
   grep -n '^    tag:' charts/plantshop/values-minikube.yaml    # tag: "1.2.0"
   kubectl argo rollouts get rollout plantshop-catalog-service -n plantshop   # Healthy, 1.2.0 (stable)
   ```
4. Відкрити **5 терміналів** (Git Bash, у кожному `export PATH="$HOME/bin:$PATH"`):

   | # | Термінал | Команда |
   |---|---|---|
   | T1 | Argo CD port-forward | `kubectl -n argocd port-forward svc/argocd-server 8080:443` |
   | T2 | Стан Rollout (не закривати) | `kubectl argo rollouts get rollout plantshop-catalog-service -n plantshop --watch` |
   | T3 | Цикл запитів у кластері | див. нижче |
   | T4 | Дашборд Rollouts (за бажанням) | `kubectl argo rollouts dashboard` → http://localhost:3100 |
   | T5 | Робочий: git, promote, abort | — |

   T3, цикл запитів **зсередини кластера** (через gateway, як ходять користувачі):
   ```bash
   kubectl -n plantshop run curl --rm -it --image=curlimages/curl -- \
     sh -c 'while true; do curl -s http://plantshop-api-gateway/api/catalog/version; echo; sleep 0.5; done'
   ```
   Якщо Git Bash псує шлях `/api/...`, додати на початку `MSYS_NO_PATHCONV=1`. Якщо под `curl`
   лишився з минулого разу: `kubectl -n plantshop delete pod curl`.

5. Увійти в Argo CD (пароль admin знаєте лише ви):
   ```bash
   argocd login localhost:8080 --username admin --insecure
   argocd app list                                  # три застосунки Synced / Healthy
   ```
   UI: https://localhost:8080 (самопідписаний сертифікат → «Продовжити»), відкрити плитку **plantshop**.

---

## 2. Демонстрація

### 2.1 Що встановлено і як (п. 1–3): 2–3 хв

```bash
cat argocd-apps/argo-rollouts.yaml               # Application для Helm-чарту, sync-wave -1
argocd app get argo-rollouts                     # Synced / Healthy
kubectl -n argo-rollouts get pods                # 2 поди контролера
kubectl get crd | grep argoproj                  # rollouts.argoproj.io, analysistemplates...
kubectl -n plantshop get rollout,deploy -l app.kubernetes.io/component=catalog-service
                                                 # є Rollout, Deployment catalog-service немає
sed -n '/^catalogService:/,/^  image:/p' charts/plantshop/values-minikube.yaml   # canary-кроки
```

Що сказати:
- Контролер — ще один Application в `argocd-apps/`, його підхопив `root`. Ручного `helm install` немає.
- `sync-wave: -1` + `retry` у plantshop: CRD з'являються раніше, ніж Argo CD застосує Rollout.
- У чарті `catalog-service-rollout.yaml` вмикається параметром `catalogService.rollout.enabled`,
  а Deployment за тієї ж умови не рендериться. Pod template спільний (`_catalog-service-pod.tpl`).
- 4 репліки: 25/50/75% = 1/2/3 поди. Кроки: 25 → **ручна пауза** → 50 → 30 с → 75 → 30 с.
- **Чому canary:** gateway ходить до catalog через Eureka (`lb://catalog-service`), а не через Service.
  У blue-green preview-поди одразу отримали б трафік через Eureka, тож перемикання Service нічого б
  не давало. До того ж blue-green потребує 8 JVM замість 5.

В UI: plantshop → дерево: `Rollout plantshop-catalog-service` → ReplicaSet-и → Pod-и.

### 2.2 Реліз комітом: 1.2.0 → 1.4.0 (п. 4): ~5 хв

T5:
```bash
sed -i 's/^    tag: ".*"$/    tag: "1.4.0"/' charts/plantshop/values-minikube.yaml
git diff                                          # змінився лише catalogService.image.tag
git commit -am "Release catalog-service 1.4.0" && git push
argocd app get plantshop --refresh                # або кнопка Refresh в UI
```

Через ~1 хв (новий под стартує й чекає `minReadySeconds: 15`):

- **T2:** `Status: ॥ Paused`, `Message: CanaryPauseStep`, `Step: 1/6`, `SetWeight: 25`,
  образи `1.2.0 (stable)` і `1.4.0 (canary)`, у дереві 1 под canary + 3 stable.
- **T3:** приблизно кожна четверта відповідь `{"version":"1.4.0"}`.
- **Argo CD:**
  ```bash
  argocd app get plantshop | head -15             # Sync Status: Synced, Health Status: Suspended
  ```
  UI: plantshop жовтий **Suspended**, у дереві два ReplicaSet-и.

Порахувати співвідношення точно (40 запитів):
```bash
kubectl -n plantshop run curl-count --rm -i --restart=Never --image=curlimages/curl -- \
  sh -c 'for i in $(seq 1 40); do curl -s http://plantshop-api-gateway/api/catalog/version; echo; done | sort | uniq -c'
#   30 {"version":"1.2.0"}
#   10 {"version":"1.4.0"}
```

**Promote** на ручній паузі:
```bash
kubectl argo rollouts promote plantshop-catalog-service -n plantshop
```
Або в UI: дерево → Rollout → **⋮ → Resume**. Також можна в дашборді (кнопка Promote).

Далі ~2,5 хв, коментувати по T2/T3: 50% (2 поди) → пауза 30 с → 75% (3 поди) → пауза 30 с → 100%.
Фінал:
```bash
kubectl argo rollouts get rollout plantshop-catalog-service -n plantshop   # ✔ Healthy, 1.4.0 (stable)
argocd app get plantshop | head -15                                         # Synced / Healthy
```
T3: лише `{"version":"1.4.0"}`.

### 2.3 Скасування релізу: 1.4.0 → 1.3.0 → abort (п. 5): ~3 хв

T5:
```bash
sed -i 's/^    tag: ".*"$/    tag: "1.3.0"/' charts/plantshop/values-minikube.yaml
git commit -am "Release catalog-service 1.3.0" && git push
argocd app get plantshop --refresh
```
Дочекатися паузи на 25% (T2: Paused, `1.3.0 (canary)`; T3 зрідка показує 1.3.0). Тоді:
```bash
kubectl argo rollouts abort plantshop-catalog-service -n plantshop
```
Або UI: Rollout → **⋮ → Abort**.

Показати (через ~20–40 с, коли stable знову має 4 поди):
```bash
kubectl argo rollouts get rollout plantshop-catalog-service -n plantshop
#   Status: ✖ Degraded   Message: RolloutAborted: Rollout aborted update to revision N
#   Images: 1.4.0 (stable)      — canary ReplicaSet ScaledDown
argocd app get plantshop | head -15
#   Sync Status: Synced     Health Status: Degraded
kubectl -n plantshop get rollout plantshop-catalog-service \
  -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'
#   ...:1.3.0   ← spec як у Git
```
T3: лише `{"version":"1.4.0"}`.

**Пояснення Synced + Degraded** (ключове питання захисту):
- *Synced*: spec Rollout у кластері = маніфест із Git (там 1.3.0, видно в jsonpath вище).
- *Degraded*: status Rollout = aborted. Працює stable 1.4.0, бажаний стан (1.3.0) не досягнуто.
- Користувачам нічого не загрожує (усі отримують 1.4.0), але Git описує версію, від якої
  відмовились. Виправляти треба Git.

**Відкат через Git:**
```bash
git revert --no-edit HEAD && git push
argocd app get plantshop --refresh
kubectl argo rollouts get rollout plantshop-catalog-service -n plantshop   # ✔ Healthy, 1.4.0
argocd app get plantshop | head -15                                         # Synced / Healthy
git log --oneline -4                                                        # release → revert видно в історії
```
Відкат займає секунди і не створює нових подів: pod template 1.4.0 має той самий хеш, що й stable
ReplicaSet, тож контролер просто робить його поточною ревізією.

Чому не `argocd app rollback` / `kubectl argo rollouts undo`: автосинхронізація одразу поверне стан
із Git. Альтернатива revert: `kubectl argo rollouts retry rollout plantshop-catalog-service -n plantshop`,
якщо проблему виправили поза образом і той самий реліз треба повторити.

---

## 3. Якщо щось пішло не так

| Симптом | Що робити |
|---|---|
| Після push нічого не відбувається | `argocd app get plantshop --refresh`; перевірити `git log origin/lab/DevOps_lab_6 -1` |
| Реліз не стартує | Тег не змінився або змінились лише `replicas`/кроки: Rollout стартує тільки при зміні pod template |
| T3 не показує нової версії | Цикл має бути в поді (`kubectl run curl ...`), не через `port-forward`. Eureka оновлює список ~30 с, тож почекати |
| Под canary `Pending` | Пам'ять: `docker stats minikube --no-stream`. Закрити дашборд (T4) або видалити зайві поди `curl` |
| `SyncFailed: no matches for kind "Rollout"` | `argocd app get argo-rollouts` → дочекатися Healthy, потім `argocd app sync plantshop` |
| Rollout `Degraded` через `ProgressDeadlineExceeded` | Под не став готовим за 120 с (наприклад, неіснуючий тег). Відкат так само: `git revert` |
| Демо треба повторити | Почати з 2.2 з наступним тегом (1.2.0 або 1.1.0 теж є в Docker Hub) |

Повернути стартовий стан (1.2.0) перед повторною репетицією:
```bash
sed -i 's/^    tag: ".*"$/    tag: "1.2.0"/' charts/plantshop/values-minikube.yaml
git commit -am "Reset catalog-service to 1.2.0 before the demo" && git push
argocd app get plantshop --refresh
kubectl argo rollouts promote plantshop-catalog-service -n plantshop --full   # пропустити паузи
```

---

## 4. Можливі питання (коротко)

- **Rollout vs Deployment?** Той самий pod template, але кроки, паузи, promote/abort, аналіз. Керує
  контролер Argo Rollouts.
- **Як ділиться трафік без Istio?** За кількістю подів: 1 з 4 = 25%. Через Eureka та сама частка.
- **`pause: {}` vs `pause: {duration: 30s}`?** Нескінченна (до promote) і з таймером.
- **`promote` vs `promote --full`?** Наступний крок або одразу 100%.
- **Чому promote/abort не відкочує selfHeal?** Вони змінюють status, а не spec, що в Git.
- **Ризик canary тут?** Дві версії одночасно з однією БД: міграції Flyway мають бути зворотно сумісні.
- **Що далі?** `AnalysisTemplate` з Prometheus (автоматичний promote/abort за 5xx і латентністю),
  traffic router для точних ваг, CI або Image Updater, що комітить тег.
