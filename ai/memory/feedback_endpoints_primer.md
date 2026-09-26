---
name: feedback_endpoints_primer
description: Whenever a REST endpoint is added, removed, or changes behaviour, update ./ai/primer/endpoints.md and the frontend client in the same change
metadata:
  type: feedback
---

When modifying any REST endpoint (route, method, params, request/response shape, role, side
effects), update `./ai/primer/endpoints.md` as part of the same change.

**Why:** In java-overmind-server the primer was the contract that briefed the separate
frontend sessions; drift meant the frontend was built against stale contracts. In this
monorepo the frontend lives next to the backend, so the primer, the backend resource and the
frontend API client/types must move together.

**How to apply:** Treat the primer as part of the endpoint's definition of done. After
editing a JAX-RS resource or a DTO that crosses the wire, update the matching primer section
and the TypeScript types/client in `frontend/`. See [[feedback_http_tests]].
