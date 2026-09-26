## Context

- The backend runs one Quarkus OIDC configuration, the default tenant, as `application-type=service`:
  bearer tokens with audience `presserl-backend` on `/api/*`. `/api/newspaper` and
  `/api/client-config` are public.
- `NewspaperRoleAugmentor` turns the `groups` claim of any `JsonWebToken` principal into the
  roles `PUBLISHER`, `EDITOR_IN_CHIEF` and `READER`.
- `ReaderResource` renders `/` and `/articles/{id}` with Qute. For `Visibility.PRIVATE` it lists
  nothing and answers `404`. The visibility is an *effective* setting (code default → env → DB
  row), resolved per request through `NewspaperSettings.effective()`.
- Realms: `deploy/keycloak/presserl-realm.json` (template, hostname `presserl.example.org`, no
  secrets) and `backend/src/main/resources/dev/presserl-realm.json` (Dev Services, localhost,
  fixed secret, dev-only clients). `RealmTemplateDriftTest` pins the set of clients and their
  flags. The dev realm already contains the users `reader` (group `reader`), `chief` and
  `nogroups`.
- The realm's SSO session is 30 min idle and 10 h max, and `rememberMe` is off.
- Quarkus 3.33.

## Goals / Non-Goals

**Goals:**
- Add the code flow to the reader without touching how `/api` authenticates.
- Make the access decision per request against the effective visibility, so it never lives in
  static path permissions.
- No new session storage on the server; the session stays in the encrypted Quarkus OIDC cookie.

**Non-Goals:**
- A reader-specific role model beyond the three newspaper-wide roles (see proposal).
- Changing the realm's session lifetimes.

## Decisions

### 1. Second OIDC tenant `reader`, selected by path
Add a named tenant `quarkus.oidc.reader.*` with `application-type=web-app`, `client-id` =
`presserl.oidc.reader-client-id` (default `presserl-reader`), `credentials.secret` =
`presserl.oidc.reader-secret`, `auth-server-url` = `presserl.oidc.issuer`. The tenant is selected
by `quarkus.oidc.reader.tenant-paths=/,/login,/logout,/articles/*`. Future reader paths
(`/sections/*`, `/issues/*`, `/print/*`) are added to this list when they arrive.

*Found during implementation:* Quarkus 3.33 selects the tenant from a `q_session_<tenant>` (or
`q_auth_<tenant>`) cookie *before* it consults `tenant-paths`, on every path. Without a guard, a
reader's session cookie therefore authenticated `GET /api/me` (`200` instead of `401`).
`ReaderTenantScope` registers a Vert.x route at the highest priority that pins every request
outside `quarkus.oidc.reader.tenant-paths` to the default tenant (routing-context attribute
`OidcUtils.TENANT_ID_ATTRIBUTE` = `OidcUtils.DEFAULT_TENANT_ID`), so Quarkus skips the cookie-based
selection there. It reads the same `tenant-paths` value, so the list stays the single source.
`ReaderLoginTest` covers both directions: cookie only → `401`, cookie plus bearer token → `200`.

*Alternatives considered:*
- Switching the default tenant to `hybrid`. This would make `/api` accept session cookies, which
  opens a CSRF surface for the API. Rejected.
- Enabling the standard flow on `presserl-backend`. This mixes the user-management service account
  with browser logins in one client and one secret. Rejected, because separate clients can be
  revoked separately.
- A custom `TenantConfigResolver`. Not needed while `tenant-paths` covers it.

### 2. Dynamic access in the resource, static protection only on `/login`
Path permissions cannot depend on the effective visibility. So `/`, `/articles/*` and `/logout`
stay `permit`, and only `/login` gets `policy=authenticated`. With proactive authentication the
reader tenant establishes an identity from an existing session cookie on every reader path. When
no cookie is present, the identity is anonymous and there is no redirect. `ReaderResource` reads
the identity through `SecurityIdentity` and decides in three states: `ANONYMOUS`, `ENTITLED`,
`NOT_ENTITLED`. `ENTITLED` means any of the three roles. The state is only relevant when the
visibility is private.

`GET /login` is a plain JAX-RS method. It is reached only after the code flow has succeeded or the
session already exists. It answers `303` to the validated `next`. `GET /logout` is the tenant's
`logout.path`. Quarkus intercepts it for a logged-in visitor, performs RP-initiated logout with
`post-logout-path=/`, and needs no Keycloak session of its own. For an anonymous visitor the
request reaches a JAX-RS method that answers `303 /`.

### 3. `next` survives the round trip
Quarkus uses the original request URL as `redirect_uri` and restores the query string after the
callback. So `/login?next=…` arrives back at the `/login` method with `next` intact (Keycloak
allows it through the `https://<host>/*` redirect URI, see decision 6). The implementation verifies this
with the HtmlUnit test. If the query does not survive, the fallback is to put `next` into a
short-lived `HttpOnly` cookie set by `/login` before the challenge. This is an internal detail, and
the spec is unaffected.

`next` validation: it must match `^/(?![/\\]).*` and contain no control characters. Otherwise the
target is `/`.

### 4. Session cookie settings
- Default token state strategy `keep-all-tokens`. The content stays encrypted with the client
  secret, which is the Quarkus default. *Found during implementation:* the planned
  `id-refresh-tokens` failed to re-establish the session from the cookie in Quarkus 3.33 ("ID token
  verification has failed: Cannot invoke String.length() because str is null"), so the default
  stays. The cookie is about 4 KB; Quarkus splits larger session cookies into chunks by itself.
- `token.refresh-expired=true`, so the session lasts as long as the Keycloak SSO session.
- `authentication.cookie-same-site=lax`, `cookie-path=/`, and in production
  `authentication.cookie-force-secure=true`. The proxy terminates TLS, and forwarded headers are
  already honoured.
- `authentication.session-expired-path=/`. When refreshing fails, the cookie is dropped and the
  visitor lands on `/` as anonymous, instead of being bounced to Keycloak on a public page.
- `authentication.java-script-auto-redirect=false` is not needed, because the reader has no XHR.
- Groups: the reader client gets the `groups` mapper with `id.token.claim=true`. The principal of
  a code-flow identity is the ID token, so `NewspaperRoleAugmentor` works unchanged.

### 5. Rendering
- The layout and masthead get an optional `viewer` value (display name, or `null` for anonymous)
  and render "Angemeldet als … · Abmelden" / "Logged in as … · Log out" in de/en.
- The private front page for `ANONYMOUS` shows the private note plus a "Log in" link to `/login`.
  For `NOT_ENTITLED` it shows a no-access note instead. For `ENTITLED` it shows the normal list.
- `/articles/{id}` for private + `ANONYMOUS` answers `303 /login?next=/articles/{id}`. The `id`
  segment is URL-encoded, and malformed ids redirect as well, so every id gets the same response.
- `Cache-Control: private, no-store` is set in `ReaderResource.render(...)` when the visibility is
  private or the identity is not anonymous. The login and logout redirects also carry `no-store`.

### 6. Realm changes
New client `presserl-reader` in the template:
- `publicClient=false`, `clientAuthenticatorType=client-secret`
- `standardFlowEnabled=true`, `directAccessGrantsEnabled=false`, `implicitFlowEnabled=false`,
  `serviceAccountsEnabled=false`, `frontchannelLogout=true`
- `redirectUris=["https://presserl.example.org/*"]`, `webOrigins=[]`
- `attributes.post.logout.redirect.uris="https://presserl.example.org/*"`
- PKCE `S256`: Quarkus supports `authentication.pkce-required=true`. It is enabled and pinned in the
  realm as defence in depth.
- protocol mappers: `groups` only, with no backend audience because the reader never calls `/api`.

In the dev realm the same client uses the fixed secret `presserl-reader-dev-secret` and the
redirect URIs `http://localhost:8080/*` (dev) and `http://localhost:8081/*` (tests). The
redirect URIs are deliberately `/*`, because Quarkus builds `redirect_uri` from the requested
reader path. `RealmTemplateDriftTest` extends its expected client set to
`presserl-admin, presserl-backend, presserl-reader`. It also asserts that the reader is
confidential, uses no direct grants, and carries no `secret` in the template.

### 7. Configuration keys
| Key | Env | Default |
|---|---|---|
| `presserl.oidc.reader-client-id` | `PRESSERL_OIDC_READER_CLIENT_ID` | `presserl-reader` |
| `presserl.oidc.reader-secret` | `PRESSERL_OIDC_READER_SECRET` | none; mandatory in prod, `presserl-reader-dev-secret` in dev/test |

Only the secret goes into `.env.example`. The client id is an optional commented example, which
keeps "mandatory only" true.

### 8. Tests
- RestAssured with the `private` test profile covers the anonymous behaviour: front page with the
  login link, article redirect for existing, unknown and malformed ids, `Cache-Control`, and
  `/logout` → `/`.
- HtmlUnit (new test dependency `org.htmlunit:htmlunit`) drives the real code flow against Dev
  Services Keycloak with the users `reader`, `chief` and `nogroups`:
  - entitled and not-entitled views
  - `next` round trip and the rejection of a foreign `next`
  - cookie attributes
  - logout ends the session
  - `/api/me` with only the cookie → `401`
- `ReaderPrivateTest` is adapted from the old "`404` for everyone" behaviour.

## Risks / Trade-offs

- [Proactive auth on `/` makes every reader request decrypt the session cookie] → The cost is
  negligible, and requests without a cookie skip it entirely.
- [30 min SSO idle means readers log in again often] → This is accepted for M1. Operators can raise
  the realm's session settings. A dedicated "stay logged in" option is a later change.
- [Existing installations break on update without `PRESSERL_OIDC_READER_SECRET`] → Startup fails
  fast with a clear config error. No installation is live yet. INSTALL.md and the fork are updated
  in this change.
- [The redirect URI wildcard `https://<host>/*` is broader than `/login*`] → It is still bound to
  the installation's own host. It is needed because Quarkus may start the flow from any reader path
  of the tenant when a session expires.
- [Behaviour of `next` restoration or `session-expired-path` differs from expectation in Quarkus
  3.33] → The HtmlUnit tests cover both. The fallbacks are described in decisions 3 and 4.

## Migration Plan

1. Operator: import or update the realm so that it contains `presserl-reader` (Clients → Import
   client from the template section), then copy its secret into `PRESSERL_OIDC_READER_SECRET`.
2. Update the image and restart.
3. Rollback: use the previous image. The extra client and env var are ignored by the old version.
