## 1. Backend

- [x] 1.1 `NewspaperAction.CONFIGURE_NEWSPAPER` (appended last), granted to `PUBLISHER` and `EDITOR_IN_CHIEF` where `allowedActions` is computed
- [x] 1.2 `EffectiveSettings` records which settings come from a valid newspaper override; `NewspaperDto` gains `overrides` (only valid overrides of `settings` keys)
- [x] 1.3 Writable-settings table in the newspaper package (`reader.text-size` → `TextSize` validation); request validation of the `PUT` body (`Map<String, JsonNode>`, explicit `null` kept, unknown/non-writable keys and invalid values → `400` naming the key, every violation reported, all-or-nothing)
- [x] 1.4 `NewspaperSettings.update(...)` in one transaction (put value / remove key for `null`) returning the new effective settings; `PUT /api/newspaper/settings` in `NewspaperResource` with `403` (empty body) unless `CONFIGURE_NEWSPAPER`
- [x] 1.5 Config `presserl.theme.dir` (default `/deployments/theme`) and `presserl.reader.cookie-secure` (default `true`, `false` in `%dev,test`)
- [x] 1.6 `ThemeFiles` + Vert.x route `GET /theme/*`: extension allow-list with content types, real-path containment (symlinks included), regular files only, `Cache-Control: no-cache`, `Last-Modified`/`304`, `404` otherwise; missing directory tolerated with one INFO log; `customCssPresent()`
- [x] 1.7 Tests: `EffectiveSettings` overrides unit test; `@QuarkusTest` for `GET /api/newspaper` `overrides`, `PUT /api/newspaper/settings` (set, clear, invalid value, non-writable key, reader `403`, anonymous `401`), `GET /api/me` publisher and editor-in-chief `allowedActions`; `ThemeFiles` tests with a temp theme dir (css served, font content type, `..`/encoded escape, symlink escape, disallowed type, directory, missing dir)

## 2. Reader

- [x] 2.1 Download fonts (WOFF2, latin + latin-ext) from the `@fontsource` packages: Atkinson Hyperlegible 400/700, Playfair Display 700, Andika 400/700, plus `OFL-<family>.txt`, into `META-INF/resources/reader/fonts/`
- [x] 2.2 Rewrite `reader.css` per design D1: `@font-face` (swap, unicode-range), public tokens incl. `--presserl-section-<key>`, `[data-text-size]` mapping, dark scheme via `prefers-color-scheme`, base typography per readability rules (no italics, no hyphenation, `65ch`), masthead, section bar, section tag, text-size switch, 12/6/1 grid, article, notes; check contrast ≥ 4.5:1 for ink/muted on paper in both schemes and ≥ 3:1 for section markers
- [x] 2.3 `ReaderArticle` gains `sectionName`/`sectionColor`, loaded with the article in `ReaderArticles` (no N+1); ordered section list for the section bar, loaded only when content is shown
- [x] 2.4 `TextSizeResource` `POST /text-size` (form `size`, `next`): validate size, `LoginTarget.of(next)`, `303`, cookie `presserl_text_size` (1 year, `/`, `HttpOnly`, `SameSite=Lax`, `Secure` per config); not in the reader tenant paths
- [x] 2.5 `ReaderResource`: effective text size (cookie if valid, else newspaper `reader.text-size`), `customCss`, sections and current path passed to every template (front page, article, not found)
- [x] 2.6 Templates: layout (`<html data-text-size>`, link `/theme/custom.css` after `reader.css` when present), masthead with text-size switch tag (`aria-pressed`, localized label), `sectionBar` and `sectionTag` tags, story/article markup renamed to the documented classes (`lead-article`, `article-card`, `kicker`, `headline`, `subheadline`, `lead`, `byline`, `article__body`), section tag on stories and above the article headline, section bar hidden on private/not-entitled pages
- [x] 2.7 Reader messages de/en for the text-size switch label and size names
- [x] 2.8 Tests (`@QuarkusTest`, update existing reader tests to the new classes): `data-text-size` from newspaper default, from cookie, tampered cookie; `POST /text-size` (redirect, cookie attributes, open-redirect refused, unknown size sets no cookie, works anonymously on a private newspaper); switch marks current size; `custom.css` linked after `reader.css` only when present; section bar order/colours and hidden when private/anonymous or no sections; section tag on story and article; documented classes and `data-view` present; font file served as `font/woff2`; reader pages and `reader.css` contain no absolute foreign URL; stylesheet defines every public token

## 3. Admin

- [x] 3.1 `Dtos.kt`: `NewspaperDto.overrides` defaulting to empty; `ApiClient.updateNewspaperSettings(map)` sending explicit `null`s
- [x] 3.2 `Navigation.kt`: `NewspaperAction.CONFIGURE_NEWSPAPER`, `NavEntry.NEWSPAPER` after `ACCOUNTS`; wire the entry in `App.kt`
- [x] 3.3 `NewspaperSettingsModel` (load, select → save, effective value from the response, error keeps previous choice) and `NewspaperScreen` with the radio group "installation default / S 17 px / M 19 px / L 22 px / XL 26 px"
- [x] 3.4 German (`values`) and English (`values-en`) strings for the entry, screen, choices and errors
- [x] 3.5 Kotlin tests: `navEntries` with `CONFIGURE_NEWSPAPER` (publisher sees four entries), DTO decoding with and without `overrides`, request body with `null`, model flow (set L, reset to default, `403` keeps previous choice)

## 4. Deploy

- [x] 4.1 `deploy/theme/`: comment-only `custom.css` starter (tokens, `data-view` values, documented classes), `README.md`, `fonts/.gitkeep`, `examples/classic.css`, `examples/colourful.css` (Andika), `examples/night.css`
- [x] 4.2 `deploy/compose.yaml`: mount `./theme:/deployments/theme:ro` on `presserl`; `deploy/INSTALL.md` "Theme" section (edit `theme/custom.css`, start from an example, fonts/images in `theme/`, applies on reload); mention `PRESSERL_READER_TEXT_SIZE` as commented example in `.env.example`
- [x] 4.3 `../presserl-deployment`: `deploy/theme/custom.css` starter and the mount in `deploy/compose.yaml`; commit there separately

## 5. Contract / Docs

- [x] 5.1 `ai/primer/endpoints.md`: `overrides` in `GET /api/newspaper`, new `PUT /api/newspaper/settings` section (auth, body, `null` semantics, `200`/`400`/`401`/`403`), `CONFIGURE_NEWSPAPER` in the `allowedActions` table and publisher example; reader routes `POST /text-size` and `GET /theme/*` noted
- [x] 5.2 `http/newspaper.http` (set, clear, invalid, non-writable, refused), `http/me.http` (publisher actions), `http/reader.http` (`POST /text-size`, `/theme/custom.css`, escape attempt)
- [x] 5.3 `docs/architecture.md`: `presserl.theme.dir` replaces `presserl.theme.css`, reader routes, `PUT /api/newspaper/settings`, `CONFIGURE_NEWSPAPER`; `docs/design-guidelines.md`: text-size switch without JS (cookie), documented class list, fonts shipped; `docs/roles-and-workflow.md` if it lists who changes newspaper settings
- [x] 5.4 `ai/open-proposals.md`: remove the M4 entry

## 6. Verification

- [x] 6.1 Run the backend tests for the newspaper, auth and reader packages and the admin tests (`reference_build_and_test.md`)
- [x] 6.2 Run the new `.http` requests against a live dev backend (incl. a temp theme dir with `custom.css`)
- [x] 6.3 Drive the reader headless (Playwright): front page and article at 1280/768/375 px in light and dark scheme with screenshots, text-size switch S→XL without JS, section bar, a `custom.css` override and the Night example; check contrast and no horizontal scroll
- [x] 6.4 Drive the admin app headless: publisher sets the default text size to L, reader front page renders `data-text-size="l"`, reset to installation default; stop every server started
