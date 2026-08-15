# Agent Rules

Work from the current repository state.

Source code, tests, configuration, migrations, and Git history are the primary implementation context.

Repository Markdown files provide lightweight supporting context only. Do not assume they contain the complete project specification.

## Context

For task work, follow the context explicitly provided by the active prompt or `plan.md`.

Do not load unrelated documentation by default.

Inspect the relevant source and Git state before making implementation assumptions.

## Scope

Keep changes focused on the active task.

Do not introduce:

* unrelated refactoring;
* speculative abstractions;
* future-feature work;
* unnecessary dependencies;
* new architectural patterns without a clear need.

Do not invent unresolved business, security, API, or persistence behavior.

## PLAN

Planning may inspect relevant source, Git history, and documentation.

The resulting `plan.md` should contain enough task-specific implementation context for a separate BUILD session and explicitly route BUILD to any additional documentation it needs.

Do not modify production code during PLAN unless explicitly requested.

## BUILD

Use `plan.md` and the current repository state as the primary context.

Inspect the files affected by the plan before modifying them.

Follow existing repository patterns where they exist.

Add or update tests relevant to the task and run applicable checks.

Do not expand the task beyond the approved plan.

If repository evidence conflicts with the plan, preserve the task intent and report the discrepancy rather than silently expanding scope.

## REVIEW

Review the implementation against:

* the task;
* `plan.md`;
* the Git diff;
* relevant source and tests.

Prioritize correctness, security, data integrity, regressions, and missing tests.

## Git

Read-only Git inspection is allowed.

Do not commit, push, merge, rebase, reset, create or switch branches, or create Pull Requests unless explicitly requested.
