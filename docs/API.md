# API

## General

Base path:

```text
/api
```

APIs use request/response DTOs.

JPA entities are not exposed directly through APIs.

Protected APIs use Bearer Access Tokens.

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

Use `code`, not `status`.

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

Follow established repository behavior when it exists.

## Resource Structure

Profile-owned resources normally use Profile-scoped routes.

Example:

```text
/api/profiles/{profileId}/educations
/api/profiles/{profileId}/educations/{educationId}
```

A nested resource ID does not replace Profile ownership validation.

## Authentication

Access Tokens are sent using:

```text
Authorization: Bearer <access-token>
```

Refresh Tokens use the project-defined HttpOnly cookie flow.

Detailed authentication behavior is task-specific.

## Context Boundary

This file contains only stable API conventions.

It does not define the full endpoint catalogue, request fields, validation rules, or feature-specific response models.

Exact API contracts should come from the active task, `plan.md`, and the relevant routed documentation.

Current source code remains the primary reference for implemented API behavior.
