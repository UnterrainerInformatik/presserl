# Open proposals

Drafted-but-not-yet-proposed work. Delete an entry as soon as it becomes an `/opsx:propose`
change — never tick it off.

Milestones from `docs/vision.md`; details in `docs/`.

## M3 — Approval chain
The chain itself (submit/approve/reject/withdraw, skip rules, reviews) is `approval-chain-core`.
Still open:
- **Trust switches** per person and level (a holder of an approving level trusts a person below;
  applies to the whole level) — one more skip predicate in `ApprovalChain.next`, storage, endpoints,
  admin UI.
- **Review queue** — "waiting for me" view in the admin app (on top of `pending=true`), counts.
- **Emergency-brake lock** — an article taken offline by a publisher is locked; only publishers
  release it (new action, `allowedActions` rule).

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
