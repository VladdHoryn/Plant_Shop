# ЛР-5: шпаргалка до захисту (GitOps через Argo CD)

Репозиторій: `https://github.com/VladdHoryn/Plant_Shop.git`, гілка `lab/DevOps_lab_5`
(вона ж `targetRevision` обох Application). Образи: `vladyslavv648/plantshop-<module>`.
Усі команди — у Git Bash з кореня репозиторію. `argocd` = `~/bin/argocd.exe`.

## 1. Підготовка перед захистом (за 10 хв)

1. Запустити **Docker Desktop**, дочекатися «Engine running».
2. Кластер (дані й Argo CD зберігаються між перезапусками):
   ```bash
   minikube start                       # профіль уже має --cpus=4 --memory=7g
   kubectl -n argocd get pods           # усі Running
   kubectl -n plantshop get pods        # усі 1/1 Running (JVM стартують 2-3 хв)
   ```
3. Port-forward — **кожен в окремому терміналі**, не закривати:
   ```bash
   kubectl -n argocd port-forward svc/argocd-server 8080:443          # UI: https://localhost:8080
   kubectl -n plantshop port-forward svc/plantshop-api-gateway 8081:80 # застосунок
   ```
4. Увійти (пароль admin знаєте лише ви):
   ```bash
   argocd login localhost:8080 --username admin --insecure
   argocd app list                      # root і plantshop: Synced / Healthy
   curl localhost:8081/api/catalog/version   # {"version":"1.0.0"}
   ```
5. Відкрити https://localhost:8080 у браузері (самопідписаний сертифікат → «Продовжити»).

Стан перед показом: тег catalog-service `1.0.0`, `selfHeal: true`, обидва образи (1.0.0 і 1.1.0) у Docker Hub.

Якщо `plantshop` після старту довго `Progressing`/`Unknown`: `argocd app get plantshop --refresh`
(після `minikube start` Argo CD деякий час не бачить github.com, поки не підніметься DNS).

## 2. Демонстрація (порядок з п. 7 інструкції)

### 2.1 Інтерфейс: root → plantshop → дерево, Synced / Healthy
- UI: плитка **root** → у дереві об'єкт Application **plantshop** → відкрити його: 12 Deployment,
  12 Service, 5 PVC, ConfigMap; усе зелене.
- Пояснити: `bootstrap/root.yaml` застосовано вручну один раз, він стежить за `argocd-apps/`,
  звідти створюється `plantshop`, який рендерить `charts/plantshop` з `values-minikube.yaml`.
- `helm list -n plantshop` — порожньо: Argo CD лише рендерить шаблони (`helm template`), релізу Helm немає.
- `kubectl -n plantshop get secret plantshop-secret` — Secret створений вручну, у Git його немає
  (`existingSecret: plantshop-secret`), тому prune його не видаляє.

### 2.2 Реліз через Git: 1.0.0 → 1.1.0
```bash
curl localhost:8081/api/catalog/version          # {"version":"1.0.0"}
sed -i '/^catalogService:/,/^[a-z]/ s/tag: "1.0.0"/tag: "1.1.0"/' charts/plantshop/values-minikube.yaml
git diff
git commit -m "Release catalog-service 1.1.0" charts/plantshop/values-minikube.yaml && git push
argocd app get plantshop --refresh               # або кнопка Refresh в UI
kubectl -n plantshop get pods -w                 # новий под catalog-service, старий зникає (Ctrl+C)
curl localhost:8081/api/catalog/version          # {"version":"1.1.0"}
```
В UI: після Refresh видно OutOfSync → автосинхронізацію → новий ReplicaSet у дереві.
Rolling update з `maxUnavailable: 0` + `minReadySeconds: 15` — нова версія з'являється приблизно за 1,5-2 хв.
Одразу після заміни пода один-два запити можуть дати 500 (`Connection refused`): кеш Eureka в api-gateway
ще кілька секунд тримає адресу старого пода, а з 1 реплікою повторити запит немає де. Зачекати ~30 с і повторити curl.

### 2.3 Дрейф: kubectl scale → Argo CD повертає репліки (selfHeal)
```bash
kubectl -n plantshop scale deployment plantshop-catalog-service --replicas=3
kubectl -n plantshop get deploy plantshop-catalog-service -w     # 3 → назад 1 за кілька секунд
argocd app get plantshop | head -15                              # Synced / Healthy
```
Пояснити: без `selfHeal` (так було на початку) застосунок ставав OutOfSync, але репліки лишалися 3;
`selfHeal: true` додано комітом у `argocd-apps/plantshop.yaml`, і root застосував зміну сам.

### 2.4 Неіснуючий тег → Synced + Degraded → git revert → Healthy
```bash
sed -i '/^catalogService:/,/^[a-z]/ s/tag: "1.1.0"/tag: "9.9.9"/' charts/plantshop/values-minikube.yaml
git commit -m "Release catalog-service 9.9.9 (broken)" charts/plantshop/values-minikube.yaml && git push
argocd app get plantshop --refresh
kubectl -n plantshop get pods                    # новий под: ErrImagePull / ImagePullBackOff
# через ~2 хв (progressDeadlineSeconds: 120):
argocd app get plantshop | head -15              # Sync: Synced, Health: Degraded
curl localhost:8081/api/catalog/version          # усе ще 1.1.0: старий под працює (maxUnavailable: 0)

git log --oneline -3
git revert --no-edit HEAD && git push            # відкочуємо опис, а не кластер
argocd app get plantshop --refresh
argocd app history plantshop                     # кожна синхронізація прив'язана до коміту
argocd app get plantshop | head -15              # Synced / Healthy
curl localhost:8081/api/catalog/version          # {"version":"1.1.0"}
```
Чому не `argocd app rollback`: з автосинхронізацією Argo CD одразу поверне стан із Git.
`Synced` + `Degraded`: маніфест у кластері такий самий, як у Git, але сам Git описує несправний стан.

## 3. Прохання викладача змінити інше значення

Принцип завжди той самий: змінити `charts/plantshop/values-minikube.yaml` → commit → push → Refresh.
Руками в кластері нічого не змінюємо (selfHeal поверне).

**Кількість реплік** (наприклад, api-gateway 1 → 2):
```bash
sed -i '/^apiGateway:/,/^[a-z]/ s/replicaCount: 1/replicaCount: 2/' charts/plantshop/values-minikube.yaml
git commit -m "Scale api-gateway to 2" charts/plantshop/values-minikube.yaml && git push
argocd app get plantshop --refresh
kubectl -n plantshop get deploy plantshop-api-gateway      # READY 2/2
```

**Змінна оточення** (назва магазину, видно в `/api/catalog/info`):
```bash
cat >> charts/plantshop/values-minikube.yaml <<'EOF'

config:
  shopName: "Plant Shop (GitOps demo)"
EOF
git commit -m "Rename shop" charts/plantshop/values-minikube.yaml && git push
argocd app get plantshop --refresh
kubectl -n plantshop get cm plantshop-config -o jsonpath='{.data.SHOP_NAME}'; echo
# ConfigMap змінився → змінився checksum/config в анотації подів → поди перезапускаються самі
curl localhost:8081/api/catalog/info             # "shopName":"Plant Shop (GitOps demo)"
```
`config.*` лягає в ConfigMap `plantshop-config`, сервіси читають його як змінні оточення (`envFrom`).
Кожен Deployment має анотацію `checksum/config`, тому після зміни ConfigMap поди оновлюються автоматично.

Після демонстрації повернути зміни: `git revert --no-edit HEAD && git push`.

## 4. Типові питання

- **Звідки Argo CD знає, що змінилося?** Опитує Git кожні ~3 хв; `--refresh` / Refresh — одразу.
- **Хто є джерелом правди?** Лише Git (гілка `lab/DevOps_lab_5`). Ручні зміни в кластері — дрейф.
- **prune / selfHeal?** prune видаляє з кластера об'єкти, яких більше немає в Git; selfHeal повертає
  ручні зміни живих об'єктів.
- **Чому App of Apps?** Новий застосунок/компонент кластера = один файл у `argocd-apps/` (ЛР-6/7:
  Argo Rollouts, моніторинг); вручну застосовується лише `bootstrap/root.yaml`.
- **Де паролі?** Тільки в Secret `plantshop-secret`, створеному вручну. Якщо його перестворити з іншим
  паролем, PostgreSQL зі старим PVC не пустить сервіси — треба видаляти PVC.
- **Чому вимкнені hook і helm test?** Argo CD виконує `pre-upgrade` hook як PreSync перед КОЖНОЮ
  синхронізацією (включно з першою, коли БД ще немає), а `helm test` не запускає взагалі.
- **Чому без Ingress?** Без `minikube tunnel` Ingress не отримує адресу, і Argo CD тримає застосунок
  у Progressing; доступ через port-forward.
