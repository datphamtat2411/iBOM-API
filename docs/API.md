# API

## General

Base path:

```text
/api
```

APIs use request/response DTOs.

JPA entities are not exposed directly through APIs.

## Response Format

Success and error responses use the common wrapper:

```json
{
  "code": 200,
  "message": "Success",
  "data": {},
  "timestamp": "..."
}
```

Error responses additionally include a stable machine-readable `errorCode`:

```json
{
  "code": 409,
  "errorCode": "AUTH_EMAIL_ALREADY_REGISTERED",
  "message": "Email is already registered",
  "data": null,
  "timestamp": "..."
}
```

Use `code`, not `status`, for the HTTP status code. Frontend logic should use `errorCode` as the programmatic error identifier; `message` is human-readable and its wording may change. Error codes are added incrementally when new business behavior is implemented.

## HTTP Semantics

Default HTTP semantics:

```text
200 → successful request
201 → resource created
400 → invalid request
401 → unauthenticated
403 → forbidden
404 → resource not found
409 → business or concurrency conflict
```

## Resource Structure

Profile-owned resources normally use Profile-scoped routes.

Example:

```text
/api/profiles/{profileId}/educations
/api/profiles/{profileId}/educations/{educationId}
```

A nested resource ID does not replace ownership validation through its Profile.

## Authentication

Access Tokens are sent using:

```text
Authorization: Bearer <access-token>
```

Refresh Tokens use the project-defined HttpOnly cookie flow.

Authentication routes:

```text
POST /api/auth/refresh-token
POST /api/auth/forgot-password
POST /api/auth/forgot-password/verify
POST /api/auth/reset-password
PUT /api/auth/change-password
PUT /api/auth/change-username
```

Detailed authentication behavior is task-specific.

## Validation

Request-shape validation belongs on request DTOs at the API boundary. Validation requiring business or persistence context belongs in the Service layer.

Use the repository's centralized exception handling rather than introducing ad hoc error formats.

## Boundary

This file defines stable API conventions, not a full endpoint catalogue. Exact contracts and feature-specific models come from the active task and implemented source.
