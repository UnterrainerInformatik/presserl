## Why

The repository contains only the product plan (`docs/`); there is no code, no build and no
deployment yet. Milestone M0 lays the skeleton every later milestone builds on: a running
backend with reader stub, an administration app that can log in end to end, a reproducible
docker compose deployment with an installation guide, and CI that builds the image — so that
M1 can start writing features instead of wiring infrastructure.

## What Changes

- Scaffold `backend/` (Quarkus, Maven, JDK 21): reactive REST, Hibernate Reactive with Panache,
  reactive PostgreSQL client, Flyway via JDBC at startup, `quarkus-oidc` bearer-token
  validation for `/api`, Qute, SmallRye OpenAPI, health checks; Dev Services start PostgreSQL
  and Keycloak for `quarkus dev` and tests with zero configuration.
- First migration creates the `newspaper` singleton (overrides only); configuration layers
  code default → deployment (environment) → newspaper (database) are resolved for the keys
  already defined in `docs/architecture.md`.
- REST endpoints: `GET /api/newspaper` (effective name, subtitle, visibility, settings),
  `GET /api/client-config` (OIDC issuer and client id for the admin app) and a minimal
  `GET /api/me` (username, display name, newspaper-wide roles) to prove the login end to end.
- Publisher bootstrap: on startup the backend makes sure a publisher exists in Keycloak, creating
  `PRESSERL_PUBLISHER_USERNAME` / `PRESSERL_PUBLISHER_PASSWORD` in group `publisher` if none does
  (Keycloak Admin API through a service account — pulled forward from M2).
- Reader stub at `/`: server-rendered masthead with the newspaper name, `data-view="frontpage"`,
  strict same-origin CSP, no third-party resources.
- Scaffold `admin/` (Compose Multiplatform, Gradle, Wasm web target): stub app served by the
  backend at `/admin/`; OIDC authorization code + PKCE login against the configured Keycloak;
  shows newspaper name, logged-in user and roles; logout.
- `deploy/`: `compose.yaml` with **only** `presserl` and `postgres`, `.env.example` with the
  mandatory values, `keycloak/presserl-realm.json` realm template to import into an existing
  Keycloak, and `INSTALL.md` covering import, `.env`, start, and attaching an external reverse
  proxy (Traefik labels; Caddy as an optional guide). **No reverse proxy and no Keycloak are
  shipped** — the operator brings both.
- One multi-stage container image (admin Wasm bundle packaged into the backend image); CI builds
  it with the UnterrainerInformatik workflows.
- Populate `../presserl-deployment` from the templates (compose with Traefik labels for Gerald's
  Traefik, `.env` git-ignored, realm to import into `auth.unterrainer.info`).
- Update `docs/architecture.md` (and its diagram) to the external proxy/Keycloak model; record
  build/test commands in memory; endpoints primer and `.http` files for the new endpoints.

## Non-goals

- Articles, sections, accounts UI, approval chain, theme, media, print (M1–M6).
- Private newspapers: `visibility=private` is resolved and reported but not yet enforced on the
  reader or `/api/newspaper`; the reader code flow and its confidential client come later.
- Editing newspaper settings (layer 3 is read, never written, in M0).
- Internationalisation of defaults (`My Newspaper`, `General` stay English for now).
- Shipping a reverse proxy, TLS automation or a Keycloak container; backups; Android/iOS.
- Going live with `../presserl-deployment` (M7).

## Capabilities

### New Capabilities
- `newspaper-settings`: effective newspaper settings from the configuration layers, exposed via `GET /api/newspaper`.
- `api-authentication`: bearer-token protection of `/api`, public client configuration for the admin app, and `GET /api/me`.
- `publisher-bootstrap`: guaranteed first publisher account created from deployment variables.
- `reader-shell`: the server-rendered reader entry page and its security headers.
- `admin-shell`: the administration web app delivery at `/admin/` and its OIDC login/logout.
- `deployment`: docker compose reference deployment, mandatory configuration, realm template and installation guide.

### Modified Capabilities
_(none — no specs exist yet)_

## Impact

- **backend**: new Maven project `backend/` (Quarkus, JDK 21), Flyway migration `V1`, three REST endpoints, Keycloak Admin API client.
- **reader**: Qute front-page stub at `/`.
- **admin**: new Gradle project `admin/` (Compose Multiplatform, Wasm), OIDC library, API client.
- **deploy**: `deploy/compose.yaml`, `.env.example`, `keycloak/presserl-realm.json`, `INSTALL.md`; root `Dockerfile`; CI workflow; `../presserl-deployment` populated.
- **docs**: `docs/architecture.md`, `docs/diagrams/architecture.puml/.svg`; `ai/primer/endpoints.md`; `http/*.http`; `ai/memory/reference_build_and_test.md`; M0 entry removed from `ai/open-proposals.md`, M1/M2 entries adjusted.
- **External dependencies**: an operator-provided Keycloak (realm imported from the template) and an operator-provided TLS-terminating reverse proxy.
