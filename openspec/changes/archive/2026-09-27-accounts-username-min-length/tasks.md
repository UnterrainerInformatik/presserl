## 1. Backend

- [x] 1.1 `UsernameDeriver`: add `MIN_LENGTH = 3`; make `firstFree` start at `-1` for a base shorter than `MIN_LENGTH` (`li` → `li-1`, `li-2`, …) and keep `base`, `base-2`, … otherwise; update the class Javadoc
- [x] 1.2 `UsernameDeriverTest`: short base yields `li-1`, skips taken `li-1` to `li-2`; one-character base `a` → `a-1`; three-character base `max` stays unsuffixed
- [x] 1.3 `AccountRequestValidator`: reject a `username` shorter than `UsernameDeriver.MIN_LENGTH` with `400` at `username` ("must be at least 3 characters"); update the class Javadoc
- [x] 1.4 `AccountResourceTest`: suggestion for `Li` is `li-1`; `POST` with `username` `li` is `400` at `username` and creates no user

## 2. Admin

- [x] 2.1 Confirm no change is needed: request/response DTOs are unchanged and the form already shows a server `400` at the username field

## 3. Contract / Docs

- [x] 3.1 `ai/primer/endpoints.md`: `GET /api/accounts/username-suggestion` — short bases are numbered from `-1`; `POST /api/accounts` — `username` 3–32 characters
- [x] 3.2 `http/accounts.http`: suggestion for `Li` asserts `li-1`; `POST` with `username` `li` asserts `400` at `username`
- [x] 3.3 Remove the "username minimum length" entry from `ai/open-proposals.md`

## 4. Verification

- [x] 4.1 `cd backend && ./mvnw verify` passes
- [x] 4.2 `cd admin && ./gradlew check` passes
- [x] 4.3 Run `http/accounts.http` against `quarkus:dev`; all requests pass
- [x] 4.4 `openspec validate accounts-username-min-length --strict` passes
