## 1. Admin — version and signing

- [x] 1.1 `admin/androidApp/build.gradle.kts`: read `presserl.version`, compute `versionName`/`versionCode` (D1), fail on malformed or out-of-range versions, fallback `0.0.0-local`/`1`; add `printAndroidVersion` task
- [x] 1.2 Signing config falls back to the `ANDROID_UPLOAD_*` environment variables when `ai/secrets/android-upload.properties` is missing (D4); local behaviour unchanged
- [x] 1.3 Check `printAndroidVersion` with `0.0.42` → `42`, `0.1.0` → `100000`, no property → `0.0.0-local`/`1`, `0.100.0` → build fails with the range message
- [x] 1.4 `./gradlew :androidApp:bundleRelease -Ppresserl.version=0.0.42` locally, signed with the env-var path (properties file temporarily out of reach) — verify the AAB's version and signature (`bundletool dump manifest`, `jarsigner -verify`)

## 2. CI — android-release job

- [x] 2.1 `pipeline.yml`: job `android-release` (`needs: bump`, babylon5) with the `admin/` change check (D2), setup-java 21, setup-android + explicit `sdkmanager` packages, setup-gradle (D3)
- [x] 2.2 Signing/Play secrets to `$RUNNER_TEMP`, env-var check before the build, `if: always()` cleanup step (D4)
- [x] 2.3 Build `bundleRelease -Ppresserl.version=<bump version>`, upload the AAB as workflow artifact (7 days), upload to Play via `r0adkll/upload-google-play` (pinned tag) with mapping file, track `internal`, status from `vars.PLAY_RELEASE_STATUS` defaulting to `draft` (D5)
- [x] 2.4 Confirm `dispatch-staging` still depends only on `bump` and `docker-build`

## 3. Store listing

- [x] 3.1 `admin/androidApp/play/listing/{de-DE,en-US}/`: title, short and full description (say that a presserl newspaper and a newsroom account slip are needed; nothing the app lacks)
- [x] 3.2 Icon 512×512 PNG and feature graphics 1024×500 per language (`feature-1024x500-{de-DE,en-US}.png`) copied from `icons/` via `icons/sync.sh` (done in app-icon-and-favicon)
- [x] 3.3 Prepare a demo state on staging (newspaper with a few sample articles and images, the `play-review` account of 5.1) for screenshots
- [x] 3.4 Screenshots on the A54 against staging, German and English phone language: start screen, scanner/slip login, "My articles", editor, media, account slip — at least 4 per language, no personal data on them
- [x] 3.5 `admin/androidApp/play/check.sh`: text length limits and image sizes/formats; run it

## 4. Play Console answers

- [x] 4.1 Check Google's current Families policy, Families SDK requirements and the ML Kit/code scanner data disclosure; record the result in `docs/play-console.md` — **stop and ask Gerald** if the code scanner is not allowed
- [x] 4.2 `docs/play-console.md`: target audience, ads, data safety, content rating, app access, privacy URL, news app, government/financial/health and any other declaration, each with answer and reason (D8)
- [x] 4.3 Compare the data safety answers with the privacy policy at `https://unterrainer.info/app/presserl/privacy`; list any mismatch for Gerald (homepage changes are outside this repo)
- [x] 4.4 Section "Release flow" in `docs/play-console.md`: first manual upload, `PLAY_RELEASE_STATUS` switch, promoting internal → closed, required secrets

## 5. Staging and reviewer access

- [x] 5.1 Create reporter account `play-review` on staging via the admin app, no trust entries, in a section without auto-approval; slip PDF to `ai/secrets/`
- [x] 5.2 Verify from outside (A54 on mobile data, Wi-Fi off): address login and QR login work, a submitted article waits for approval
- [x] 5.3 Correct "LAN/VPN only" for staging in `.claude/CLAUDE.md`, `openspec/config.yaml` and `../presserl-deployment/README.md` (plus the stale entrypoints comment in `../presserl-deployment/deploy/site.env`)

## 6. Gerald — Play Console and Google Cloud

- [ ] 6.1 Create the app in the Play Console (name "presserl", default language German, app, free)
- [ ] 6.2 Create a Google Cloud service account, enable the Play Android Developer API, invite it in Play Console → Users and permissions with release rights for this app; JSON key to `ai/secrets/play-service-account.json`
- [ ] 6.3 Set the repository secrets (`ANDROID_UPLOAD_KEYSTORE_BASE64`, `ANDROID_UPLOAD_STORE_PASSWORD`, `ANDROID_UPLOAD_KEY_ALIAS`, `ANDROID_UPLOAD_KEY_PASSWORD`, `PLAY_SERVICE_ACCOUNT_JSON`) — Claude may set them with `gh secret set` from `ai/secrets/` on Gerald's go
- [ ] 6.4 Upload the first AAB (workflow artifact of 2.3) to the internal track by hand; confirm Play App Signing enrolment
- [ ] 6.5 Fill in store listing (from 3.x) and all declarations (from 4.x); internal testers list (Gerald's own account at least)
- [ ] 6.6 Roll out the first internal release, set repository variable `PLAY_RELEASE_STATUS=completed`
- [ ] 6.7 Create the closed testing track with the tester list/Google Group and put the current release in it (start of the 14 days waits for 12 testers)

## 7. Verification

- [ ] 7.1 Push an admin change: the job uploads to internal, Gerald's phone gets the update from Play; job log shows no secrets, `$RUNNER_TEMP` files gone
- [ ] 7.2 Push a backend-only change: no Android upload, image and staging deployment as before
- [ ] 7.3 Internal build from Play: QR login against staging works (Play App Signing re-signs — check the code scanner and login work with the Play-signed build)

## 8. Docs and backlog

- [x] 8.1 `ai/open-proposals.md`: remove "M8 — Google Play publishing" and "Homepage: private legal notice, LeRoi's e-mail"; add "M8 — Play production release" (12 testers, 14 days, production access application, production rollout)
- [x] 8.2 Memory `reference_build_and_test.md`: release pipeline, version property, Play upload, `PLAY_RELEASE_STATUS`
- [x] 8.3 Push `../presserl-deployment` (README) in the archive step
