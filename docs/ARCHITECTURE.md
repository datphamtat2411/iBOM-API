# Architecture

## High-Level Architecture

The backend is a modular Spring Boot application using layered architecture.

```text
Client
  ↓
Controller
  ↓
Service
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

---

## Main Modules

Backend functionality is organized around business areas such as:

```text
Auth / Account
Profile
CV
Master Data
Member Management
User Management
Dashboard
```

Each module should keep its own business logic while following the common backend layers.

---

## Layers

### Controller

HTTP boundary of the application.

Responsibilities:

* receive requests,
* validate request DTOs,
* resolve authentication/context,
* call Services,
* return API responses.

Controllers should not contain business logic or direct repository access.

### Service

Main business layer.

Responsibilities:

* business rules,
* authorization and ownership checks,
* transaction boundaries,
* domain state changes,
* coordination between repositories.

### Repository

Persistence boundary.

Responsibilities:

* database access,
* queries,
* existence checks,
* filtering and aggregation.

Repositories should not contain business decisions.

### DTO / Mapper

API models are separated from persistence entities.

```text
Request DTO
   ↓
Service / Domain
   ↓
Entity
```

```text
Entity
   ↓
Mapper
   ↓
Response DTO
```

JPA entities should not be exposed directly through APIs.

---

## Dependency Direction

Dependencies flow inward through the application layers.

```text
Controller
   ↓
Service
   ↓
Repository
   ↓
Persistence
```

Avoid:

```text
Controller → Repository
Repository → Service
Entity → Controller
```

Business modules may collaborate through Services when required.

Avoid bypassing module boundaries through direct repository access.

---

## Request Flow

Typical request flow:

```text
HTTP Request
    ↓
Security / Authentication
    ↓
Controller
    ↓
Request Validation
    ↓
Service
    ↓
Authorization / Business Rules
    ↓
Repository
    ↓
Database
    ↓
Response Mapping
    ↓
HTTP Response
```

---

## Boundaries

### API Boundary

Controllers and DTOs define communication with clients.

API concerns should not leak into persistence logic.

### Business Boundary

Services own business behavior and application decisions.

### Persistence Boundary

Repositories and Entities handle database interaction.

Persistence structure should not dictate API design directly.

### Security Boundary

Backend security is authoritative.

Authorization may depend on:

```text
Authentication
+ Role
+ Ownership
+ Business Rules
```

### Module Boundary

Business modules should interact through clear service-level contracts rather than reaching into each other's persistence implementation.

---

## Important Architectural Decisions

* Backend uses layered architecture.
* Business logic belongs in Services.
* Controllers remain thin.
* JPA Entities are not exposed directly through APIs.
* DTOs are used at API boundaries.
* Repository access is performed through Services.
* Authorization is enforced in the backend.
* Profile is the main aggregate for CV-specific data.
* Profile-owned child operations may affect Profile-level state.
* Multi-record business operations should use transactional boundaries.
* Database schema changes are managed through Flyway.
* Existing architecture should be extended rather than introducing new patterns without task-level justification.

---

Detailed implementation choices are defined by the active task prompt.
