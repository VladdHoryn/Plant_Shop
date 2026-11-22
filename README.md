---
owner: VladdHoryn
status: published
review_date: 2027-02-12
tags:
  - overview
---

<!-- site:start -->
# Plant Shop — e-commerce microservices for selling plants

Java 21 · Spring Boot 3.5 · Spring Cloud 2025.0 · PostgreSQL + Flyway · Kafka

Documentation site (built from this repository): <https://vladdhoryn.github.io/Plant_Shop/>

## What it does and for whom

Plant Shop is an online shop for house plants, built as a university course project.

- **Customers** browse the catalog (filter by category, care level and price), place orders and follow their
  status. An order is accepted at once (`PENDING`); stock is reserved and the payment is simulated in the
  background, then the order becomes `CONFIRMED` or `CANCELLED`.
- **Shop staff** manage plants, categories and stock levels.
- **Privacy officer and marketing** handle personal data on request: export, erasure, consents; marketing e-mails
  are sent only with the customer's consent.

To follow an order, one request is enough: `GET /api/orders/{id}/tracking` returns the order status, the payment
status, the failure reason and the next step in plain words.

## Quick start (about 5 minutes)

Prerequisites: Git and Docker Desktop (running). No local JDK is needed.

```bash
git clone https://github.com/VladdHoryn/Plant_Shop.git && cd Plant_Shop
docker compose up -d --build
curl http://localhost:8080/api/catalog/plants
```

The first build takes a few minutes; the services are ready about a minute after the containers start
(`docker compose ps` shows them as healthy). Then place an order: [API guide](docs/api/index.md#спробувати-через-gateway).

## Documentation

The knowledge base is a four-level pyramid; each level answers the question of its own audience. Every page has a
front matter passport (`owner`, `status`, `review_date`); the site shows it at the top of the page.

| Level | Question | Where | Audience |
|---|---|---|---|
| 1 | What is it and how do I start it? | this README | everyone, business, newcomers |
| 2 | How do I join the development? | [`docs/developer/`](docs/developer/CONTRIBUTING.md): contributing rules, local setup, code standards, maintenance regime, handover, changelog | developers, maintainers |
| 3 | How do I integrate? | [`docs/api/`](docs/api/index.md): OpenAPI contract [`openapi.yaml`](docs/api/openapi.yaml) rendered as Swagger UI, privacy API, Javadoc | integrators, QA, frontend |
| 4 | Why is it built this way? | [`docs/architecture/`](docs/architecture/sdd.md): SDD, ISD, deployment runbook, [ADR](docs/architecture/adr/README.md) | architects, DevOps |

Lab reports (`docs/lab-*.md`, `docs/*-report.md`, `docs/reports/`) are process documentation: historical records
of a moment in time. They stay in Git, are not updated and are not part of the site.

## Definition of Done: documentation checks

A pull request is merged only when every check is green (details: [CONTRIBUTING](docs/developer/CONTRIBUTING.md#definition-of-done-docs-or-dont-merge)).

| What is checked | Tool | Where it runs | Fails when |
|---|---|---|---|
| API contract | Spectral (`.spectral.yaml`) | CI `docs / lint`, `./scripts/docs-lint.sh` | an operation has no description, a schema has no example, an error response is not `Error` |
| Markup | markdownlint (`.markdownlint-cli2.yaml`) | CI `docs / lint`, `./scripts/docs-lint.sh` | Markdown style rules are broken |
| Links and navigation | `mkdocs build --strict` | CI `docs / build`, `./scripts/docs-build.sh` | a dead link or anchor, a page outside `nav`, a broken page |
| Currency | `review_date` in front matter | CI `docs / lint` → `./scripts/doc-review-audit.sh` | report: overdue pages are listed in the job summary |
| Code ↔ documentation | `./scripts/doc-drift-check.sh` | CI `docs-currency / doc-drift` | an endpoint is not in the SDD, an OpenAPI operation does not exist in code, the startup order differs from the ISD |
| Ownership | `.github/CODEOWNERS` | GitHub review | a change in `docs/` has no approval of the document owner |
