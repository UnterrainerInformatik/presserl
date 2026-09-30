## Why

Children write for the newspaper mostly on phones, where the web admin app means typing a web
address, a username and a four-word pass-phrase. Every account slip printed since qr-slip-login
carries a QR code with exactly these three values; M8 turns that code into a one-scan login. The
Android target is the first half of M8 — a Google developer account exists, an Apple one does not,
so iOS follows later from the same code base.

## What Changes

- **Android app:** the Compose Multiplatform admin app gains an Android target (new Gradle module
  `admin/androidApp`, application id `info.unterrainer.presserl`). All screens of the web app work
  on Android; the platform pieces get Android implementations: login, image picking and camera,
  printing the account slip, date formatting, crypto. One app serves any presserl installation:
  the server is chosen at runtime, not built in.
- **Connect by scanning:** the start screen offers "Scan account slip". The app reads the slip's
  QR code (`<base>/qr?u=<username>#pw=<pass-phrase>`, unchanged), checks that `<base>` is a
  presserl server (`GET /api/client-config`) and logs in without any typing.
- **Connect by address:** as an alternative the user types the server address and logs in on the
  Keycloak page as in the browser (adults, lost slip).
- **Login in an embedded web view:** the Keycloak login page runs in a WebView owned by the app;
  after a scan the app fills in username and pass-phrase and submits once. Authorization code
  flow + PKCE as on the web, reusing the existing redirect URI `<base>/admin/…`, so **no change to
  the realm or the operator's Keycloak** is needed.
- **Staying logged in:** credentials from a scan are kept on the device, encrypted with a key
  from the Android Keystore, so the app logs in again by itself after a restart; logout deletes
  them. A rejected scan login deletes them too and leaves the user on the login page.
- **System back** navigates back within the app.
- **Release build:** a signed Android App Bundle built locally with an upload key kept outside
  the repository (`ai/secrets/`); version name and code maintained in the module.
- **Privacy policy:** a privacy policy for the app in German and English on the author's homepage
  (`../../../private/js/homepage`, `https://unterrainer.info`), linked from the app's start
  screen — needed for Google Play and useful before that.
- **Docs:** vision (M8 row), architecture (admin app on Android, login in the web view),
  `docs/roles-and-workflow.md` (hand-over with the app), `ai/memory/reference_build_and_test.md`
  (Android build, emulator), `ai/open-proposals.md` (Android entry replaced by the Play
  publishing follow-up).

## Capabilities

### New Capabilities
- `admin-android`: the Android app — choosing the server by scan or address, the QR login, stored
  credentials, logout, system back, the privacy policy link, and the Android variants of image
  picking and slip printing.

### Modified Capabilities
<!-- none: the web admin app, the REST API and the reader keep their behaviour -->

## Non-goals

- iOS (needs an Apple developer account; later change).
- Google Play publishing: store listing, Families policy questionnaire, data safety form, the
  mandatory closed test, Play App Signing enrolment and CI builds of the bundle (follow-up change).
- Opening the app directly from the phone camera (Android App Links need `assetlinks.json` on every
  installation's host); the phone camera keeps opening the reader's `/qr` in the browser.
- Several accounts or several newspapers at the same time; switching means logging out.
- Storing credentials typed on the Keycloak page (the app never sees them); such logins last as
  long as the Keycloak session.
- Reading the newspaper inside the app; the reader stays in the browser.
- A tablet- or phone-specific redesign of the screens; only layout breakage that blocks use on a
  phone is fixed.

## Impact

- **admin:** new module `admin/androidApp` (Android Gradle plugin, activity, manifest, resources,
  signing config); `composeApp` gains an Android target with `androidMain` actuals
  (`AuthClient` in a WebView, Keystore-backed credential store, photo picker + camera via
  `FileProvider`, `PrintManager` slip printer, `java.time` timestamps, `MessageDigest`/
  `SecureRandom`); common code gains the QR payload parser (counterpart of `SlipQr.payload`), the
  connect screen and a back-navigation hook; new dependencies: Google code scanner
  (`play-services-code-scanner`, no camera permission), `ktor-client-okhttp`, AndroidX activity/
  webkit/security. The Android target is configured only when an Android SDK is present, so the
  wasm build in the image (`Dockerfile`) and in CI is unchanged. Kotlin tests for parser and flow.
- **backend / reader:** none.
- **deploy:** none — realm, `.env` and compose unchanged; the app reuses the `presserl-admin`
  client and its `https://<host>/admin/*` redirect URI.
- **REST contract:** unchanged; `ai/primer/endpoints.md` unaffected.
- **homepage** (`~/source/private/js/homepage`, separate repo, deployed to `unterrainer.info`):
  new privacy policy route in German and English.
- **docs:** `docs/vision.md`, `docs/architecture.md`, `docs/roles-and-workflow.md`, `ai/memory/`,
  `ai/open-proposals.md`.
- **machine:** the Android SDK of Gerald's Android Studio (`/home/psilo/Android/Sdk`), missing
  components (emulator, system image with Google Play) added there; outside the repository.
