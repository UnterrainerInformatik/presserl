---
name: feedback_tests_welcome
description: User likes tests — write them freely when adding or changing behaviour, no need to ask first
metadata:
  type: feedback
---

Adding or extending tests alongside code changes does not need permission.

**Why:** Gerald said so explicitly. Tests are a default part of the work.

**How to apply:** Backend: JUnit 5 + AssertJ, `@QuarkusTest` where the container is needed
(see the `java-test-quality` and `tdd` skills). Frontend: Vitest + Vue Test Utils. Cover the
path you touched; no coverage bikeshedding. For pure config/wiring, use judgment. Never strip
existing tests to simplify a refactor without flagging it.
