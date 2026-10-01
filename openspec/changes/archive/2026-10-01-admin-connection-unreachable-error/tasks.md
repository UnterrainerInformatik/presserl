## 1. Admin

- [x] 1.1 Add `ConnectError.UNREACHABLE` and an optional `address` to `ConnectionState.Start`
- [x] 1.2 Add the pure `classify(e: Throwable): ConnectError` of design D1 and use it in `ConnectionModel.check`; set `address` to the normalised base for `UNREACHABLE`
- [x] 1.3 Give `ApiClient.clientConfig()` its own request/connect timeout (D3)
- [x] 1.4 Add `expect fun logWarning(message, cause)` with Android (`Log.w`, tag `presserl`) and wasm (`console.warn`) actuals; log every failed check with base, error and cause/HTTP status, never credentials (D4)
- [x] 1.5 Add the de/en string `connect_error_unreachable` with the address placeholder and map it in `StartScreen`

## 2. Tests

- [x] 2.1 `ConnectionModelTest`: mock engine throwing an IO exception → `Start(UNREACHABLE, address = base)`; `404` and HTML body → `Start(NO_PRESSERL_SERVER)` as before
- [x] 2.2 `ConnectionModelTest`: a scanned slip for an unreachable host → `UNREACHABLE` with the base only (no fragment), nothing stored
- [x] 2.3 Unit tests for `classify` (response exception, conversion failure, timeout exception, generic IO exception)
- [x] 2.4 Run the admin common/Android host tests (see `reference_build_and_test.md`)

## 3. Verification

- [x] 3.1 On the A54: enter an address with VPN off / non-existent host → "not reachable" with the address; `adb logcat -s presserl` shows the cause
- [x] 3.2 On the A54: enter an address that answers `404` → "no presserl newspaper"; logcat shows `HTTP 404`
