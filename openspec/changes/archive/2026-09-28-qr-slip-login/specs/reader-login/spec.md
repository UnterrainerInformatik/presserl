## MODIFIED Requirements

### Requirement: Reader login via the authorization code flow
The system SHALL offer `GET /login` on the reader origin. For a visitor without a reader session
it SHALL redirect to the configured Keycloak realm using the OIDC authorization code flow with a
confidential reader client. When the request carries a `login_hint` query parameter, the
redirect to Keycloak SHALL carry it unchanged as `login_hint`, so the login form shows that
username pre-filled; without it no `login_hint` is sent. After a successful login the visitor
SHALL be redirected to the path given in the optional `next` query parameter, or to `/` when
`next` is missing or is not a same-origin path (it MUST start with a single `/`; values starting
with `//` or `/\`, and absolute URLs, fall back to `/`). A visitor who already has a reader
session SHALL be redirected to the target at once, without contacting Keycloak.

#### Scenario: Anonymous visitor starts the login
- **WHEN** an anonymous visitor requests `GET /login`
- **THEN** the response redirects to the realm's authorization endpoint with the reader client id and `response_type=code`, without `login_hint`

#### Scenario: Login with a username hint
- **WHEN** an anonymous visitor requests `GET /login?login_hint=lena`
- **THEN** the response redirects to the realm's authorization endpoint with `login_hint=lena`

#### Scenario: Back to the article after login
- **WHEN** the reader `oma` (group `reader`) requests `GET /login?next=/articles/7` and logs in successfully in Keycloak
- **THEN** the browser ends up on `/articles/7`

#### Scenario: Foreign target is ignored
- **WHEN** a visitor logs in via `GET /login?next=//evil.example/x`
- **THEN** the browser ends up on `/`

#### Scenario: Already logged in
- **WHEN** a visitor with a valid reader session requests `GET /login?next=/articles/7`
- **THEN** the response redirects to `/articles/7`

## ADDED Requirements

### Requirement: QR code entry
The system SHALL offer `GET /qr` on the reader origin without authentication; it is the address
the QR code on the account slip points to (`/qr?u=<username>#pw=<pass-phrase>`). When `u`
satisfies the username rules of account creation (lowercase letters and digits in groups joined
by single dashes), the response SHALL redirect (`303`) to `/login?login_hint=<u>`; otherwise, or
when `u` is missing, to `/login`. The `Location` SHALL end with an empty fragment (`#`) so that
the browser drops the fragment of the scanned address instead of carrying the pass-phrase into
the following pages. The response SHALL NOT be cached and SHALL NOT set or require a reader
session.

#### Scenario: Scanned slip
- **WHEN** a visitor requests `GET /qr?u=lena`
- **THEN** the response is `303` with `Location` `/login?login_hint=lena#` and `Cache-Control: no-store`

#### Scenario: Invalid username is not forwarded
- **WHEN** a visitor requests `GET /qr?u=Lena%20X`
- **THEN** the response is `303` with `Location` `/login#`

#### Scenario: Missing username
- **WHEN** a visitor requests `GET /qr`
- **THEN** the response is `303` with `Location` `/login#`

#### Scenario: Scan ends on the pre-filled login form
- **WHEN** an anonymous visitor opens `/qr?u=lena#pw=tiger-wolke-apfel-leiter` in a browser
- **THEN** the Keycloak login form shows `lena` in the username field and the address bar no longer contains the pass-phrase
