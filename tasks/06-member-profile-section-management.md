Read `AGENTS.md`.

Plan Member Profile Section Management for the Member Management & Search module.

Start discovery around:

* the reusable Profile access foundation introduced by Task 02;
* existing Education, Language, Certificate, Project, and Skill controllers/services;
* Profile-level optimistic concurrency, Preview invalidation, and child-to-Profile ownership checks;
* focused section CRUD/security tests.

Read only:

* `docs/PRODUCT.md`;
* `docs/API.md`;
* `docs/ARCHITECTURE.md`;
* `docs/DATA.md`;
* `docs/TESTING.md`.

Follow task-related source dependencies as needed. Keep discovery inside existing Profile Section read/mutation behavior and management authorization.

Plan Manager/Admin access to existing Profile Section APIs for:

* Education;
* Languages;
* Certificates;
* Projects;
* Skills.

Do not add Member-specific section routes. Preserve the existing `/api/profiles/{profileId}/...` contracts.

Task-specific constraints:

* preserve existing owner self-service behavior;
* Manager/Admin may access sections only when the parent Profile belongs to a `MEMBER`;
* inactive Member Profiles remain manageable;
* soft-deleted, unauthorized, or missing Profiles remain `404`;
* reuse the Task 02 Profile access mechanism rather than duplicating role checks across section services;
* child update/delete must verify the child belongs to the authorized parent Profile;
* preserve every section’s existing validation, master-data, duplicate, ordering, and error semantics;
* preserve Profile-level optimistic concurrency for all section mutations;
* stale Profile versions remain `409`;
* successful create/update/delete must advance Profile version and invalidate Preview state;
* read/list operations must not advance version or invalidate Preview;
* Manager/Admin authorization must not bypass section business rules;
* no schema change is expected.

Keep existing domain behavior, including Education date/status rules, controlled Language levels and per-Profile uniqueness, Certificate validation/uniqueness, Project validation, and Skill Master/experience/last-used rules.

Out of scope:

* Profile/About Me mutation;
* Profile create/copy/delete;
* Master Data mutation;
* Member search;
* CV Preview/Export;
* completeness redesign.

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

