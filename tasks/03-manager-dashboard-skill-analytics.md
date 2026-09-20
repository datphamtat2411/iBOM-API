Read `AGENTS.md`.

Plan Manager Dashboard Skill Analytics for the current Dashboard module.

Start discovery from:

* the Manager Dashboard foundation and eligible-Profile population established by Task 02;
* current ProfileSkill, Skill, and SkillCategory relationships and repositories;
* existing Profile Skill ordering behavior, without assuming display ordering defines Primary Skill;
* focused Manager Dashboard and Profile Skill tests.

From these anchors, follow only task-related dependencies needed for Manager Dashboard skill analytics. Prefer existing collaborators and tests before broad repository search.

Read only:

* `docs/PRODUCT.md`;
* `docs/ARCHITECTURE.md`;
* `docs/DATA.md`;
* `docs/API.md`;
* `docs/TESTING.md`.

Plan these capabilities:

* extend `GET /api/dashboard/manager-stats` with Primary Skill and Skill Category distributions;
* derive exactly one Primary Skill per eligible Profile;
* provide deterministic Top 7 plus remainder analytics;
* derive Category analytics from each Profile's selected Primary Skill.

Task-specific constraints:

* reuse Task 02 eligibility exactly; do not redefine the Manager Dashboard population;
* Primary Skill precedence is `experienceYears DESC`, then `lastUsed DESC NULLS LAST`, then Skill Name ascending case-insensitively, then Skill ID ascending;
* Profiles without Skills do not contribute to either distribution;
* Primary Skill distribution orders by `profileCount DESC`, then Skill Name ascending, keeps Top 7, and exposes the remaining count separately as `otherProfileCount`;
* Category distribution uses only each Profile's Primary Skill Category, not all Profile Skills;
* Category percentage denominator is the number of Profiles contributing a Primary Skill and is rounded to the nearest whole number;
* unavailable Category data contributes to `Uncategorized` without failing the dashboard;
* apply the same deterministic Top 7 plus separate Others semantics to Category distribution;
* empty analytics returns successful empty distributions;
* avoid obvious per-Profile query fan-out;
* keep completion statistics, authorization, and endpoint identity unchanged.

Out of scope:

* changing Manager Dashboard eligibility or completion semantics;
* Member Dashboard changes;
* Profile Skill CRUD or master-data management;
* Dashboard persistence, caching, or schema changes.

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

