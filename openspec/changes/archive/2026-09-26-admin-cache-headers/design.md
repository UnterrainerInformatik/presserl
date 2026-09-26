## Context

`web/SecurityHeaders` registers a Vert.x route with `order(Integer.MIN_VALUE)` that runs before
Quarkus' static handler and sets the CSP via `addHeadersEndHandler`. The static handler (Quarkus
defaults: caching enabled, max-age 24h) writes `Cache-Control: public, immutable, max-age=86400`
and `Last-Modified` for every file under `META-INF/resources/`. The Kotlin/Wasm webpack build
emits content-hashed `.wasm` names, but `index.html`, `composeApp.js`, `styles.css` and
`composeResources/**` keep stable names.

## Goals / Non-Goals

**Goals:**
- A deploy reaches every browser and shared cache on the next load of `/admin/`.
- Keep the large `.wasm` files (≈11 MB) cached long-term.

**Non-Goals:**
- Changing caching of reader, API or `/q/` paths.
- Fingerprinting the remaining bundle files.

## Decisions

1. **Set the header in the existing filter's headers-end handler.** Headers-end handlers run
   after the static handler has written its headers, so `set` overrides the default. This keeps
   all header policy for `/admin/` in one place and also covers `304` and `404` responses.
   *Alternative:* global `quarkus.http.static-resources.max-age` / `caching-enabled=false` —
   rejected: it would change the reader's static files and cannot distinguish `.wasm`.
   *Alternative:* `quarkus.http.filter.*.header` config with path matching — rejected: splits the
   `/admin/` header policy across config and code, and its path matching is prefix-based.
2. **`no-cache` rather than `no-store`** for non-hashed files: the static handler already sends
   `Last-Modified` and answers conditional requests with `304`, so revalidation costs one small
   request per file.
3. **`.wasm` by suffix** is the fingerprint criterion. The build only emits hashed `.wasm` names
   (`[contenthash].wasm`); should an unhashed `.wasm` ever appear, the failure mode is the
   current one, not a new one. One year `max-age` because hashed names never change content.
4. **Pure function for the decision** (`adminCacheControl(path)`), unit-testable without the
   Vert.x router, plus `@QuarkusTest` against a stub `.wasm` in the test resources.

## Risks / Trade-offs

- [Existing CDN copies stay stale until they expire or are purged] → one-time purge documented in
  `deploy/INSTALL.md`; browsers pick up the change after at most one day or a hard reload.
- [One extra conditional request per entry file on each load] → negligible (`304`, a few files).
- [A future build emitting unhashed `.wasm` names] → would get `immutable`; noted in the code
  comment next to the rule.
