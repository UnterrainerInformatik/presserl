## Context

Reader queries today filter on `status = PUBLISHED` in several places: the `ReaderArticles` front
page and article lookup, the print article, the media usage check in `ReaderMediaResource`, and the
section count. Issue membership is a nullable `issue_id` (plus position) on the article, and
`issue.published` is a boolean. The first publication already appends an article to the issue with
the highest number (`issues` spec, "Newest issue collects newly published articles"). Issue pages
already require a published issue.

`ReaderArticles` loads the front page as "published, newest first publication, limit 30".
Section tags (`tags/sectionTag.html`) are plain spans, and the section bar (`tags/sectionBar.html`)
lists all sections in position order. Article state changes that are not content (`take-offline`,
`unlock`) are `POST` actions on `ArticleResource`, and `allowedActions` lists them. Newspaper-wide
roles come from the token. The admin app knows them from `/api/me`. The latest migration is
`V16__media_details.sql`.

The reader is rendered by `ReaderResource` with Qute templates (`layout/reader.html`, one template
per view). `ThemeFiles` owns the theme directory (`presserl.theme.dir`, mounted read-only from the
deployment's `theme/`) and already answers whether `custom.css` exists. Only a fixed set of static
file types is served from `/theme/*`, and `.txt` is not among them. Private newspapers redirect
anonymous visitors to `/login` inside the page handlers. The backend has no Markdown library.

## Goals / Non-Goals

**Goals:** one definition of "visible to readers", used everywhere, and state the admin app can
show; a weight the editor-in-chief controls and a linkable one-section filter, without changing the
approval chain; an optional, deployment-owned legal notice with the least possible machinery.
**Non-Goals:** see proposal.

## Decisions

### Visibility follows the issue

#### D1 — One predicate, one place
A single reusable condition, *reader-visible* = `a.status = PUBLISHED AND a.issue IS NOT NULL AND
a.issue.published = true`, lives in one place (e.g. a static JPQL fragment or a Panache query helper
next to `ReaderArticles`). Every reader query uses it, including the section-filtered and weighted
front page (D7). A grep for `ArticleStatus.PUBLISHED` in the `reader` package and in the media usage
and section count queries after the change must find only the helper. This keeps the surfaces from
drifting apart again.

#### D2 — `readerVisible` computed, not stored
The flag is derived from the article's status and its issue's `published` in the DTO mapping.
Lists already join the issue for `issue {id, number}`, so it costs no extra query. Storing it would
need updates on every issue switch.

#### D3 — No data migration
Existing data already fits: articles published since issues exist are in the newest issue, and
older ones were moved into issue 1. Nothing is moved automatically. The visible effect on existing
installations is intended: articles of a not-live issue disappear until that issue goes live.

#### D4 — Admin marker
Status chips get a secondary line or suffix when `status == PUBLISHED && !readerVisible`, using
`issue.number` or the "no issue" text. "View in reader" checks `readerVisible` instead of `status`.

### Section filter and front-page weight

#### D5 — Weight is article metadata, not content
Column `front_page_weight INTEGER NULL` on `article` (`V17__front_page_weight.sql`, with check
`front_page_weight BETWEEN 1 AND 999`). It is not on the revision, so it needs no approval, and a
chief can move a story without reopening the author's work. The optimistic-concurrency `version`
used for content saves must not change. Otherwise a chief setting the weight would break the
author's open editor with a `409`. So the update is a targeted `UPDATE article SET
front_page_weight = ? WHERE id = ?`, not an entity merge that bumps `@Version`. If `ArticleEntity`
uses JPA `@Version`, the column is mapped `updatable = false` and written with a JPQL update.

#### D6 — Endpoint `PUT /api/articles/{id}/front-page-weight`
```
PUT /api/articles/42/front-page-weight
{"weight": 1}          → 200 ArticleDto { …, "frontPageWeight": 1, … }
{"weight": null}       → 200 ArticleDto { …, "frontPageWeight": null, … }
{"weight": 0}          → 400 {"fieldErrors":[{"field":"weight", …}]}
section editor         → 403
unknown id             → 404
```
`PUT` because it's idempotent state, not an action. It is deliberately **not** in
`allowedActions`, since that would change every existing `allowedActions` scenario for chiefs and
publishers. The admin app shows the field by role, which is a newspaper-wide rule without
per-article conditions. `ArticleSummaryDto` gets `frontPageWeight` for the list marker.

#### D7 — Front-page query
One query: `WHERE <reader-visible (D1)> [AND section_id = :section] ORDER BY front_page_weight ASC
NULLS LAST, first_published_at DESC, id DESC LIMIT 30`. That orders weighted articles first and
keeps the old order for the rest, and a weighted old article is included even when 30 newer ones
exist.

#### D8 — Section filter in `ReaderResource`
`GET /` gets `@QueryParam("section") String section`. It is parsed like other reader ids: a malformed
value or no such section gives the `404` page. The resolved section goes into `ReaderPage` as
`activeSectionId`. `sectionTag` renders `<a href="/?section={id}">`, or `<a href="/" aria-current="page">`
when `section.id == page.activeSectionId`. The same tag is used in the bar, so the bar gets the toggle
for free. The empty note gets a section-specific message ("In „{name}" gibt es noch keine
Artikel."). Access handling (private newspaper, cache headers) is exactly that of `GET /`, because it
is the same handler.

#### D9 — Admin weight field
The editor shows the field below the section chooser when `me.roles` contains `EDITOR_IN_CHIEF` or
`PUBLISHER`. It is a number field, saved on focus loss or Enter through `ApiClient.setFrontPageWeight`,
and the returned DTO updates the editor state without touching the undo stack. The lists show a
small chip when `frontPageWeight != null`.

### Legal notice

#### D10 — Plain text in the theme directory
The notice lives in `theme/legal-notice.txt`, next to `custom.css`, so it travels with the
deployment repository like the rest of the theme. Plain text needs no new dependency and can't
inject markup. The legal minimum is three short lines.
*Alternatives:* Markdown (needs a library plus sanitising, which the minimum text doesn't need),
`.env` values (awkward for multi-line text), and a newspaper setting in the admin app (the legal
notice belongs to the operator, not the editors, and the text must exist before anybody logs in).

#### D11 — `ThemeFiles.legalNotice()` returns paragraphs
`Optional<List<List<String>>>`: paragraphs, each a list of lines. The file is read on each call, as
the `custom.css` presence check is, and is at most a few hundred bytes. Files over 64 KiB are
ignored and logged as a warning. The template escapes (Qute's default) and joins lines with `<br>`.
`ReaderPage` gets a `boolean legalNotice` that the footer uses.

#### D12 — Route and access
`GET /legal-notice` in `ReaderResource`, without the private-newspaper redirect, and with the same
viewer handling for the header (logged-in name, `no-store` for logged-in visitors). The path is
English like `/issues`; the visible wording is localized.

#### D13 — Footer
A `<footer class="presserl-footer">` in the layout, rendered when `page.legalNotice`. Print
templates don't use it. The class joins the stable class list in `custom.css` comments and the
theme README.

#### D14 — alexpresse.net text (confirmed by Gerald, 2026-09-30)
```
Offenlegung gemäß § 25 Mediengesetz

Medieninhaber: Gerald Unterrainer, Enns, Österreich

Grundlegende Richtung: Alex-Presse ist eine private, nicht kommerzielle Zeitung, in der Kinder
über ihren Alltag, ihre Interessen und ihre Umgebung schreiben.
```
No street and no e-mail, because § 25 MedienG asks for the place of residence only. No trade name:
a business name would ask for its purpose ("Unternehmensgegenstand") too, so Gerald appears as a
private person.

## Risks / Trade-offs

- **Deploying this hides content** on installations with a not-live newest issue that holds
  published articles (alexpresse.net today). That is the goal, but the operator has to know: the
  archive step reminds Gerald to switch alexpresse.net's issue live if he wants the articles back.
- **Fresh installs** start with issue 1 not live. Nothing appears until the editor-in-chief switches
  it live, which is a new first step. `deploy/INSTALL.md` and the admin issue screen text mention it.
- Articles left without an issue are invisible and nothing picks them up. The admin marker
  "in keiner Ausgabe" makes that visible.
- Weights never expire. A forgotten weight-1 story stays the lead for ever. The list marker makes
  that visible, and an automatic expiry can come later if it bothers anyone.
- Several articles can share a weight. Ties fall back to newest first, which is predictable and
  needs no renumbering UI.
- The weight is not in `allowedActions`, so the "server tells what is allowed" rule has one
  exception. It is documented in the primer.
- The legal notice is not legal advice. The text is the operator's responsibility; presserl only
  displays it.
