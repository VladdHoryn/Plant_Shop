---
owner: VladdHoryn
status: published
review_date: 2027-02-20
tags:
  - api
---

# Довідник API (OpenAPI 3.0, Swagger UI)

Цю сторінку **не пишуть вручну**: Swagger UI нижче рендерить файл [`openapi.yaml`](openapi.yaml) під час
відкриття сторінки (плагін `mkdocs-swagger-ui-tag`). Версія API, маршрути, схеми, приклади та коди помилок
беруться лише з контракту, тому довідка не може розійтися з ним (Single Source of Truth).

«Try it out» надсилає запит на сервер зі списку `servers` контракту — gateway локального стеку
`http://localhost:8080` (`docker compose up -d`); gateway дозволяє CORS для будь-якого origin, тому виклик
працює і з `mkdocs serve`, і з опублікованого сайту.

<swagger-ui src="openapi.yaml"/>
