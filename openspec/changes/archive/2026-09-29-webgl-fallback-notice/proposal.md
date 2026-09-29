## Why

The admin app draws its whole interface through WebGL (Compose Multiplatform / Skiko). When a
browser has WebGL disabled or blocked — Firefox `webgl.disabled`, `privacy.resistFingerprinting`,
LibreWolf/Mullvad defaults, CanvasBlocker-style extensions, locked-down school machines — the
app crashes during start-up ("Exception while trying to handle coroutine exception") and the
page stays blank white. The user gets no hint what is wrong; this happened on alexpresse.net.

## What Changes

- The admin page no longer loads `composeApp.js` directly. A small same-origin start-up script
  checks the prerequisites first (WebAssembly, a WebGL context) and only then loads the app.
- When a prerequisite is missing, the page shows a plain HTML notice instead of a blank page:
  what is missing and how to allow it (allow WebGL for this site in the address bar, set
  `webgl.disabled` to `false`, add an exception in a fingerprinting/canvas blocker).
- When the app fails to start for another reason (script or Wasm load failure, an uncaught error
  before the first frame), the page shows a generic "could not start" notice with a reload hint.
- The notice is localized (German by default, English for English-preferring browsers), styled
  from `styles.css`, and works under the existing admin CSP — no inline scripts or styles, no CSP
  change.

## Non-goals

- No rendering fallback (no non-WebGL/HTML version of the admin app).
- No change to the reader, which is server-rendered HTML and does not need WebGL.
- No change to the Content-Security-Policy.
- No browser-specific detection of *why* WebGL is off; the notice lists the common remedies.

## Capabilities

### New Capabilities

### Modified Capabilities
- `admin-shell`: new requirement "Start-up failures show a notice instead of a blank page";
  the entry-file caching scenario also names the new start-up script.

## Impact

- **admin**: `wasmJsMain/resources/index.html` (loads the start-up script instead of
  `composeApp.js`), new `wasmJsMain/resources/startup.js`, notice styles in `styles.css`,
  `Main.kt` signals the first rendered frame.
- **backend**: none in main code (the new file is served like the other entry files with
  `no-cache`); the admin delivery test stubs gain the start-up script and a caching assertion.
- **reader, deploy, docs**: none. `docs/architecture.md` unchanged (CSP untouched).
- **Deployment repos**: nothing to change; they pick up the new image.
