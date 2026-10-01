## Why

The Android admin app (android-app-qr-login) builds a signed bundle locally, but nobody outside
the developer's phone can install it. Milestone M8 calls for Google Play: families should install
the app from the store and log in by scanning the account slip. A new personal Play developer
account must run a closed test with at least 12 testers for 14 days before production, so the
store setup, the release pipeline and the closed test have to start now; the production release
itself follows as a separate change once the closed test has run its course.

## What Changes

- `versionCode`/`versionName` of the Android app are derived from the release version the
  pipeline's bump job produces (`X.Y.Z`); local builds keep a fixed fallback.
- New pipeline job `android-release` on babylon5: runs only when `admin/` changed (or on a manual
  run), builds the signed release bundle with the upload key from GitHub secrets and uploads it
  with its R8 mapping file to the Play **internal** track via the Play Developer API (service
  account). Promotion to the closed or production track stays a manual step in the Play Console.
- Store listing kept in the repository (`admin/androidApp/play/`): German and English title, short
  and full description, 512×512 icon, 1024×500 feature graphic and phone screenshots taken on a
  real device.
- `docs/play-console.md`: prepared answers for every Play Console form — target audience (including
  children under 13, so the Families policy applies), data safety, content rating, ads, app access
  for reviewers, privacy policy URL, Families self-certification — and the check whether the Google
  Play services code scanner is permitted under the Families policy.
- App access for the Play review: a reviewer account on the staging newspaper
  `presserl.unterrainer.info`, which is reachable from the internet since 2026-09-30; the docs that
  still call staging "LAN/VPN only" (this repo and `../presserl-deployment`) are corrected.
- Manual Play Console tasks for Gerald: create the app, first bundle upload (registers the upload
  key, Play App Signing), service account with API access via Workload Identity Federation, GitHub secrets, forms, closed test
  track with testers once 12 are found.
- Backlog: the entries "M8 — Google Play publishing" and "Homepage: private legal notice, LeRoi's
  e-mail" (done in the homepage repo) are removed; a new entry "M8 — Play production release"
  takes over what follows the closed test.

## Non-goals

- Production release on Google Play (separate change after the 14-day closed test).
- Layout polish of the admin app on phones (own backlog entry).
- iOS target and App Store.
- A separate public demo instance; reviewers use staging.

## Capabilities

### New Capabilities
- `android-release`: how the Android app is versioned, built, signed and delivered to Google Play,
  and what the repository keeps for the store listing and the Play Console forms.

### Modified Capabilities
<!-- none: app behaviour does not change -->

## Impact

- **admin**: `admin/androidApp/build.gradle.kts` (version from Gradle properties), new
  `admin/androidApp/play/` (listing texts and graphics).
- **CI**: `.github/workflows/pipeline.yml` gets the `android-release` job; new repository secrets
  (`ANDROID_UPLOAD_KEYSTORE`, `ANDROID_UPLOAD_PROPERTIES` values) and variables for the release
  status and the Workload Identity Federation provider and service account.
- **docs**: `docs/play-console.md`; staging reachability in `.claude/CLAUDE.md`, `openspec/config.yaml`
  and `../presserl-deployment/README.md`.
- **deploy**: none in `deploy/`; staging gets a reviewer account (realm data, no file change).
- **backend, reader**: none.
- **ai/**: `ai/open-proposals.md`, build/test memory (release pipeline, Play).
- **External**: Google Play Console, Google Cloud service account and workload identity pool (Gerald).
