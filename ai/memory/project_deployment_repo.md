---
name: project_deployment_repo
description: The real fork lives at ../presserl-deployment (name, .env, theme only) and is maintained by Claude
metadata:
  type: project
---

`../presserl-deployment` (next to this monorepo) is the first real Presserl deployment: it
copies the templates from `deploy/`, sets newspaper name, `.env` and `theme/`, and runs the
upstream container images. It contains no code. Created in M0, goes live in M7.

**Why:** Decided 2026-09-26 (`revise-product-plan`, D6): the fork is kept separate so upstream
stays generic, and Claude maintains it too.

**How to apply:** Changes to the deployment repo are planned and recorded in this monorepo's
OpenSpec changes — no separate spec tree there. Keep it minimal; anything reusable belongs in
`deploy/` upstream. Secrets stay out of git (`.env` is not committed). See [[project_vision]].
