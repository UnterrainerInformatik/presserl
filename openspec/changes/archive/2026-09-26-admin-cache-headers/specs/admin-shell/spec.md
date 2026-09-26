## ADDED Requirements

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
