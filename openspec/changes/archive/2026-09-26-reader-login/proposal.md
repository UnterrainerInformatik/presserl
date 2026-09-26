## Why

A newspaper can be switched to `visibility=private`, but the reader has no login yet, so
`reader-articles` hides every article of a private newspaper from everyone — including the family
the newspaper is written for. Reader login is the last open item of M1 (solo newspaper): with it,
a private newspaper becomes readable by the people who hold the `READER` role (or higher).

## What Changes

- New confidential Keycloak client `presserl-reader` (authorization code flow, groups claim in the
  ID token) in the realm template `deploy/keycloak/presserl-realm.json`, the dev realm and the
  realm drift test.
- New OIDC tenant `reader` in the backend (Quarkus OIDC `web-app`, code flow) for the reader
  paths only; the bearer-token protection of `/api` stays unchanged.
- Reader login at `GET /login` (optional `next` pointing back to a reader page) and logout at
  `GET /logout`; the session lives in an encrypted, `HttpOnly`, `SameSite=Lax` session cookie on
  the reader origin (`Secure` in production).
- Private newspaper, anonymous visitor: the front page keeps the private note and adds a login
  link; an article page redirects to the login (for every id, so existence is not revealed).
- Private newspaper, logged in with `READER`, `EDITOR_IN_CHIEF` or `PUBLISHER`: front page and
  article pages behave exactly like in a public newspaper.
- Private newspaper, logged in without such a role: the front page shows a "no access" note, and
  article pages answer `404`.
- Logged-in visitors see their display name and a logout link in the masthead area.
- Reader pages of a private newspaper and pages rendered for a logged-in visitor are sent with
  `Cache-Control: private, no-store`.
- New mandatory deployment variable `PRESSERL_OIDC_READER_SECRET`; `INSTALL.md` explains where to
  copy it from. **BREAKING** for an existing installation: it must add the reader client to its
  realm and set the secret before updating (no installation is live yet; the fork is updated in
  this change).

## Non-goals

- Section roles (`SECTION_EDITOR`, `REPORTER`) as reader access — they live in the database and
  arrive with M2; until then only the newspaper-wide roles from the token count.
- Longer reader sessions ("stay logged in", offline tokens) — session length follows the realm's
  SSO session settings.
- Login for a public newspaper as a visible feature (no login link on a public front page); a
  logged-in visitor of a public newspaper only gets the name and logout link.
- Account creation, password reset, account slips (M2).
- Any change to the REST API, the admin app or its login.

## Capabilities

### New Capabilities
- `reader-login`: reader login and logout via the OIDC authorization code flow, session cookie on
  the reader origin, access rule (`READER` or higher) for a private newspaper, caching of
  private/personal pages.

### Modified Capabilities
- `reader-articles`: the requirement "Private newspaper shows no articles without login" changes —
  anonymous visitors get a login link / login redirect, entitled readers see the articles, other
  logged-in users get a no-access note and `404`.
- `deployment`: the realm template gains the confidential reader client, and `.env.example` gains
  the reader client secret as a mandatory value; the installation guide covers copying it.

## Impact

- **Backend / reader**: `application.properties` (second OIDC tenant, path permissions),
  `OidcConfig` (reader client id and secret), `ReaderResource` and its templates/messages (login
  link, no-access note, masthead user line), new login/logout handling, cache headers, tests
  (`ReaderPrivateTest`, new code-flow tests with HtmlUnit as a new test dependency).
- **Deploy**: `deploy/keycloak/presserl-realm.json`, `deploy/.env.example`, `deploy/INSTALL.md`;
  fork `../presserl-deployment` (realm copy, env).
- **Dev/test**: `backend/src/main/resources/dev/presserl-realm.json`, `RealmTemplateDriftTest`.
- **Docs**: `docs/architecture.md` (reader login paragraph), `ai/open-proposals.md` (entry removed).
- **Admin**: none. **REST contract / `ai/primer/endpoints.md`**: none.
