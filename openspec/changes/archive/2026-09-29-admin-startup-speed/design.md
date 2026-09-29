## Context

- `startup.js` (from `webgl-fallback-notice`) runs before the app: it checks WebAssembly and
  WebGL, injects `composeApp.js`, and shows `#presserl-startup-notice` on failure until `Main.kt`
  sets `data-presserl-started="true"` on `<html>` after the first frame. Nothing is shown while the
  Wasm modules download.
- The admin bundle is copied into `backend/src/main/resources/META-INF/resources/admin/` in the
  backend stage of the `Dockerfile` and served by Quarkus' static resource handler (Vert.x
  `StaticHandler`, Vert.x 4.5), which sets `Last-Modified` and answers `If-Modified-Since` with `304`.
  It has no support for pre-compressed files. HTTP compression in Quarkus is off.
- `SecurityHeaders` is a Vert.x route at `Integer.MIN_VALUE` that sets the CSP and, for `/admin/`,
  `Cache-Control` from the path (`.wasm` immutable, everything else `no-cache`) in a headers-end
  handler.
- Current bundle: `bfa5…wasm` 8.6 MB, `a373…wasm` 5.9 MB, `composeApp.js` 0.5 MB. Brotli
  quality 11: 2.6 MB + 1.3 MB. gzip -9: 3.3 MB + 1.7 MB. Cloudflare delivers 3.3 MB + 1.6 MB.

## Goals / Non-Goals

**Goals:**
- Visible feedback during a cold load, without a flash on warm loads.
- Fewer bytes for the Wasm download everywhere, including behind proxies that do not compress.
- No change to the caching contract (immutable `.wasm`, revalidated rest, `304` on revalidation).

**Non-Goals:**
- Serving pre-compressed files outside `/admin/`, or compressing reader/API responses.

## Decisions

### 1. Loading indicator lives in `startup.js` and `styles.css`
After injecting `composeApp.js`, `startup.js` starts a 500 ms timer. When it fires and the app has
not started and no notice is shown, it appends `#presserl-startup-loading` (`role="status"`, a CSS
spinner element and a localized text via `textContent`) to `<body>`. A `MutationObserver` on the
`data-presserl-started` attribute of `<html>` removes it at the first frame; `showNotice` removes
it before it shows a notice. The texts go into the existing `TEXTS` table (`loadingText`).
Styles in `styles.css`: fixed, centred, same colours and font as the notice, spinner as a CSS
`@keyframes` rotation that is switched off under `prefers-reduced-motion: reduce`.

Alternatives: static markup in `index.html` hidden by Compose (rejected: it would also show on
failed checks and needs Kotlin code to remove it); a Compose-drawn indicator (impossible: nothing
Compose draws exists before the Wasm download finishes); polling the attribute (rejected:
`MutationObserver` is event-driven and available wherever WebAssembly is).

### 2. Brotli at image build time in the `Dockerfile`
The backend stage installs `brotli` and, after copying the admin bundle, runs
`brotli --best --keep` on every `*.wasm` and `*.js` under `META-INF/resources/admin/`. The admin
Gradle build stays unchanged; dev mode serves the bundle from the webpack dev server and is not
affected.

Alternatives: dynamic compression in Quarkus (`quarkus.http.enable-compression`) (rejected: it
compresses 14.5 MB on every uncached request, at a quality far below 11, costing CPU on small
hosts); a Gradle task in the admin build (rejected: needs a Brotli implementation in the admin
toolchain for output only the image uses); gzip in addition (rejected: every browser that runs
the admin app sends `br` over HTTPS).

### 3. Serve the variant by rerouting to the `.br` file
A new Vert.x route next to `SecurityHeaders` (same class or a sibling in `web/`, ordered after it)
handles `GET` and `HEAD` for paths under `/admin/` ending in `.wasm` or `.js`:

- It always adds `Vary: Accept-Encoding` to the response.
- If `Accept-Encoding` lists `br` with a non-zero q-value and a classpath resource
  `META-INF/resources/admin/<name>.br` exists (looked up once and cached per path), it registers a
  headers-end handler that sets `Content-Encoding: br` and the `Content-Type` of the original
  extension (`application/wasm`, `text/javascript;charset=UTF-8`), and calls
  `ctx.reroute(path + ".br")`.
- Otherwise it calls `ctx.next()` and the original file is served as before.

The static handler then serves the `.br` file and keeps doing `Last-Modified`, `304`, `HEAD` and
ranges. `SecurityHeaders` derives `Cache-Control` from the path with a trailing `.br` removed, so
the compressed variant keeps the original caching.

Alternatives: writing the `.br` bytes from a custom handler (rejected: re-implements
conditional requests, `HEAD` and ranges); a reverse-proxy rule in Traefik/Caddy (rejected: every
deployment would need it, and it is not part of the image).

## Risks / Trade-offs

- [Reroute runs the router again; headers-end handlers could be registered twice or the CSP route
  could see the `.br` path] → both set idempotent header values; `AdminDeliveryTest` asserts the
  exact headers of a rerouted response.
- [The static handler's own `Content-Type` for `.br` wins over ours] → the headers-end handler
  runs last and overwrites it; asserted in tests.
- [Cloudflare fetches from the origin without `br` or recompresses anyway] → no regression: it
  then compresses itself as today. The transferred size on alexpresse.net is measured after the
  rollout.
- [Image grows by about 4.5 MB, the build by the Brotli run] → accepted; quality 11 on 15 MB takes
  well under a minute.
- [A browser sees a stale cached `.wasm` from before the change] → `.wasm` names are content
  hashes; the rebuilt bundle has new names or identical bytes, so decoded content is the same.

## Migration Plan

Ships with the next image; no configuration and nothing to do in the deployment repositories.
Rollback: deploy the previous image.
