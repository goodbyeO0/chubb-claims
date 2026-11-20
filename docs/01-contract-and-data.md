# 0. Contract and data

1. Read the operations and their named request/response examples in [openapi.yaml](../api/openapi.yaml). Use camelCase on the wire and snake_case in PostgreSQL. UUIDs identify rows. Generate a unique human reference using the database sequence. Never require a client to supply its identity, role, currency, reference or event visibility.
2. Run the contract gate below. Its inputs are the actual spec and [exported fixtures](../api/ui-fixtures.json). When a contract changes, update the canonical spec, review the change, run the checker with --refresh-fixtures and rerun without it. Keep example excerpts out of application logic.
3. Implement the [Flyway migration](../backend/src/main/resources/db/migration/V1__claims.sql) as supplied. In later stages configure Hibernate schema validation and map the text enums using string values. Keep monetary arithmetic in Java BigDecimal and PostgreSQL numeric. Reject excess scale before writing, so the database never silently rounds an input. Use UTC instants; incidentOn is a local calendar date.
4. Write the externally visible contract first if a missing requirement emerges. Change the schema, fixtures and tests together. Use existing endpoints for the specified workflows.

### Contract gate

From the root, use a disposable Python environment with PyYAML, jsonschema[format] and openapi-spec-validator. These are authoring tools and need no application runtime container.

```sh
python -m venv .venv
# Activate the environment, e.g. source .venv/bin/activate on Linux/macOS.
python -m pip install PyYAML 'jsonschema[format]' openapi-spec-validator
python verification/check_contract.py
```

Expect 18 operation IDs, 100 request/response examples and consistent fixture and Kafka data. The totals describe this version; deliberate contract edits can change them.

### Workflow rules

| Command | Required actor and prior state | Result |
|---|---|---|
| submitClaim | Claimant with valid incident data | SUBMITTED, unassigned, reserve NULL, version 1. |
| assignClaim | Officer, SUBMITTED and unassigned | UNDER_REVIEW, current officer owns claim. |
| requestInfo | Assigned officer, UNDER_REVIEW | AWAITING_INFO, one pending request. |
| answerRequest | Owning claimant, AWAITING_INFO, matching pending request | Save answer; UNDER_REVIEW with the same officer. |
| setReserve | Assigned officer, UNDER_REVIEW | Keep status, set absolute reserve including zero. Staff-only event. |
| settleClaim | Assigned officer, UNDER_REVIEW | SETTLED, positive final amount, reserve zero, closedAt populated. |
| rejectClaim | Assigned officer, UNDER_REVIEW | REJECTED, public reason, settlement NULL. Release any assessed reserve to zero; keep unassessed NULL. |

Every successful update increments version once and appends one claim_event. JPA primitive @Version fields commonly start at zero. Explicitly arrange and test version 1 on creation (e.g. a repository INSERT with version=1 followed by normal managed updates). Flush before reading the new version for the event. Conflict checks, claim write, request write when applicable, audit and any outbox insert share one transaction. Roll back all writes if any fails.

For an authenticated command: check role and visibility, then assigned officer when required, then expectedVersion and state. A claimant accessing another owner’s resource gets 404, including answer attempts. A staff member mutating another officer’s case gets 403. On pickup, competing requests use the same original version and the loser gets 409 VERSION_CONFLICT. Check stale input even if the entity was freshly loaded. Also handle database optimistic-lock failures and map them to the same 409. Validate that requestId belongs to the path claim. Final states support reads only.

### Tables and reporting boundaries

| Table | Key and lifecycle |
|---|---|
| app_user | UUID PK. Normalized unique username, password_hash, display_name and role. Local-only seed users are created in stage 2. |
| claim | Many claims per claimant; zero or one assigned officer per claim. Server verifies roles referenced by the two FKs. Unique claim_ref; version detects racing writes. |
| information_request | Many per claim; partial unique index permits one unanswered question per claim. |
| claim_event | Append-only history; unique (claim_id, claim_version). details JSON contains message plus typed fields such as requestId, reason, amount or previous/current reserve. Build the claimant response from an allowlist. |
| outbox | Zero or one row for a claim_event. The PK also references the event. Commit a full exposure payload on submission, reserve changes, settlement and rejection only. |
| exposure_snapshot | Zero or one row per claim. Track the latest applied financial version. A skip from v1 to v5 is valid because intermediate nonfinancial actions produce no reporting events. |

Market/currency pairs for this exercise: MY/MYR, SG/SGD, TH/THB, ID/IDR, PH/PHP, HK/HKD. Store all amounts at two decimals for the prototype. The API rejects an unknown market or future incident date in the chosen market time zone (Kuala Lumpur, Singapore, Bangkok, Jakarta, Manila, Hong Kong respectively). Keep a Clock injectable in tests. Lists are unpaginated for the bounded local dataset; document that choice. Order timelines by version ascending and requests by asked_at then id.

### API conventions

Claimant and staff endpoints have separate response schemas. The staff endpoint for a command returns the fresh staff view. A claimant answer returns the fresh claimant view. Use absolute decimal strings such as "5000.00", including output zero "0.00". Use structured Error objects even for security filter responses. For query parameter binding errors use 400 INVALID_INPUT. Client login is form-urlencoded; workflow commands use JSON. Reject unknown JSON body keys and whitespace-only required text. Treat GET requests as read-only. Disable double-submit in the UI; creation has no idempotency key, so never automatically retry an ambiguous submission.
