## Context

- Keycloak has no separate refresh-token lifetime for normal (online) sessions: a refresh token
  expires with the SSO session, i.e. after `ssoSessionIdleTimeout` (today 1800 s) without use or
  `ssoSessionMaxLifespan` (today 36000 s) in total, whichever comes first.
- The reader is a Quarkus OIDC `web-app` tenant. It keeps ID, access and refresh token in an
  encrypted session cookie whose max-age is the ID token's remaining lifetime plus
  `authentication.session-age-extension` (default 5 minutes). With `token.refresh-expired=true` it
  refreshes an expired token on the next request — but only while the cookie still exists.
- The dev realm (`backend/src/main/resources/dev/presserl-realm.json`) is loaded by Keycloak Dev
  Services in dev and test, so tests see the same session values as the template.
- Live realms (staging, alexpresse.net) were created from the template earlier; Keycloak never
  re-reads the template.

## Goals / Non-Goals

**Goals:**
- A reader who logged in once stays logged in for up to 180 days of inactivity on the same device.
- Locking an account or resetting its password still cuts access within 5 minutes.

**Non-Goals:**
- Offline tokens, per-client lifetimes, web admin persistence across reloads (see proposal).

## Decisions

1. **Realm-wide SSO session of 180 days (idle = max = 15552000 s).** One setting covers reader and
   admin app. Idle equal to max means half a year from login, not half a year from the last
   visit; a rolling session would need a larger max and was not asked for.
   *Alternative:* `offline_access` for the reader client only with `offlineSessionIdleTimeout`
   180 d — more targeted, but needs proof that lock/reset revokes offline sessions and changes the
   reader's scope; deferred.
2. **`accessTokenLifespan` stays 300 s.** It bounds how long a locked/reset account keeps access
   (lock and reset end all Keycloak sessions, so the next refresh fails — covered by existing tests
   in `AccountResourceTest`).
3. **`quarkus.oidc.reader.authentication.session-age-extension=180d`.** Makes the cookie live as
   long as the refresh token can. If the Keycloak session ends earlier (logout elsewhere, lock),
   the refresh fails and Quarkus drops the cookie and sends the visitor to `/` as anonymous — the
   existing behaviour of the requirement.
4. **Test via cookie expiry.** In `ReaderLoginTest`, after a login, assert that the session
   cookie's expiry lies at least 179 days ahead. This proves the cookie is persistent and carries the
   session-age extension; the realm values themselves are checked by reading the template. A test
   that actually waits for token expiry is not practical.
5. **Live realms via the admin console**, *Realm settings → Sessions*: *SSO session idle* 180 days,
   *SSO session max* 180 days. Existing sessions keep their old expiry; readers get the long session
   at their next login. Gerald makes the change himself; Claude hands him the exact console steps
   during apply and waits for his confirmation.

## Risks / Trade-offs

- [Publishers and editors-in-chief also stay logged in for half a year] → accepted; lock/reset
  ends sessions; logout ends them per device.
- [A lost or shared device keeps reader access for months] → a publisher can lock the account or
  reset its password (slip); access ends within 5 minutes.
- [Keycloak without persistent user sessions (before version 25, or feature disabled) drops all
  sessions on restart] → readers log in again after a Keycloak restart; check the Keycloak version
  of the shared instance during apply and note it in `INSTALL.md` if relevant.
- [Large cookie / many long-lived sessions in Keycloak's store] → session count is bounded by the
  number of reader devices of a school newspaper; negligible.
