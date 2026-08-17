# Data

## Main Entities

```text
User
 ├── Profile
 │    ├── Education
 │    ├── Certificate
 │    ├── Project
 │    ├── ProfileLanguage → Language
 │    └── ProfileSkill → Skill
 │
 ├── RefreshToken
 └── VerificationCode
```

Shared Master Data:

* `Skill`
* `Language`
* `Seniority`
* `FileNameFormat`

## Relationships

```text
User 1:N Profile

Profile 1:N Education
Profile 1:N Certificate
Profile 1:N Project

Profile 1:N ProfileLanguage
Language 1:N ProfileLanguage

Profile 1:N ProfileSkill
Skill 1:N ProfileSkill

FileNameFormat 1:N Profile
```

## Ownership

`User` owns `Profile`.

`Profile` owns all CV-specific data:

* Education
* Certificate
* Project
* ProfileLanguage
* ProfileSkill

Profile-owned records are independent between Profiles.

`Skill`, `Language`, `Seniority`, and `FileNameFormat` are shared data and are not copied as independent master records.

Copying a Profile deep-copies its Profile-owned data while continuing to reference shared Master Data.

Nested resource ownership is resolved through:

```text
Child Resource
→ Profile
→ User
```

## Important State

`Profile` carries cross-feature state such as:

* selected file name format;
* preview state;
* soft-delete state;
* optimistic-lock version.

`ProfileSkill` stores Profile-specific Skill information such as experience and last-used data.

`ProfileLanguage` stores Profile-specific language proficiency.

## Persistence Rules

* Profile deletion is soft delete.
* Soft-deleted Profiles are excluded from normal application behavior.
* Profile changes participate in optimistic concurrency control.
* CV-data mutations may update Profile-level state.

## Important Constraints

* Profile Name is unique per User, case-insensitive.
* The same Language cannot appear twice in one Profile.
* The same Skill cannot appear twice in one Profile.
* A User cannot delete their final active Profile.
* Seniority is derived from `ProfileSkill.experienceYears`; it is not the persisted source of truth on `ProfileSkill`.

Exact columns, SQL types, indexes, foreign keys, and migration details remain task-specific unless established as stable repository behavior.
