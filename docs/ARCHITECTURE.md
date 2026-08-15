# Architecture

## Backend Shape

iBOM API is a Spring Boot backend organized around business modules and layered application boundaries.

```text
Client
  ↓
Controller
  ↓
Serviceg
  ↓
Repository
  ↓
Database
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

The intended dependency direction is:

```text
Controller
   ↓
Service
   ↓
Repository
   ↓
Persistence
```

General boundaries:

* Controllers define the HTTP boundary.
* Services own application and business behavior.
* Repositories define the persistence boundary.
* DTOs define API input/output models.
* JPA entities are persistence models and are not exposed directly through APIs.
* Backend security is authoritative.

Detailed coding rules belong in `CONVENTIONS.md`.

## Profile Aggregate

`Profile` is the central aggregate for CV-specific data.

Profile-owned data includes areas such as:

```text
Education
Certificate
Project
ProfileLanguage
ProfileSkill
```

Operations on Profile-owned data may also affect Profile-level state.

Detailed ownership and relationships belong in `DATA.md`.

## Persistence

Database schema evolution is managed through Flyway.

Detailed persistence design belongs in the relevant task context and `DATA.md`.
