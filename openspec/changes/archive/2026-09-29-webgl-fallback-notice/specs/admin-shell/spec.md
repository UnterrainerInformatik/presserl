## ADDED Requirements

### Requirement: Start-up failures show a notice instead of a blank page
Before the admin app starts, the page SHALL check that the browser supports WebAssembly and can
create a WebGL context. The app SHALL only be loaded when both checks pass. When a check fails,
the page SHALL show a readable HTML notice instead of a blank page, naming what is missing and how
to enable it; for WebGL the notice SHALL list allowing WebGL for the site in the browser, the
Firefox setting `webgl.disabled`, and exceptions in fingerprinting or canvas blockers. When the
checks pass but the app fails to load or throws an uncaught error before it has drawn its first
frame, the page SHALL show a generic notice that the app could not start, with a hint to reload.
The notice SHALL be in English when the browser's preferred language is English and in German
otherwise. The notice SHALL work under the admin Content-Security-Policy without inline scripts or
inline styles, and the policy SHALL NOT be relaxed for it.

#### Scenario: WebGL disabled
- **WHEN** a user whose browser has WebGL disabled opens `/admin/`
- **THEN** the page shows a notice explaining that the admin app needs WebGL and how to allow it, and the app is not loaded

#### Scenario: WebGL available
- **WHEN** a user whose browser supports WebAssembly and WebGL opens `/admin/`
- **THEN** the admin app starts as before and no notice is shown

#### Scenario: WebAssembly unsupported
- **WHEN** a user whose browser does not support WebAssembly opens `/admin/`
- **THEN** the page shows a notice that the browser is not supported and the app is not loaded

#### Scenario: App fails before its first frame
- **WHEN** the checks pass but loading the app fails or it throws an uncaught error before drawing its first frame
- **THEN** the page shows a notice that the app could not start, with a hint to reload

#### Scenario: Notice language
- **WHEN** a user whose browser prefers `en-GB` and has WebGL disabled opens `/admin/`
- **THEN** the notice is in English; with `de-AT` it is in German

#### Scenario: No CSP violation
- **WHEN** the notice is shown
- **THEN** the browser reports no Content-Security-Policy violation caused by the page's own scripts or styles

## MODIFIED Requirements

### Requirement: Admin bundle caching survives deploys
Responses under `/admin/` SHALL carry an explicit `Cache-Control` header. Files with
content-hashed names (`*.wasm`) SHALL be sent with `public, max-age=31536000, immutable`. All
other responses under `/admin/` SHALL be sent with `no-cache`, so browsers and shared caches
revalidate them on every load and a new deploy takes effect on the next page load. Caching of
responses outside `/admin/` SHALL NOT be changed.

#### Scenario: Entry files are revalidated
- **WHEN** a browser requests `GET /admin/`, `GET /admin/startup.js`, `GET /admin/composeApp.js` or `GET /admin/styles.css`
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
