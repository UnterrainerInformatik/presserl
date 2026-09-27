# Open proposals

Drafted-but-not-yet-proposed work. Delete an entry as soon as it becomes an `/opsx:propose`
change — never tick it off.

Milestones from `docs/vision.md`; details in `docs/`.

## M5 — Images
Upload, sniffing, re-encoding and EXIF/GPS stripping are done (`media-upload`). Still open:
- `media-renditions` — renditions (thumbnail / web / print) derived from the stored master.
- `article-lead-image` — lead image and caption per article, upload UI in the editor, delivery to
  readers.

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
