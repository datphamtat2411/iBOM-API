# Requirements

## Profile

* A User can have multiple Profiles.
* Profiles are independent CV versions.
* Profile-owned data is not shared between Profiles.
* Profile Name is unique per User using case-insensitive comparison.
* Copying a Profile performs a deep copy.
* Profile deletion uses soft delete.
* The last active Profile cannot be deleted.
* Profile editing must support concurrency protection.

## Authorization

* `MEMBER` manages their own Profile data.
* `MANAGER` / `ADMIN` can manage their own Profiles and have full management access to Member Profile data.
* Backend is the final authorization authority.
* Nested Profile resources follow Profile ownership.

## CV

* A Profile must be previewed before export.
* Any CV-data change invalidates the previous preview.
* CV export supports PDF and DOCX.
* Empty CV sections are not rendered.

## Account

* Email is immutable after account creation.
* Self-registration creates a `MEMBER`.
* Inactive accounts cannot continue authenticated usage.
* Account inactivity does not delete stored User/Profile data and is not a global visibility filter.
* `ADMIN` currently has the same application permissions as `MANAGER`.

## Data

* Education, Certificates and Projects belong to a Profile.
* Skills and Languages are shared Master Data.
* Profile-specific Skill and Language information belongs to their Profile associations.
* Seniority is derived from `ProfileSkill.experienceYears`.

## Task Detail

Feature-specific rules, validation, API behavior, edge cases, and implementation requirements belong to the active task context and any documentation explicitly routed during PLAN.