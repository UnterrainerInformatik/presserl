## Why

Articles can be written and published in the admin app since `articles-core` and
`article-editor`, but nobody can read them: the reader front page is still the M0 stub that shows
only the masthead. The reader front page and article page are the last missing piece of M1
("Solo newspaper").

## What Changes

- The reader front page (`GET /`) lists the published articles below the masthead: the newest
  one as lead story, the others as cards (kicker, headline, lead, byline, date), each linking to
  its article page. An empty newspaper shows a friendly "no articles yet" note.
- New reader article page `GET /articles/{id}` rendering the article's **live revision**:
  kicker, headline, subheadline, lead, byline with publication date and the body in format v1
  (paragraphs with bold runs and line breaks, subheads, quotes, bullet lists). Only `PUBLISHED`
  articles are shown; drafts, offline articles and unknown ids get an HTML `404` page. Work in
  progress on a published article (unpublished changes) never leaks.
- Body rendering is a strict mapping of the allowlisted block types onto fixed HTML; all text is
  escaped, nothing from the body is ever interpreted as markup.
- **Private newspapers without login**: until `reader-login` exists, a newspaper whose effective
  `visibility` is `private` shows only the masthead and a "this newspaper is private" note on
  the front page and answers every article page with `404`. No article content is reachable
  anonymously.
- Reader chrome texts (empty note, private note, byline wording, dates, 404 page) are localized
  German/English following the browser's preferred language, German by default — the same rule
  as the admin app. `<html lang>` follows the chosen language.
- Minimal reader CSS for cards and the single-column article (≤ 65ch, line height 1.5); the real
  theme with tokens and fonts stays in M4.
- `ai/open-proposals.md`: the `reader-articles` entry is removed and a new `reader-login` entry
  takes over the login part (code flow, confidential reader client, session cookie, enforcing
  `private`).

## Non-goals

- Reader login and showing private newspapers to logged-in readers (`reader-login`).
- Preview of drafts or unpublished changes in the reader (needs login; later).
- Sections, archive, pagination beyond the front-page limit, issues, print views, images.
- Theme tokens, self-hosted fonts, text-size switch, dark mode, `custom.css` (M4).
- Any REST API change; the admin app is not touched.

## Capabilities

### New Capabilities
- `reader-articles`: front-page article list, article page rendering the live revision, body
  format v1 to HTML, not-found page, and hiding content of a private newspaper.

### Modified Capabilities
- `reader-shell`: adds localized reader chrome (German/English by preferred language, `lang`
  attribute).

## Impact

- **Platforms**: backend/reader only (Qute templates, `ReaderResource`, reader CSS, message
  bundle). Admin app, deploy and REST contract are unaffected; `ai/primer/endpoints.md` stays
  unchanged (reader HTML routes are not part of the REST contract).
- **Code**: `backend/.../reader/` (resource, read-only query service, body renderer),
  `templates/ReaderResource/`, `META-INF/resources/reader/reader.css`, message bundle.
- **Tests**: `@QuarkusTest` for front page, article page, 404, private mode, escaping and
  localization; unit tests for the body renderer.
- **Docs/backlog**: `ai/open-proposals.md` (replace entry with `reader-login`); `http/` gets
  reader requests.
- No DB migration: the existing `article.published_at`, `live_revision` and `status` columns
  suffice.
