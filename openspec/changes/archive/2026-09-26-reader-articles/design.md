## Context

The reader is a Qute stub (`ReaderResource.frontpage`, `templates/ReaderResource/frontpage.html`,
`reader/reader.css`) behind the reader CSP (`default-src 'self'`, set by `SecurityHeaders`).
Articles live in `article` (status, `live_revision`, `published_at` = first publication, author
snapshots) and `article_revision` (text fields, `body` jsonb, `published_at` of that revision).
`ArticleService` is reactive (Hibernate Reactive Panache, `@WithSession`) and serves the
authenticated API with the *latest* revision. `NewspaperSettings.effective()` yields the
effective `visibility`. The admin app picks German/English from the browser language.

## Goals / Non-Goals

**Goals:**
- Reader queries that can only ever return live revisions of `PUBLISHED` articles.
- Body rendering whose output HTML is fixed by code, with Qute's escaping doing all text escaping.
- Private mode that fails closed.

**Non-Goals:**
- Login, sessions, draft preview (`reader-login` and later).
- Reusing `ArticleView`/`ArticleService` for the reader — they model the working revision and
  permissions, which the reader must never see.

## Decisions

### D1 — Separate read-only `ReaderArticles` query service in `reader/`
A new `@ApplicationScoped` service with two `@WithSession` methods:
- `frontPage(int limit)` → `select a, r from ArticleEntity a, ArticleRevisionEntity r where
  r.articleId = a.id and r.number = a.liveRevision and a.status = PUBLISHED order by
  a.publishedAt desc, a.id desc` with `setMaxResults(30)`.
- `article(long id)` → same join filtered by id; empty when not found/not published.

Both return a reader record (`ReaderArticle`: id, kicker, headline, subheadline, lead, body,
byline name, first-published instant, live-revision-published instant). The status and
live-revision filter sits in the query, so no code path can accidentally hand a draft or the
working revision to a template.
*Alternative*: extend `ArticleService` — rejected, it is the author/permission side and returns
the latest revision by design.

### D2 — Body rendering in Java to a small view model, markup in Qute
`BodyRenderer` turns the stored `JsonNode` into a list of typed blocks
(`Paragraph(runs)`, `Subhead(text)`, `Quote(runs)`, `BulletList(items)`), each run split on `\n`
into segments. A Qute tag template (`tags/runs.html`) renders runs as
`{#if bold}<strong>…</strong>{/if}` with `<br>` between segments. All text passes through Qute's
default HTML escaping; the template never uses `raw`. Unknown block types (impossible after
validation, but defensive) are skipped and logged at WARN.
*Alternative*: build the HTML string in Java and emit it with `raw` — rejected: one escaping
mistake becomes XSS; keeping markup in templates keeps the escaping guarantee uniform.
Heading levels: on the article page the headline is the single `<h1>` and subheads are `<h2>`;
the masthead name there becomes a `<p class="masthead__name">` link to `/`. The front page keeps
the masthead name as `<h1>`.

### D3 — Private mode check in the resource, before any query
`ReaderResource` loads `settings.effective()` first; for `PRIVATE` the front page renders with an
empty list and the private flag, and the article route returns the 404 page without querying
articles. `reader-login` will replace this check with an authentication requirement.

### D4 — 404 as a rendered template with status 404
`GET /articles/{id}` takes the id as `String`; a non-numeric id, private mode or an empty query
result all produce `Response.status(404).entity(Templates.notFound(...))`. One code path, identical
output, so existence is not revealed.

### D5 — Localization with a Qute message bundle
`@MessageBundle ReaderMessages` (default locale `de`) plus `ReaderMessages_en.properties` /
localized interface. The resource resolves the locale from `HttpHeaders.getAcceptableLanguages()`:
first entry whose language is `en` → English, otherwise German (mirrors the admin rule:
English only when English is preferred). The locale is set on the template instance
(`setLocale`), used for `<html lang>`, message lookup and date formatting
(`DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG)` in `ZoneId.systemDefault()`). The resource
adds `Vary: Accept-Language` to the response.
*Alternative*: newspaper-level locale setting — deferred; the admin app has none either.

### D6 — "Updated" date
Shown when the live revision's `published_at`, as a local date, is after the article's first
`published_at` date. Same-day corrections show no update note.

### D7 — CSS stays minimal
Add card grid (lead spanning full width, others `repeat(auto-fill, minmax(18rem, 1fr))`),
article column `max-width: 65ch`, `line-height: 1.5`, left-aligned. No tokens, no fonts (M4).
No inline styles, no scripts (CSP).

## Risks / Trade-offs

- [Container time zone defaults to UTC, so dates near midnight can shift by a day] → Accepted
  for M1 (the date is informational); setting `TZ` in the deployment is left for M7.
- [Front page capped at 30 with no archive] → Acceptable for M1; archive/sections come later.
- [Private mode hides everything even for newsroom members] → Intended until `reader-login`;
  authors still see content in the admin app.
- [Qute escaping relies on template file extension `.html`] → All reader templates are `.html`;
  a test asserts escaped output for headline and body.

## Migration Plan

No database migration, no config change. Deploy as usual; rollback = previous image.
