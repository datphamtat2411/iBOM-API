# Task Plan

1. Add a password-recovery service using existing verification-code persistence, SMTP sender, password encoder, and refresh-token sessions.
2. Expose exactly three public authentication endpoints for request, standalone verification, and final reset.
3. Validate and lock the latest unused reset code during final reset; atomically change the password, consume that code, and revoke all refresh sessions.
4. Add focused service and MVC/security tests.

## Repository Findings

- Registration already normalizes email with trim().toLowerCase(Locale.ROOT), generates SHA-256-hashed six-digit codes, uses a 5-minute TTL, and limits sends through VerificationCodeRepository.
- PASSWORD_RESET already exists in VerificationPurpose; verification_codes supports purpose, expiry, use state, lookup, and rate-limit indexing. No schema migration is required.
- BCrypt strength is already 12 in SecurityConfig; the existing password policy is @NotBlank @Size(min = 8, max = 72).
- Refresh sessions are persisted in refresh_tokens and support revoked_at; current repository support only locks an individual token.
- Authentication endpoints are public through explicit SecurityConfig allowlisting. CSRF protection is scoped only to refresh and logout.
- Existing tests use mocked service/repository unit tests and @WebMvcTest for controller/security boundaries.

## Proposed Changes

### Password-reset code request:

- Normalize email identically to login and registration.
- Use PASSWORD_RESET, six-digit SHA-256-hashed codes, five-minute expiry, and the existing verification_codes table.
- Record reset-code requests for every normalized email, including unknown emails, so the rolling 60-minute five-send limit returns identical externally observable behavior for registered and unregistered addresses.
- Send the reset email only when an account exists; always return the same successful response for requests below the limit, without applying the registration domain allowlist.
- Return the same rate-limit response after five requests regardless of account existence.

### Reset-code verification:

- Provide a standalone endpoint accepting email and six-digit code.
- Look up only the newest unused PASSWORD_RESET code, require it to be unexpired and hash-equal, and also require an existing account.
- Return a generic invalid-or-expired-code failure for all invalid cases, including unknown email.
- Do not consume a valid code.

### Final password reset:

- Accept only email, verification code, and new password; enforce the established email, six-digit-code, and password validation rules.
- Revalidate the code against the latest unused applicable reset code inside one transaction; never rely on prior verification.
- Pessimistically lock the relevant account/code state so concurrent resets cannot reuse a code.
- Update only passwordHash using the configured BCrypt encoder, consume the code, and revoke every refresh-token session for that user in the same transaction.
- Preserve email, username, role, and current account status. INACTIVE accounts remain inactive.
- Do not revoke or version access tokens; existing access tokens expire under the current 900-second lifetime.

### Security and response behavior:

- Allow all three endpoints without authentication.
- Keep them outside the refresh/logout CSRF flow and do not issue or clear cookies.
- Continue using the existing ApiResponse and exception-handling conventions.

## API Surface

### POST /api/auth/password-reset-code

- Request: { "email": "user@example.com" }
- Response: 200 with the generic success wrapper whether or not the account exists; 429 after the fifth request for that normalized email in the rolling hour.

### POST /api/auth/password-reset-code/verify

- Request: { "email": "user@example.com", "verificationCode": "123456" }
- Response: 200 with the success wrapper when the latest unused unexpired reset code belongs to an existing account; otherwise generic 400.

### POST /api/auth/password-reset

- Request: { "email": "user@example.com", "verificationCode": "123456", "password": "new-password" }
- Response: 200 with the success wrapper after atomic password update, code consumption, and refresh-session revocation; otherwise generic 400.
- No confirmPassword, reset JWT, reset token, cookie, or access token is introduced.

## File Delta

### Modify:

- src/main/java/com/fpt/ibom/auth/controller/AuthController.java
- src/main/java/com/fpt/ibom/auth/entity/UserAccount.java
- src/main/java/com/fpt/ibom/auth/repository/UserAccountRepository.java
- src/main/java/com/fpt/ibom/auth/repository/VerificationCodeRepository.java
- src/main/java/com/fpt/ibom/auth/repository/RefreshTokenRepository.java
- src/main/java/com/fpt/ibom/config/SecurityConfig.java
- src/test/java/com/fpt/ibom/auth/AuthControllerTest.java

### Add:

- src/main/java/com/fpt/ibom/auth/dto/PasswordResetCodeRequest.java
- src/main/java/com/fpt/ibom/auth/dto/PasswordResetCodeVerificationRequest.java
- src/main/java/com/fpt/ibom/auth/dto/PasswordResetRequest.java
- src/main/java/com/fpt/ibom/auth/service/PasswordResetService.java
- src/test/java/com/fpt/ibom/auth/PasswordResetServiceTest.java

### Remove:

- None.

## Focused Tests

- Request flow normalizes email, creates a six-digit hashed PASSWORD_RESET code with five-minute expiry, sends mail only for an existing account, and does not apply the registration domain allowlist.
- Registered and unregistered emails have identical success and five-per-hour rate-limit behavior.
- Verification accepts only the latest unused unexpired matching code for an existing account and does not consume it.
- Reset rejects expired, used, superseded, incorrect, and unknown-account codes.
- Reset encodes the new password, consumes the code, revokes all user refresh sessions, and preserves inactive status and immutable account attributes.
- MVC tests confirm the three routes are public, require no CSRF token, validate request shape, and produce no authentication cookies.
- Focused command: mvn test -Dtest=PasswordResetServiceTest,AuthControllerTest.

## Out of Scope

- Authenticated change-password behavior.
- Username, email, role, or account-status changes.
- Password history or expiration policy.
- Reset JWTs, reset-token persistence, CAPTCHA, and device/IP controls.
- Immediate access-token revocation or versioning.
- Separate logout-all-devices functionality.