# Task Plan

Implement refresh-token renewal and current-session logout with rotation, server-side revocation, cookie-based authentication, and targeted stateless CSRF protection. Preserve JWT statelessness, existing token hashing, and the seven-day refresh lifetime.

## Repository Findings

- The refresh token is stored hashed in refresh_tokens, but has no revocation state.
- LoginService creates refresh sessions and access tokens in one transaction.
- AuthController currently sets only the HttpOnly refresh_token cookie.
- SecurityConfig disables CSRF globally and permits login and registration endpoints.
- Access-token authentication already rejects inactive users.
- Existing tests cover login cookies, login failures, JWT boundaries, and service-level refresh-session creation.
- Flyway migrations currently end at V2; applied migrations must remain unchanged.

## Proposed Changes

### Refresh Renewal

- Add refresh-session lookup by hashed token with row locking to prevent concurrent successful rotations.
- Reject missing, unknown, expired, revoked, and inactive-user sessions with one generic authentication failure response.
- Within one transaction, mark the old session revoked and create a replacement using the existing SHA-256 hashing and seven-day TTL.
- Issue a new access token for the active user and return the normal authentication response shape.
- Ensure the old token cannot be reused after successful rotation.

### Logout

- Accept the current refresh cookie without requiring a valid Bearer access token.
- Revoke only the matching refresh session when it exists.
- Treat missing, expired, unknown, and already-revoked cookies as successful logout cases.
- Always clear the refresh-token cookie and CSRF cookie.

### CSRF Protection

- Replace global CSRF disablement with stateless targeted protection for refresh and logout only.
- Use a client-readable CSRF cookie and a required request header, such as XSRF-TOKEN and X-XSRF-TOKEN.
- Leave login, registration, and general Bearer-token APIs outside CSRF enforcement.
- Generate and send CSRF state on login and successful refresh.
- Use the same cookie name, path, Secure, SameSite, and related attributes when setting, replacing, and clearing cookies.

### Persistence

- Add nullable revoked_at to the refresh-token entity and database schema through a new Flyway migration.
- Keep refresh-token hashes, expiry timestamps, and the existing seven-day configuration unchanged.
- Avoid exposing token hashes, session identifiers, ownership details, or other internal refresh-session state in errors.

## API Surface

### POST /api/auth/refresh

- Uses the HttpOnly refresh_token cookie and the CSRF header.
- Requires no valid Access Token.
- Returns 200 with a new access token and refreshed authentication state.
- Sets a rotated refresh cookie and refreshed CSRF cookie.
- Returns a generic 401 response for all invalid refresh-session cases.

### POST /api/auth/logout

- Uses the current refresh cookie and CSRF header.
- Requires no valid Access Token.
- Revokes only the matching refresh session.
- Returns success for missing, expired, unknown, or already-revoked refresh cookies.
- Clears both the refresh-token and CSRF cookies.

## Configuration

- Retain app.auth.refresh-token-ttl-seconds=604800.
- Retain the existing access-token lifetime and JWT configuration.
- Configure the CSRF cookie as client-readable and scoped consistently to /api/auth.
- Keep refresh and CSRF cookies Secure with SameSite=Strict; keep only the refresh cookie HttpOnly.
- Keep the application session policy stateless.

## File Delta

### Modify:

- src/main/java/com/fpt/ibom/auth/controller/AuthController.java
- src/main/java/com/fpt/ibom/auth/entity/RefreshToken.java
- src/main/java/com/fpt/ibom/auth/repository/RefreshTokenRepository.java
- src/main/java/com/fpt/ibom/auth/service/LoginService.java
- src/main/java/com/fpt/ibom/config/SecurityConfig.java
- src/test/java/com/fpt/ibom/auth/AuthControllerTest.java
- src/test/java/com/fpt/ibom/auth/LoginServiceTest.java
- src/test/java/com/fpt/ibom/controller/SecurityBoundaryTest.java

### Add:

- src/main/resources/db/migration/V3__add_refresh_token_revocation.sql

### Remove:

- None

## Focused Tests

- Run mvn -Dtest=LoginServiceTest,AuthControllerTest,SecurityBoundaryTest test.
- Cover successful refresh rotation and new access-token issuance.
- Cover rejection of missing, unknown, expired, revoked, and inactive-user refresh sessions.
- Cover prevention of reuse after rotation.
- Cover logout idempotency and current-session-only revocation.
- Cover refresh/logout CSRF header enforcement.
- Cover login and successful refresh CSRF-cookie issuance.
- Cover cookie clearing and consistent cookie attributes.
- Cover continued CSRF-free Bearer-token API behavior.

## Out of Scope

- Logout all devices.
- Refresh-token families or advanced reuse detection.
- Device, IP, or user-agent session tracking.
- Password recovery.
- Account settings.
- User-management behavior.
- Profile functionality.