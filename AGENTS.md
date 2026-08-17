# Agent Rules

Work from the current repository state.

Source code, tests, configuration, migrations, and Git history are the primary implementation context. Documentation is lightweight supporting context, not a complete specification.

## Context

Follow the active prompt or `plan.md`. Read only documentation relevant to the active task, and inspect the affected source and Git state before making implementation assumptions.

## Scope

Keep changes focused on the active task.

Avoid unrelated refactoring, speculative abstractions, future-feature work, unnecessary dependencies, and new architectural patterns without a clear need. Do not invent unresolved business, security, API, or persistence behavior.

## PLAN

Inspect relevant source, Git history, and documentation. Make `plan.md` sufficient for a separate BUILD session and route it to any additional documentation it needs. Do not modify production code unless explicitly requested.

## BUILD

Use `plan.md` and the current repository state as primary context. Inspect affected files, follow established patterns, add or update relevant tests, and run applicable checks. Do not expand the approved scope; report repository evidence that conflicts with the plan.

## REVIEW

Review against the task, `plan.md`, Git diff, and relevant source and tests. Prioritize correctness, security, data integrity, regressions, and missing tests.

## Git

Read-only Git inspection is allowed. Git write operations, including commits, pushes, merges, rebases, resets, branch changes, and pull requests, require explicit instruction.
