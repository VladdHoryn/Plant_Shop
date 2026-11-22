---
owner: VladdHoryn
status: published
review_date: 2027-02-20
tags:
  - developer
---

<!-- site:start -->
# Changelog

Зміни, помітні для користувачів API, розробників і супроводу. Формат — [Keep a Changelog](https://keepachangelog.com/uk/1.1.0/);
версії API — SemVer у `info.version` контракту `docs/api/openapi.yaml`; версії документації — теги `docs-vX.Y`.

## [Unreleased]

## [docs-v2.0] — 2026-11-22

### Added

- База знань MkDocs Material за чотирирівневою пірамідою (README → developer → API → architecture), сайт на
  GitHub Pages, конвеєр `.github/workflows/docs.yml` (markdownlint, Spectral, аудит `review_date`, strict-збірка).
- Контракт API `docs/api/openapi.yaml` **1.0.0** (API-first): `GET /api/catalog/plants`,
  `GET /api/catalog/plants/{id}`, `POST /api/orders`, `GET /api/orders`, `GET /api/orders/{id}`,
  `GET /api/orders/{id}/tracking`; спільна схема помилки `Error`.
- Front matter-паспорти (`owner`, `status`, `review_date`, `tags`) у кожному документі; `.github/CODEOWNERS`.
- ADR-0004 (Documentation as Code), ADR-0005 (API-first контракт), ADR-0006 (уніфікація помилок, Proposed).

### Changed

- SDD та ISD переміщено в `docs/architecture/`, регламент супроводу і handover — у `docs/developer/`;
  розділи README про Kubernetes/Helm, приватність, локальний запуск і приклади запитів переміщено на відповідні
  рівні піраміди (README лишився 5-хвилинним входом).
- `doc-drift-check.sh`: перевірка 2 тепер звіряє операції OpenAPI з контролерами (замість переліку в README).

### Fixed

- Розбіжності коду з контрактом, знайдені contract-тестом Schemathesis: `POST /api/orders` з `"items": [null]`
  повертав 500 замість 400 (додано `@NotNull` до елементів `items`); `GET /api/catalog/plants` з `page × size`
  понад 2147483647 повертав 500 замість 400 (перевірка зсуву в `PlantController`).

### Deprecated

- Старі адреси `docs/sdd.md`, `docs/isd.md`, `docs/maintenance.md`, `docs/handover.md` — заглушки зі
  `status: deprecated` і посиланням на нове місце (на них посилаються звіти лабораторних).

## [docs-v1.0] — 2026-11-14

### Added

- Архітектурний паспорт ЛР№10: SDD, ISD, ADR-0001…0003, матриця аудиторій, регламент супроводу, handover,
  перевірка `doc-drift-check.sh` у CI.
