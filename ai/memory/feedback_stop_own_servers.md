---
name: feedback_stop_own_servers
description: Stop every server/daemon/container Claude started before finishing; verify with ps/ss/docker instead of assuming
metadata:
  type: feedback
---

Every server instance Claude starts (`quarkus:dev` with its Dev Services containers, the admin
dev server, Gradle daemons, Playwright containers, compose stacks) must be stopped again when the
work that needed it is done — and the shutdown must be verified.

**Why:** Gerald found two of Claude's servers running in parallel (2026-09-26) while Claude
believed none were running anymore.

**How to apply:** before finishing a turn that started servers, check `ps -eo pid,etime,cmd`,
`ss -ltnp` (8080, 8081, 8180) and `docker ps`; stop only what is Claude's own (e.g. Dev
Services postgres/keycloak/ryuk, `./gradlew --stop`), leave foreign processes and containers
alone, then check again. Before starting a server, check whether one of Claude's is still up.
