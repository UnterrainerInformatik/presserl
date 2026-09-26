---
name: feedback_evidence_over_speculation
description: When a problem is not pinpointed by existing logs, add targeted diagnostic logging and wait for a reproduction — don't theorise
metadata:
  type: feedback
---

If the user reports a misbehaviour ("X is slow", "Y didn't fire") and existing logs don't
show the cause, don't speculate. Propose targeted diagnostic logging (correlation ids per
request/event, timing per hop), get it deployed, wait for a reproduction, then analyse.

**Why:** In java-overmind-server a session burned ~30 minutes on plausible theories before
the real cause showed up in a few log lines once tracing existed. Gerald: "Do detailed
logging just for this scenario, turn it on and tell me to commit and build."

**How to apply:** Diagnostic logging is a code change → [[feedback_openspec_only_changes]].
Prefer a durable, switchable logger category over throwaway prints, and a small analysis
tool over ad-hoc awk. Say clearly what is known and what is guesswork.
