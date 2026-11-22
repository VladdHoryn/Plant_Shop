---
owner: VladdHoryn
status: published
review_date: 2027-02-20
tags:
  - developer
---

# Як долучитися до розробки (CONTRIBUTING)

Короткий шлях нового розробника: [локальне середовище](local-setup.md) → [стандарти коду](coding-standards.md) →
[архітектура](../architecture/sdd.md) свого модуля → цей регламент.

## Робочий процес

1. Гілка від актуальної: `feat/<коротко>`, `fix/<коротко>` (лабораторні — `lab/Lab_N`).
2. Коміти — [Conventional Commits](https://www.conventionalcommits.org/) з областю:
   `feat(order): ...`, `fix(catalog): ...`, `docs(api): ...`, `refactor(audit): ...`, `ci(docs): ...`.
3. Перед комітом: хук `pre-commit` (CC ≤ 10, встановлюється `./scripts/install-hooks.sh`), `./mvnw test`
   для зачеплених модулів.
4. Pull request заповнює шаблон `.github/pull_request_template.md`; зміни в `docs/`, `mkdocs.yml`,
   `README.md` і контракті API потребують схвалення власника (`.github/CODEOWNERS`).
5. Злиття лише при зелених перевірках: `quality-gate`, `code-metrics`, `docs-currency`, `docs`.
6. Перед релізом пройти [чек-лист релізу](../architecture/release-checklist.md).

## Definition of Done: docs-or-don't-merge

Pull request, що змінює **публічну поведінку**, не зливається без документації. Публічна поведінка — це:

- новий, змінений або видалений ендпоінт, параметр, поле чи формат відповіді;
- нова подія Kafka, зміна топіка або типу повідомлення;
- зміна конфігурації, змінних оточення, порядку запуску, розгортання (`docker-compose.yml`, `k8s/`, `helm/`);
- нове архітектурне рішення.

Чек-лист автора PR (той самий у шаблоні PR):

- [ ] **docs/** оновлено за таблицею «Що оновлювати при зміні коду» ([регламент супроводу](maintenance.md#3-що-оновлювати-при-зміні-коду)).
- [ ] **OpenAPI**: змінено `docs/api/openapi.yaml` і `info.version` за SemVer, якщо зачеплено клієнтський
      шлях (каталог, замовлення, трекінг); `./scripts/docs-lint.sh` — Spectral 0 errors.
- [ ] **CHANGELOG.md**: запис у розділі `Unreleased` (Added / Changed / Deprecated / Removed / Fixed).
- [ ] **ADR**: нове рішення — новий файл `docs/architecture/adr/NNNN-*.md`; старий отримує статус `Superseded`,
      його текст не переписується.
- [ ] **Паспорт сторінки**: у front matter змінених сторінок актуальні `owner`, `status`, `review_date`;
      застаріла сторінка не видаляється, а отримує `status: deprecated` і посилання на актуальну.
- [ ] Локально зелені `./scripts/doc-drift-check.sh` і `./scripts/docs-build.sh` (strict).
- [ ] Якщо документацію не зачеплено — пояснення в описі PR, чому.

Що перевіряє автоматика, а що — рецензент:

| Перевірка | Хто | Інструмент |
|---|---|---|
| контракт API | CI | Spectral (`.spectral.yaml`) |
| розмітка Markdown | CI | markdownlint (`.markdownlint-cli2.yaml`) |
| мертві посилання, якорі, сторінки поза навігацією | CI | `mkdocs build --strict` |
| ендпоінти коду ↔ SDD і OpenAPI, порядок запуску ↔ ISD | CI | `./scripts/doc-drift-check.sh` |
| прострочені `review_date` | CI (звіт) | `./scripts/doc-review-audit.sh` |
| зміст, діаграми, правильність опису поведінки | людина | рецензія власника з `CODEOWNERS` |

## Версії документації

Документація версіонується разом із кодом. Мажорна зміна документації (нова структура бази знань, новий MAJOR
контракту API) фіксується анотованим тегом `docs-vMAJOR.MINOR`; знімок відновлюється з тегу так само, як реліз
коду: `git checkout docs-v1.0 && ./scripts/docs-build.sh`. Перелік тегів — у [CHANGELOG](changelog.md).
