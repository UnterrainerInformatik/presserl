## Why

Readers (children at the school newspaper) have to log in again every day: the Keycloak SSO
session — and with it every refresh token — ends after 30 minutes idle and 10 hours at most, and
the reader session cookie expires five minutes after the ID token. They end up keeping the
password slip around as a permanent "key", which defeats its purpose.

## What Changes

- Realm template and dev realm: `ssoSessionIdleTimeout` and `ssoSessionMaxLifespan` raised to
  180 days (15552000 s). `accessTokenLifespan` stays at 5 minutes, so locking an account or
  resetting its password still takes effect within at most 5 minutes.
- Reader: the session cookie lives as long as the Keycloak session can be refreshed
  (`quarkus.oidc.reader.authentication.session-age-extension=180d`), so a reader who comes back
  after days or weeks is still logged in; the access token is refreshed silently as today.
- `deploy/INSTALL.md` §2a step 3 documents the new session values for operators who fill an
  existing realm by hand.
- Live realms on staging (`presserl.unterrainer.info`) and `alexpresse.net` are switched to the new
  session values in the Keycloak admin console (the template is only read on import).
- The admin app benefits automatically (its refresh tokens follow the same SSO session); no admin
  code changes.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `reader-login`: the reader session cookie requirement gains a lifetime — it outlives the ID token
  and stays valid for as long as the Keycloak session (up to 180 days).
- `deployment`: the realm template requirement gains the session lifetimes (access token 5 minutes,
  SSO session idle and max 180 days).

## Non-goals

- Offline tokens / `offline_access` or per-client session lifetimes (variant B, discussed and
  rejected for now): publishers and editors-in-chief get the same long session as readers.
- Keeping the web admin app logged in across a page reload (its tokens live in memory only).
- Changing the Android admin app's stored credentials.
- "Remember me" on the Keycloak login form — it stays off.

## Impact

- **backend / reader:** `application.properties` (one property), reader login test.
- **deploy:** `deploy/keycloak/presserl-realm.json`, `deploy/INSTALL.md`.
- **backend dev/test realm:** `backend/src/main/resources/dev/presserl-realm.json`.
- **admin:** none (behaviour follows the realm).
- **docs / primer:** no REST contract change; `ai/primer/endpoints.md` untouched.
- **Operations:** manual realm change on staging and alexpresse.net; the deployment repos
  themselves hold no realm and need no file change. Keycloak sessions must survive a Keycloak
  restart (persistent user sessions, default since Keycloak 25) or readers are logged out on
  every Keycloak restart.
