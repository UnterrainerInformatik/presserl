## Context

- The backend already builds a Keycloak Admin client (`KeycloakAdminProducer`) with the
  `presserl-backend` service account; it holds `manage-users`, `view-users`, `query-users`,
  `query-groups` in both the dev realm and `deploy/keycloak/presserl-realm.json`. So far only
  `PublisherBootstrap` uses it, on its own thread.
- Newspaper-wide roles are the groups `publisher`, `editor-in-chief`, `reader`
  (`NewspaperRole`); the token's `groups` claim becomes Quarkus roles via
  `NewspaperRoleAugmentor`, so `@RolesAllowed` works.
- API resources are reactive (Quarkus REST + Hibernate Reactive). The Keycloak Admin client is
  a blocking RESTEasy client.
- The refusal body `ApiErrorDto`/`FieldError` lives in the `article` package.
- The admin app navigates with a `Route` stack below a header (`App.kt`); `siteUrl` (the reader
  origin) is already passed in. The Compose Wasm UI renders into a canvas, so the canvas itself
  prints poorly. Admin pages have a strict CSP (`'self'`, no `'unsafe-inline'`).

## Goals / Non-Goals

**Goals:**
- A thin account layer over Keycloak without any Presserl DB table: Keycloak stays the only
  store for users and newspaper-wide roles.
- One place that decides which roles a user may assign, reusable by the next M2 changes
  (sections, role changes, reset, lock).
- A slip that prints cleanly from the browser.

**Non-Goals:**
- Caching Keycloak data; paging the account list (a family newspaper has tens of accounts).
- A generic "user admin" abstraction for other identity providers.

## Decisions

### D1 — REST contract

`GET /api/accounts` → `200`
```json
{
  "assignableRoles": ["PUBLISHER", "EDITOR_IN_CHIEF", "READER"],
  "accounts": [
    { "id": "5f0c…", "username": "chief", "firstName": "Chief", "lastName": "Editor",
      "roles": ["EDITOR_IN_CHIEF"], "enabled": true }
  ]
}
```

`GET /api/accounts/username-suggestion?firstName=J%C3%BCrgen` → `200 {"username": "juergen"}`

`POST /api/accounts`
```json
{ "firstName": "Lena", "lastName": "", "username": "lena", "roles": ["EDITOR_IN_CHIEF"] }
```
→ `201`, `Location: /api/accounts/{id}`
```json
{
  "account": { "id": "9a1e…", "username": "lena", "firstName": "Lena", "lastName": "",
               "roles": ["EDITOR_IN_CHIEF"], "enabled": true },
  "password": "tiger-wolke-apfel-leiter"
}
```
Errors use the shared error body `{"errors": [{"field": …, "message": …}]}`: `400` (all
violations), `403` with field `roles` (delegation), `409` with field `username`, `503` with
field `null` (Keycloak unavailable). `403` from `@RolesAllowed` stays an empty body, as for
articles.

The password travels in the creation response rather than a separate "reveal" call because it
exists only at that moment; nothing server-side could hand it out later. `GET /api/accounts/{id}`
is not added — the `Location` header points at a resource the next M2 changes (role change,
reset, lock) will live under.

Alternative considered: let the server pick the username on `POST` when omitted. Rejected —
the UI must show and allow editing the username before creation, so a suggestion endpoint is
needed anyway, and one path is simpler than two.

### D2 — Blocking Keycloak calls off the event loop

`AccountResource` methods are annotated `@Blocking` (plain return types, worker thread) and call
an `@ApplicationScoped AccountService` that wraps the Keycloak Admin client. Alternative:
wrapping each call in `Uni.createFrom().item(...).runSubscriptionOn(executor)` — more code for
no benefit, since account requests do not touch the reactive DB.

### D3 — Listing with a fixed number of Keycloak calls

`users().list(0, 1000)` plus `groups().group(id).members(0, 1000)` for each of the three newspaper
groups (group ids looked up by name as in `PublisherBootstrap`); roles are joined in memory. That
is five calls independent of the number of users, instead of one `groups()` call per user.
Service-account users are filtered by `serviceAccountClientLink != null` and, as a fallback, by the
`service-account-` username prefix. The group lookup moves out of `PublisherBootstrap` into a
small shared `NewspaperGroups` helper used by both.

### D4 — Creation is all-or-nothing

Sequence: validate input → check delegation → check username free (`searchByUsername(u, true)`)
→ `users().create()` with names, `enabled=true` and the password credential (`temporary=false`)
→ `joinGroup` for each role. If any step after `create` fails, the service deletes the new user
and rethrows, so no half-created account remains. A `409` from `create` itself (race with a
parallel creation) is mapped to the same `409 username` as the pre-check.

The creation is logged at INFO with username, creator username and roles; the password is never
passed to a logger, and `CreatedAccount.toString()` masks it (same pattern as
`PublisherCredentials`).

### D5 — Delegation rule in one place

`RoleDelegation.assignableBy(CurrentUser)` returns the assignable newspaper roles: a publisher
gets all three, an editor-in-chief `EDITOR_IN_CHIEF` and `READER`, others none. Both the list
response (`assignableRoles`) and the creation check use it, so the UI never re-implements the rule
("the server decides"). The next M2 change extends it with section roles and scopes.

### D6 — Username derivation

`UsernameDeriver` is a pure function (first name → base) followed by a collision loop against
Keycloak (`base`, `base-2`, `base-3`, …, exact search). Folding: German umlauts and `ß` first
(`ä→ae`…), then `Normalizer.Form.NFD` and removal of combining marks, then lower case, then
`[^a-z0-9]+` → `-`, trim `-`, cut to 32, empty → `user`. With a suffix the base is shortened so
that base + suffix ≤ 32 and trailing `-` is trimmed again. Unit-tested without Keycloak.

### D7 — Pass-phrase word list

A resource file `backend/src/main/resources/accounts/words-de.txt`, one word per line, lower case,
`a-z` only (no umlauts or `ß` — the words must be typeable on any keyboard and by children), 3–8
letters, at least 1000 distinct entries, curated for kid-friendliness (animals, food, nature,
things, colours; no insults, bodily or violent terms, brand names). A unit test enforces shape,
size and uniqueness. `PassPhraseGenerator` loads it once and picks four words with a
`SecureRandom` (`nextInt(size)` — uniform). With ≥1000 words that is ≥ 2^39.8 combinations,
sufficient together with Keycloak's brute-force protection that the realm template enables.

Alternatives: an existing diceware list (German diceware lists contain umlauts, obscure words and
adult vocabulary — not kid-friendly); generating random syllables (hard to read aloud and to
type for children).

### D8 — Shared error body

`ApiErrorDto` and `FieldError` move from `article` to a new `info.unterrainer.presserl.api`
package; the article classes import them from there. The wire format is unchanged.
`AccountException(status, errors)` plus mappers mirror `ArticleException`. Keycloak failures
(`ProcessingException`, `WebApplicationException` with `401`/`403`/`5xx` from the admin client)
become `503`.

### D9 — Admin app structure

- `ApiClient`: `accounts()`, `usernameSuggestion(firstName)`, `createAccount(request)`; DTOs in
  `Dtos.kt`; `ApiException`-style error parsing as already done for articles.
- `Route.Accounts`, `Route.NewAccount`, `Route.AccountSlip(created)` on the existing stack. The
  header shows "Accounts"/"Articles" toggles for users with `PUBLISHER` or `EDITOR_IN_CHIEF`
  (decided from `MeDto.roles`; only visibility — the server enforces access).
- `NewAccountModel` (plain Kotlin, unit-tested) holds form state: `usernameEdited` flag, debounced
  suggestion requests (300 ms after the last first-name change, stale responses ignored), field
  errors from the server, `canCreate`.
- Slip data (`newspaper name`, `siteUrl`, `username`, `password`) is held only in the route
  object; leaving the slip drops it.

### D10 — Printing the slip without a canvas

A `SlipPrinter` interface in `commonMain`, implemented in `wasmJsMain`: it creates a
`<section id="presserl-slip">` element with DOM APIs (`createElement`, `textContent` — never
`innerHTML`), appends it to `document.body`, calls `window.print()` and removes the element on
`afterprint`. `styles.css` (already `'self'`) gets the rules: hidden on screen; under `@media print`
everything except `#presserl-slip` hidden, `@page { size: A4; margin: 20mm }`, large monospace
password. No inline styles or scripts, so the CSP stays unchanged and the style-hash list is not
affected.

Alternatives: `window.print()` on the Compose canvas (bitmap, blurry, prints the whole app);
opening a `blob:` window with generated HTML (popup blockers, and the CSP inheritance of `blob:`
documents differs between browsers); a server-rendered slip page (the password would have to be
sent back to the server).

## Risks / Trade-offs

- [Keycloak Admin client on a worker thread per request] → acceptable for a handful of admin
  requests; no pooling changes needed.
- [List limited to 1000 users per call] → far above a family newspaper; documented in the primer.
- [Username pre-check and creation are not atomic] → Keycloak's own uniqueness returns `409`,
  mapped to the same error.
- [Compensating delete can fail too] → logged at ERROR with the orphan's username; the publisher
  can remove it in the Keycloak console. Very unlikely because both calls use the same client
  moments apart.
- [Curating 1000+ kid-friendly words by hand is laborious] → the list is reviewed once in the
  apply phase; the unit test guards format, and Gerald reviews the content before archive.
- [Editors-in-chief can create further editors-in-chief] → intended by the delegation rule
  ("at or below their own level"); publishers see every account.

## Migration Plan

No data migration. Deploys with the next image; the realm template and `.env` are unchanged.
Rollback = previous image; accounts created meanwhile stay valid Keycloak users.
