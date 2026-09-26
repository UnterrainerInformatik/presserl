# Vision

## Problem

Children want to write, take pictures, report — and be read. The available options are either too open (social media, WordPress with plugins, comment sections, tracking) or too school-like/commercial (school-newspaper SaaS, print designers). None of them is *their own* newspaper that a family hosts and controls itself.

## Solution

Presserl is a self-hosted, multi-user newspaper platform that looks like a real newspaper — front page, sections, issues, bylines, print views. Children write articles; depending on the size of the newsroom they publish directly or after a review.

## Principles

1. **Grows with complexity.** A newspaper with a single publisher has no review step at all: *Publish* and *Take offline* are one click each. Reviews appear only when several people are involved (see [roles-and-workflow.md](roles-and-workflow.md)). The UI shows only the actions that make sense in the current situation.
2. **Sensible defaults, everything overridable.** A fresh installation works with nothing but passwords and a hostname. Every behaviour has a default that can be overridden per deployment, per newspaper and per section (see [architecture.md](architecture.md#configuration)).
3. **Safe by design.**
   - No account without an invitation; login via Keycloak; parents act as *operators* with an emergency brake.
   - No comments from strangers, no tracking, no third-party resources (fonts, CDNs).
   - **No advertising** — not even ad slots in the layout.
   - Uploaded images are re-encoded and stripped of EXIF/GPS data.
   - Newspapers are private by default; public only by explicit choice.
4. **Made for 6–16.** Reading view and editor follow age-appropriate typography and interaction guidelines (see [design-guidelines.md](design-guidelines.md)).
5. **Forkable.** UnterrainerInformatik maintains upstream. A family, class or club forks the repository and customises only `deploy/` — name, logo, CSS theme — without touching code.

## Target audience

- Children aged 6–16 as the newsroom (publisher, section editors, reporters).
- Parents as operators.
- Later: classes and teachers (school context).

## Milestones

| # | Goal | Outcome |
|---|---|---|
| M0 | Skeleton | `backend/`, `frontend/`, `deploy/` scaffolded; Quarkus Dev Services running; compose with realm import; end-to-end login; CI builds images |
| M1 | Solo newspaper | Article CRUD, editor (level `standard`), publish/offline without review, front page |
| M2 | Look | Default theme per design guidelines, custom CSS from `deploy/theme/`, reader text size |
| M3 | Images | Upload, EXIF stripping, lead images, captions |
| M4 | Newsroom | Roles, sections, memberships, review workflow `auto`, settings layers |
| M5 | Print | Issues, print views for article and issue |
| M6 | First fork | A real family newspaper deployed from a fork; first issue appears |

M1 deliberately delivers a usable solo newspaper — a child can start writing from then on while the rest grows around it.
