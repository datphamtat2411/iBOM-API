# API

## General

Base path:

```text
/api
```

APIs use request/response DTOs. JPA entities are not exposed directly.

Protected APIs use Bearer Access Tokens.

---

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

---

## HTTP Semantics

Use the existing project semantics:

```text
200 → successful request
201 → resource created
400 → invalid request
401 → unauthenticated
403 → forbidden
404 → resource not found
409 → business or concurrency conflict
```

Do not invent different status behavior for a task unless explicitly required.

---

## Resource Structure

Profile-owned resources normally use Profile-scoped routes.

Example:

```text
/api/profiles/{profileId}/educations
/api/profiles/{profileId}/educations/{educationId}
```

A nested resource ID does not replace Profile ownership validation.

---

## Authentication

Access Token:

```text
Authorization: Bearer <access-token>
```

Refresh Token is handled through the backend session mechanism and HttpOnly cookie.

Do not design APIs around storing or manually passing Refresh Tokens from frontend code.

---

## API Changes

The active task prompt defines the exact API contract when implementation detail is required.

When adding or changing an API:

* follow existing resource structure,
* reuse existing response conventions,
* preserve compatibility with related APIs where practical,
* update Swagger/OpenAPI when applicable,
* do not create supporting endpoints unless the task requires them.

---

## Boundary

This file defines only stable API conventions.

It does not maintain the full endpoint catalogue, request fields, validation rules, or feature-specific response models.
