## Why

Images can be uploaded safely since `media-upload`, but nobody sees them: articles cannot carry an
image and readers get nothing. M5 (Images) promises a lead image with caption per article, shown
to readers in sizes that fit phone, desktop and — later — print. Serving the 4096 px master to a
phone wastes bandwidth, so every image needs smaller renditions derived from the stored master.
Both parts are done together because the lead image is the first and only consumer of renditions.

## What Changes

- **Renditions:** every media gets three renditions derived from its stored master —
  `thumbnail` (long side ≤ 480 px), `web` (≤ 1600 px) and `print` (≤ 3000 px) — never enlarged,
  same format as the master (JPEG, or PNG with transparency), no metadata. They are produced
  during the upload (from the already decoded pixels) and stored in the same bucket; media
  uploaded before this change get their renditions by a backfill at startup.
- `MediaDto` gains `renditions` (size and dimensions per kind). New endpoint
  `GET /api/media/{id}/renditions/{kind}` for writers (`WRITE_ARTICLES`), same headers as
  `/content`.
- **Lead image per article:** an article revision gains an optional lead image: a media id plus a
  caption (plain text, ≤ 300 characters, may be empty). It is part of the revision content — it
  is saved, revisioned and goes live with the next publication like the text fields.
  `PUT /api/articles/{id}` and `POST /api/articles` accept `leadImage: {"mediaId", "caption"}` or
  `null`; `ArticleDto` and `RevisionDto` return it with the master's width and height. A missing
  field means "no lead image" (full-replace semantics as for the text fields). **BREAKING** for
  clients that save without sending `leadImage`: they remove the image — the only client is the
  admin app, updated in this change.
- **Reader delivery:** new public route `GET /media/{id}/{kind}` (outside `/api`, no token) that
  serves a rendition only while the media is the lead image of the live revision of a
  `PUBLISHED` article, and only to visitors who may read the newspaper (private newspaper:
  entitled readers only); everything else is `404`. The master is never served to readers.
- **Reader pages:** the article page shows the lead image (`web`, with `srcset` including
  `thumbnail`) with its caption below the headline block; the front page's lead story shows the
  `web` rendition, the cards the `thumbnail`. `<img>` carries width and height against layout
  shift. New documented styling classes `.lead-image` and `.lead-image__caption`.
- **Admin editor:** a lead-image field in the editor (all levels — the design guidelines list
  "image (+ caption)" already for `starter`): choose a file, upload it, see a thumbnail preview,
  write the caption, replace or remove the image; errors of the upload (too large, wrong type,
  undecodable) are shown in plain words. Autosave includes the lead image. The revision view
  shows the revision's lead image and caption.
- `ai/open-proposals.md`: the M5 section is removed (both remaining entries become this change).

## Capabilities

### New Capabilities
- `reader-media`: delivering renditions of published lead images to readers (route, access rules
  including a private newspaper, caching headers).

### Modified Capabilities
- `media`: renditions (kinds, sizes, generation, backfill), `renditions` in the media metadata,
  the writer endpoint for renditions; the access requirement no longer says media are never served
  to readers.
- `articles`: the revision content gains the optional lead image with caption (validation,
  revisioning, equality for "unchanged" saves, API shape).
- `reader-articles`: article page and front page show the lead image and caption.
- `admin-articles`: lead-image field in the editor, preview, upload errors, revision view.

## Non-goals

- Images inside the article body, galleries, info boxes (`profi` level).
- Choosing an already uploaded image from a media library; every lead image is uploaded in the
  editor.
- A separate alt text or photo credit field; the caption is the only text (see design).
- Cropping, focal point or rotation in the editor.
- Deleting media and cleaning up media no revision references (still open from `media-upload`).
- Print views and the use of the `print` rendition in them (M6 — the rendition is produced and
  served now so M6 only has to link it).
- WebP/AVIF output, responsive art direction via `<picture>`.
- Lead image in the admin article lists.

## Impact

- **backend:** `media` package — rendition generation in `MediaProcessor`, `MediaRenditionEntity`,
  upload writes master + three renditions (all-or-nothing cleanup), startup backfill, new writer
  endpoint, `MediaDto.renditions`; `article` package — lead image in `ArticleContent`,
  validator, revision entity, DTOs; Flyway `V10__media_renditions_and_lead_image.sql`
  (`media_rendition` table, two revision columns with a foreign key to `media`).
- **reader:** `ReaderArticle` carries the lead image, new `ReaderMediaResource` (`/media/{id}/{kind}`),
  templates `article.html`, `frontpage.html`/`story.html`, `reader.css`.
- **admin:** API client (`ArticleContent.leadImage`, `MediaDto.renditions`, rendition download),
  editor model and screen (file picker via browser interop, preview, caption), revision view,
  Kotlin tests.
- **deploy:** none (same bucket; storage per image grows by roughly half the master).
- **docs:** `docs/architecture.md` (data model, API list, reader routes, security checklist),
  `docs/design-guidelines.md` (styling API classes), `ai/primer/endpoints.md`,
  `http/media.http`, `http/articles.http`, `http/reader.http`, `ai/open-proposals.md`.
