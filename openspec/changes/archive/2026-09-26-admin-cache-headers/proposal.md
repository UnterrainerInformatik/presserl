## Why

After every deploy the admin app breaks for anyone who opened it before: Quarkus serves all
static files with its default `Cache-Control: public, immutable, max-age=86400`, including the
files whose names do not change between builds (`index.html`, `composeApp.js`, `styles.css`,
`composeResources/*`). Browsers and shared caches in front of the server (observed: Cloudflare
on a fork, `cf-cache-status: HIT`) keep the old `composeApp.js` for up to a day. It references a
content-hashed `.wasm` file that the new image no longer contains, so the Wasm request answers
`404 text/html` and the app fails with "WebAssembly: Response has unsupported MIME type".

## What Changes

- Responses under `/admin/` get an explicit `Cache-Control`:
  - `*.wasm` (content-hashed file names) → `public, max-age=31536000, immutable`
  - every other response under `/admin/` → `no-cache` (caches must revalidate; unchanged files
    answer `304` via `Last-Modified`)
- `deploy/INSTALL.md`: updating section and troubleshooting row about purging a CDN cache once
  when upgrading from a version without this fix.

## Non-goals

- No change to caching of the reader, `/api/*` or `/q/*`.
- No content hashing of `composeApp.js` or other bundle files (webpack configuration).
- No service worker or offline support.
- No favicon (the `favicon.ico` 404 is harmless).

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `admin-shell`: new requirement on cache headers of the admin bundle, so a deploy takes effect
  on the next page load.

## Impact

- **backend**: `web/SecurityHeaders.java` (route filter already covering static resources) and a
  new/extended `@QuarkusTest` with a stub `.wasm` test resource.
- **deploy**: `deploy/INSTALL.md` (updating, troubleshooting).
- **admin, reader, REST contract**: unaffected.
- **Operations**: existing deployments behind a CDN need one cache purge for `/admin/*` after
  upgrading; afterwards no purge is needed.
