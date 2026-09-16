Read `AGENTS.md`.

Plan File Name Resolution for the current CV Preview & Export module.

Start discovery around:

* the File Name Format foundation and Profile preferred-format relationship introduced by the current export-state foundation;
* current Profile fields used by filename placeholders;
* current application time/Clock conventions;
* package and error-handling patterns appropriate for reusable business-resolution services;
* focused tests around Profile and master-data behavior relevant to this task.

Read only:

* `docs/PRODUCT.md`;
* `docs/ARCHITECTURE.md`;
* `docs/DATA.md`;
* `docs/TESTING.md`.

Follow task-related source-code dependencies discovered from these areas as needed. Keep discovery inside filename-format selection and filename generation behavior.

Plan these capabilities:

* resolve the applicable File Name Format for an export;
* apply precedence: explicit export selection, then Profile preferred format, then system default;
* interpret supported pattern components in stored order;
* resolve `LastName`, `FirstName`, `Role`, and `Date`;
* map `Role` to Profile job title and `Date` to the current export date using `yyyyMMdd`;
* produce a safe final filename with the correct PDF or DOCX extension;
* validate that all Profile values required by the selected pattern are usable.

Task-specific constraints:

* an invalid explicit File Name Format selection must fail and must not silently fall back;
* a missing Profile preference may fall back to the system default;
* absence of a usable system default must fail clearly rather than use a hidden hard-coded pattern;
* do not mutate the Profile preferred format when an explicit one-time export format is used;
* preserve stored token order;
* do not introduce new filename placeholders or arbitrary pattern syntax;
* normalize resolved values only as needed for safe filename use; do not transliterate or rewrite Profile data;
* reject unsupported or invalid persisted patterns instead of producing a malformed filename;
* use the project’s existing Clock/time convention rather than scattered direct system-time calls;
* filename resolution must not depend on authorization, preview state, document rendering, HTTP response handling, or export-state mutation.

Out of scope:

* File Name Format CRUD or builder APIs;
* changing Profile preferred File Name Format;
* PDF/DOCX rendering;
* Preview or Export APIs;
* authorization;
* `hasPreviewed` or `lastExportedAt` behavior;
* Content-Disposition or binary response construction.

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
