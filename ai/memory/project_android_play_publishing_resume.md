---
name: project-android-play-publishing-resume
description: Paused state of the android-play-publishing change (2026-10-01) — what is done, what waits on Gerald, leftovers on staging/phone
metadata:
  type: project
---

Change `android-play-publishing` paused on 2026-10-01; resumed the same evening, now 26/33 tasks. Resume with `/opsx:apply android-play-publishing`.

- Done: everything up to 6.3. 6.2 uses Workload Identity Federation instead of a JSON key (Gerald's Google Cloud org enforces `iam.disableServiceAccountKeyCreation`): pool `github`, provider `presserl`, SA `play-publisher`, restricted to this repo's `master`. Repo variables `GCP_WORKLOAD_IDENTITY_PROVIDER` / `PLAY_SERVICE_ACCOUNT` and the four `ANDROID_UPLOAD_*` secrets are set (Gerald set the secrets himself — the auto-mode classifier blocks Claude's secret-store writes).
- Next: push the WIF pipeline change, run the pipeline manually (a `.github/`-only push skips the Android build), first AAB from the workflow artifact → Gerald uploads by hand (6.4), then 6.5–6.7 (Gerald), then 7.1–7.3.
- Leftovers: staging article "Reachability test from mobile data" by `play-review` waits in the publisher's approval queue (reject/delete it or keep it as reviewer example); the A54 app is logged in as `play-review`.

**Why:** the change spans days because Play Console work and the 12-tester closed test are external.
**How to apply:** read this at resume, then delete this file when the change is archived. Related: [[reference-build-and-test]], [[feedback-clear-between-opsx-phases]].
