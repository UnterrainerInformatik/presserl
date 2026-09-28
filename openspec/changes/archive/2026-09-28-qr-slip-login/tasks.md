## 1. Reader — QR entry and login hint

- [x] 1.1 `quarkus.oidc.reader.authentication.forward-params=login_hint` in `application.properties`; confirm the property name against the Quarkus version in use
- [x] 1.2 Route `GET /qr` on `ReaderResource` (public, outside `tenant-paths`): `u` checked against the username pattern of `AccountRequestValidator` (shared, not copied), `303` to `/login?login_hint=<u>#` or `/login#`, `Cache-Control: no-store`, no session read or created; verify `ReaderTenantScope` leaves `/qr` off the reader tenant
- [x] 1.3 Tests (`@QuarkusTest`, JUnit 5 + AssertJ): `/qr?u=lena` → exact `Location` and `no-store`; invalid `u` and missing `u` → `/login#`; no `Set-Cookie`; `/login?login_hint=lena` redirects to the authorization endpoint with `login_hint=lena`; `/login` without hint carries no `login_hint`; existing `ReaderLoginTest` scenarios still pass

## 2. Admin — QR code on the slip

- [x] 2.1 Settle the QR encoder (design D4): try a KMP library with a `wasmJs` artifact exposing the module matrix against the current Kotlin/Compose versions; otherwise port Nayuki's QR Code generator (MIT, attribution in the file header) into `commonMain`; wrap it behind a small `QrCode` (matrix of booleans, level M)
- [x] 2.2 `SlipQr.payload(siteUrl, username, password)` (trailing `/` trimmed) in common code
- [x] 2.3 `AccountSlipScreen`: QR code drawn with a Compose `Canvas` from the matrix (quiet zone, black on white regardless of theme) plus the localized hint line; `PrintableSlip` gains `qrPayload` and `qrHint`, `toString` still hides secrets
- [x] 2.4 `BrowserSlipPrinter`: `<svg>` built via `createElementNS` (viewBox in modules, `fill` attribute, `shape-rendering="crispEdges"`, no `style` attribute) next to the text; `styles.css` print rules size it to about 35 mm and keep the slip on one A4 page; CSP and `csp-style-hashes.txt` unchanged (admin CSP test green)
- [x] 2.5 Strings de/en for the hint line (e.g. "Scan the code to open the login." / „Code scannen öffnet die Anmeldung.")
- [x] 2.6 Kotlin tests: payload of the spec scenario exactly; trailing-slash trimming; encoder yields the expected matrix size and finder patterns for that payload; `PrintableSlip.toString` does not contain the password or payload

## 3. Contract and docs

- [x] 3.1 `http/reader.http`: `/qr?u=lena`, invalid `u`, `/login?login_hint=lena`; run them against a live dev backend
- [x] 3.2 `docs/architecture.md`: `/qr` in the reader routes (outside the tenant, like `/text-size`), `login_hint` forwarding; `ai/primer/endpoints.md` unchanged (no `/api` change) — confirm it lists no reader login routes that would need the hint
- [x] 3.3 `docs/design-guidelines.md` §6: QR code content, size, placement and hint line replace the reserved-room note; `docs/roles-and-workflow.md`: hand-over line mentions the QR code, "Later: …" line removed
- [x] 3.4 `docs/vision.md` M8 row: "QR code on the slip (address + credentials, implemented), Android/iOS targets reading it, store publishing"; `ai/memory/project_vision.md` if it mentions the token exchange
- [x] 3.5 `ai/open-proposals.md` M8 entry: drop the token exchange, keep Android/iOS targets, reading the slip's QR code in the app (`<base>/qr?u=…#pw=…`) and store publishing

## 4. Verification

- [x] 4.1 `./mvnw verify` in `backend/` (or the reader test classes plus `ReaderLoginTest` if the full suite is not needed) and `./gradlew check` in `admin/`
- [x] 4.2 Headless UI check (Playwright) in dev: create account `lena`, slip shows the QR code; print preview (`page.pdf()`) shows the slip with the code on one page; decode a screenshot of the code with a QR decoder and compare with the expected payload
- [x] 4.3 Browser flow in Chromium and Firefox (Playwright): open `/qr?u=lena#pw=tiger-wolke-apfel-leiter` anonymously → Keycloak form with `lena` pre-filled and no `#pw=` in the URL; log in with the password → reader front page
- [x] 4.4 Stop every server/container started for verification (check with `ps`/`ss`/`docker`)
