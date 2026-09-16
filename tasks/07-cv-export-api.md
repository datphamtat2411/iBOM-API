Read `AGENTS.md`.

Plan CV Export API for the current CV Preview & Export module.

Start discovery around:

* current CV Profile authorization/access behavior introduced by Preview;
* the canonical CV assembler;
* PDF and DOCX renderer contracts;
* filename resolution behavior and File Name Format selection;
* current Profile preview/export state and optimistic-concurrency conventions;
* current controller, binary-download, security, exception, and focused-test patterns.

Read only:

* `docs/API.md`;
* `docs/PRODUCT.md`;
* `docs/ARCHITECTURE.md`;
* `docs/TESTING.md`.

Follow task-related source-code dependencies discovered from these areas as needed. Keep discovery inside CV export orchestration, download delivery, preview gating, and successful-export state.

The required API contract is fixed:

```http
GET /api/cv/download/{profileId}?format=pdf
GET /api/cv/download/{profileId}?format=docx
Authorization: Bearer <JWT>
```

Optional query parameter:

```text
fileNameFormatId={id}
```

Do not rename, relocate, or change the HTTP method of this endpoint.

Plan these capabilities:

* allow a Profile owner to export their own active Profile;
* allow Manager/Admin to export an active Member Profile;
* reject unauthorized, missing, or soft-deleted Profiles without exposing CV data;
* require `hasPreviewed=true` before either PDF or DOCX export;
* support only `pdf` and `docx`;
* resolve the export filename through the shared filename resolver;
* assemble the latest saved Profile through the canonical CV assembler;
* route PDF exports through the shared PDF renderer and DOCX exports through the shared DOCX renderer;
* return the generated document as a downloadable binary response;
* update `lastExportedAt` only after a successful export;
* preserve `hasPreviewed=true` after export.

Successful response requirements:

* PDF: `Content-Type: application/pdf`;
* DOCX: `Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document`;
* use `Content-Disposition: attachment` with the filename returned by the filename resolver.

Task-specific constraints:

* an unpreviewed Profile must be rejected before document rendering;
* PDF and DOCX share the same preview gate;
* invalid or missing required export format must not silently default;
* explicit `fileNameFormatId` selection must follow the filename-resolution rules from the filename task;
* filename-resolution, assembly, or rendering failure must leave `lastExportedAt` unchanged;
* use one logical export timestamp for filename date resolution and successful `lastExportedAt` recording;
* the Profile version that passed the preview gate must remain current through export completion;
* a concurrent CV-data mutation or preview invalidation must prevent stale export success and must not update `lastExportedAt`;
* do not hold unnecessary database locks/transactions across expensive document rendering when current-version validation can safely preserve the invariant;
* successful export must not reset or otherwise mutate preview state;
* reuse existing CV Profile access/authorization behavior rather than duplicating ownership/RBAC rules.

Out of scope:

* PDF or DOCX renderer internals;
* CV assembly/content rules;
* filename-resolution algorithm;
* File Name Format CRUD or builder APIs;
* changing Profile preferred File Name Format;
* Preview API implementation;
* Dashboard aggregation.

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
