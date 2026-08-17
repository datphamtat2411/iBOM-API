# Agent Rules

Work from the current repository state.

Source, tests, configuration, and migrations are the primary implementation context.

Keep work focused.
Avoid unrelated refactoring, speculative abstractions, future work,
unnecessary dependencies, and invented behavior.

## Documentation

Do not read repository docs by default.

Read docs only when:
- the prompt or `plan.md` routes to them; or
- implementation cannot proceed safely without them.

Do not copy large documentation sections into `plan.md`.

Inline small task-critical rules when cheaper than routing BUILD to an entire document.
Route to docs when broader context is genuinely needed.

## PLAN

PLAN owns discovery and task-specific decisions.

Inspect only what the task needs.
Do not inspect Git history unless materially required.

Keep `plan.md` concise.

Prefer:
- Repository Findings
- Relevant Code
- Proposed Changes
- Docs BUILD May Need

Add Risks or Verification only when useful.

Carry forward only the context BUILD needs.
Do not modify implementation files unless explicitly requested.

## BUILD

BUILD owns implementation, not discovery or review.

Read `plan.md` and affected files.
Read additional source only when implementation requires it.
Read docs only when routed by `plan.md` or required to proceed safely.

Do not repeat PLAN discovery or expand scope.

Implement the approved changes and add/update relevant tests.

Use one focused test command as the default verification.

If it passes:
- run no additional verification;
- perform one final Git status/diff safety check;
- report and stop.

If it fails:
- investigate only the task-related failure;
- fix it;
- rerun only the relevant focused test.

Do not by default:
- scan the repository/module;
- inspect Git history;
- repeatedly inspect Git status/diff;
- run the full test suite or full build/package;
- run startup, Swagger, curl, or manual API checks;
- inspect test reports when command output is sufficient;
- investigate unrelated environment failures;
- inspect dependency/JAR internals;
- perform REVIEW work.

Broader verification requires explicit task/plan instruction
or a task-related failure that focused checks cannot resolve.

BUILD reports only:
- changes made;
- focused test result;
- blockers or deviations.

## REVIEW

REVIEW is separate from BUILD.

Review `plan.md`, the relevant diff, affected source, and tests.
Prioritize correctness, security, data integrity, regressions,
scope deviations, and missing meaningful tests.

Do not modify implementation unless explicitly requested.

## Git

Read-only Git inspection is allowed when relevant.

Do not perform Git write operations unless explicitly requested.