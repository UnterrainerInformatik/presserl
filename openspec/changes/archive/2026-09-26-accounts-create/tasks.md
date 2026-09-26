## 1. Backend — shared pieces

- [x] 1.1 Move `ApiErrorDto` and `FieldError` to `info.unterrainer.presserl.api`; update the article imports (no wire change)
- [x] 1.2 Extract the group lookup by name from `PublisherBootstrap` into `NewspaperGroups` (group id per `NewspaperRole`); `PublisherBootstrap` uses it; existing bootstrap tests stay green
- [x] 1.3 `RoleDelegation.assignableBy(CurrentUser)` (publisher → all three, editor-in-chief → `EDITOR_IN_CHIEF`, `READER`, others → none) with unit tests

## 2. Backend — usernames and pass-phrases

- [x] 2.1 `UsernameDeriver`: pure folding (umlauts/`ß`, NFD diacritics, lower case, `[^a-z0-9]+`→`-`, trim, 32 chars, empty → `user`) and suffixing (`-2`, `-3`, base shortened to stay ≤ 32); unit tests incl. `Jürgen Maria`, `Zoë`, `李`, long names with suffix
- [x] 2.2 Curate `backend/src/main/resources/accounts/words-de.txt`: ≥ 1000 distinct kid-friendly German words, `a-z` only, 3–8 letters, no insults/violent/bodily/brand terms
- [x] 2.3 `PassPhraseGenerator` (loads the list once, four words via `SecureRandom`, joined by `-`) with unit tests: list shape/size/uniqueness, password pattern, words from the list, two calls differ

## 3. Backend — account service and endpoints

- [x] 3.1 `AccountDto`, `AccountListDto` (`assignableRoles`, `accounts`), `CreateAccountRequest`, `CreatedAccountDto` (masked `toString`), `UsernameSuggestionDto`
- [x] 3.2 `AccountService.list()`: users list + members of the three newspaper groups joined in memory, service accounts filtered, sorted by username
- [x] 3.3 `AccountService.suggestUsername(firstName)` using `UsernameDeriver` and exact Keycloak search
- [x] 3.4 Input validation for `POST` (all violations at once: names, username pattern/length/`service-account-` prefix, roles missing/empty/unknown, unknown fields; trimming, duplicate roles collapsed)
- [x] 3.5 `AccountService.create(...)`: delegation check (`403 roles`), username pre-check (`409 username`), create user with password credential (`temporary=false`), join groups, compensating delete on failure, Keycloak `409` → `409 username`; INFO log with username, creator, roles (never the password)
- [x] 3.6 `AccountException` + mappers; Keycloak unreachable/refusing → `503` with error body
- [x] 3.7 `AccountResource` at `/api/accounts` (`@RolesAllowed PUBLISHER, EDITOR_IN_CHIEF`, `@Blocking`): `GET`, `GET /username-suggestion`, `POST` with `201` + `Location`

## 4. Backend — tests

- [x] 4.1 `@QuarkusTest` access: no token → `401`, `reader` → `403`, `chief` and publisher → `200`
- [x] 4.2 List: dev-realm users with roles, `nogroups` with `[]`, no service account, sorted; `assignableRoles` for publisher and for `chief`
- [x] 4.3 Suggestion: `Jürgen Maria` → `juergen-maria`; collision after creating `anna` and `anna-2` → `anna-3`; blank/missing `firstName` → `400`
- [x] 4.4 Create: publisher creates `EDITOR_IN_CHIEF` account → `201`, `Location`, password pattern; the new user obtains a token with that password (direct grant via the test client) and `GET /api/me` shows `EDITOR_IN_CHIEF`; several roles; two creations yield different passwords
- [x] 4.5 Create refusals: `400` listing username and roles; `409` for `chief`; `403 roles` when `chief` requests `PUBLISHER` (and no user created); `chief` creates a `READER` → `201`
- [x] 4.6 Log check: creation log line contains username/creator/roles and not the password
- [x] 4.7 Unit test for the compensating delete and the `503` mapping with a stubbed Keycloak client

## 5. Admin app

- [x] 5.1 DTOs and `ApiClient` methods `accounts()`, `usernameSuggestion()`, `createAccount()` with error-body parsing; `ApiClientTest`/`DtoTest` cases
- [x] 5.2 `Route.Accounts`, `Route.NewAccount`, `Route.AccountSlip`; header entry "Accounts"/"Articles" only for `PUBLISHER`/`EDITOR_IN_CHIEF`
- [x] 5.3 `AccountListScreen`: username, names, localized roles or "no role", disabled marker, "New account"
- [x] 5.4 `NewAccountModel` (form state, `usernameEdited`, debounced suggestion with stale-response guard, server field errors, `canCreate`) with unit tests
- [x] 5.5 `NewAccountScreen`: fields, role choices from `assignableRoles` (none preselected), errors at fields, input kept on error
- [x] 5.6 `AccountSlipScreen`: newspaper name, reader address (`siteUrl`), username, password, "shown only now" note, "Print", "Done"
- [x] 5.7 `SlipPrinter` interface (common) + Wasm implementation via DOM APIs (`textContent`, no `innerHTML`), removed on `afterprint`; print rules in `styles.css` (screen hidden, print-only slip, `@page A4`); `CspStyleHashTest` stays green
- [x] 5.8 German and English strings for all new texts; `StringsTest`/`LabelsTest` extended

## 6. Contract / Docs

- [x] 6.1 `ai/primer/endpoints.md`: new "Accounts" section with the three endpoints, shapes, errors, delegation table, 1000-user limit
- [x] 6.2 `http/accounts.http`: list, suggestion, create (publisher), refused creations (`chief` → `PUBLISHER`, taken username); run against `quarkus dev`
- [x] 6.3 `docs/roles-and-workflow.md`: note under Accounts that creation is available for newspaper-wide roles (only if the text needs it after reading)
- [x] 6.4 `ai/open-proposals.md`: M1 block removed and M2 trimmed (done at propose time)

## 7. Verification

- [x] 7.1 Run the backend suite and the admin suite (`reference_build_and_test.md`); both green
- [x] 7.2 Manual check in dev mode: publisher creates an editor-in-chief in the admin app, prints the slip (print preview shows only the slip), logs in as the new user
- [x] 7.3 `openspec validate accounts-create --strict`
