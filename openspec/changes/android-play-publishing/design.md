## Context

android-app-qr-login delivered the Android app (`admin/androidApp`, application id
`info.unterrainer.presserl`, minSdk 26, targetSdk 36, compileSdk 37, AGP 9.4.1) with release
signing from `ai/secrets/android-upload.properties` (design D10 there) and a privacy policy on
`unterrainer.info`. `versionCode`/`versionName` are hard-coded (`1` / `0.1.0`).

The pipeline (`.github/workflows/pipeline.yml`) runs `bump` (shared bump-semver-workflow, tag
`X.Y.Z`, currently `0.0.37`, patch bumped on every push), `docker-build` on babylon5 and
`dispatch-staging`. It ignores pushes touching only `openspec/`, `ai/`, `docs/`, `http/`. Runners are
self-hosted containers (`myoung34/github-runner:ubuntu-noble`) with the host's docker socket; the
`babylon5` label pins jobs to the fast host. No Android SDK is installed there.

Gerald's personal Play developer account exists. New personal accounts must run a closed test with
at least 12 opted-in testers for 14 consecutive days before they can apply for production access.
The app targets children under 13 as well (Gerald's decision), so the Families policy applies.
Staging `presserl.unterrainer.info` is reachable from the internet since 2026-09-30 via the
`presserl-ext` router in `babylon5:~/scripts/traefik/rules.yml`.

## Goals / Non-Goals

**Goals:**
- Monotonic, traceable version codes without hand edits.
- Every admin change reaches internal testers through Google Play without manual building.
- Everything needed to re-enter or check the Play Console (listing, graphics, declarations and their
  reasoning) lives in the repository.
- The closed test can start as soon as 12 testers exist.

**Non-Goals:**
- Production release, staged rollouts, in-app update prompts (follow-up change).
- Phone layout polish, iOS.
- A dedicated public demo instance.

## Decisions

### D1 — Version from Gradle properties, code `X*10^7 + Y*10^5 + Z`
`androidApp/build.gradle.kts` reads `presserl.version` (Gradle property, `-Ppresserl.version=X.Y.Z`
from CI). `versionName` = the string; `versionCode` = `X * 10_000_000 + Y * 100_000 + Z`, failing the
configuration when `Y > 99` or `Z > 99_999` (or when the string is not `X.Y.Z`). Without the property:
`0.0.0-local` / `1`. Highest code with Play's limit 2_100_000_000: major 209 — far away.
The computation is a small function in the build script; a `printAndroidVersion` task prints
name and code, and verification runs it with the spec's example versions (a separate build-logic
module with unit tests is not worth it for one formula).
*Alternative:* GitHub `run_number` — independent of the visible version, reset if the workflow is
renamed. *Alternative:* `X*10^6+Y*10^3+Z` — patch is bumped on every push and would pass 999 within
months.

### D2 — Path filter inside the pipeline, not a separate workflow
The `android-release` job lives in `pipeline.yml`, `needs: bump`, parallel to `docker-build`, so it
gets the same release version. A first step compares `github.event.before..github.sha` with
`git diff --name-only` (full fetch) and ends the job early (`changed=false` output, remaining steps
skipped) when nothing under `admin/` changed; `workflow_dispatch` always builds. A new branch push or
a forced push with an unknown `before` counts as changed. `dispatch-staging` keeps depending only on
`bump` and `docker-build`, so an Android failure never blocks staging.
*Alternative:* a separate workflow with `paths: admin/**` — would need its own bump and produce a
second tag per push.

### D3 — Android SDK on the runner via setup actions
Job steps: `actions/checkout` (fetch-depth 0), `actions/setup-java` (Temurin 21, matches the machine
JDK rule), `android-actions/setup-android` (cmdline-tools; accepts licences), then
`sdkmanager "platforms;android-37" "build-tools;<agp default>"` explicitly so AGP's auto-download is
not relied on, `gradle/actions/setup-gradle` for the Gradle cache. Runs on
`[self-hosted, Linux, X64, babylon5]`. Wasm is not built: `./gradlew :androidApp:bundleRelease`
only configures the Android path. The image build's Gradle and this job do not share caches.
*Alternative:* a container image with the SDK (`cirruslabs/android-sdk`) — tag lags compileSdk 37,
and job containers on these runners would need the docker socket dance again.

### D4 — Signing from secrets, written to `$RUNNER_TEMP` and removed
Secrets: `ANDROID_UPLOAD_KEYSTORE_BASE64` (the `.jks`, base64), `ANDROID_UPLOAD_STORE_PASSWORD`,
`ANDROID_UPLOAD_KEY_ALIAS`, `ANDROID_UPLOAD_KEY_PASSWORD`, `PLAY_SERVICE_ACCOUNT_JSON`. The build
script gets a second source for the signing config: when `ai/secrets/android-upload.properties` is
missing it looks at the environment variables `ANDROID_UPLOAD_STORE_FILE`,
`ANDROID_UPLOAD_STORE_PASSWORD`, `ANDROID_UPLOAD_KEY_ALIAS`, `ANDROID_UPLOAD_KEY_PASSWORD`. The job
decodes the keystore to `$RUNNER_TEMP/upload.jks`, writes the service account JSON to
`$RUNNER_TEMP/play.json`, and an `if: always()` step deletes both. Secrets are only passed through
`env:`, never echoed; GitHub masks them additionally. A release build in CI without a signing config
fails (`bundleRelease` would otherwise produce an unsigned bundle Play refuses later and less
clearly): the job checks the env vars before building.

### D5 — Upload with `r0adkll/upload-google-play`
Pinned to a release tag; inputs `packageName: info.unterrainer.presserl`, `releaseFiles` = the AAB,
`mappingFile` = `androidApp/build/outputs/mapping/release/mapping.txt`, `track: internal`,
`status: ${{ vars.PLAY_RELEASE_STATUS || 'draft' }}`, `releaseName: X.Y.Z`. While the app has never
been reviewed, Play accepts only `draft` releases via the API; once the first internal release is
rolled out by hand, Gerald sets the repository variable `PLAY_RELEASE_STATUS=completed` and later
uploads reach testers automatically.
*Alternative:* Gradle Play Publisher plugin — puts Play credentials and listing sync into the build;
more moving parts than one upload step, and listing sync is not wanted while the forms are filled by
hand.

### D6 — First upload by hand, Play App Signing
The Play Developer API cannot create an app. Gerald creates the app in the Console (default
language German, app, free), then uploads the first AAB manually — the artifact of the first
`android-release` run (the job also uploads the AAB as a workflow artifact, retention 7 days) — to the
internal track. With that upload Play enrols the app in Play App Signing (Google-generated app
signing key) and registers our key as upload key. Gerald keeps `ai/secrets/android-upload.jks` backed
up; a lost upload key is reset via Play support, not fatal.

### D7 — Store listing as plain files
`admin/androidApp/play/listing/{de-DE,en-US}/{title,short-description,full-description}.txt`,
`admin/androidApp/play/graphics/icon-512.png`, `feature-1024x500-{de-DE,en-US}.png`,
`phone-screenshots/{de-DE,en-US}/NN-*.png`. Layout follows the fastlane/Gradle Play Publisher
convention so a later automated sync can take it over unchanged. Icon and feature graphics are
copies of the brand artwork in `icons/` (`play-icon-512.png`, `feature-graphic-{de,en}.png`),
distributed by `icons/sync.sh` (app-icon-and-favicon): the icon 512×512 full bleed, one feature
graphic per listing language with the brand icon, "Presserl" and the claim in that language,
1024×500 without alpha. Screenshots: taken with `adb exec-out screencap` on Gerald's A54 against staging with a
demo newspaper state (start screen, scan, "My articles", editor, media, account slip) — one set per
language by switching the phone language. A small check script (`admin/androidApp/play/check.sh`)
verifies text lengths and image sizes (`file`/`identify`).

### D8 — Play Console answers in `docs/play-console.md`
One section per declaration with the exact answer and its reason. Key answers, to be confirmed at
apply time against Google's current forms:
- **Target audience:** age groups 5–8? no; 9–12, 13–15, 16–17, 18+ — children from school age write
  for the family/school newspaper; the app does not appeal primarily to children (it is a tool for a
  newsroom), so "Designed for Families" is not joined; mixed audience means Families policy rules
  still apply.
- **Ads:** none. **In-app purchases:** none.
- **Data safety:** the app itself collects nothing for the developer; data typed into the app goes
  to the newspaper server the user chose, run by a third party (the newspaper's operator) — Play's
  definition counts data sent off the device by the app as "collected" even to other servers unless
  it is end-to-end user-initiated transfer to a service the user chose; answer and reasoning written
  out, including the Play services code scanner (on-device, no image leaves the device) and
  encrypted transport (https only in release).
- **Content rating (IARC):** user-generated content is shared with other users of the same newspaper
  only after approval; no unmoderated public chat.
- **App access:** reviewer account on staging (D9).
- **Privacy policy:** `https://unterrainer.info/app/presserl/privacy`.
- **News app declaration:** presserl is a tool to write a newspaper, not a news publisher — answer
  "not a news app", reasoning recorded.
- **Families / code scanner:** Families policy requires that SDKs used in apps for children comply
  (no ads SDKs outside the self-certified list, no collection of device identifiers from children).
  `play-services-code-scanner` is a Google Play services API, not an ads SDK; ML Kit docs state that
  it may send usage metrics to Google. The apply task checks Google's current Families SDK and ML
  Kit data disclosure pages and records the result; if the scanner turns out not to be allowed, the
  change stops at that task and Gerald decides (bundled ML Kit without metrics or ZXing → own
  change).

### D9 — Reviewer account on staging
A reporter account `play-review` in the staging newspaper, created through the admin app like any
other account, with no trust entries (so every article waits for approval) and a section where
nobody auto-approves. Its slip (address, username, pass-phrase, QR code) goes to the Console's "App
access" instructions as text plus the QR image; the slip PDF itself stays in `ai/secrets/`. Staging is
redeployed with every image but keeps its database, so the account survives. Reachability is checked
from outside (phone on mobile data, Wi-Fi off) before the first review submission.
The docs that still say staging is LAN/VPN-only are corrected: `.claude/CLAUDE.md`,
`openspec/config.yaml` context, `../presserl-deployment/README.md` (routing stays in Traefik's
`rules.yml`, not in the deployment repo).

### D10 — Closed test without a deadline in this change
The change prepares the closed track (`Closed testing - Alpha`, tester list as e-mail list or Google
Group, opt-in link) and ends when the track exists and the first release is in it. Starting the
14 days needs 12 testers; that and the production application go into the new backlog entry
"M8 — Play production release".

## Risks / Trade-offs

- [Play API only accepts drafts until the first manual rollout] → `PLAY_RELEASE_STATUS` variable,
  documented switch in `docs/play-console.md`.
- [Families policy rejects the code scanner] → explicit check task with a stop; the address field
  works without scanning, so the app remains usable while a replacement is planned.
- [Reviewers hit staging while it redeploys] → a redeploy takes seconds; reviewers retry. Staging
  with every image is bleeding edge; review submissions are rare.
- [Reviewer account misused on staging] → reporter rights only, everything waits for approval;
  lock the account after the review if needed.
- [SDK download on every run slows the job] → setup-android caches in the runner's tool cache;
  measure the first runs, add an `actions/cache` for `~/.gradle` if needed.
- [Secrets in the runner workspace of shared self-hosted runners] → `$RUNNER_TEMP` plus `always()`
  cleanup; the runners are ours only (org-scoped).
- [Upload key lost] → backup by Gerald; Play support can reset the upload key under Play App Signing.

## Migration Plan

1. Merge with `android-release` in place; first run builds and uploads a draft (or fails at upload
   until the app exists — the job then still provides the AAB artifact for the manual first upload).
2. Gerald creates the app, uploads the artifact, sets secrets/variable.
3. Re-run the pipeline manually; draft upload succeeds; roll out internal; set
   `PLAY_RELEASE_STATUS=completed`.
Rollback: remove the job from `pipeline.yml`; Play releases stay as they are.

## Open Questions

- Exact wording of current Play forms (target audience age bands, news declaration) — checked in
  the Console at apply time and recorded in `docs/play-console.md`.
