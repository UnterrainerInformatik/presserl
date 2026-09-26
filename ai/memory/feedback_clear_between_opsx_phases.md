---
name: feedback_clear_between_opsx_phases
description: Pause at each OpenSpec phase boundary so Gerald can run /clear — plan, propose, apply, and archive+commit are separate contexts
metadata:
  type: feedback
---

Stop and hand over at every OpenSpec phase boundary — after planning/diagnosis, after
`/opsx:propose`, after `/opsx:apply`, and before `archive + commit + push`. Say plainly that
the phase is done and that the next one should start in a fresh context. Claude cannot run
`/clear` itself, so the only correct move is to pause and let Gerald do it.

**Why:** stated by Gerald 2026-09-03 (java-overmind-server). The change artifacts carry
everything the next phase needs, so nothing is lost by dropping the conversation. A small
context keeps model performance up.

**How to apply:** at a boundary, summarise the result in a few lines, name the change, and
state the exact next command (e.g. "`/clear`, dann `/opsx:apply <name>`"). Do not start the
next phase in the same turn. Mid-phase is a bad cut — finish the phase first if it is nearly
done, and say why. See [[feedback_openspec_only_changes]].
