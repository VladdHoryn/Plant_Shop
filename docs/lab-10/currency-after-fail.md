Documentation currency audit: reference date 2026-11-14, revision aef1513
Thresholds: age > 90 d SUSPICIOUS, > 180 d STALE; lag > 20 code commits DRIFT (lag > 0 is reported as 'behind')

| Document | Kind | Last updated | Age, d | Doc commits | Code commits | Lag (code commits after last doc commit) | Inbound refs | Changes in 30 d | Signal |
|---|---|---|---|---|---|---|---|---|---|
| `README.md` | product | 2026-11-14 | 0 | 8 | 17 | 1 | 10 | 7 | OK, behind |
| `CLAUDE.md` | product | 2026-11-14 | 0 | 3 | 27 | 1 | 6 | 3 | OK, behind |
| `docker-compose.yml` | product | 2026-11-14 | 0 | 3 | 4 | 0 | 12 | 2 | OK |
| `Dockerfile` | product | 2026-10-25 | 20 | 3 | 5 | 2 | 14 | 2 | OK, behind |
| `.github/workflows/quality-gate.yml` | product | 2026-10-25 | 20 | 1 | 3 | 0 | 6 | 1 | OK |
| `.github/workflows/code-metrics.yml` | product | 2026-11-01 | 13 | 1 | 2 | 0 | 2 | 1 | OK |
| `helm/plantshop/templates/NOTES.txt` | product | 2026-10-15 | 30 | 1 | 1 | 0 | 3 | 1 | OK |
| `user-service/privacy-demo.http` | product | 2026-10-25 | 20 | 1 | 6 | 5 | 3 | 1 | OK, behind |
| `sonar-project.properties` | product | 2026-11-07 | 7 | 1 | 5 | 0 | 5 | 1 | OK |
| `scripts/git-hooks/pre-commit` | product | 2026-10-25 | 20 | 2 | 4 | 0 | 12 | 2 | OK |
| `docs/sdd.md` | product | 2026-11-14 | 0 | 1 | 16 | 1 | 9 | 1 | OK, behind |
| `docs/isd.md` | product | 2026-11-14 | 0 | 2 | 9 | 1 | 10 | 2 | OK, behind |
| `docs/architecture/adr/0001-database-per-service-postgresql-flyway.md` | product | 2026-11-14 | 0 | 1 | 4 | 1 | 4 | 1 | OK, behind |
| `docs/architecture/adr/0002-kafka-choreography-saga.md` | product | 2026-11-14 | 0 | 1 | 2 | 0 | 3 | 1 | OK |
| `docs/maintenance.md` | product | 2026-11-14 | 0 | 1 | n/a | n/a | 7 | 1 | OK |
| `docs/privacy-report.md` | process | 2026-10-25 | 20 | 1 | n/a | n/a | 7 | 1 | OK |
| `docs/privacy-demo.md` | process | 2026-10-25 | 20 | 1 | n/a | n/a | 4 | 1 | OK |
| `docs/metrics-report.md` | process | 2026-10-25 | 20 | 1 | n/a | n/a | 6 | 1 | OK |
| `docs/gqm-metrics.md` | process | 2026-10-25 | 20 | 1 | n/a | n/a | 3 | 1 | OK |
| `docs/goodhart-demo.md` | process | 2026-10-25 | 20 | 1 | n/a | n/a | 3 | 1 | OK |
| `docs/reliability-report.md` | process | 2026-10-25 | 20 | 1 | n/a | n/a | 4 | 1 | OK |
| `docs/stability-report.md` | process | 2026-10-25 | 20 | 1 | n/a | n/a | 4 | 1 | OK |
| `docs/complexity-report.md` | process | 2026-10-25 | 20 | 1 | n/a | n/a | 4 | 1 | OK |
| `docs/k8s-report.md` | process | 2026-11-14 | 0 | 2 | n/a | n/a | 4 | 2 | OK |
| `docs/helm-report.md` | process | 2026-11-14 | 0 | 2 | n/a | n/a | 4 | 2 | OK |
| `docs/lab-07.md` | process | 2026-11-07 | 7 | 1 | n/a | n/a | 1 | 1 | OK |
| `docs/reports/lab-07-report.md` | process | 2026-11-07 | 7 | 1 | n/a | n/a | 1 | 1 | OK |
| `docs/lab-08.md` | process | 2026-11-07 | 7 | 1 | n/a | n/a | 1 | 1 | OK |
| `docs/lab-08-report.md` | process | 2026-11-11 | 3 | 2 | n/a | n/a | 2 | 2 | OK |
| `docs/lab-09.md` | process | 2026-11-14 | 0 | 1 | n/a | n/a | 1 | 1 | OK |
| `docs/lab-9.md` | process | 2026-11-14 | 0 | 2 | n/a | n/a | 1 | 2 | OK |
| `FINANCIAL_LEDGER.md` | process | 2026-11-14 | 0 | 1 | n/a | n/a | 7 | 1 | OK |
| `.github/workflows/docs-currency.yml` | product | 2026-11-14 | 0 | 1 | 2 | 0 | 3 | 1 | OK |
