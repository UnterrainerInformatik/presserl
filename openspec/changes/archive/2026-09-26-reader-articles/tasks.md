## 1. Backend — reader queries and body model

- [x] 1.1 Add `ReaderArticle` record and `ReaderArticles` service in `reader/` with `frontPage(limit)` and `article(id)`, joining only the live revision of `PUBLISHED` articles, ordered by `published_at desc, id desc` (D1)
- [x] 1.2 Add `BodyRenderer` turning a v1 body `JsonNode` into typed blocks (paragraph, subhead, quote, bullet list) with runs split into line segments; skip and WARN-log unknown types (D2)
- [x] 1.3 Unit tests for `BodyRenderer`: every block type, bold runs, line feeds, empty body, unknown type skipped

## 2. Reader — pages, localization, styles

- [x] 2.1 Add `ReaderMessages` message bundle with German default and English localization (empty note, private note, byline, published/updated wording, 404 texts, back-to-front-page link) (D5)
- [x] 2.2 Resolve the locale from `Accept-Language` (English only when preferred, else German), set it on every template instance, set `<html lang>` and add `Vary: Accept-Language`
- [x] 2.3 Extend `ReaderResource.frontpage` and `frontpage.html`: lead story + cards with kicker, headline, lead, byline, date and link; empty note; private note without articles (D3)
- [x] 2.4 Add `GET /articles/{id}` with `article.html` (`data-view="article"`, masthead link to `/`, single `<h1>` headline, subheadline, lead, byline, published/updated dates, body via a `runs` tag template, no `raw`) (D2, D6)
- [x] 2.5 Add `notFound.html` and return it with status `404` for non-numeric ids, unknown/unpublished articles and private mode — one code path (D4)
- [x] 2.6 Extend `reader.css` with card grid, lead story and the single-column article (≤ 65ch, line height 1.5, left-aligned) (D7)

## 3. Verification — tests

- [x] 3.1 `@QuarkusTest` front page: order by first publication, draft/offline hidden, unpublished changes not leaked, empty note, 30-article limit
- [x] 3.2 `@QuarkusTest` article page: live revision content, byline (display name, username fallback), published and updated dates, unpublished changes hidden, title contains headline and newspaper name
- [x] 3.3 `@QuarkusTest` 404: draft, offline, unknown id, `abc` — identical 404 HTML with link to `/`
- [x] 3.4 `@QuarkusTest` escaping: `<b>Hi</b>` headline and `<script>` run appear escaped; no article-originated `<script>`; all block types map to `<p>`/`<strong>`/`<br>`/`<h2>`/`<blockquote>`/`<ul><li>`
- [x] 3.5 `@QuarkusTest` with a test profile `PRESSERL_NEWSPAPER_VISIBILITY=private`: front page shows private note and no headline; article page `404`
- [x] 3.6 `@QuarkusTest` localization: `de-AT` → `lang="de"` German note, `en-GB` → `lang="en"` English note, no header → German
- [x] 3.7 Keep existing reader tests green (masthead, CSP, no foreign URLs) and assert CSP/no-script also on the article and 404 pages

## 4. Contract/Docs

- [x] 4.1 Add `http/reader.http` with requests for `/`, an article page, a 404 and an English `Accept-Language`; run it against a live dev backend
- [x] 4.2 `ai/open-proposals.md`: `reader-articles` entry removed and `reader-login` entry added (done at propose time)
- [x] 4.3 Confirm `ai/primer/endpoints.md` needs no change (reader HTML routes are not REST contract); update `docs/architecture.md` only if a route or view differs from what is described there

## 5. Final check

- [x] 5.1 Run backend and admin test suites locally (per `reference_build_and_test.md`) and fix failures
- [x] 5.2 Manually check the front page and an article page in a browser in dev mode (German and English)
