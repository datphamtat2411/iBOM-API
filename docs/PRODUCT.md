# Product

iBOM is an internal CV Management System for managing employee professional profiles and generating standardized CVs.

## Roles

- `MEMBER`
- `MANAGER`
- `ADMIN`

Currently, `ADMIN` has the same permissions as `MANAGER`.

## Core Concept

A User can have multiple independent Profiles.

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

Each Profile represents one independent CV version.

## Main Areas

- Authentication & Account
- Profile Management
- Multi-Profile Management
- CV Preview & Export
- Master Data
- Member Management
- User Management
- Dashboard

## CV

Supported outputs:

- PDF
- DOCX

## Scope of This File

This file provides only high-level product context.

Detailed requirements and implementation behavior are defined by the active task, `plan.md`, and relevant routed documentation.
