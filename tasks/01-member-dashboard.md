Read `AGENTS.md`.

Plan Member Dashboard for the current Dashboard module.

Start discovery from:

* the current Profile completeness service and API;
* Profile repository/service paths for current-user active Profiles and export state;
* existing Profile ownership/access behavior;
* focused completeness, Profile access, and CV export tests.

From these anchors, follow only task-related source dependencies needed for Member Dashboard. Prefer existing collaborators and tests before broad repository search.

Read only:

* `docs/PRODUCT.md`;
* `docs/ARCHITECTURE.md`;
* `docs/DATA.md`;
* `docs/API.md`;
* `docs/TESTING.md`.

Plan these capabilities:

* expose Member Dashboard statistics for an explicitly selected Profile;
* reuse canonical Profile completeness semantics, including section breakdown;
* expose the latest successful export across all non-deleted Profiles owned by the authenticated user;
* return a Dashboard-specific read model without duplicating Profile-list behavior.

The required API contract is fixed:

`GET /api/dashboard/my-stats?profileId={profileId}`

`profileId` is required. Do not infer or select a default Profile.

Task-specific constraints:

* the selected Profile must be non-deleted and owned by the authenticated user; foreign or deleted Profiles return `404`;
* `MEMBER`, `MANAGER`, and `ADMIN` may use this endpoint only for their own Profiles;
* completeness is for the selected Profile only and must retain the existing canonical completeness calculation;
* latest export is `MAX(lastExportedAt)` across all non-deleted Profiles owned by the current user, independent of the selected Profile;
* when no successful export exists, return `latestExportedAt = null`; do not return presentation text such as `Never exported`;
* include selected Profile identity, completeness, and latest export timestamp in the response;
* keep the operation read-only;
* no schema change or Dashboard persistence is expected.

Out of scope:

* Profile listing or default Profile selection;
* Manager Dashboard statistics or authorization;
* Primary Skill or Skill Category analytics;
* CV export behavior or export-state writes;
* changing Profile completeness rules.

Return `plan.md` with exactly:

* Task Plan
* Repository Findings
* Proposed Changes
* API Surface
* Configuration
* File Delta
* Focused Tests
* Out of Scope

Keep `plan.md` within roughly 150 lines.
Organize Proposed Changes by capability or concern, not by file.
Keep File Delta to Modify / Add / Remove inventory only.
Do not modify repository files.

