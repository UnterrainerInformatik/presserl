# admin-shell Specification

## Purpose

Delivers the administration web app from the backend and lets newsroom members log in and out
through the operator's Keycloak.

## Requirements

### Requirement: Admin app is served at /admin/
The system SHALL serve the administration web app at `/admin/` from the same origin as the API
and the reader. Requests to `/admin` SHALL redirect to `/admin/`.

#### Scenario: Opening the admin app
- **WHEN** a browser requests `GET /admin/`
- **THEN** the response is `200` HTML that loads the app's scripts and Wasm module from `/admin/`

#### Scenario: Missing trailing slash
- **WHEN** a browser requests `GET /admin`
- **THEN** it is redirected to `/admin/`

### Requirement: Admin pages allow only the needed origins
Admin pages SHALL be sent with a `Content-Security-Policy` that restricts all sources to `'self'`
except that `connect-src` additionally allows the origin of the configured OIDC issuer,
`script-src` additionally allows `'wasm-unsafe-eval'`, and `style-src` additionally allows the
hashes of the style elements the UI framework injects (listed with the admin bundle and verified
by the admin build). `'unsafe-inline'` SHALL NOT be allowed.

#### Scenario: CSP header on the admin app
- **WHEN** a browser requests `GET /admin/`
- **THEN** the `Content-Security-Policy` header has `default-src 'self'` and a `connect-src` listing `'self'` and the issuer origin only

#### Scenario: Framework styles are allowed by hash only
- **WHEN** the admin app starts in a browser
- **THEN** no CSP violation is reported, and `style-src` lists `'self'` plus the hashes from the admin bundle and no `'unsafe-inline'`

### Requirement: Login with authorization code and PKCE
The admin app SHALL obtain the OIDC configuration from `GET /api/client-config`, redirect an
unauthenticated user to the issuer with the authorization code flow and PKCE (`S256`), exchange
the code for tokens, and keep tokens only in memory. After login it SHALL show the newspaper
name, the user's display name and roles as returned by `GET /api/me`.

#### Scenario: Publisher logs in
- **WHEN** the bootstrapped publisher opens `/admin/`, is sent to Keycloak and enters valid credentials
- **THEN** they return to `/admin/` and see the newspaper name, their display name and the role `PUBLISHER`

#### Scenario: Login cancelled or failed
- **WHEN** Keycloak redirects back with an `error` parameter
- **THEN** the app shows a message that login failed and offers to try again

### Requirement: Logout
The admin app SHALL offer a logout that discards the tokens and ends the Keycloak session via the
issuer's end-session endpoint, returning to `/admin/`.

#### Scenario: Publisher logs out
- **WHEN** a logged-in user chooses logout
- **THEN** the Keycloak session ends and opening `/admin/` again requires entering credentials

### Requirement: Admin bundle caching survives deploys
Responses under `/admin/` SHALL carry an explicit `Cache-Control` header. Files with
content-hashed names (`*.wasm`) SHALL be sent with `public, max-age=31536000, immutable`. All
other responses under `/admin/` SHALL be sent with `no-cache`, so browsers and shared caches
revalidate them on every load and a new deploy takes effect on the next page load. Caching of
responses outside `/admin/` SHALL NOT be changed.

#### Scenario: Entry files are revalidated
- **WHEN** a browser requests `GET /admin/`, `GET /admin/composeApp.js` or `GET /admin/styles.css`
- **THEN** the response carries `Cache-Control: no-cache` and no `immutable`

#### Scenario: Hashed Wasm modules are cached long-term
- **WHEN** a browser requests a `.wasm` file under `/admin/`
- **THEN** the response carries `Cache-Control: public, max-age=31536000, immutable`

#### Scenario: Unchanged entry file is not downloaded again
- **WHEN** a browser revalidates `GET /admin/composeApp.js` with the `If-Modified-Since` value it received earlier
- **THEN** the response is `304` with `Cache-Control: no-cache`

#### Scenario: Deploy with a new Wasm build
- **WHEN** a new version is deployed whose `composeApp.js` references different `.wasm` file names and a user reloads `/admin/`
- **THEN** the browser fetches the new `composeApp.js` and loads the new `.wasm` files without a `404`

#### Scenario: Other paths keep their caching
- **WHEN** a client requests `GET /api/newspaper`
- **THEN** the `Cache-Control` header is the same as before this change
