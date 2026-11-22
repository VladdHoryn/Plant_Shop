---
owner: VladdHoryn
status: published
review_date: 2027-02-20
tags:
  - api
  - developer
---

# Javadoc: довідник з коментарів коду

Варіативний пункт ЛР№11 для API-проєктів: статичний довідник, згенерований інструментом рідної екосистеми з
коментарів коду (для Java — Javadoc). Його не пишуть і не комітять: конвеєр `docs.yml` запускає
`./mvnw javadoc:aggregate` і кладе результат у `site/javadoc/` перед публікацією.

- Опублікований довідник: [Javadoc усіх модулів](https://vladdhoryn.github.io/Plant_Shop/javadoc/)
- Локально: `./scripts/docs-javadoc.sh` → `target/reports/apidocs/index.html`

| Що там є | Звідки |
|---|---|
| пакети `controller`, `service`, `client`, `event`, `dto` кожного сервісу | Javadoc-коментарі класів і методів |
| сигнатури record-класів подій і DTO | компілятор, без ручного опису |

Javadoc описує **внутрішню будову** (рівень розробника), а не публічний контракт: для інтеграції використовуйте
[довідник API](reference.md).
