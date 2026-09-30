---
name: reference_build_and_test
description: Verified dev/test/build commands for backend (Maven/Quarkus), admin (Gradle/Compose Wasm + Android), emulator, image, .http files and local e2e
metadata:
  type: reference
---

Verified 2026-09-26 on Gerald's machine (JDK 21, Docker running) unless marked otherwise.

- **Backend dev:** `cd backend && ./mvnw quarkus:dev` → http://localhost:8080; Dev Services start
  PostgreSQL and Keycloak (fixed port 8180, realm `presserl` from `src/main/resources/dev/`,
  admin console `admin`/`admin`, publisher `publisher`/`publisher`). No manual setup.
- **RustFS in dev/test (verified 2026-09-27):** `backend/compose-devservices.yml` (Compose Dev
  Services) starts `rustfs/rustfs:1.0.0` for `quarkus:dev` and every `@QuarkusTest` run (project
  `quarkus-devservices-backend`, own random-suffixed project for tests); its mapped host port lands
  in `presserl-dev.rustfs.port`, from which `%dev,test.presserl.media.s3.endpoint` is built.
  Credentials `presserl-dev` / `presserl-dev-secret`, bucket `presserl-media` created by the backend.
  Needs `docker compose` (v2) on the PATH; stopped together with dev mode / the tests.
- **LanguageTool in dev (verified 2026-09-29):** `quarkus:dev` starts `erikvl87/languagetool:6.8`
  from `backend/compose-devservices.yml` (compose profile `spell-check`, `%dev` only); `@QuarkusTest`
  runs do not start it and use `LanguageToolStub` (a global test resource) instead. The first checks
  after a cold start take longer than the 5 s timeout and answer `503`. `docker stop`/`start` of the
  dev container gives it a new host port the running backend does not know — simulate an outage
  with `docker pause`/`unpause` instead, or restart dev mode.
- **Backend tests:** `cd backend && ./mvnw verify` (needs Docker for Dev Services). Single class:
  `./mvnw test -Dtest=AdminDeliveryTest`. Surefire XML in `target/surefire-reports/` — check the
  file timestamp; stale reports from earlier runs stay there. `@QuarkusTest` binds port 8081 —
  stop the admin dev server first, or every Quarkus test fails with "Port already bound: 8081".
- **Admin dev:** `cd admin && ./gradlew wasmJsBrowserDevelopmentRun` → http://localhost:8081
  (calls the backend on :8080; `%dev` CORS allows it) — standard Kotlin/Wasm task, not re-run on 2026-09-26.
- **Admin dev server opens a browser:** `wasmJsBrowserDevelopmentRun` auto-opens the default
  browser at :8081, which redirects to the Keycloak login (:8180) and stays there — that window is
  not the automated test. Close it / stop the server when done.
- **Admin UI checks (verified 2026-09-27):** `mcr.microsoft.com/playwright:v1.55.0-noble`, `npm i
  playwright@1.55.0` in a scratch dir, `--network host`, log in via `#username`/`#password`/
  `#kc-login`. `ariaSnapshot()` shows buttons with names, header text fields as unnamed
  `textbox` (editor order: section chooser, kicker, headline, subheadline, lead). Clicks must go
  to the bounding box via `page.mouse.click` (the canvas intercepts locator clicks); the
  semantics tree can lag (e.g. a closed menu still listed) — verify with screenshots or the API.
- **Compose dialogs in Playwright (verified 2026-09-27):** there is no `dialog` role; while an
  `AlertDialog` is open the semantics tree holds only the dialog. After it closes the tree keeps
  the dialog's nodes indefinitely (waiting, mouse moves, wheel do not refresh it) — reload the page
  after each dialog and check in-place updates by screenshot or API, secrets from the network response.
- **Compose popups in Playwright (verified 2026-09-28):** while a `Popup` is open the semantics
  tree hides everything below it (locators time out) and keeps the popup's nodes after it closes.
  Record button bounding boxes before opening anything, click by coordinates, use a fresh page per
  scenario, and judge "closed" by screenshot. `page.mouse.click` moves and presses at once, so a
  late hover event can follow the click.
- **Admin dev server does not rebuild:** `wasmJsBrowserDevelopmentRun --continuous` kept serving the
  old bundle; stop and restart it after code changes. Stop it with
  `pkill -f "[w]asmJsBrowserDevelopmentRun"` (the bracket keeps pkill from matching its own shell).
- **.http response handlers:** `client.test(...)` callbacks run after the handler body, so read a
  global into a `const` before a later `client.global.set` overwrites it.
- **Admin tests:** `cd admin && ./gradlew check` (Karma, headless Chrome with SwiftShader via
  `composeApp/karma.config.d/`; Node cannot run the Compose runtime).
- **Image:** `docker build -t presserl:local .` from the repo root (~3 min cold). Needs the
  buildx plugin (`docker-buildx` package): the Dockerfile uses `$BUILDPLATFORM` and `RUN --mount`,
  which the legacy builder rejects ("failed to parse platform"). Verified 2026-09-29 with buildx 0.37.1.
- **.http files:** `cd http && docker run --rm --network host -v "$PWD":/workdir
  jetbrains/intellij-http-client --env-file http-client.env.json --env dev *.http`
  against a running `quarkus:dev`; `-V baseUrl=http://localhost:8090` overrides the env file.
- **When :8080 is taken (verified 2026-09-29, Gerald's long-running Vue dev server):**
  `./mvnw quarkus:dev -Dquarkus.http.port=8090`; serve the admin production bundle
  (`./gradlew wasmJsBrowserDistribution`, `composeApp/build/dist/wasmJs/productionExecutable`) with
  `python3 -m http.server 8081 --bind 127.0.0.1` (dev realm redirects allow :8081) and in Playwright
  `context.route('http://localhost:8080/**', r => r.fulfill({response: await r.fetch({url: <8090>})}))`
  — the app on :8081 hardcodes the API at :8080. Never stop the foreign :8080 process.
- **Local e2e of image + compose:** import `deploy/keycloak/presserl-realm.json` into the dev
  Keycloak under another realm name (hostname replaced), run `deploy/compose.yaml` with a
  `network_mode: host` override so the container reaches `localhost:8180`, drive the login with
  the `mcr.microsoft.com/playwright` image (Compose draws on a canvas: read texts via the
  accessibility snapshot, click by bounding box). Headless browsers need an explicit `locale`,
  otherwise Compose throws `RangeError: Incorrect locale information provided`.
- **Standalone e2e without quarkus:dev (verified 2026-09-29):** copy the realm with
  `https://presserl.example.org` replaced by `http://localhost:8090` and fixed `secret` fields on
  `presserl-backend`/`presserl-reader`; run `quay.io/keycloak/keycloak:26.5.7 start-dev
  --import-realm` on `-p 8180:8080`; compose override: presserl `network_mode: host`,
  `ports: !reset []`, `QUARKUS_HTTP_PORT=8090` (8080 is often taken), `PRESSERL_DB_HOST=localhost`,
  `PRESSERL_DB_PORT=55432`, `PRESSERL_MEDIA_S3_ENDPOINT=http://localhost:59000`; postgres/rustfs
  publish those ports on 127.0.0.1. Normal headless Chromium needs `--use-angle=swiftshader
  --enable-unsafe-swiftshader` for WebGL. Tear down with `docker compose -p <name> down -v`.

- **Android (verified 2026-09-30):** SDK `/home/psilo/Android/Sdk` ([[reference_machine_android_sdk]]),
  `admin/local.properties` with `sdk.dir` (git-ignored; without it Gradle builds the web app only).
  compileSdk 37 (Compose 1.12 requires it), targetSdk 36 (Play), minSdk 26, AGP 9.4.1. Package
  manager: `$ANDROID_HOME/cmdline-tools/latest/bin/android sdk install <path>` (`sdkmanager` still
  works, deprecated). AVD `presserl` (Pixel 7, API 36 Google Play). Start headless:
  `$ANDROID_HOME/emulator/emulator -avd presserl -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect &`,
  wait for `adb shell getprop sys.boot_completed` = `1`. Gerald's phone may be plugged in
  (unauthorized) — always `adb -s emulator-5554`, and `ANDROID_SERIAL=emulator-5554` for Gradle
  device tasks. Stop with `adb -s emulator-5554 emu kill`.
- **Android builds/tests:** `./gradlew :androidApp:assembleDebug`
  (`androidApp/build/outputs/apk/debug/`); `./gradlew check` also runs the common tests on the
  JVM (`testAndroidHostTest`; `ImageBitmap(w, h)` needs the Android framework there — use
  `FakeImageBitmap`); instrumented: `ANDROID_SERIAL=emulator-5554 ./gradlew :composeApp:connectedAndroidDeviceTest`.
  Release: `./gradlew :androidApp:bundleRelease` (R8, signed with `ai/secrets/android-upload.properties`;
  unsigned without it); on the emulator via bundletool (GitHub release jar) `build-apks --connected-device
  --ks ai/secrets/android-upload.jks --ks-key-alias upload` + `install-apks` (uninstall the debug app
  first: other signature). Release builds refuse `http`.
  Release crash stack traces: `logcat -b crash`, then `cmdline-tools/latest/bin/retrace
  androidApp/build/outputs/mapping/release/mapping.txt <trace>`. R8 full mode strips members that
  only reflection uses (ML Kit registrars, fixed in `androidApp/proguard-rules.pro`); `mapping.txt`
  omits unrenamed members — check the DEX (`unzip` the AAB's `base/dex`, `build-tools/*/dexdump`).
- **Android against quarkus dev:** `adb reverse tcp:8080 tcp:<quarkus port>` and `adb reverse tcp:8180 tcp:8180`,
  then the app uses `http://localhost:8080` exactly like the web app (dev realm redirect
  `http://localhost:8080/admin/*`). Debug intent: `adb shell "am start -n
  info.unterrainer.presserl/info.unterrainer.presserl.android.MainActivity --es qr
  'http://localhost:8080/qr?u=publisher#pw=publisher'"` logs in like a scan. Keycloak login events:
  enable at run time via admin API `PUT /admin/realms/presserl/events/config`.
- **Driving the emulator:** `adb shell uiautomator dump` lists Compose texts with bounds (tap the
  centre); `adb shell input text` fails on umlauts; Gboard autocorrects typed words; with the IME
  open, system back first closes it. Per-app language: `adb shell cmd locale set-app-locales
  info.unterrainer.presserl --locales de-AT` (`Locale.getDefault()` does not follow it — use the
  resources' configuration). The code scanner's close button cancels cleanly, system back in it
  reports `INTERNAL` (13).

See [[reference_machine_jdk]].
