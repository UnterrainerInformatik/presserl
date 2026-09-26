## 1. Deploy / Realm

- [x] 1.1 Add the confidential client `presserl-reader` to `deploy/keycloak/presserl-realm.json` (standard flow only, PKCE `S256`, `groups` mapper incl. ID token, redirect and post-logout URIs `https://presserl.example.org/*`, no secret)
- [x] 1.2 Add the same client to `backend/src/main/resources/dev/presserl-realm.json` with secret `presserl-reader-dev-secret` and redirect/post-logout URIs for `http://localhost:8080/*` and `http://localhost:8081/*`; check that dev user `reader` has a display name for the masthead test
- [x] 1.3 Extend `RealmTemplateDriftTest`: expected clients `presserl-admin, presserl-backend, presserl-reader`; reader confidential, no direct/implicit grants, no service account, no `secret` in the template; adjust `KeycloakRealmTest` if it enumerates clients
- [x] 1.4 `deploy/.env.example`: mandatory `PRESSERL_OIDC_READER_SECRET` with comment (Clients > presserl-reader > Credentials); optional commented `PRESSERL_OIDC_READER_CLIENT_ID=presserl-reader`
- [x] 1.5 `deploy/INSTALL.md`: realm description names three clients; copy the reader secret next to the backend secret (incl. the "realm created by the Keycloak admin" path); section on making the newspaper private and adding readers to group `reader` in Keycloak; troubleshooting rows (missing reader secret at startup, `redirect_uri` error on reader login)

## 2. Backend – OIDC tenant

- [x] 2.1 `OidcConfig`: `readerClientId()` (default `presserl-reader`) and `readerSecret()`; `%dev,test` secret in `application.properties`
- [x] 2.2 `application.properties`: tenant `quarkus.oidc.reader.*` (web-app, auth-server-url, client-id, secret, `tenant-paths=/,/login,/logout,/articles/*`, default `keep-all-tokens` state strategy (see design decision 4), `refresh-expired`, PKCE, `cookie-same-site=lax`, `cookie-path=/`, `%prod` `cookie-force-secure`, `session-expired-path=/`, `logout.path=/logout`, `logout.post-logout-path=/`); permission `/login` authenticated; `ReaderTenantScope` pins non-reader paths to the default tenant (design decision 1)
- [x] 2.3 Verify `NewspaperRoleAugmentor` assigns roles for the code-flow (ID token) principal; add a unit test if the principal type differs

## 3. Reader

- [x] 3.1 Resolve the viewer state (`ANONYMOUS` / `ENTITLED` / `NOT_ENTITLED` + display name) from `SecurityIdentity` in `ReaderResource`
- [x] 3.2 `GET /login`: validate `next` (single leading `/`, no `//`, no `/\`, no control characters, else `/`) and answer `303` with `Cache-Control: no-store`; unit test for the validator
- [x] 3.3 `GET /logout` fallback for anonymous visitors → `303 /`
- [x] 3.4 Front page: private + anonymous → private note plus login link; private + not entitled → no-access note; private + entitled → normal list
- [x] 3.5 Article page: private + anonymous → `303 /login?next=/articles/{id}` (id URL-encoded, also for unknown/malformed ids); private + not entitled → `404`; private + entitled → as public
- [x] 3.6 Layout/masthead: viewer line with display name (username fallback) and logout link, only for logged-in visitors
- [x] 3.7 New messages in `ReaderMessages` / `ReaderMessagesEn` (login link, no-access note, logged-in-as, log out)
- [x] 3.8 `Cache-Control: private, no-store` when visibility is private or the viewer is logged in
- [x] 3.9 Update the `ReaderResource` Javadoc (no longer "until reader login exists")

## 4. Tests

- [x] 4.1 Add test dependency `org.htmlunit:htmlunit` (version managed by the Quarkus BOM if available, else pinned)
- [x] 4.2 Adapt `ReaderPrivateTest` (anonymous): front page note + `/login` link without headline; article redirect for published, unknown and malformed ids; `Cache-Control`; `/logout` → `/`; `/login` redirects to Keycloak with the reader client id and `response_type=code`
- [x] 4.3 New HtmlUnit test (private profile): `reader` logs in via `/login?next=/articles/{id}` and lands on the article; `chief` sees the articles; `nogroups` sees the no-access note and `404` on the article; foreign `next` ends on `/`; `/login` with an existing session redirects without Keycloak
- [x] 4.4 HtmlUnit: session cookie has `HttpOnly`, `SameSite=Lax`, path `/`; `/api/me` with only the cookie → `401`; logout removes the session and shows the anonymous front page
- [x] 4.5 Public profile: logged-in visitor sees name + logout link and `Cache-Control: private, no-store`; anonymous page shows neither login nor logout link and no `no-store`

## 5. Contract / Docs

- [x] 5.1 `docs/architecture.md`: reader login paragraph (tenant, `/login`, `/logout`, access rule, cookie)
- [x] 5.2 Confirm `ai/primer/endpoints.md` and the admin API client need no change (no `/api` change); no `.http` file needed for HTML routes
- [x] 5.3 Remove the `reader-login` entry from `ai/open-proposals.md` (done at propose time)

## 6. Deployment fork

- [x] 6.1 `../presserl-deployment`: add `presserl-reader` to `keycloak/presserl-realm.json` (with the fork's hostname) and `PRESSERL_OIDC_READER_SECRET` to the fork's env setup (`deploy/site.env` / `up.sh`, secret value only outside git)

## 7. Verification

- [x] 7.1 Run the backend test suite (`reference_build_and_test.md`) and the admin suite; both green
- [x] 7.2 Manual check in dev mode: set visibility `private`, log in as `reader` via the front page link, read an article, log out
- [x] 7.3 `openspec validate reader-login --strict`
