## Why

Three gaps keep the reader from working like a newspaper, and all three change the same front page,
templates and reader tests, so they ship together:

- **Visibility follows the issue.** On alexpresse.net (2026-09-30) the approved articles of an issue
  that was not live showed up on the front page. The spec defines it that way: approving publishes
  at once, and the issue's live switch only governs the issue pages. Gerald expects issues to work
  like a newspaper. Articles collect in the newest issue and readers see them when that issue goes
  live. Blog mode then needs nothing new: with one live issue and no newer one, every new article
  joins the live issue and is online at once.
- **Front page and sections.** The front page is a plain "newest first" list, and the section tags
  only decorate. The editor-in-chief can't put a story on top, and a reader can't see "just the
  sport". Both are basic newspaper moves (docs/design-guidelines.md: one lead story, smaller
  stories below).
- **Legal notice.** Austrian media law (§ 25 Mediengesetz) requires every website, including a
  private, non-commercial one, to disclose its media owner: name and place of residence, and for a
  site that goes beyond presenting its owner (a newspaper does) also its basic orientation
  ("Blattlinie"). alexpresse.net is public and has no such disclosure. The content differs per
  deployment, so presserl only provides the place for it. Every deployment writes its own text.

## What Changes

### Visibility follows the issue
- **One visibility rule for readers:** an article is *visible to readers* when its status is
  `PUBLISHED` **and** it belongs to an issue that is published (live). Every reader surface uses it:
  - the front page, unfiltered and filtered by section
  - the article page
  - the article print view
  - the reader media route
  - the section counts (`articleCounts.live`)
- An article that is `PUBLISHED` but in a not-live issue, or in no issue, is not shown and its
  pages answer `404`, as for a draft.
- **Unchanged:** the existing rule that the first publication appends an article to the issue with
  the highest number, and the issue management. Creating issue N+1 opens a new issue. Switching it
  live releases everything in it. Unpublishing an issue hides its articles again, without changing
  their status.
- `ArticleDto` and article summaries get `readerVisible` (boolean), computed by the server.
- The admin app shows a published article that readers can't see as "wartet auf Ausgabe N" /
  "waits for issue N", or "in keiner Ausgabe" / "in no issue". "View in reader" is offered only for
  a visible article.

### Section filter and front-page weight
- **Section filter:** section tags in the reader (section bar, stories, article and issue pages)
  become links to `/?section=<id>`. The front page then lists only that section's reader-visible
  articles, in the same order and layout. The active section's tag links back to `/`, which
  clears the filter. At most one section is active. Filtered views can be linked.
- **Front-page weight:** an article can carry an integer weight (1 or more; smaller means higher
  up). On the front page, weighted reader-visible articles come first, lowest weight first, ahead
  of all others. The first of them is the lead story and the next ones follow as cards, so weight
  1–4 fills the lead plus the three stories below it. Unweighted articles follow newest first, as
  today. The limit of 30 stays.
- Editors-in-chief and publishers set or clear the weight through a new endpoint
  `PUT /api/articles/{id}/front-page-weight`. Setting the weight isn't a content change: no new
  revision, no approval, and it works in any status. The weight stays when an article goes
  offline and has an effect only while the article is visible to readers.
- The admin app shows and edits the weight in the editor (for editors-in-chief and publishers) and
  marks weighted articles in the article lists.

### Legal notice
- A deployment can put a plain-text file `legal-notice.txt` into its theme directory.
- When the file exists, every reader page shows a footer link "Impressum" / "Legal notice" to a new
  page `GET /legal-notice` that renders the text: blank lines separate paragraphs, line breaks
  are kept, and everything is HTML-escaped.
- Without the file the reader shows neither footer link nor page (`/legal-notice` answers `404`),
  as it does today.
- `/legal-notice` is reachable without login, also in a private newspaper.
- `deploy/theme/` ships a commented example and the theme README / `INSTALL.md` explain it.
- alexpresse.net gets its text with only the legal minimum. It names Gerald Unterrainer
  as a private person and Enns, with no street and no e-mail, plus a one-sentence Blattlinie.
  Staging (`presserl-deployment`, LAN/VPN only) gets none.

## Non-goals

- No preview of not-live issues for the newsroom in the reader.
- No new automatic issue assignment. Articles that end up without an issue (issue deleted, removed
  from its issue) stay invisible until an editor-in-chief puts them into an issue.
- No scheduled go-live of issues.
- No per-issue weighting. Issue pages keep their issue order.
- No drag-and-drop layout editor and no pinning to a fixed slot beyond ordering.
- No combination of several section filters and no separate section pages (`/sections/...`).
- No markup, links or images in the legal notice, and no editing of it in the admin app.
- No per-language variants of the legal notice. The deployment writes the text in its newspaper's
  language.
- No legal notice in print views.
- No advice beyond § 25 MedienG. § 5 ECG (commercial providers) does not apply to a private site.

## Capabilities

### New Capabilities

- `reader-legal-notice`: optional legal notice page from the deployment's theme, linked from every
  reader page.

### Modified Capabilities

- `reader-articles`: front page and article page list or serve only reader-visible articles;
  front-page order with weights; the section filter; section tags become links.
- `reader-print`: the article print view follows the visibility rule.
- `reader-media`: renditions are served only for media of reader-visible articles.
- `sections`: `articleCounts.live` counts reader-visible articles.
- `articles`: representations carry `readerVisible` and `frontPageWeight`; new weight endpoint.
- `admin-articles`: "waits for issue" marker; "View in reader" only for visible articles; weight
  input in the editor and a marker in the lists.

## Impact

- **backend:** Flyway `V17__front_page_weight.sql` (nullable `front_page_weight` on the article),
  `ArticleEntity`, DTOs (`readerVisible`, `frontPageWeight`), `ArticleResource`/`ArticleService`
  (weight endpoint and permission check), `SectionService` (count). Tests.
- **reader:** `ReaderArticles` (visibility predicate, order, filter), `ReaderResource` (article,
  print, `section` query parameter, `/legal-notice`), `ReaderMediaResource` (media usage query),
  `ThemeFiles` (read `legal-notice.txt`); templates `layout/reader.html` (footer), `sectionTag`,
  `sectionBar`, `frontpage`, new `legalNotice`; `reader.css`; `ReaderMessages` de/en. Tests.
- **admin:** API client and DTOs (`readerVisible`, `frontPageWeight`, new call), status marker,
  "View in reader" condition, editor weight field, list marker, strings de/en, tests.
- **contract:** `ai/primer/endpoints.md`, `http/articles.http`, `http/reader.http`.
- **docs:** `docs/roles-and-workflow.md` (going live releases the articles; chief/publisher set the
  front-page weight).
- **deploy:** `deploy/theme/legal-notice.txt.example`, `deploy/theme/README.md`, `deploy/INSTALL.md`.
- **deployment repos:** `../alexpresse/deploy/theme/legal-notice.txt`. After the update,
  alexpresse.net and staging show no article until their issue is live. On alexpresse.net issue 1
  is not live today, so Gerald has to switch it live, or leave it off on purpose.
