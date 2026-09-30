---
name: feedback_push_shared_ci_without_asking
description: At the end of /opsx:archive commit and push every repo the change touched, without asking, unless something is unusual
metadata:
  type: feedback
---

The archive step of an OpenSpec change ends with commit **and push** of every repo the change
touched — this monorepo, shared workflow repos (e.g. `UnterrainerInformatik/docker-build-workflow`)
and the deployment repos (`../presserl-deployment`, `../alexpresse`) when they are part of the
change. No separate question first; report what was pushed where.

**Why:** Gerald (2026-09-30): "Ich hätte einfach gerne am Ende eines archive-schrittes alle
beteiligten Repos im Normalfall gepusht, sonst muss ich das ohnehin immer schreiben." Earlier the
same day, "mach das bitte immer" when asked to approve a planned push to docker-build-workflow.

**How to apply:** "Im Normalfall" — still run the chosen tests first
([[feedback_tests_before_push]]) and stop and ask instead of pushing when something is off:
failing tests, unexpected diffs or foreign uncommitted work in a repo, a force push, or a push not
covered by the change. Pushes a change's tasks plan during apply (e.g. shared CI workflow fixes)
go ahead the same way. The phase-boundary pause before archive stays
([[feedback_clear_between_opsx_phases]]).
