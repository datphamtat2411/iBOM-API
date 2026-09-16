Read `AGENTS.md`.

Plan CV Preview API for the current CV Preview & Export module.

Start discovery around:

* current Profile authorization and active/non-deleted Profile resolution;
* the canonical CV assembler introduced by the CV document task;
* the shared PDF renderer;
* current Profile preview-state/version behavior and optimistic-concurrency conventions;
* current controller, binary-response, security, exception, and focused-test patterns.

Read only:

* `docs/API.md`;
* `docs/ARCHITECTURE.md`;
* `docs/TESTING.md`.

Follow task-related source-code dependencies discovered from these areas as needed. Keep discovery inside CV preview orchestration, authorization, preview-state transition, and PDF delivery.

The required API contract is fixed for this task:

```http
GET /api/cv/preview/{profileId}
Authorization: Bearer <JWT>
```

Successful response:

```text
Content-Type: application/pdf
```

Do not rename, relocate, or change the HTTP method of this endpoint.

Plan these capabilities:

* allow a Profile owner to preview their own active Profile;
* allow Manager/Admin to preview an active Member Profile;
* reject unauthorized Profile access without exposing Profile data;
* treat a missing or soft-deleted Profile as unavailable;
* assemble the latest saved Profile data through the canonical CV assembler;
* render the assembled document through the shared PDF renderer;
* return the generated PDF directly as the successful response;
* after successful generation, set that Profile's `hasPreviewed=true`;
* allow an already-previewed Profile to be previewed again.

Task-specific constraints:

* applicable CV content is Personal/About Me, Technical Summary, Personality, Education, Languages, Certificates, Projects, and Skills;
* empty list sections must omit both heading and content;
* Preview must not duplicate Profile-section retrieval, ordering, or PDF-rendering rules owned by earlier tasks;
* assembly or rendering failure must leave preview state unchanged;
* Preview must never update `lastExportedAt`;
* Preview must not resolve File Name Formats or export filenames;
* the preview must validate the same Profile version that was assembled/rendered: a concurrent CV-data mutation must not let a stale preview mark the current Profile `hasPreviewed=true`;
* preserve existing optimistic-concurrency semantics;
* preview state is independent per Profile;
* use existing centralized authentication, authorization, and error-handling conventions.

Out of scope:

* PDF renderer implementation details;
* DOCX generation;
* filename resolution or export Content-Disposition;
* CV Export API;
* export-before-preview validation;
* `lastExportedAt` updates;
* File Name Format CRUD;
* Dashboard behavior.

Return `plan.md` with exactly:

* Task Plan
* Repository Findings
* Proposed Changes
* API Surface
* Configuration
* File Delta
* Focused Tests
* Out of Scope

Keep `plan.md` within roughly 150 lines.
Organize Proposed Changes by capability or concern, not by file.
Keep File Delta to Modify / Add / Remove inventory only.
Do not modify repository files.
