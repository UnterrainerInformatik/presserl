## Why

The Android start screen shows "No presserl newspaper was found at this address" for every failed
`GET <base>/api/client-config`, whether the server gave a wrong answer or could not be reached at all.
It also logs nothing. In the real-phone check of android-app-qr-login (2026-09-30) the cause was the
wrong VPN: DNS returned the public address, where the proxy answers `404`. The screen looked exactly
as it does for a mistyped address, and only `adb` checks (DNS, `nc`) found the cause.

## What Changes

- The connection check tells two failures apart:
  - **Server not reachable**: no HTTP answer at all (DNS failure, connection refused, timeout,
    TLS error). The app shows a new message that names the address it tried and suggests
    checking the network or VPN.
  - **No presserl newspaper**: the server answered, but not with a valid client config (4xx/5xx,
    not JSON, missing OIDC fields). The existing message stays.
- The check gets a finite timeout, so an address that swallows packets ends in "not reachable"
  instead of a spinner that never stops.
- Every failed check logs its cause (exception class and message, HTTP status). On Android the log
  goes to `logcat`. Credentials and pass-phrases are never logged.
- `ConnectionModelTest` covers both kinds of failure.

## Non-goals

- No automatic retry and no detection of *which* VPN is missing.
- The web admin app is unchanged. It is served by the server it talks to, so it never shows the
  start screen.
- No change to the address rules (`https` only in release builds) or to the slip handling.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `admin-android`: requirement "Start screen chooses the server" distinguishes an unreachable
  server from an address without presserl.

## Impact

- **admin** (common code used by Android): `ui/connect/ConnectionModel.kt` (error classification,
  timeout, logging), `ui/connect/StartScreen.kt` (new message with address), string resources
  de/en, a small `expect`/`actual` log function (Android `Log`, wasm `console`),
  `ConnectionModelTest`.
- backend, reader, deploy: not affected. REST contract unchanged, so the endpoints primer is untouched.
- docs: not affected (no document lists the start-screen messages).
