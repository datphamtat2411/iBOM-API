Read `AGENTS.md`.

Plan Language Master Management for the current Master Data Management module.

Start discovery around:

* the existing Language master-data entity, repository, service, controller, DTO, migration, and focused tests;
* current Language list/search/pagination behavior and common API response/error conventions;
* Profile Language references needed to enforce safe Language deletion;
* current authentication/RBAC patterns for role-restricted mutations.

Read only:

* `docs/PRODUCT.md`;
* `docs/API.md`;
* `docs/DATA.md`;
* `docs/ARCHITECTURE.md`;
* `docs/TESTING.md`.

Follow task-related source-code dependencies discovered from these areas as needed. Keep discovery inside Language Master Management and its direct referential/security boundaries.

Plan these capabilities:

* preserve the existing paginated Language read/search behavior;
* add Language create, update, and delete management;
* enforce Language-name validation and case-insensitive uniqueness;
* reject deletion when a Language is referenced by Profile Language data;
* enforce read access for authenticated MEMBER/MANAGER/ADMIN and mutation access only for MANAGER/ADMIN.

The required API contract is fixed:

```text
GET    /api/master/languages
POST   /api/master/languages
PUT    /api/master/languages/{languageId}
DELETE /api/master/languages/{languageId}
```

For GET, preserve `page`, `size`, and optional `search`; defaults remain `page=0`, `size=10`. Search trims the keyword and performs case-insensitive name containment. Results remain ordered by Language Name ascending, case-insensitively.

Task-specific constraints:

* Language Name is required, trimmed before validation/persistence, and unique case-insensitively;
* duplicate names must be rejected, including case-only variants;
* unknown update/delete IDs must fail as not found;
* deleting a referenced Language must be rejected; never cascade-delete Profile Language data;
* preserve the existing `languages` persistence foundation and database uniqueness protection rather than redesigning the table;
* MEMBER must receive `403` for POST/PUT/DELETE; MANAGER and ADMIN may mutate;
* keep the existing common response/error conventions.

Out of scope:

* Profile Language CRUD or Language Level changes;
* Member language search;
* Profile completeness or preview-state behavior;
* CV rendering/export behavior;
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
