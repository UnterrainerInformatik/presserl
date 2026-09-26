---
name: feedback_immutable_migrations
description: Never edit an applied DB migration (Flyway/Liquibase) — not even comments; add a correction migration
metadata:
  type: feedback
---

An applied database migration is immutable. To change anything about it — including a
comment or remark — add a new correction migration.

**Why:** Migration tools checksum each migration. In java-overmind-server (2026-09-01) a
repo-wide `sed` rename touched two applied Liquibase changesets; the checksum mismatch made
the server fail at startup and crash-loop for ~20 minutes. Gerald: "you have to write a
correction-changeset for such things."

**How to apply:** Before any mechanical text replacement across the repo, exclude append-only
history (`backend/src/main/resources/db/**`). If an applied migration was edited, revert it
byte-for-byte and express the new intent in a fresh migration. Never patch checksums in the
history table to get past it.
