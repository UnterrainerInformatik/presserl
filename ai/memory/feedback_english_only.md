---
name: feedback_english_only
description: All identifiers, comments, log messages, docs and commit messages in this repo are English
metadata:
  type: feedback
---

Everything in the repo — Java and Kotlin identifiers, comments, log messages, docs,
OpenSpec artifacts, commit messages — is English. No German.

**Why:** Project-wide rule stated by Gerald.

**How to apply:** Anything you add or rewrite is English. User-facing UI text is a separate
concern: it belongs in i18n resources (`en`/`de`) — Qute message bundles for the reader,
Compose resources for the admin app — never hard-coded in templates or composables.
