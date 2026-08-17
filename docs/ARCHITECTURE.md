# Architecture

## Backend Shape

iBOM API is a Spring Boot backend organized around business modules and layered application boundaries.

```text
Client
  ↓
Controller
  ↓
Service
  ↓
Repository
  ↓
Persistence
```

Cross-cutting concerns include:

* Security
* Validation
* Exception Handling
* Mapping
* Configuration

## Business Modules

Main backend areas include:

```text
Auth / Account
Profile
CV
Master Data
Member Management
User Management
Dashboard
```

Modules should keep their business responsibilities separated.

When modules need to collaborate, prefer service-level interaction instead of reaching directly into another module's persistence logic.

## Layer Boundaries

Layer responsibilities:

* Controllers define the HTTP boundary.
* Services own application and business behavior.
* Repositories define the persistence boundary.
* Controllers do not access repositories directly.
* Repositories do not implement business decisions.
* Multiple writes that must succeed or fail together use an appropriate transaction boundary.

Backend authorization is the final authority; frontend controls and route protection are not sufficient authorization mechanisms.

## Persistence

Database schema evolution is managed through Flyway migrations. Exact persistence design remains task-specific unless documented as a stable invariant in `DATA.md`.
