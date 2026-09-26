## Why

Keycloak's user profile requires usernames of at least 3 characters, but the `accounts` spec sets no
lower bound. A first name like `Li` is suggested as `li`, passes our validation and then fails at
Keycloak with a `400` — the admin form offers a username the server cannot create.

## What Changes

- `POST /api/accounts` rejects a `username` shorter than 3 characters with `400` at the field
  `username`, before Keycloak is called.
- `GET /api/accounts/username-suggestion` never suggests a username shorter than 3 characters: a
  base shorter than 3 characters is numbered from `-1` (`li` → `li-1`, then `li-2`, `li-3`, …).
  Bases of 3 or more characters behave as before (`anna`, then `anna-2`, …).
- `ai/primer/endpoints.md` and `http/accounts.http` document and exercise both rules.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `accounts`: "Username derived from the first name" gains the short-base rule; "Account input is
  validated" gains the 3-character minimum.

## Non-goals

- No client-side length check in the admin app: the form already shows a server `400` at the
  username field, and suggestions are always valid.
- No change to the upper bound (32), the allowed character set or the `user` fallback.
- No migration of existing accounts — Keycloak never accepted shorter usernames.

## Impact

- **backend**: `UsernameDeriver` (suggestion numbering for short bases), `AccountRequestValidator`
  (minimum length), their unit tests and `AccountResourceTest`.
- **admin**: none (behaviour follows from the server).
- **reader, deploy**: none.
- **docs**: `ai/primer/endpoints.md` (`GET /api/accounts/username-suggestion`,
  `POST /api/accounts`), `http/accounts.http`.
- **Backlog**: remove the entry from `ai/open-proposals.md`.
