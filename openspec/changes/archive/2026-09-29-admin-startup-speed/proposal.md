## Why

On a first visit (or after the browser cache was cleared) the admin page stays blank white for
about five seconds before the Keycloak login appears: the app has to download its two Wasm modules
before it can do anything. Measured on alexpresse.net with Chromium: 4.7 s to Keycloak with an
empty cache, 0.4 s with a warm cache. The download is about 4.9 MB (Cloudflare's on-the-fly
Brotli of 14.5 MB of Wasm); staging, without Cloudflare, sends the full 14.5 MB uncompressed. A
blank page for several seconds looks broken, and the bytes on the wire can be cut substantially.

## What Changes

- While the app is loading, the admin page shows a small localized loading indicator ("Die
  Verwaltung wird geladen …" / "Loading the administration …") instead of a blank white page. It
  appears only when loading takes longer than a short delay, so warm loads do not flash, and it
  disappears when the app draws its first frame or a start-up notice takes its place.
- The image build pre-compresses the admin bundle's `.wasm` and `.js` files with Brotli at the
  highest quality. The backend sends the pre-compressed variant (`Content-Encoding: br`) to
  clients that accept Brotli and the original file to all others. Measured on the current
  bundle: 14.5 MB of Wasm becomes 3.9 MB (Cloudflare's own Brotli today: 4.9 MB).
- Caching behaviour stays as specified: `.wasm` immutable, everything else revalidated,
  conditional requests still answered with `304`.

## Non-goals

- No change to how the app itself loads or to its bundle size (no code splitting, no lazy
  modules, no Kotlin/Wasm compiler tuning).
- No redirect to Keycloak before the app has loaded.
- No real download progress (percentages); the indicator is indeterminate.
- No gzip or zstd variants; clients without Brotli get the uncompressed file as today.
- No Cloudflare or Traefik configuration; the Cloudflare "Browser Cache TTL" setting on
  alexpresse.net is an operator task outside this repository.
- No change to the Content-Security-Policy.

## Capabilities

### New Capabilities

### Modified Capabilities
- `admin-shell`: new requirements "A loading indicator replaces the blank page during start-up"
  and "Admin bundle is sent pre-compressed".

## Impact

- **admin**: `wasmJsMain/resources/startup.js` (shows and removes the loading indicator),
  `styles.css` (indicator styles, reduced-motion aware); admin tests.
- **backend**: `web/SecurityHeaders.java` or a sibling handler serves `<file>.br` for `.wasm` and
  `.js` under `/admin/` when the client accepts Brotli, with `Content-Encoding`, the original
  `Content-Type` and `Vary: Accept-Encoding`; `AdminDeliveryTest` with `.br` stubs.
- **deploy**: `Dockerfile` installs `brotli` in the backend build stage and compresses the admin
  bundle; image size grows by the `.br` files (about 4.5 MB).
- **reader, docs**: none. `docs/architecture.md` unchanged (CSP untouched).
- **Deployment repos**: nothing to change; they pick up the new image. After the rollout the
  transferred size is checked on staging and on alexpresse.net.
