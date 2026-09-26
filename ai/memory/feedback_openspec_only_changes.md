---
name: feedback_openspec_only_changes
description: Every code change goes through the OpenSpec workflow — start with opsx:propose, never patch directly
metadata:
  type: feedback
---

Code changes — backend, reader and admin app alike — are only made inside an OpenSpec change
(`/opsx:propose`, then `/opsx:apply`, `/opsx:verify`, `/opsx:archive`). Do not edit source
directly, not even for an obvious one-line bugfix.

**Why:** Gerald: "wir machen KEINE Changes außerhalb des Contextes eines opsx:propose. Wir
wollen die openspec-Spec nicht umgehen." The `openspec/` specs are the source of truth for
what the system does; a direct patch leaves the spec silently out of date.

**How to apply:**
- Announce the intent first ([[feedback_announce_changes_first]]), then `/opsx:propose` so
  proposal, design, delta spec and tasks exist before any file is touched.
- Implementation runs via the `/opsx:apply` skill — working `tasks.md` by hand as a plain
  checklist does not count.
- Amend specs by proposing, not inline: a new field or behaviour that the delta spec lacks is
  a change of its own. Ticking `tasks.md` checkboxes and adding findings files is fine.
- In this monorepo, a change that alters the REST contract covers backend, admin API client and
  `ai/primer/endpoints.md` in one change ([[feedback_endpoints_primer]]).
- This includes plan/doc revisions (`docs/`, diagrams, README, project config): Gerald
  (2026-09-26): "Alles wird bei uns über openspec abgewickelt." Memory files are the only
  exception.
- Read-only diagnostics are not restricted.
