Documentation currency audit: reference date 2026-11-14, revision 69df71c
Thresholds: age > 90 d SUSPICIOUS, > 180 d STALE; lag > 20 code commits DRIFT (lag > 0 is reported as 'behind')

| Document | Kind | Last updated | Age, d | Doc commits | Code commits | Lag (code commits after last doc commit) | Inbound refs | Changes in 30 d | Signal |
|---|---|---|---|---|---|---|---|---|---|
| `README.md` | product | 2026-10-25 | 20 | 6 | 15 | 7 | 4 | 5 | OK, behind |
| `CLAUDE.md` | product | 2026-11-07 | 7 | 2 | 23 | 8 | 4 | 2 | OK, behind |
| `docker-compose.yml` | product | 2026-10-25 | 20 | 2 | 4 | 1 | 6 | 1 | OK, behind |
| `Dockerfile` | product | 2026-10-25 | 20 | 3 | 5 | 2 | 12 | 2 | OK, behind |
| `.github/workflows/quality-gate.yml` | product | 2026-10-25 | 20 | 1 | 3 | 0 | 5 | 1 | OK |
| `.github/workflows/code-metrics.yml` | product | 2026-11-01 | 13 | 1 | 2 | 0 | 1 | 1 | OK |
| `helm/plantshop/templates/NOTES.txt` | product | 2026-10-15 | 30 | 1 | 1 | 0 | 3 | 1 | OK |
| `user-service/privacy-demo.http` | product | 2026-10-25 | 20 | 1 | 5 | 4 | 3 | 1 | OK, behind |
| `sonar-project.properties` | product | 2026-11-07 | 7 | 1 | 5 | 0 | 5 | 1 | OK |
| `scripts/git-hooks/pre-commit` | product | 2026-10-25 | 20 | 2 | 4 | 0 | 11 | 2 | OK |
| `docs/sdd.md` | product | not in Git | - | 0 | - | - | - | - | MISSING |
| `docs/isd.md` | product | not in Git | - | 0 | - | - | - | - | MISSING |
| `docs/architecture/adr/0001-database-per-service-postgresql-flyway.md` | product | not in Git | - | 0 | - | - | - | - | MISSING |
| `docs/architecture/adr/0002-kafka-choreography-saga.md` | product | not in Git | - | 0 | - | - | - | - | MISSING |
| `docs/maintenance.md` | product | not in Git | - | 0 | - | - | - | - | MISSING |
| `docs/privacy-report.md` | process | 2026-10-25 | 20 | 1 | n/a | n/a | 6 | 1 | OK |
| `docs/privacy-demo.md` | process | 2026-10-25 | 20 | 1 | n/a | n/a | 4 | 1 | OK |
| `docs/metrics-report.md` | process | 2026-10-25 | 20 | 1 | n/a | n/a | 5 | 1 | OK |
| `docs/gqm-metrics.md` | process | 2026-10-25 | 20 | 1 | n/a | n/a | 3 | 1 | OK |
| `docs/goodhart-demo.md` | process | 2026-10-25 | 20 | 1 | n/a | n/a | 3 | 1 | OK |
| `docs/reliability-report.md` | process | 2026-10-25 | 20 | 1 | n/a | n/a | 4 | 1 | OK |
| `docs/stability-report.md` | process | 2026-10-25 | 20 | 1 | n/a | n/a | 4 | 1 | OK |
| `docs/complexity-report.md` | process | 2026-10-25 | 20 | 1 | n/a | n/a | 4 | 1 | OK |
| `docs/k8s-report.md` | process | 2026-10-15 | 30 | 1 | n/a | n/a | 3 | 1 | OK |
| `docs/helm-report.md` | process | 2026-10-15 | 30 | 1 | n/a | n/a | 3 | 1 | OK |
| `docs/lab-07.md` | process | 2026-11-07 | 7 | 1 | n/a | n/a | 1 | 1 | OK |
| `docs/reports/lab-07-report.md` | process | 2026-11-07 | 7 | 1 | n/a | n/a | 1 | 1 | OK |
| `docs/lab-08.md` | process | 2026-11-07 | 7 | 1 | n/a | n/a | 1 | 1 | OK |
| `docs/lab-08-report.md` | process | 2026-11-11 | 3 | 2 | n/a | n/a | 1 | 2 | OK |
| `docs/lab-09.md` | process | 2026-11-14 | 0 | 1 | n/a | n/a | 1 | 1 | OK |
| `docs/lab-9.md` | process | 2026-11-14 | 0 | 2 | n/a | n/a | 0 | 2 | OK, no inbound refs |
| `FINANCIAL_LEDGER.md` | process | 2026-11-14 | 0 | 1 | n/a | n/a | 5 | 1 | OK |
