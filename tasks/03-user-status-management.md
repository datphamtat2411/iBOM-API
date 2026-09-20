Read `AGENTS.md`.

Plan User Status Management for the current User Management module.

Start discovery from:

* the User Management controller/service structure established by Tasks 01–02;
* `UserAccount` and `UserAccountRepository`;
* `RefreshTokenRepository`;
* `LoginService`, `UserAccountJwtAuthenticationConverter`, and `SecurityConfig`;
* focused authentication, session, and User Management tests.

Use these as entry points, not an exhaustive file list. Follow only task-related source dependencies as needed. Do not perform broad repository discovery.

Read only:

* `docs/PRODUCT.md`;
* `docs/API.md`;
* `docs/ARCHITECTURE.md`;
* `docs/TESTING.md`.

Plan these capabilities:

* allow Manager/Admin to explicitly activate or deactivate a User;
* prevent Manager/Admin from deactivating their own account;
* invalidate existing authentication usage when a User is deactivated;
* preserve all User and Profile data across status changes.

The required API contract is fixed:

`PUT /api/users/{userId}/status`

Request:

* `status`: `ACTIVE` or `INACTIVE`.

Successful response:

* canonical User response containing `id`, `username`, `email`, `role`, and `status`.

Task-specific constraints:

* only `MANAGER` and `ADMIN` may manage User status; they have equal permissions;
* use desired-state semantics, not toggle semantics; same-state requests must be idempotent;
* self-deactivation must be rejected;
* deactivation must set the account to `INACTIVE` and revoke all active refresh tokens for that User;
* existing access tokens must become unusable through the current account-status authentication checks;
* reactivation sets the account to `ACTIVE` but must not restore previously revoked refresh tokens;
* status change must not delete or modify User-owned Profile data;
* concurrent status operations must not leave account status and refresh-session invalidation inconsistent;
* reuse existing authentication/session infrastructure rather than redesigning JWT or refresh-token flows;
* no schema change is expected.

Out of scope:

* User list/search or creation changes;
* existing-user role changes;
* User deletion;
* Profile or Member Management behavior changes;
* Dashboard behavior;
* invitation or password-management flows.

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

