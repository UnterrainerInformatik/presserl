## Context

`admin/composeApp/src/wasmJsMain/resources/index.html` loads `composeApp.js` directly. It loads
the Wasm module, and `Main.kt` calls `ComposeViewport(document.body!!)`, which needs a WebGL
context for Skiko. Without one, Skiko fails asynchronously inside a coroutine and nothing is
drawn. The admin CSP is `script-src 'self' 'wasm-unsafe-eval'` and `style-src 'self'` plus
hashes of the style Compose injects (`csp-style-hashes.txt`). Inline scripts and styles are
blocked, and this change keeps it that way. Every `/admin/` file except `*.wasm` is served with
`no-cache` (`SecurityHeaders`, admin-shell spec).

## Goals / Non-Goals

**Goals:**
- Detect the missing prerequisites before the Wasm download starts, so users do not wait for
  megabytes that cannot run.
- Show one small, dependency-free fallback that works even when Kotlin/Wasm never runs.

**Non-Goals:**
- Recovering from errors after the first frame. Those are app bugs, not start-up failures.
- Telling the user which browser setting or extension exactly caused it.

## Decisions

**1. A loader script replaces the direct `composeApp.js` tag.**
`index.html` references only `startup.js`. The script runs the checks and, if they pass,
appends `<script src="composeApp.js">` to the document. That is a same-origin script element,
which `script-src 'self'` allows.
- *Alternative:* keep both tags and let `startup.js` run first. Rejected: `composeApp.js` would
  still load and crash, and its failure would race with the notice.
- *Alternative:* do the check in Kotlin before `ComposeViewport`. Rejected: it cannot cover
  "WebAssembly unsupported" or a failed Wasm load, and it still downloads the whole bundle first.

**2. Checks.**
- WebAssembly: `typeof WebAssembly === "object"` and `WebAssembly.instantiate` exists.
- WebGL: `document.createElement("canvas").getContext("webgl2")`, falling back to
  `getContext("webgl")`. The probe mirrors what Skiko requests. A `null` result or an exception
  counts as "no WebGL".

In Firefox with fingerprinting protection the probe itself triggers the address-bar permission
prompt. The notice therefore says to allow WebGL there and then reload.

**3. First-frame signal from the app.**
Inside the `ComposeViewport` content, `Main.kt` runs `LaunchedEffect(Unit) { withFrameNanos {} }`
and then sets `document.documentElement.dataset["presserlStarted"] = "true"`. Until that
attribute is present, `startup.js` treats the following as start-up failures and shows the
generic notice:
- `error` and `unhandledrejection` events on `window`
- the `onerror` of the injected `composeApp.js` script

The Skiko failure in the reported log surfaces as an uncaught `RuntimeException`, so it is caught
by this path as well, in case the WebGL probe ever passes but context creation still fails. Once
the attribute is set, the listeners do nothing.

**4. Notice rendering.**
The notice is built with `createElement` and `textContent`, without `innerHTML`, into
`<div id="presserl-startup-notice" role="alert">`. It holds a heading, one explanatory paragraph
and, for WebGL, a list of remedies. It is appended to `body`. The styles live in `styles.css` as
a `position: fixed` scrollable panel above everything else, because `body` has
`overflow: hidden` for Compose. A second failure does not add a second notice.

**5. Localization.**
Compose resources are not available outside the app, so the texts live in `startup.js` as two
small dictionaries (`de`, `en`). The language rule is the same as the app's: English when
`navigator.language` starts with `en`, German otherwise.

**6. Tests.**
- **Backend:** the test classpath stubs of `index.html` gain a `startup.js` stub, and
  `AdminDeliveryTest` asserts `no-cache` for it.
- **Admin:** a Kotlin/Wasm test in `wasmJsTest` checks that the bundled `index.html` references
  `startup.js` and not `composeApp.js`, if the Karma setup can read the resource. Otherwise this
  moves to the manual check.
- **Browser behaviour:** checked end to end with Playwright against a backend-served bundle,
  because only that path has the CSP:
  - Firefox with `webgl.disabled = true`
  - Chromium with `--disable-webgl`
  - a normal run
  - `de-AT` and `en-GB` locales
  - a listener for `securitypolicyviolation` events

## Risks / Trade-offs

- [The probe passes, but Skiko needs a feature it lacks (e.g. WebGL1 only)] → The first-frame
  guard catches the resulting error and shows the generic notice.
- [One extra request and a sequential load before `composeApp.js`] → `startup.js` is a few KB
  and revalidated with `no-cache`. This is negligible next to the Wasm module.
- [An extension-injected error arrives before the first frame and shows the generic notice
  although the app would have started] → Only errors whose `filename` is same-origin, or has no
  filename (Wasm/runtime errors), are counted. Errors from `moz-extension:` or
  `chrome-extension:` sources are ignored.
- [The dev server on :8081 serves the same `index.html`] → `startup.js` lives in the same
  resources folder, so dev runs keep working.

## Migration Plan

This is shipped with the next image. `presserl-deployment` redeploys automatically, and
`alexpresse` picks it up by merging upstream. Rollback is the previous image. No data or
configuration is involved.
