---
name: feedback_http_tests
description: When REST endpoints are added or changed, create/update .http test files and run them
metadata:
  type: feedback
---

When a REST endpoint is added, changed or changes behaviour, create `.http` files under
`http/` covering it and run the requests against a live backend (`quarkus dev`).

**Why:** Catches breakage in serialization, routing and headers that unit tests miss.

**How to apply:** Don't claim "done" on a REST change without exercising it this way.
Pairs with [[feedback_endpoints_primer]].
