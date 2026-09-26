## 1. Backend — scaffold

- [x] 1.1 Generate `backend/` (Maven wrapper, current Quarkus LTS, `release=21`, group `info.unterrainer.presserl`) with the extensions from design D1; remove generated sample code
- [x] 1.2 Base `application.properties`: datasources (reactive + JDBC for Flyway, `migrate-at-start`), proxy forwarding, OpenAPI, health, `%dev`/`%test` publisher defaults, dev CORS for `http://localhost:8081`
- [x] 1.3 Keycloak Dev Services wired to `src/main/resources/dev/presserl-realm.json` (dev realm per D6, incl. dev-only `presserl-http` client and a client without the backend audience for negative tests)
- [x] 1.4 Verify `./mvnw quarkus:dev` starts with PostgreSQL and Keycloak via Dev Services and no manual configuration

## 2. Backend — newspaper settings

- [x] 2.1 Flyway `V1__newspaper.sql`: singleton table `newspaper` (`id=1`, nullable `name`, `subtitle`, `settings jsonb default '{}'`) with its row
- [x] 2.2 `@ConfigMapping` for the settings with code defaults and enums for `visibility`, `editor.level`, `reader.text-size`
- [x] 2.3 Panache entity + settings service resolving code default → env → DB override
- [x] 2.4 `GET /api/newspaper` resource and DTO per design D3
- [x] 2.5 Tests: defaults, env override, DB override wins, invalid enum fails startup with variable name and allowed values (QuarkusTest profiles / config unit test), endpoint shape anonymous and authenticated

## 3. Backend — authentication

- [x] 3.1 OIDC config per design D4 (`PRESSERL_OIDC_ISSUER`, audience `presserl-backend`, service application type); `/api/newspaper` and `/api/client-config` permitted, rest of `/api` authenticated
- [x] 3.2 `GET /api/client-config` from config (`PRESSERL_OIDC_ISSUER`, `PRESSERL_OIDC_ADMIN_CLIENT_ID`, scopes)
- [x] 3.3 `GET /api/me`: username, display name fallback, groups claim → roles mapping, unknown groups ignored
- [x] 3.4 Tests: 401 without token, foreign issuer, missing audience; `/api/me` for publisher and for a user without groups; `/api/client-config` shape

## 4. Backend — publisher bootstrap

- [x] 4.1 Keycloak Admin client built from issuer + `PRESSERL_OIDC_BACKEND_SECRET` (client credentials)
- [x] 4.2 Startup bootstrap per design D5 (group check, find/create user, join group) with exponential backoff and `publisher-bootstrap` readiness check
- [x] 4.3 `%prod` fails fast when `PRESSERL_PUBLISHER_USERNAME`/`PRESSERL_PUBLISHER_PASSWORD` are missing or blank, naming the variable
- [x] 4.4 Tests against Dev Services Keycloak: fresh realm creates user in `publisher` and it can log in; existing user joins group and keeps password; second run changes nothing; missing `publisher` group keeps readiness down; missing variable fails prod-profile config validation

## 5. Reader

- [x] 5.1 Qute front-page template: masthead with name, optional subtitle, `<main data-view="frontpage">`, minimal same-origin stylesheet, no JavaScript
- [x] 5.2 CSP response filter per design D8 (reader and admin variants by path)
- [x] 5.3 Tests: `GET /` HTML contains default name and `data-view`; name from env; CSP header present with `default-src 'self'`; no foreign absolute URLs in the HTML

## 6. Admin

- [x] 6.1 Generate Gradle wrapper (via `gradle` Docker image) and scaffold `admin/` (Compose Multiplatform, `wasmJs`, package `info.unterrainer.presserl.admin`), pinned versions
- [x] 6.2 Spike: `kotlin-multiplatform-oidc` code + PKCE flow on `wasmJs`; decide library vs. in-house fallback (design D7) and record the result in design.md
- [x] 6.3 `AuthClient` implementation: client-config fetch, redirect with PKCE `S256` + `state`, code exchange, in-memory tokens, refresh, error-parameter handling, logout via end-session
- [x] 6.4 API client (Ktor + kotlinx.serialization) and DTOs for `/api/newspaper`, `/api/client-config`, `/api/me`
- [x] 6.5 Shell UI: login-failed state with retry; logged-in view with newspaper name, display name, roles, logout button
- [x] 6.6 Move any inline script/style from the generated `index.html` into files (CSP without `'unsafe-inline'`)
- [x] 6.7 Kotlin tests: PKCE challenge against the RFC 7636 test vector, `state` check, DTO deserialisation of the D3 examples, role display mapping
- [x] 6.8 Backend serves the bundle: `/admin` → `/admin/` redirect; test for redirect, `200` on `/admin/` and admin CSP (`connect-src` = `'self'` + issuer origin)

## 7. Deploy

- [x] 7.1 Root `Dockerfile` (multi-stage per design D1), `.dockerignore`; verify `docker build .` and that the image serves `/`, `/admin/`, `/api/newspaper`
- [x] 7.2 `deploy/compose.yaml` per design D9 (presserl + postgres only, health checks, named volume)
- [x] 7.3 `deploy/.env.example` with the mandatory values only (commented, empty secrets, optional settings commented out)
- [x] 7.4 `deploy/keycloak/presserl-realm.json` template per design D4/D6; unit test comparing it with the dev realm (groups, clients, mappers, service-account roles, security flags)
- [x] 7.5 `deploy/INSTALL.md`: prerequisites, realm import incl. hostname `sed` and secret copy, `.env`, `docker compose up -d`, Traefik label override, optional Caddy, first login, updates, troubleshooting (invalid redirect_uri, readiness down)
- [x] 7.6 End-to-end check: image + compose against the local dev Keycloak (realm template imported), first publisher logs in to `/admin/` and sees `PUBLISHER`
- [x] 7.7 CI in `.github/workflows/` using UnterrainerInformatik `docker-build-workflow` (inputs read from that repo) plus a test job (`./mvnw verify`, `./gradlew check`)
- [x] 7.8 Admin CSP allows Compose's injected shadow-DOM style by hash (design D8 amendment): `csp-style-hashes.txt` in the admin bundle, `CspStyleHashTest` (Karma, SwiftShader), backend adds `style-src`; `AdminDeliveryTest` covers it

## 8. Deployment repo (`../presserl-deployment`)

- [x] 8.1 Ask Gerald for Traefik entrypoint, cert resolver, external network name and public hostname
- [x] 8.2 Populate from the templates: `compose.yaml` with Traefik labels, `.env.example` with the auth.unterrainer.info issuer, `.gitignore` (`.env`), short `README.md` pointing to upstream `INSTALL.md`; realm file with the real hostname for Gerald to import into `auth.unterrainer.info`
- [x] 8.3 `deploy/INSTALL.md` step 2a: step-by-step setup of an existing (empty) realm from the template (needed for staging and the alexpresse fork)
- [x] 8.4 Staging deploy (added 2026-09-26 with Gerald): `presserl-deployment/deploy/` (`compose.yaml` with Traefik labels on `proxy_default`, `site.env`, `up.sh` loading `secrets.env` from the server), `.github/workflows/deploy.yml` calling `deploy-workflow`, upstream `dispatch-staging` job (`repository_dispatch` with the version); verify a first deploy to babylon5 and the login at `https://presserl.unterrainer.info/admin/`. Deploy of `0.0.1` verified 2026-09-26 (healthy, publisher bootstrapped, HTTPS endpoints, admin CSP, Keycloak accepts the redirect); needed `deploy-workflow` fix `edf9058` (install `iproute2` for `wg-quick`) and the per-site compose project name. Gerald logged in as publisher and out again; the first login asked for first name, last name and e-mail because the realm's user profile still required them (partial import does not carry the template profile) — INSTALL.md 2a.4 now covers all three attributes

## 9. Contract / Docs

- [x] 9.1 `ai/primer/endpoints.md`: `GET /api/newspaper`, `GET /api/client-config`, `GET /api/me` (auth, shapes, errors)
- [x] 9.2 `http/newspaper.http`, `http/client-config.http`, `http/me.http` (token via dev `presserl-http` client); run them against the live dev backend
- [x] 9.3 `docs/architecture.md`: Operations/Identity rows and repo layout for external proxy + Keycloak, realm template, service-account roles (D4), CSP `connect-src` note; update `docs/diagrams/architecture.puml` and re-render the SVG
- [x] 9.4 `ai/memory/reference_build_and_test.md` with dev/test/build commands; update `reference_machine_jdk.md` with the verified target JDK; add both to `ai/memory/MEMORY.md`
- [x] 9.5 `ai/open-proposals.md` (M0 entry already deleted at propose time): M1 gains private-visibility enforcement (reader code flow + confidential client); M2 says "extend" for the Keycloak Admin client and `GET /api/me`

## 10. Verification

- [x] 10.1 `./mvnw verify` and `./gradlew check` green
- [x] 10.2 `openspec validate scaffold-skeleton --strict` passes; walk through every spec scenario and tick it off against a test or the manual end-to-end check
  - admin-shell: opening/trailing slash/CSP → `AdminDeliveryTest`; framework styles by hash → `CspStyleHashTest`, `AdminDeliveryTest`, no violations in local e2e and on staging; login, failed login, logout → local Playwright e2e (7.6) and Gerald on staging (8.4)
  - api-authentication: all six → `AuthenticationTest`, `NewspaperRoleTest`
  - deployment: start, restart keeps data, template → local compose e2e (7.1/7.6), `.env.example` review; existing Keycloak → staging realm via INSTALL 2a (8.4); fresh host → local e2e plus staging, which found the user-profile gap fixed in INSTALL 2a.4
  - newspaper-settings: all six → `NewspaperConfigTest`, `NewspaperResourceTest`, `DeploymentNameTest`
  - publisher-bootstrap: password not set → `PublisherCredentialsTest`; fresh realm, existing user, restart → `PublisherBootstrapTest`; Keycloak later, template missing → `PublisherBootstrapRunnerTest`, `PublisherBootstrapTest`
  - reader-shell: all three → `ReaderResourceTest`, `DeploymentNameTest`
