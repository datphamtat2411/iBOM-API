Read `AGENTS.md`.

Plan Skill Master Management for the current Master Data Management module.

Start discovery around:

* the existing Skill and Skill Category master-data entities, repositories, service, controller, DTOs, migrations, and focused tests;
* current Skill list/search/pagination behavior and common API response/error conventions;
* Profile Skill references needed to enforce safe Skill deletion;
* current authentication/RBAC patterns for role-restricted mutations.

Read only:

* `docs/PRODUCT.md`;
* `docs/API.md`;
* `docs/DATA.md`;
* `docs/ARCHITECTURE.md`;
* `docs/TESTING.md`.

Follow task-related source-code dependencies discovered from these areas as needed. Keep discovery inside Skill Master Management and its direct Category/reference/security boundaries.

Plan these capabilities:

* preserve the existing paginated Skill read/search behavior;
* add Skill create, update, and delete management;
* require each Skill to reference one existing controlled Skill Category;
* enforce Skill-name validation and case-insensitive uniqueness;
* reject deletion when a Skill is referenced by Profile Skill data;
* enforce read access for authenticated MEMBER/MANAGER/ADMIN and mutation access only for MANAGER/ADMIN.

The required API contract is fixed:

```text
GET    /api/master/skills
POST   /api/master/skills
PUT    /api/master/skills/{skillId}
DELETE /api/master/skills/{skillId}
```

For GET, preserve `page`, `size`, and optional `search`; defaults remain `page=0`, `size=10`. Search trims the keyword and performs case-insensitive Skill-name containment. Results remain ordered by Skill Name ascending, case-insensitively.

Task-specific constraints:

* Skill Name is required, trimmed before validation/persistence, and unique case-insensitively;
* Category is required and must resolve to an existing controlled Skill Category;
* preserve the current seven seeded Skill Categories and the existing lookup-table/FK foundation;
* Skill mutations must not create or modify Skill Categories;
* duplicate names, including case-only variants, must be rejected;
* unknown Skill or Category references must fail appropriately;
* deleting a referenced Skill must be rejected; never cascade-delete Profile Skill data;
* MEMBER must receive `403` for POST/PUT/DELETE; MANAGER and ADMIN may mutate;
* keep the existing common response/error conventions.

Out of scope:

* independent Skill Category CRUD management;
* Profile Skill CRUD, experienceYears, or lastUsed behavior;
* Seniority derivation or search;
* Dashboard aggregation;
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
