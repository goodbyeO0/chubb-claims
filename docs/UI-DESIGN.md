# Claims workbench UI decisions

The primary task is progressing a claim with a clear next action. Claimants see progress, questions and the recorded decision. Officers see a queue, an assessment and a separate final decision. Reports keep currencies separate and explain asynchronous freshness.

Palette: ink `#172b35`, muted text `#526773`, surface `#ffffff`, canvas `#f4f6f7`, action teal `#006b68`, rejection red `#99252e`. Color never carries status alone. A restrained sans serif and tabular amounts support scanning; labels and headings stay left aligned. Borders group evidence and actions; no decorative charts or stock imagery.

Desktop case layout:

```
Reference / status / next step
Incident and conversation | Ownership
Timeline                  | Staff assessment
                          | Request information
                          | Final decision
```

On mobile, the queue becomes stacked labelled rows and case actions follow the evidence. Native controls, visible focus, a skip link and native modal dialogs support keyboard use. Cancel receives initial dialog focus and returns focus to the initiating action. A conflict refreshes the current representation, preserves input and requires a new confirmation; no mutation is automatically retried.

Design review: the skill search returned landing-page patterns that did not fit an operational workbench. Applied its accessibility and spacing guidance, using the supplied route/task matrix for layout. Kept the sign-in introduction brief and the case workspace practical. The fixture adapter is selected at build time, shares business state within a test browser context and keeps persona identity in session storage. Named OpenAPI examples are cloned, never modified at source. Fixture scenarios are query parameters available only in fixture builds.
