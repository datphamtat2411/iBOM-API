Read `AGENTS.md`.

Plan CV Document Model & Assembler for the current CV Preview & Export module.

Start discovery around:

* the current Profile aggregate and all CV-owned sections;
* current retrieval, mapping, and display-order behavior for Education, Languages, Certificates, Projects, and Skills;
* shared Language/Skill master-data relationships needed to produce complete CV content;
* current package/module conventions and focused tests around Profile data.

Read only:

* `docs/PRODUCT.md`;
* `docs/ARCHITECTURE.md`;
* `docs/DATA.md`;
* `docs/TESTING.md`.

Follow task-related source-code dependencies discovered from these areas as needed. Keep discovery inside CV document assembly and existing Profile data access.

Plan these capabilities:

* define one canonical, renderer-independent CV document model shared by future PDF and DOCX generation;
* assemble the latest saved Profile data into that model;
* include Personal/About Me data, technical summary, personality, Education, Languages, Certificates, Projects, and Skills;
* carry the master-data values required for rendering, including Language names and Skill names/categories;
* preserve current established section-level ordering semantics;
* represent empty list sections cleanly so later renderers can omit both heading and content without re-querying or re-deciding business rules.

Task-specific constraints:

* the canonical model must not expose JPA entities as the renderer contract;
* keep the model document-oriented but free of PDF, HTML, Thymeleaf, Apache POI, CSS, HTTP, and filename concerns;
* PDF and DOCX renderers must be able to consume the same assembled model without querying Profile repositories or duplicating CV-content rules;
* do not add new CV-specific sorting rules where current requirements or implementation do not already define ordering;
* do not mutate Profile state while assembling;
* authorization remains outside the assembler; later Preview/Export orchestration owns actor/ownership checks;
* no database schema change is expected for this task.

Out of scope:

* PDF or DOCX rendering;
* templates or document styling;
* Preview/Export APIs;
* `hasPreviewed` or `lastExportedAt` changes;
* File Name Format or filename resolution;
* export authorization or HTTP response behavior.

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
