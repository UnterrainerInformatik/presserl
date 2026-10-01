---
name: project-android-play-publishing-resume
description: Paused state of the android-play-publishing change (2026-10-01) — what is done, what waits on Gerald, leftovers on staging/phone
metadata:
  type: project
---

Change `android-play-publishing` paused on 2026-10-01 at 23/33 tasks; Gerald commits the work so far himself. Resume with `/opsx:apply android-play-publishing`.

- Done by Claude: everything except groups 6 and 7 (3.4 screenshots de-DE/en-US, 5.2 outside check on mobile data both passed).
- Next: 6.1–6.7 are Gerald's Play Console / Google Cloud steps. Offered for 6.3: set the four `ANDROID_UPLOAD_*` repo secrets with `gh secret set` from `ai/secrets/` on his go; `PLAY_SERVICE_ACCOUNT_JSON` needs the key from 6.2 at `ai/secrets/play-service-account.json` first.
- Then 7.1–7.3 (verification via Play, incl. the real camera scan with the Play-signed build); 8.3 is done (`../presserl-deployment` pushed as 22b8fa1 on 2026-10-01).
- Leftovers: staging article "Reachability test from mobile data" by `play-review` waits in the publisher's approval queue (reject/delete it or keep it as reviewer example); the A54 app is logged in as `play-review`.

**Why:** the change spans days because Play Console work and the 12-tester closed test are external.
**How to apply:** read this at resume, then delete this file when the change is archived. Related: [[reference-build-and-test]], [[feedback-clear-between-opsx-phases]].
