---
name: project_vision
description: Product vision, roles, review rule, config layers and design guidelines live in docs/ — read before proposing features
metadata:
  type: project
---

Presserl is a self-hosted newspaper platform for children aged 6–16. The agreed concept
(2026-09-26) is written down in `docs/`: `vision.md` (principles, milestones),
`roles-and-workflow.md` (roles, review rule), `architecture.md` (stack, layout, config
layers, data model, API sketch), `design-guidelines.md` (layout, readability, editor,
print, theming). PlantUML sources in `docs/diagrams/`, rendered via Gerald's server
`https://plantuml.unterrainer.info/plantuml` (commit the SVGs next to the sources).

**Why:** Gerald's decisions — no advertising at all; checks only when several people are
involved (a solo publisher publishes and takes offline with one click); sensible defaults
everywhere, overridable per deployment/newspaper/section; forks touch only `deploy/`,
including CSS theming.

**How to apply:** Proposals must fit these principles; if a proposal contradicts `docs/`,
say so and update `docs/` inside the same OpenSpec change. The API sketch in
`architecture.md` is not a contract — `ai/primer/endpoints.md` is.
