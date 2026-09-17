Read `AGENTS.md`.

Plan File Name Format Master Management for the current Master Data Management module.

Start discovery around:

* the existing File Name Format entity, repository, migration, and focused tests;
* current Profile preferred-format relationship and delete-reference boundary;
* existing filename resolution/default-selection behavior used by CV export;
* current Master Data API, validation, error, pagination, and RBAC conventions.

Read only:

* `docs/PRODUCT.md`;
* `docs/API.md`;
* `docs/DATA.md`;
* `docs/ARCHITECTURE.md`;
* `docs/TESTING.md`.

Follow task-related source-code dependencies discovered from these areas as needed. Keep discovery inside File Name Format management and its direct Profile/export compatibility boundaries.

Plan these capabilities:

* provide paginated File Name Format listing;
* add create, update, and delete management;
* enforce Format Name and controlled filename-pattern validation;
* reject deletion when a format is referenced by any Profile;
* preserve and protect the existing system-default format;
* enforce read access for MEMBER/MANAGER/ADMIN and mutation access only for MANAGER/ADMIN.

The required API contract is fixed:

```text
GET    /api/master/file-name-formats
POST   /api/master/file-name-formats
PUT    /api/master/file-name-formats/{fileNameFormatId}
DELETE /api/master/file-name-formats/{fileNameFormatId}
```

For GET, use `page` and `size` with defaults `page=0`, `size=10`. Results are ordered by Format Name ascending, case-insensitively.

Task-specific constraints:

* Format Name is required, trimmed before validation/persistence, and unique case-insensitively;
* patterns are controlled definitions, not unrestricted free text;
* supported placeholders are exactly `LastName`, `FirstName`, `Role`, and `Date`;
* a pattern must contain at least one placeholder and must not repeat the same placeholder;
* preserve compatibility with the existing `{Token}` pattern representation and filename resolver;
* preserve the existing `is_default` persistence foundation and the current system default;
* create/update management must not introduce client-controlled default switching;
* the current default format must not be deletable;
* deleting a format referenced by a Profile must be rejected;
* preserve export selection precedence: explicit format → Profile preferred format → system default;
* MEMBER must receive `403` for POST/PUT/DELETE; MANAGER and ADMIN may mutate;
* keep existing common response/error conventions.

Out of scope:

* set/change-default APIs;
* Profile preferred-format mutation flows;
* CV Preview or Export API changes;
* filename resolution/sanitization redesign;
* preview/export state changes;
* unrelated Master Data resources.

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
