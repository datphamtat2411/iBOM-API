Read `AGENTS.md`.

Plan PDF Renderer for the current CV Preview & Export module.

Start discovery around:

* the canonical CV document model produced by the current CV assembly layer;
* current application resource/template conventions;
* build dependencies and document-generation integration points;
* focused tests and test-resource patterns suitable for generated binary documents.

Read only:

* `docs/PRODUCT.md`;
* `docs/ARCHITECTURE.md`;
* `docs/TESTING.md`.

Follow task-related source-code dependencies discovered from these areas as needed. Keep discovery inside PDF rendering and the canonical CV document contract.

Plan these capabilities:

* render the canonical CV document model into a valid PDF;
* use Thymeleaf to produce HTML and Flying Saucer to convert that HTML to PDF;
* provide reusable PDF rendering behavior consumable by both future Preview and PDF Export flows;
* render all supported CV content supplied by the canonical model;
* omit empty collection sections, including their headings;
* support Vietnamese and other expected Unicode text through bundled/local font handling;
* provide print-oriented template/CSS/resource structure suitable for A4 CV output.

Task-specific constraints:

* the renderer must depend on the canonical CV document model, not JPA entities, repositories, Profile services, or HTTP state;
* do not query or reassemble Profile data inside the renderer;
* do not redefine CV content selection or ordering already owned by the canonical model;
* Preview and PDF Export must later reuse the same renderer;
* do not rely on external network resources or OS-installed fonts;
* generation failures must not silently return empty or partial PDF output;
* add required Thymeleaf/Flying Saucer dependencies using versions compatible with the current project;
* focused tests must exercise real PDF generation and verify non-empty valid PDF output plus representative expected content/section behavior;
* do not require pixel-perfect or byte-for-byte PDF comparison.

Out of scope:

* DOCX generation;
* Preview or Export APIs;
* authorization;
* Profile lookup or assembly;
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
