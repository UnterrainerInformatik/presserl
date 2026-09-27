# Open proposals

Drafted-but-not-yet-proposed work. Delete an entry as soon as it becomes an `/opsx:propose`
change — never tick it off.

Milestones from `docs/vision.md`; details in `docs/`.

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
