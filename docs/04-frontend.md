# 7. Angular UI with contract fixtures

Build one Angular SPA. Keep calls behind a small ClaimsApi/AuthApi boundary. Implement a fixture adapter first. Choose the adapter by build configuration so demo fixtures cannot be silently used in the integrated app. Import [the named examples](../api/ui-fixtures.json) generated from [OpenAPI](../api/openapi.yaml). Use local state for navigation and subsequent commands. Clone fixture records before mutation. Enforce expectedVersion in the fake so conflicts can be exercised.

| Route | What the user needs | Interaction and states to verify |
|---|---|---|
| /login | Sign in | Username/password labels, submit with Enter, generic invalid-credentials message, sensible focus, disable duplicate submit. Local demo accounts are identified as sample data. |
| /my-claims | Find my cases | Show reference, status, incident and next action. Display a clear empty state with Report an incident. |
| /my-claims/new | Report an incident | Motor/property, market, date, description. Mark required fields; inline errors preserve input. Submit once, show reference and navigate to actual detail. |
| /my-claims/:id | Understand progress | Status, what happens next, oldest-first timeline and pending question with a reply field. On closure show amount/currency or rejection reason. Keep staff-only fields out of both the response and the DOM. |
| /staff/queue | Choose work | All / Mine / Unassigned controls, table on desktop and a readable compact list on mobile. Show age or submission time, market, status and owner. Offer pickup only where valid. |
| /staff/claims/:id | Work one case | Show assessment separately from settlement. Distinguish “Awaiting estimate” and “MYR 0.00”. Show disabled or absent actions with a clear next step. Confirm settlement or rejection using amount or reason before submitting. |
| /staff/reports | Understand team position | Open cases by officer, unassigned, waiting on claimant, closed in seven days. Show known exposure and unassessed count by currency, plus last financial update. |

Use neutral backgrounds, one action color, readable text, consistent spacing and a visible focus indicator. Text accompanies status colors. Use native labels, buttons, headings and tables with headers. Error messages explain how to recover. Keep destructive and final actions visually distinct. Use Angular 21/Tailwind 4 and local reusable components, with no dependency on a prerelease design system.

Fixture login uses distinct claimant and officer identities. Implement a working fake state transition for submit → pickup → ask → answer → reserve → settle, plus rejection. Derive new record IDs/references for fake submissions. The submit success must appear in the list immediately. Add controlled fixture scenarios for loading, empty list, retryable read error, permission error and stale version. On a 409, refresh the detail and explain the conflict; require the user to confirm again before any money-changing action. Avoid automatic command retries and fictitious success toasts.

**Fixture gate U1–U4.** In frontend/ wire npm scripts build and test:fixture. The latter runs Playwright against fixture mode on a dedicated local port, launches the frontend itself and fails on unexpected /api or /actuator traffic. Fixture/Auth adapters simulate session identity per browser context; share fake business state for the scenario when exercising cross-role flows (e.g. one context changes personas via logout, or a shared test-owned in-memory store). Run npm ci, npm run build and npm run test:fixture. Use Playwright keyboard interaction and desktop/mobile viewports. Browser interactions, assertions and a screenshot on failure give better evidence than a screenshot alone.

Integration starts after the fixture gate. The real HTTP adapter should need no changes to forms, tables or timelines.
