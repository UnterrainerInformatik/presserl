---
name: feedback_ui_tests_myself
description: Click through UI checks (manual-check tasks) myself with a headless browser instead of handing them to Gerald
metadata:
  type: feedback
---

Manual UI checks (e.g. "manual check in dev mode" tasks in an OpenSpec change) are Claude's job:
drive the admin app and the reader with a headless browser (Playwright/Puppeteer) and report the
result, instead of listing click steps for Gerald.

**Why:** stated by Gerald 2026-09-27 after an apply ended with "7.2 please check manually".

**How to apply:** start backend + admin dev servers, drive them with the Playwright container
described in [[reference_build_and_test]] (Compose draws on a canvas: read texts via the
accessibility snapshot, click by bounding box, set an explicit `locale`). Only mark the task done
when the scripted run showed the expected behaviour. Afterwards stop every server you started,
see [[feedback_stop_own_servers]].
