# AI working journal

Use this as a running log during implementation. Record the actual prompt or short excerpt, the proposed change and your decision. Include the test or observation that informed it. Add an entry when you accept a suggestion as well as when you challenge or override one.

| Date | Prompt and context | AI suggestion | Accepted, challenged or overridden; reason | Test command, observation and commit |
|---|---|---|---|---|
| | | | | |

### Delivery evidence

Record the commands actually run, commit tested, totals, failures, retries and deferred work. Describe any manual checks. Keep screenshots or logs free of credentials and claimant information. The tests in the implementation guides are planned checks until executed against the built app.

## 2026-11-21 — Stage 0: supplied baseline

Prompt: implement the stages in order, preserve the migration, validate before advancing, and commit meaningful changes.

Decision: the application directory and its parent have no Git repository. Initialize one history here and commit the supplied pack before implementation. The contract remains canonical; no migration or contract changes. Contract gate executed in a disposable Python venv: `.venv/bin/python verification/check_contract.py` → PASS, 18 operations, 100 HTTP examples, Kafka example and UI fixtures. Reviewed supplied paths and stage rules. Java was absent and Docker initially stopped; provisioning a user-local JDK and starting installed Docker Desktop is necessary for the real PostgreSQL tests.

## 2026-11-21 — Stage 1: foundation

Accepted: Spring Boot 4.0.10 (published Maven Central coordinate verified), Java 21, Maven wrapper 3.9.11, PostgreSQL 17.12, Kafka 4.2.2. Boot's BOM resolves Hibernate 7.2.25.Final, Spring Security 7.0.9 and Testcontainers 2.0.5. Vendor release/advisory review: https://spring.io/blog/2026/11/20/spring-boot-4-0-10-available-now/ and https://spring.io/security/cve-2026-59411/ identify 4.0.10 as a fixed release. This is not a complete transitive dependency/container vulnerability scan; that remains a delivery check.

Implemented the Maven application, six entity mappings for schema validation, public health summary, request IDs, deployment JSON logging, Compose services and bounded container logs. The migration is byte-for-byte unchanged from the baseline. Generated Windows wrapper CRLF initially triggered diff whitespace checks; `.gitattributes` and index renormalization resolved that without editing wrapper logic.

Actual runs:
- Contract check: PASS (18 operations / 100 HTTP examples).
- `./mvnw -Dtest=FoundationTest test`: PASS, 2 tests, 0 failures/errors/skips; repeated after adding entity mappings: PASS, 2 tests.
- `./mvnw -DskipTests package`: PASS (packaging only; not counted as a test pass).
- Docker initially unavailable. Starting its installed user service (`systemctl --user start docker-desktop`) restored Docker Engine 29.6.1. User-local Temurin 21.0.13.1+2 supplied the absent JDK. No system package changes.
- Port 8080 was occupied by an existing portfolio application; its HTML response is NOT a health pass. Added `API_PORT` override and selected 18080 without stopping the other application. First Compose port interpolation needed YAML quotes; fixed and retried. Original startup overlapped container recreation and reported a missing old container; subsequent `up -d` succeeded.
- Added bounded Maven/test heaps because the host was under swap pressure.

Foundation defect found by the real HTTP check: Actuator in the container returned `{"groups":["liveness","readiness"],"status":"UP"}`, violating Health.additionalProperties=false. The earlier MockMvc JSON comparison was lenient. Disabled probe-group exposure and changed the assertion to exact response content. SQL smoke on Compose PostgreSQL passed (`BEGIN`, `DO`, `ROLLBACK`). Updated the runtime image to a resolved current Java 21 digest rather than retaining the older 21.0.9 example tag; pinned the Maven builder digest too. Verification is being repeated against the correction.

Final foundation verification: exact-health test PASS (2 tests, 0 failures/errors/skips), rebuilt Compose API PASS at `http://localhost:18080/actuator/health` with exactly `{"status":"UP"}`. Recreated API against the same named volume; Flyway history still contains exactly version 1 with success=true. Schema smoke PASS. Reviewed staged diff and `git diff --cached --check` PASS. Foundation gate complete; authentication is next.

## 2026-11-21 — Stage 2: sessions and CSRF

Accepted Spring Security's SPA CSRF support (official 7.0 reference https://docs.spring.io/spring-security/reference/7.0/servlet/exploits/csrf.html), form authentication and server sessions rather than custom credentials/token logic. Four BCrypt demo identities are seeded only under `local`; identity is looked up from the authenticated principal. API filter failures use the contract Error shape. Session cookies are HttpOnly/SameSite=Lax and Secure in deployment. Request cache disabled to avoid anonymous saved sessions on bad credentials.

`./mvnw -Dtest=AuthTest test`: PASS, 3 tests, 0 failures/errors/skips. Tests execute form login with actual CSRF cookies/headers, fresh post-login token, role-specific identity, logout invalidation, identical bad-password/unknown-user responses, missing/incorrect CSRF and claimant/staff access denial. Reviewed authentication diff and whitespace check. Real network cookie smoke will also run with intake and again at the backend gate. Stopped only this project's idle API/Kafka containers while testing to reduce host memory pressure; retained database volume.

## 2026-11-21 — Stage 3: intake and ownership

Accepted explicit repository INSERT at version 1 followed by managed JPA updates. Database sequence generates references; server derives currency, claimant and timestamps. Explicit response allowlists omit internal fields; no entity is serialized. Submission, audit and full initial outbox payload share the transaction. Unknown JSON keys are rejected, and market-local date validation uses an injectable Clock.

`./mvnw -Dtest=ClaimIntakeTest test`: PASS, 2 tests, 0 failures/errors/skips. Verified persisted claim/event/outbox counts, payload identity/version/NULL reserve, exact fixed timestamp, cross-owner list/detail isolation, officer submission denial and zero writes for blank/unknown/future/forged input. Diff reviewed; migration unchanged. Staff-event visibility is additionally exercised with reserve actions in stage 5.

## 2026-11-21 — Stage 4: pickup and information handoff

Accepted JPA optimistic locking with an explicit expectedVersion check before state checks. Assignment writes flush before audit creation; assigned-officer authorization precedes stale/state checks for subsequent commands. Request ownership and claim/request association are verified before answering. Nonfinancial changes do not create reporting events.

`./mvnw -Dtest=ClaimWorkflowTest test`: PASS, 2 tests, 0 failures/errors/skips. Concurrent pickup used two HTTP requests, a barrier after both loaded the row, and two distinct `pg_backend_pid()` connections: exactly one 200, one 409, one assignment event. Handoff verified one pending question, wrong officer 403, wrong claimant/request 404, answer restoration, stale replay, queue filters and unchanged outbox count. Reviewed diff and `git diff --cached --check` PASS.

## 2026-11-21 — Stage 5: assessment and decisions

Accepted BigDecimal arithmetic and exact two-decimal input strings. Challenged relying solely on dirty checking for reserve changes: setting the same absolute amount would produce no Hibernate update. The conditional reserve UPDATE increments version exactly once even for an unchanged value, then refreshes the managed entity before audit/outbox creation. Settlement closes and releases the entire reserve; rejection preserves NULL for unassessed cases and releases known reserves. Success logging runs after commit only.

`./mvnw -Dtest=DecisionTest test`: PASS, 3 tests. `./mvnw test`: PASS, 12 tests, 0 failures/errors/skips. Covered invalid scales/signs/zero settlement, repeated zero reserve, private reserve history, stale versions, all staff mutations on terminal states, both rejection reserve cases, and forced outbox constraint failure with claim/version/events/outbox unchanged. Expected test-only constraint error appears in test logs; no success log emitted for that transaction. Diff review and whitespace check passed. No payment or coverage automation is implied by a recorded decision.

## 2026-11-21 — Stage 6: reporting gate in progress

Implemented SQL workload/reference aggregation, transactional monotonic snapshot upsert, scheduled stored-payload outbox publication and real Kafka consumption with record acknowledgements after the database transaction returns. Invalid messages remain retryable at their offset; no silent skip or fabricated successful application. Publisher retries are bounded in time and leave rows pending.

First `./mvnw -Dtest=ReportingTest,KafkaFlowTest test`: reporting/replay/rolling workload PASS (3 tests), Kafka recovery FAIL (snapshot remained version 1 instead of 3; pending reserve event recorded in assertion). Broker restart used an automatically assigned host port, while clients retained the original advertised address. Test now binds a selected free port explicitly for the container lifetime and captures broker logs under target/ for timeout diagnosis. A subsequent run failed earlier during context initialization: custom payload-safe producer listener had String/String generics while Boot required Object/Object. Corrected its type and made the factory static to avoid configuration-constructor dependency cycles. These failed runs are not passing gates.

Contract review also found Jackson scalar coercion could admit numeric money or fractional/string versions. Added strict coercion configuration and regression inputs. Applied Google Java Format 1.28.0 to improve readability; source formatting does not change the preserved SQL migration. Complete backend suite and live schema gate remain required before UI work.

Final stage 6 gate: `./mvnw test` PASS — 16 tests, 0 failures/errors/skips (2026-11-21 13:53 MYT). Targeted recovery/regression run also PASS — 7 tests. Rebuilt Compose with persistent Kafka storage; live `verification/api_smoke.py --base http://localhost:18080 --through decision` PASS, and `verification/backend_gate.py --base http://localhost:18080` PASS across all 18 operations with matching OpenAPI response validation, cookie flags, both decisions and ownership isolation. The schema validator emits a jsonschema RefResolver deprecation warning, not a failure. Reviewed reporting code, strict input behavior, container configuration and formatting diff; migration unchanged. Backend gate complete before frontend implementation.

## 2026-11-21 — User-requested pause after backend

Izhan requested a pause to conserve Codex usage and instructions for self-testing. Backend stages 0–6 are complete at commit `5f5f3f4`, with 16 passing tests and all 18 live API operations validated. Health rechecked at pause: `http://localhost:18080/actuator/health` returns exactly `{"status":"UP"}`. The existing `chubb-foundation` Compose project remains running for manual testing. The original migration remains unchanged.

Stage 7 scaffolding had already begun before the pause request arrived. Files in frontend/ are uncommitted and the fixture gate has NOT passed; do not treat that stage as complete. No frontend server is running. Stop implementation here and resume at the fixture UI stage when requested. No human walkthrough or final submission sign-off is claimed.

## 2026-11-21 — Stage 7 resumed: fixture UI

Izhan requested continuation of frontend work after the usage pause. Backend gate remains at the previously tested commit. Continued Angular 21/Tailwind 4 scaffold with a typed WorkbenchApi boundary, build-selected fixture provider, reactive forms, signals, role routes and explicit money confirmation. Fixture business state survives persona changes in the same browser context. Money totals use integer cents (BigInt), not floating arithmetic. A supplied rejected example reused CL-101's UUID; review caught that it was being skipped, so the independent cloned demo case receives CL-102 and its own UUID. API fixtures themselves remain unchanged.

Added Playwright gates for keyboard intake, full handoff/settlement and rejection, privacy, loading/empty/read failure/permission/not-found states, stale money reconfirmation and 375px queue/action layout. Every fixture test fails if any /api or /actuator request occurs. Initial scaffold build at pause passed; this alone was not a fixture gate. Current browser gate results are pending.

Fixture verification: initial 5-browser-test run PASS (52.4 seconds). After formatting and adding the market-local future-date regression, 5 tests PASS again (48.5 seconds). Screenshots inspected by Codex at 1280px and 375px: readable case hierarchy, visible controls and no page overflow; keyboard dialog cancel returns focus to its trigger. Added a reports assertion to verify 5000.00 reserve exposure becomes no open exposure after a 4200.00 settlement. No human validation is claimed.

Izhan then requested another pause after the frontend to inspect the result. Agreed to finish and expose the fixture UI, then stop before stage 8 real API integration. The project API and Kafka containers were stopped during browser testing to reduce memory pressure; their named volumes were preserved. README now documents separate fixture and backend startup, credentials, scenarios and test commands. The final submission checklist remains open because integration, clean-checkout verification and human sign-off are still pending.

Final stage 7 gate (2026-11-21, approximately 18:54 MYT): `npm ci` PASS; `npm run build` PASS (348.73 kB initial bundle, within configured budget); `npm run test:fixture` PASS — 6 tests, no skips, 1.1 minutes. Contract check PASS — 18 operations, 100 HTTP examples, Kafka example and unchanged fixtures. Source/lockfile/diff reviewed and `git diff --cached --check` PASS. Migration and API source files remain byte-for-byte unchanged against the initial baseline. npm emitted optional install-script policy warnings, and Angular emitted dev-server optimization/HMR warnings; build and all browser tests still completed successfully. No live API traffic or browser runtime errors occurred in fixture tests. Stage 7 complete; stage 8 deliberately not started at the user's request.

## 2026-11-21 — Stage 8: real API integration

Izhan requested integration after reviewing the fixture milestone. Added a separate HttpClient adapter as the default production provider; the fixture configuration explicitly replaces it at build time. Components keep the same API boundary. Sessions bootstrap with csrf then me, login uses form fields, authentication changes refresh CSRF, and commands are never automatically retried. Removed the fixture preview process so fixture regression tests can own their dedicated port.

Added known-route-only SPA forwarding and a root multi-stage Docker build: Node lockfile build → static browser assets in Spring resources → Maven wrapper JAR → nonroot Java 21 runtime. Compose now builds from the root. Development proxy forwards same-origin /api and /actuator. Integration browser tests use isolated claimant/officer cookie contexts, fresh records, actual CSRF mutation, direct reload, response privacy checks, real 409 reconfirmation and baseline-relative exposure reconciliation. Tests also assert late HTTP failures never load fixtures. Verification is in progress; no integration pass claimed yet.

Stage 8 working-tree verification PASS: production `npm run build` (348.80 kB initial bundle); `./mvnw test` 17 tests, 0 failures/errors/skips; fixture regression 6 tests PASS (49.8 seconds); packaged Compose `npm run test:e2e` 4 tests PASS (32.0 seconds). Live decision smoke PASS and all-18-operation schema gate PASS. Exact health is `{"status":"UP"}`. Real workflows verified separate sessions, direct reload, native XSRF header, HttpOnly/Lax session, question/answer, both decisions, public response/DOM privacy, cross-owner 404, full-reserve report delta, real 409 refresh/reconfirmation and late-error behavior without fixtures. Missing API/asset routes return errors rather than index.html. No browser runtime errors.

I5 evidence: recovery-test event `e068f56c-efb8-4729-88a9-dd721fdb483c` appears in reserve_saved → publish_retry → event_published → snapshot_updated. Packaged Compose event `fef2f212-3213-44cc-9e66-6dafd55718a9` was traced from reserve save through publication/application. Scanning local app logs found no demo password, session/CSRF header names or submitted test prose. Testcontainers had a 30-second credential-helper timeout before using public images successfully; this was startup latency, not a failed test. Root Dockerfile replaces the earlier backend-only Dockerfile, and the resolved Node image digest is pinned. Migration/API sources still unchanged. Clean-checkout gate remains next.

Development proxy check PASS: `npm start -- --host 127.0.0.1` on 14201 forwarded health to the API (UP), and `E2E_BASE_URL=http://127.0.0.1:14201 npm run test:e2e -- --grep 'late API'` passed (1 test, 4.4 seconds), including real cookie/CSRF login and retry after a controlled read outage. Reviewed integration adapter, build selection, routing, tests and README; staged whitespace check passed.

## 2026-11-22 — Final verification resumed

Izhan asked whether the app was ready, then confirmed the usage limit had reset and requested completion. The clean checkout of `cff339c216ec747a990ca181e0ffbdfdebed323e` built successfully with newly installed Python/Node dependencies and new `chubb-review` volumes. However, its first health command stopped on curl exit 52 (empty reply during startup): `--retry-connrefused` did not cover that transient response. That attempt did NOT run the subsequent clean browser gate. The running app later reports exact UP and contains two manually entered claims; no claim about who performed which checks is inferred from those records.

Updated the README health probe to retry all transient curl errors with a bounded retry duration. To preserve the preview records, final fresh-database verification uses a separate `chubb-submission` project on port 18081. Only this project's preview API/Kafka are temporarily stopped to reduce memory pressure; their data volumes remain intact and the preview will be restored after verification.
