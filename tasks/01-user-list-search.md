Read `AGENTS.md`.

Plan User List & Search for the current User Management module.

Start discovery from:

* `UserAccountRepository` and the existing `UserAccount` model;
* the current Member list read flow as a pagination/query precedent;
* `SecurityConfig`;
* existing focused list, pagination, and authorization tests.

Use these as entry points, not an exhaustive file list. Follow only task-related source dependencies as needed. Do not perform broad repository discovery.

Read only:

* `docs/PRODUCT.md`;
* `docs/API.md`;
* `docs/ARCHITECTURE.md`;
* `docs/TESTING.md`.

Plan these capabilities:

* expose a paginated User Management list;
* search users by username or email;
* filter by one or more roles;
* return only canonical account-level user data;
* enforce Manager/Admin-only access.

The required API contract is fixed:

`GET /api/users`

Query parameters:

* `page`, default `0`;
* `size`, default `10`;
* optional `search`;
* optional repeatable `role` using only `MEMBER`, `MANAGER`, `ADMIN`.

Successful items expose:

* `id`;
* `username`;
* `email`;
* `role`;
* `status`.

Task-specific constraints:

* `MANAGER` and `ADMIN` have equal access; `MEMBER` must be forbidden;
* search is trimmed, blank means no search, and matching is case-insensitive substring over username or email;
* multiple role values use OR semantics; omitting role returns all roles;
* default ordering is `ACTIVE` before `INACTIVE`, then username ascending case-insensitively, then id ascending;
* include inactive users;
* do not derive Full Name, Job Title, or other identity data from Profiles;
* filtering, ordering, and pagination must operate at the persistence query level;
* reuse the existing common paginated response conventions;
* no schema change is expected.

Out of scope:

* managed User creation;
* activate/deactivate behavior or session revocation;
* existing-user role changes;
* Profile or Member Management changes;
* Dashboard behavior.

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

