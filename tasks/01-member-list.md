Read `AGENTS.md`.

Plan Member List for the Member Management & Search module.

Start discovery around:

* user-account role/status persistence and Profile relationships;
* pagination/API response conventions;
* Manager/Admin authorization patterns;
* focused tests around users, Profiles, security, and paginated reads.

Read only:

* `docs/PRODUCT.md`;
* `docs/API.md`;
* `docs/DATA.md`;
* `docs/TESTING.md`.

Follow task-related source dependencies as needed. Keep discovery inside Member listing and read-only Profile aggregation.

Plan these capabilities:

* expose a paginated Manager/Admin Member list;
* support optional username/email search and account-status filtering;
* return a stable Member summary reusable by later search tasks;
* derive active Profile count and Member last-updated time without exposing deleted Profile data.

The required API contract is fixed:

`GET /api/members`

Bearer JWT required. Only `MANAGER` and `ADMIN` may access it; `MEMBER` is forbidden.

Query parameters:

* `page`, default `0`;
* `size`, default `10`;
* optional `search`;
* optional `status=ACTIVE|INACTIVE`.

Task-specific constraints:

* Member means a user account with role `MEMBER`;
* absent `status`, both active and inactive Members are eligible;
* `search` is trimmed, case-insensitive contains matching on username or email; blank behaves as absent;
* each item exposes `id`, `username`, `email`, `status`, `activeProfileCount`, and `lastUpdatedAt`;
* `activeProfileCount` counts only Profiles with `deletedAt = null`;
* `lastUpdatedAt` is the latest User Account `updatedAt` or Profile `updatedAt`, including soft-deleted Profiles; no deleted Profile data is exposed;
* default order is active before inactive, then username case-insensitive ascending, then id ascending;
* out-of-range pages recover to the last valid page; an empty dataset returns a valid empty page;
* do not derive or expose Member-level full name or job title;
* reuse existing API envelope/pagination conventions; no schema change is expected.

Out of scope:

* Skill/Seniority or Language/Level search;
* Member Profile access or mutation;
* Profile Section management;
* CV Preview/Export;
* account activation/deactivation or Member creation.

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

