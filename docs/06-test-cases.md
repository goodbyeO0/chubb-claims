# Acceptance cases

Each ID is a test to implement or a reproducible manual check explicitly marked below. The expected outcomes are design criteria. Record actual results in the journal. Use fresh test records and controlled clocks. For API assertions validate response JSON against the matching operation and use a second database read when the case checks persistence.

| ID | Given / action | Required observation |
|---|---|---|
| F1 | Launch Spring against a virgin PostgreSQL 17 container. Run Flyway; restart on the same DB. | Six domain tables plus Flyway history; migration applies once. No destructive startup logic. |
| F2 | Execute verification/schema-smoke.sql after migration. | Constraint checks, conditional update and stale reporting event checks complete; test data rolls back. |
| F3 | GET /actuator/health and inspect local logs. | 200 UP; readable log output with no demo credentials or session material. |
| A1 | Anonymous GET me; fetch CSRF; log in as aina; GET me. | First 401, valid login 204 with session; subsequent 200 returns only id/displayName/role. |
| A2 | Unknown username and wrong password, with valid CSRF. | Both give identical generic 401 BAD_CREDENTIALS; no authenticated session. |
| A3 | Signed-in POST with missing/incorrect CSRF; retry with fresh valid token. | 403 CSRF_INVALID, zero writes, then the valid action succeeds once. |
| A4 | Logout with valid CSRF; replay the old session on GET me; sign in as farah. | Logout 204, old session 401, fresh login identifies OFFICER. |
| C1 | Aina submits valid MY motor claim. | 201, version 1, MYR, unassigned, reserve NULL in DB; claim, event and pending outbox agree. |
| C2 | Submit blank description, unknown market, future date or forged claimantId. | 400 INVALID_INPUT for each; no rows created. Fix the Clock for date boundary cases. |
| C3 | Daniel lists claims and attempts GET on Aina's UUID. | Aina's claim absent from Daniel's list; GET 404 with generic message. |
| C4 | Aina opens a claim with reserve history; officer tries claimant-only submission. | Aina sees public events only, no reserve/actor fields; staff submission 403. |
| W1 | Farah and Arjun send concurrent pickup commands on version 1. | One 200 version 2, one 409 VERSION_CONFLICT; one assignment and audit event, one current owner. |
| W2 | Winner opens a question; loser tries to change the case. | Winner gets AWAITING_INFO; other officer gets 403 on mutation. A second ask at the current version gives 409 INVALID_STATE. |
| W3 | Aina answers the pending request with the current version. | 200 UNDER_REVIEW, same owner, answered_at set, one public event and zero new financial outbox rows. |
| W4 | Daniel tries Aina's request; Aina supplies another claim's request ID. | Both 404; original request is still open. |
| W5 | Replay an old answer; query ALL, MINE and UNASSIGNED with both officers. | Replay 409; queues obey scope and documented ordering. |
| D1 | Set reserve 0.00, then 5000.00; try -1.00 or 1.001. | Zero differs from NULL; 5000.00 accepted; invalid money gives 400, with no change in version. |
| D2 | Settle UNDER_REVIEW at 4200.00; try settlement 0.00 on a fresh claim. | Valid settlement closes, reserve 0.00, positive settlement and public message. Zero gives 400. |
| D3 | Reject another claim with a reason; try blank reason on a fresh case. | Rejection closes, stores public reason and NULL settlement; blank reason gives 400. |
| D4 | Submit an old expectedVersion, then attempt each workflow mutation on a closed case with the current version. | Authorized actor gets 409, zero extra events, financial values preserved. |
| D5 | Cause the financial outbox insert to fail inside the transaction using a test-only constraint/failure injection. | Claim fields, request/event rows and version roll back together. No success log before commit. |
| R1 | Three open MY claims with 5000.00, 1200.00 and NULL. One open SG claim with 800.00. | MYR shows 3 open, 1 awaiting estimate, 6200.00 known. SGD separately shows 1, 0 and 800.00. |
| R2 | Apply a full v5 payload twice and then an older v1 payload to the same claim. | One snapshot at v5; reserve and applied_at unchanged on both replays. |
| R3 | Stop broker, save reserve, bring broker back. | HTTP success while down; outbox pending, then acknowledged; real consumer applies the expected version. |
| R4 | Close MY claim reserved at 5000.00; reconcile after catch-up. | MYR falls to 1200.00 with 2 open and 1 unknown. Closed claim settlement remains in DB; no cross-currency summing. |
| R5 | One unassigned claim, one waiting with Farah, Arjun zero open, one closure yesterday, one eight days ago. | Workload reports unassigned=1, awaitingInfo=1, Farah=1, Arjun=0, closedLast7Days=1. |
| U1 | Fixture mode: sign in and use keyboard to file a claim and open the reference. | Labels, focus, validation, disabled in-flight submit, visible next action; no live API traffic. |
| U2 | Fixture mode: complete ask/answer/settle, and inspect rejected fixture. | Correct statuses, public/private details and final decision; no success toast on error. |
| U3 | Fixture mode: exercise empty, loading, read failure and stale update. | Empty state provides next action, retry retains context, conflict refreshes latest data and requires reconfirmation. |
| U4 | Fixture mode: 375px mobile and desktop, keyboard-only dialog. | No clipped primary action; readable queue; focus enters dialog and returns to trigger on cancel. |
| I1 | Start from clean checkout on fresh project volume; browser refreshes /staff/queue after login. | Commands work, session restored, page renders, missing API path returns an API error. |
| I2 | Run settlement flow in separate claimant/officer Playwright contexts against the real backend. | Handoff, answered question, public settlement and hidden reserve verified in UI and network payloads. |
| I3 | Run independent rejection flow and direct cross-owner read. | Correct reason visible to owner; another claimant's GET gives 404. |
| I4 | Compare actual dashboard delta before/after settling an assessed MY case. | After catch-up the known exposure falls by the full reserve, not by the settlement; correct currency. |
| I5 | Inspect logs of a delayed or replayed event (manual or integration assertion). | Trace by eventId and claimRef from command through publication and apply/skip; no sensitive payloads. |

At the final gate record the exact commit, test command, pass/fail and any skipped cases. If a test is manual, say who performed it and what they saw.
