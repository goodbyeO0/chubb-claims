# 8. Integrate, verify and hand over

1. Implement the HTTP adapter with Angular HttpClient, same-origin relative /api paths, cookie sessions and native Angular XSRF cookie/header names. Bootstrap with csrf then me. A 401 from me on first visit shows login. Before an anonymous login obtain the CSRF cookie; after successful login or logout refresh it. Surface actionable validation errors and handle 403, 404 and 409 distinctly. A late API error must never switch the UI to fixture mode.
2. Create a multi-stage build: compile Angular with the Node lockfile, copy its browser output into Spring's static assets, build the JAR with the Maven wrapper, then run it on a Java 21 runtime image. SPA navigation falls back to index.html only for real frontend routes. Missing assets and unknown API paths retain appropriate errors. During development an Angular dev proxy forwards /api and /actuator to Spring.
3. Start the complete application on a clean disposable Compose project: docker compose -p chubb-review up --build -d. Keep db data across restarts. Open http://localhost:8080. Use local demo credentials from the README. Check docker compose -p chubb-review ps and the basic health status before browser testing.
4. In frontend/ implement npm run test:e2e. Use Playwright browser contexts for isolated cookies and a shared real backend. Generate new claims per run and compare dashboard deltas with a captured baseline, or explicitly isolate the test database. Use condition-based waits for Kafka and UI refresh. Provision the browser once with npx playwright install chromium.
5. Run the walkthrough below, verify operational logs, and record actual test results and gaps. Put the working commands, startup URL, local-only logins, architecture decisions and known limitations at the top of the submission README. Use meaningful commits at passing backend, fixture and integration milestones. The AI journal should contain dated real interactions and human validation.

### Reviewer walkthrough

| Actor | Action | Observation |
|---|---|---|
| Aina | Submit MY motor bumper incident. | Unique reference and SUBMITTED. Farah's unassigned queue includes it. |
| Farah | Pick up, ask for workshop estimate. | Aina's view says she needs to reply. Farah sees one pending question. |
| Aina | Answer that the initial estimate is MYR 5,000. | UNDER_REVIEW and the answer is visible to Farah. |
| Farah | Set reserve to 5000.00. | Staff can trace the estimate. After Kafka catches up, known MYR exposure increases by 5000.00. Aina's timeline has no reserve event. |
| Farah | Record final workshop amount 4200.00 and settle. | Aina sees the amount and decision. The case is closed and the MYR exposure increase disappears. |
| Daniel and Arjun | Daniel files an SG property claim and later confirms a duplicate report. Arjun records the case-specific rejection reason. | The decision is visible to Daniel. The SG case contributes zero open claims after processing. This path needs no fabricated policy check. |

### Commands and evidence

```sh
python verification/check_contract.py
(cd backend && ./mvnw test)
(cd frontend && npm ci && npm run build && npm run test:fixture)
docker compose -p chubb-review up --build -d
curl -fsS http://localhost:8080/actuator/health
python verification/api_smoke.py --through decision
(cd frontend && npm run test:e2e)
docker compose -p chubb-review logs --since=10m api
```

**Gate I1–I5.** Use the [case matrix](06-test-cases.md). Include at least one real GET from a signed-in session after a direct page reload, and one CSRF-protected mutation. In the reviewer notes distinguish “tested with Compose locally” from “deployed”. For a deployment profile use HTTPS, Secure session cookies, environment-supplied secrets, no demo user seeding and authorized identity provisioning. A container platform can collect the JSON stdout stream; Azure Container Apps/Log Analytics is an optional deployment destination. The assessment delivery is local.

**Failure triage.** A 403 after first login usually calls for inspecting the refreshed XSRF cookie/header in the test. An unchanging report calls for tracing the same eventId through the pending row, broker acknowledgment and snapshot version. A refresh returning HTML on /api calls for correcting the SPA fallback. Preserve the original assertion or reproduction in the journal when fixing a defect.
