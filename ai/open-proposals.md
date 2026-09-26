# Open proposals

Drafted-but-not-yet-proposed work. Delete an entry as soon as it becomes an `/opsx:propose`
change — never tick it off.

Milestones from `docs/vision.md`; details in `docs/`.

## M0 — Skeleton
Scaffold `backend/` (Quarkus REST reactive, Hibernate Reactive Panache, reactive-pg-client,
oidc, Flyway + JDBC PostgreSQL, SmallRye OpenAPI; Dev Services for PostgreSQL and Keycloak),
`frontend/` (Vue 3 + TS, Vite, Pinia, keycloak-js) and `deploy/` (compose, Keycloak realm
`presserl` with clients `presserl-web` public/PKCE and `presserl-server` bearer-only, realm
roles `presserl-user`/`presserl-operator`, `.env.example` with mandatory values only).
First endpoint `GET /api/newspaper` returning effective defaults. End-to-end login. CI builds
images. Record build/test commands in `ai/memory/reference_build_and_test.md`.

## M1 — Solo newspaper
Article CRUD with revisions, Tiptap editor at level `standard` (JSON body, server-side
allowlist validation), publish/offline without review for the publisher, front page with
masthead and published articles. See `docs/roles-and-workflow.md` stage 1.

## M2 — Look
Default theme built on `--presserl-*` design tokens per `docs/design-guidelines.md`,
self-hosted fonts, `data-view` hooks, custom CSS loaded from `deploy/theme/`, reader
text-size switch, dark mode.

## M3 — Images
Media upload with MIME sniffing, size limit, re-encoding, EXIF/GPS stripping, renditions
(thumbnail/web/print); lead images and captions.

## M4 — Newsroom
Roles (`PUBLISHER`, `SECTION_EDITOR`, `REPORTER`, `READER`, operator), sections,
memberships, review rule `auto`/`always`/`never` + `chief-needs-publisher`, review queue,
review notes, `allowedActions` in responses, four-layer settings resolution
(code → env → newspaper → section).

## M5 — Print
Issues; print views for a single article and a whole issue (`@page` A4, columns, page
numbers).

## M6 — First fork
Validate the fork workflow: a fork customises only `deploy/` (name, theme) and runs the
upstream images; document it in the README.
