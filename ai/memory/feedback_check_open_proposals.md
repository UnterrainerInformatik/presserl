---
name: feedback_check_open_proposals
description: Every time the user asks what is left to do, read ./ai/open-proposals.md first; delete entries once they become an opsx change
metadata:
  type: feedback
---

Whenever the user asks what there is to do ("gibt es was zu tun", "was steht an", "what's
next"), read `./ai/open-proposals.md` before answering and reconcile the answer against it.

**Why:** That file is the backlog of drafted-but-not-yet-proposed work — the only place a
pending item lives after its investigation ends. It must stay small to stay useful; once an
item is an OpenSpec change, OpenSpec is the record.

**How to apply:** Open `./ai/open-proposals.md`, then check `openspec/changes/` for items that
already have a change directory. Report open items cross-checked against the current code.
The moment an item becomes an `/opsx:propose` change, **delete its entry** — never tick it
off, strike it through or move it to a "done" section. See [[feedback_openspec_only_changes]].
