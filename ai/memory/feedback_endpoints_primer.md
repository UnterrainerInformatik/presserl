---
name: feedback_endpoints_primer
description: Whenever a REST endpoint is added, removed, or changes behaviour, update ./ai/primer/endpoints.md and the admin app's API client in the same change
metadata:
  type: feedback
---

When modifying any REST endpoint (route, method, params, request/response shape, role, side
effects), update `./ai/primer/endpoints.md` as part of the same change.

**Why:** In java-overmind-server the primer was the contract that briefed the separate
frontend sessions; drift meant a client was built against stale contracts. In this
monorepo the Compose administration app (`admin/`) lives next to the backend, so the primer,
the backend resource and the admin API client/DTOs must move together. The Qute reader is
rendered by the backend itself and does not consume `/api`.

**How to apply:** Treat the primer as part of the endpoint's definition of done. After
editing a JAX-RS resource or a DTO that crosses the wire, update the matching primer section
and the Kotlin API client/DTOs in `admin/`. See [[feedback_http_tests]].
