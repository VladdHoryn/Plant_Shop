# ЛР №7. Семантичний аудит за Чепіним, імутабельність і Value Objects, хмарний збір метрик і матриця Hotspots

| | |
|---|---|
| Проєкт | Plant Shop: мікросервіси на Java 21 / Spring Boot 3.5 (цей репозиторій) |
| Гілка | `lab/Lab_7` |
| Ключові коміти | `42f3690` FAIL → `f2d4201` PASS → `819b36e` CI |
| Інструменти | Lizard 1.24.3 (контейнер `plantshop/lizard:1.24.3-py3.13.17`), Git CLI, GitHub Actions, JUnit 5 + Mockito, actionlint 1.7.7 |
| Сирі дані | `docs/reports/lab-07/` (кожне число у звіті береться з одного з цих файлів, перелік у [Додатку А](#додаток-а-сирі-дані)) |

## Зміст

1. [Декларація об'єкта аналізу](#1-декларація-обєкта-аналізу)
2. [Семантична матриця Чепіна (до рефакторингу)](#2-семантична-матриця-чепіна-до-рефакторингу)
3. [Звіт про симуляцію збою (FAIL)](#3-звіт-про-симуляцію-збою-fail)
4. [Архітектурний кейс «Очищення від хаосу змінних» (PASS)](#4-архітектурний-кейс-очищення-від-хаосу-змінних-pass)
5. [Код автоматизації CI/CD](#5-код-автоматизації-cicd)
6. [Лог метрик процесу (Churn Rate)](#6-лог-метрик-процесу-churn-rate)
7. [Синергетична матриця Hotspots](#7-синергетична-матриця-hotspots)
8. [Лог валідації ШІ-Техліда](#8-лог-валідації-ші-техліда)
9. [Додатки](#9-додатки)

---

## 1. Декларація об'єкта аналізу

**Метод:** `PersonalDataExportService.assemble(AppUserPrincipal actor, LocalData local, List<OrderEntry> orders, List<Omission> omissions)`
(`user-service/src/main/java/com/plantshop/user/service/PersonalDataExportService.java`, рядки 181–230 у коміті `cc8b76c`).

**Архітектурна роль.** Метод збирає експорт персональних даних за правом доступу (GDPR, ст. 15) з усіх джерел: дев'яти локальних
таблиць user-service та замовлень, отриманих з order-service через REST (Feign-клієнт `OrderPrivacyClient`). Це інтеграційний
контур із мапуванням DTO: метод поєднує дані двох сервісів в один версійований документ `PersonalDataExport` і вирішує, які
**категорії персональних даних** у ньому задекларовано. Неправильний перелік категорій означає юридично некоректну відповідь
клієнту («ми не обробляємо ваших контактних даних», хоча обробляємо).

**Цикломатична складність (доказ лінійності):** Lizard, `docs/reports/lab-07/cc-before.txt`:

```text
  NLOC    CCN   token  PARAM  length  location
      46      5    680      4      50 PersonalDataExportService::assemble@181-230@user-service/.../PersonalDataExportService.java
```

**CC = 5 ≤ 5**: формально простий метод. Водночас це найбільший за обсягом даних метод проєкту (680 токенів, 4 параметри,
22 змінні, див. розділ 2): саме той випадок, коли метрика Мак-Кейба «сліпа».

**Чому саме він.** З трьох кандидатів (усі CC ≤ 5, підтверджено Lizard) лише цей дозволяє чесно досягти обох цілей завдання 2
(падіння C_ch щонайменше вдвічі і до ≤ 6). В інших методах залишаються injected-залежності, кожна з яких сама по собі дає ≥ 1 бал:

| Кандидат | Контур | CC | C_ch до (оцінка) | C_ch після (оцінка) | Висновок |
|---|---|---|---|---|---|
| `PaymentService.process` | платіжний шлюз, лінійний після Lookup Table ЛР №6 | 2 | 19.5 | ≈ 8.5 | ×2 так, ≤ 6 ні: перевірка дубліката, репозиторій, `log`, publisher |
| `OrderService.create` | 2 Feign REST-клієнти + мапування DTO | 5 | ≈ 30 | ≈ 12–14 | ×2 так, ≤ 6 ні: метод тримає 6 бінів |
| **`PersonalDataExportService.assemble`** | **мапування DTO експорту з даних двох сервісів** | **5** | **42** | **5.5** | **обидві цілі досяжні** |

## 2. Семантична матриця Чепіна (до рефакторингу)

### 2.1. Правила підрахунку

Враховано **всі** змінні методу: параметри, локальні змінні, параметри лямбд і змінну циклу, а також поля (екземпляра і
статичні), які метод читає. Не є змінними: літерали, константи enum (`ExportSection.PROFILE`), імена методів і типів.
Ролі визначено так:

- **C (керуюча):** змінна бере участь в умові: `if`, тернарному операторі, заголовку циклу, предикатній лямбді
  (`noneMatch`), або в булевому виразі `isEmpty()` / `!= null`, значення якого далі керує гілкою `if (present)`;
- **M (модифікована):** стан змінної змінюється в тілі методу (`put`, `add`, `addAll`);
- **P (вхідна):** лише читається для обчислень;
- **T (транзитна):** без змін передається як аргумент в інший виклик.

Пріоритет за кількох ролей: **C → M → P → T** (наприклад, `orders` передається в конструктор `Sections` (T), але також
перевіряється `!orders.isEmpty()` (C), тож це C).

### 2.2. Матриця

| Назва змінної | Тип даних | Клас за Чепіним | Інженерне обґрунтування ролі | Вага |
|---|---|---|---|---|
| `actor` | `AppUserPrincipal` | C | `actor.isSubject(user.getId())` у тернарній умові вибирає `requestedBy` | 3 |
| `local` | `LocalData` | C | `local.settings() == null ? null : ...` вирішує, чи буде секція налаштувань | 3 |
| `orders` | `List<OrderEntry>` | C | `!orders.isEmpty()` вирішує, чи задекларовано TRANSACTIONS (також T у `Sections`) | 3 |
| `omissions` | `List<Omission>` | C | квантор `noneMatch` над списком визначає повноту експорту (також T у результат) | 3 |
| `user` | `User` | C | `user.getId()` бере участь у тернарній умові (також P: читаються поля для `Subject`) | 3 |
| `consentHistory` | `List<ConsentHistoryResponse>` | C | `!consentHistory.isEmpty()` вирішує, чи задекларовано CONSENT_RECORDS | 3 |
| `sections` | `Sections` | C | 7 предикатів `!sections.x().isEmpty()` / `sections.settings() != null` | 3 |
| `present` (λ) | `Boolean` | C | `if (present)` безпосередньо розгалужує обчислення категорій | 3 |
| `section` (цикл `for`) | `ExportSection` | C | керуюча змінна циклу по `ExportSection.values()` | 3 |
| `o` (λ) | `Omission` | C | предикат `o.reason().startsWith("SOURCE_UNAVAILABLE")` | 3 |
| `hasData` | `Map<ExportSection, Boolean>` | M | змінюваний словник, 9 × `put` | 2 |
| `categories` | `Set<DataCategory>` | M | змінюваний набір, `addAll` у циклі; результат залежить від порядку викликів | 2 |
| `sources` | `Map<String, String>` | M | змінюваний словник, 9 × `put` у циклі | 2 |
| `consents` | `Map<ConsentPurpose, UserConsent>` | P | лише читання `consents.get(p)` | 1 |
| `section` (λ у `forEach`) | `ExportSection` | P | читання `section.categories()` для обчислення | 1 |
| `properties` (поле) | `PrivacyProperties` | P | конфігурація: читання `currentPolicyVersion(p)` | 1 |
| `currentConsents` | `List<ConsentResponse>` | T | створюється лише для передачі в `new Consents(...)` | 0.5 |
| `complete` | `boolean` | T | обчислюється й без змін передається в `Metadata` | 0.5 |
| `metadata` | `Metadata` | T | створюється й без змін передається в конструктор експорту | 0.5 |
| `p` (λ) | `ConsentPurpose` | T | без змін прокидається у 3 виклики (`toResponse`, `get`, `currentPolicyVersion`) | 0.5 |
| `EXCLUDED_FIELDS` (статичне поле) | `List<ExcludedField>` | T | без змін передається в конструктор експорту | 0.5 |
| `SCHEMA_VERSION` (статичне поле) | `String` | T | без змін передається в `Metadata` | 0.5 |

### 2.3. Розрахунок

Кількість змінних за класами: **P = 3**, **M = 3**, **C = 10**, **T = 6** (разом 22).

```text
C_ch = 1·P + 2·M + 3·C + 0.5·T
     = 1·3 + 2·3 + 3·10 + 0.5·6
     = 3   + 6   + 30   + 3
     = 42
```

**Індекс до рефакторингу: C_ch = 42.**

### 2.4. Висновок про ризик

- **Контроль потоку захований у даних.** 30 із 42 балів (71 %) дають керуючі змінні, хоча Lizard бачить лише 5 точок
  розгалуження. Кожен вираз `!sections.x().isEmpty()` є прихованою умовою: 9 секцій дають 2⁹ = 512 комбінацій
  «є дані / немає даних», а тести покривають лічені з них. На одиницю CC припадає 8.4 одиниці C_ch.
- **Три мутабельні акумулятори (M) залежать від порядку.** Набір `categories` наповнюється в циклі по `hasData`, тож
  результат залежить від того, хто і в якій послідовності його змінює. Будь-яка нова гілка, що модифікує спільний набір, може
  стерти внесок іншої секції. Це саме той клас дефектів, який демонструє FAIL-кейс.
- **Мутабельний список `omissions` виходить за межі методу.** Його наповнюють `export()` і `loadOrders()` (параметр як
  вихідний аргумент), і той самий екземпляр потрапляє в результат. Це ризик зміни вже зібраного експорту ззовні.
- Для лінійного методу таке значення свідчить про «архітектурний хаос у даних». Доданок 3·C показує, що головна причина
  не в довжині коду, а в розпорошенні рішень по змінних.

## 3. Звіт про симуляцію збою (FAIL)

### 3.1. Сценарій

Розробник додає «очевидне» правило: *якщо секція порожня, її категорії не розкриваються*, і реалізує його через гілку `else`,
яка **перезаписує** спільний мутабельний набір `categories` (змінна типу M):

```java
Set<DataCategory> categories = EnumSet.noneOf(DataCategory.class);
hasData.forEach((section, present) -> {
    if (present) {
        categories.addAll(section.categories());
    } else {
        // Lab 7 FAIL simulation: "an empty section discloses nothing", but the mutable set is shared by
        // every section, so this also overwrites what an earlier section (PROFILE: CONTACT_DATA) added
        categories.removeAll(section.categories());
    }
});
```

`PROFILE` і `ADDRESSES` обидві містять `CONTACT_DATA`. Для **кожного клієнта без збережених адрес** (тобто для більшості нових
клієнтів) секція ADDRESSES, оброблена після PROFILE, видаляє з набору CONTACT_DATA, хоча e-mail і телефон у профілі є.
Експорт стверджує, що контактні дані не обробляються. Це невалідний юридичний результат.

Важливо: **CC методу не змінився (5 → 5)**, бо `else` не додає точки розгалуження. Pre-commit hook (CC ≤ 10) пропустив коміт.
Метрика Мак-Кейба цей дефект не бачить, а доданок 2·M у Чепіна прямо вказує на його джерело.

### 3.2. Unit-тест (краш-тест)

`user-service/src/test/java/com/plantshop/user/service/PersonalDataExportServiceTest.java`: чистий JUnit 5 + Mockito, без
Spring-контексту й без бази даних (усі 13 залежностей замінено моками):

```java
@Test
void categoriesOfAnEmptySectionDoNotEraseThoseOfASectionWithData() {
    User user = customer();
    AppUserPrincipal actor = new AppUserPrincipal(user);
    when(accessPolicy.canHandleDataRequest(actor, SUBJECT_ID)).thenReturn(true);
    when(userRepository.findById(SUBJECT_ID)).thenReturn(Optional.of(user));

    PersonalDataExport export = service.export(actor, SUBJECT_ID);

    assertThat(export.sections().profile().email()).isEqualTo("profile.only@example.test");
    assertThat(export.sections().profile().phone()).isEqualTo("+380501112233");
    assertThat(export.sections().addresses()).isEmpty();
    assertThat(export.categories())
            .as("categories declared for a customer whose only data is the profile (e-mail, phone)")
            .containsExactly(DIRECT_IDENTIFIERS, CONTACT_DATA);
}
```

### 3.3. Результат: FAIL

`docs/reports/lab-07/fail-test-output.txt` (коміт `42f3690 feat(audit): simulate data mutation issue, FAIL`):

```text
[ERROR] Tests run: 1, Failures: 1, Errors: 0, Skipped: 0, Time elapsed: 1.947 s <<< FAILURE! -- in com.plantshop.user.service.PersonalDataExportServiceTest
org.opentest4j.AssertionFailedError:
[categories declared for a customer whose only data is the profile (e-mail, phone)]
Expecting actual:
  [DIRECT_IDENTIFIERS]
to contain exactly (and in same order):
  [DIRECT_IDENTIFIERS, CONTACT_DATA]
but could not find the following elements:
  [CONTACT_DATA]
[INFO] BUILD FAILURE
```

Тест падає з бізнесової причини (неповний перелік категорій), а не через помилку компіляції чи інфраструктури.

> 📸 SCREENSHOT: вивід `./mvnw -B test -pl user-service -am -Dtest=PersonalDataExportServiceTest -Dsurefire.failIfNoSpecifiedTests=false` на коміті `42f3690` з `Expecting actual: [DIRECT_IDENTIFIERS]` і `BUILD FAILURE` (або червоний тест в IntelliJ)

## 4. Архітектурний кейс «Очищення від хаосу змінних» (PASS)

### 4.1. Що зроблено

**Імутабельність.** Усі параметри та локальні змінні `assemble`, `export` і `loadOrders` оголошено `final`. Жодна змінна не
перезаписується і жоден об'єкт не модифікується: нове значення означає новий об'єкт (`ExportOmissions.with(...)`).
Мутабельні акумулятори `hasData`, `categories`, `sources` і список `omissions` **зникли повністю**, а не перенесені в інший
метод: їх замінили вирази, що одразу дають незмінний результат (`Stream...toList()`, `Collectors.toUnmodifiableSet()`,
`List.copyOf`).

**Value Objects** (Java `record`, валідація в compact constructor, захисні копії колекцій):

| Value Object | Що згрупував | Яку логіку забрав із методу (C/M) |
|---|---|---|
| `privacy/ExportCoverage(Set<ExportSection> sectionsWithData)` | секції з даними + задекларовані категорії | 9 предикатів «секція має дані» (тепер Lookup Table `HAS_DATA`), `if (present)`, акумулятори `hasData` і `categories` |
| `privacy/ExportOmissions(List<Omission> items)` | пропущені секції та причини | `noneMatch(o -> ...)` → `complete()`; `omissions.add` → `with(...)`, що повертає новий екземпляр; правило «settings == null» |
| `privacy/OrderHistory(List<OrderEntry> entries, boolean sourceAvailable)` | відповідь order-service разом із фактом її доступності | вихідний параметр `omissions` у `loadOrders`; інваріант «недоступне джерело не має замовлень» |
| `privacy/ExportRequest(AppUserPrincipal actor, Long subjectId)` | хто запитує і чиї дані | тернарний `actor.isSubject(...) ? "SUBJECT" : "PRIVACY_OFFICER"` → `requestedBy()` |
| `service/SubjectRecords(...)` (раніше приватний `LocalData`) | знімок усіх локальних записів клієнта | побудова `Sections`, мапування згод (`consents`, `p`, `currentConsents`), тернарний `settings == null` |
| `ExportSection.SOURCES` (незмінна константа) | ключ секції → джерело | цикл `for` і акумулятор `sources`: дані однакові для всіх експортів, тож будуються один раз |
| `Metadata.generated(requestedBy, complete)` | службові поля метаданих | `SCHEMA_VERSION`, `UUID`, час, часовий пояс, correlation id |

### 4.2. Порівняльна матриця коду (До / Після)

| Фрагмент | До (`cc8b76c`) | Після (`f2d4201`) |
|---|---|---|
| Сигнатура | `assemble(AppUserPrincipal actor, LocalData local, List<OrderEntry> orders, List<Omission> omissions)` | `assemble(final ExportRequest request, final SubjectRecords records, final OrderHistory orders)` |
| Секції | 10 рядків `new Sections(...)` + мапування згод у методі | `final Sections sections = records.toSections(orders, properties);` |
| «Чи є дані» | `Map<ExportSection, Boolean> hasData = new LinkedHashMap<>();` + 9 × `put` | `HAS_DATA`: незмінна Lookup Table предикатів у `ExportCoverage` |
| Категорії | `Set<DataCategory> categories = EnumSet.noneOf(...)` + `forEach` / `if` / `addAll` | `ExportCoverage.of(sections).categories()`: один вираз, без мутацій |
| Джерела | `Map<String,String> sources = new LinkedHashMap<>()` + `for` / `put` | `ExportSection.SOURCES` |
| Повнота | `boolean complete = omissions.stream().noneMatch(o -> ...)` | `omissions.complete()` |
| Пропуски | `new ArrayList<>()`, `omissions.add(...)` у `export()` і `loadOrders()` | `ExportOmissions.of(orders, sections)`, `with(...)` → новий екземпляр |
| Хто запитав | `actor.isSubject(user.getId()) ? "SUBJECT" : "PRIVACY_OFFICER"` | `request.requestedBy()` |
| Метадані | `new Metadata(SCHEMA_VERSION, UUID.randomUUID(), Instant.now(), "UTC", ..., complete)` | `Metadata.generated(request.requestedBy(), omissions.complete())` |
| Обсяг (Lizard) | NLOC 46, CC 5, 680 токенів | NLOC 8, CC 1, 95 токенів |

**До** (`cc8b76c`, без симульованого дефекту):

```java
private PersonalDataExport assemble(AppUserPrincipal actor, LocalData local, List<OrderEntry> orders,
                                    List<Omission> omissions) {
    User user = local.user();
    Map<ConsentPurpose, UserConsent> consents = local.consents().stream()
            .collect(Collectors.toMap(UserConsent::getPurpose, Function.identity()));
    List<ConsentResponse> currentConsents = Arrays.stream(ConsentPurpose.values())
            .map(p -> ConsentMapper.toResponse(p, consents.get(p), properties.currentPolicyVersion(p), null))
            .toList();
    List<ConsentHistoryResponse> consentHistory =
            local.consentHistory().stream().map(ConsentMapper::toResponse).toList();

    Sections sections = new Sections(
            PersonalDataMapper.toProfile(user),
            local.addresses().stream().map(PersonalDataMapper::toEntry).toList(),
            local.settings() == null ? null : PersonalDataMapper.toSettings(local.settings()),
            local.activity().stream().map(PersonalDataMapper::toEntry).toList(),
            new Consents(currentConsents, consentHistory),
            local.communications().stream().map(PersonalDataMapper::toEntry).toList(),
            local.analytics().stream().map(PersonalDataMapper::toEntry).toList(),
            orders,
            local.privacyRequests().stream().map(PersonalDataMapper::toEntry).toList());

    Map<ExportSection, Boolean> hasData = new LinkedHashMap<>();
    hasData.put(ExportSection.PROFILE, true);
    hasData.put(ExportSection.ADDRESSES, !sections.addresses().isEmpty());
    hasData.put(ExportSection.SETTINGS, sections.settings() != null);
    hasData.put(ExportSection.ACTIVITY, !sections.activity().isEmpty());
    hasData.put(ExportSection.CONSENTS, !consentHistory.isEmpty());
    hasData.put(ExportSection.COMMUNICATIONS, !sections.communications().isEmpty());
    hasData.put(ExportSection.ANALYTICS, !sections.analytics().isEmpty());
    hasData.put(ExportSection.ORDERS, !orders.isEmpty());
    hasData.put(ExportSection.PRIVACY_REQUESTS, !sections.privacyRequests().isEmpty());

    Set<DataCategory> categories = EnumSet.noneOf(DataCategory.class);
    hasData.forEach((section, present) -> {
        if (present) {
            categories.addAll(section.categories());
        }
    });
    Map<String, String> sources = new LinkedHashMap<>();
    for (ExportSection section : ExportSection.values()) {
        sources.put(section.key(), section.source());
    }

    boolean complete = omissions.stream().noneMatch(o -> o.reason().startsWith("SOURCE_UNAVAILABLE"));
    Metadata metadata = new Metadata(PersonalDataExport.SCHEMA_VERSION, UUID.randomUUID(), Instant.now(), "UTC",
            actor.isSubject(user.getId()) ? "SUBJECT" : "PRIVACY_OFFICER", CorrelationId.current(), complete);
    return new PersonalDataExport(metadata, new Subject(user.getId(), user.getStatus().name(), user.getCreatedAt()),
            List.copyOf(categories), sources, sections, omissions, EXCLUDED_FIELDS);
}
```

**Після** (`f2d4201`):

```java
private PersonalDataExport assemble(final ExportRequest request, final SubjectRecords records,
                                    final OrderHistory orders) {
    final Sections sections = records.toSections(orders, properties);
    final ExportOmissions omissions = ExportOmissions.of(orders, sections);
    return new PersonalDataExport(Metadata.generated(request.requestedBy(), omissions.complete()),
            records.subject(), ExportCoverage.of(sections).categories(), ExportSection.SOURCES, sections,
            omissions.items(), EXCLUDED_FIELDS);
}
```

Ключові Value Objects (повністю: `user-service/src/main/java/com/plantshop/user/privacy/`):

```java
public record ExportCoverage(Set<ExportSection> sectionsWithData) {

    /** "Does this section hold data?" as data: one predicate per section instead of one branch per section. */
    private static final Map<ExportSection, Predicate<Sections>> HAS_DATA = Map.of(
            ExportSection.PROFILE, sections -> true,
            ExportSection.ADDRESSES, sections -> !sections.addresses().isEmpty(),
            // ... one predicate per section, 9 in total
            ExportSection.PRIVACY_REQUESTS, sections -> !sections.privacyRequests().isEmpty());

    public ExportCoverage {
        sectionsWithData = Set.copyOf(Objects.requireNonNull(sectionsWithData, "sectionsWithData"));
    }

    public static ExportCoverage of(Sections sections) {
        Objects.requireNonNull(sections, "sections");
        return new ExportCoverage(HAS_DATA.entrySet().stream()
                .filter(rule -> rule.getValue().test(sections))
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet()));
    }

    /** Every category of every section with data, each once, in the order of {@link DataCategory}. */
    public List<DataCategory> categories() {
        return sectionsWithData.stream()
                .flatMap(section -> section.categories().stream())
                .distinct()
                .sorted()
                .toList();
    }
}
```

```java
public record ExportOmissions(List<Omission> items) {

    public ExportOmissions {
        items = List.copyOf(Objects.requireNonNull(items, "items"));
    }

    public static ExportOmissions of(OrderHistory orders, Sections sections) {
        final ExportOmissions afterOrders = orders.sourceAvailable() ? NONE : NONE.with(ORDERS_UNAVAILABLE);
        return sections.settings() == null ? afterOrders.with(SETTINGS_NOT_STORED) : afterOrders;
    }

    public ExportOmissions with(Omission omission) {
        return new ExportOmissions(Stream.concat(items.stream(), Stream.of(Objects.requireNonNull(omission))).toList());
    }

    public boolean complete() {
        return items.stream().noneMatch(omission -> omission.reason().startsWith(SOURCE_UNAVAILABLE));
    }
}
```

Дефект із розділу 3 у новій архітектурі неможливо відтворити в тій самій формі. Категорії обчислюються з **усього** набору секцій
одним виразом, і не існує спільного змінюваного набору, з якого одна секція могла б «вийняти» внесок іншої.

### 4.3. Нова матриця Чепіна (PASS)

| Назва змінної | Тип даних | Клас за Чепіним | Інженерне обґрунтування ролі | Вага |
|---|---|---|---|---|
| `request` | `ExportRequest` | P | лише читання `request.requestedBy()`; рішення ухвалює сам VO | 1 |
| `records` | `SubjectRecords` | P | лише читання: `records.toSections(...)`, `records.subject()` | 1 |
| `omissions` | `ExportOmissions` | P | лише читання: `omissions.complete()`, `omissions.items()` | 1 |
| `orders` | `OrderHistory` | T | без змін передається в `toSections(...)` і `ExportOmissions.of(...)` | 0.5 |
| `sections` | `Sections` | T | створюється й без змін передається в `ExportOmissions.of`, `ExportCoverage.of` і конструктор | 0.5 |
| `properties` (поле) | `PrivacyProperties` | T | без змін передається в `toSections(...)` | 0.5 |
| `EXCLUDED_FIELDS` (статичне поле) | `List<ExcludedField>` | T | без змін передається в конструктор експорту | 0.5 |
| `ExportSection.SOURCES` (статичне поле) | `Map<String, String>` | T | без змін передається в конструктор експорту | 0.5 |

Кількість: **P = 3**, **M = 0**, **C = 0**, **T = 5** (разом 8).

```text
C_ch_new = 1·P + 2·M + 3·C + 0.5·T
         = 1·3 + 2·0 + 3·0 + 0.5·5
         = 3   + 0   + 0   + 2.5
         = 5.5
```

**Доказ:**

```text
Умова 1 (падіння щонайменше вдвічі):  C_ch_new ≤ C_ch_old / 2   →   5.5 ≤ 42 / 2 = 21   ✔
Умова 2 (безпечний еталон):           C_ch_new ≤ 6              →   5.5 ≤ 6             ✔
Коефіцієнт зниження:                  42 / 5.5 ≈ 7.6 раза
```

| Клас | До | Після | Δ |
|---|---|---|---|
| P | 3 | 3 | 0 |
| M | 3 | 0 | −3 |
| C | 10 | 0 | −10 |
| T | 6 | 5 | −1 |
| **C_ch** | **42** | **5.5** | **−36.5** |

**Статус: PASS.** Метод став чистою композицією незмінних об'єктів: він не ухвалює жодного рішення і нічого не змінює.

### 4.4. Чесні застереження

- **Логіка не зникла, вона переїхала у VO.** Предикати й тернарні оператори тепер живуть у `ExportCoverage`, `ExportOmissions`,
  `ExportRequest`, `SubjectRecords`. Кожен їхній метод має CC ≤ 3, не має змінних типу M і протестований окремо
  (`ExportValueObjectsTest`). Це відрізняється від формального Extract Method: мутабельні змінні не перенесено, а ліквідовано,
  а правила стали даними (Lookup Table `HAS_DATA`, константа `SOURCES`).
- **Compact constructor** присвоює нормалізовані значення параметрам компонентів (`items = List.copyOf(items)`). Це канонічна
  ідіома Java records: вона виконується до ініціалізації `final`-полів і не змінює наявний об'єкт.
- **`SubjectRecords` містить JPA-сутності** (`User`, `Address` тощо мають Lombok-сетери). VO гарантує незмінність структури
  (списки скопійовано), але не самих сутностей. Вони відокремлені після read-only транзакції й лише читаються. Наступний крок:
  мапувати сутності в DTO одразу під час завантаження.
- **Сліпа зона Lizard.** Lizard 1.24.3 (це й остання версія на PyPI) не бачить методів record після першого `new <Цей Record>(`
  і ніколи не звітує compact constructor. Тому CC методів `ExportCoverage`, `ExportOmissions`, `OrderHistory` виміряно на
  копіях, де заголовок `record` замінено на `class` (тіла методів побайтово ідентичні, перевірено `diff`):
  максимальний CC = 3 (`docs/reports/lab-07/cc-after.txt`).

### 4.5. Перевірка: GREEN

Той самий тест із розділу 3, **без жодної зміни**, тепер проходить; разом із ним проходять 4 нові тести VO і весь набір тестів
усіх модулів, включно з інтеграційними тестами на Testcontainers (7 тестів експорту з реальною PostgreSQL слугують регресійним
захистом). `docs/reports/lab-07/pass-test-output.txt`:

```text
$ ./mvnw -B test -pl user-service -am -Dtest=PersonalDataExportServiceTest,ExportValueObjectsTest -Dsurefire.failIfNoSpecifiedTests=false
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.103 s -- in com.plantshop.user.privacy.ExportValueObjectsTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.649 s -- in com.plantshop.user.service.PersonalDataExportServiceTest
[INFO] BUILD SUCCESS

$ ./mvnw -B test        # every module, including the Testcontainers integration tests
[INFO] Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.899 s -- in com.plantshop.user.PersonalDataExportIntegrationTest
[INFO] Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.646 s -- in com.plantshop.user.service.PersonalDataExportServiceTest
[INFO] Tests run: 31, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

Усього в повному прогоні 111 тестів у 6 модулях (40 + 14 + 3 + 8 + 15 + 31), 0 падінь.

| Метрика `assemble` | До | Після |
|---|---|---|
| CC (Lizard) | 5 | 1 |
| NLOC | 46 | 8 |
| Токени | 680 | 95 |
| Параметри | 4 | 3 |
| Змінні | 22 | 8 |
| C_ch | 42 | 5.5 |

> 📸 SCREENSHOT: вивід `./mvnw -B test -pl user-service -am -Dtest=PersonalDataExportServiceTest,ExportValueObjectsTest -Dsurefire.failIfNoSpecifiedTests=false` на коміті `f2d4201` з `BUILD SUCCESS` (або зелені тести в IntelliJ)

> 📸 SCREENSHOT: `Reactor Summary` повного `./mvnw -B test` з `SUCCESS` для всіх модулів

### 4.6. Git History

`git log --oneline --graph -n 10` (`docs/reports/lab-07/git-graph.txt`):

```text
* f2d4201 refactor(audit): reduce Chapin complexity using immutable Value Objects, PASS
* 42f3690 feat(audit): simulate data mutation issue, FAIL
* cc8b76c docs(claude): add CLAUDE.md with build commands, architecture and lab workflow
* 8008696 docs(audit): complexity audit report, hook demo script
* 1b0218f feat(catalog): watering interval as a lookup table
* 875376d build(audit): versioned pre-commit hook that blocks methods with CC > 10
* 47306c9 refactor(audit): eliminate conditional branches using lookup table, PASS
* 53c18ab feat(audit): simulate topological CC failure, FAIL
* 76989e6 chore(audit): baseline for the complexity audit, PaymentService.process CC=3
* 8c6a605 Finish Testting Qual Lab_5
```

Перехід **Simulated Mutation Issue (FAIL) → Chapin weight reduction via Value Objects (PASS)**: `42f3690 → f2d4201`.

> 📸 SCREENSHOT: дерево комітів (`git log --oneline --graph -n 10` у терміналі або Git-панель IntelliJ) з комітами `42f3690` FAIL і `f2d4201` PASS

## 5. Код автоматизації CI/CD

### 5.1. Пайплайн GitHub Actions

`.github/workflows/code-metrics.yml` (коміт `819b36e`). Під час кожного push і pull request хмарний раннер встановлює сканер,
сканує всі модулі, експортує CSV, конвертує його в JSON і зберігає обидва файли як артефакт збірки:

```yaml
# Lab 7, task 3: complexity metrics of every push and pull request, exported as a machine-readable build
# artifact (CSV + JSON). It only measures; enforcing the CC limit stays in quality-gate.yml.
# Lizard is pinned to the version of tools/lizard, so the cloud report and the local one
# (scripts/cc-check.sh) come from the same analyzer.
# Known blind spot of Lizard 1.24.3: inside a Java record, methods after the first "new <SameRecord>(" and
# compact constructors are not reported (docs/reports/lab-07/cc-after.txt).
name: code-metrics

on:
  push:
  pull_request:

permissions:
  contents: read

concurrency:
  group: code-metrics-${{ github.ref }}
  cancel-in-progress: true

jobs:
  lizard-report:
    runs-on: ubuntu-latest
    timeout-minutes: 10
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-python@v5
        with:
          python-version: "3.13"

      - name: Install Lizard
        run: pip install --disable-pip-version-check lizard==1.24.3

      # Production sources of every Maven module. -C 1000: Lizard exits non-zero above its own default
      # threshold (CC 15), and a metrics run must report every function instead of failing on one.
      - name: Scan the repository (CSV)
        run: lizard -l java -C 1000 --csv ./*/src/main/java > lizard-report.csv

      - name: Convert to JSON
        run: python scripts/lizard_to_json.py lizard-report.csv lizard-report.json | tee -a "$GITHUB_STEP_SUMMARY"

      - uses: actions/upload-artifact@v4
        with:
          name: lizard-report
          path: |
            lizard-report.csv
            lizard-report.json
          if-no-files-found: error
          retention-days: 30
```

**Рішення, що відрізняються від «лобового» варіанта:**

| Рішення | Чому |
|---|---|
| `./*/src/main/java` замість `src/` | багатомодульний Maven-проєкт не має кореневого `src/`; скануються продакшн-джерела всіх модулів, як і в `quality-gate.yml` |
| `lizard==1.24.3` замість `lizard` | версія сканера зафіксована, як і в `tools/lizard`: метрика змінюється лише разом із кодом, а не через новий реліз аналізатора |
| `-C 1000` | Lizard повертає ненульовий код вище власного порогу CC 15; завдання цього job лише вимірювати, а примусове обмеження CC ≤ 10 залишається в `quality-gate.yml` |
| `./*` замість `*` у glob | зауваження shellcheck SC2035 (знайдено actionlint) |
| `tee -a "$GITHUB_STEP_SUMMARY"` | зведення (кількість функцій, топ файлів за сумарним CC) видно одразу на сторінці запуску |

### 5.2. Конвертер CSV → JSON

`scripts/lizard_to_json.py` (лише стандартна бібліотека Python). Він читає CSV Lizard (11 колонок без заголовка) і записує JSON
із трьома розділами: `summary` (кількість файлів і функцій, максимальний CC, функції понад ліміт), `files` (для кожного файлу
кількість функцій, `max_cc`, `sum_cc`, NLOC, тобто вхід матриці Hotspots) та `functions` (`file`, `method`, `cc`, `nloc`,
`params`, `tokens`, `start_line`, `end_line`). На некоректному CSV завершується з кодом 2 і зрозумілим повідомленням.

```json
{
  "tool": "lizard",
  "cc_limit": 10,
  "summary": { "files": 106, "functions": 265, "max_cc": 6, "over_limit": [] },
  "files": [
    { "file": "inventory-service/src/main/java/com/plantshop/inventory/service/InventoryService.java",
      "functions": 9, "max_cc": 5, "sum_cc": 18, "nloc": 81 }
  ],
  "functions": [
    { "file": "payment-service/src/main/java/com/plantshop/payment/service/PaymentService.java",
      "method": "PaymentService::process", "cc": 2, "nloc": 18, "params": 1, "tokens": 183,
      "start_line": 57, "end_line": 76 }
  ]
}
```

(фрагмент; масиви `files` і `functions` скорочено до одного елемента)

### 5.3. Перевірка до push

- **Синтаксис і семантика YAML:** `actionlint` 1.7.7 (контейнер `rhysd/actionlint:1.7.7`, з вбудованим shellcheck) дав
  0 помилок (перша версія мала зауваження SC2035, виправлено).
- **Сухий прогін у Docker:** ті самі команди виконано в чистому контейнері `python:3.13-slim` (`pip install lizard==1.24.3`
  → `lizard ... --csv` → `scripts/lizard_to_json.py`): 265 функцій у 106 файлах, максимальний CC = 6, 0 функцій понад CC 10.
  GitHub-hosted раннер виконує ті самі кроки на `ubuntu-latest`.

> 📸 SCREENSHOT: GitHub → Actions → workflow `code-metrics` → успішний запуск (зелений job `lizard-report`, Job Success) з Job Summary «Lizard complexity report»

> 📸 SCREENSHOT: блок Artifacts того ж запуску з артефактом `lizard-report` (і, за бажання, відкритий `lizard-report.json`)

## 6. Лог метрик процесу (Churn Rate)

### 6.1. Команда

Команда з умови не має фільтра за датою, а `egrep` застарілий, тому використано:

```bash
git log --since="90 days ago" --format=format: --name-only | grep -Ev '^$' | sort | uniq -c | sort -nr
```

### 6.2. Власний проєкт (Plant Shop)

`docs/reports/lab-07/churn.txt`: уся історія репозиторію складається з **15 комітів** (з 2026-10-14), 389 рядків
«кількість / файл». Початок списку:

```text
      6 README.md
      3 user-service/src/main/java/com/plantshop/user/service/PersonalDataExportService.java
      3 scripts/cc-check.sh
      3 pom.xml
      3 payment-service/src/main/java/com/plantshop/payment/service/PaymentService.java
      3 config-service/src/main/resources/configurations/application.yml
      3 config-service/src/main/resources/configurations/api-gateway.yml
      3 Dockerfile
      3 .gitattributes
      2 user-service/src/main/java/com/plantshop/user/privacy/ExportSection.java
      2 user-service/src/main/java/com/plantshop/user/dto/PersonalDataExport.java
      2 secure-logging/src/test/java/com/plantshop/logging/PiiMaskerTest.java
      ...
```

Розподіл для продакшн-Java (`src/main`): 183 файли мають 1 коміт, 10 файлів мають 2, і 2 файли мають 3. Цього замало для
аналізу мінливості: «~15 комітів на ключовий файл» недосяжні. До того ж найбільший CC у проєкті дорівнює 6, тож за порогом
CC > 10 Hotspot- і Sleeping-зони порожні за визначенням. Тому обрано **варіант 3** (аудит Enterprise Open-Source проєкту), а власний
проєкт подано окремою матрицею в розділі 7.3.

### 6.3. Варіант 3: Apache Commons Lang

[apache/commons-lang](https://github.com/apache/commons-lang): бібліотека на Java, той самий стек; на момент аудиту `master` на
`c833cc1ef` (2026-11-01), **10 138 комітів** в історії, **436 комітів за 90 днів**, 4 004 функції у 216 файлах з кодом.
`docs/reports/lab-07/churn-commons-lang.txt` (291 рядок). Початок списку:

```text
    107 src/changes/changes.xml
     14 src/test/java/org/apache/commons/lang3/reflect/TypeUtilsTest.java
     12 src/main/java/org/apache/commons/lang3/reflect/TypeUtils.java
     12 src/main/java/org/apache/commons/lang3/StringUtils.java
     11 .github/workflows/scorecards-analysis.yml
     11 .github/workflows/codeql-analysis.yml
     10 src/main/java/org/apache/commons/lang3/exception/ExceptionUtils.java
     10 pom.xml
      9 src/test/java/org/apache/commons/lang3/math/FractionTest.java
      8 src/main/java/org/apache/commons/lang3/math/Fraction.java
      7 src/main/java/org/apache/commons/lang3/time/FastDatePrinter.java
      7 src/main/java/org/apache/commons/lang3/CharRange.java
      6 src/test/java/org/apache/commons/lang3/exception/ExceptionUtilsTest.java
      6 src/test/java/org/apache/commons/lang3/StringUtilsTest.java
      6 src/test/java/org/apache/commons/lang3/ConversionTest.java
      6 src/main/java/org/apache/commons/lang3/time/FastDateParser.java
      6 src/main/java/org/apache/commons/lang3/Range.java
      6 src/main/java/org/apache/commons/lang3/Conversion.java
      ...
```

> 📸 SCREENSHOT (за бажанням): термінал із командою churn і початком відсортованого списку для commons-lang

## 7. Синергетична матриця Hotspots

### 7.1. Методика

- **Складність:** Lizard 1.24.3 по `src/main/java` (той самий сканер і ті самі параметри, що й у CI); агрегація по файлу:
  `max CC` і `sum CC`.
- **Мінливість:** кількість комітів за 90 днів із розділу 6; файл без комітів у вікні має 0.
- **Пороги:**
  - висока складність: **max CC файлу > 10**;
  - висока мінливість: **кількість комітів > медіани по проєкту**. Медіану пораховано по всіх 216 файлах commons-lang з
    кодом (з нулями): **медіана = 1 коміт**, тобто висока мінливість означає ≥ 2 коміти за 90 днів. Без нулів (160 файлів)
    медіана теж 1, тож поріг стійкий до вибору популяції.
- **Перевірка повноти вимірювання.** Порівняно кількість оголошених методів із кількістю функцій, яку повідомив Lizard.
  У двох файлах Lizard мовчки обриває розбір: `ArrayUtils.java` (20 з ~390 методів, зупинка після рядка 853 з 9 848) і
  `Range.java` (15 з ~31, після рядка 387). Їхні CC недостовірні, тому їх **виключено з матриці**, хоча за сирими числами обидва
  потрапили б у «Active». Для решти файлів матриці Lizard повідомив не менше функцій, ніж оголошено методів (у
  `FastDatePrinter` більше: 98 проти 80, бо він рахує й методи внутрішніх класів).

Повні таблиці всіх файлів з категоріями: `docs/reports/lab-07/hotspots-commons-lang.txt` і `hotspots-plant-shop.txt`.
Розмір квадрантів commons-lang: **Hotspot 23, Sleeping 2, Active 55, Stable 136**.

### 7.2. Матриця: Apache Commons Lang

| Назва файлу | Метрика складності (max CC / sum CC) | Кількість комітів (90 днів) | Категорія ризику |
|---|---|---|---|
| `reflect/TypeUtils.java` | 37 / 366 (9 методів з CC > 10) | 12 | 🔴 Hotspot |
| `StringUtils.java` | 27 / 907 (247 функцій, 15 з CC > 10) | 12 | 🔴 Hotspot |
| `math/Fraction.java` | 17 / 124 | 8 | 🔴 Hotspot |
| `time/FastDatePrinter.java` | 49 / 224 | 7 | 🔴 Hotspot |
| `text/translate/NumericEntityUnescaper.java` | 21 / 25 | 0 | 🟡 Sleeping |
| `text/translate/UnicodeUnescaper.java` | 11 / 12 | 1 | 🟡 Sleeping |
| `exception/ExceptionUtils.java` | 9 / 84 | 10 | 🟢 Active |
| `AnnotationUtils.java` | 10 / 73 | 5 | 🟢 Active |
| `builder/ReflectionToStringBuilder.java` | 9 / 60 | 1 | ⚪ Stable |
| `reflect/FieldUtils.java` | 8 / 55 | 1 | ⚪ Stable |

(шляхи відносно `src/main/java/org/apache/commons/lang3/`)

**Що стоїть за числами** (`docs/reports/lab-07/hotspots-commons-lang-commits.txt`, назви методів з CSV Lizard):

- **TypeUtils:** найскладніші методи `isAssignable` (CC 37) і `unrollVariables` (CC 24). За 90 днів 12 комітів, з яких
  7 є виправленнями дефектів, і 4 з них саме в цих двох методах: `isAssignable` для wildcard-типів (#1782), для `Class<X>` →
  `Class<?>` (#1820 і follow-up), `unrollVariables` з owner type (LANG-1836). Решта: рекурсія в `toString` (#1789, двічі)
  і хеші типів (LANG-1323). Класичний Hotspot: складність тут прямо генерує регресії.
- **StringUtils:** найбільший сумарний CC (907) як God Object на 247 функцій; `replaceEachOnce` (CC 27), `splitWorker` (CC 23).
  П'ять із 12 комітів виправляють **один і той самий клас дефектів** (розрив сурогатних пар UTF-16) у різних методах:
  `chop`, `stripStart/stripEnd`, `left/right/mid/overlay`, padding, `containsAny/indexOfAny`.
- **Fraction:** `greatestCommonDivisor` (CC 17); 5 із 8 комітів виправляють арифметичні дефекти: скорочення для
  `Integer.MIN_VALUE` (LANG-1834), `add/subtract` з нулем (#1787) і з нескороченими операндами (#1784), хибне переповнення
  в `multiplyBy` (#1769), неоголошений `ArithmeticException` у `getFraction(String)`.
- **FastDatePrinter:** `parsePattern` (CC 49, NLOC 137) є найскладнішим методом у матриці; 7 комітів (standalone-назви місяців,
  ери з календаря локалі, клонування `TimeZone`).
- **NumericEntityUnescaper:** `translate` (CC 21), жодного коміту за 90 днів, тобто заплутаний, але ніхто не чіпає.
- **ExceptionUtils:** 10 комітів, але max CC 9: команда активно переписує файл (6 комітів за 2026-10-25), і він
  залишається простим. Еталон «Active».

### 7.3. Матриця: власний проєкт (Plant Shop)

Пороги ті самі (CC > 10; медіана по 106 файлах = 1 коміт, висока мінливість означає ≥ 2). Квадранти: **Hotspot 0, Sleeping 0,
Active 6, Stable 100**.

| Назва файлу | Метрика складності (max CC / sum CC) | Кількість комітів (90 днів) | Категорія ризику |
|---|---|---|---|
| `user-service/.../service/PersonalDataExportService.java` | 3 / 9 | 3 | 🟢 Active |
| `payment-service/.../service/PaymentService.java` | 2 / 7 | 3 | 🟢 Active |
| `inventory-service/.../service/InventoryService.java` | 5 / 18 | 2 | 🟢 Active |
| `order-service/.../service/OrderService.java` | 5 / 11 | 2 | 🟢 Active |
| `user-service/.../service/ErasureService.java` | 6 / 11 | 1 | ⚪ Stable |
| `secure-logging/.../logging/PiiMasker.java` | 5 / 16 | 1 | ⚪ Stable |
| `user-service/.../service/ConsentService.java` | 3 / 14 | 1 | ⚪ Stable |

Порожні червона й жовта зони означають, що працює обмеження CC ≤ 10 із ЛР №6 (pre-commit hook + CI). Мінливість
`PersonalDataExportService` і `PaymentService` здебільшого спричинена самими лабораторними роботами (FAIL/PASS-коміти ЛР №6 і №7).

### 7.4. Управлінський висновок: план рефакторингу на наступний спринт

**Commons Lang (позиція зовнішнього аудитора), у порядку пріоритету:**

1. **`TypeUtils.isAssignable` / `unrollVariables` (Hotspot №1).** Найвища щільність дефектів: 7 виправлень за квартал у файлі,
   з них 4 у двох методах з CC 37 і 24. Спершу варто зафіксувати поведінку характеризаційними тестами (матриця «тип джерела ×
   тип цілі»), а потім замінити ланцюг `instanceof` на диспетчеризацію за видом типу (Lookup Table:
   `Class` / `ParameterizedType` / `GenericArrayType` / `WildcardType` / `TypeVariable` → окремий метод з CC ≤ 10).
   Очікуваний ефект: кожен вид типу змінюється ізольовано, тож зникає причина каскадних «Follow up: Fix ...».
2. **`StringUtils`: сурогатні пари (Hotspot №2).** Проблема не в одному методі: той самий дефект виправляли п'ять разів у
   різних місцях. Варто централізувати безпечну роботу з межами code point в одному допоміжному Value Object (індекс, що не
   може вказувати всередину сурогатної пари), перевести на нього методи по одному і закрити тему одним набором property-тестів.
   Розбивати God Object на 247 функцій зараз не варто: публічний API бібліотеки незмінний.
3. **`Fraction` (Hotspot №3).** Чотири з п'яти арифметичних виправлень стосуються інваріанту «дріб завжди скорочений і без
   переповнення», який перевіряється в окремих операціях, а не гарантується під час створення. Варто гарантувати інваріант у фабриці (той самий принцип Value
   Object, що й у розділі 4 цієї роботи) і спростити `greatestCommonDivisor` (CC 17).
4. **`FastDatePrinter.parsePattern` (CC 49).** Варто замінити великий `switch` за літерами шаблону на таблицю «літера → фабрика
   правила». Пріоритет нижчий, бо коміти тут функціональні, а не виправлення регресій.
5. **Sleeping (`NumericEntityUnescaper.translate`, `UnicodeUnescaper`)** у спринт **не брати**. Рефакторинг лише разом із
   першим Change Request, і перед ним спочатку характеризаційні тести.
6. **Active / Stable:** дій не потрібно. `ExceptionUtils` показує, що висока мінливість за низької складності є нормальним станом.
7. **Governance:** CI-метрики не можна приймати на віру. Lizard мовчки втрачає частину файлів (`ArrayUtils`, `Range`) і
   методи records. До конвеєра варто додати перевірку повноти (оголошені методи проти виміряних) і вважати файл із розбіжністю
   «невиміряним», а не «простим».

**Власний проєкт:**

1. Невідкладних дій немає: червона й жовта зони порожні, а всі Active-файли мають max CC ≤ 5.
2. **`ErasureService.anonymize`** (CC 6, найвищий у проєкті, Stable) має п'ять однотипних викликів `audit.record(...)` у різних
   гілках. Під час наступного Change Request до стирання даних варто застосувати той самий підхід, що й до експорту: результат як
   Value Object.
3. **`InventoryService`** має найбільший сумарний CC у проєкті (18) і містить логіку саги (`reserve`, `settle`). Він Active,
   тож варто додати його до мутаційного тестування (`quality-gate.yml`), поки складність не зросла.
4. **Повнота метрик:** нові records (`ExportCoverage`, `ExportOmissions`, `OrderHistory`) невидимі для Lizard, а отже, і для
   pre-commit hook та CI-гейту CC ≤ 10. Варто додати в `scripts/lizard_to_json.py` попередження про файли без жодної виміряної
   функції.

## 8. Лог валідації ШІ-Техліда

> **TODO:** розділ буде заповнено за окремою інструкцією викладача (протокол проходження краш-тесту).

## 9. Додатки

### Додаток А. Сирі дані

| Файл (`docs/reports/lab-07/`) | Вміст |
|---|---|
| `cc-before.txt` | Lizard для `PersonalDataExportService` до рефакторингу (`assemble`: CC 5, NLOC 46) |
| `fail-test-output.txt` | повний вивід Maven для FAIL-тесту (коміт `42f3690`) |
| `cc-after.txt` | Lizard після рефакторингу + вимір VO на копіях із class-заголовком і опис сліпої зони Lizard |
| `pass-test-output.txt` | вивід цільових тестів + зведення повного `./mvnw -B test` (коміт `f2d4201`) |
| `git-graph.txt` | `git log --oneline --graph -n 10` |
| `churn.txt` | churn власного проєкту за 90 днів |
| `churn-commons-lang.txt` | churn apache/commons-lang за 90 днів |
| `hotspots-plant-shop.txt` | усі 106 файлів власного проєкту: max/sum CC, коміти, категорія |
| `hotspots-commons-lang.txt` | усі 216 файлів commons-lang: max/sum CC, коміти, категорія, перевірка повноти |
| `hotspots-commons-lang-commits.txt` | теми комітів за 90 днів для кожного файлу матриці commons-lang |

Відтворення: `./scripts/cc-check.sh <файл>` (Lizard у контейнері), `./mvnw -B test`, команда churn з розділу 6.1;
commons-lang клоновано `git clone --filter=blob:none https://github.com/apache/commons-lang.git` (коміт `c833cc1ef`).

### Додаток Б. Перелік скріншотів

1. FAIL-тест на коміті `42f3690` (розділ 3.3).
2. PASS / GREEN цільових тестів на коміті `f2d4201` (розділ 4.5).
3. Reactor Summary повного `./mvnw -B test` (розділ 4.5).
4. Дерево комітів FAIL → PASS (розділ 4.6).
5. GitHub Actions: успішний запуск `code-metrics` (Job Success) (розділ 5.3).
6. GitHub Actions: артефакт `lizard-report` (розділ 5.3).
7. (за бажанням) Термінал із командою churn (розділ 6.3).
