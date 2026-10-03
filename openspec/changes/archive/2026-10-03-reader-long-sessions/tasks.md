## 1. Deploy (realm template)

- [x] 1.1 `deploy/keycloak/presserl-realm.json`: set `ssoSessionIdleTimeout` and `ssoSessionMaxLifespan` to `15552000` (180 days); leave `accessTokenLifespan` at `300`
- [x] 1.2 `deploy/INSTALL.md` §2a step 3: *SSO session idle* 180 days, *SSO session max* 180 days (access token lifespan stays 5 minutes); add one sentence why (readers stay logged in, lock/reset still cut access within 5 minutes)
- [x] 1.3 Check the Keycloak version of the shared instance (persistent user sessions, Keycloak ≥ 25); if sessions would not survive a Keycloak restart, add a note to `INSTALL.md`

## 2. Backend / Reader

- [x] 2.1 `backend/src/main/resources/dev/presserl-realm.json`: same two session values as 1.1
- [x] 2.2 `application.properties`: add `quarkus.oidc.reader.authentication.session-age-extension=180d` next to the other reader authentication settings, with a short comment
- [x] 2.3 `ReaderLoginTest`: new test — after a login the session cookie's expiry is at least 179 days in the future (cookie is persistent)

## 3. Admin

- [x] 3.1 No code change; confirm the admin app's existing token refresh tests still pass (behaviour follows the realm)

## 4. Live realms (Gerald, in the Keycloak console)

- [x] 4.1 Hand Gerald the exact console steps for staging (`presserl.unterrainer.info`) and `alexpresse.net`: *Realm settings → Sessions* → *SSO session idle* 180 days, *SSO session max* 180 days → Save; *Realm settings → Tokens* → *Access token lifespan* stays 5 minutes
- [x] 4.2 Wait for Gerald's confirmation that both realms are switched

## 5. Verification

- [x] 5.1 Run `ReaderLoginTest` and `AccountResourceTest` (lock/reset still reject refresh tokens)
- [x] 5.2 After Gerald's realm change: log in on staging's reader headless and check the session cookie's expiry (~180 days) (checked manually by Gerald)
- [x] 5.3 `openspec validate reader-long-sessions --strict`
