---
name: feedback_announce_changes_first
description: Never edit code unasked — describe the intended change first and wait; a diagnosis request means diagnose only
metadata:
  type: feedback
---

Before touching any source file, tell the user what you intend to change and why, and wait.
A request to investigate ("schau warum X nicht läuft", "warum ist das so?") is a request for
a **diagnosis**, not for a fix — deliver the analysis and stop there.

**Why:** Carried over from java-overmind-server (2026-08-01): a correct diagnosis was followed
by an unrequested patch. Gerald: "Ich hab Dich um eine Diagnose gebeten und nicht nach
changes... fixen können wir das später." Unrequested edits cost review time and pre-empt his
decision about *whether* and *when* to fix.

**How to apply:** Investigate freely — reading code, logs, read-only DB queries, running tests
and builds need no permission. The line is at *writing*: source edits, DB writes, config
changes. Present root cause plus proposed fix in prose, let the user decide, then go through
[[feedback_openspec_only_changes]].

This extends into an approved `/opsx:apply`: if a task's literal wording conflicts with a
documented architectural decision found while reading the code (a comment citing a prior
change, an established invariant), stop that task and present the conflict with file/line
evidence plus options (AskUserQuestion) — neither implement it literally nor silently deviate.
Update design.md/tasks.md/proposal.md to match the decision before continuing.
