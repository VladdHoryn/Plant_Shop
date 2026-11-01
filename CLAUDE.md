# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Plant Shop: a Spring Boot microservices e-commerce app for selling plants (Java 21, Spring Boot 3.5, Spring Cloud
2025.0, PostgreSQL + Flyway, Kafka). It is a university course project: each lab is developed on its own branch
(`lab/Lab_N`, `k8s-lab`, `helm-lab`) and its report goes into `docs/` (e.g. `docs/lab-07.md` holds the assignment
text, `docs/complexity-report.md` the Lab 6 report). Commit messages use Conventional Commits with a scope
(`feat(audit): ...`, `refactor(audit): ...`, `docs(audit): ...`); some labs ask for specific FAIL/PASS commits.

## Build and test

On this Windows machine `JAVA_HOME` is not set globally; in Git Bash prefix Maven calls with
`export JAVA_HOME="/c/Users/user/.jdks/corretto-21.0.12.1"; export PATH="$JAVA_HOME/bin:$PATH";`.
Docker Desktop must be running for integration tests, the complexity hook and the analysis scripts.

```bash
./mvnw clean package                                     # build all modules (mvnw.cmd on cmd/PowerShell)
./mvnw test                                              # all tests; *IntegrationTest classes need Docker (Testcontainers)
./mvnw test -pl payment-service -am -Dtest=PaymentServiceTest -Dsurefire.failIfNoSpecifiedTests=false
./mvnw test -pl user-service -am -Dtest='ErasureIntegrationTest#repeatingTheRequestChangesNothing' -Dsurefire.failIfNoSpecifiedTests=false
```

`-am` is needed because every service depends on the `secure-logging` module.

### Quality gates (mirror `.github/workflows/quality-gate.yml`)

```bash
# Coverage (JaCoCo) + mutation score (PIT); integration tests are excluded from PIT
./mvnw -B -Pquality verify -pl secure-logging,catalog-service,inventory-service,order-service,payment-service -am \
  -Dtest='!*IntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false
COVERAGE_MIN=80 MUTATION_MIN=65 ./scripts/quality-gate.sh secure-logging   # enforced; other modules report-only

./scripts/cc-check.sh                     # Lizard in a pinned container: every method CC <= 10 (CI also: <= 60 NLOC)
./scripts/cc-check.sh path/to/File.java   # scan specific files
./scripts/install-hooks.sh                # installs scripts/git-hooks/pre-commit (blocks commits with CC > 10)
```

The pre-commit hook scans the *staged* content and fails closed if Docker is not running. When a method exceeds
CC 10, the established fix in this repo is splitting the logic or replacing condition chains with a lookup table
(see `PaymentService` / `WateringPlanner`). Analysis tools are pinned (`tools/lizard`, `tools/halstead`, plugin
versions in the root `pom.xml`) so metrics never change because a tool moved; keep them pinned.

## Running

- Whole stack: `docker compose up -d --build` (one root `Dockerfile` builds any module via `--build-arg SERVICE=<module>`).
- From the IDE: `docker compose up -d catalog-db inventory-db order-db payment-db user-db kafka kafka-ui`, then start
  config-service → discovery-service → business services → api-gateway (jars in `<module>/target/`).
- Kubernetes manifests in `k8s/`, Helm chart in `helm/plantshop` (`helm lint helm/plantshop --strict`); images are
  built into minikube with `./scripts/build-images.sh`. The README documents both in detail.

## Architecture

| Module | Port | Role |
|---|---|---|
| config-service | 8888 | Spring Cloud Config server, `native` profile |
| discovery-service | 8761 | Eureka |
| api-gateway | 8080 | Spring Cloud Gateway (WebFlux), routes `/api/<service>/**` via `lb://` |
| catalog / inventory / order / payment / user-service | 8081–8085 | business services |
| secure-logging | – | shared library, auto-configured in every service |

**Configuration lives in config-service, not in the services.** Each service's own `application.yml` only sets its
name and `spring.config.import: configserver:...`. The real config (ports, datasource, Flyway, Kafka serializers,
type mappings) is in `config-service/src/main/resources/configurations/<service>.yml`, with shared settings in
`configurations/application.yml`. Environment overrides: `CONFIG_SERVER_URL`, `EUREKA_URL`,
`KAFKA_BOOTSTRAP_SERVERS`, `DB_HOST`, `DB_PORT`, `DB_USERNAME`, `DB_PASSWORD`.

**Integration tests reuse those same files**: `<service>/src/test/resources/application.yml` disables config
server, Eureka and Kafka listeners and imports `file:../config-service/src/main/resources/configurations/*.yml`;
PostgreSQL comes from Testcontainers (`@ServiceConnection`). A config change in config-service therefore affects tests.

**Database per service + Flyway.** Each business service has its own PostgreSQL container/database; Flyway creates
the service's schema (`catalog`, `inventory`, `orders`, `payments`, `users`) and Hibernate runs with
`ddl-auto: validate`, so migrations in `<service>/src/main/resources/db/migration` are the only source of the schema.
Add a new `V<n>__description.sql`; never edit an applied migration and never use docker init SQL scripts.

**Order saga over Kafka (choreography):** order-service validates price/stock synchronously via Feign
(`client/CatalogClient`, `InventoryClient`), saves the order `PENDING` and emits `order-created` → inventory reserves
(`inventory-reserved` / `inventory-failed`) → payment charges (`payment-completed` / `payment-failed`, declines
amounts above `payment.max-amount`) → order becomes `CONFIRMED`/`CANCELLED` and inventory commits or releases the
reservation. catalog's `plant-created` makes inventory create a stock row. Conventions:
- Events are published from `@TransactionalEventListener` publishers (`event/*EventPublisher`), i.e. only after the
  DB transaction commits; listeners must be idempotent (redeliveries are ignored).
- There is no shared events module: each service keeps its own copy of the event records it uses in its `event`
  package, plus a `KafkaTopics` constants class. JSON type aliases (`spring.json.type.mapping`) in the
  config-service YAML map them across services, so a new or renamed event must be updated in every service copy
  **and** in the producer/consumer type mappings.

**Service layout:** `controller` → `service` → `repository` (Spring Data JPA) with `entity`, `dto` (records),
`mapper` (hand-written `final` classes with static methods such as `toResponse`, no MapStruct), `exception`
(`GlobalExceptionHandler` as `@RestControllerAdvice`), `event`. Lombok is used throughout.

**user-service / privacy:** HTTP Basic auth (email as username) with roles CUSTOMER, PRIVACY_OFFICER, MARKETING;
data export, erasure/anonymization (calls order-service `/internal/**` with the shared `INTERNAL_API_TOKEN`; those
endpoints are not routed by the gateway) and consent gates. Accessing another user's id returns the same 404 as a
missing id. Details: `docs/privacy-report.md`.

**secure-logging:** `SecureLoggingAutoConfiguration` (registered via `AutoConfiguration.imports`) plus
`logback-spring.xml` with `MaskingEncoder`/`PiiMasker` that mask personal data and redact secrets in every log line,
and `CorrelationIdFilter`. It is a "critical module" in the quality gate; keep its coverage and mutation score up.

## Repository conventions

- `.gitattributes` / `.editorconfig` force LF for sources, configs and scripts (metrics must not differ between
  Windows and Linux checkouts); Java uses 4-space indent, max 120 columns; YAML 2 spaces.
- Scripts in `scripts/` are bash (run them from Git Bash); several write their transcript into `docs/`
  (e.g. `privacy-demo.sh`, `k8s-demo.sh`, `helm-demo.sh`, `hook-demo.sh`).
- Python and jq are not installed locally; analysis tools run in Docker images under `tools/`. Do not `pip install`
  anything on the host: extend or reuse a `tools/` image instead. On GitHub Actions runners Python is available.

## Lab workflow rules (apply to every lab)

- Before starting a lab, read its assignment in `docs/lab-NN.md` completely and work on branch `lab/Lab_N`
  (create it from the latest lab branch if it does not exist; ask me if unsure which base to use).
- Never run `git push`, `--force`, `rebase`, `reset --hard` or `commit --amend` without my explicit permission.
  Lab commits are graded as history, so FAIL/PASS commits must stay separate, ordered and unsquashed.
- When the assignment gives an exact commit message, use it verbatim. Otherwise use Conventional Commits with
  the lab's scope.
- Reports are written in **Ukrainian** (code, identifiers and commit messages stay in English). Markdown must work
  in Obsidian and Notion: GitHub-style tables, fenced code blocks with language tags, no HTML.
- Where the assignment needs a screenshot, put a placeholder `> 📸 SCREENSHOT: <what exactly to capture>` and
  keep a list of all placeholders at the end of the report.
- Save raw tool output (test runs, Lizard/Halstead output, git logs) as text files in `docs/lab-NN/` and quote them
  in the report, so every number in the report is traceable to a file.
- Show every formula with step-by-step substitution. Never adjust a classification or threshold to hit a target
  value; if a target is unreachable honestly, stop and tell me.
- At each "stop and ask" point in my prompt, actually stop and wait for my answer.

## Current lab: Lab 7 (Chapin audit, immutability, CI metrics, Hotspots)

Assignment: `docs/lab-07.md`. Branch: `lab/Lab_7`. Report: `docs/lab-07-report.md`, raw outputs in `docs/lab-07/`.

- **Method selection (task 1):** prefer integration-contour code (payment-service charging logic, Feign clients
  in order-service, Kafka listeners/publishers, mappers) or the linear methods produced by the Lab 6 lookup-table
  refactoring (`PaymentService`, `WateringPlanner`, see `docs/complexity-report.md`). Verify CC <= 5 with
  `./scripts/cc-check.sh <file>` and quote the Lizard line.
- **Chapin classification:** count every variable in the method, including parameters, locals, loop/lambda
  variables and fields the method reads or writes. Priority when a variable has several roles: C > M > P > T.
  Formula: `C_ch = 1·P + 2·M + 3·C + 0.5·T`. Report table columns:
  `Змінна | Тип даних | Клас (P/M/C/T) | Обґрунтування ролі | Вага`.
- **FAIL commit (task 2):** the mutation bug is demonstrated with a plain JUnit 5 unit test (no Spring context,
  no Testcontainers, so it runs without Docker). The test must fail for a business reason (wrong amount, lost
  status), not a compile error. The pre-commit hook only checks CC, so a failing test can be committed; CI will be
  red on this commit, which is expected. Commit: `feat(audit): simulate data mutation issue, FAIL`.
- **PASS refactoring (task 2):** all parameters and locals `final`; "changes" create new objects; related values
  grouped into immutable Value Objects as Java `record`s with validation in the compact constructor and
  `withX(...)` methods returning new instances (no Lombok `@Setter`/`@Data` on VOs, defensive copies for
  collections). Validation/decision logic moves into the VO so the method loses C variables. Extract Method alone
  does not count. All tests green. Commit:
  `refactor(audit): reduce Chapin complexity using immutable Value Objects, PASS`.
  Target: `C_ch_after <= C_ch_before / 2` and `C_ch_after <= 6`.
- **CI (task 3):** add a separate workflow `.github/workflows/code-metrics.yml` (do not break
  `quality-gate.yml`): on push and pull_request, install Lizard with the same version as pinned in `tools/lizard`,
  scan all modules' `src/main/java`, export CSV, convert to JSON with `scripts/lizard_to_json.py`, upload both via
  `actions/upload-artifact@v4`. Halstead Effort from `tools/halstead` may be added as an extra column.
  Validate the YAML locally (e.g. via a Docker image with `actionlint`); I push and take the screenshots myself.
- **Churn (task 3):** use a 90-day window and `grep -E` (not the deprecated `egrep`):
  `git log --since="90 days ago" --format=format: --name-only | grep -Ev '^$' | sort | uniq -c | sort -nr`
  Count only `*.java` files under `src/main` for the matrix. If history is too short, stop and ask which
  variant (1/2/3 from the assignment) to use.
- **Hotspot matrix:** 5+ files, columns `Файл | Max CC / Sum CC (Effort) | Комітів за 90 днів | Категорія`.
  High complexity = any method with CC > 10 (or file Sum CC above the project median, state which); high churn =
  above the project median commit count (state the number). End with a prioritized refactoring plan for the next
  sprint.
- The "Лог валідації ШІ-Техліда" section stays a TODO until I provide its instructions.
