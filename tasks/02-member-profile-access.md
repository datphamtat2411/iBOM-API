Read `AGENTS.md`.

Plan Member Profile Read & Access Foundation for the Member Management & Search module.

Start discovery around:

* current Profile ownership, retrieval, soft-delete, and summary behavior;
* current authentication/authorization boundaries for Profile access;
* User Account role/status relationships;
* focused Profile controller/service/security tests.

Read only:

* `docs/PRODUCT.md`;
* `docs/API.md`;
* `docs/ARCHITECTURE.md`;
* `docs/DATA.md`;
* `docs/TESTING.md`.

Follow task-related source dependencies as needed. Keep discovery inside Member Profile read access and reusable Profile authorization boundaries.

Plan these capabilities:

* list active Profiles belonging to a Member;
* allow Manager/Admin to read an active Member Profile through the existing Profile detail surface;
* establish one reusable access mechanism for later Member Profile mutation and CV-access tasks;
* preserve existing owner self-service Profile access.

The required API contracts are fixed:

`GET /api/members/{memberId}/profiles`

Only `MANAGER` and `ADMIN` may access it. Return active/non-deleted Profiles ordered by `updatedAt DESC`, then `id DESC`. Reuse the established Profile summary contract where appropriate.

`GET /api/profiles/{profileId}`

Keep the existing route. Access is allowed when the actor owns the Profile, or when the actor is `MANAGER`/`ADMIN` and the Profile owner has role `MEMBER`.

Task-specific constraints:

* inactive Members remain manageable and their active Profiles remain readable by Manager/Admin;
* soft-deleted Profiles are never listable or readable;
* management access must not grant access to Profiles owned by Manager/Admin accounts;
* Member Profile listing with a missing or non-MEMBER `memberId` returns `404`;
* valid Members with no active Profiles return `200` with an empty list;
* unauthorized, missing, or soft-deleted Profile detail must remain indistinguishable as `404`;
* do not duplicate Profile detail APIs under `/api/members`;
* design the access boundary so later tasks can reuse the same semantics without duplicating authorization logic;
* no schema change is expected.

Out of scope:

* Profile create/copy/update/delete;
* Profile Section mutation;
* Member search;
* CV Preview/Export;
* account lifecycle management.

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

