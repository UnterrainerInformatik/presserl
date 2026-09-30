## Context

- `admin/` is a single Gradle module `composeApp` with a `wasmJs` target only. The platform
  surface is small: `AuthClient` (web: `BrowserAuthClient`, full-page redirects), `SlipPrinter`
  (`BrowserSlipPrinter`, DOM + print CSS), `expect` functions `pickImageFiles`, `formatTimestamp`/
  `formatDate`, `sha256`/`secureRandomBytes`, and `Main.kt` wiring `ApiClient`, auth and `App`
  with `siteUrl` = the page origin. `FocusGuard` is a browser workaround with no Android
  counterpart.
- Kotlin 2.4.20, Compose Multiplatform 1.12.1, Ktor 3.6.0; `qrose-encoder-matrix` and
  `richeditor-compose` publish Android artifacts.
- The image (`Dockerfile`, stage `gradle:9.8.0-jdk21`) runs `wasmJsBrowserDistribution` in
  `admin/` without an Android SDK; CI builds only that image. Gerald's Android Studio installs the SDK at
  `/home/psilo/Android/Sdk` (the only SDK on the machine); `/dev/kvm` exists (emulator with hardware acceleration possible).
- The realm's public client `presserl-admin` allows redirect URIs `https://<host>/admin/*`,
  PKCE `S256`, no direct access grants. Keycloak's default login theme names its fields
  `#username`, `#password`, the button `#kc-login`, and marks errors with `#input-error` /
  `.alert-error` (`kc-feedback-text`). The operator's Keycloak must not need changes.
- Slip QR payload (qr-slip-login D1): `<base>/qr?u=<username>#pw=<pass-phrase>`; username rule
  `^[a-z0-9]+(-[a-z0-9]+)*$`; pass-phrase four German words joined by `-` (may be changed by the
  user later, so the parser does not pin the word format).

## Goals / Non-Goals

**Goals:**
- One Android app for all installations, logging in from the slip with zero typing.
- No change to backend, realm, `.env` or the image build.
- Maximum reuse: every screen stays in `commonMain`; Android adds only actuals and the connect flow.

**Non-Goals:**
- Play publishing, CI bundle builds, App Links, iOS (proposal non-goals).

## Decisions

### D1 — Module layout: `composeApp` as KMP library, new `androidApp` application module
Since AGP 9 an Android application cannot live in a KMP module; `composeApp` gets an Android
library target (`com.android.kotlin.multiplatform.library`, `androidMain` source set) and a new
module `admin/androidApp` (`com.android.application`) holds `MainActivity`, the manifest, icons,
`FileProvider` paths, signing and version. The wasm target and its tasks stay unchanged.
Versions: the AGP release matching Gradle 9.8 / Kotlin 2.4.20; `compileSdk`/`targetSdk` = the
newest stable API level Google Play requires at apply time; `minSdk` 26.
*Alternative:* everything in `composeApp` with `com.android.application` — deprecated with AGP 9.

### D2 — Android only with an SDK present
`settings.gradle.kts` includes `:androidApp` and `composeApp` configures its Android target only
when an SDK is found (`ANDROID_HOME`/`ANDROID_SDK_ROOT` or `sdk.dir` in `local.properties`).
The image and CI therefore build exactly as before; verified by `docker build`.
*Alternative:* installing the SDK in the Docker admin stage — slower image builds for nothing the
image ships.

### D3 — Login in a WebView the app controls (user's choice)
`AndroidAuthClient` implements `AuthClient` with the same code flow + PKCE as the web (shared
pieces: `Pkce`, `OidcDiscovery`, `TokenResponse`, the token request moves to common code as
`TokenClient` so both clients use it). It shows the authorization URL in a full-screen
`WebView` composable. `redirect_uri` = `<base>/admin/` — already registered, so no realm change.
`shouldOverrideUrlLoading` intercepts every navigation starting with the redirect URI, never loads
it, checks `state`, and exchanges the `code` via Ktor (OkHttp engine). Logout loads the
end-session URL in the WebView with `post_logout_redirect_uri` = `<base>/admin/` (intercepted the
same way) and then clears the WebView's cookies.
RFC 8252 recommends the system browser over embedded web views because a third-party app could
read typed credentials; here the app is the first-party client, it already holds the credentials
from the slip, and only the installation's own issuer is loaded. JavaScript is enabled for the
issuer page (Keycloak needs it); file and content access are off; no JS interface is exposed.
*Alternatives:* password grant with a new client (needs the operator to change the realm);
Custom Tab + `login_hint` (the child still types the pass-phrase).

### D4 — Autofill and submit exactly once
After a scan the client holds `(username, passPhrase)`. On `onPageFinished` for a URL on the
issuer's host, if a `#password` field exists and no submission happened yet, it runs a script
that sets `#username` and `#password` (values passed as JSON string literals, never concatenated
raw), dispatches `input` events and clicks `#kc-login`, then marks the attempt as used. If a page
with `#password` loads again after that, the credentials were rejected: the client deletes stored
credentials, leaves the page visible for typing and reports `CredentialsRejected`, which the UI
shows as a notice above the web view. One attempt per scan keeps Keycloak's brute-force protection
from locking the account. A theme without these element ids simply shows the login page for typing.

### D5 — QR payload parser in common code
`SlipQr.parse(text): SlipCredentials?` is the inverse of `SlipQr.payload`: parses with Ktor's
`Url`, requires path ending in `/qr`, query `u` matching the username pattern, fragment `pw=`
non-empty (percent-decoded), scheme `https` (or `http` in debug builds, passed in as a flag);
`base` = scheme, host, port and the path before `/qr`. Anything else → `null`. Exhaustive tests
in `commonTest` including the round trip `parse(payload(...))`.

### D6 — QR scanning with the Google code scanner
`play-services-code-scanner` (`GmsBarcodeScanning`, format `QR_CODE`) shows Google Play services'
own scanner UI: no `CAMERA` permission in the manifest, decoding on the device, nothing leaves the
phone. Devices without Play services cannot scan and use the address field.
*Alternative:* CameraX + ML Kit bundled / ZXing — own camera UI and the camera permission for the
same result.

### D7 — Connection state and stored credentials
A common `Connection` model drives a new start flow before `App`: `NotConnected` (start screen) →
`Connecting(base, credentials?)` (check `client-config`) → `LoggingIn` (web view) → `App(auth,
api, siteUrl = base, …)`. The Android activity creates `ApiClient` per chosen base. Persistence
(`ConnectionStore`): the base address in `SharedPreferences`; credentials (username, pass-phrase)
encrypted with AES-GCM under a key in the Android Keystore (`KeyGenParameterSpec`, no user
authentication required), stored only after a successful scan login; `allowBackup=false` and
data-extraction rules exclude the app's data. Logout clears both. The web app keeps its current
entry (`Main.kt` unchanged apart from the shared token code).
*Alternative:* offline refresh token (`offline_access`) — depends on the realm's client scopes and
session limits; the pass-phrase is printed on the slip anyway and a reset invalidates it.

### D8 — System back through a common hook
`App` exposes its back behaviour (route stack pop / screen's cancel action) through a common
`BackHandler` from Compose Multiplatform's navigation-event API (`androidx.compose.ui.backhandler`
or its successor in 1.12). Where a screen has no back action the app is left.

### D9 — Android actuals
- `pickImageFiles(camera=false)`: `PickMultipleVisualMedia` (JPEG/PNG/WebP), `camera=true`:
  `TakePicture` into a cache file shared via `FileProvider`; bytes read lazily through
  `ContentResolver`, name and size from `OpenableColumns`. Activity result launchers bridged to
  `suspend` via a small registry owned by `MainActivity`.
- `SlipPrinter`: builds a small HTML document (escaped text, QR as inline SVG from the same
  `QrCode` runs as the web printer), loads it into an off-screen `WebView` and hands
  `createPrintDocumentAdapter` to `PrintManager`.
- `formatTimestamp`/`formatDate`: `java.time` with the device locale's short format, matching the
  wasm output (`dd.MM.yyyy HH:mm` for German).
- `sha256`/`secureRandomBytes`: `MessageDigest`, `SecureRandom`.
- UI glyph check (`checkUiGlyphs`) stays as is; `androidMain` strings come from the shared
  resources.

### D10 — Release signing outside the repository
`androidApp` reads `ai/secrets/android-upload.properties` (store file, passwords, alias) when it
exists; without it only debug builds are possible. The upload keystore is created once with
`keytool` and kept in `ai/secrets/` (git-ignored); Gerald keeps a copy. `versionCode`/
`versionName` live in `androidApp/build.gradle.kts` (`1` / `0.1.0`), raised by hand per upload
until the Play change automates it. Output: `./gradlew :androidApp:bundleRelease` →
`androidApp/build/outputs/bundle/release/androidApp-release.aab`.

### D11 — Privacy policy on the homepage
New route `/app/presserl/privacy` in the homepage (Vue 2, vue-i18n, locale files
`src/locales/parts/presserlPrivacy_{de,en}.ts`), language chosen by the site's language switch.
The app links to `https://unterrainer.info/app/presserl/privacy?lang=de|en` from the phone
language; the page honours `lang` if the homepage's i18n allows it, otherwise the task adds it.
Content: controller for the app (Gerald Unterrainer, contact from the imprint), that the app sends
nothing to the developer (no analytics, ads, crash reporting or tracking), what stays on the
device (server address, encrypted slip credentials) and how to delete it (logout, uninstall), that
all newspaper data (account, articles, images) goes to the server of the newspaper the user
connects to, whose operator is responsible for it, QR scanning on the device via Google Play
services, children's use through accounts created by their parents/newsroom. The homepage change
is committed in its repo; pushing (= deploying to `unterrainer.info`) only after Gerald's go.

## Risks / Trade-offs

- [Keycloak theme with other element ids (operator's custom theme)] → autofill does nothing, the
  user types; documented in the architecture doc.
- [Embedded web view against RFC 8252 / future Keycloak blocking of web views] → accepted for a
  first-party client; switching to password grant later needs only a new `AuthClient`.
- [Stored pass-phrase on a shared family phone] → same exposure as the paper slip; logout deletes
  it; Keystore key never leaves the device.
- [Emulator cannot really "scan" paper] → the emulator's virtual scene camera shows an image of
  the slip; a debug-only intent extra `qr` injects a payload for automated checks; a final scan
  with a real phone is part of the manual check.
- [AGP/Compose version mismatch with Kotlin 2.4.20] → first task pins working versions before any
  code is written.
- [Phone layouts] → some screens (media grid, editor toolbar) may be cramped; only blocking
  breakage is fixed here, the rest goes to the backlog.

## Migration Plan

Nothing on servers changes; existing slips work with the app as they are. Rollback = uninstall;
the web admin app is unaffected.
