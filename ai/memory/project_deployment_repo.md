---
name: project_deployment_repo
description: Two deployment repos, no code: ../presserl-deployment = staging (auto-deployed), ../alexpresse = first public fork alexpresse.net (merged from upstream); both maintained by Claude
metadata:
  type: project
---

Two deployment repositories sit next to this monorepo. Each copies the templates from `deploy/`,
sets newspaper name, `.env` and `theme/`, and runs the upstream container images. Neither
contains code.

- `../presserl-deployment` (GitHub `UnterrainerInformatik/presserl-deployment`) — **staging**,
  `presserl.unterrainer.info` on babylon5, public since 2026-09-30 (Google Play review account):
  the deployment's labels give the internal router only, the external entrypoints come from the
  `presserl-ext` router in `babylon5:~/scripts/traefik/rules.yml` — never put routing into the
  deployment repo, it is the template for forks. Upstream's
  `dispatch-staging` job redeploys it after every image build.
- `../alexpresse` (GitHub `guFalcon/alexpresse`, remote `upstream` = presserl-deployment) —
  **first public fork**, the family newspaper *Alex-Presse* at `alexpresse.net` /
  `www.alexpresse.net`, realm `alexpresse` on `auth.unterrainer.info`. Upstream does **not**
  dispatch to it: it is updated by `git pull upstream master` + push (redeploys the latest
  release tag) or a manual workflow run. It carries its own theme (OÖN-inspired look).

**Why:** Decided 2026-09-26 (`revise-product-plan`, D6) that forks stay separate so upstream stays
generic. On 2026-09-28 (`first-fork-alexpresse`, D1) the topology was recorded: staging gets every
image, the public newspaper only what Gerald merges — a natural promotion step.

**How to apply:** Changes to either repo are planned and recorded in this monorepo's OpenSpec
changes — no separate spec tree there. Keep them minimal; anything reusable belongs in `deploy/`
upstream. Secrets stay out of git (`.env` is not committed). Every push to either repo redeploys a
live site — push it in the archive step of the change that touched it
([[feedback_push_shared_ci_without_asking]]); ask first for any push outside that. See [[project_vision]] and
[[project_deployment_docker_compose]].
