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
