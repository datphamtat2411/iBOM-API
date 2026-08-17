# Product

iBOM is an internal CV Management System for managing employee professional profiles and generating standardized CVs.

## Roles

- `MEMBER`
- `MANAGER`
- `ADMIN`

`MEMBER` users manage their own Profile data. `MANAGER` and `ADMIN` users can manage their own Profiles and have full management access to Member Profile data. Currently, `ADMIN` has the same application permissions as `MANAGER`.

## Core Concept

A User can have multiple independent Profiles, each representing a separate CV version.

```text
User
 └── Profile
      ├── About Me
      ├── Education
      ├── Languages
      ├── Certificates
      ├── Projects
      └── Skills
```

## Main Areas

- Authentication & Account
- Profile Management
- Multi-Profile Management
- CV Preview & Export
- Master Data
- Member Management
- User Management
- Dashboard

## Stable Behavior

- A Profile must be previewed before export, and any CV-data change invalidates the previous preview.
- CV export supports PDF and DOCX, and empty CV sections are not rendered.
- Account email is immutable after account creation.
- Self-registration creates a `MEMBER` account.
- Inactive accounts cannot continue authenticated usage.
- Account inactivity does not delete User or Profile data and is not a global visibility filter.

Feature-specific requirements and acceptance criteria belong to the active task context.
