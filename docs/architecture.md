# Architecture

![Architecture](diagrams/architecture.svg)

## Stack

| Part | Choice |
|---|---|
| Backend | Quarkus (current LTS), **Quarkus REST** (reactive), **Hibernate Reactive with Panache**, reactive PostgreSQL client, `quarkus-oidc` (bearer tokens for `/api`, code flow + session cookie for the reader), Flyway (runs at startup via an additional JDBC datasource — Flyway is not reactive), SmallRye OpenAPI, Keycloak Admin API client |
| Reader | Server-rendered HTML from **Qute** templates inside the backend — front page, sections, articles, print views; design tokens and fork theme |
| Administration app | **Compose Multiplatform** (Kotlin, Gradle), **Wasm web target** first, Android/iOS later from the same code base; OIDC auth code + PKCE via a small in-house browser flow behind an `AuthClient` interface (M0 spike: the KMP OIDC library's web flow is popup-only) |
| Database | **PostgreSQL** |
| Media store | **RustFS** (S3-compatible, Apache-2.0) in the reference deployment, reached only by the backend through the plain S3 API (Quarkiverse `quarkus-amazon-s3`, URL-connection client, path-style); any S3-compatible store works by configuration. Images are decoded and re-encoded with ImageIO + TwelveMonkeys (JPEG, WebP), EXIF orientation read with `metadata-extractor` |
| Identity | **Keycloak provided by the operator** (usually shared): a dedicated realm imported from `deploy/keycloak/presserl-realm.json`; newspaper-wide roles as groups; public client `presserl-admin` (PKCE, audience `presserl-backend`, `groups` claim); confidential client `presserl-backend` whose **service account** manages users and groups (`manage-users`, `view-users`, `query-users`, `query-groups`) |
| Development | **Quarkus Dev Services** start PostgreSQL and Keycloak automatically, RustFS via Compose Dev Services (`backend/compose-devservices.yml`) — `quarkus dev` needs no configuration |
| CI/CD | UnterrainerInformatik workflows (`docker-build-workflow`, `deploy-workflow`, `bump-semver-workflow`); the admin Wasm bundle is built with Gradle and packaged into the backend image. CI builds and ships images only (bump → image build → staging dispatch on push to `master`); it runs no tests — the backend (`./mvnw verify`) and admin (`./gradlew check`) suites run locally before every push |
| Operations | docker compose: `presserl` (API + reader + static admin bundle), `postgres` and `rustfs` (media, no published port) only. The TLS-terminating reverse proxy (Traefik via labels, or Caddy) and Keycloak are the operator's; `deploy/INSTALL.md` shows how to attach them |

Java toolchain: JDK 21 (`maven.compiler.release=21`, Temurin 21 in the image; see `ai/memory/reference_machine_jdk.md` — Lombok constraints).

Why the split: the reader needs a real DOM for print (`@page`), CSS theming, accessibility, search engines and link sharing — plain HTML, no second frontend build. The administration app is one Compose code base for web, Android and iOS (store release and QR scanning later). Rejected: Vue + Capacitor (two paradigms once native features grow) and Compose for the reader (canvas rendering breaks print, theming and accessibility).

## Repository layout

```
presserl/                     # monorepo (upstream)
├── backend/                  # Quarkus (Maven): API + Qute reader
├── admin/                    # Compose Multiplatform (Gradle): administration app
├── deploy/                   # reference deployment and templates
│   ├── INSTALL.md            # step-by-step installation guide
│   ├── compose.yaml
│   ├── .env.example          # mandatory values only: hostname, DB password, OIDC issuer + secrets, first publisher, media store keys
│   ├── keycloak/presserl-realm.json   # realm template for the operator's Keycloak (hostname placeholder)
│   └── theme/                # template; overrides the default reader theme
│       ├── custom.css        # starter: lists tokens, data-view values and stable classes
│       ├── README.md
│       ├── examples/
│       └── fonts/
├── http/                     # .http files for the REST API
├── docs/
├── openspec/
└── ai/

../presserl-deployment/       # staging: presserl.unterrainer.info (LAN/VPN only)
../alexpresse/                # first public fork: alexpresse.net (fork of presserl-deployment)
```

A fork copies the templates from `deploy/`, sets name, `.env` and theme, and runs the upstream container images. Both deployment repositories are maintained alongside this repository; changes to them are recorded in this repository's OpenSpec changes.

- **Staging** — `../presserl-deployment` (GitHub `UnterrainerInformatik/presserl-deployment`) serves
  `presserl.unterrainer.info`, reachable from LAN/VPN only. Upstream's pipeline dispatches to it after
  every image build, so staging always runs the newest image.
- **First fork** — `../alexpresse` (GitHub `guFalcon/alexpresse`, remote `upstream` =
  `presserl-deployment`) serves the family newspaper *Alex-Presse* at `alexpresse.net`. Upstream does
  not dispatch to it. It is updated by merging `upstream` (`git pull upstream master`) and pushing,
  which redeploys the latest release tag, or by a manual workflow run. A release therefore runs on
  staging first and reaches the public newspaper only when it is merged into the fork.

## Configuration

Four layers; the later one wins:

1. **Code default** — `application.properties` (Quarkus / SmallRye Config)
2. **Deployment** — environment variable in `deploy/.env` (`PRESSERL_NEWSPAPER_VISIBILITY=private` overrides `presserl.newspaper.visibility`)
3. **Newspaper** — setting changed in the administration app by a publisher (database)
4. **Section** — setting per section (database; kept for future per-section overrides)

The database layers store only overrides, never copies of defaults. The backend exposes the *effective* value.

| Key | Default | Alternatives | Layers |
|---|---|---|---|
| `presserl.newspaper.name` | `My Newspaper` (i18n) | any | deployment, newspaper |
| `presserl.newspaper.subtitle` | empty | any | deployment, newspaper |
| `presserl.newspaper.visibility` | `public` | `private` | deployment, newspaper |
| `presserl.retract.author-can-retract` | `true` | `false` | deployment, newspaper |
| `presserl.section.default` | `General` (i18n) | any | deployment |
| `presserl.editor.level` | `standard` | `starter`, `profi` | deployment, newspaper, per user |
| `presserl.reader.text-size` | `m` | `s`, `l`, `xl` | deployment, newspaper, per reader |
| `presserl.media.max-size` | `10M` | up to `60M` (startup fails above) | deployment |
| `presserl.media.max-concurrent-processing` | `2` | any ≥ 1 | deployment |
| `presserl.media.s3.endpoint` | `http://rustfs:9000` | any S3-compatible endpoint | deployment |
| `presserl.media.s3.region` | `us-east-1` | any | deployment |
| `presserl.media.s3.bucket` | `presserl-media` | any (created on start if missing) | deployment |
| `presserl.theme.dir` | `/deployments/theme` | any directory | deployment |
| `presserl.reader.cookie-secure` | `true` (`false` in dev/test) | `false` | deployment |

Approval is not configured by keys but by roles and trust (see [roles-and-workflow.md](roles-and-workflow.md#approval-chain)).

Newspaper overrides are written with `PUT /api/newspaper/settings` by users whose `allowedActions`
contain `CONFIGURE_NEWSPAPER` (publisher, editor-in-chief); writable so far: `reader.text-size`. The
per-reader layer of `reader.text-size` is the reader's own choice in the `presserl_text_size` cookie
(see the reader routes). `presserl.theme.dir` is the directory served read-only at `/theme/`; when it
contains `custom.css`, every reader page links it after the default theme.

Deployment-only values (no defaults, set in `.env`):

| Variable | Purpose |
|---|---|
| `PRESSERL_PUBLISHER_USERNAME` / `PRESSERL_PUBLISHER_PASSWORD` | **Bootstrap:** on first start, if no publisher exists, the backend creates this account in the `publisher` group; it holds all roles |
| `PRESSERL_HOSTNAME`, `PRESSERL_DB_PASSWORD` | public DNS name (proxy rule, realm redirect URIs); database password |
| `PRESSERL_OIDC_ISSUER`, `PRESSERL_OIDC_BACKEND_SECRET`, `PRESSERL_OIDC_READER_SECRET`, `PRESSERL_OIDC_ADMIN_CLIENT_ID` | the realm in the operator's Keycloak; secrets of `presserl-backend` and `presserl-reader` (`PRESSERL_OIDC_READER_CLIENT_ID` optional, default `presserl-reader`) |
| `PRESSERL_MEDIA_S3_ACCESS_KEY` / `PRESSERL_MEDIA_S3_SECRET_KEY` | credentials of the media store; the compose file starts `rustfs` with them |

`presserl.media.max-size` is reported as the newspaper setting `media.max-size`. The HTTP body limit
is 64M for `POST /api/media` and 10M for every other path.

## Data model (MVP)

One newspaper per server; a second newspaper is a second deployment.

- **Newspaper** — singleton: name, subtitle, visibility, settings (JSON, overrides only)
- **Section** — name (unique ignoring case), slug (derived once from the name, stable for reader URLs), colour (palette key `red` | `orange` | `yellow` | `green` | `teal` | `blue` | `purple` | `pink`, mapped to `--presserl-section-<key>` by the theme), position, settings (JSON, overrides only)
- **SectionRole** — user (Keycloak id = token `sub`) × section × role (`SECTION_EDITOR` | `REPORTER`), at most one role per user and section. Newspaper-wide roles (`PUBLISHER`, `EDITOR_IN_CHIEF`, `READER`) are Keycloak groups, not rows.
- **Trust** — approving level × trusted user × section (section only for the section-editor level, the other levels are newspaper-wide), plus who set it and when; at most one row per user, level and section; deleted with its section. One row skips that level for that user's articles (in that section).
- **Article** — section (exactly one, `NOT NULL` in the database; the section belongs to the article, not to a revision, so moving an article creates no revision; a section with articles cannot be deleted), author (token `sub` plus username/display-name snapshot for the byline), status (`DRAFT` | `SUBMITTED` | `PUBLISHED` | `OFFLINE`; `SUBMITTED` = never published and waiting for approval), pending approval level (`SECTION_EDITOR` | `EDITOR_IN_CHIEF` | `PUBLISHER`, `NULL` while no submission is pending; published and offline articles keep their status while it is set), live revision (by number, `NULL` until the first publication), first publication time, emergency-brake lock (only `OFFLINE` articles; set when a publisher takes the article offline, cleared when it goes online or a publisher unlocks it), optimistic-lock version. The lead image is revision content (see ArticleRevision), not an attribute of the article
- **ArticleRevision** — numbered per article (`1, 2, …`), holds the content: kicker, headline, subheadline, lead (plain text), an optional lead image (media reference plus caption, plain text ≤ 300 characters; a caption needs an image, a referenced media cannot be deleted) and body (**body format v1**: structured JSON of blocks — paragraph, subhead, quote, bullet list — with inline runs whose only mark is bold; validated server-side against an allowlist, never raw HTML). The latest revision is the **working revision**: saves overwrite it until it is published; after that the next save with changed content starts a new revision. Publishing makes the latest revision the article's live revision, which stays unchanged until the next publication, so a new or changed lead image reaches readers only with it. Taking offline keeps the live revision reference.
- **ArticleReview** — one approval or rejection: decision, the level the article waited for, the reviewed revision, reviewer (token `sub` plus username/display-name snapshot), note (rejections only, feedback to the author), time; deleted with the article
- **Issue** — number (assigned as highest + 1, unique, never changed), optional publication date (a calendar date, display only), published switch plus time of the latest switch; groups articles in an order (the first is the lead story). Membership lives on the article (`issue_id` + `issue_position`, both set or both `NULL`), so an article belongs to at most one issue. The first publication of an article without issue appends it to the issue with the highest number, live or not (blog mode: one live issue that grows; planned issues: the highest one is not live yet and collects). Only unpublished issues can be deleted; their articles then belong to no issue. The migration creates issue 1 (not live) with every article published before. Issues only hide themselves while not live — article visibility stays governed by the article status
- **Media** — one uploaded image after re-encoding: object key in the media store (`media/<uuid>.<jpg|png>`, random, written once), content type (`image/jpeg` | `image/png`), width, height, stored size, uploader (token `sub` plus username/display-name snapshot), upload time. The bytes live only in the object store; the rows are written after the objects, so no row points to a missing object.
- **MediaRendition** — media × kind (`thumbnail` ≤ 480 px, `web` ≤ 1600 px, `print` ≤ 3000 px on the longer side, never enlarged): own object key, content type (that of the media), width, height, size. Produced during the upload from the decoded pixels (media and rendition rows in one transaction); media from before renditions get theirs from a startup backfill in the background. Storage per image grows by roughly half of the stored image

## Routes

### Reader (HTML, Qute)

```
GET    /                                  front page
GET    /login?next={path}&login_hint={u}  reader login (code flow), back to a same-origin path; login_hint
                                          (optional) is forwarded to Keycloak and pre-fills the username
GET    /qr?u={username}                   QR code entry of the account slip: 303 to /login?login_hint={u}# (or
                                          /login# for an invalid/missing u), Cache-Control: no-store
GET    /logout                            reader logout (RP-initiated), back to /
GET    /sections/{slug}                   section page
GET    /articles/{id}                     article page
GET    /issues                            archive of the live issues, highest number first      (implemented)
GET    /issues/{id}                       live issue: its published articles in issue order     (implemented)
GET    /print/article/{id}                print view: a published article on A4                 (implemented)
GET    /print/issue/{id}                  print view: a live issue on A4, columns after page 1  (implemented)
GET    /reader/print.js                   same-origin script of the print button
POST   /text-size                         reader's text-size choice (form: size, next) → cookie, 303 back
GET    /reader/reader.css, /reader/fonts/* default theme and its self-hosted fonts (OFL)
GET    /theme/*                           fork theme directory (presserl.theme.dir, from deploy/theme/): allow-listed
                                          static types, no path escapes, Cache-Control: no-cache
GET    /media/{id}/{kind}                 rendition (thumbnail | web | print) of a lead image of a live revision of a
                                          PUBLISHED article; empty 404 otherwise; Cache-Control public/private max-age=3600
```

**Lead images.** The article page shows the live revision's lead image after the headline block as
`<figure class="lead-image">` (`web` with `thumbnail` in `srcset`, `width`/`height` against layout
shift, `alt=""` because the caption in `<figcaption class="lead-image__caption">` is the image's
text); the front page's lead story uses `web`, the cards `thumbnail` (lazy). `/media/{id}/{kind}`
answers only for media that are the lead image of at least one live revision of a `PUBLISHED`
article, with the same visibility decision as the pages (private newspaper: entitled readers only,
otherwise `404`, never a login redirect since only `<img>` loads it). The stored image is never
served to readers. The route caches for an hour, not immutably: the bytes never change, but whether
they may be served does (taking offline, emergency brake, private switch).

**Theme and text size.** The default theme `reader.css` is built on public design tokens
(`--presserl-*`), follows `prefers-color-scheme` for dark mode and uses only self-hosted fonts, so
the CSP stays `'self'`. `/text-size`, `/theme/*` and `/qr` are outside the reader OIDC tenant: they need no
session and also work for anonymous visitors of a private newspaper. `/theme/*` is a Vert.x route
(`ThemeFiles`) that serves only regular files whose real path lies inside the theme directory.
Every reader page renders `<html data-text-size>` from the reader's cookie, falling back to the
newspaper's effective `reader.text-size`.

**Issues and print views.** The front page masthead names the newest live issue (linked) and links
the archive when more than one issue is live. Print views are pure HTML + CSS on the reader stack:
`@page` A4 portrait with the page number in the footer, black on white regardless of theme and dark
mode, sizes in `pt`, figures and headline blocks kept whole, the issue's articles after the first
page in `--presserl-grid-columns` columns (default 2); the lead image uses the `print` rendition.
The "Print" button is shown and bound by `/reader/print.js`, so no inline script is needed.

**Reader login.** The reader paths (`/`, `/login`, `/logout`, `/articles/*`, `/issues`, `/issues/*`,
`/print/*`, `/media/*`, later sections) belong to the OIDC tenant `reader`: a web-app tenant using the confidential
Keycloak client `presserl-reader` with the authorization code flow and PKCE. The session lives in the
encrypted `q_session_reader` cookie (`HttpOnly`, `SameSite=Lax`, path `/`, `Secure` in production);
there is no server-side session store. Only `/login` requires authentication; it starts the code
flow and afterwards redirects to `next` when that is a same-origin path. The tenant forwards a
`login_hint` query parameter of `/login` to the authorization endpoint (`forward-params`), so Keycloak
pre-fills the username.

**QR code entry.** The account slip's QR code encodes `<reader address>/qr?u=<username>#pw=<pass-phrase>`:
the pass-phrase sits in the fragment, which no browser sends to a server (proxy, backend and Keycloak
never see it); the mobile app will read address, username and pass-phrase from the same code. `/qr`
checks `u` against the username rules of account creation and redirects to
`/login?login_hint=<u>#`; the empty fragment replaces the scanned one, which browsers would otherwise
carry across the redirects into the Keycloak address bar. `/qr` neither reads nor creates a session;
a visitor who already has a reader session lands on the front page as with `/login`.

`/logout` ends the reader
session and the Keycloak session. Quarkus would otherwise pick the tenant from the session cookie on
any path, so `ReaderTenantScope` pins every non-reader path to the default tenant: `/api` accepts
bearer tokens only, never the reader cookie.

Access is decided per request in `ReaderResource` against the effective visibility. A public
newspaper is open to everyone. In a private newspaper, anonymous visitors see the masthead, a note
and a login link, and article, issue and print pages redirect to the login (returning to the
requested path) for every id. Visitors with `READER`,
`EDITOR_IN_CHIEF` or `PUBLISHER` read normally. Other logged-in accounts get a no-access note and
`404`. Pages of a private newspaper and pages for a logged-in visitor are sent with
`Cache-Control: private, no-store`.

### Administration app

```
GET    /admin/                            static Compose Wasm bundle (same origin)
```

### REST API sketch

Draft only — the contract becomes binding in `ai/primer/endpoints.md` once an OpenSpec change implements it.

```
GET    /api/newspaper                     name, subtitle, effective settings, overrides        (implemented)
PUT    /api/newspaper/settings            set/clear newspaper overrides; CONFIGURE_NEWSPAPER   (implemented)
GET    /api/sections                      every user; canManage and assignable section roles     (implemented)
POST   /api/sections                      editor-in-chief+                                   (implemented)
PUT    /api/sections/{id}                 name and colour, editor-in-chief+                  (implemented)
PUT    /api/sections/order                editor-in-chief+                                   (implemented)
DELETE /api/sections/{id}                 editor-in-chief+; only without articles, roles go with it (implemented)
GET    /api/sections/{id}/members         who may assign section roles there                 (implemented)
PUT    /api/sections/{id}/members/{acc}   assign/replace a section role, within my scope     (implemented)
DELETE /api/sections/{id}/members/{acc}   remove a section role, within my scope             (implemented)
GET    /api/articles?status=…&mine=true&pending=true&awaitingMe=true   visible articles: all for editor-in-chief+, own and own sections' for section editors, own for reporters (implemented)
GET    /api/articles/{id}                 visible articles only, else 404                    (implemented)
POST   /api/articles                      reporter+ in a section they may write in           (implemented)
PUT    /api/articles/{id}                 author with write access to the section; overwrites the working revision, or starts a new one after a publication; optional move to another section (implemented)
DELETE /api/articles/{id}                 author with write access to the section, only if never published (implemented)
POST   /api/articles/{id}/publish         author whose approval chain is empty → PUBLISHED   (implemented)
POST   /api/articles/{id}/submit          author whose chain is not empty → waits for the lowest level (implemented)
POST   /api/articles/{id}/approve|reject  holder of the pending level or a higher one, not the author; reject with a note (implemented)
POST   /api/articles/{id}/withdraw        author ends the pending submission                  (implemented)
GET    /api/articles/{id}/reviews         approvals and rejections, newest first             (implemented)
POST   /api/articles/{id}/offline         take offline: author, section editor, editor-in-chief, publisher (publisher ⇒ locked)
POST   /api/articles/{id}/unlock          publisher: lift the emergency-brake lock, the article stays offline
GET    /api/articles/{id}/revisions       revision history                                   (implemented)
GET    /api/articles/{id}/revisions/{n}   one revision                                       (implemented)
GET    /api/accounts                      section editor+; all accounts with their section roles (implemented)
POST   /api/accounts                      create account (roles and section roles ≤ mine, within my scope) → username + pass-phrase for the slip (implemented)
POST   /api/accounts/{id}/password-reset  anyone above the person, never a publisher, never oneself → new pass-phrase for the slip (implemented)
POST   /api/accounts/{id}/lock            publisher; never a publisher, never oneself (implemented)
POST   /api/accounts/{id}/unlock          publisher; never a publisher, never oneself (implemented)
PUT    /api/accounts/{id}/roles           anyone above the person, never oneself; changed roles at or below mine, within my scope (implemented)
PUT    /api/accounts/{id}/trust           set/clear trust at my own highest level on someone below; clear any entry of my level (implemented)
POST   /api/media                         WRITE_ARTICLES; multipart part `file`; sniffed, re-encoded, metadata stripped (implemented)
GET    /api/media/{id}                    WRITE_ARTICLES; metadata (implemented)
GET    /api/media/{id}/content            WRITE_ARTICLES; stored image bytes (implemented)
GET    /api/media/{id}/renditions/{kind}  WRITE_ARTICLES; rendition bytes (thumbnail | web | print) (implemented)
GET    /api/issues                        MANAGE_ISSUES; highest number first                (implemented)
POST   /api/issues                        MANAGE_ISSUES; next number, optional date          (implemented)
GET    /api/issues/{id}                   MANAGE_ISSUES; with its articles in order          (implemented)
PUT    /api/issues/{id}                   MANAGE_ISSUES; set or clear the publication date   (implemented)
POST   /api/issues/{id}/publish|unpublish MANAGE_ISSUES; switch live, no approval            (implemented)
PUT    /api/issues/{id}/articles          MANAGE_ISSUES; complete ordered article list       (implemented)
DELETE /api/issues/{id}                   MANAGE_ISSUES; only while not live                 (implemented)
GET    /api/me                            my roles, section roles and allowed actions (implemented)
```

Article and account responses and `GET /api/me` carry `allowedActions`; clients render buttons from it and never re-implement the approval chain or the account rules. The approval chain follows roles, trust and the emergency-brake lock; see `ai/primer/endpoints.md` for the binding contract of the implemented endpoints.

## Views

### Reader (Qute)

1. **Front page** — masthead, lead story, modular article cards
2. **Article page** — reading mode with byline
3. **Issues** — issue archive and issue page (implemented); section pages later
4. **Print views** — article and whole issue (implemented)

Every reader view sets `data-view="…"` on `<main>` (`frontpage`, `article`, `issues`, `issue`, `print-article`, `print-issue`, `not-found`; later `section`) as a stable hook for custom CSS.

### Administration app (Compose)

1. **My articles** — cards by status, big "New article" button
2. **Editor** — age levels; preview opens the article in the reader
3. **Review queue** — "Waiting for me (n)" tab of the article lists (`GET /api/articles?awaitingMe=true`), only visible when there is something to approve (implemented)
4. **Accounts** — create (with printable slip), reset password, lock, assign roles, trust
5. **Sections** — create, order, colours, section roles
6. **Issues** — create, date, switch live, order articles, delete while not live; links to the issue page and its print view (`MANAGE_ISSUES`, implemented)
7. **Newspaper** — default reader text size (publisher, editor-in-chief; `CONFIGURE_NEWSPAPER`, implemented); later name, subtitle, visibility

## Security checklist (MVP)

- Keycloak: no e-mail, self-registration and "forgot password" off, **brute-force detection on**, PKCE, short-lived access tokens
- Default passwords are four-word pass-phrases from a curated word list; users may change them
- Service account client limited to `manage-users`, `view-users`, `query-users`, `query-groups` in its realm (Keycloak needs the view/query roles for lookups); its secret never leaves the backend. Tokens must carry the audience `presserl-backend`, since the realm may live on a shared Keycloak
- Every action checked server-side against groups, section roles and trust; ownership checks
- Article bodies only as structured JSON with an allowlist — never raw HTML; strict CSP (`self` only), reader and admin bundle served from the same origin. The admin policy additionally allows `'wasm-unsafe-eval'` in `script-src`, the issuer origin in `connect-src` (Keycloak lives on another origin) and, by hash only, the style Compose injects into its shadow DOM (`csp-style-hashes.txt` in the admin bundle, checked by the admin tests)
- Uploads: type from magic bytes only (JPEG, PNG, still WebP; declared type and name ignored), size limit (`media.max-size`), dimensions checked before decoding (≤ 50 MP, ≤ 20000 px per side), re-encoding from pixels (JPEG or PNG, ≤ 4096 px) so no EXIF/GPS/XMP/IPTC/ICC/comment survives, renditions derived from the same decoded pixels (no second decoder path for untrusted bytes), at most `max-concurrent-processing` images decoded at once; the media store is never exposed to browsers
- Readers only ever get renditions of lead images of published live revisions (`/media/{id}/{kind}`), never the stored image, drafts or working revisions; every other media answers an indistinguishable empty `404`. An image of an article taken offline can stay in caches for up to an hour
- Free choice of author name (nickname); no real-name requirement
- Backups of PostgreSQL (`presserl-db`) and the media volume (`presserl-media`) via cron; see `deploy/INSTALL.md`, "Backups"
