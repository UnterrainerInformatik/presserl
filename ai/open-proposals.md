# Open proposals

Drafted-but-not-yet-proposed work. Delete an entry as soon as it becomes an `/opsx:propose`
change — never tick it off.

Milestones from `docs/vision.md`; details in `docs/`.

## M2 — Accounts & sections
Account creation for newspaper-wide roles is done (`accounts-create`). Remaining:
sections (created by editors-in-chief), section roles in the DB; delegation rule extended to
section roles (assign at or below own level, within own scope); changing the roles of existing
accounts; password reset (new pass-phrase and slip), lock/unlock; extend `GET /api/me`
additively (section roles, scopes, allowed actions).

### Follow-up to `accounts-create`: username minimum length (next change, agreed 2026-09-26)
Keycloak's user profile requires usernames of at least 3 characters; the `accounts` spec has no
lower bound, so a first name like `Li` is suggested as `li` and the creation fails with a Keycloak
`400`. Add "at least 3 characters" to the `POST /api/accounts` validation and make the suggestion
produce a valid name for short bases (e.g. `li` → `li-1`, or padding — decide in propose). Update
spec, primer and `http/accounts.http`.

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
