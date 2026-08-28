# Task Plan

# Repository Findings

* Current branch is `module-profile-core`.
* Profile stores ownership through `user`, soft deletion through `deletedAt`, optimistic locking through `version`, and recency through `updatedAt`.
* `ProfileRepository` currently supports only active-name uniqueness checks.
* `ProfileService` currently implements creation only.
* `ProfileController` currently exposes only `POST /api/profiles`.
* `ProfileResponse` currently exposes `userId`; new read responses must use DTOs that omit ownership and deletion state.
* `UserPrincipal.userId()` is the established authenticated-user identifier.
* `SecurityConfig` protects all non-public API routes, including both new endpoints.
* `ApiResponse` and `GlobalExceptionHandler` define the common success and error contract.
* `ErrorCode` does not yet contain `PROFILE_NOT_FOUND`.
* Existing Profile tests are service-level Mockito tests. Controller tests use focused `@WebMvcTest` and Spring Security test patterns.

# Proposed Changes

## Profile Read Queries

* Add a repository query for active Profiles owned by a user.
* Filter with `deletedAt IS NULL`.
* Order by `updatedAt DESC`, with `id DESC` as a deterministic tie-breaker.
* Add an ownership-scoped lookup by Profile ID and authenticated user ID, also requiring `deletedAt IS NULL`.

## Profile DTOs

* Add a Profile summary DTO containing only selector/list fields, including Profile ID, Profile name, core identity fields needed for display, and last-updated timestamp.
* Add a Profile detail DTO containing the implemented Profile Core fields and optimistic-lock version.
* Exclude `userId` and `deletedAt` from both read DTOs.
* Do not include child Profile sections that are not implemented.

## Profile Service

* Add an authenticated-user list operation that maps repository results to summaries and returns an empty collection when no active Profiles exist.
* Add an authenticated-user detail operation using the owner-scoped repository lookup.
* Throw `ApiException` with `404 NOT_FOUND` and `PROFILE_NOT_FOUND` when the Profile is missing, soft-deleted, or owned by another User.

## Profile Controller and Errors

* Add authenticated `GET /api/profiles/me`.
* Add authenticated `GET /api/profiles/{profileId}`.
* Wrap successful results in `ApiResponse` with `200 OK`.
* Add `PROFILE_NOT_FOUND` to the shared error-code enum so the global handler returns the required common error response.

# API Surface

* `GET /api/profiles/me`

  * Authentication: required.
  * Success: `200 OK`.
  * Response: common `ApiResponse` containing an ordered collection of Profile summaries.
  * Empty result: data is an empty collection.
  * Ordering: recently updated Profiles first, then Profile ID descending for ties.
* `GET /api/profiles/{profileId}`

  * Authentication: required.
  * Success: `200 OK`.
  * Response: common `ApiResponse` containing the authenticated User's active Profile Core detail, including `version`.
  * Missing, deleted, or foreign Profile: `404 NOT_FOUND`, `errorCode: PROFILE_NOT_FOUND`.

# File Delta

## Modify

* `src/main/java/com/fpt/ibom/profile/repository/ProfileRepository.java`
* `src/main/java/com/fpt/ibom/profile/service/ProfileService.java`
* `src/main/java/com/fpt/ibom/profile/controller/ProfileController.java`
* `src/main/java/com/fpt/ibom/exception/ErrorCode.java`
* `src/test/java/com/fpt/ibom/profile/ProfileServiceTest.java`

## Add

* `src/main/java/com/fpt/ibom/profile/dto/ProfileSummaryResponse.java`
* `src/main/java/com/fpt/ibom/profile/dto/ProfileDetailResponse.java`
* Focused Profile controller test covering the two read endpoints and their response/error contracts.

## Remove

* None.

# Focused Tests

* Service list returns only repository-provided active, owned Profiles and preserves deterministic ordering.
* Service list returns an empty collection when no active Profiles exist.
* Service detail maps implemented Core fields and includes the optimistic-lock version.
* Service detail raises `PROFILE_NOT_FOUND` for an absent owner-scoped lookup.
* Controller requires authentication for both endpoints.
* Controller returns the common `200 OK` response for list and detail.
* Controller exposes no ownership ID or deletion state in read payloads.
* Controller returns `404` with `PROFILE_NOT_FOUND` for a missing, deleted, or foreign Profile.

# Out of Scope

* Profile creation behavior changes.
* Profile update, About Me, or delete operations.
* Last-profile protection.
* Profile copying.
* Completeness calculation.
* Education, Languages, Certificates, Projects, or Skills.
* CV preview or export.
* Manager/Admin access to another User's Profile.
