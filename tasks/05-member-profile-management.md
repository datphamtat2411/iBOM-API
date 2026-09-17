Read `AGENTS.md`.

Plan Member Profile Edit & Delete Management for the Member Management & Search module.

Start discovery around:

* the reusable Profile access foundation introduced by Task 02;
* existing Profile update/delete, optimistic locking, soft-delete, and active-name uniqueness behavior;
* Profile ownership and owner-scoped locking/counting;
* focused Profile service/controller/security tests.

Read only:

* `docs/PRODUCT.md`;
* `docs/API.md`;
* `docs/ARCHITECTURE.md`;
* `docs/DATA.md`;
* `docs/TESTING.md`.

Follow task-related source dependencies as needed. Keep discovery inside existing Profile root mutation and management authorization.

Plan these capabilities:

* allow Manager/Admin to update an existing Member Profile;
* allow Manager/Admin to soft-delete an existing Member Profile;
* preserve existing owner self-service behavior and all Profile invariants;
* reuse the Task 02 Profile access mechanism rather than duplicating authorization logic.

The required API contracts remain:

`PUT /api/profiles/{profileId}`

`DELETE /api/profiles/{profileId}`

Do not add Member-specific mutation routes.

Task-specific constraints:

* access is allowed when the actor owns the Profile, or when the actor is `MANAGER`/`ADMIN` and the Profile owner has role `MEMBER`;
* inactive Member Profiles remain manageable;
* missing, unauthorized, or soft-deleted Profiles remain indistinguishable as `404`;
* management privilege must not grant cross-user mutation of Manager/Admin-owned Profiles;
* update keeps the existing request/validation contract and optimistic-concurrency behavior;
* Profile-name uniqueness remains scoped to the target Profile owner’s active Profiles;
* successful Profile data mutation must invalidate Preview state as existing behavior requires;
* delete remains soft delete;
* the last active Profile of the target owner cannot be deleted;
* any locking/counting needed to preserve delete invariants must operate on the target Profile owner, not the authenticated Manager/Admin;
* Manager/Admin authorization must not bypass validation, uniqueness, concurrency, or lifecycle invariants;
* no schema change is expected.

Out of scope:

* Profile create/copy;
* Profile Section mutation;
* Member search;
* CV Preview/Export access;
* account activation/deactivation.

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

