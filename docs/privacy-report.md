# Інженерні контролі приватності в Plant Shop: звіт до лабораторної роботи

Система: мікросервіси Plant Shop (Java 21, Spring Boot 3.5, PostgreSQL + Flyway, Kafka). Для роботи додано:

| Що | Де | Для якого завдання |
|---|---|---|
| Модуль `secure-logging`: маскування PII і редагування секретів у кожному рядку логу, correlation id | `secure-logging/` (підключений до всіх сервісів) | 1 |
| Сервіс `user-service` (порт 8085, власна БД `user-db`, схема `users`): акаунти, export, erasure, згоди, Policy Gate | `user-service/` | 1–4 |
| Внутрішній API order-service для export/anonymize замовлень (захищений сервісним токеном) | `order-service/.../InternalPrivacyController.java` | 2, 3 |
| Мінімізація: email прибрано з логу order-service і з Kafka-подій saga | order-, inventory-, payment-service | 1, 3 |
| Демо-скрипт і API-клієнт для захисту | `scripts/privacy-demo.sh`, `user-service/privacy-demo.http` | усі |
| Транскрипт реального прогону | [privacy-demo.md](privacy-demo.md) | усі |

Усі дані в коді, тестах і демо синтетичні: e-mail на зарезервованому домені `.test`, телефони з діапазону
`+44 7700 900xxx` (Ofcom резервує його для вигаданих номерів), IP з документаційних мереж `203.0.113.0/24` і
`2001:db8::/32`, паролі й ключі вигадані.

---

## 0. Підготовчий етап: інвентаризація персональних даних

### 0.1. Категорії, джерела, сховища

| Категорія | Поля | Джерело | Сховище | Пов'язані сутності | У логах | В експорті |
|---|---|---|---|---|---|---|
| Прямі ідентифікатори | `full_name`, `external_provider` + `external_subject_id` (соцлогін) | реєстрація, соцлогін | `users.users` | усі таблиці user-service через `user_id` | заборонено (redact за ключем); user id лише як HMAC-псевдонім `u_…` | так (`profile`, `linkedAccount`) |
| Контактні дані | `email`, `phone`, адреси; `customer_email`, `shipping_address` у замовленнях | реєстрація, оформлення замовлення | `users.users`, `users.user_addresses`, `orders.orders` | замовлення пов'язані з людиною через e-mail | e-mail і телефон лише частково масковані; адреса заборонена | так (`profile`, `addresses`, `orders`) |
| Технічні ідентифікатори | IP-адреса, user agent | HTTP-запит | `users.user_activity` | історія дій | не логуються | так (`activity`) |
| Історія дій | тип дії, вільний текст (пошукові запити, відгуки), optional analytics | дії користувача | `users.user_activity`, `users.analytics_events` | — | не логуються | так (`activity`, `analytics`) |
| Налаштування | мова, валюта, тема | профіль | `users.user_settings` | — | — | так (`settings`) |
| Транзакційні записи | замовлення, позиції, сума, метод оплати, статус; надіслані листи | order-service, payment-service, user-service | `orders.orders`, `orders.order_items`, `payments.payments`, `users.email_outbox` | `payments.order_id` → замовлення | лише id і суми | так (`orders`, `communications`) |
| Дані про згоду | мета, статус, версія політики, канал, час надання/відкликання | GRANT/REVOKE | `users.user_consents`, `users.consent_history` | — | лише факт (security event) | так (`consents.current`, `consents.history`) |
| Запити щодо приватності | тип, результат, час | export/erasure/consent | `users.privacy_audit_events` | псевдонім суб'єкта | так, без PII | так (без `actor`) |

### 0.2. Що ніколи не повертається через Data Export

| Поле | Причина |
|---|---|
| `password_hash` | облікові дані (`SECURITY_CREDENTIAL`) |
| `verification_token_hash`, `password_reset_token_hash`, `password_reset_expires_at` | одноразові токени безпеки |
| `fraud_review_flag` | внутрішній antifraud-контроль (`INTERNAL_SECURITY_CONTROL`) |
| `role` | внутрішня авторизація, не дані про людину |
| `actor_ref` у журналі запитів | ідентифікує співробітника, а не суб'єкта (`OTHER_PERSONS_DATA`) |
| ключ провайдера пошти, `INTERNAL_API_TOKEN`, ключ HMAC | секрети інтеграцій, у БД не зберігаються взагалі |
| дані інших користувачів | вибірки лише за `user_id` суб'єкта; перевірено тестом |

Ці поля перелічені в самому експорті в `excludedFields`: користувач бачить, *що* ми зберігаємо, але не отримує значень.

### 0.3. Знахідки до виконання роботи

1. **Витік e-mail у лог.** `OrderService` писав `Order 5 created for a@b.com …`. Виправлено двічі: e-mail прибрано
   з повідомлення, а централізоване маскування тепер накриває всі сервіси.
2. **E-mail у Kafka-подіях.** `OrderCreatedEvent` і `InventoryReservedEvent` несли `customerEmail`, який не
   використовує жоден учасник saga. Kafka зберігає повідомлення і після стирання користувача, тому поле прибрано
   (мінімізація). Старі повідомлення зникнуть за `retention.ms` топіку (за замовчуванням 7 днів).
3. **Залишковий ризик поза межами роботи:** `GET /api/orders?email=` і `GET /api/orders/{id}` в order-service
   досі доступні без автентифікації (успадкований IDOR-канал). Рекомендація: перевести їх за автентифікацію в
   gateway або user-service. Новий export цього каналу не відкриває: внутрішні ендпоінти не маршрутизуються
   gateway і вимагають сервісний токен.

### 0.4. Контрольні тестові дані

| Акаунт (seed `V2__seed_demo_users.sql`) | Пароль (демо) | Роль | Навіщо |
|---|---|---|---|
| `alice@example.test` (id 1) | `Alice-Demo-2026` | CUSTOMER | export; має всі категорії даних, reset-токен і antifraud-прапорець (перевірка мінімізації) |
| `bob@example.test` (id 2) | `Bob-Demo-2026` | CUSTOMER | «сторонній користувач» для негативних сценаріїв |
| `dpo@example.test` (id 3) | `Dpo-Demo-2026` | PRIVACY_OFFICER | export/erasure на запит суб'єкта |
| `marketing@example.test` (id 4) | `Marketing-Demo-2026` | MARKETING | запуск маркетингової розсилки |
| `erin@example.test` (id 5) | `Erin-Demo-2026` | CUSTOMER | стирання; має адресу, історію, обидві згоди, листи, analytics |

Інтеграційні тести створюють власних користувачів з унікальними маркерами в кожній таблиці
(`<suffix> Control Street`, `control search <suffix>`, `2001:db8::<n>`, `ControlAgent/<suffix>` …), тож
перевірки «значення присутнє / відсутнє» однозначні.

---

## 1. Secure Logging і маскування PII

### 1.1. Політика логування

| Клас | Що | Як виглядає в лозі |
|---|---|---|
| Дозволено | технічні id (`orderId`, `messageId`), correlation id, тип операції, статус і результат, суми, час, псевдоніми `u_…` | як є |
| Масковано частково | e-mail (зокрема URL-encoded `%40`) | `a***@example.test`: перший символ і домен для діагностики |
| | телефон | `+44********23`: код країни й дві останні цифри |
| Заборонено (повна заміна) | `password`, `passwd`, `pwd`, будь-який `*token*` (access, refresh, reset, verification), `api key`, `Authorization` (Bearer/Basic), `cookie`, `session`, `secret`, `credential`, `cvv`, JWT, `user:password@` в URL | `[REDACTED]` |
| | ПІБ, адреси, IBAN, номер картки (за назвою ключа) | `[REDACTED]` |

### 1.2. Реалізація: один механізм для всіх логів

- `PiiMasker` описує політику як набір правил: ключ-значення (`key=value`, `key: value`, `"key":"value"`,
  `\"key\":\"value\"` для JSON усередині JSON, `'key': value`), а також вільний текст (e-mail, телефон, Bearer, JWT,
  облікові дані в URL).
- `MaskingEncoder` обгортає **будь-який** logback-енкодер і пропускає через маскувальник увесь відрендерений рядок:
  повідомлення, аргументи, `toString()` вкладених DTO, MDC, key-value пари, stack trace з усіма `Caused by`.
  Тому неважливо, як PII потрапила в лог: вручну, через об'єкт запиту чи через exception.
- `logback-spring.xml` лежить у jar модуля, тож Spring Boot підхоплює його в кожному сервісі, що залежить від
  `secure-logging`, без копіювання конфігурації. Формат за замовчуванням текстовий; профіль `json-logs` вмикає
  структурований JSON (logstash), маскований тим самим енкодером і досі валідний JSON.
- `CorrelationIdFilter` бере `X-Correlation-Id` із запиту (лише `[A-Za-z0-9-]{8,64}`, щоб не впустити ін'єкцію в
  лог) або генерує UUID, кладе в MDC і повертає у відповіді. Correlation id не маскується, тож діагностика зберігається.

Маскування на виході енкодера, а не в окремому контролері, означає, що забута «ручна» маска в новому коді не
призведе до витоку.

### 1.3. «Не логувати зайве»

- Тіла запитів і відповідей не логуються ніде (немає request-logging filter; export логує лише свій факт).
- Лог виклику поштового провайдера (заголовки й JSON-тіло) пишеться тільки на рівні DEBUG. У продакшн-конфігурації
  він вимкнений; для демо вмикається в `docker-compose.yml`, щоб показати, що навіть тоді секрети не виходять.
- Security-події (`PrivacyAudit`): `Security event event="…" outcome="…" actor="u_…" subject="u_…"`; час і
  correlation id є в префіксі рядка. Той самий запис зберігається в `privacy_audit_events`. Без e-mail, імен і токенів.
- Обробник помилок повертає клієнту нейтральний текст, а stack trace логує через маскування. Помилки валідації не
  повертають і не логують відхилені значення (стандартний текст Spring містив би `rejected value [пароль]`).

### 1.4. Обмеження

Розпізнавання шаблонне: секрет під невідомою назвою ключа або без ключа у вільному тексті не впізнається. Тому
deny-list — це задокументована політика, зафіксована тестами, а не заміна правила «секрети не логуються».
Телефон без `+` маскується лише за назвою ключа; user-service нормалізує номери в E.164.

### 1.5. Тести

| Тест | Що доводить |
|---|---|
| `PiiMaskerTest` (22) | кожне правило; JSON лишається валідним і зберігає `correlationId`, суми, числа; JSON усередині JSON; `toString()` вкладених record-DTO; рядки stack trace і рядок логера не зачіпаються; ідемпотентність |
| `MaskingEncoderTest` (3) | реальний logback-пайплайн: вкладений DTO + exception з причиною, текст і JSON; **контрольний прогін без маскування** показує, що той самий сценарій справді містить усі значення, отже основна перевірка впаде на першому ж витоку |
| `SecureLoggingConfigurationTest` (2) | конфігурація з jar працює під Spring Boot у текстовому та JSON-профілі |
| `SecureLoggingIntegrationTest` (3) | реєстрація, скидання пароля й exception-шлях user-service: перехоплений stdout містить маски й `[REDACTED]`, але не містить жодного вихідного e-mail, телефону, пароля, хешу, токена чи API-ключа |

Перевірка — `assertThat(log).doesNotContain(всі_вихідні_значення)`: тест падає (FAIL), якщо хоча б одне значення
потрапило в лог.

---

## 2. Right of Access: Personal Data Export

### 2.1. Контракт `GET /api/users/{id}/personal-data`

```text
metadata        schemaVersion "1.0", exportId, generatedAt (ISO-8601, UTC "Z"), timeZone "UTC",
                requestedBy SUBJECT | PRIVACY_OFFICER, correlationId, complete
subject         userId, accountStatus, accountCreatedAt
categories      DIRECT_IDENTIFIERS, CONTACT_DATA, TECHNICAL_IDENTIFIERS, PREFERENCES, ACTIVITY_HISTORY,
                CONSENT_RECORDS, COMMUNICATIONS, TRANSACTIONS, PRIVACY_REQUESTS (лише ті, де є дані)
sources         секція -> система й таблиця
sections        profile         <- user-service: users
                addresses       <- user-service: user_addresses
                settings        <- user-service: user_settings
                activity        <- user-service: user_activity
                consents        <- user-service: user_consents, consent_history
                communications  <- user-service: email_outbox
                analytics       <- user-service: analytics_events
                orders          <- order-service: orders, order_items  (Feign, POST з e-mail у тілі)
                privacyRequests <- user-service: privacy_audit_events
omissions       [{section, reason}]  NO_DATA_STORED | SOURCE_UNAVAILABLE: …
excludedFields  [{field, reason}]    див. 0.2
```

Усі секції присутні завжди: порожній список або `null` + запис в `omissions`. Якщо order-service не відповів,
експорт не падає і не «мовчить», а повертає `complete: false` і `omissions[orders] = SOURCE_UNAVAILABLE`.
`schemaVersion` фіксує контракт: перейменування поля означає нову major-версію.

### 2.2. Мінімізація

`PersonalDataMapper` копіює **явний перелік полів** кожної сутності. Нова колонка в таблиці не потрапить в експорт,
доки хтось свідомо не додасть її в мапер. Агрегація йде лише за `user_id` суб'єкта (та e-mail для замовлень), без
змішування з чужими записами; тест перевіряє відсутність даних іншого клієнта.

### 2.3. Контроль доступу

- HTTP Basic; principal завантажується з БД (`AppUserDetailsService`), у security-контексті хеш пароля стирається.
- `SubjectAccessPolicy`: дозволено, якщо `principal.id == {id}` або роль `PRIVACY_OFFICER`. Path-параметр сам доступу
  не дає.
- Для чужого id і для неіснуючого id відповідь **однакова**: `404 {"title":"Not Found","detail":"Subject not found"}`
  без даних. Тому зі статусу не можна дізнатися, чи існує чужий профіль. Без облікових даних: 401.
- Відмова фіксується як `PERSONAL_DATA_EXPORT DENIED` з псевдонімами, без даних профілю.

### 2.4. Логи під час експорту

Лише `Personal data export generated exportId=… categories=… complete=…` і security-подія. Тест
`theExportItselfIsNeverLogged` перевіряє, що в лозі немає жодного значення з експорту.

### 2.5. Тести (`PersonalDataExportIntegrationTest`, 7 + `CustomerDataIntegrationTest` в order-service)

Успішний експорт з контрольним записом із кожного джерела; відсутність `passwordHash`, токенів, antifraud-прапорця й
даних іншого клієнта; однакова 404 для чужого й неіснуючого id та відсутність PII жертви в лозі; доступ privacy
officer; 401 без автентифікації; documented omission для `settings` і недоступного order-service; експорт не
пишеться в лог.

---

## 3. Right to Erasure: workflow анонімізації

### 3.1. Маскування, псевдонімізація, анонімізація, видалення

| Механізм | Суть | Зворотність | Де в проєкті |
|---|---|---|---|
| **Маскування** | приховати частину значення під час показу | оригінал лишається в джерелі | лог: `a***@example.test`, `+44********23` |
| **Псевдонімізація** | замінити ідентифікатор на інший, який можна пов'язати назад за окремим ключем | зворотна для власника ключа | `u_3fa9c2e1b04d7a65` = HMAC-SHA256(ключ, user id) у логах і аудиті |
| **Анонімізація** | незворотно прибрати зв'язок з людиною; лишаються лише неідентифікуючі дані | незворотна | tombstone `deleted-<random UUID>@anon.invalid`, плейсхолдери в замовленнях, analytics без `user_id` і з часом до дня |
| **Видалення** | фізично прибрати запис | незворотне | адреси, налаштування, історія дій, листи |

Чому заміна e-mail на `sha256(email)` **не була б** анонімізацією: хеш детермінований, і e-mail відновлюється
перебором словника адрес. Тому tombstone отримує випадковий UUID, який ніде не зберігається поруч з оригіналом.

### 3.2. Політика обробки сутностей (`ErasureRule`)

| Система | Набір даних | Дія | Обґрунтування / що видаляється |
|---|---|---|---|
| user-service | `users` | ANONYMIZE | рядок лишається tombstone, щоб збережені записи мали валідний FK; e-mail → випадковий унікальний `deleted-<UUID>@anon.invalid` (є unique constraint); ПІБ, телефон, external id, хеш пароля, токени, прапорці → `NULL`/`false`; `status=ANONYMIZED` |
| | `user_addresses` | DELETE | адреси, причин зберігати немає |
| | `user_settings` | DELETE | налаштування |
| | `user_activity` | DELETE | IP, user agent, вільний текст |
| | `email_outbox` | DELETE | копії листів з адресою отримувача |
| | `analytics_events` | ANONYMIZE | для агрегованої статистики: `user_id → NULL`, час узагальнено до дня |
| | `user_consents` | RETAIN | **retention:** доказ законності обробки в минулому; PII немає; усі надані згоди відкликаються (`source=SYSTEM`), тож Policy Gate відмовляє будь-якій optional-обробці |
| | `consent_history` | RETAIN | **retention:** append-only доказ (GDPR ст. 7(1)): мета, версія політики, канал, час; PII немає |
| | `privacy_audit_events` | RETAIN | **retention:** підзвітність (факт стирання); лише HMAC-псевдоніми |
| order-service | `orders` | ANONYMIZE | **retention:** бухгалтерські записи зберігаються 1095 днів (модельоване правило; аналог вимог податкового обліку). `customer_email` → `erased-customer@anonymized.invalid`, `shipping_address` → `[erased]`; суми, позиції, статус і дати лишаються, звіти не змінюються |
| | `order_items` | RETAIN | PII немає (рослина, кількість, ціна) |
| payment-service | `payments` | SKIP | PII немає: сума, метод, transaction id, зв'язок лише через `order_id` |
| усі сервіси | логи | SKIP | PII маскується ще під час запису (завдання 1) |

Плейсхолдер замовлень однаковий для всіх стертих клієнтів. Так збережені замовлення не можна згрупувати назад «по
людині» (singling out), а звіт виручки по статусах дає той самий результат, що й до стирання (перевірено тестом).

### 3.3. Команда, транзакційна межа, збої

`POST /api/users/{id}/anonymize` (сам суб'єкт або `PRIVACY_OFFICER`; GET → 405; чужий id → та сама 404).

1. **Віддалений крок** (order-service, ідемпотентний) виконується **першим**: замовлення пов'язані з людиною через
   e-mail, а крок 2 цей e-mail знищує.
2. **Локальний крок**: одна транзакція над усіма таблицями user-service під `SELECT … FOR UPDATE` на рядок
   користувача. Блокування не дає паралельному GRANT чи розсилці втрутитися посередині.

Стратегія збоїв (forward recovery):

- збій кроку 1 → нічого не змінено, 503, аудит `FAILED`;
- збій кроку 2 → відкат усієї локальної транзакції, 500, аудит `FAILED` (окремою транзакцією);
- повтор того самого запиту безпечний: крок 1 знаходить 0 замовлень, крок 2 виконується знову.

**Ідемпотентність.** Повторний запит для вже стертого суб'єкта нічого не змінює, нових профілів не створює і
повертає `ALREADY_ANONYMIZED` (аудит `NO_CHANGE`). Сам суб'єкт після стирання вже не може увійти, бо хеш пароля
видалено.

**Audit event.** `ERASURE / SUCCESS` пишеться в тій самій транзакції, що й стирання (існує лише якщо стирання
закомічене). Запис містить тип, час, результат, псевдоніми actor/subject і correlation id, жодного видаленого значення.

### 3.4. Технічний висновок щодо retention і залишкові ризики

Записи `orders`, `order_items`, `user_consents`, `consent_history`, `privacy_audit_events` лишаються, бо мають
змодельовану підставу (облік, доказ згоди, підзвітність). Вони структурно цілісні: FK вказують на tombstone, orphan
rows немає, звітні запити працюють. Ідентифікатори з них видалені або їх там і не було.

Залишкові ризики, які це рішення не закриває:

- резервні копії БД містять PII до завершення їхнього строку зберігання;
- Kafka-повідомлення, створені до мінімізації подій, живуть до `retention.ms`;
- замовлення, оформлені на інший e-mail, ніж e-mail акаунта, з акаунтом не пов'язані й не будуть знайдені.

### 3.5. Тести

`ErasureIntegrationTest` (7):

- повне сканування **кожної таблиці** схеми `users` до/після: жодного вихідного значення; tombstone; збережені
  записи; FK і звітні запити; аудит; лог без PII; неможливість входу;
- повтор: стан не змінюється, крім одного аудит-рядка `NO_CHANGE`;
- унікальність tombstone для двох користувачів;
- заборона для стороннього actor;
- GET → 405;
- недоступний order-service → нічого не змінено;
- збій посередині локальної транзакції → повний відкат, повтор завершує стирання.

`CustomerDataIntegrationTest` (order-service, 4): анонімізуються лише замовлення цього клієнта (з урахуванням
регістру e-mail), звіт виручки не змінився, позиції без orphan rows, повтор повертає 0, без сервісного токена 401.

---

## 4. Consent Management Engine

### 4.1. Модель

- `UserConsent` (поточний стан, unique `(user_id, purpose)`): `userId`, `purpose` (`MARKETING_EMAIL`,
  `OPTIONAL_ANALYTICS`), `status` (`GRANTED`/`WITHDRAWN`), `policyVersion`, `source` (`WEB`, `MOBILE_APP`,
  `EMAIL_LINK`, `SUPPORT`; `SYSTEM` зарезервовано за erasure), `grantedAt`, `withdrawnAt`, `updatedAt`.
- `ConsentHistoryEntry` (append-only, `updatable = false`): кожна **фактична** зміна з версією політики, каналом,
  часом і correlation id. З неї видно, яку політику прийняли, коли, через який канал і коли згоду відкликали.
- Поточні версії текстів — у конфігурації (`privacy.policy-versions`).

### 4.2. API

| Метод | Хто | Поведінка |
|---|---|---|
| `GET /api/users/{id}/consents` | суб'єкт | стан по кожній меті (`NOT_GIVEN` теж) + історія |
| `POST /api/users/{id}/consents/{purpose}/grant` `{policyVersion, source}` | лише суб'єкт | невідома мета → 400; версія не поточна → 409 з `currentPolicyVersion`; `source=SYSTEM` → 400; повтор → `changed:false`, без нового запису в історії |
| `POST /api/users/{id}/consents/{purpose}/revoke` `{source}` | лише суб'єкт | повтор або відкликання ненаданої згоди → `changed:false`, суперечливих записів немає |

Згода особиста, тому навіть privacy officer не може її надати чи відкликати за суб'єкта (404, як і для чужого id).
Згода на одну мету ніколи не поширюється на іншу: рядок і рішення Policy Gate прив'язані до `purpose`.

### 4.3. Policy Gate

`ConsentPolicyGate.check(userId, purpose)` викликається безпосередньо перед залежною дією і читає актуальний стан
з БД, без кешу:

- `DENY / SUBJECT_INACTIVE`: користувача немає або його стерто;
- `DENY / NO_CONSENT`: згоди на **цю** мету немає;
- `DENY / CONSENT_WITHDRAWN`: згоду відкликано;
- `DENY / POLICY_VERSION_OUTDATED`: згоду дано на стару версію тексту;
- `ALLOW / CONSENT_GRANTED`.

Залежні дії:

- `POST /api/marketing/emails` (роль MARKETING): ALLOW → рядок `MARKETING` в `email_outbox`; DENY → жодного
  побічного ефекту;
- `POST /api/analytics/events`: ALLOW → рядок в `analytics_events`; DENY → подію відкинуто.

**Негайне відкликання.** REVOKE і перевірка gate перед розсилкою беруть той самий row lock користувача. Відкликання
або завершується до перевірки, або чекає, доки поточна відправка закінчиться. Наступна дія після REVOKE завжди
отримує DENY. Кешу чи черги немає. Якби відправка була фоновою, gate потрібно викликати в момент виконання job,
а не в момент постановки в чергу.

**Згода не універсальний прапорець.** Gate застосовується лише до optional-операцій. Лист підтвердження e-mail і
скидання пароля мають договірну підставу (`EmailCategory.TRANSACTIONAL`) і відправляються без маркетингової
згоди; це перевіряє тест `contractualEmailIsNotBlockedByMissingMarketingConsent`.

### 4.4. Тести (`ConsentIntegrationTest`, 9)

- `grantAllowRevokeDeny`: початковий DENY без побічного ефекту → GRANT → ALLOW з рядком в outbox → REVOKE →
  DENY, кількість листів не змінилась; історія `GRANTED, WITHDRAWN` з версією політики;
- згода на analytics не дозволяє marketing;
- analytics зупиняється одразу після REVOKE;
- застаріла версія політики;
- ідемпотентність команд;
- невалідні мета й канал;
- керувати згодою може лише суб'єкт; розсилка лише для ролі MARKETING;
- договірний лист не блокується;
- стертий суб'єкт отримує DENY навіть для «застарілого» job.

---

## 5. Захист: підготовка та сценарій

**Заздалегідь** (сервіс, БД, API-клієнт і тести вже запущені):

```bash
docker compose up -d --build              # 15 контейнерів; ~1 хв після старту всі healthy
./scripts/privacy-demo.sh reset           # синтетичні дані user-service в початковий стан
```

В IntelliJ відкрити `user-service/privacy-demo.http`, тестові класи та термінал. Транскрипт повного прогону:
[privacy-demo.md](privacy-demo.md).

| Крок | Команда | Що показати |
|---|---|---|
| 1 | `./scripts/privacy-demo.sh 1` | рядки логу реєстрації та скидання пароля: `l***@example.test`, `+44********99`, `Authorization=[REDACTED]`, `"verificationToken":"[REDACTED]"`, security event з `u_…`; лічильники вихідних значень = 0; тести зелені |
| 2 | `./scripts/privacy-demo.sh 2` | JSON експорту Alice, `sources` → пояснити кожну секцію; Bob → 404 для Alice і для 999999 (однакові відповіді); тести |
| 3 | `./scripts/privacy-demo.sh 3` | стан БД до (users, addresses, activity, consents, orders, payments) → `anonymize` → стан після; tombstone; замовлення з плейсхолдерами й тими самими сумами; повтор → `ALREADY_ANONYMIZED`; аудит; тести |
| 4 | `./scripts/privacy-demo.sh 4` | DENY → GRANT → ALLOW (+1 лист) → REVOKE → DENY (без змін), історія згоди; `ConsentIntegrationTest` |

Короткі відповіді на очікувані запитання:

- **Чому 404, а не 403, для чужого профілю?** 403 підтвердив би, що профіль існує (user enumeration). Для чужого і
  неіснуючого id відповідь однакова.
- **Чому стирання починається з order-service?** Зв'язок замовлень з людиною тримається на e-mail. Якби спершу
  анонімізували користувача, замовлення вже не можна було б знайти. Віддалений крок ідемпотентний, тож повтор
  після будь-якого збою безпечний.
- **Чому користувач стає tombstone, а не видаляється?** На нього посилаються збережені записи (згоди, історія), FK
  мають лишатися валідними. У рядку не лишається жодного ідентифікатора; e-mail випадковий, бо колонка унікальна.
- **Чому `u_…` у логах не анонімізація?** Це HMAC з ключем: власник ключа зіставить псевдонім з id. Саме тому це
  псевдонімізація, і ключ зберігається як секрет (`PRIVACY_PSEUDONYMIZATION_KEY`).

---

## 6. Результати тестів

Повний прогін `./mvnw test` (Docker має бути запущений: інтеграційні тести піднімають PostgreSQL через
Testcontainers):

| Модуль | Тестові класи | Тестів |
|---|---|---|
| secure-logging | `PiiMaskerTest`, `MaskingEncoderTest`, `SecureLoggingConfigurationTest` | 27 |
| user-service | `SecureLoggingIntegrationTest`, `PersonalDataExportIntegrationTest`, `ErasureIntegrationTest`, `ConsentIntegrationTest` | 26 |
| order-service | `CustomerDataIntegrationTest` (+ наявний `OrderServiceTest`) | 4 (+4) |
| catalog-, inventory-, payment-service | наявні юніт-тести (оновлені під мінімізовані події) | 8 |
| **Разом** | | **69, BUILD SUCCESS** |

Testcontainers піднято до 1.21.4: версія 1.21.3 зі Spring Boot 3.5.6 звертається до Docker API 1.32, яку Docker
Engine 29+ відхиляє.
