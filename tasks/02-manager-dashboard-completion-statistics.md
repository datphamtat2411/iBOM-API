Read `AGENTS.md`.

Plan Manager Dashboard Foundation & Completion Statistics for the current Dashboard module.

Start discovery from:

* the current Profile completeness service and its focused tests;
* Profile/User persistence paths exposing Profile ownership, soft delete, User role, and User status;
* current authorization patterns for Manager/Admin-only APIs;
* the Member Dashboard implementation from Task 01 where useful for Dashboard module conventions.

From these anchors, follow only task-related dependencies needed for Manager Dashboard completion aggregation. Prefer existing collaborators and tests before broad repository search.

Read only:

* `docs/PRODUCT.md`;
* `docs/ARCHITECTURE.md`;
* `docs/DATA.md`;
* `docs/API.md`;
* `docs/TESTING.md`.

Plan these capabilities:

* establish the Manager Dashboard API and reusable eligible-Profile population;
* report completed Profiles and total Profiles;
* preserve canonical Profile completeness semantics;
* provide a foundation that later Manager Skill Analytics can extend without redefining eligibility.

The required API contract is fixed:

`GET /api/dashboard/manager-stats`

Task-specific constraints:

* only `MANAGER` and `ADMIN` may access the endpoint; `MEMBER` receives `403`;
* an eligible Profile is non-deleted, owned by a `MEMBER`, and owned by a user whose status is `ACTIVE`;
* Manager/Admin-owned Profiles are excluded;
* `totalProfiles` counts eligible Profiles, not Users;
* `completedProfiles` counts eligible Profiles satisfying the canonical Profile completeness definition;
* do not duplicate or redefine completeness rules inside Dashboard;
* eligibility semantics must be reusable by later Manager Skill Analytics;
* avoid an obvious per-Profile query fan-out when computing dashboard-wide completeness while preserving canonical semantics;
* no eligible Profiles returns `200` with both counts equal to `0`;
* keep the operation read-only;
* no Dashboard persistence or schema change is expected.

Out of scope:

* Primary Skill selection or distribution;
* Skill Category analytics, percentages, Top 7, Others, or Uncategorized;
* Member Dashboard changes;
* frontend behavior or caching;
* unrelated completeness redesign.

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

