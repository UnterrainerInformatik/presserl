---
name: feedback-prefer-a54-over-emulator
description: For Android checks, Gerald's Samsung A54 via adb is always fine and preferred over the emulator
metadata:
  type: feedback
---

Gerald's Samsung A54 (adb serial `RZCW911HTLA`) may always be used for Android checks instead of the emulator, even when a task names the emulator.

**Why:** faster than the emulator and needs less RAM on the dev machine (Gerald, 2026-09-30).

**How to apply:** when the A54 shows up in `adb devices`, install and test on it without asking; fall back to the emulator only when the phone is not connected or the check needs emulator-only features. Related: [[reference-build-and-test]].
