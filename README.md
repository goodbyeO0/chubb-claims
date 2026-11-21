# Chubb APAC claims workbench

## Run the complete application

From the repository root (`readme_main_impl/`), with Docker Engine and Compose running:

```sh
API_PORT=18080 docker compose -p chubb-review up --build -d
curl --retry 60 --retry-all-errors --retry-delay 2 --retry-max-time 180 --max-time 5 -fsS http://localhost:18080/actuator/health
```

Open **http://localhost:18080**. Angular and the API are served from the same Spring Boot container. The health response is exactly `{"status":"UP"}`. Omit `API_PORT=18080` to use port 8080, provided it is free. On Linux Docker Desktop, use `docker --context desktop-linux compose` if your default context is different. The first build downloads dependencies and can take several minutes.

| Local demo username | Role | Password |
|---|---|---|
| aina, daniel | Claimant | local-demo-only |
| farah, arjun | Officer | local-demo-only |

These fictional identities are seeded only by the `local` Spring profile used in Compose. Database and Kafka data persist in named volumes. An API restart ends in-memory sessions; sign in again. `docker compose -p chubb-review down` stops the project and keeps its data. Do not add `-v` when preserving cases.

**Walkthrough:** Aina reports an MY motor incident. Farah picks it up and asks for the workshop estimate. Aina replies “Initial estimate is MYR 5,000.” Farah records reserve `5000.00`, checks Reports after processing, then confirms settlement `4200.00`. Aina sees the decision, without staff assessment history. Daniel and Arjun can independently record an SG property incident, clarify a duplicate report and reject it with a case-specific reason.

## Test commands

Host tests require Java 21, Node.js 24/npm and Python 3. Docker must be available to Testcontainers; Maven 3.9.11 is provided by `backend/mvnw`. Docker-only application startup does not require host Java or Node.

```sh
# From the repository root:
python3 -m venv .venv
.venv/bin/pip install -r verification/requirements.txt
.venv/bin/python verification/check_contract.py
(cd backend && ./mvnw test)
(cd frontend && npm ci && npx playwright install chromium)
(cd frontend && npm run build && npm run test:fixture)

# Against the running Compose app (creates fictional test claims):
.venv/bin/python verification/api_smoke.py --base http://localhost:18080 --through decision
.venv/bin/python verification/backend_gate.py --base http://localhost:18080
(cd frontend && E2E_BASE_URL=http://localhost:18080 npm run test:e2e)
docker compose -p chubb-review logs --since=10m api
```

Set `JAVA_HOME` to your Java 21 installation if it is not the default. On Linux Docker Desktop, if Testcontainers cannot discover Docker, set `DOCKER_HOST=unix://$HOME/.docker/desktop/docker.sock`. The fixture suite owns port 14200; stop any manual fixture server first. Playwright saves failure screenshots/traces under `frontend/test-results/` and an HTML report under `frontend/playwright-report/`.

Actual results, failed attempts, decisions and verification revisions are in [AI-JOURNAL.md](AI-JOURNAL.md). The [acceptance matrix](docs/06-test-cases.md) defines the requirements. Browser integration tests create fresh claims and compare report deltas with a baseline; avoid editing other claims concurrently during that suite.

## Development and fixture preview

```sh
# Real API development: start Compose above, then in another terminal:
(cd frontend && npm start -- --host 127.0.0.1)
# Open http://127.0.0.1:14201; the dev proxy targets localhost:18080.

# Independent fixture preview, without a backend:
(cd frontend && npm run start:fixture -- --host 127.0.0.1)
# Open http://127.0.0.1:14200.
```

Production uses the HTTP adapter. Only the explicit `fixture` build configuration selects sample data; an API error never activates fixtures. `npm run build:fixture` produces the fixture bundle. Fixture scenarios append `?scenario=empty`, `loading`, `error` or `permission` to a list URL, or `?scenario=conflict` to a case URL. Keep the same browser tab when switching fixture personas; records persist in that origin's local storage. Clear its site data to reset sample records. Real API records are independent of fixture records.

## Architecture and scope

Spring Boot 4.0 / Java 21, Angular 21 / Tailwind 4, PostgreSQL 17 and Kafka 4.2. The supplied OpenAPI spec is the contract and the original Flyway migration is preserved. Cookie sessions and native Angular XSRF headers protect commands. Explicit role-specific views keep staff reserves and audit identity out of claimant responses. Optimistic versions prevent stale updates; a 409 refreshes the detail and requires a new confirmation.

Financial commands commit the claim, audit event and full exposure outbox payload in one transaction. HTTP success does not wait for Kafka. Publication retries pending records; the consumer applies only newer financial versions. Workload is live SQL; exposure is eventually consistent with an explicit last-update time. Currency totals stay separate; NULL means awaiting estimate, and settlement releases the full reserve. See [reporting operations](docs/OPERATIONS.md) and [UI decisions](docs/UI-DESIGN.md).

This delivery is tested locally with Compose, not deployed. It has no external policy verification, payments, documents or notifications. Lists are unpaginated for a bounded assessment dataset, sessions are in memory and the publisher is single-instance. The deployment profile enables Secure cookies and JSON stdout; an actual deployment additionally requires HTTPS, environment secrets and authorized identity provisioning without local demo seeds. [The submission checklist below](#before-sending-the-link) separates technical verification from the candidate's final review and repository sharing.

Implementation guide for Codex and Izhan. Build a locally runnable application for reporting motor and property incidents, assessing claims and tracking team workload and outstanding reserves. The reviewer should be able to sign in as a claimant, respond to an officer’s question and see the final decision. Staff should be able to explain each decision from the timeline and trace a delayed dashboard update using one event ID.

**Start with the [OpenAPI contract](api/openapi.yaml).** It defines the HTTP operations, actors, payloads, examples and error codes. The [database migration](backend/src/main/resources/db/migration/V1__claims.sql) defines storage. The [Kafka contract](api/exposure-event.schema.json) defines the reporting message. An HTTP response is a role-specific view of the data. Returning a database entity directly would expose internal fields.

The original starting pack supplied the contracts, migration and acceptance guides. The application, Maven wrapper, Compose file, backend tests and Angular workspace are now included. The stage guides remain the acceptance criteria; the journal records actual verification.

## Folder structure and working directory

In Izhan’s VS Code layout, `CHUBB/` is the folder opened in the editor. **Use `CHUBB/readme_main_impl/` as the application root.** It already contains the API contract and the first database migration. Keep the implementation beside those files so commands and relative links resolve from one place.

| Path relative to the application root | When | What goes here |
|---|---|---|
| `README.md`, `AI-JOURNAL.md` | Already present | Start here. Record decisions and actual test runs in the journal as you work. |
| `api/` | Already present | HTTP contract in `openapi.yaml`, Kafka event schema and UI fixtures. |
| `docs/`, `verification/` | Already present | Implementation stages, acceptance cases and runnable contract, SQL and HTTP checks. |
| `backend/` | Migration supplied, app added in stage 1 | Scaffold Spring Boot in this existing directory. Put `pom.xml` and Maven wrappers directly here. Use `src/main/java/` for code and `src/test/java/` for tests. Preserve `src/main/resources/db/migration/V1__claims.sql`. |
| `compose.yaml` | Create in stage 1 | Local services. Run Docker Compose from the application root. |
| `frontend/` | Create in stage 7 | Angular workspace beside `backend/`. Put `angular.json` and `package.json` directly here. Run npm commands here. |

**First terminal check.** From `CHUBB/`, run `cd readme_main_impl`. If the terminal already shows `readme_main_impl`, start there. Run `git status` and, if Git recognizes a repository, `git rev-parse --show-toplevel` to find its root. Keep one Git history. The stage guides use “root” for the application directory unless they explicitly discuss Git.

**Reviewer entry point.** If Git reports `CHUBB/` as its root, put the launch command and a link to `readme_main_impl/README.md` in the outer README before submission. Show `cd readme_main_impl` as the first setup command for that checkout. If Git reports `readme_main_impl/` as its root, this README already opens on the repository landing page.

## Start Codex with this instruction

> Work in readme_main_impl/. Read README.md and api/openapi.yaml, then start with the first unfinished stage. Use the API spec as the source of truth. Preserve the existing database migration. Build and test the Spring Boot backend first. Once its tests pass, build the Angular UI with fixture data, verify it, then connect it to the real API. At each stage, review the diff, run the listed tests, record actual results and decisions in AI-JOURNAL.md, and commit each meaningful change. Finish with the submission checklist and verified commands to run and test the app.

## Commit history and submission checklist

Chubb asks for a Git repository with meaningful development history, an application that starts locally, and a running AI journal committed alongside the code. Supporting documentation is at the candidate’s discretion. Submit the repository link by **22 November 2026** (recruiter email).

### During the sprint

1. Check the repository state before starting. In a new repository, commit the supplied contract and implementation pack as the starting point. In an existing repository, preserve its history and any unrelated user changes.
2. Use AI as the primary interface for code generation. As each coherent change is built, review its diff, run the relevant stage test, record the real result and decision in [AI-JOURNAL.md](AI-JOURNAL.md), then stage the related files and commit. Use a subject that says what behavior changed. Pick boundaries that reflect the work as it actually happens; no particular number of commits or branch strategy is required.
3. Example subjects for milestones you actually reach: `Add session login and claimant ownership checks`, `Handle concurrent pickup with a version check`, `Apply each reserve update once during Kafka replay`. If a test reveals a defect, record the observed failure and why the fix worked. Preserve the real sequence of work.
4. Include the candidate’s judgment in the journal. For example, Izhan selected Spring Boot after the initial .NET suggestion because he had previously worked with Spring. That is a planning decision. Add implementation decisions as they occur, with the prompt, what was accepted or challenged, the reason and the evidence. The candidate reviews and signs off on every submitted line.

### Before sending the link

- [ ] Use `git status --short` and `git log --oneline --reverse` to check that the intended files, tests and journal are committed and the history shows the actual development process.
- [ ] Verify the exact submission commit from a separate clean checkout and fresh local database. Follow only the reviewer README to start the application, log in and exercise a real workflow. Record the command, outcome, test results and any gaps.
- [ ] Put startup prerequisites, one-command launch, URL, local demo credentials, test commands, implemented scope and limitations at the top of the reviewer README. Include a link to the AI journal.
- [ ] Prepare to walk through a claimant-to-officer handoff in the shared Angular app and explain the resulting workload and outstanding exposure. Trace one operation from its OpenAPI contract to its database change. Explain why the response is synchronous, where Kafka fits, and how the dashboard handles delayed or repeated events.
- [ ] Record one scope decision with its reason and the evidence behind it. Explain what was deferred and why. Check that the repository URL opens the intended revision and that the hiring panel can access it under the chosen sharing arrangement.

## Development tools for Codex

Use these when they answer a specific implementation question. The contract, migration and acceptance cases govern the project. Each stage can run with the repository commands alone.

| Stage | Recommended use | Verification |
|---|---|---|
| Backend, stages 1–6 | Start with the Maven wrapper, Boot 4.0 reference docs and the named Spring Boot tests. If the project is already open in a Spring Tools-enabled IDE, its optional embedded MCP server can show the resolved Boot version, classpath and discovered beans. For example, inspect which security configuration bean was discovered when a login test fails. | Reproduce the behavior with the stage test, e.g. ./mvnw -Dtest=AuthTest test from backend/. Use the Windows wrapper mvnw.cmd on Windows. |
| Angular, stage 7 | Use the official Angular CLI 21 MCP server from the installed frontend workspace. Ask it to list the project and show examples or best practices for typed reactive forms, standalone components and signals. The newer angular-developer and angular-new-app skills were introduced with Angular 22. Stay with the pinned v21 CLI for this project. | Run npm run build and npm run test:fixture from frontend/. Check keyboard operation and conflict recovery in the browser. |
| Browser integration, stage 8 | Run the committed Playwright tests. Use a saved trace to investigate a failed claimant/officer handoff. A separate browser MCP is optional for exploratory debugging. | Run npm run test:e2e against the real local backend. Record the command, failing assertion and final result in the AI journal. |

**Local Codex setup for Angular.** After the Angular workspace exists and npm ci has installed its locked CLI, add the following to the local project's .codex/config.toml if you want this helper. Replace cwd with the absolute path to your frontend directory. Forward slashes also work in a Windows TOML path. Starting Node directly uses the project's own CLI without downloading another version.

```toml
[mcp_servers.angular21]
command = "node"
args = ["./node_modules/@angular/cli/bin/ng.js", "mcp", "--read-only", "--local-only"]
cwd = "C:/projects/chubb-claims/frontend"
```

Open /mcp in Codex and ask it to run list_projects. Seeing the application from frontend/angular.json confirms the connection; get_best_practices and find_examples are useful next calls. For online documentation use the Angular 21 site. If the server is unavailable, proceed with the CLI and the existing tests.

**Local Codex setup for Spring Tools.** If using this helper, open backend/ in the Spring Tools IDE or extension, enable its embedded MCP server in the AI preferences and restart the IDE if prompted. In Codex, register the local Streamable HTTP endpoint advertised by that installed version, then verify it can identify this project's Boot version. Availability depends on the IDE, transport and workspace. The embedded server is a developer tool and belongs outside the application dependencies and Docker Compose. For a standalone CLI session, the Maven dependency tree and targeted tests are the quicker path.

[Angular 21 MCP commands](https://v21.angular.dev/ai/mcp) · [Angular agent skills announcement](https://blog.angular.dev/announcing-angular-v22-c52bb83a4664) · [Spring Tools MCP setup](https://spring.io/blog/2025/12/04/towards-spring-tools-5-part3/) · [Codex MCP configuration](https://developers.openai.com/codex/mcp/)

## Build order

| Stage | Guide | Evidence before advancing |
|---|---|---|
| 0. Contracts | [Workflow and data](docs/01-contract-and-data.md) | OpenAPI syntax, HTTP examples, Kafka schema and fixture consistency pass. |
| 1. Backend foundation | [Backend steps](docs/02-backend.md#1-foundation) | Clean PostgreSQL migration, schema smoke checks, health endpoint and Spring Boot context pass. |
| 2. Authentication | [Sessions and access](docs/02-backend.md#2-authentication) | Cookie login, identity, CSRF and logout work through Spring Security. |
| 3. Intake and ownership | [Claimant operations](docs/02-backend.md#3-intake-and-ownership) | A claimant can submit and read their own claim; the cross-owner access test passes. |
| 4. Pickup and information | [Staff handoff](docs/02-backend.md#4-pickup-and-information) | Concurrent pickup has one winner; only the claimant can answer the pending question. |
| 5. Assessment and decisions | [Settlement and rejection](docs/02-backend.md#5-assessment-and-decisions) | Money, terminal states, visibility and optimistic concurrency pass. |
| 6. Reporting and backend gate | [Kafka and reporting](docs/03-reporting.md) | Both reports, replay, broker recovery and the complete backend suite pass. |
| 7. UI with fixture data | [Angular UX](docs/04-frontend.md) | Both roles complete their journeys with the fixture adapter, including empty/error states. |
| 8. Integration and delivery | [Real sessions and walkthrough](docs/05-integration.md) | Clean Compose startup, real browser handoff, dashboard reconciliation and reviewer instructions. |

Use the [case matrix](docs/06-test-cases.md) for concrete expected results. Keep actual runs and decisions in [AI-JOURNAL.md](AI-JOURNAL.md). The contract checker is [verification/check_contract.py](verification/check_contract.py); the SQL smoke test is [verification/schema-smoke.sql](verification/schema-smoke.sql).

## Verification of this starting pack

The supplied OpenAPI document passed structural validation. The checker validated all 100 HTTP examples, the Kafka example and the fixture export. The migration and rollback-only SQL smoke cases ran on a fresh PostgreSQL 17.12 database and created six domain tables. Spring Boot, browser, concurrency and broker tests are acceptance gates for the implementation. The live smoke client runs once the application is available.

## Technology decisions

| Area | Agreed plan |
|---|---|
| Frontend | Angular 21 LTS and aligned CLI, TypeScript 5.9, RxJS 7.8, Node 24 LTS, npm, Tailwind CSS 4. Own a small set of accessible Angular components. Use Angular Router, Reactive Forms, HttpClient and signals. |
| Backend | Java 21 LTS, Maven 3.9 wrapper, Spring Boot 4.0 line, MVC, Security, Bean Validation, Jackson, Data JPA/Hibernate, PostgreSQL JDBC, Flyway with PostgreSQL support, Kafka, Actuator, SLF4J/Logback. Use the Boot dependency BOM. |
| Infrastructure | PostgreSQL 17, a local single-node Kafka 4.2 KRaft broker, Docker Compose. One Spring process hosts the API, publisher and consumer. Serve built Angular files from Spring on the same origin. |
| Tests | JUnit Jupiter, AssertJ, Spring Boot Test, MockMvc, Testcontainers PostgreSQL, real Kafka for the broker gate and Playwright/Chromium in TypeScript. |
| Version policy | Use application APIs available by **30 April 2026**, the published knowledge cutoff for [GPT-6 Astra](https://developers.openai.com/api/docs/models/gpt-6-astra), selected in Codex as GPT-6 Astra Light. Later maintenance/security patches on the agreed release lines are approved. Reference pins from planning: Boot 4.0.10, PostgreSQL 17.12, Kafka 4.2.2. Verify published coordinates, vendor maintenance and advisories when resolving. Record actual JDK, Node, Maven and container patch versions, commit lockfiles and use fixed image tags/digests. |
| Security review | Review actual Maven/npm resolution and container image findings. Record relevant fixes or limitations. Revisit a release line if it loses support. Avoid drifting to a new major just to satisfy a generator default. |

## Scope assumptions

One staff team across six **illustrative** markets. Two application roles, CLAIMANT and OFFICER; the team summary is a staff view. The flow supports one owner, one pending question at a time and one final settlement per claim. An officer estimates the reserve and records a decision based on evidence. A settlement records a final amount, closes the case and releases its reserve. External coverage lookup, actual payment transfer, file storage and notification delivery are outside this prototype. A decision is delivered via the claimant timeline. All demo data is fictional.

At delivery, put verified startup, demo users, tests and limitations at the top of the repository README so the hiring panel can use it without reading this build guide. Preserve links to the contract and the journal.

[Original assessment brief](https://docs.google.com/document/d/1dlMvqp0FOMI_TZK1Nj2QHIBo0l_V0BTV/edit)
