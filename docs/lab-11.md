# Лабораторна робота №11 — теорія для захисту

**Тема:** Documentation as Code — корпоративна база знань (MkDocs Material, Obsidian, ADR), контрактне
документування API за підходом API-first (OpenAPI, Swagger UI, Spectral, OpenAPI Generator) та CI/CD-конвеєр
документації з краш-тестом «не збирається — не їде». Звіт з виконання — [`lab-11-report.md`](lab-11-report.md).

## Зміст

- [1. Що вимагає завдання (коротко)](#1-що-вимагає-завдання-коротко)
- [2. Documentation as Code: суть і принципи](#2-documentation-as-code-суть-і-принципи)
- [3. Single Source of Truth (штраф −2)](#3-single-source-of-truth-штраф-2)
- [4. Code-first і API-first (штраф −2)](#4-code-first-і-api-first-штраф-2)
- [5. Чотирирівнева піраміда документації](#5-чотирирівнева-піраміда-документації)
- [6. Генератори статичних сайтів і MkDocs Material](#6-генератори-статичних-сайтів-і-mkdocs-material)
- [7. Strict-режим: як дефект документації зупиняє збірку](#7-strict-режим-як-дефект-документації-зупиняє-збірку)
- [8. Front matter-паспорт документа](#8-front-matter-паспорт-документа)
- [9. CODEOWNERS і власник документа](#9-codeowners-і-власник-документа)
- [10. ADR: канон запису рішень](#10-adr-канон-запису-рішень)
- [11. Obsidian і граф знань](#11-obsidian-і-граф-знань)
- [12. OpenAPI Specification](#12-openapi-specification)
- [13. Swagger Editor, Swagger UI і «Try it out»](#13-swagger-editor-swagger-ui-і-try-it-out)
- [14. Лінтинг контракту: Spectral](#14-лінтинг-контракту-spectral)
- [15. Генерація SDK: OpenAPI Generator](#15-генерація-sdk-openapi-generator)
- [16. Контрактний дрейф і contract-тести (Schemathesis, Dredd)](#16-контрактний-дрейф-і-contract-тести-schemathesis-dredd)
- [17. Формат помилок: RFC 9457 Problem Details](#17-формат-помилок-rfc-9457-problem-details)
- [18. CI/CD документації: GitHub Actions і GitHub Pages](#18-cicd-документації-github-actions-і-github-pages)
- [19. markdownlint](#19-markdownlint)
- [20. Краш-тест FAIL → PASS](#20-краш-тест-fail--pass)
- [21. Definition of Done: docs-or-don't-merge, CHANGELOG, версії документації](#21-definition-of-done-docs-or-dont-merge-changelog-версії-документації)
- [22. Безперервний аудит актуальності та деприкація](#22-безперервний-аудит-актуальності-та-деприкація)
- [23. Варіативний пункт: Javadoc](#23-варіативний-пункт-javadoc)
- [24. Як це зроблено в Plant Shop (шпаргалка для Live Demo)](#24-як-це-зроблено-в-plant-shop-шпаргалка-для-live-demo)
- [25. Питання та відповіді для захисту](#25-питання-та-відповіді-для-захисту)
- [26. Словник термінів](#26-словник-термінів)

---

## 1. Що вимагає завдання (коротко)

| Завдання | Роль | Що зробити | Результат у проєкті |
|---|---|---|---|
| 1 (3 бали) | Documentation Engineer | розкласти документацію за пірамідою README → developer → API → architecture; MkDocs Material з `nav`, мовою `uk`, плагінами search, mermaid2, git-revision-date-localized; front matter `owner` / `status` / `review_date` у кожному документі; `.github/CODEOWNERS`; кущ ADR з нумерацією і статусами; граф Obsidian без «островів» | `mkdocs.yml`, `docs/developer/`, `docs/api/`, `docs/architecture/`, `overrides/main.html`, `.github/CODEOWNERS`, ADR-0001…0006, `.obsidian/`, `scripts/doc-link-graph.sh` |
| 2 (3 бали) | API-архітектор | `docs/api/openapi.yaml` (≥ 3 реальні ендпоінти, `info` із SemVer, `components.schemas` з `required`/`enum`/`example`, 200/4xx/5xx зі спільним `Error`); Swagger UI з «Try it out»; SDK через OpenAPI Generator; Spectral 0 errors; сторінка API рендериться з контракту; перевірка дрейфу проти живого API; варіативно — Javadoc | контракт 1.0.0 на 6 операцій, `.spectral.yaml`, `docs/api/reference.md`, `scripts/api-sdk.sh`, `scripts/api-contract-test.sh`, `docs/api/javadoc.md` |
| 3 (4 бали) | DevOps-інженер документації | `.github/workflows/docs.yml`: checkout → Python → mkdocs-material → markdownlint → `mkdocs build --strict` → GitHub Pages; FAIL-коміт з дефектом і червоним job (Exit Code 1); PASS-коміт; чек-лист docs-or-don't-merge у CONTRIBUTING; таблиця DoD у README; CI-аудит `review_date`; деприкація замість видалення; теги `docs-vX.Y` | `docs.yml`, коміти `docs(ci): ... FAIL` / `PASS`, `docs/developer/CONTRIBUTING.md`, `scripts/doc-review-audit.sh`, теги `docs-v1.0`, `docs-v2.0` |

**Штрафи на захисті:** −2, якщо не можете пояснити принцип Single Source of Truth або плутаєте code-first і
API-first (розділи 3 і 4 цього файла — вивчити напам'ять); −3 за порушення регламенту взаємодії з ШІ (немає логу
діалогу, або «ШІ згенерував специфікацію OpenAPI чи workflow замість студента»). Тому кожен рядок `openapi.yaml` і
`docs.yml` треба вміти пояснити своїми словами і показати, звідки взявся (код контролера, DTO, правило Spectral).

---

## 2. Documentation as Code: суть і принципи

**Documentation as Code (DaC, Docs as Code)** — підхід, за якого до документації застосовуються ті самі
інженерні практики, що й до коду. Документація перестає бути «файлом у Word на диску в аналітика» і стає
артефактом репозиторію:

| Практика коду | Те саме для документації | У Plant Shop |
|---|---|---|
| текстовий формат, зручний для diff | Markdown, YAML, Mermaid замість картинок | `docs/**/*.md`, `openapi.yaml`, діаграми в SDD |
| версіонування в Git | історія, `git blame`, `git log --follow`, теги релізів | теги `docs-v1.0`, `docs-v2.0` |
| code review | рецензія документації в тому самому PR; власник — обов'язковий рецензент | `.github/CODEOWNERS`, шаблон PR |
| лінтери | markdownlint (стиль), Spectral (контракт) | `.markdownlint-cli2.yaml`, `.spectral.yaml` |
| збірка, що падає на помилках | `mkdocs build --strict` | Exit Code 1 на мертвому посиланні |
| CI/CD | автоматична збірка і публікація | `.github/workflows/docs.yml` → GitHub Pages |
| генерація з єдиного джерела | Swagger UI і SDK з контракту, Javadoc з коментарів | `docs/api/reference.md`, `sdk-client/`, `site/javadoc/` |

**Навіщо:** документ і код змінюються одним комітом, тож розбіжність видно в diff і її можна заблокувати
автоматично. «Мертві» формати (Word, PDF, вікі окремо від коду) не мають ні рев'ю, ні перевірок, ні зв'язку з
версією коду — вони неминуче застарівають (Documentation Decay з ЛР№10).

**Головне інженерне правило ЛР№11 — «не збирається — не їде»:** якщо документацію неможливо зібрати без
попереджень (мертве посилання, сторінка поза навігацією, зламаний синтаксис, контракт без описів), конвеєр
завершується з Exit Code 1, і нова версія не публікується — так само, як червоний тест не пускає реліз коду.

---

## 3. Single Source of Truth (штраф −2)

**Single Source of Truth (SSOT, єдине джерело істини)** — кожен факт про систему зберігається **рівно в одному
місці**, а всі інші представлення цього факту **генеруються (рендеряться) з нього**, а не копіюються вручну.

Чому це важливо: якщо факт записано у двох місцях, рано чи пізно одне з них оновлять, а інше — ні. З цього
моменту документація суперечить сама собі, і читач не знає, якій копії вірити.

| Факт | Єдине джерело в Plant Shop | Що з нього генерується (не копіюється) |
|---|---|---|
| контракт API (маршрути, схеми, приклади, версія) | `docs/api/openapi.yaml` | сторінка Swagger UI `docs/api/reference.md`; Java-SDK `sdk-client/`; contract-тест Schemathesis |
| вміст README | `README.md` | головна сторінка сайту `docs/index.md` (плагін `include-markdown`) |
| журнал змін | `CHANGELOG.md` | сторінка `docs/developer/changelog.md` (`include-markdown`) |
| власник, статус, дата перегляду документа | front matter самого документа | плашка «Паспорт документа» на сайті (`overrides/main.html`), звіт `doc-review-audit.sh` |
| дата останньої зміни сторінки | історія Git | «Останнє оновлення» на сайті (`git-revision-date-localized`) |
| внутрішня будова класів | Javadoc-коментарі в коді | `site/javadoc/` |

**Як довести, що довідка не може розсинхронізуватися з контрактом:** у `docs/api/reference.md` немає жодного
рядка про маршрути — там лише тег `<swagger-ui src="openapi.yaml"/>`. Під час збірки плагін копіює **той самий
файл** `openapi.yaml` у `site/api/openapi.yaml`, а Swagger UI у браузері читає його й будує сторінку. Змінили
версію в контракті — після збірки змінилась і довідка; окремої копії, яку можна «забути оновити», не існує.

**Протилежність SSOT** — «копіпаста»: перелік ендпоінтів у README, в SDD і в Wiki одночасно. У ЛР№10 у README
була таблиця «API at a glance», яка дублювала SDD 5.1. У ЛР№11 її прибрано з README: повний перелік — лише в SDD,
формальний контракт — лише в `openapi.yaml`.

**SSOT не означає «один файл на все»:** означає один **власник кожного факту**. SDD і `openapi.yaml` обидва
говорять про API, але про різне: SDD — повний перелік і поведінка всіх 30 ендпоінтів (хто викликає, які гарантії),
контракт — формальні схеми 6 операцій клієнтського шляху.

---

## 4. Code-first і API-first (штраф −2)

| | **Code-first** | **API-first (design-first, contract-first)** |
|---|---|---|
| Що пишуть спочатку | код контролерів; специфікацію генерують з коду (анотації, рефлексія) | контракт (OpenAPI-файл); код реалізують під контракт |
| Хто джерело істини | код | контракт |
| Інструмент у Plant Shop | springdoc-openapi: `http://localhost:<port>/swagger-ui.html`, генерується при запуску сервісу | `docs/api/openapi.yaml`, Swagger UI на сайті, Spectral, OpenAPI Generator |
| Переваги | завжди відповідає коду; нуль ручної роботи | контракт рецензують **до** реалізації; фронтенд і інтеграції працюють паралельно (мок, SDK); ламаючу зміну видно в diff контракту |
| Недоліки | контракт — випадковий наслідок реалізації: перейменували поле DTO — мовчки зламали клієнтів; немає прикладів і описів; без запущеного сервісу довідки немає | контракт треба підтримувати; можливий дрейф між контрактом і кодом (його ловлять drift-check і contract-тести) |
| Як ловлять розбіжності | розбіжностей «немає», але немає й обіцянок | `doc-drift-check.sh` (операція є в коді), Schemathesis (відповіді відповідають схемам) |

**Як пояснити різницю одним реченням:** у code-first документація **описує те, що код уже робить**; в API-first
код **реалізує те, що обіцяно в контракті**.

**Чому в Plant Shop обидва підходи одночасно (ADR-0005):** проєкт історично code-first (springdoc у кожному
сервісі). Для клієнтського шляху (каталог → замовлення → трекінг) у ЛР№11 введено API-first-контракт: його
змінюють першим у PR, з нього генерують довідку і SDK. springdoc лишився для внутрішніх і адміністративних
ендпоінтів. Найсильніший варіант API-first — генерувати серверні інтерфейси з контракту (`openapi-generator -g
spring`), тоді дрейф неможливий на рівні сигнатур; він розглянутий в ADR-0005 як варіант C і відкладений.

**Типова пастка на захисті:** «ми написали `openapi.yaml`, отже це API-first». Ні — якщо файл просто переписали з
коду після реалізації, це code-first, оформлений вручну. API-first визначається **процесом**: зміна API
починається зі зміни контракту, яку рецензують, і код перевіряють на відповідність контракту (а не навпаки).

---

## 5. Чотирирівнева піраміда документації

Кожен рівень відповідає на питання **своєї аудиторії**; читач спускається вниз лише настільки, наскільки йому
потрібно. Нижчі рівні не дублюють вищі, а посилаються на них.

| Рівень | Питання | Аудиторія | Обсяг | У Plant Shop |
|---|---|---|---|---|
| 1. README | «Що це і як запустити?» | усі, бізнес, новачок | прочитати за 5 хвилин | `README.md`: призначення, quick start, карта піраміди, таблиця Definition of Done |
| 2. Developer Docs | «Як приєднатися до розробки?» | розробники, мейнтейнери | | `docs/developer/`: CONTRIBUTING, local-setup, coding-standards, maintenance, handover, changelog |
| 3. API Docs | «Як інтегруватися?» | інтегратори, QA, фронтенд | | `docs/api/`: огляд і формат помилок, Swagger UI з `openapi.yaml`, API приватності, Javadoc |
| 4. Architecture Docs | «Чому система влаштована саме так?» | архітектори, DevOps | | `docs/architecture/`: SDD, ISD, runbook розгортання, матриця аудиторій, ADR |

```text
Plant_Shop/
├── README.md                     рівень 1: що це і як запустити (5 хв)
├── CHANGELOG.md                  журнал змін (рендериться на сайті)
├── mkdocs.yml                    конфігурація бази знань: nav = піраміда
├── overrides/main.html           показ front matter-паспорта на кожній сторінці
└── docs/
    ├── index.md                  головна сайту = README (include, не копія)
    ├── developer/                рівень 2: як приєднатися до розробки
    ├── api/                      рівень 3: як інтегруватися (openapi.yaml - SSOT контракту)
    ├── architecture/             рівень 4: чому так (SDD, ISD, ADR)
    │   └── adr/                  кущ рішень 0001…0006
    ├── sdd.md, isd.md, ...       старі адреси: status: deprecated + посилання на нове місце
    └── lab-*.md, *-report.md     документація процесу: у Git, але не в базі знань
```

**Чому README став коротшим (512 → ~70 рядків):** у ЛР№10 README змішував рівні: Kubernetes-runbook (рівень 4),
приватність (рівень 3), запуск з IDE (рівень 2). Новачок, якому треба «запустити за 5 хвилин», мусив гортати
500 рядків. Тепер кожен розділ живе на своєму рівні, а README лише веде до них.

---

## 6. Генератори статичних сайтів і MkDocs Material

**Static Site Generator (SSG)** — інструмент, що перетворює текстові файли (Markdown) на набір статичних HTML-
сторінок, які можна розмістити на будь-якому вебсервері або GitHub Pages без бекенду і бази даних.

| SSG | Мова | Особливості |
|---|---|---|
| **MkDocs** (+ тема Material) | Python | конфігурація одним `mkdocs.yml`, чистий Markdown, strict-режим, багата екосистема плагінів |
| Docusaurus | JavaScript (React) | MDX (Markdown + JSX), версіонування з коробки |
| Sphinx | Python | reStructuredText, autodoc з docstring Python |
| Hugo, Jekyll | Go, Ruby | загального призначення, блоги |

**Чому MkDocs Material (ADR-0004):** джерело лишається чистим Markdown, тому той самий файл читається на GitHub,
в Obsidian і Notion; strict-режим із валідацією посилань; українська локалізація теми; плагіни для всього, що
потрібно завданню.

Ключові частини `mkdocs.yml`:

| Ключ | Що робить | Значення в проєкті |
|---|---|---|
| `docs_dir` | каталог джерел | `docs` |
| `exclude_docs` | що не потрапляє на сайт | звіти лабораторних (документація процесу) |
| `nav` | дерево навігації | чотири рівні піраміди + теги + архів deprecated |
| `theme.language` | мова інтерфейсу | `uk` |
| `strict`, `validation` | які проблеми є попередженнями | мертві посилання, якорі, сторінки поза nav → `warn` |
| `plugins` | розширення | `search`, `tags`, `include-markdown`, `swagger-ui-tag`, `mermaid2`, `git-revision-date-localized` |
| `markdown_extensions` | синтаксис | `admonition`, `pymdownx.superfences` (Mermaid), `toc` зі `slugify`, що зберігає кирилицю |

Плагіни з завдання:

- **search** — повнотекстовий пошук (lunr.js) у браузері. У lunr немає українського стемера, тому мова пошуку
  `ru` (найближчий кириличний) + `en`.
- **mermaid2** — рендерить діаграми Mermaid (C4, sequence, flowchart з SDD/ISD) у браузері. З Material потрібна
  кастомна fence `mermaid2.fence_mermaid_custom` у `pymdownx.superfences`.
- **git-revision-date-localized** — під кожною сторінкою дата останнього коміту, що її змінив (`git log`). Це
  технічна основа метрики актуальності Лекції 8: дату не пишуть вручну, тому вона не може «збрехати». У CI
  потрібен `fetch-depth: 0` (повна історія), інакше всі сторінки отримають дату останнього коміту.

Плагіни, додані з інженерних причин: **include-markdown** (SSOT для README і CHANGELOG, переписує відносні
посилання), **swagger-ui-tag** (SSOT контракту), **tags** (теги front matter = теги Obsidian).

---

## 7. Strict-режим: як дефект документації зупиняє збірку

`mkdocs build --strict` (або `strict: true` у `mkdocs.yml`) перетворює **кожне попередження збірки на помилку**:
збірка завершується повідомленням `Aborted with N warnings in strict mode!` і кодом виходу **1**. Для CI код
виходу ≠ 0 означає червоний крок і зупинку конвеєра.

Які дефекти стають попередженнями, задає блок `validation` (MkDocs 1.5+):

| Дефект | Налаштування | Приклад повідомлення |
|---|---|---|
| мертве посилання на сторінку | `links.not_found: warn` | `Doc file 'x.md' contains a link 'y.md', but the target is not found among documentation files.` |
| посилання на неіснуючий заголовок | `links.anchors: warn` | `... does not contain an anchor '#no-such-anchor'` |
| orphan — сторінка поза `nav` | `nav.omitted_files: warn` | `The following pages exist in the docs directory, but are not included in the "nav" configuration` |
| `nav` вказує на відсутній файл | `nav.not_found: warn` | |
| абсолютні посилання `/page` | `links.absolute_links: warn` | |

**Важлива деталь, яку варто сказати на захисті:** за замовчуванням у MkDocs 1.6 сторінка поза `nav` — лише
`INFO`, а не попередження, тож «голий» `--strict` її **не зупинить**. Тому в `mkdocs.yml` явно задано
`nav.omitted_files: warn`. Аналогічно перевірка якорів за замовчуванням `info`.

Що strict **не** ловить: зміст, який бреше (сторінка синтаксично ціла, але описує стару поведінку) — це закривають
drift-check, contract-тести, `review_date` і рецензія власника.

Чому посилання між документами — звичайні відносні Markdown-посилання, а не `[[wikilinks]]`: strict-збірка
перевіряє лише Markdown-посилання; «мертвий» wikilink пройшов би збірку непоміченим і обійшов би правило «не
збирається — не їде». Obsidian однаково будує граф і з Markdown-посилань.

---

## 8. Front matter-паспорт документа

**Front matter** — YAML-блок між `---` на початку Markdown-файла. Його читають MkDocs (`page.meta`), Obsidian
(Properties), GitHub (показує таблицею), скрипти.

```yaml
---
owner: VladdHoryn          # хто відповідає за актуальність
status: published          # draft / in-review / published / deprecated
review_date: 2027-02-20    # дата наступного обов'язкового перегляду
tags: [architecture, api]  # теги графа Obsidian і сторінки тегів сайту
superseded_by: architecture/sdd.md   # лише для deprecated: куди переспрямувати
---
```

| Поле | Навіщо | Хто використовує |
|---|---|---|
| `owner` | роль «власника документа» з Лекції 8: конкретна людина, а не «команда» | плашка на сайті, CODEOWNERS (той самий власник), звіт аудиту |
| `status` | життєвий цикл документа | плашка (deprecated — жовта), аудит не рахує deprecated простроченими |
| `review_date` | безперервний аудит актуальності замість разового | `scripts/doc-review-audit.sh` у CI |
| `tags` | класифікація, граф знань | Obsidian, плагін `tags` |

**Життєвий цикл `status`:** `draft` (пишеться, неповний) → `in-review` (на рецензії; у проєкті так позначені ADR зі
статусом Proposed) → `published` (чинний) → `deprecated` (застарів, лишається з посиланням на актуальний).

**Як заповнено `review_date` (з урахуванням аудиту ЛР№10):** пороги аудиту — понад 90 днів без оновлення —
«підозрілий», понад 180 — «застарілий». Тому експлуатаційні/розробницькі документи переглядаються кожні 90 днів
(README, ISD, developer, API), архітектурні — кожні 180 днів (SDD, ADR). Для документів, переглянутих у ЛР№10
14.11.2026, дати збережено з регламенту ЛР№10 (2027-02-12, 2027-05-13); нові документи ЛР№11 від 22.11.2026
отримали 2027-02-20 (+90) або 2027-05-21 (+180).

**Чому прибрано таблицю власників із `maintenance.md`:** вона дублювала факти, що тепер у front matter (порушення
SSOT). Зведену таблицю тепер **друкує скрипт** з front matter.

---

## 9. CODEOWNERS і власник документа

`.github/CODEOWNERS` — файл GitHub, що зіставляє шаблони шляхів з власниками. Коли PR змінює файл, власник
автоматично додається рецензентом. Якщо в захисті гілки (branch protection / ruleset) увімкнено **Require review
from Code Owners**, PR **фізично неможливо злити** без схвалення власника — це інженерна реалізація ролі
«власника документа» з Лекції 8.

Правила синтаксису: один шаблон на рядок, як у `.gitignore`; **виграє останній відповідний рядок**, тому загальні
правила йдуть першими, конкретні — нижче; `/docs/` з початковим `/` — від кореня репозиторію.

Обмеження, яке варто знати: у навчальному репозиторії з одним автором власник не може схвалити власний PR
(GitHub не дозволяє self-approve), тому для демонстрації або додають другого учасника, або показують, що PR
блокується (статус «Review required»).

---

## 10. ADR: канон запису рішень

**ADR (Architecture Decision Record)** — короткий документ про одне архітектурне рішення: контекст, розглянуті
альтернативи, рішення, наслідки. Відповідає на питання «**чому** так», на яке код не відповідає.

Канон Лекції 9:

| Правило | Що означає | У проєкті |
|---|---|---|
| наскрізна нумерація | `NNNN-коротка-назва.md`, номери не перевикористовуються | `0001` … `0006` |
| статуси | `Proposed` → `Accepted` → `Superseded by ADR-NNNN` (або `Deprecated`, `Rejected`) | 0001, 0002, 0003, 0005 — Accepted; 0004, 0006 — Proposed |
| незмінність | прийнятий запис не переписується; нове рішення = новий запис; старий лише отримує статус `Superseded` | ADR-0003 **доповнює** ADR-0002 окремим записом, а не правкою 0002 |
| «кущ» (граф) рішень | записи посилаються один на одного: залежить від, доповнює, замінює | 0002 → 0001, 0003 → 0002, 0005 → 0003, 0006 → 0005 |

**Проблема незмінності при переміщенні файлів:** перенесення SDD у `docs/architecture/` зламало б посилання
`../../sdd.md` в ADR-0001…0003. Виправлено лише шлях у посиланні (рядок «Пов'язані документи»); текст рішення,
статус і дату не змінено — це технічна правка адреси, а не зміна рішення. Аналогічно до ADR додано front matter —
це метадані сторінки бази знань, а не частина рішення.

**Чим статус ADR відрізняється від `status` у front matter:** статус ADR — стан **рішення** (Accepted = рішення
діє), `status` у front matter — стан **сторінки** в базі знань. Відповідність у проєкті: Proposed → `in-review`,
Accepted → `published`, Superseded/Deprecated → `deprecated`.

**Демонстрація життєвого циклу в ЛР№11:** ADR-0004 (DaC на MkDocs) створено зі статусом Proposed, бо рішення
підтверджується лише першим зеленим конвеєром з публікацією сайту; тоді змінюється **лише рядок статусу** на
Accepted (окремим комітом, видно в `git log`). ADR-0006 (єдиний формат помилок) — Proposed: проблему знайдено, але
рішення відкладено до API 2.0.0.

---

## 11. Obsidian і граф знань

**Obsidian** — безкоштовний редактор баз знань на звичайних Markdown-файлах. **Vault** — каталог, який Obsidian
відкриває як базу знань (у проєкті — корінь репозиторію; налаштування в `.obsidian/app.json`,
`.obsidian/graph.json`). **Graph view** показує документи як вузли, посилання як ребра, теги — як окремі вузли.

| Поняття | Що це | У проєкті |
|---|---|---|
| wikilink | посилання `[[sdd]]` у синтаксисі Obsidian | не використовується: strict-збірка не перевіряє wikilinks, Notion їх не розуміє |
| Markdown-посилання | `[SDD](../architecture/sdd.md)` | використовується всюди; Obsidian будує з нього ті самі ребра графа (`useMarkdownLinks: true`) |
| теги | `#architecture` у тексті або `tags:` у front matter | `architecture`, `api`, `ops`, `developer`, `adr`, `overview`, `archive` |
| «острів» | вузол без жодного ребра — знання, до якого не можна дійти | 0 після виправлення (див. нижче) |

**Зв'язність графа перевіряється не лише скріншотом**, а скриптом `scripts/doc-link-graph.sh`: він будує ті самі
ребра (відносні Markdown-посилання + include) і друкує документи з нулем зв'язків. Перший запуск знайшов два
острови — `docs/architecture/audience-coverage.md` (жодного посилання ні до, ні від) і `docs/tags.md`; після
додавання посилань — 29 документів, 72 ребра, 0 островів.

---

## 12. OpenAPI Specification

**OpenAPI Specification (OAS)** — стандарт машинно-читного опису HTTP API у YAML/JSON (раніше Swagger
Specification). Версії: 2.0 (Swagger), 3.0.x, 3.1.x (повна сумісність з JSON Schema 2020-12). У проєкті —
**3.0.3**, бо її найстабільніше підтримують усі інструменти ланцюжка (Swagger UI, Spectral, генератор,
Schemathesis).

Структура документа:

| Розділ | Що містить | У контракті Plant Shop |
|---|---|---|
| `openapi` | версія специфікації | `3.0.3` |
| `info` | назва, **версія API за SemVer**, опис, контакт, ліцензія | `Plant Shop Customer API`, `1.0.0` |
| `servers` | базові URL | `http://localhost:8080` (gateway) |
| `tags` | групи операцій | Catalog, Orders |
| `paths` | маршрути → методи → операції | 5 шляхів, 6 операцій |
| операція | `operationId`, `summary`, `description`, `parameters`, `requestBody`, `responses` | напр. `trackOrder` |
| `components.schemas` | моделі даних з `type`, `required`, `enum`, `example`, `nullable` | `Plant`, `Order`, `OrderTracking`, `Error`, … |
| `components.responses` | спільні відповіді | `BadRequest`, `NotFound`, `OutOfStock`, `ServiceUnavailable` |
| `components.parameters` | спільні параметри | `PlantId`, `OrderId` |
| `$ref` | посилання на компонент замість копії (SSOT усередині контракту) | `$ref: "#/components/schemas/Error"` |

**SemVer для API:** `MAJOR.MINOR.PATCH`. MAJOR — ламаюча зміна для клієнтів (видалили поле, змінили тип, новий
обов'язковий параметр, звузили enum); MINOR — сумісне розширення (нова операція, необов'язкове поле); PATCH —
лише описи та приклади. Контракт — **знімок поведінки системи в часі**: версія каже клієнту, чи зламається його
код.

Операції контракту (усі — реальні маршрути коду, перевіряє `doc-drift-check.sh`, перевірка 2):

| Операція | Маршрут | Код | Відповіді |
|---|---|---|---|
| `searchPlants` | `GET /api/catalog/plants` | `PlantController.search` | 200 `PlantPage`, 400, 503 |
| `getPlant` | `GET /api/catalog/plants/{id}` | `PlantController.findById` | 200 `Plant`, 400, 404, 503 |
| `createOrder` | `POST /api/orders` | `OrderController.create` | **202** `Order`, 400, 409, 503 |
| `listCustomerOrders` | `GET /api/orders?email=` | `OrderController.findByCustomer` | 200 `Order[]`, 400, 503 |
| `getOrder` | `GET /api/orders/{id}` | `OrderController.findById` | 200 `Order`, 400, 404, 503 |
| `trackOrder` | `GET /api/orders/{id}/tracking` | `OrderTrackingController.track` (краш-тест ЛР№10, ADR-0003) | 200 `OrderTracking`, 400, 404, 503 |

Чому `POST /api/orders` повертає **202 Accepted**, а не 201: замовлення приймається асинхронно — сага (резерв,
оплата) ще не завершилась, статус `PENDING`.

---

## 13. Swagger Editor, Swagger UI і «Try it out»

| Інструмент | Призначення |
|---|---|
| **Swagger Editor** (editor.swagger.io або Docker-образ `swaggerapi/swagger-editor`) | редагування контракту з живою валідацією за схемою OAS: підсвічує синтаксичні й структурні помилки |
| **Swagger UI** | інтерактивна довідка, згенерована з контракту: операції, схеми, приклади; кнопка **Try it out** надсилає справжній запит |
| **mkdocs-swagger-ui-tag** | вбудовує Swagger UI у сторінку MkDocs: `<swagger-ui src="openapi.yaml"/>` |

**«Try it out»** — Swagger UI формує HTTP-запит із введених параметрів і надсилає його з браузера на URL із
`servers`. Щоб виклик зі сторінки сайту (`localhost:8000` або `github.io`) дійшов до gateway `localhost:8080`,
gateway має дозволяти **CORS** для чужого origin; у Plant Shop `globalcors` у конфігурації api-gateway дозволяє
`*`, тому виклик працює без змін коду.

---

## 14. Лінтинг контракту: Spectral

**Spectral** (Stoplight) — лінтер JSON/YAML, зокрема OpenAPI. Правила об'єднують у **ruleset** (`.spectral.yaml`).
Кожне правило: `given` (JSONPath — які вузли документа перевіряти), `then` (поле і функція перевірки),
`severity` (`error` / `warn` / `info` / `hint`).

| Правило в `.spectral.yaml` | Вимога завдання | Як працює |
|---|---|---|
| `extends: spectral:oas` | — | вбудований набір (operationId, теги, валідні `$ref`, приклади за схемою…) |
| `operation-description: error` | кожна операція має непорожній `description` | вбудоване правило, підвищене з warn до error |
| `plantshop-schema-example` | кожна схема — хоча б один `example` | `given: $.components.schemas[*]`, `then: field: example, function: defined` |
| `plantshop-error-response-shared` | коди помилок описано узгоджено | кожна 4xx/5xx-відповідь шляху — `$ref` на `#/components/responses/...` (`resolved: false`, бо після розв'язання `$ref` зникає) |
| `plantshop-error-schema` | один об'єкт `Error` | кожна спільна відповідь-помилка має `schema: $ref: #/components/schemas/Error` |
| `plantshop-operation-error-codes` | 200/4xx/5xx | функція `schema`: у `responses` є хоча б одна з 400/404/409 і одна з 500/503 |

Поріг — **0 errors** (`--fail-severity error`: код виходу 1 лише за помилок). Що правила справді ловлять,
доведено самоперевіркою (`docs/lab-11/spectral-ruleset-selftest.txt`): копія контракту з трьома внесеними
дефектами дає 3 errors і код виходу 1.

---

## 15. Генерація SDK: OpenAPI Generator

**OpenAPI Generator** генерує з контракту клієнтські бібліотеки (SDK, 50+ мов), серверні заглушки і документацію:

```bash
openapi-generator-cli generate -i docs/api/openapi.yaml -g java -o sdk-client
```

У проєкті — закріплений Docker-образ `openapitools/openapi-generator-cli:v7.14.0` у `scripts/api-sdk.sh`,
бібліотека `native` (java.net.http, без сторонніх HTTP-клієнтів). Результат: класи `CatalogApi`
(`searchPlants`, `getPlant`), `OrdersApi` (`createOrder`, `getOrder`, `listCustomerOrders`, `trackOrder`) і моделі
(`Order`, `OrderTracking`, `Error`, …); SDK компілюється в `plantshop-api-client-1.0.0.jar`.

Чому SDK не комітиться (`.gitignore`): це **похідний артефакт** контракту. Закомітити його = створити другу копію
контракту, яка може розійтися з першою (порушення SSOT). SDK завжди відтворюється з `openapi.yaml`.

---

## 16. Контрактний дрейф і contract-тести (Schemathesis, Dredd)

**Контрактний дрейф** — розбіжність між задокументованим контрактом і фактичною поведінкою API: код повертає поле,
якого немає в схемі, інший код статусу, інший `Content-Type`, або контракт обіцяє маршрут, якого немає. За
завданням розбіжність — **дефект документації**, який фіксують у звіті.

Три рівні перевірки в проєкті:

| Рівень | Що ловить | Інструмент |
|---|---|---|
| статичний, без запуску | операція контракту відсутня в контролерах | `doc-drift-check.sh`, перевірка 2 (CI) |
| інтерактивний | одиничний виклик відповідає схемі | «Try it out» у Swagger UI |
| автоматизований contract-тест | статуси, `Content-Type`, тіла всіх відповідей проти схем; 5xx | **Schemathesis** проти запущеного gateway |

**Schemathesis** — property-based-тестування API: читає контракт, **генерує сотні запитів** (зокрема граничні
значення, яких людина не придумає) і перевіряє кожну відповідь: `not_a_server_error` (немає 5xx),
`status_code_conformance` (код задокументовано), `content_type_conformance`, `response_schema_conformance` (тіло
валідне за схемою). **Dredd** — інший підхід: виконує **приклади** з контракту як тест-кейси і порівнює відповіді.
Schemathesis знаходить більше (генерує дані), Dredd — простіший і детермінований.

---

## 17. Формат помилок: RFC 9457 Problem Details

**RFC 9457** (раніше RFC 7807) — стандартний JSON-формат помилки HTTP API, тип `application/problem+json`:
`type` (URI типу проблеми, `about:blank` за замовчуванням), `title` (коротко), `status` (код HTTP), `detail`
(пояснення конкретного випадку), `instance` (шлях запиту) + довільні розширення (`errors`, `plantIds`). У Spring 6
— клас `ProblemDetail`.

Знахідка ЛР№11: сервіси Plant Shop повертають помилки у **двох** форматах — ProblemDetail з
`GlobalExceptionHandler` і формат Spring Boot `{timestamp, status, error, path}` для помилок самого фреймворку
(зламаний JSON, неправильний тип параметра) та gateway. Контракт 1.0.0 описує це чесно одним об'єктом `Error`
(обов'язкове лише `status`, обидва набори полів необов'язкові, обидва media type), а повну уніфікацію
запропоновано в ADR-0006 (Proposed). Чому не увімкнули просто `spring.mvc.problemdetails.enabled`: вбудований
обробник має `@Order(0)` і перехопив би валідацію раніше за наш, тобто зникло б поле `errors` — це зміна поведінки.

---

## 18. CI/CD документації: GitHub Actions і GitHub Pages

**GitHub Actions** — CI/CD GitHub: workflow (`.github/workflows/*.yml`) → jobs (окремі віртуальні машини) → steps
(команди або готові actions). **GitHub Pages** — безкоштовний хостинг статичних сайтів репозиторію; сучасний спосіб
публікації — через Actions (`actions/upload-pages-artifact` + `actions/deploy-pages`), джерело Pages у
налаштуваннях репозиторію — «GitHub Actions».

Стадії `.github/workflows/docs.yml` (job `build` → job `deploy`):

| # | Крок | Навіщо | Падіння = |
|---|---|---|---|
| 1 | `actions/checkout` з `fetch-depth: 0` | повна історія для дат сторінок | |
| 2 | `actions/setup-python` 3.12 з кешем pip | середовище MkDocs | |
| 3 | `pip install -r tools/mkdocs/requirements.txt` | mkdocs-material і плагіни, ті самі версії, що локально | |
| 4 | markdownlint | стиль Markdown | Exit 1 |
| 5 | Spectral | якість контракту, 0 errors | Exit 1 |
| 6 | `doc-review-audit.sh` → Step Summary | прострочені `review_date` (звіт, не блокує) | |
| 7 | `mkdocs build --strict` | мертві посилання, якорі, orphan-сторінки | **Exit 1 — краш-тест** |
| 8 | Javadoc → `site/javadoc` | варіативний пункт | Exit 1 |
| 9 | `upload-pages-artifact` → job `deploy` → `deploy-pages` | публікація, лише push у `master` або `lab/Lab_11` після зеленого `build` | |

Безпека: глобально `permissions: contents: read`; права `pages: write` і `id-token: write` (OIDC-токен для
Pages) — лише job `deploy`. `environment: github-pages` дає URL сайту і правила захисту середовища (з яких гілок
дозволено деплой). `concurrency` скасовує застарілий запуск тієї самої гілки.

Що потрібно налаштувати в GitHub вручну (не в коді): Settings → Pages → Source: **GitHub Actions**; Settings →
Environments → `github-pages` → дозволити гілку `lab/Lab_11` (за замовчуванням деплой дозволено лише з основної
гілки); для CODEOWNERS — правило захисту `master` з «Require review from Code Owners».

---

## 19. markdownlint

**markdownlint** — лінтер стилю Markdown (правила `MD001`…). У проєкті — `markdownlint-cli2` 0.18.1 у Docker,
конфігурація `.markdownlint-cli2.yaml`: усі правила за замовчуванням, крім обґрунтованих винятків — довжина рядка
120 (як у `.editorconfig`), але не для таблиць і коду; дозволений HTML лише `<swagger-ui>`; `MD041` вимкнено
(сторінки починаються з front matter). Звіти лабораторних ігноруються — їх не редагують.

Перший запуск знайшов 6 порушень у документах продукту: 4 задовгі рядки, блок коду без мови (`MD040`), 2 списки
без порожнього рядка (`MD032`). Виправлено; у ADR-0002 — лише вставлено порожній рядок перед списком (рендер і текст
рішення не змінилися).

---

## 20. Краш-тест FAIL → PASS

Мета — **довести**, що правило «не збирається — не їде» справді діє, а не лише задеклароване.

| Крок | Що зроблено | Доказ |
|---|---|---|
| FAIL | у `docs/` внесено один контрольований дефект — мертве посилання на неіснуючу сторінку; коміт `docs(ci): inject broken reference into strict build, FAIL` | локально `docs/lab-11/strict-build-fail.txt` (`Aborted with 1 warnings in strict mode!`, exit code 1); у GitHub — червоний job `lint and strict build`, `deploy` пропущено, сайт не змінився |
| PASS | дефект усунено; коміт `docs(ci): restore strict build pipeline, PASS` | `docs/lab-11/strict-build-pass.txt` (exit 0); зелений job збірки; `deploy` публікує сайт з датами редагування (за ввімкненого GitHub Pages) |

Чому один дефект: щоб падіння мало **одну** причину і було видно, що саме його ловить. Три варіанти дефекту з
завдання і що їх ловить: мертве посилання — `links.not_found`; orphan-сторінка — `nav.omitted_files: warn`
(без цього налаштування strict її пропустив би!); зламаний Markdown — markdownlint (стиль) або MkDocs (якщо зламано
include/YAML).

---

## 21. Definition of Done: docs-or-don't-merge, CHANGELOG, версії документації

**Definition of Done (DoD)** — перелік умов, без яких задача не вважається завершеною. **docs-or-don't-merge** —
пункт DoD: PR, що змінює **публічну поведінку** (ендпоінт, формат відповіді, конфігурацію, подію, розгортання), не
зливається без оновлення `docs/`, OpenAPI і CHANGELOG. Опубліковано в `docs/developer/CONTRIBUTING.md` і продубльовано
як чек-лист у шаблоні PR (шаблон — інструмент, а не друге джерело правил: він посилається на CONTRIBUTING).

Таблиця документаційних перевірок у README:

| Що | Інструмент | Блокує? |
|---|---|---|
| контракти | Spectral | так |
| розмітка | markdownlint | так |
| посилання і навігація | `mkdocs build --strict` | так |
| актуальність | `review_date` з front matter | звіт |
| код ↔ документація | `doc-drift-check.sh` | так |
| власник | CODEOWNERS | так (з branch protection) |

**CHANGELOG** у форматі Keep a Changelog: розділ `Unreleased`, групи Added / Changed / Deprecated / Removed / Fixed /
Security, найновіша версія зверху.

**Версії документації — теги Git:** `docs-v1.0` (паспорт ЛР№10) і `docs-v2.0` (база знань ЛР№11 — мажорна зміна,
бо змінились адреси документів). Знімок документації, що відповідав релізу, відновлюється з тегу так само, як код:
`git checkout docs-v1.0`. Тег анотований (`git tag -a`), бо зберігає автора, дату і повідомлення.

---

## 22. Безперервний аудит актуальності та деприкація

У ЛР№10 актуальність перевірялась **разово** (`doc-currency.sh` на дату аудиту). У ЛР№11 — **безперервно**:
`scripts/doc-review-audit.sh` у кожному запуску CI порівнює поточну дату з `review_date` кожного документа
продукту і друкує таблицю в Step Summary: `OK`, `due soon` (≤ 30 днів), `OVERDUE`, `NO PASSPORT`,
`DEPRECATED -> нове місце`. За `STRICT=1` прострочені сторінки валять збірку (у CI режим звіту, щоб прострочена
дата не блокувала термінові виправлення коду).

**Деприкація замість видалення:** застаріла сторінка не видаляється — отримує `status: deprecated`,
`superseded_by` і банер `DEPRECATED` з посиланням на актуальний документ. Причина: на стару адресу ведуть
посилання (у Plant Shop — зі звітів лабораторних, які не можна редагувати), закладки, пошуковики; видалення
створило б мертві посилання. У ЛР№11 так позначено 4 старі адреси: `docs/sdd.md`, `docs/isd.md`,
`docs/maintenance.md`, `docs/handover.md`.

---

## 23. Варіативний пункт: Javadoc

Для API-проєктів завдання вимагає статичний довідник з коментарів коду рідної екосистеми (JSDoc / Sphinx autodoc /
godoc). Для Java це **Javadoc**: `./mvnw javadoc:aggregate` (скрипт `scripts/docs-javadoc.sh`) збирає довідник
усіх модулів у `target/reports/apidocs/`; CI копіює його в `site/javadoc/`, і він публікується поруч із сайтом,
окремим розділом бази знань «API → Javadoc». `-Ddoclint=none`: без цього Javadoc вимагав би коментар до кожного
методу, включно з тими, що генерує Lombok.

Javadoc і OpenAPI — різні аудиторії: Javadoc описує **внутрішні класи** (розробник), OpenAPI — **зовнішній
контракт** (інтегратор).

---

## 24. Як це зроблено в Plant Shop (шпаргалка для Live Demo)

```bash
./scripts/docs-build.sh serve          # сайт на http://localhost:8000/Plant_Shop/
./scripts/docs-build.sh                # strict-збірка, як у CI; exit code в останньому рядку
./scripts/docs-lint.sh                 # markdownlint + Spectral + review_date
./scripts/doc-review-audit.sh 2027-03-01   # показати OVERDUE на майбутню дату
./scripts/doc-drift-check.sh           # код ↔ SDD / OpenAPI / ISD
./scripts/doc-link-graph.sh            # граф знань і острови
./scripts/api-sdk.sh                   # SDK з контракту
docker compose up -d --build && ./scripts/api-contract-test.sh   # Schemathesis проти живого API
git log --oneline --graph -8           # FAIL і PASS коміти
git tag -n docs-v*                     # версії документації
```

Демо краш-тесту «вживу» за 1 хвилину: додати в будь-яку сторінку `[x](nope.md)` → `./scripts/docs-build.sh` →
`Aborted with 1 warnings in strict mode!`, `exit code 1` → прибрати → `PASS (strict)`.

---

## 25. Питання та відповіді для захисту

**1. Що таке Documentation as Code?** Підхід, за якого документація живе в репозиторії поряд із кодом у текстових
форматах і проходить ті самі практики: Git, рев'ю в PR, лінтери, автоматична збірка й публікація в CI.

**2. Поясніть Single Source of Truth на прикладі вашого проєкту.** Кожен факт зберігається в одному місці, решта
— генерується. Контракт API — лише `docs/api/openapi.yaml`; сторінка Swagger UI не містить копії, а рендерить цей
файл; SDK генерується з нього; головна сайту включає README, а не копіює його.

**3. Як довести, що довідка API не може розсинхронізуватися з контрактом?** `reference.md` містить лише тег
`<swagger-ui src="openapi.yaml"/>`; при збірці копіюється сам файл контракту, і Swagger UI читає його в браузері.
Двох копій немає, отже розійтися нічому.

**4. Чим code-first відрізняється від API-first?** Code-first: спочатку код, специфікацію генерують з нього
(springdoc) — документація описує, що код уже робить. API-first: спочатку контракт, його рецензують, код
реалізують під нього і перевіряють на відповідність — код реалізує обіцяне.

**5. Який підхід у вас і чому обидва?** Історично code-first (springdoc у сервісах). Для клієнтського шляху
введено API-first-контракт (ADR-0005); springdoc лишився для внутрішніх ендпоінтів.

**6. Що буде, якщо розробник додасть поле в DTO замовлення?** Code-first-довідка springdoc зміниться сама;
контракт — ні. Schemathesis не впаде (додаткові поля схема дозволяє), тому DoD вимагає оновити контракт і MINOR-
версію. Якщо поле **видалити** — `response_schema_conformance` впаде на обов'язковому полі.

**7. Навіщо `--strict`?** Перетворює кожне попередження на помилку з Exit Code 1, тому дефект документації
зупиняє конвеєр і публікацію.

**8. Чи ловить `--strict` сторінку поза навігацією?** За замовчуванням у MkDocs 1.6 — ні (це INFO). Тому в
`mkdocs.yml` задано `validation.nav.omitted_files: warn`.

**9. Що таке front matter і які поля ви використовуєте?** YAML-блок на початку файла: `owner`, `status`,
`review_date`, `tags`, для застарілих — `superseded_by`.

**10. Які статуси документа і що вони означають?** draft — пишеться; in-review — на рецензії; published —
чинний; deprecated — застарів, лишається з посиланням на актуальний.

**11. Як CODEOWNERS забезпечує власника документа?** Власник автоматично стає рецензентом; з правилом «Require
review from Code Owners» PR без його схвалення неможливо злити.

**12. Канон ADR?** Наскрізна нумерація, статуси Proposed → Accepted → Superseded, незмінність: нове рішення —
новий запис, старий лише отримує статус.

**13. Ви змінили ADR-0001…0003. Чи це не порушення незмінності?** Змінено лише шлях у посиланні після
перенесення SDD і додано front matter (метадані сторінки). Рішення, статус, дату не змінено; diff це показує.

**14. Чому посилання Markdown, а не wikilinks?** Strict-збірка перевіряє лише Markdown-посилання; мертвий
wikilink обійшов би правило «не збирається — не їде». Obsidian будує граф з Markdown-посилань так само.

**15. Як довели зв'язність графа?** Скріншот Obsidian + `scripts/doc-link-graph.sh`: 29 документів, 72 ребра,
0 островів (до виправлення — 2 острови).

**16. Структура OpenAPI-документа?** `openapi`, `info` (з версією), `servers`, `tags`, `paths` (операції з
параметрами, тілом, відповідями), `components` (schemas, responses, parameters), `$ref`.

**17. Навіщо SemVer у `info.version`?** Клієнт за версією розуміє, чи зламається його код: MAJOR — ламаюча
зміна, MINOR — сумісне розширення, PATCH — описи.

**18. Чому `POST /api/orders` повертає 202?** Замовлення приймається асинхронно, сага ще триває — статус PENDING.

**19. Що перевіряють ваші правила Spectral?** Непорожній опис кожної операції; `example` у кожній схемі; кожна
4xx/5xx — спільна відповідь з `components.responses` зі схемою `Error`; кожна операція має 4xx і 5xx.

**20. Звідки впевненість, що правила працюють?** Самоперевірка: копія контракту з трьома дефектами → 3 errors,
exit 1 (`spectral-ruleset-selftest.txt`).

**21. Що таке контрактний дрейф і як ви його шукали?** Розбіжність контракту і живого API. Статично — drift-check
(операції контракту є в коді), автоматично — Schemathesis проти запущеного gateway, вручну — «Try it out».

**22. Schemathesis vs Dredd?** Schemathesis генерує багато запитів зі схем (property-based) і перевіряє відповіді;
Dredd виконує приклади з контракту як тести.

**23. Що таке RFC 9457?** Стандартний формат помилки `application/problem+json`: `type`, `title`, `status`,
`detail`, `instance`. У Spring — `ProblemDetail`.

**24. Навіщо генерувати SDK і чому його не комітять?** Клієнт отримує типізований код, що відповідає контракту;
SDK — похідний артефакт, комміт створив би другу копію контракту.

**25. Які стадії вашого конвеєра?** checkout → Python → mkdocs-material → markdownlint → Spectral → аудит
`review_date` → `mkdocs build --strict` → Javadoc → артефакт → deploy на GitHub Pages.

**26. Чому deploy — окремий job?** Щоб права `pages: write` мав лише він, і щоб він запускався тільки після
зеленого build і тільки на push у дозволені гілки.

**27. Навіщо `fetch-depth: 0`?** Плагін дат читає `git log` кожної сторінки; без повної історії всі дати були б
датою останнього коміту.

**28. Що таке docs-or-don't-merge?** Пункт DoD: PR, що змінює публічну поведінку, не зливається без оновлення
docs, OpenAPI і CHANGELOG.

**29. Як працює безперервний аудит актуальності?** Скрипт у CI порівнює `review_date` з поточною датою і
публікує перелік прострочених сторінок у Step Summary.

**30. Чому застарілі сторінки не видаляють?** На них ведуть посилання (звіти, закладки); видалення дало б мертві
посилання. Сторінка стає deprecated з переспрямуванням.

**31. Навіщо теги `docs-vX.Y`?** Щоб відновити документацію, яка відповідала релізу системи, так само як код
релізу: `git checkout docs-v1.0`.

**32. Що strict-збірка НЕ ловить?** Неправдивий зміст при цілому синтаксисі. Це закривають drift-check,
contract-тести, `review_date` і рецензія власника.

---

## 26. Словник термінів

| Термін | Значення |
|---|---|
| Documentation as Code (DaC) | документація в репозиторії з практиками коду: Git, рев'ю, лінтери, CI/CD |
| Single Source of Truth (SSOT) | кожен факт в одному місці; решта представлень генерується з нього |
| Code-first | специфікацію API генерують з коду |
| API-first (design-first, contract-first) | спочатку контракт, код реалізують і перевіряють під нього |
| Static Site Generator (SSG) | інструмент, що робить статичний HTML-сайт з Markdown |
| MkDocs Material | SSG на Python з темою Material |
| strict mode | режим збірки, у якому попередження = помилка, Exit Code 1 |
| orphan page | сторінка, на яку немає шляху в навігації |
| front matter | YAML-метадані на початку Markdown-файла |
| CODEOWNERS | файл GitHub: шлях → обов'язковий рецензент |
| ADR | запис архітектурного рішення: контекст, альтернативи, рішення, наслідки |
| Vault (Obsidian) | каталог Markdown-файлів, відкритий як база знань |
| wikilink | посилання `[[назва]]` у синтаксисі Obsidian |
| OpenAPI Specification (OAS) | стандарт машинно-читного опису HTTP API |
| `$ref` | посилання на компонент контракту замість копії |
| SemVer | MAJOR.MINOR.PATCH: ламаюча / сумісна / косметична зміна |
| Swagger UI | інтерактивна довідка з контракту з кнопкою «Try it out» |
| CORS | правила браузера для запитів з одного origin на інший |
| Spectral | лінтер OpenAPI з власними правилами (ruleset) |
| JSONPath | мова вибору вузлів JSON/YAML (`$.paths[*][*]`) |
| OpenAPI Generator | генератор SDK і заглушок з контракту |
| SDK | клієнтська бібліотека для виклику API |
| контрактний дрейф | розбіжність контракту і фактичної поведінки API |
| contract test | автоматична перевірка відповідності API контракту |
| Schemathesis | property-based contract-тестування з OpenAPI |
| Dredd | contract-тести за прикладами з контракту |
| RFC 9457 Problem Details | стандартний JSON-формат помилки HTTP API |
| GitHub Actions | CI/CD GitHub: workflow → jobs → steps |
| GitHub Pages | хостинг статичних сайтів репозиторію |
| markdownlint | лінтер стилю Markdown |
| Definition of Done (DoD) | умови, без яких задача не завершена |
| docs-or-don't-merge | PR зі зміною публічної поведінки не зливається без документації |
| Keep a Changelog | формат журналу змін за версіями і типами змін |
| deprecated | застарілий, але не видалений; з посиланням на актуальний |
| Javadoc | довідник Java, згенерований з коментарів коду |
