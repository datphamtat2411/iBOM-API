Read `AGENTS.md`.

Plan Managed User Creation for the current User Management module.

Start discovery from:

* the User Management controller/service/DTO structure established by Task 01;
* `RegistrationService` and current registration validation behavior;
* `UserAccount`, `UserAccountRepository`, and `StrongPassword`;
* `SecurityConfig` and focused registration/user-management tests.

Use these as entry points, not an exhaustive file list. Follow only task-related source dependencies as needed. Do not perform broad repository discovery.

Read only:

* `docs/PRODUCT.md`;
* `docs/API.md`;
* `docs/ARCHITECTURE.md`;
* `docs/TESTING.md`.

Plan these capabilities:

* allow Manager/Admin to create application users;
* assign one supported role at creation;
* enforce existing account identity, password, and email-domain rules;
* create the account directly without registration email verification.

The required API contract is fixed:

`POST /api/users`

Request:

* `email`;
* `username`;
* `password`;
* `role`: `MEMBER`, `MANAGER`, or `ADMIN`.

Successful response:

* HTTP `201`;
* canonical User response containing `id`, `username`, `email`, `role`, and `status`.

Task-specific constraints:

* only `MANAGER` and `ADMIN` may create users; they have equal permissions;
* managed users are created as `ACTIVE`; status is not client-selectable;
* no verification code or registration-verification flow is required;
* enforce the existing allowed email-domain rule;
* apply the existing strong-password policy and password encoder; never persist or expose plaintext passwords;
* email and username uniqueness remain case-insensitive;
* preserve persistence-level uniqueness as the final authority and translate concurrent duplicate failures consistently;
* reuse existing account rules where appropriate without forcing User Management through the self-registration flow;
* creation may assign any supported role, but must not add existing-user role mutation;
* do not create a Profile or other CV-owned data;
* no schema change is expected.

Out of scope:

* User list/search changes;
* activate/deactivate behavior or session revocation;
* changing an existing User's role;
* invitation, generated-password, or first-login password-change flows;
* admin password reset;
* Profile creation.

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

