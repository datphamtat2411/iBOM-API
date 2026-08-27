# Agent Rules

Work from the current repository state.

Source, tests, configuration, and migrations are the primary implementation context.

Keep work focused.
Avoid unrelated refactoring, speculative abstractions, future work,
unnecessary dependencies, and invented behavior.

## Documentation

Do not read repository docs by default.

Read docs only when:

* the active prompt or `plan.md` routes to them; or
* work cannot proceed safely without them.

Do not copy large documentation sections into `plan.md`.

Inline small task-critical rules when cheaper than routing BUILD to an entire document.
Route to docs when broader context is genuinely needed.

## PLAN

PLAN owns discovery and task-specific decisions.

Inspect only task-relevant source, tests, configuration, migrations, and documentation.

Keep `plan` concise.

Prefer:

* Repository Findings
* Relevant Code
* Proposed Changes
* Docs BUILD May Need

Add Risks or Verification only when useful.

Carry forward only the context BUILD needs.
Do not modify implementation files unless explicitly requested.

## BUILD

BUILD owns implementation, not discovery or review.

Read `plan.md` and the files directly affected by it before editing.
Do not repeat PLAN discovery.

Expand context only when:

* additional source is required to implement the change; or
* affected source directly contradicts `plan.md`.

Read docs only when routed by `plan.md` or required to proceed safely.

Implement only the approved scope.
Add or update tests relevant to the changed behavior.

After implementation, run only the tests directly relevant to the task and changed behavior.

Do not run the full repository test suite, full module test suite, or unrelated tests unless explicitly required by the active prompt or justified in `plan.md`.

Prefer the narrowest test command that provides meaningful coverage of the change.

Do not run tests before implementation unless reproducing a reported failure is required.

If the focused test passes:

* do not run any additional or broader tests;
* do not perform any other verification;
* run one final `git status --short`;
* report and stop.

If the focused test fails:

* investigate only the task-related failure;
* make one corrective pass;
* rerun the same focused test command once;
* if it still fails, report the blocker and stop.

Do not by default:

* scan or rediscover the repository/module;
* run the full test suite or full build/package;
* run startup, Swagger, curl, or manual checks;
* inspect test reports when command output is sufficient;
* investigate unrelated environment failures;
* inspect dependency/JAR internals;
* perform REVIEW work.

Broader verification requires explicit justification in the active prompt
or a task-specific reason recorded in `plan.md`.

BUILD reports only:

* changes made;
* focused test result;
* blockers or deviations.

## Git

Git inspection is phase-specific.

PLAN may inspect Git history only when materially required for discovery.

BUILD may run the final `git status --short` defined above.
Do not otherwise inspect Git history or diffs unless explicitly required by the active prompt or `plan.md`.

REVIEW may inspect the relevant Git diff and related read-only Git state.

Do not perform Git write operations unless explicitly requested.
