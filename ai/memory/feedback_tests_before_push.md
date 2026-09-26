---
name: feedback_tests_before_push
description: CI does not run tests; run backend and admin tests locally before every commit and push
metadata:
  type: feedback
---

Run the full test suites locally (`backend: ./mvnw verify`, `admin: ./gradlew check`, see
[[reference_build_and_test]]) before committing and pushing. The GitHub pipeline does not run them.

**Why:** Gerald (2026-09-26): the tests take far too long in CI, and the pipeline only feeds staging.

**How to apply:** Before any commit/push (e.g. the archive+commit step of an OpenSpec change),
run both suites and report the results; do not push with failing tests.
