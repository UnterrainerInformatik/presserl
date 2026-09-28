## Why

Children log in by typing the web address, their username and a four-word pass-phrase from the
account slip — slow and error-prone on a phone. The slip already reserves room for a QR code
(design guidelines §6), and M8 (Mobile & QR) starts here: the QR code makes scanning replace
typing, and its content is fixed now so that every slip printed from today on also works with
the mobile app later.

The vision planned a one-time token that the backend exchanges for a Keycloak login. Keycloak
offers no such exchange without either storing the pass-phrase reversibly and enabling the
password grant, or enabling preview features on the operator's (often shared) Keycloak. Since
the pass-phrase is printed in plain text on the same slip, a QR code carrying the credentials
exposes nothing the slip does not already show. This change therefore puts the address and
the credentials into the QR code and drops the token exchange from the vision.

## What Changes

- **QR code on the account slip:** after creating an account or resetting a password, the slip
  (on screen and printed) shows a QR code next to the text. It encodes
  `<reader address>/qr?u=<username>#pw=<pass-phrase>`. The pass-phrase sits in the URL fragment,
  so browsers never send it to any server.
- **Reader entry `GET /qr`:** a public route that sends the visitor on to the reader login with
  the username as login hint, and clears the fragment on the way so the pass-phrase does not
  travel on into the Keycloak address bar or history. An invalid or missing username leads to
  the plain login.
- **Login hint:** `GET /login` forwards an optional `login_hint` to Keycloak, which pre-fills the
  username field. Scanning with the phone camera thus opens the newspaper's login with the
  username filled in; the child types only the pass-phrase. The mobile app (later) will read
  address, username and pass-phrase from the same code and need no typing at all.
- **Docs:** vision (M8 row), roles-and-workflow (hand-over), design guidelines §6 (slip with QR
  code), architecture (reader routes); `http/reader.http`; M8 entry in `ai/open-proposals.md`
  reduced to the mobile targets, reading the QR code in the app and store publishing.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `reader-login`: new public entry `GET /qr` and the optional `login_hint` forwarded by
  `GET /login`.
- `admin-accounts`: the printable account slip carries the QR code with address and credentials.

## Non-goals

- A one-time token, a token exchange endpoint or any stored pass-phrase; no new Keycloak client
  and no change to the realm template or to the operator's Keycloak.
- Filling in the pass-phrase in the browser: the Keycloak login page lives on another origin and
  cannot be pre-filled with a password; only the username is pre-filled.
- Switching accounts via `/qr` when the phone already holds a reader session of another person:
  the existing session wins (as with `/login`); the person logs out first.
- Android/iOS targets, scanning in the app and store publishing (remaining M8 work).
- Logging into the administration app via the QR code; the code targets the reader.

## Impact

- **backend / reader:** `ReaderResource` gains `GET /qr` (outside the reader OIDC tenant);
  reader OIDC tenant forwards `login_hint` to the authorization endpoint; username validation
  for the hint; tests (`@QuarkusTest`).
- **admin:** QR encoding (library with wasmJs support or a small in-house encoder), QR payload
  builder, slip screen shows the code, `PrintableSlip` carries the payload and
  `BrowserSlipPrinter` renders it as SVG built from DOM elements (CSP unchanged), print styles,
  strings (de/en), Kotlin tests.
- **deploy:** none (Keycloak supports `login_hint` out of the box; realm unchanged).
- **docs:** `docs/vision.md`, `docs/roles-and-workflow.md`, `docs/design-guidelines.md`,
  `docs/architecture.md`, `http/reader.http`, `ai/open-proposals.md`. `ai/primer/endpoints.md`
  is unaffected: no `/api` endpoint changes.
