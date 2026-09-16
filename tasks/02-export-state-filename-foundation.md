Read `AGENTS.md`.

Plan Export State & FileName Foundation for the current CV Preview & Export module.

Start discovery around:

* current Profile export/preview state and mutation lifecycle;
* current Profile creation and copy behavior;
* existing master-data entity/repository/migration conventions;
* current Flyway migration state;
* focused Profile/domain/persistence tests relevant to these concerns.

Read only:

* `docs/PRODUCT.md`;
* `docs/ARCHITECTURE.md`;
* `docs/DATA.md`;
* `docs/TESTING.md`.

Follow task-related source-code dependencies discovered from these areas as needed. Keep discovery inside Profile export state, File Name Format foundation, and directly affected persistence behavior.

Plan these capabilities:

* persist `lastExportedAt` as the latest successful export timestamp;
* provide Profile domain behavior needed by later preview/export orchestration;
* introduce persistent File Name Format master-data foundation;
* allow a Profile to optionally reference a preferred File Name Format;
* provide one seeded system-default File Name Format for later fallback resolution.

Task-specific constraints:

* preserve the existing `hasPreviewed` invalidation behavior already owned by Profile mutations;
* `lastExportedAt` starts null and must not be changed by preview or ordinary Profile edits;
* File Name Format is persistent master data, not an enum;
* its foundation must support identity, name, pattern, default designation, and normal audit timestamps following current conventions;
* supported pattern components are `LastName`, `FirstName`, `Role`, and `Date`; do not implement runtime token resolution in this task;
* exactly one usable system default must exist after the migration;
* the Profile preferred-format relationship is nullable;
* a null Profile preference means later resolution may fall back to the system default;
* existing Profiles must remain valid without requiring a preferred format;
* copying a Profile copies CV content only: the new Profile starts with `hasPreviewed=false`, `lastExportedAt=null`, and no preferred File Name Format;
* use new Flyway migration(s); do not modify already-applied migrations.

Out of scope:

* File Name Format CRUD;
* filename/token resolution or sanitization;
* PDF/DOCX generation;
* Preview or Export APIs;
* authorization;
* updating `lastExportedAt` as part of an export use case;
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
