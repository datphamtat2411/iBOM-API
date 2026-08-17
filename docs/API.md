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

The wrapper fields are `code`, `message`, `data`, and `timestamp`; use `code`, not `status`. Validation failures and handled API exceptions follow this shape.

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

Detailed authentication behavior is task-specific.

## Validation

Request-shape validation belongs on request DTOs at the API boundary. Validation requiring business or persistence context belongs in the Service layer.

Use the repository's centralized exception handling rather than introducing ad hoc error formats.

## Boundary

This file defines stable API conventions, not a full endpoint catalogue. Exact contracts and feature-specific models come from the active task and implemented source.
