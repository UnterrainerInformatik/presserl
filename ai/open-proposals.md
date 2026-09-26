# Open proposals

Drafted-but-not-yet-proposed work. Delete an entry as soon as it becomes an `/opsx:propose`
change — never tick it off.

Milestones from `docs/vision.md`; details in `docs/`.

## M0 — Skeleton
Scaffold `backend/` (Quarkus REST reactive, Hibernate Reactive Panache, reactive-pg-client,
oidc, Qute, Flyway + JDBC PostgreSQL, SmallRye OpenAPI; Dev Services for PostgreSQL and
Keycloak) with a Qute reader stub at `/`; `admin/` (Compose Multiplatform, Gradle, Wasm web
target) as a stub served at `/admin/` from the backend image; choose the KMP OIDC library
(auth code + PKCE). `deploy/` with `INSTALL.md`, `compose.yaml` (`presserl`, `keycloak`,
`postgres`), realm `presserl` (groups `publisher`, `editor-in-chief`, `reader`; public admin
client with PKCE; confidential reader client for the code flow; no e-mail, no
self-registration, brute-force detection on), `.env.example` with mandatory values only.
Publisher bootstrap from `PRESSERL_PUBLISHER_USERNAME`/`PRESSERL_PUBLISHER_PASSWORD`.
First endpoint `GET /api/newspaper` returning effective defaults. End-to-end login in the
admin app. CI builds images. Create `../presserl-deployment` from the templates. Record
build/test commands in `ai/memory/reference_build_and_test.md`.

## M1 — Solo newspaper
Articles with revisions, editor at level `standard` (structured JSON body, server-side
allowlist validation; evaluate a Compose rich-text editor, fallback block-based),
publish/offline for an author who holds all roles (no approval yet), reader front page with
masthead and article page. See `docs/roles-and-workflow.md` stage 1.

## M2 — Accounts & sections
Keycloak service account (`manage-users` only) and Admin API client; account creation with
username from first name and a four-word pass-phrase (curated German word list), printable
account slip, password reset, lock; groups for newspaper-wide roles; sections (created by
editors-in-chief), section roles in the DB; delegation rule (assign at or below own level,
within own scope); `GET /api/me`.

## M3 — Approval chain
Chain section editor → editor-in-chief → publisher with skip rules (own role, trust, section
without section editor), trust switches per person and level, `SUBMITTED` with pending
level, review queue, review notes, emergency-brake lock (only publishers release),
`allowedActions` in responses.

## M4 — Look
Reader theme built on `--presserl-*` design tokens per `docs/design-guidelines.md`,
self-hosted fonts, `data-view` hooks, `custom.css` served from `deploy/theme/`, reader
text-size switch, dark mode.

## M5 — Images
Media upload with MIME sniffing, size limit, re-encoding, EXIF/GPS stripping, renditions
(thumbnail/web/print); lead images and captions.

## M6 — Print
Issues; reader print views for a single article and a whole issue (`@page` A4, columns,
page numbers).

## M7 — First fork
`../presserl-deployment` goes live for a real family newspaper: name, `.env`, theme, upstream
images; walk through `deploy/INSTALL.md` end to end and fix what is missing.

## M8 — Mobile & QR
Android/iOS targets of the admin app; QR code on the account slip (server URL + one-time
token) exchanged by the backend for a Keycloak login; store publishing (Google Play Families
policy, Apple developer account).
