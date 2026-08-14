# Conventions

## Structure

* Keep new code within the existing package/module structure.
* Do not introduce a new architectural layer without task-level justification.
* Reuse existing shared utilities, abstractions, and infrastructure before adding new ones.

## Layer Boundaries

* Controllers must not access repositories directly.
* Controllers handle HTTP concerns; business behavior belongs in Services.
* Repositories handle persistence and queries, not business decisions.
* Do not expose JPA entities directly through API boundaries.
* Use existing DTO and mapping patterns instead of creating parallel approaches.

## Validation & Errors

* Request-shape validation belongs on request DTOs.
* Validation requiring business or persistence context belongs in Services.
* Errors must flow through the existing centralized exception-handling mechanism.
* Do not build ad-hoc error responses inside controllers or services.

## Persistence

* Database changes must use Flyway migrations.
* Multi-write operations that must succeed or fail together use a transaction.
* Respect existing aggregate state when modifying child entities; child CRUD is not always isolated.

## Reuse

* Prefer extending an existing shared component over creating a duplicate helper.
* Do not introduce a new dependency when the existing stack already solves the problem.

## Tests

* Service business logic is tested at the Service layer.
* HTTP behavior is tested at the Controller/API layer.
* Follow the existing test package and naming structure in the repository.
* Add tests relevant to the active task; do not expand test scope without reason.

## Security

* Authorization is enforced on the backend.
* Do not rely on controller routing or client behavior as the only authorization check.
* Never log passwords, tokens, password hashes, or verification secrets.
