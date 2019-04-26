# Lint on Studio source code

As part of Project Marble we started running our Lint tool on the source code in `tools/base` and
`tools/adt/idea`. This is done via Bazel, so that every linted module gets a new Bazel test target
which will fail if a change introduces new Lint issues.

## FAQ

### Why are we doing this?
In short: to detect as many bugs as early as possible. The direct motivation was our work
on IDE hangs and freezes, where we hope to detect invalid threading behavior (blocking operations on
UI thread) statically. But if you can think of an anti-pattern related to Gradle, Windows etc., that
we could detect automatically, feel free to reach out or just implement the new check under
tools/base/lint/studio-checks.

### Which modules are affected?
Modules (iml_module Bazel targets) that specify a lint baseline file. You can get the current list
by running `bazel query 'kind("lint_test rule", //tools/...)'`.

### How do I run the Lint checks locally?
You can run Lint the same way as the presubmit checks by running `bazel test` on the right test
target, e.g. `bazel test //tools/adt/idea/android:intellij.android.core_lint_test`. These targets
produce a JUnit-style test.xml file with the results.

### What if Lint was wrong and is flagging correct code?
If you believe there's a bug in one of the checks, please consider fixing the check before
submitting your current change. If that's not practical, at least file a bug to investigate.

If you believe there's a good reason why a piece of code flagged by Lint should be submitted,
follow these steps:
1. Suppress the warning using `@SuppressWarnings` (in Java) or `@Suppress` (in Kotlin).
2. Add a comment in code explaining why the suppression is needed and why Lint was wrong.
3. Discuss this with your code reviewer.

### Pre-submit checks fail because the baseline is out of date, how do I fix this?
Modules which contain pre-existing violations of Lint checks have a file called `lint_baseline.xml`.
This file describes issues that are expected to be found (for now), so that we don't block unrelated
changes to older files. The plan is to eventually fix all historical violations and remove the
baseline files, but we're not there yet.

To prevent these files from getting out of date, we also checked if any violations present in the
baseline have been fixed. In this case the baseline needs to be updated, i.e. parts of it need to
be deleted. **NOTE:** don't add new issues to baseline files, instead follow the steps described
above for newly added violations.

Big refactorings or renaming existing files may cause the baseline to get out of date,
even if no new code is written.

To get your change to pass pre-submit checks, you can either delete the relevant sections of the
baseline file by hand, or (if it's not clear how to find them) replace the whole file with the new
baseline generated during the Lint run. The regenerated baseline can be found under bazel-out when
running locally (see output from the `bazel test` run for the exact path) or downloaded when running
on CI (under Artifacts/Archives/undeclared_outputs.zip).

### Which checks are run?
We run all the default checks that come with the Lint tool as well as a few custom checks, which you
can find in this module. The full list of additional checks can be found in `StudioIssueRegistry`.

### Can I write a new check?
Yes, if you can think of a bug that can be automatically detected, please consider writing a check
for it. See code in this module for examples of how to use the Lint API.

If you know a given anti-pattern appears frequently in our source code, write the check anyway.
Existing violations can be added to the baseline (see above) and the check will stop new ones from
being introduced.

### How is the Bazel integration for Lint implemented?
See `lint.bzl` for details.
