Read `AGENTS.md`.

Plan Skill + Seniority Search for the Member Management & Search module.

Start discovery around:

* the Member List read model and pagination conventions introduced by Task 01;
* Profile, ProfileSkill, Skill, and Seniority relationships;
* current Seniority range semantics and Profile soft-delete behavior;
* focused repository/service/controller tests around Profile Skills and master data.

Read only:

* `docs/PRODUCT.md`;
* `docs/API.md`;
* `docs/DATA.md`;
* `docs/TESTING.md`.

Follow task-related source dependencies as needed. Keep discovery inside Member skill search and existing Profile Skill/Seniority foundations.

Plan these capabilities:

* search Members by one or more Skill + Seniority pairs;
* require all pairs to match within the same active Profile;
* derive Seniority dynamically from each ProfileSkill experience value;
* return deduplicated, paginated Members with the Profiles that satisfy the full filter set.

The required API contract is fixed:

`GET /api/members/search-by-skill`

Only `MANAGER` and `ADMIN` may access it.

Query parameters:

* required `skillIds`;
* required `seniorityIds`;
* optional `status=ACTIVE|INACTIVE`;
* `page`, default `0`;
* `size`, default `10`.

`skillIds` and `seniorityIds` pair by index, must be non-empty and equal-length.

Task-specific constraints:

* every requested pair uses AND semantics and must be satisfied by one same Profile;
* only Profiles with `deletedAt = null` participate;
* one Member appears once even when multiple Profiles match;
* inactive Members remain eligible unless filtered otherwise;
* Seniority uses `ProfileSkill.experienceYears` against the current Seniority range `[fromExperience, toExperience)`, with null upper bound meaning unbounded;
* do not use Profile overall experience or persist Seniority on ProfileSkill;
* duplicate skill IDs, missing Skill IDs, missing Seniority IDs, or malformed pair lists return `400`;
* paginate and order at Member level using Task 01 conventions;
* reuse the Task 01 Member summary fields and add only matching active Profile evidence;
* no schema change is expected.

Out of scope:

* Language + Level search;
* combined Skill/Language search;
* Member Profile mutation or access changes;
* Master Data mutation;
* CV Preview/Export.

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

