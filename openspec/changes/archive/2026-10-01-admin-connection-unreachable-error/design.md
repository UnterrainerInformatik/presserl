## Context

`ConnectionModel.check` (common code, used by the Android host) calls a `clientConfig(base)` lambda.
The Android host passes `ApiClient(http, base).clientConfig()`. That client runs with
`expectSuccess = true`, content negotiation and `HttpTimeout` installed **without** any timeout
values. Every `Throwable` except cancellation becomes `null`, which becomes
`ConnectError.NO_PRESSERL_SERVER`. The error is an enum without data, and `StartScreen` maps it
to a fixed string. Nothing is logged. The only log call in the admin module is `android.util.Log.w`
in `QrScanner.kt`.

## Goals / Non-Goals

**Goals:** tell "no HTTP answer" apart from "wrong answer", name the address for the first case,
end the check after a bounded time, and log the cause.

**Non-Goals:** retries, VPN detection, and changes to the web admin app or the backend.

## Decisions

### D1 — Classify by "did an HTTP response arrive?"
- A `ResponseException` (any non-2xx status under `expectSuccess`) or a failure to read the body as
  `ClientConfigDto` (`ContentConvertException` / `NoTransformationFoundException` /
  `SerializationException`) means the server answered: `NO_PRESSERL_SERVER`. A missing issuer or
  client id also stays `NO_PRESSERL_SERVER`, as today.
- Every other exception (except `CancellationException`, which is rethrown) means no answer
  arrived: `UNREACHABLE`. That covers `UnknownHostException`, `ConnectException`,
  `SSLException`, `SocketTimeoutException` and ktor's `HttpRequestTimeoutException`.
- *Alternative considered:* matching on platform IO exception classes. Rejected, because those are
  JVM-only and would need `expect`/`actual`. The "response or not" rule works in common code and
  fails safe: an exception nobody anticipated reads as "not reachable", which points at the network.

The classification is a small pure function `classify(e: Throwable): ConnectError`, so it can be
tested without HTTP.

### D2 — The address travels with the state
`ConnectionState.Start` gets an optional `address: String?` next to `error`. It is set to the
normalised base (never the slip URL, whose fragment holds the pass-phrase) when the error is
`UNREACHABLE`. `StartScreen` formats the new string with it:
- de: „Der Server %1$s ist nicht erreichbar. Prüfe Netzwerk und VPN.“
- en: "The server %1$s cannot be reached. Check the network and VPN."

`NO_PRESSERL_SERVER` keeps its text. It is also used for malformed input, where no address exists.

### D3 — A timeout only for the check
The check's request gets its own `timeout { requestTimeoutMillis = 15_000; connectTimeoutMillis = 10_000 }`
inside `ApiClient.clientConfig()`. Other calls keep their current behaviour. 15 s is long enough
for a slow mobile network and short enough that the user doesn't give up.

### D4 — Logging through a tiny `expect` function
`expect fun logWarning(message: String, cause: Throwable?)` in common code. The Android `actual`
uses `Log.w("presserl", …)`, the tag `QrScanner` already uses. The wasm `actual` uses
`console.warn`. The message reads, for example,
`client-config check of https://x failed: UNREACHABLE (UnknownHostException: Unable to resolve host "x")`,
or `… NO_PRESSERL_SERVER (HTTP 404)` for a response. Only the base address and the exception go
into the log, never `SlipCredentials`.

## Risks / Trade-offs

- A captive portal or a proxy that answers `404` for an unknown host (the actual VPN case of
  2026-09-30) is an HTTP response, so it still reads "no presserl newspaper". The log with
  `HTTP 404` now shows this. The message could say more (e.g. "check VPN"), but only with guessing,
  so it stays as it is. Accepted.
- A 15 s request timeout could cut off a very slow first TLS handshake. The user can simply retry.
