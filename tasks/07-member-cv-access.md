Read `AGENTS.md`.

Plan Member CV Preview & Export Access for the Member Management & Search module.

Start discovery around:

* the reusable Profile access foundation introduced by Task 02;
* existing CV Preview and Export controllers/services;
* Preview-before-Export state, optimistic concurrency, and Profile version handling;
* filename resolution and existing PDF/DOCX rendering flow;
* focused CV access/state/security tests.

Read only:

* `docs/PRODUCT.md`;
* `docs/API.md`;
* `docs/ARCHITECTURE.md`;
* `docs/DATA.md`;
* `docs/TESTING.md`.

Follow task-related source dependencies as needed. Keep discovery inside CV access integration and existing Preview/Export state behavior.

Plan these capabilities:

* allow Manager/Admin to Preview an existing Member Profile CV;
* allow Manager/Admin to Export an existing Member Profile CV;
* reuse the Task 02 Profile access mechanism;
* preserve all existing CV rendering, filename, Preview-state, and concurrency behavior.

The required API contracts remain:

`GET /api/cv/preview/{profileId}`

`GET /api/cv/download/{profileId}` with existing `format` and optional `fileNameFormatId` parameters.

Do not add Member-specific CV routes.

Task-specific constraints:

* preserve existing owner self-service behavior;
* Manager/Admin may access CV operations only when the Profile owner has role `MEMBER`;
* inactive Member Profiles remain eligible;
* missing, unauthorized, or soft-deleted Profiles must be indistinguishable as `404`;
* management privilege must not grant cross-user access to Manager/Admin-owned Profiles;
* use one canonical Profile access source rather than maintaining separate CV authorization semantics;
* successful Preview continues to mark the target Profile as previewed;
* Preview state belongs to the Profile, not the actor or session;
* Export still requires a valid Preview and Manager/Admin cannot bypass that prerequisite;
* Profile or Section mutation continues to invalidate Preview state;
* preserve current Profile-version conflict protection during Preview and Export;
* preserve existing PDF/DOCX rendering, document assembly, filename precedence, response headers, and `lastExportedAt` behavior;
* no schema change is expected.

Out of scope:

* new CV endpoints;
* renderer/layout/content changes;
* filename-resolution redesign;
* new export formats;
* Profile or Section mutation;
* Member search;
* actor-specific Preview state.

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

