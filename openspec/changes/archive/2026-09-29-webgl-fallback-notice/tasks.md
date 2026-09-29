## 1. Admin

- [x] 1.1 Add `wasmJsMain/resources/startup.js`. It checks WebAssembly and WebGL (webgl2, then webgl), injects `composeApp.js` when both pass, and otherwise renders the localized notice (de/en, `textContent` only, a single `#presserl-startup-notice`). Verify with `node --check startup.js`.
- [x] 1.2 In `startup.js`, show the generic "could not start" notice on `error`/`unhandledrejection` and on the script's `onerror` until `data-presserl-started` is set, ignoring errors from extension sources. Verify by review against design decision 3.
- [x] 1.3 Change `index.html` to load `startup.js` instead of `composeApp.js`. Verify that `./gradlew wasmJsBrowserDistribution` puts `startup.js` next to `index.html` in the distribution.
- [x] 1.4 In `Main.kt`, set `document.documentElement.dataset["presserlStarted"]` after the first frame (`LaunchedEffect` + `withFrameNanos`). Verify that `./gradlew check` passes.
- [x] 1.5 Add notice styles to `styles.css` (fixed, scrollable, readable in light surroundings, no effect on the print slip). Verify by screenshot in 3.2.
- [x] 1.6 Add a `wasmJsTest` test asserting that the bundled `index.html` references `startup.js` and not `composeApp.js` directly, or record in 3.2 why Karma cannot read it. Verify that `./gradlew check` passes.

## 2. Backend

- [x] 2.1 Add a `startup.js` stub to `backend/src/test/resources/META-INF/resources/admin/` and add `/admin/startup.js` to the entry-file caching test in `AdminDeliveryTest`. Verify that `./mvnw test -Dtest=AdminDeliveryTest` passes.

## 3. Verification

- [x] 3.1 Build the image (`docker build -t presserl:local .`) and run it locally per the e2e recipe so the admin bundle is served with the real CSP. Verify that `/admin/startup.js` returns 200 with `Cache-Control: no-cache`.
- [x] 3.2 Drive `/admin/` headless with Playwright. Verify with screenshots, and record the `securitypolicyviolation` events:
  - Firefox with `webgl.disabled=true` (locales `de-AT` and `en-GB`) shows the WebGL notice, `composeApp.js` is not requested, and there are no CSP violations.
  - Chromium with `--disable-webgl` shows the notice.
  - A normal Chromium run reaches the Keycloak login or the app with no notice.
- [x] 3.3 Simulate a start-up failure: block `*.wasm` requests with `page.route`. Verify that the generic notice appears.
- [x] 3.4 Stop every server and container started for these checks. Verify with `ps`, `ss` and `docker ps`.
