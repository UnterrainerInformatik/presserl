## Context

- The reader login (`GET /login`) belongs to the OIDC tenant `reader` (web-app, code flow + PKCE,
  confidential client `presserl-reader`). Quarkus starts the code flow for an anonymous visitor
  before `ReaderResource.login` runs; `restore-path-after-redirect=true` brings the visitor back
  to `/login?…`, which then redirects to `next`.
- The admin app already knows the reader address (`siteUrl`) and shows it on the slip
  (`AccountSlipScreen`). Printing builds real DOM elements in `BrowserSlipPrinter` (`textContent`
  only, no inline styles/scripts) because the Compose canvas prints as a blurry bitmap; the print
  rules live in `styles.css`.
- Usernames match `^[a-z0-9]+(-[a-z0-9]+)*$` (`AccountRequestValidator.USERNAME`); pass-phrases
  are four words from a list without umlauts, joined by dashes. Neither needs URL encoding.
- Keycloak honours the standard `login_hint` parameter on the authorization endpoint and pre-fills
  the username field; no realm change is needed.

See proposal.md for why credentials, not a one-time token, go into the code.

## Goals / Non-Goals

**Goals:**
- A payload format that stays valid for the mobile app later: the app recognises
  `<base>/qr?u=…#pw=…`, takes `<base>` as server address, `u` as username and `pw` as
  pass-phrase.
- The pass-phrase never reaches a server log, the proxy or Keycloak, and does not linger in the
  address bar after the first hop.
- No new dependency in the backend, no CSP change, no realm change.

**Non-Goals:**
- Pre-filling the password in the browser (cross-origin Keycloak form).
- Versioning the payload beyond the fixed path `/qr`; a later format would use a new path.

## Decisions

### D1 — Credentials in the QR code, pass-phrase in the fragment
Payload: `<siteUrl without trailing slash>/qr?u=<username>#pw=<pass-phrase>`.
The fragment is never sent in an HTTP request, so neither the reverse proxy, the backend access
log nor Keycloak sees the pass-phrase. A plain URL (rather than a custom scheme or JSON) means the
phone camera opens something useful today.
*Alternatives:* one-time token + password grant or Keycloak token exchange (rejected in the
proposal); pass-phrase as query parameter (would land in proxy logs); custom scheme
`presserl://` (camera apps would do nothing without the mobile app).

### D2 — `GET /qr` as a separate hop that clears the fragment
Browsers carry a URL's fragment across a redirect whose `Location` has none (Fetch spec, "set
the location URL's fragment"). A direct `/login?login_hint=…#pw=…` would therefore put the
pass-phrase into the Keycloak login page's address. `/qr` answers `303` with
`Location: /login?login_hint=<u>#`: an *empty* fragment is a non-null fragment and replaces the
inherited one. `/qr` lies outside the reader tenant's `tenant-paths` (like `/text-size` and
`/theme/*`), so it is a plain JAX-RS method on `ReaderResource` that neither reads nor creates a
session; `Cache-Control: no-store`. `u` is checked against the username pattern (reused from the
account validator, not duplicated) and dropped when it does not match, so `/qr` cannot inject
arbitrary parameters.
*Alternative:* a small page with a same-origin script that strips `location.hash` — more moving
parts for the same effect.

### D3 — `login_hint` forwarded by the reader tenant
`quarkus.oidc.reader.authentication.forward-params=login_hint` makes Quarkus copy the request's
`login_hint` into the authorization redirect. Nothing else in `ReaderResource.login` changes;
after the round trip `/login?login_hint=…` redirects to `/` as before. The value reaches only
Keycloak, which treats it as a pre-fill.
*Alternative:* building the authorization URL by hand — would bypass PKCE/state handling of
`quarkus-oidc`.

### D4 — QR encoding in the admin app, rendered twice from one module matrix
A common-code `QrCode` produces the module matrix (error correction level M; the payload is
about 70 characters, version 4–5). The screen draws it with a Compose `Canvas`; the printer
builds an `<svg>` with `createElementNS` and one `<path>` (or rects) per dark run, fill via an
SVG attribute (`fill="#000"`, not a `style` attribute), with a quiet zone of four modules and a
printed size of about 35 mm. `PrintableSlip` gains `qrPayload: String` and `qrHint: String`; its
`toString` keeps hiding the payload (it contains the pass-phrase).
Library choice: a Kotlin Multiplatform QR encoder with a `wasmJs` artifact that exposes the
module matrix (candidates: `io.github.alexzhirkevich:qrose`, `io.github.g0dkar:qrcode-kotlin`);
if none fits the Kotlin/Compose versions in use, port Nayuki's MIT-licensed QR Code generator
into `commonMain` (single file, no dependencies). The first task settles this.
*Alternative:* rendering the code in the backend (image endpoint) — would send the pass-phrase to
the server in a request; rejected.

### D5 — Payload builder is pure and tested
`SlipQr.payload(siteUrl, username, password)` trims a trailing `/` from `siteUrl` and assembles
the string; a Kotlin test pins the exact output of the spec scenario. A second test encodes the
payload and checks the matrix size/finder patterns; decoding is verified end to end by scanning a
screenshot of the printed slip with a QR decoder during the manual check.

## Risks / Trade-offs

- [A photo of the slip's QR code reveals the pass-phrase, like a photo of the printed text] →
  Accepted; the slip note already says to keep it safe. Password reset invalidates both.
- [A browser does not replace the fragment on `Location: …#`] → Covered by the spec scenario
  "Scan ends on the pre-filled login form", checked with Chromium and Firefox via Playwright; worst
  case the pass-phrase shows in the Keycloak address bar on the child's own phone.
- [Phone already logged in as someone else] → `/login` redirects without contacting Keycloak;
  documented non-goal, the person logs out first.
- [QR library pulls a heavy Compose dependency into the wasm bundle] → prefer a matrix-only
  artifact; fall back to the in-house port.
- [Old slips printed before this change have no QR code] → none needed; they keep working as
  before.

## Migration Plan

No data or configuration migration. Deploy as usual; staging (`presserl-deployment`) picks up the
new image, `alexpresse` after merging upstream. Rollback: redeploy the previous image; printed QR
codes then lead to `404` on `/qr`, the printed text still works.
