Read `AGENTS.md`.

Plan DOCX Renderer for the current CV Preview & Export module.

Start discovery around:

* the canonical CV document model produced by the current CV assembly layer;
* current build dependency and resource conventions;
* package structure appropriate for document-generation components;
* focused test patterns suitable for generated binary documents.

Read only:

* `docs/PRODUCT.md`;
* `docs/ARCHITECTURE.md`;
* `docs/TESTING.md`.

Follow task-related source-code dependencies discovered from these areas as needed. Keep discovery inside DOCX rendering and the canonical CV document contract.

Plan these capabilities:

* render the canonical CV document model into a valid DOCX document;
* use Apache POI directly for DOCX generation;
* provide reusable DOCX rendering behavior consumable by the future Export flow;
* render all supported CV content supplied by the canonical model;
* preserve collection ordering already established by the canonical model;
* omit empty collection sections, including their headings;
* provide professional, readable document structure with appropriate headings, paragraphs, spacing, bullets, and other Word-specific presentation;
* preserve Vietnamese and other expected Unicode text.

Task-specific constraints:

* the renderer must depend on the canonical CV document model, not JPA entities, repositories, Profile services, or HTTP state;
* do not query or reassemble Profile data inside the renderer;
* do not redefine CV content selection or ordering already owned by the canonical model;
* generate DOCX directly from the canonical model using Apache POI; do not convert from HTML or PDF;
* PDF and DOCX may differ in presentation but must preserve the same business content;
* do not introduce embedded-font complexity unless required by the current repository or document-generation constraints;
* generation failures must not silently return empty or partial DOCX output;
* add the required Apache POI dependency using a version compatible with the current project;
* focused tests must generate real DOCX bytes, reopen them with Apache POI, and verify representative expected content, section presence/absence, ordering, and Unicode preservation;
* do not require pixel-perfect or byte-for-byte document comparison.

Out of scope:

* PDF generation;
* Profile lookup or assembly;
* Preview or Export APIs;
* authorization;
* `hasPreviewed` or `lastExportedAt` changes;
* filename resolution;
* Content-Disposition or download response behavior.

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
