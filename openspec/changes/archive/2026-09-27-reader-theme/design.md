## Context

See `proposal.md` for the motivation and the specs for the required behaviour.

Current state:

- The reader is Qute-rendered in the backend (`ReaderResource`, `templates/layout/reader.html`,
  tags `masthead`, `story`, `runs`). The only stylesheet is the static
  `META-INF/resources/reader/reader.css` (placeholder, no tokens). Class names are BEM-ish
  (`story__headline`, `article__kicker`); `data-view` already exists on `<main>` of all three
  views.
- `SecurityHeaders` sets the reader CSP on every non-admin response:
  `default-src 'self'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'self'; form-action 'self'`.
  Fonts, styles and form posts to the own origin are already allowed — the CSP does not change.
- `reader.text-size` is already resolved (code default `m`, `PRESSERL_READER_TEXT_SIZE`, newspaper
  JSONB override in `newspaper.settings`) and returned by `GET /api/newspaper`, but nothing writes
  the override and the reader ignores it.
- `GET /api/newspaper` is whitelisted as public by an exact permission path; everything else under
  `/api/*` requires a bearer token. The reader OIDC tenant covers `/`, `/login`, `/logout`,
  `/articles/*`; every other path is pinned to the bearer tenant by `ReaderTenantScope`.
- `ReaderArticle` does not carry the section; `SectionEntity` has `name`, `slug`, `color`
  (`SectionColor`), `position`.

## Goals / Non-Goals

**Goals:**
- One stylesheet architecture that M5 (images) and M6 (print) extend without renaming tokens or
  documented classes.
- No JavaScript on reader pages; CSP unchanged.
- Theme changes in a fork apply on reload, without restart or rebuild.

**Non-Goals:**
- A generic settings-write framework; the endpoint accepts a map but only `reader.text-size` is
  writable. Further keys are added one by one in later changes.
- Minifying/bundling CSS or fingerprinting reader assets.

## Decisions

### D1 — Stylesheet structure

`/reader/reader.css` stays the single default-theme stylesheet (no `@import` chain, one request),
ordered in layers by comment blocks: `@font-face` → tokens (`:root`) → text-size mapping
(`[data-text-size]`) → dark scheme (`@media (prefers-color-scheme: dark) { :root { … } }`) → base
typography → masthead / section bar / text-size switch → front-page grid → article → notes.

Token set (public, see spec) plus a few internal helpers prefixed `--presserl-_` that forks must
not rely on. Section colours: `--presserl-section-red` … `--presserl-section-pink`, chosen to reach
≥ 3:1 against paper as a non-text marker in both schemes. Text-size mapping:

```css
:root, [data-text-size="m"] { --presserl-text-size: 19px; --presserl-letter-spacing: .02em; }
[data-text-size="s"]  { --presserl-text-size: 17px; }
[data-text-size="l"]  { --presserl-text-size: 22px; }
[data-text-size="xl"] { --presserl-text-size: 26px; --presserl-letter-spacing: .04em; }
```

The body `font-size` is `var(--presserl-text-size)`; all other sizes are `em`/`rem` relative to
it, so the switch scales headlines too. Because `data-text-size` is on `<html>`, `rem` follows it.

*Alternatives:* separate `tokens.css` + `layout.css` (more requests, no benefit without a build
step); CSS `@layer` (would let forks' unlayered rules win automatically, but makes specificity
reasoning for `custom.css` authors harder to explain — the plain "loaded later wins" rule is
enough).

### D2 — Fonts

Vendored WOFF2 files from the `@fontsource` npm packages (latin + latin-ext subsets, which cover
German), stored under `META-INF/resources/reader/fonts/` with `OFL-<family>.txt` next to them:
Atkinson Hyperlegible 400/700, Playfair Display 700, Andika 400/700. `@font-face` with
`font-display: swap` and `unicode-range` per subset, so browsers fetch only faces and subsets in
use (Andika is only fetched when a theme switches `--presserl-font-body` to it). Quarkus serves
`.woff2` as `font/woff2`; a test pins that. Download is a one-off task step, not a build
dependency.

*Alternatives:* a Maven/npm build step pulling fonts (adds tooling for five files); variable
fonts (bigger, Atkinson Hyperlegible has no variable version).

### D3 — Theme directory route

New `ThemeFiles` bean + a Vert.x route (`@Observes Router`, like `SecurityHeaders`) on
`GET /theme/*` — not JAX-RS, so it bypasses the auth permissions and needs no OIDC tenant.
Config `presserl.theme.dir` (`Optional<Path>`-like with default `/deployments/theme`, which is the
Quarkus container working directory).

Resolution per request: take `ctx.normalizedPath()` (Vert.x already decodes and removes dot
segments), strip `/theme/`, reject empty paths, backslashes and NUL; check the extension against
the allow-list map (extension → content type); resolve against the theme dir's real path, then
`toRealPath()` the file and require `startsWith(themeRoot)` — this also blocks symlinks pointing
outside. Regular files only. Serve with `ctx.response().sendFile(...)`, `Cache-Control: no-cache`
and `Last-Modified`; Vert.x answers `If-Modified-Since` with `304`. Everything else → `404` with
empty body. The CSP handler already runs on these responses (so an SVG opened directly cannot run
script).

`ThemeFiles.customCssPresent()` (a `Files.isRegularFile` stat per page render; cheap, and it keeps
"add/remove applies on reload") drives a `customCss` boolean for the layout, which links
`/theme/custom.css` after `/reader/reader.css`.

Docs: the config key `presserl.theme.css` in `docs/architecture.md` is replaced by
`presserl.theme.dir` (deployment layer only).

*Alternatives:* `quarkus.http.static-resources` / a second static root (cannot be pointed at an
arbitrary runtime directory with an allow-list); serving via JAX-RS `StreamingOutput` (would fall
under `/api`-style auth wiring and reader tenant decisions for no gain).

### D4 — Text size: cookie, POST, server-rendered attribute

`POST /text-size` (form `size`, `next`) is a JAX-RS method on a new small `TextSizeResource` in the
reader package, **outside** the reader OIDC tenant paths (no session needed, and anonymous access
on private newspapers must work). It validates `size` via `TextSize` / `SettingValueConverter`,
reuses `LoginTarget.of(next)` for the same-origin check, and answers `303 See Other`. Cookie:
`presserl_text_size=<s|m|l|xl>; Path=/; Max-Age=31536000; HttpOnly; SameSite=Lax` plus `Secure`
when `%prod` (config `presserl.reader.cookie-secure`, default `true`, `%dev,test` `false`) —
mirroring the OIDC cookie settings.

Rendering: `ReaderResource` reads the cookie (`@CookieParam`), falls back to
`EffectiveSettings.readerTextSize()` (already loaded per request for name/visibility) and passes
`textSize` to every template. The layout puts it on `<html data-text-size>`; the
`textSizeSwitch` tag renders

```html
<form class="text-size-switch" method="post" action="/text-size">
  <input type="hidden" name="next" value="/articles/7">
  <button name="size" value="s" aria-pressed="false">S</button> … <button name="size" value="xl" …>XL</button>
</form>
```

with an accessible label from the message bundle (de/en). `next` is the request path plus query.

CSRF: a cross-site form could change someone's text size — harmless, and `SameSite=Lax` still
lets the cookie be *set* by the response. Accepted, no token.

*Alternatives:* `GET /text-size?size=…` links (simpler, but a state change on GET that crawlers
and prefetchers may trigger); JS + `localStorage` (rejected by the user: page must work without
JS, and the server must know the size to avoid a flash of wrong size).

### D5 — Writable newspaper settings

REST contract:

```
PUT /api/newspaper/settings
Authorization: Bearer …            (PUBLISHER or EDITOR_IN_CHIEF)
Content-Type: application/json

{ "reader.text-size": "l" }        // or null to clear the override
```

`200`:

```json
{
  "name": "My Newspaper",
  "subtitle": "",
  "visibility": "public",
  "settings": {
    "retract.author-can-retract": true,
    "section.default": "General",
    "editor.level": "standard",
    "reader.text-size": "l",
    "media.max-size": "10M"
  },
  "overrides": { "reader.text-size": "l" }
}
```

`400`: `{"errors": [{"field": "reader.text-size", "message": "must be one of s, m, l, xl"}]}`
(`field` = the offending key; unknown/non-writable keys: `"message": "is not a writable setting"`).
`401` / `403`: empty body, like the other endpoints.

`GET /api/newspaper` gains the same `overrides` object (additive). `overrides` lists only keys of
`settings` whose value currently comes from a *valid* newspaper override (invalid stored values are
already ignored with a warning by `EffectiveSettings.resolve`).

Implementation: body read as `Map<String, JsonNode>` (Jackson keeps explicit `null`s), validated
completely before writing (all-or-nothing); `NewspaperSettings.update(Map)` modifies
`NewspaperEntity.settings` in one `@WithTransaction` — removing the key for `null`, putting the
string value otherwise — and returns the new `EffectiveSettings`. A `WRITABLE` map (key →
validator) in the newspaper package keeps the allowed keys in one place. `EffectiveSettings`
records which values came from overrides so `NewspaperDto.of` can emit `overrides`.

Authorization: a new `NewspaperAction.CONFIGURE_NEWSPAPER` (appended last, so the existing order
of `allowedActions` stays) granted to `PUBLISHER` and `EDITOR_IN_CHIEF`, computed where the other
actions are computed; the endpoint checks the same rule. The path `/api/newspaper/settings` is
not covered by the exact public permission `/api/newspaper`, so it falls under the authenticated
`/api/*` permission automatically.

*Why editor-in-chief too:* the editor-in-chief is "the child who owns the newspaper" and already
manages sections; the text size is an editorial look decision, not technology. The theme itself
(files) stays with the operator/publisher.

*Alternatives:* `PATCH /api/newspaper` with JSON merge patch (would mix name/subtitle/visibility in
before they are writable, and the public `GET` path would need a method-specific permission);
`PUT /api/newspaper/settings/{key}` per key (one call per setting; the map form scales to the next
keys without new routes).

### D6 — Templates and classes

Template classes are renamed to the documented API (spec "Stable styling API"): story tag emits
`article-card` or `lead-article` plus `kicker`, `headline`, `lead`, `byline`; the article page
uses `article`, `kicker`, `headline`, `subheadline`, `lead`, `byline`, `article__body`. New tags
`sectionBar` and `sectionTag` (`<span class="section-tag" data-section-color="blue">Sport</span>`,
colour via `[data-section-color="blue"] { --presserl-_section: var(--presserl-section-blue); }`
and a `::before` marker). BEM element classes that are not in the documented list may remain as
internal helpers.

Data: `ReaderArticles` loads the section (`name`, `color`) with the article — join in the
existing query, no N+1 — and `ReaderArticle` gains `sectionName`, `sectionColor`. The section bar
list comes from one ordered query of all sections, loaded only when content is shown (the same
branch that loads the articles; private/anonymous and not-entitled pages skip it). The 404 page
shows the section bar when the newspaper is public or the viewer is entitled.

Grid: `.stories` becomes `display: grid; grid-template-columns: repeat(var(--presserl-grid-columns), 1fr)`;
`.lead-article { grid-column: 1 / -1 }`, `.article-card { grid-column: span 4 }`; at
`max-width: 60rem` columns → 6 and cards `span 3`; at `max-width: 36rem` columns → 1 and cards
`span 1`. Rules between cards via `border-top`/`column-gap` with a thin `--presserl-color-rule`.

### D7 — Deploy and example themes

`deploy/theme/custom.css` (comment-only starter listing tokens, `data-view` values and classes),
`deploy/theme/README.md`, `deploy/theme/fonts/.gitkeep`, `deploy/theme/examples/classic.css`
(explicit default tokens as a template), `colourful.css` (Andika body, larger default via token,
brighter accent, rounder cards), `night.css` (dark tokens without media query). Compose:
`volumes: ["./theme:/deployments/theme:ro"]`. `INSTALL.md` gets a "Theme" section.

`../presserl-deployment`: `deploy/theme/custom.css` (starter copy) and the same mount in its
`deploy/compose.yaml`; its deploy workflow copies the whole `deploy/` directory, so the theme
travels with it. Committed in that repository separately.

### D8 — Admin app

`NavEntry.NEWSPAPER` after `ACCOUNTS`, shown for `CONFIGURE_NEWSPAPER`. `NewspaperDto` gains
`overrides: Map<String, JsonElement> = emptyMap()` (default keeps old servers working).
`ApiClient.updateNewspaperSettings(Map<String, String?>)`. New `ui/newspaper/NewspaperScreen.kt`
+ `NewspaperSettingsModel` (state: effective size, override, saving, error) with a radio group
"Installation default / S (17 px) / M (19 px) / L (22 px) / XL (26 px)"; saving happens on
selection (autosave style, consistent with the editor), errors via the existing refusal display.
German (`values`) and English (`values-en`) string resources.

## Risks / Trade-offs

- [Class renames break nothing external yet, but reader tests assert on old class names] →
  update the reader tests in the same task; the documented list becomes the stability promise
  only from this change on.
- [Contrast of section colours in dark mode] → separate dark values; verify with a contrast
  check during the manual UI task (Playwright screenshots light/dark).
- [Per-render `stat` of `custom.css`] → negligible for a family newspaper; could be cached with a
  short TTL later without spec change.
- [`toRealPath` on a missing theme dir throws] → `ThemeFiles` treats a missing/unreadable
  directory as empty (404 for all, no `custom.css`), logs one INFO at startup.
- [Font licence compliance] → OFL texts shipped and served next to the fonts; no renaming or
  modification of the fonts.
- [Text-size cookie on shared family devices] → it is a harmless preference; the reader can
  switch back at any time.

## Migration Plan

No database migration (overrides live in the existing `newspaper.settings` JSONB). Deploy order:
new image with the compose mount; an existing deployment without `theme/` keeps working (no
`custom.css`, default theme). Rollback: previous image; a stored `reader.text-size` override is
still read correctly by the old version.
