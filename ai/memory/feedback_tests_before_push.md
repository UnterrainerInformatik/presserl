---
name: feedback_tests_before_push
description: CI does not run tests; before commit/push run locally the tests relevant to the change — not blindly every suite
metadata:
  type: feedback
---

Run tests locally before committing and pushing — the GitHub pipeline does not run them. Pick
the tests that make sense for the change (see [[reference_build_and_test]] for the commands):
test classes of the touched packages/features plus whatever depends on the changed code (e.g. a
new DB migration or changed shared DTOs widen the circle); skip suites the change cannot affect
(e.g. no admin tests for a backend-only change).

**Why:** Gerald (2026-09-26): the tests take far too long in CI, and the pipeline only feeds
staging. Gerald (2026-09-27): "tests immer nur die laufen lassen, die auch Sinn ergeben. Nicht
immer alle blind." Gerald (2026-09-27): full suites only before a release, once things are
done; while building up, running ~10 min of tests after every step within a milestone is
paralysing.

**How to apply:** Before any commit/push (e.g. the archive+commit step of an OpenSpec change),
state which tests were chosen and why, run them and report the results; do not push with
failing tests. Run the full suites (`./mvnw verify`, `./gradlew check`) only before a release or
when Gerald asks.
