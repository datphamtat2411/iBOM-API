Read `AGENTS.md`.

Plan Language + Level Search for the Member Management & Search module.

Start discovery around:

* the Member List read model and pagination conventions introduced by Task 01;
* Profile, ProfileLanguage, Language, and LanguageLevel relationships;
* Profile soft-delete behavior;
* focused repository/service/controller tests around Profile Languages and master data.

Read only:

* `docs/PRODUCT.md`;
* `docs/API.md`;
* `docs/DATA.md`;
* `docs/TESTING.md`.

Follow task-related source dependencies as needed. Keep discovery inside Member language search and existing Profile Language foundations.

Plan these capabilities:

* search Members by one or more Language + Level pairs;
* require all pairs to match within the same active Profile;
* match LanguageLevel exactly;
* return deduplicated, paginated Members with the Profiles that satisfy the full filter set.

The required API contract is fixed:

`GET /api/members/search-by-language`

Only `MANAGER` and `ADMIN` may access it.

Query parameters:

* required `languageIds`;
* required `levels`;
* optional `status=ACTIVE|INACTIVE`;
* `page`, default `0`;
* `size`, default `10`.

`languageIds` and `levels` pair by index, must be non-empty and equal-length.

Task-specific constraints:

* every requested pair uses AND semantics and must be satisfied by one same Profile;
* only Profiles with `deletedAt = null` participate;
* one Member appears once even when multiple Profiles match;
* inactive Members remain eligible unless filtered otherwise;
* LanguageLevel matching is exact; do not introduce ordering or minimum-level semantics;
* supported levels remain `BEGINNER`, `INTERMEDIATE`, `UPPER_INTERMEDIATE`, `ADVANCED`, `NATIVE`;
* duplicate language IDs, missing Language IDs, invalid levels, or malformed pair lists return `400`;
* paginate and order at Member level using Task 01 conventions;
* reuse the Task 01 Member summary fields and add only matching active Profile evidence;
* do not combine Skill and Language criteria in one search;
* no schema change is expected.

Out of scope:

* Skill + Seniority search;
* combined Skill/Language search;
* Member Profile access or mutation;
* Profile Section mutation;
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

