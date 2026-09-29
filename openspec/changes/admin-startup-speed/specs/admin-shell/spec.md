## ADDED Requirements

### Requirement: A loading indicator replaces the blank page during start-up
While the admin app is loading, the page SHALL show a loading indicator with a short text saying
that the administration is loading, instead of a blank page. The indicator SHALL appear only when
the app has not drawn its first frame within 500 ms of the start-up checks passing, and it SHALL be
removed as soon as the app draws its first frame. When a start-up notice is shown, the notice
SHALL replace the indicator. The indicator SHALL NOT be shown when a start-up check fails. Its text
SHALL follow the same language rule as the start-up notices (English when the browser prefers
English, German otherwise). Any animation SHALL stop when the user prefers reduced motion. The
indicator SHALL work under the admin Content-Security-Policy without inline scripts or inline
styles, and the policy SHALL NOT be relaxed for it.

#### Scenario: Slow first load
- **WHEN** a user with an empty browser cache opens `/admin/` and the app takes several seconds to load
- **THEN** the page shows the loading indicator after at most 500 ms until the app draws its first frame or navigates to the login

#### Scenario: Fast load does not flash
- **WHEN** the app draws its first frame within 500 ms
- **THEN** the loading indicator is never shown

#### Scenario: Indicator gives way to the app
- **WHEN** the loading indicator is shown and the app draws its first frame
- **THEN** the indicator is removed and the app is visible

#### Scenario: Indicator gives way to a notice
- **WHEN** the loading indicator is shown and loading the app fails
- **THEN** the indicator is removed and the "could not start" notice is shown

#### Scenario: Failed check shows no indicator
- **WHEN** a user whose browser has WebGL disabled opens `/admin/`
- **THEN** only the WebGL notice is shown, without a loading indicator

#### Scenario: Indicator language
- **WHEN** a user whose browser prefers `en-GB` sees the loading indicator
- **THEN** its text is English; with `de-AT` it is German

#### Scenario: No CSP violation
- **WHEN** the loading indicator is shown
- **THEN** the browser reports no Content-Security-Policy violation caused by the page's own scripts or styles

### Requirement: Admin bundle is sent pre-compressed
The `.wasm` and `.js` files of the admin bundle in the container image SHALL be accompanied by a
Brotli-compressed variant produced at build time with the highest compression quality. When a
client requests such a file and its `Accept-Encoding` header accepts `br`, the response SHALL carry
the compressed variant with `Content-Encoding: br` and the `Content-Type` of the original file.
Otherwise the response SHALL carry the original file without `Content-Encoding`. Responses for
these files SHALL carry `Vary: Accept-Encoding`. The `Cache-Control` values and conditional-request
behaviour of these responses SHALL be the same as for the original files. A file without a
compressed variant SHALL be served as before.

#### Scenario: Browser accepting Brotli
- **WHEN** a browser requests a `.wasm` file under `/admin/` with `Accept-Encoding: gzip, deflate, br`
- **THEN** the response carries `Content-Encoding: br`, `Content-Type: application/wasm`, `Vary: Accept-Encoding` and `Cache-Control: public, max-age=31536000, immutable`, and its body decompresses to the original file

#### Scenario: Client without Brotli
- **WHEN** a client requests the same `.wasm` file without `br` in `Accept-Encoding`
- **THEN** the response carries the original file, no `Content-Encoding` and `Vary: Accept-Encoding`

#### Scenario: Compressed entry script is revalidated
- **WHEN** a browser accepting Brotli requests `GET /admin/composeApp.js`
- **THEN** the response carries `Content-Encoding: br`, a JavaScript `Content-Type` and `Cache-Control: no-cache`

#### Scenario: Unchanged compressed entry script
- **WHEN** a browser accepting Brotli revalidates `GET /admin/composeApp.js` with the `If-Modified-Since` value it received earlier
- **THEN** the response is `304` with `Cache-Control: no-cache`

#### Scenario: File without a compressed variant
- **WHEN** a browser accepting Brotli requests `GET /admin/styles.css`
- **THEN** the response is the original file without `Content-Encoding`, as before

#### Scenario: Smaller download
- **WHEN** a browser with an empty cache opens `/admin/` on a deployed image
- **THEN** the Wasm modules are transferred Brotli-compressed at the build-time size, not uncompressed
