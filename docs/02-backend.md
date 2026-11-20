# Backend implementation

Use [the HTTP contract](../api/openapi.yaml) and [test matrix](06-test-cases.md). Create controllers for HTTP, services for transactions and ownership rules, repositories for persistence, and response DTOs for serialization. Organize by auth, claims and reporting. Add each named test class in the same commit as its behavior. Commands below run from backend/ unless marked otherwise. On Windows use mvnw.cmd.

From the application root you can also run [the live API smoke client](../verification/api_smoke.py) against a running local-demo server after gates 2–5. Use python verification/api_smoke.py --through auth, intake, handoff or decision (pick one value). Each run after auth creates one fictional claim. Use normal Python mode so assertions are enabled.

## 1. Foundation

1. Scaffold a Java 21 Maven application under backend/ while preserving the supplied migration. Let Boot manage the JDBC driver, JPA, MVC, Security, Validation, Flyway's PostgreSQL module, Actuator and test modules. Keep Kafka configuration in the reporting package. Testcontainers starts a disposable PostgreSQL for integration tests. Use the Boot 4 MVC test support matching the pinned starter.
2. Create compose.yaml in the application root with services named api, db and kafka. Use a named PostgreSQL volume, database health check and local .env.example values. Choose database claims and local user claims. Bind the API to 127.0.0.1:8080. Allow plain local HTTP in the demo profile. The app should start with db ready even if Kafka is temporarily unavailable. Run Kafka as a single KRaft broker with internal advertised address kafka:9092. Configure local single-replica topic and consumer offsets settings.
3. Enable Flyway and set spring.jpa.hibernate.ddl-auto=validate. Place all backend tests named *Test under Surefire so ./mvnw test includes integration tests. Publish only a basic health summary. Use the Clock bean for time tests.
4. Configure local console logs and an opt-in deployment profile for JSON stdout (logging.structured.format.console=logstash). Configure the Compose container log driver for bounded rotation. Include application version and request ID. Carry eventId, claimRef and claimVersion across async operations. Never log credentials, session/CSRF tokens, descriptions, request bodies or claimant personal information.

**Gate F1–F3.** Run ./mvnw -Dtest=FoundationTest test. From the application root run docker compose up -d --build and curl -fsS http://localhost:8080/actuator/health. Expect UP. Against a disposable Compose database run:

```sh
docker compose exec -T db psql -X -v ON_ERROR_STOP=1 -U claims -d claims < verification/schema-smoke.sql
```

If migrations fail, inspect docker compose logs api db and the first failing SQL statement. Test clean creation on a fresh Compose project/volume, then restart against that same volume. Never reset the user's existing data to make a test pass.

## 2. Authentication

1. Seed local-demo accounts only in the local profile. Use short usernames aina, daniel, farah and arjun. The first two are claimants, the others officers. Use the same documented local-only password local-demo-only. Store adaptive bcrypt hashes using Spring's password encoder. Seed once by username. Derive identity from the authenticated principal.
2. Implement csrf, login, me and logout. Use Spring Security form login at the contract path, with form-urlencoded credentials, success 204 and JSON failure handlers. Use server-side sessions, session fixation protection and HttpOnly/SameSite=Lax session cookies. Enable Secure in the deployed HTTPS profile. The SPA reads only the CSRF cookie. Implement Spring Security's SPA token handling including token generation on GET /api/auth/csrf. Angular calls that endpoint before login and after login/logout. Test with real cookies in stage 8.
3. Permit the SPA shell, its assets, basic health, csrf and login. Protect the application endpoints. Return 401 JSON on unauthenticated protected reads. Invalid/missing CSRF on unsafe methods returns 403 even when anonymous. Avoid redirecting API callers to an HTML login page. An unknown username and a wrong password get the same generic 401. Keep server sessions in memory for the single-instance demo; restart requires signing in again.

**Gate A1–A4.** Run ./mvnw -Dtest=AuthTest test. Include at least one actual form login and real session/CSRF handshake; use Spring Security test helpers for other focused permission tests. If the first mutation after login returns 403, check the fresh CSRF cookie and matching request header before changing authorization.

## 3. Intake and ownership

1. Implement submitClaim, myClaims and myClaim. Persist server-generated ID, unique reference and version 1. Enforce market pairing and incident date. Use DTO allowlists. Reject caller-supplied identity, role or internal fields.
2. Create CLAIM_SUBMITTED with public visibility and an initial open/unknown financial outbox payload. Store event ID and payload in the same transaction. Use a test clock to verify ordering, dates and audit timestamps.
3. Query claimant detail by both principal ID and claim ID. Exclude reserve, officerId, actorId, raw details and staff-only events from every claimant view, including lists.

**Gate C1–C4.** Run ./mvnw -Dtest=ClaimIntakeTest test. Assert database rows and HTTP responses. Expect one row each in claim, claim_event and outbox for the first submission. If the generated version starts at 0, correct insertion/version mapping here before implementing pickup.

## 4. Pickup and information

1. Implement staffQueue, staffClaim, assignClaim, requestInfo and answerRequest. MINE filters by current officer. UNASSIGNED filters by NULL officer. Give every action an explicit endpoint, authenticated actor and transaction.
2. For pickup, use an atomic conditional update or an @Version-guarded update with row-count/conflict handling. Resolve version and ownership races before appending an event. On failed transaction, zero new events and zero partial side effects.
3. Allow the assigned officer to open one question. Aina can answer only her own unanswered request. After the answer, restore UNDER_REVIEW and retain the same officer. Both participants can see the question and answer. No outbox rows for these nonfinancial changes.

**Gate W1–W5.** Run ./mvnw -Dtest=ClaimWorkflowTest test. Exercise two independent requests/transactions, separate database connections and a synchronization barrier for pickup. Assert one 200, one 409, one new audit row. A sequential retry is a separate case.

## 5. Assessment and decisions

1. Implement setReserve, settleClaim and rejectClaim using BigDecimal. A zero reserve is a valid estimate. Require a positive final settlement or a nonblank rejection reason as appropriate. Settlement records the final amount and closes the case. Use the workflow rules for reserve release.
2. Set public decision messages deliberately. A rejection message contains the officer's decision reason. Reserve history remains staff-only. Do not automate a policy-coverage decision from the incident date alone.
3. For each successful financial action append the event and corresponding outbox message atomically. Set closed_at for both terminal outcomes. Capture the saved version and record a success log only after commit. Use a forced persistence failure in a test to prove rollback.

**Gate D1–D5.** Run ./mvnw -Dtest=DecisionTest test, then ./mvnw test. Failed commands must preserve version, reserve, event count and outbox count. Continue with [Kafka and reports](03-reporting.md).
