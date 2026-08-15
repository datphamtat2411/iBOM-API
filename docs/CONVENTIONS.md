# Conventions

These are stable coding rules for this repository.

Task-specific implementation details belong in the active `plan.md`.

## Existing Patterns

Inspect the current repository before introducing a new pattern.

When an established pattern exists:

* follow it;
* reuse existing shared components where practical;
* avoid introducing competing abstractions without a task-level reason.

Do not assume a pattern exists only because it is described in documentation.

## Layer Boundaries

Keep responsibilities separated:

* Controllers handle HTTP concerns.
* Services handle application and business behavior.
* Repositories handle persistence and queries.

Controllers must not access repositories directly.

Business decisions should not be implemented inside repositories.

## API Boundary

Use DTOs at API boundaries.

Do not expose JPA entities directly through API responses or requests.

Follow the repository's existing mapping approach when one is established.

## Validation and Errors

Request-shape validation belongs on request DTOs.

Validation that requires business or persistence context belongs in the Service layer.

Use the repository's centralized exception-handling approach when one exists.

Do not introduce ad-hoc error-response formats.

## Persistence

Database schema changes must use Flyway migrations.

Operations containing multiple writes that must succeed or fail together should use an appropriate transaction boundary.

Do not introduce persistence behavior that conflicts with the active task or current repository model.

## Security

Backend authorization is authoritative.

Do not rely on frontend behavior, hidden controls, or route protection as the only authorization mechanism.

## Tests

Follow the existing test structure and conventions when they exist.

Detailed testing strategy belongs in `TESTING.md` and the active `plan.md`.
