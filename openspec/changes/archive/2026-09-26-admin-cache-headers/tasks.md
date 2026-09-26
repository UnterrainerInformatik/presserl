## 1. Backend

- [x] 1.1 In `web/SecurityHeaders.java` add `static String adminCacheControl(String path)` returning `public, max-age=31536000, immutable` for paths ending in `.wasm` and `no-cache` otherwise; set it as `Cache-Control` in the headers-end handler for paths under `/admin/` (next to the CSP), and update the class Javadoc
- [x] 1.2 Unit test for `adminCacheControl` (`.wasm`, `/admin/`, `composeApp.js`, `styles.css`, `composeResources/...`)
- [x] 1.3 Add a stub `backend/src/test/resources/META-INF/resources/admin/0123456789abcdef0123.wasm` and a stub `composeApp.js`
- [x] 1.4 Extend `AdminDeliveryTest`: `/admin/` and `/admin/composeApp.js` → `no-cache` without `immutable`; stub `.wasm` → `public, max-age=31536000, immutable`; conditional request with `If-Modified-Since` → `304` with `no-cache`; `/api/newspaper` `Cache-Control` unchanged

## 2. Deploy

- [x] 2.1 `deploy/INSTALL.md` section 7 (Updating): note that a CDN/proxy cache in front of Presserl needs one purge of `/admin/*` when upgrading from a version without explicit admin cache headers
- [x] 2.2 `deploy/INSTALL.md` section 8 (Troubleshooting): row for "admin app blank, console shows `WebAssembly … unsupported MIME type 'text/html'` / `.wasm` 404" → stale cached `composeApp.js`; hard reload, purge CDN cache

## 3. Verification

- [x] 3.1 Run the backend test suite locally (`./mvnw verify` in `backend/`)
- [ ] 3.2 After deploy: `curl -I` on `https://presserl.unterrainer.info/admin/composeApp.js` and one `.wasm` shows the new headers
