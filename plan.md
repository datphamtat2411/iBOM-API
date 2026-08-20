# Task Plan

Plan two Bearer-authenticated account-setting endpoints for self-service password and username changes, scoped only by UserPrincipal.userId.

## Repository Findings

- UserPrincipal carries authoritative user ID; JWT username claims can remain stale.
- Existing password policy is nonblank, 8-72 characters; BCrypt strength is 12.
- Username maximum is 100; registration trims usernames and enforces case-insensitive uniqueness.
- Flyway V2 already enforces case-insensitive username uniqueness with generated username_ci and a unique key.
- Refresh/logout CSRF protection is restricted to /api/auth/refresh and /api/auth/logout; other Bearer mutations are CSRF-free.
- Registration provides existing 409 conflict behavior for duplicate usernames. Email is immutable by product rule.

## Proposed Changes

### Password change:

- Add a validated request with currentPassword and newPassword; no account identifier or confirmPassword.
- Load the account by authenticated user ID, verify the current password, encode the new password through the existing BCrypt-12 encoder, and persist it transactionally.
- Return the existing invalid-credentials behavior for an incorrect current password.
- Do not create tokens, rotate/revoke refresh sessions, or alter reset-password behavior.

### Username change:

- Add a validated request containing only username; trim before persistence and retain the submitted casing.
- Reject a case-insensitive username owned by a different account using existing conflict behavior.
- Permit a case-only change for the authenticated account.
- Flush the update and translate a database uniqueness race into the existing conflict behavior.
- Return updated account data using the existing authenticated-user response shape.
- Do not issue tokens or rotate refresh sessions.

### Persistence and security:

- Add a username mutator and repository support for checking a case-insensitive username while excluding the current account.
- No security configuration or Flyway migration changes: authenticated routes are already protected by default, V2 already supplies the database uniqueness safeguard, and CSRF remains limited to refresh/logout.

## API Surface

### POST /api/auth/change-password

- Request: { "currentPassword": "...", "newPassword": "..." }
- Response: standard successful ApiResponse with no token or cookie changes.

### PATCH /api/auth/username

- Request: { "username": "..." }
- Response: standard successful ApiResponse<AuthenticatedUser> with updated ID, email, username, and role.

## File Delta

### Modify:

- src/main/java/com/fpt/ibom/auth/controller/AuthController.java
- src/main/java/com/fpt/ibom/auth/entity/UserAccount.java
- src/main/java/com/fpt/ibom/auth/repository/UserAccountRepository.java
- src/test/java/com/fpt/ibom/auth/AuthControllerTest.java

### Add:

- Account-settings service in src/main/java/com/fpt/ibom/auth/service/
- Password-change and username-change DTOs in src/main/java/com/fpt/ibom/auth/dto/
- Focused account-settings service test in src/test/java/com/fpt/ibom/auth/

### Remove:

- None.

## Focused Tests

- Correct and incorrect current-password handling; policy validation; password encoding; no refresh-token interaction.
- Username trimming, preserved casing, maximum length validation, duplicate conflict, database-race conflict handling, and permitted case-only self-change.
- Bearer authentication is required, target ID comes from the JWT principal, no request identifier is accepted, no CSRF header is required, and neither endpoint sets cookies or returns tokens.

## Out of Scope

Email changes, reset/forgot-password changes, logout or session revocation, token issuance, roles, account status, password history/expiry, auditing, user management, profile functionality, and current-user read endpoints.