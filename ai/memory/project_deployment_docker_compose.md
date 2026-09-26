---
name: project_deployment_docker_compose
description: Deployments run as docker compose containers (presserl + postgres only); reverse proxy/TLS and Keycloak are external, brought by the operator
metadata:
  type: project
---

Presserl is always deployed as Docker containers orchestrated by `docker compose` — the
reference deployment in `deploy/` and every fork such as `../presserl-deployment`. The compose
file ships **only `presserl` and `postgres`**. We do **not** roll out a reverse proxy or a
Keycloak: the operator attaches their own. `INSTALL.md` explains how (Traefik labels; Caddy as
an optional guide) and the repo ships a realm template to import into an existing Keycloak.

**Why:** Gerald's decision (2026-09-26): he runs Presserl behind his Traefik (rules in the
deployment repo's compose) and uses his in-house Keycloak at `auth.unterrainer.info` with a
dedicated realm; others may prefer Caddy. Shipping a proxy or Keycloak would duplicate what
operators already have.

**How to apply:** No systemd/bare-JVM/Kubernetes paths. No proxy or Keycloak container in
`deploy/compose.yaml`; OIDC issuer, clients and secrets come from `.env`. Keycloak and the
reader/admin live on different origins, so CSP `connect-src` must allow the configured issuer.
Dev Services still start Keycloak locally for development. See [[project_deployment_repo]].
