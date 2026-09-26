---
name: project_vision
description: Product vision, roles, approval chain, config layers and design guidelines live in docs/ — read before proposing features
metadata:
  type: project
---

Presserl is a self-hosted newspaper platform for children aged 6–16. The concept (revised
2026-09-26 by the `revise-product-plan` change) is written down in `docs/`: `vision.md`
(principles, milestones M0–M8), `roles-and-workflow.md` (roles, accounts, approval chain,
trust, emergency brake), `architecture.md` (stack, layout, config
layers, data model, API sketch), `design-guidelines.md` (layout, readability, editor,
print, theming). PlantUML sources in `docs/diagrams/`, rendered via Gerald's server
`https://plantuml.unterrainer.info/plantuml` (commit the SVGs next to the sources).

**Why:** Gerald's decisions — no advertising at all; roles Publisher (parents) >
Editor-in-chief (child) > Section editor > Reporter > Reader; approval chain with per-person
trust (a solo first account publishes with one click); accounts without e-mail, pass-phrase
on a printed slip; one newspaper per server, public by default; reader = Qute HTML, admin
app = Compose Multiplatform; sensible defaults everywhere; forks live in their own
deployment repo (see [[project_deployment_repo]]).

**How to apply:** Proposals must fit these principles; if a proposal contradicts `docs/`,
say so and update `docs/` inside the same OpenSpec change. The API sketch in
`architecture.md` is not a contract — `ai/primer/endpoints.md` is.
