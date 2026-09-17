Read `AGENTS.md`.

Plan Seniority Master Management for the current Master Data Management module.

Start discovery around:

* current Master Data package, persistence, API, error, and testing conventions;
* Profile Skill persistence and `experienceYears`, only as needed for Seniority derivation and delete-in-use checks;
* existing authentication/RBAC patterns for Master Data mutations;
* current Flyway migration state and focused Master Data tests.

Read only:

* `docs/PRODUCT.md`;
* `docs/API.md`;
* `docs/DATA.md`;
* `docs/ARCHITECTURE.md`;
* `docs/TESTING.md`.

Follow task-related source-code dependencies discovered from these areas as needed. Keep discovery inside Seniority Master Management and its direct Profile Skill/security boundaries.

Plan these capabilities:

* introduce the Seniority persistence and application foundation;
* provide Seniority create, read, update, and delete management;
* enforce Seniority-name and experience-range invariants;
* reject overlapping ranges and multiple Unlimited ranges;
* protect deletion of Seniority ranges currently classifying Profile Skill data;
* enforce read access for MEMBER/MANAGER/ADMIN and mutation access only for MANAGER/ADMIN.

The required API contract is fixed:

```text
GET    /api/master/seniority
POST   /api/master/seniority
PUT    /api/master/seniority/{seniorityId}
DELETE /api/master/seniority/{seniorityId}
```

Mutation data contains `name`, `fromExperience`, and nullable `toExperience`. Lists are ordered by minimum experience ascending.

Task-specific constraints:

* Name is required, trimmed before validation/persistence, and unique case-insensitively;
* `fromExperience` is required and must be `>= 0`;
* `toExperience = null` means Unlimited; otherwise it must be greater than `fromExperience`;
* ranges use `[fromExperience, toExperience)` semantics; touching boundaries are valid, overlapping ranges are rejected;
* only one Unlimited range may exist, and it must begin at or above the highest finite upper boundary;
* do not require ranges to be continuous or gap-free;
* Seniority is derived dynamically from `ProfileSkill.experienceYears`; never persist `seniorityId` on Profile Skill;
* a Seniority is “in use” when any Profile Skill experience value falls within its current range; deleting such a Seniority must be rejected;
* updating a Seniority may reclassify Profile Skills as long as all Seniority invariants remain valid;
* MEMBER must receive `403` for POST/PUT/DELETE; MANAGER and ADMIN may mutate;
* keep common response/error conventions.

Out of scope:

* Member Search implementation;
* Profile Skill CRUD changes;
* Dashboard behavior;
* Skill Master changes;
* persisted Seniority assignments.

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
