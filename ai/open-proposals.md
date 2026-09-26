# Open proposals

Drafted-but-not-yet-proposed work. Delete an entry as soon as it becomes an `/opsx:propose`
change — never tick it off.

Milestones from `docs/vision.md`; details in `docs/`.

## M1 — Solo newspaper
Article model, body format v1, REST contract and `allowedActions` are done (`articles-core`).
Remaining, each its own change:
- `article-editor` — admin app editor at level `standard` on top of `/api/articles` and body
  format v1 (evaluate a Compose rich-text editor, fallback block-based; typed body model and
  mapping in the admin app), "My articles" list, autosave with `version`, publish/offline
  buttons from `allowedActions`.
- `reader-articles` — reader front page with masthead and article page rendering the live
  revision. Enforce `visibility=private` in the reader (M0 only stores the setting): reader
  login via the authorization code flow with a new confidential reader client in the realm
  template (and dev realm, drift test), session cookie on the reader origin.
See `docs/roles-and-workflow.md` stage 1.

## M2 — Accounts & sections
Extend the Keycloak Admin client from M0 (service account already holds `manage-users`,
`view-users`, `query-users`, `query-groups`); account creation with
username from first name and a four-word pass-phrase (curated German word list), printable
account slip, password reset, lock; groups for newspaper-wide roles; sections (created by
editors-in-chief), section roles in the DB; delegation rule (assign at or below own level,
within own scope); extend `GET /api/me` additively (section roles, scopes, allowed actions).

## M3 — Approval chain
Chain section editor → editor-in-chief → publisher with skip rules (own role, trust, section
without section editor), trust switches per person and level, `SUBMITTED` with pending
level, review queue, review notes, emergency-brake lock (only publishers release).
`allowedActions` already exists on every article response (`ArticlePolicy`, `articles-core`);
M3 only extends its rules (approve/reject, submit for editors-in-chief and reporters, lock).

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
