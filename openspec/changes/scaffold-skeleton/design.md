## Context

Greenfield: the repo holds only `docs/`, `ai/`, `openspec/`. Motivation and scope: see
proposal.md. Requirements: see `specs/`. Constraints that shape the design:

- Stack is fixed by `docs/architecture.md` (Quarkus reactive + Qute, Compose Multiplatform Wasm,
  PostgreSQL, Keycloak). JDK 21 on the build machine (Lombok constraint, `reference_machine_jdk`).
- Deployment is docker compose only, with **`presserl` + `postgres`**; reverse proxy/TLS and
  Keycloak are provided by the operator (Gerald: Traefik, in-house Keycloak at
  `auth.unterrainer.info` with a dedicated realm; others may use Caddy).
- Consequence: Keycloak lives on a **different origin** than reader/admin, and the browser reaches
  it via its public URL while the backend may reach it the same way (no shared compose network).
- PKCE in the browser needs a secure context (HTTPS or `localhost`) — satisfied by the external
  proxy in production and by `localhost` in development.
- `../presserl-deployment` exists (git repo, only `LICENSE`); it is populated by this change.

## Goals / Non-Goals

**Goals:**
- One command each for dev (`quarkus dev` / Gradle dev server), tests (`./mvnw verify`,
  `./gradlew check`) and image build (`docker build .`).
- Keep all operator-facing knobs in `.env`; everything else defaulted.
- Establish patterns later milestones copy: settings resolution, resource + DTO + primer +
  `.http` per endpoint, Keycloak Admin API access, CSP handling.

**Non-Goals:**
- Tuning (native image, caching), multi-arch images, image signing.
- Any UI beyond the shell screens; a design system for the admin app (M4 covers the reader look).

## Decisions

### D1 — Repository and build layout
- `backend/`: Maven (wrapper), Quarkus current LTS pinned at scaffold time, `maven.compiler.release=21`.
  Extensions: `rest-jackson`, `hibernate-reactive-panache`, `reactive-pg-client`, `jdbc-postgresql`
  + `flyway` (JDBC datasource used only for migrations), `oidc`, `keycloak-admin-rest-client`
  (reactive REST client flavour), `rest-qute`, `smallrye-openapi`, `smallrye-health`.
  Tests: `junit5`, AssertJ, `rest-assured`, `test-keycloak-server`.
- `admin/`: Gradle (wrapper; generated via the `gradle` Docker image since Gradle is not installed),
  Kotlin + Compose Multiplatform, single `composeApp` module with `wasmJs` target only for now,
  package `info.unterrainer.presserl.admin`. Versions pinned at scaffold time (latest stable).
- Backend package root `info.unterrainer.presserl`, sub-packages by feature (`newspaper`, `auth`,
  `bootstrap`, `reader`, `web`).
- Root `Dockerfile`, multi-stage: (1) `gradle` JDK 21 image builds `wasmJsBrowserDistribution`;
  (2) Maven JDK 21 image builds the backend with the admin distribution copied to
  `META-INF/resources/admin/`; (3) `eclipse-temurin:21-jre` runtime running the Quarkus fast-jar
  as non-root. *Alternative:* two images (admin via nginx) — rejected, architecture wants one
  image and same origin.

### D2 — Settings resolution
A `NewspaperSettings` service holds a typed code-default table and SmallRye `@ConfigMapping`
(`presserl.newspaper.*`, `presserl.retract.*`, …) for layers 1+2 — env var mapping gives layer 2
for free (`PRESSERL_NEWSPAPER_NAME` → `presserl.newspaper.name`). Enumerations are Java enums in
the mapping, so an invalid env value fails config validation at startup (spec). Layer 3 is a
Hibernate Reactive Panache entity on table `newspaper` (singleton, `id = 1`, nullable `name`,
`subtitle`, `settings jsonb` default `'{}'`), created with its row by Flyway `V1__newspaper.sql`.
Resolution: override from row if present, else config value. Layer 4 (section) is not touched.
*Alternative:* store all effective values in the DB at first start — rejected, docs require
overrides only.

### D3 — REST contract (new endpoints)

`GET /api/newspaper` — public
```json
200 OK
{
  "name": "My Newspaper",
  "subtitle": "",
  "visibility": "public",
  "settings": {
    "retract.author-can-retract": true,
    "section.default": "General",
    "editor.level": "standard",
    "reader.text-size": "m",
    "media.max-size": "10M"
  }
}
```

`GET /api/client-config` — public
```json
200 OK
{
  "oidc": {
    "issuer": "https://auth.unterrainer.info/realms/presserl",
    "clientId": "presserl-admin",
    "scopes": ["openid", "profile"]
  }
}
```

`GET /api/me` — bearer token required
```json
200 OK
{ "username": "papa", "displayName": "Papa", "roles": ["PUBLISHER"] }
```
`401` with empty body when the token is missing/invalid. M2 extends this response (scopes,
allowed actions) additively. Settings keys in `settings` are the config keys without the
`presserl.` prefix, so clients and docs use one vocabulary.

### D4 — OIDC topology with an external Keycloak
- Configuration: `PRESSERL_OIDC_ISSUER` (e.g. `https://auth.unterrainer.info/realms/presserl`) maps
  to `quarkus.oidc.auth-server-url`; `quarkus.oidc.client-id=presserl-backend`,
  `quarkus.oidc.application-type=service`, `quarkus.oidc.token.audience=presserl-backend`.
  Audience verification matters because the realm may live on a shared Keycloak.
- Admin client `presserl-admin` gets an audience mapper (`presserl-backend`) and a `groups` claim
  mapper (group names without path). `GET /api/me` maps that claim to roles.
- Backend client `presserl-backend`: confidential, service account enabled, standard flow off;
  service-account roles `realm-management: manage-users, view-users, query-users, query-groups`
  (Keycloak needs the query/view roles for lookups; docs' "manage-users only" is corrected).
  Admin API calls use `PRESSERL_OIDC_BACKEND_SECRET`; the Admin API base URL and realm are derived
  from the issuer (`…/realms/{realm}` → server URL + realm name).
- *Alternative:* serve Keycloak under `/auth` of the same origin — rejected by the operator decision
  (Keycloak is external and shared).

### D5 — Publisher bootstrap
A startup bean (`@Observes StartupEvent`) runs the bootstrap on a worker, non-blocking for the
HTTP server: find group `publisher` → if it has members, done → else find user by exact username
→ create if absent (enabled, password credential `temporary=false`) → join group. Failures are
retried with exponential backoff (1 s … 60 s cap) forever. A readiness check `publisher-bootstrap`
reports DOWN until success; compose's health check uses `/q/health/ready`. Required variables are
enforced in `%prod` through non-optional config properties; `%dev`/`%test` default to
`publisher`/`publisher`. *Alternative:* create the user via placeholders in the realm import —
rejected: import happens outside our control on an existing Keycloak, and the service account is
needed in M2 anyway.

### D6 — Realm template vs. dev realm
- `deploy/keycloak/presserl-realm.json`: the operator template. Hostname placeholder
  `https://presserl.example.org` in the admin client's redirect URIs, web origins and
  post-logout redirect; no client secret (Keycloak generates it; the operator copies it into
  `.env`). `INSTALL.md` gives a `sed` one-liner to set the hostname before import. Realm name
  `presserl` (operator may rename; the issuer URL carries the name).
- `backend/src/main/resources/dev/presserl-realm.json`: used by Keycloak Dev Services
  (`quarkus.keycloak.devservices.realm-path`), with `http://localhost:8080` and `:8081` redirects,
  a fixed dev secret, and an extra dev-only client `presserl-http` (direct access grants) for the
  `.http` files and tests.
- A unit test compares both files: same groups, same client ids (except `presserl-http`), same
  mappers and service-account roles — guards against drift.

### D7 — Admin app login
- Library: `kalinjul/kotlin-multiplatform-oidc` if its `wasmJs` web flow works (verified in a
  spike task first). **Fallback:** a small in-house PKCE flow (Web Crypto for `S256`, redirect,
  token exchange via `fetch`, end-session redirect), which is ~200 lines and has no platform
  dependencies beyond the browser. The OIDC boundary sits behind an `AuthClient` interface so
  Android/iOS can later use the library's native flows.
- Tokens live in memory only; the PKCE verifier and `state` live in `sessionStorage` for the
  redirect round-trip. On reload the app re-runs the code flow; the Keycloak SSO cookie makes this
  a silent redirect. Refresh via refresh token while the page lives.
- **Spike result (2026-09-26): in-house flow.** `kotlin-multiplatform-oidc` 0.18.3 publishes
  `wasm-js` artifacts, but its web flow (`WebCodeAuthFlowFactory` → internal `WebPopupFlow`) is
  popup-only: login *and* logout open a window via `window.open` and wait for a `postMessage`
  from a redirect page, all marked `@ExperimentalOpenIdConnect`. `PlatformCodeAuthFlow` is bound
  to that popup class, so a full-page redirect flow cannot be plugged in (a suspended coroutine
  does not survive the page leaving). Popups also get blocked when the login starts without a
  user gesture. The in-house flow lives in `BrowserAuthClient` (wasmJs) behind `AuthClient`;
  Web Crypto provides `S256` and the random verifier/state. Unit tests run in headless Chrome
  (Karma) — the Node test runner cannot load the Compose runtime.
- API client: Ktor client (`js` engine) + kotlinx.serialization DTOs mirroring D3.
- Development: admin dev server on `localhost:8081` calling the backend on `localhost:8080`;
  CORS allowed for `http://localhost:8081` in `%dev` only. Production is same origin, no CORS.

### D8 — Serving `/admin/` and CSP
Quarkus serves the bundle from `META-INF/resources/admin/`; a small route redirects `/admin` →
`/admin/`. A response filter sets CSP by path: reader
`default-src 'self'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'self'; form-action 'self'`;
admin the same plus `script-src 'self' 'wasm-unsafe-eval'` and `connect-src 'self' <issuer-origin>`.
Any inline style/script in the generated Compose `index.html` is moved into files so no
`'unsafe-inline'` is needed.
**Amendment (2026-09-26, found in the end-to-end check):** Compose's `ComposeViewport` appends a
`<style>` element to its shadow DOM at runtime (canvas layout, hidden IME backing field), which
`default-src 'self'` blocks. Instead of `'unsafe-inline'`, the admin bundle ships
`csp-style-hashes.txt` with the `'sha256-…'` source of that style; the backend reads it from the
classpath at startup and adds `style-src 'self' <hashes>` to the admin policy (no file → no
`style-src`). The admin browser test `CspStyleHashTest` renders a `ComposeViewport`, hashes every
injected style and fails with the hash to add when a Compose upgrade changes it. Karma runs Chrome
with SwiftShader so Skia gets a WebGL context headless.

### D9 — Compose and reverse proxy
`deploy/compose.yaml`: `presserl` (image `${PRESSERL_IMAGE:-<registry>/presserl:<tag>}`, publishes
`${PRESSERL_HTTP_PORT:-8080}`, `depends_on: postgres: service_healthy`, health check on
`/q/health/ready`) and `postgres` (official image, `pg_isready` health check, named volume
`presserl-db`). Quarkus is told it is behind a proxy (`quarkus.http.proxy.proxy-address-forwarding`,
`enable-forwarded-host`) so redirects and URLs use the public HTTPS base URL. `INSTALL.md` shows a
Traefik label block (router host rule, TLS cert resolver, service port, external network) as a
compose override file, and a Caddyfile snippet (`reverse_proxy presserl:8080`) as the optional
alternative. `../presserl-deployment/compose.yaml` = template + Gerald's Traefik labels.

### D10 — CI
GitHub Actions in `.github/workflows/` calling UnterrainerInformatik's reusable
`docker-build-workflow` (and `bump-semver-workflow` for tags) with the root `Dockerfile`; a
separate job runs `./mvnw verify` and `./gradlew check`. Exact workflow inputs (registry, image
name, secrets) are read from the workflow repository at implementation time.

## Risks / Trade-offs

- [KMP OIDC library lacks a working `wasmJs` flow] → spike first; fallback in-house PKCE (D7).
- [Keycloak's required service-account roles differ from expectation] → integration test against
  Dev Services Keycloak using the dev realm, whose roles are drift-checked against the template.
- [Tests need Docker (Dev Services)] → acceptable; Docker is required for deployment anyway.
- [Compose Wasm bundle size / first-load time] → acceptable for an admin app; revisit in M8.
- [Issuer URL differs between browser and backend] → not an issue here: both use the public URL;
  documented that the backend container must be able to resolve and reach it (hairpin NAT).
- [Hostname placeholder in the realm template is error-prone] → `sed` one-liner plus a check in
  the guide ("login redirect fails with invalid redirect_uri → hostname not replaced").

## Migration Plan

Greenfield — nothing to migrate. Flyway `V1` is the first migration and becomes immutable once
applied. Rollback: `docker compose down`, previous image tag.

## Open Questions

- Traefik specifics for `../presserl-deployment` (entrypoint name, cert resolver, external network
  name, public hostname) — asked from Gerald when that task is reached; they only fill labels.
- Container registry and image name — dictated by the UnterrainerInformatik workflow inputs.
