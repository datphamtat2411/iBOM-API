# Testing

Backend testing uses:

* JUnit 5
* Mockito
* MockMvc
* JaCoCo

## Test Scope

Test behavior relevant to the active task.

Depending on the feature, consider:

* happy path,
* validation,
* authorization,
* ownership,
* not found,
* duplicate or business conflict,
* concurrency,
* persistence behavior.

Exact test scenarios are defined by the active task prompt.

## Service Tests

Use service tests for business behavior such as:

* business validation,
* ownership and permission rules,
* state changes,
* duplicate checks,
* repository coordination,
* business conflicts.

Mock dependencies when the goal is to isolate service logic.

Do not mock the behavior being tested.

## Controller Tests

Use controller/API tests for HTTP-level behavior such as:

* request mapping,
* request validation,
* authentication,
* authorization,
* HTTP status,
* response structure,
* exception mapping.

Use `MockMvc` where consistent with the existing project.

## Persistence-Sensitive Behavior

Add persistence/integration coverage when the task depends on behavior such as:

* database constraints,
* soft delete,
* optimistic locking,
* Flyway changes,
* repository queries.

Do not add integration tests when isolated tests are sufficient for the task.

## Regression

When fixing a meaningful bug, add or update a test that reproduces the failure when practical.

## Coverage

JaCoCo is used for backend coverage.

Coverage is a quality signal, not the goal.

Prefer meaningful tests for business and security behavior over tests written only to increase coverage.

## Boundary

This file defines backend testing conventions only.

It does not define the complete project test suite or feature-specific test cases.
