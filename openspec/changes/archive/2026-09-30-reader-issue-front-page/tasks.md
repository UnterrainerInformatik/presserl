## 1. Visibility follows the issue — backend / reader

- [x] 1.1 Reader-visible predicate in one place (D1)
- [x] 1.2 Use it for the front page list and the article page in `ReaderArticles` / `ReaderResource`
- [x] 1.3 Use it for the article print view
- [x] 1.4 Use it for the media usage check of `/media/{id}/{kind}`
- [x] 1.5 Use it for `articleCounts.live` in `GET /api/sections`
- [x] 1.6 `readerVisible` in `ArticleDto` and article summaries (D2)
- [x] 1.7 Check that no reader-side query filters on `PUBLISHED` alone anymore (grep)

## 2. Front-page weight — backend

- [x] 2.1 Migration `V17__front_page_weight.sql`: nullable `front_page_weight` with check 1–999
- [x] 2.2 `ArticleEntity` mapping without affecting the content `version` (D5); `frontPageWeight` in `ArticleDto` and the summary DTO
- [x] 2.3 `PUT /api/articles/{id}/front-page-weight` in `ArticleResource`/`ArticleService`: role check (EDITOR_IN_CHIEF, PUBLISHER), validation 1–999 or null with field error `weight`, targeted update, `200` with the DTO

## 3. Section filter and weight — reader

- [x] 3.1 `ReaderArticles`: reader-visible predicate, order by weight (nulls last), first publication desc, id desc; optional section filter (D7)
- [x] 3.2 `ReaderResource`: `section` query parameter on `GET /`, `404` for malformed/unknown, `activeSectionId` in `ReaderPage`
- [x] 3.3 `sectionTag`: link to `/?section=<id>`, or to `/` with `aria-current="page"` for the active section; used by stories, article, issue pages and the section bar
- [x] 3.4 Empty-section note de/en in `ReaderMessages`; link and active styles in `reader.css` (contrast, focus ring)

## 4. Legal notice — reader

- [x] 4.1 `ThemeFiles.legalNotice()`: read `legal-notice.txt` (UTF-8, ≤ 64 KiB, blank-only = absent), split into paragraphs and lines
- [x] 4.2 `ReaderPage.legalNotice` flag, set for all reader views (front page, article, issue, issues, 404)
- [x] 4.3 Footer in `layout/reader.html` with the localized link; `ReaderMessages` de/en ("Impressum" / "Legal notice")
- [x] 4.4 `GET /legal-notice` with template `ReaderResource/legalNotice.html`, `data-view="legal-notice"`, no private-newspaper redirect, `404` without a notice
- [x] 4.5 Footer and notice styles in `reader.css`, using the existing design tokens

## 5. Admin

- [x] 5.1 DTO fields `readerVisible` and `frontPageWeight`; `ApiClient.setFrontPageWeight(id, weight)`
- [x] 5.2 Marker "wartet auf Ausgabe N" / "in keiner Ausgabe" (de/en) in article lists, review queue, issue details and editor
- [x] 5.3 "View in reader" only when `readerVisible`
- [x] 5.4 Editor field "Titelseite"/"Front page" for EDITOR_IN_CHIEF/PUBLISHER, saved separately, not in undo/redo, server error at the field; help text de/en
- [x] 5.5 List marker "Titelseite N" / "Front page N"

## 6. Contract / Docs / Deploy

- [x] 6.1 `ai/primer/endpoints.md`: `readerVisible` and `frontPageWeight` in article and summary shapes; the weight endpoint; note that the weight is not in `allowedActions`
- [x] 6.2 `http/articles.http` / `http/reader.http`: an article of a not-live issue answering `404` and appearing after publishing the issue; weight set, clear, 400, 403; run against a live backend
- [x] 6.3 `docs/roles-and-workflow.md`: articles become visible when their issue goes live; editor-in-chief/publisher set the front-page weight
- [x] 6.4 `deploy/INSTALL.md`: switch issue 1 live (blog mode); the legal notice file and that it is the operator's legal duty
- [x] 6.5 `deploy/theme/legal-notice.txt.example` with a commented minimal § 25 MedienG text
- [x] 6.6 `deploy/theme/README.md`: the legal notice file and the new `presserl-footer` class; list it in the `custom.css` comments of stable classes
- [x] 6.7 `../alexpresse/deploy/theme/legal-notice.txt` with the text of design D14

## 7. Tests

- [x] 7.1 Reader `@QuarkusTest`: planned issue hides front page entry, article page, print view and media; publishing the issue shows them; unpublishing hides them again; article without issue hidden; blog mode visible at once
- [x] 7.2 Sections test: `articleCounts.live` counts only reader-visible articles
- [x] 7.3 Articles test: `readerVisible` for live issue, planned issue, no issue, draft
- [x] 7.4 Backend `@QuarkusTest`: weight endpoint (200 set/clear, 400, 403 section editor, 404, no new revision, version unchanged, kept across offline/online)
- [x] 7.5 Reader `@QuarkusTest`: weighted order, ties, weighted article beyond the newest 30, offline and waiting weight ignored, section filter incl. weight and waiting articles, empty section, unknown/malformed id `404`, private newspaper, tag links and `aria-current`
- [x] 7.6 Reader `@QuarkusTest`: legal notice rendered with paragraphs and line breaks; HTML escaped; `404` without the file and for a whitespace-only file; footer link present with a notice and absent without, not in print views; private newspaper, anonymous visitor gets the notice (no redirect)
- [x] 7.7 Adapt existing reader/media/print tests whose fixtures publish articles without a published issue
- [x] 7.8 Admin tests: waiting marker, "View in reader" condition, weight field visible only for chief/publisher, save does not touch undo, list marker
- [x] 7.9 Run the backend and admin tests relevant to the change (see `reference_build_and_test.md`)

## 8. Verification / Deployment

- [x] 8.1 Local run with Playwright: issue 2 not live → new approved article absent from `/`, admin shows "wartet auf Ausgabe 2"; publish issue 2 → article on `/`
- [x] 8.2 Local run with Playwright: set weights in the admin app, check the front page order; click section tags to filter and back
- [x] 8.3 Local run with Playwright: open `/`, click the footer link, check the legal notice page in light and dark mode
- [x] 8.4 After deployment: `https://alexpresse.net/legal-notice` shows the text
- [x] 8.5 Archive step: tell Gerald that alexpresse.net and staging hide the articles of their not-live issue until he switches it live
