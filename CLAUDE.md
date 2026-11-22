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
  built into minikube with `./scripts/build-images.sh`. `docs/architecture/deployment.md` documents both in detail.

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

## Current lab: Lab 11 (Documentation as Code: MkDocs knowledge base, OpenAPI API-first, docs CI/CD)

Assignment: PDF from the course (theory for the defence in `docs/lab-11.md`). Branch: `lab/Lab_11`. Report:
`docs/lab-11-report.md`, raw outputs in `docs/lab-11/`.

- **Knowledge base:** `mkdocs.yml` (nav = four-level pyramid: README → `docs/developer/` → `docs/api/` →
  `docs/architecture/`), theme override `overrides/main.html`, pinned toolchain `tools/mkdocs/requirements.txt`
  (Docker image locally, pip in CI). Build only with `./scripts/docs-build.sh` (`--strict`); lint with
  `./scripts/docs-lint.sh` (markdownlint + Spectral + review_date audit). Process docs are excluded from the site
  (`exclude_docs`) and from markdownlint (`.markdownlint-cli2.yaml`).
- **Every product page** has front matter `owner`, `status` (draft / in-review / published / deprecated),
  `review_date`, `tags`, and is listed in `nav` (an orphan fails the strict build). Never delete an outdated page:
  `status: deprecated` + `superseded_by` + a `DEPRECATED` banner. Links are relative Markdown links, not wikilinks.
- **API-first contract** `docs/api/openapi.yaml` (OpenAPI 3.0.3, SemVer `info.version`) is the single source for
  the Swagger UI page, the SDK (`./scripts/api-sdk.sh`) and contract tests (`./scripts/api-contract-test.sh`,
  Schemathesis against the running gateway). Spectral threshold: 0 errors.
- CI: `.github/workflows/docs.yml` (build job: markdownlint → Spectral → review_date → `mkdocs build --strict` →
  Javadoc; deploy job → GitHub Pages). Crash test commits: `docs(ci): inject broken reference into strict build, FAIL`
  and `docs(ci): restore strict build pipeline, PASS`; documentation versions are annotated tags `docs-vX.Y`.

## Lab 10 (completed): documentation audit, SDD/ISD/ADR passport, maintenance and handover

Assignment: PDF from the course (summary and theory in `docs/lab-10.md`). Branch: `lab/Lab_10`. Report:
`docs/lab-10-report.md`, raw outputs in `docs/lab-10/`.

- **Architecture passport is product documentation and must follow the code:** `docs/architecture/sdd.md` (C4, components,
  data model, API and Kafka contracts), `docs/architecture/isd.md` (topology, environments, startup order, CI/CD, RTO/RPO),
  `docs/architecture/adr/` (never rewrite an accepted ADR; supersede it with a new number), `README.md`.
- **Code change = documentation change** (`docs/developer/maintenance.md`): a new or changed endpoint, Feign client, event,
  migration, compose/k8s/helm change updates the documents listed there in the same commit/PR.
  `./scripts/doc-drift-check.sh` must print `RESULT: PASS` (also CI job `docs-currency.yml / doc-drift`).
- Currency metrics come only from Git: `./scripts/doc-currency.sh <ref-date>` with the map
  `docs/lab-10/doc-map.txt`; add every new product document to the map.
- Lab reports (`docs/*-report.md`, `docs/lab-*.md`) are process documentation: never update their content; mark
  misleading ones with a `DEPRECATED` banner that links the current document.

## Lab 8 (completed): CK & MOOD audit, God Object decomposition, inheritance → composition

Assignment: `docs/lab-08.md`. Branch: `lab/Lab_8`. Report: `docs/lab-08-report.md`, raw outputs (JSON, CSV,
compiler logs, dependency graphs) in `docs/lab-08/`.

- **Metric sources (check first, never invent numbers):** current SonarQube Community / SonarCloud for Java most
  likely does not compute `coupling_between_objects`, `lcom4` or DIT, and has never computed MOOD (MHF/AHF/COF).
  `class_complexity` may also be deprecated or missing. Workflow:
    1. `GET api/metrics/search?ps=500` → save as `docs/lab-08/sonar-metrics-available.json` as proof of which keys exist.
    2. Still send the assignment's request with the lab keys plus `complexity,cognitive_complexity,ncloc,functions`
       and save the raw response, including any "metric not found" error. This is the API evidence for the report.
    3. Compute the missing CK metrics with the CK tool (`ck` jar → `class.csv` with `wmc, cbo, lcom*, rfc, dit, noc`
       and visibility counts). If the jar is not in `tools/`, ask me before downloading anything. Convert CSV → JSON
       with a script in `scripts/`, so the report still contains a JSON package.
    4. In every metrics table, mark where each number comes from (`Sonar API` / `CK`).
- **API calls (task 1):** the token is read only from env `SONAR_TOKEN`. Never write it into files, scripts or the
  report; show it as `$SONAR_TOKEN`. Call pattern:
  `curl -s -G -u "$SONAR_TOKEN:" "$SONAR_URL/api/measures/component" --data-urlencode "component=<key>" --data-urlencode "metricKeys=<keys>"`.
  Find exact file keys via `api/components/tree?component=<projectKey>&qualifiers=FIL&q=<ClassName>`, because
  multi-module keys contain the module path. Use the same three files (Core Domain, Infrastructure, Utility/DTO)
  as in the Lab 6 report in `docs/`. Make one project-level call for global numbers.
  Scanner: compile first, because `sonar-project.properties` (repo root, no token inside) must set
  `sonar.java.binaries` for every module.
- **MOOD:** compute with `scripts/ck_mood.py` over CK `class.csv`, `src/main` classes only:
  MHF = non-public methods / all methods, AHF = non-public fields / all fields,
  COF = Σ couplings to other project classes / (TC² − TC). State in the report that visibility is treated as
  binary (a simplification of Abreu's definition).
- **Baseline table:** `Файл | Роль | WMC | CBO | LCOM* | RFC | DIT | Джерело`, plus a row with MHF / AHF / COF and
  a short verdict. Use CK `lcom*` (0..1, matches the PASS target ≤ 0.2). Read the assignment's garbled "LCOM1" as
  "LCOM close to 1" and state this interpretation in the report.
- **Coupling matrix (task 2):** SonarQube no longer has a dependency matrix. Build the class graph from
  `jdeps -verbose:class` on compiled classes (or from CK data) as DOT/Mermaid in `docs/lab-08/`. Render it to SVG
  if Graphviz is available, with the God Object highlighted. I take IDE screenshots (MetricsReloaded etc.) myself.
- **God Object selection:** the class with the highest CK `cbo`. If no class has CBO > 15 together with high
  LCOM*, stop and ask which variant to use (artificial aggregation of scattered logic into one class, or an
  Open-Source project). Baseline commit: `chore(audit): capture OO design baseline`.
- **FAIL (task 2, Shotgun Surgery):** make one small change to the signature or behaviour of a class the God
  Object depends on, e.g. a Feign client, mapper or publisher method. Save the compiler output to
  `docs/lab-08/shotgun-surgery.log` and count how many places break inside the God Object. Commit the broken
  state: `arch(audit): capture god object coupling failure, FAIL`. The pre-commit hook only checks CC, so this
  commit is allowed; a red build/CI on it is expected.
- **PASS (task 2):** split into 3–4 classes with one business responsibility each. Collaborators are interfaces
  (contracts/ports) injected through the constructor: `final` fields, no field `@Autowired`, no `new` of
  collaborators. New concrete classes never call each other directly. Moving code (Extract Method/Class) without
  interfaces does not count. Unit tests use fakes or Mockito against the interfaces; all tests green. Re-scan and
  re-run CK: every new class must have CBO ≤ 8 and LCOM* ≤ 0.2. Provide a before/after CK table and a Mermaid
  class diagram (blueprint). Commit: `refactor(audit): eliminate god object via interface decomposition, PASS`.
- **Task 3 baseline (Lorenz–Kidd):** find an `extends` chain of our own classes. Ignore JDK/framework bases
  (`RuntimeException`, Spring base classes, JPA repository interfaces). Report DIT, PIM (public methods including
  inherited ones), added methods and overridden methods. CK counts `Object`, so a class without `extends` has
  DIT = 1; say so in the report. If no chain with DIT ≥ 3 exists, create an artificial 3-level service hierarchy
  in an existing module where the child overrides about half of the parent's methods. Commit it as
  `chore(audit): add inheritance hierarchy baseline`.
- **FAIL (task 3):** change the signature or logic of a method in the top base class. Capture either the cascade
  of compile errors in the subclasses (`docs/lab-08/inheritance-crash.log`) or a failing plain JUnit 5 test for
  the behaviour change. Commit: `arch(audit): capture inheritance fragility crash, FAIL`.
- **PASS (task 3):** remove `extends`. The former parent becomes a `private final` field injected via the
  constructor, and the child only delegates. Prefer plain delegation without a shared interface, so that
  "overridden = 0" holds literally. If an interface is introduced, explain in the report that implementing an
  abstract contract is not overriding parent logic. `equals/hashCode/toString` are not counted. Re-run CK and
  prove DIT = 1 and overridden = 0. Include before/after code listings. Commit:
  `refactor(audit): replace inheritance with composition pattern, PASS`.
- **Git history:** after each task, save `git log --oneline --graph` to `docs/lab-08/`. I take the screenshots
  myself.
- Report headings follow the 9 items of "Вимоги до оформлення звіту" in the assignment.
- The "Лог валідації ШІ-Техліда" section stays a TODO until I provide its instructions.
