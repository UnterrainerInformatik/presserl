## 1. Admin

- [x] 1.1 In `startup.js`, add `loadingText` (de/en) to `TEXTS`, show `#presserl-startup-loading` (`role="status"`, spinner element, text via `textContent`) 500 ms after injecting `composeApp.js` unless the app has started or a notice is shown, remove it via a `MutationObserver` on `data-presserl-started`, and remove it in `showNotice`. Verify with `node --check startup.js` and review against design decision 1.
- [x] 1.2 Add indicator styles to `styles.css` (fixed, centred, notice colours and font, `@keyframes` spinner, animation off under `prefers-reduced-motion: reduce`, hidden in print). Verify by screenshot in 4.2.
- [x] 1.3 Extend `StartupScriptTest` (or add a sibling test) that loads `startup.js` in the Karma browser with a stubbed slow `composeApp.js` or checks that the indicator appears after the delay and disappears when `data-presserl-started` is set; if Karma cannot drive this, record why in 4.2 and cover it there. Verify that `./gradlew check` passes.

## 2. Backend

- [x] 2.1 Check whether the Vert.x `StaticHandler` in Quarkus 3.33 can serve pre-compressed files; if it cannot, add the reroute route from design decision 3 (`Vary: Accept-Encoding` for `.wasm`/`.js` under `/admin/`, reroute to `<path>.br` when `br` is accepted and the resource exists, `Content-Encoding: br` and the original `Content-Type`). Verify with the tests in 2.3.
- [x] 2.2 Make `SecurityHeaders` derive `Cache-Control` from the path without a trailing `.br`. Verify with the tests in 2.3.
- [x] 2.3 Add Brotli stubs (`composeApp.js.br`, `0123456789abcdef0123.wasm.br`, produced with `brotli`) to the admin test resources and extend `AdminDeliveryTest`: Brotli-accepting `.wasm` request (headers, raw body equals the `.br` stub, immutable caching), request without `br` (original body, no `Content-Encoding`, `Vary`), `composeApp.js` with `br` (`no-cache`, JavaScript type), `304` on revalidation with `br`, `styles.css` with `br` unchanged, CSP present on rerouted responses. Verify that `./mvnw test -Dtest=AdminDeliveryTest` passes.

## 3. Deploy

- [x] 3.1 In the `Dockerfile` backend stage, install `brotli` and run `brotli --best --keep` on every `*.wasm` and `*.js` of the copied admin bundle. Verify that `docker build -t presserl:local .` succeeds and the image contains the `.br` files next to the originals.

## 4. Verification

- [x] 4.1 Run the image locally per the standalone e2e recipe. Verify with `curl` that `/admin/<hash>.wasm` with `Accept-Encoding: br` returns `Content-Encoding: br`, the build-time size and immutable caching, without `br` the original size, and that `composeApp.js` revalidates with `304`.
- [x] 4.2 Drive `/admin/` headless with Playwright (Chromium with SwiftShader, network throttled so the load takes several seconds): screenshot the indicator (`de-AT` and `en-GB`), confirm it disappears on the way to Keycloak or at the first frame, that a warm load never shows it, that a failed WebGL check shows only the notice, that blocking `*.wasm` replaces it with the "could not start" notice, and that no `securitypolicyviolation` events occur.
  - Result 2026-09-29 (local image): `bfa5…wasm` 8,640,316 → 2,618,182 bytes, `a0be…wasm` 5,853,684 → 1,295,415 bytes,
    `composeApp.js` 541,062 → 82,622 bytes with `br`; decoded bodies identical to the originals; immutable caching
    kept, without `br` the original size, `composeApp.js` revalidation `304` with `no-cache`.
  - Result 2026-09-29 (Playwright, Chromium/SwiftShader, ~10 Mbit/s): indicator appears after ~550 ms (`de-AT`
    "Die Verwaltung wird geladen …", `en-GB` "Loading the administration …"), is removed at the first frame
    (~3.8 s) before the redirect to Keycloak, and again at the first frame after login; warm load starts after
    ~220 ms without it; WebGL disabled shows only the WebGL notice; a failing `*.wasm` replaces it with
    "could not start"; spinner animation `none` under reduced motion; hidden in print; no
    `securitypolicyviolation` events. 1.3 is covered by Karma (`StartupScriptTest`).
- [x] 4.3 Stop every server and container started for these checks. Verify with `ps`, `ss` and `docker ps`.
- [x] 4.4 After the rollout, measure the transferred Wasm size and cold-load time on staging and alexpresse.net with `curl` and Playwright, and record the numbers in the change.
  - Result 2026-09-29, image 0.0.26 (Chromium/SwiftShader, empty cache, three runs each): staging transfers
    1,295,412 + 2,619,7xx bytes of Wasm with `Content-Encoding: br` (before: 14.5 MB uncompressed) and reaches
    Keycloak after 1.9–2.1 s; alexpresse.net transfers 1,295,6xx + 2,619,3xx bytes (before: about 4.9 MB of
    Cloudflare Brotli) and reaches Keycloak after 2.0–2.2 s (before: 4.7 s). The loading indicator was shown
    on every cold run. A `curl` right after the deploy still got Cloudflare's own Brotli (3,264,764 bytes for
    `bfa5…wasm`); minutes later Cloudflare passed the origin's variant through (2,618,182 bytes).
