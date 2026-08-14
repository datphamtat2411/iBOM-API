# OpenCode Agent Instructions

Default rules for OpenCode agents.

Narrower task-specific instgructions override these defaults.

## Core Principles

Work from the **current repository state**.

Never assume a dependency, component, convention, API, database structure, or feature exists before inspecting the repository.

Use:

```text
Task       → required change
Repository → implementation truth
Docs       → supporting context
```

Repository state overrides documentation and prior assumptions.

Load only context relevant to the current task.

## Inspect Before Acting

Before PLAN, BUILD, or REVIEW, inspect relevant source, existing patterns, tests, configuration, migrations, and Git state as needed.

Follow established repository patterns unless the task requires changing them.

## Scope

Make only changes required for the current task.

Avoid:

- unrelated refactoring or cleanup;
- future-feature implementation;
- speculative abstractions;
- unnecessary architecture changes;
- competing patterns without a requirement.

Small supporting changes are acceptable when required for correctness.

Keep the diff focused and explainable.

## Decisions

Make normal implementation-level decisions when repository evidence provides sufficient direction.

Do not invent unresolved:

- product behavior;
- business rules;
- major architecture;
- security or ownership policy;
- public API behavior;
- persistence semantics with wider impact.

Report important unresolved decisions instead of guessing.

---

# PLAN

1. Understand the task.
2. Inspect relevant repository state.
3. Determine existing behavior and patterns.
4. Identify affected areas, risks, and test needs.
5. Load additional context only when needed.
6. Produce a focused implementation plan.

Do not modify production code unless explicitly instructed.

Do not plan from documentation or assumptions alone.

---

# BUILD

1. Read the task and PLAN.
2. Re-inspect relevant repository state and verify important assumptions.
3. Implement the smallest correct change using existing patterns.
4. Add or update relevant tests.
5. Run applicable tests and checks.
6. Fix failures caused by the change.
7. Inspect the final diff.

Repository evidence overrides PLAN assumptions.

Do not expand scope or weaken existing behavior, validation, security, or tests merely to make checks pass.

Before finishing, report:

- what changed;
- tests/checks executed;
- important implementation decisions;
- unresolved issues, if any.

Leave the working tree ready for REVIEW.

---

# REVIEW

Independently evaluate:

```text
Original task
+
Git diff
+
Relevant surrounding source/tests
```

Prioritize:

1. incorrect or missing behavior;
2. security or data-integrity issues;
3. regressions;
4. incorrect assumptions;
5. missing or insufficient tests;
6. unnecessary scope;
7. maintainability issues.

Do not approve changes merely because they compile or tests pass.

Report concrete findings and affected areas when possible.

Do not modify code unless explicitly instructed to review and fix.

---

# Git

Git history is controlled by the human operator.

Read-only Git inspection is allowed.

Unless explicitly instructed, do not:

```text
git add
git commit
git push
git merge
git rebase
git reset
```

Do not create or switch branches or create Pull Requests.

Leave the final working-tree diff for human inspection.
