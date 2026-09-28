## Context

See proposal.md for the motivation. Relevant current state:

- `media-upload` stores one re-encoded master per upload (`media/<uuid>.<jpg|png>`, ≤ 4096 px) in
  RustFS and a `media` row (`V9__media.sql`). `MediaProcessor.process` decodes, orients, converts to
  sRGB, scales (`scale(image, maxLongSide)`, stepwise halving + bilinear) and encodes (JPEG 0.85
  baseline or PNG, no metadata). Processing is bounded by a semaphore
  (`presserl.media.max-concurrent-processing`).
- Upload order is process → put object → insert row; a failed insert deletes the object best-effort.
- Article content lives in `article_revision` (kicker, headline, subheadline, lead, body);
  `ArticleContentValidator` reads requests strictly (unknown fields rejected, all errors together);
  `ArticleRevisionEntity.holds/apply` decide "unchanged" saves and copy content.
- The reader (`ReaderResource`, Qute) reads only live revisions of `PUBLISHED` articles
  (`ReaderArticles.LIVE_PUBLISHED`), handles private visibility via `ReaderViewer`, and the reader
  OIDC tenant covers `quarkus.oidc.reader.tenant-paths=/,/login,/logout,/articles/*`.
- CSP for reader and API: `img-src 'self' data:` — same-origin images need no CSP change.
- The admin app (Compose Wasm) has `uploadMedia`, `media`, `mediaContent` in `ApiClient` but no UI.

## Goals / Non-Goals

**Goals:**
- Readers never download more than the size they display, and never anything unpublished.
- Lead image behaves exactly like the other content fields (revisioned, "unchanged" detection,
  goes live with publishing) — no second publication path.
- Renditions reuse the one safe pipeline; no second decoder path for untrusted bytes.

**Non-Goals:** see proposal (body images, media library, alt/credit, cropping, print views, deletion).

## Decisions

### 1. Rendition kinds and sizes

| Kind | Long side ≤ | Purpose |
|---|---|---|
| `thumbnail` | 480 px | front-page cards, editor preview, small screens via `srcset` |
| `web` | 1600 px | article page, front-page lead story |
| `print` | 3000 px | M6 print views (≈ 25 cm at 300 dpi) |

Never enlarged; aspect ratio kept (`Math.round` on the short side, as for the master); same format
as the master (JPEG 0.85 baseline / PNG), encoded by the existing metadata-free writer. Kinds are a
Java enum `RenditionKind` with the size; URL/JSON values are the lower-case names.

A rendition is always its own object, even when it has the master's size (small images). Keeps one
code path; cost is a few hundred KB per small image. Alternative "point the rendition to the master
key" rejected: shared objects complicate later deletion and the reader must never see master keys.

### 2. Generation during upload, from the decoded master pixels

`MediaProcessor.process` returns the master plus the three renditions, derived from the already
scaled sRGB `BufferedImage` of the master (scale 4096 → 3000 → 1600 → 480 successively, each from the
previous step's result, which is exactly what `scale` would do via halving anyway). No re-decode,
no extra untrusted input. `Processed` gains `List<Rendition> renditions` (`kind`, `bytes`, `width`,
`height`).

Upload: process → put master → put 3 renditions → insert `media` + 3 `media_rendition` rows in
one transaction. On any failure after the first put, all already written keys are deleted
best-effort (logged). `503` on store errors as before.

Memory: the heaviest rendition (3000 px) is ≈ 36 MB as `INT_RGB`, below the master; the semaphore
bounds concurrency as before.

Alternative: lazy generation on first request — rejected: the first reader pays the latency, two
code paths for "exists / does not exist", concurrent first requests race; eager is simple and the
upload response can list the renditions.

### 3. Backfill for media without renditions

`MediaRenditionBackfill` observes `StartupEvent` (after `MediaBucketBootstrap`) and starts a
background task on a worker thread: select media ids without a complete set of renditions, for
each download the master, decode it with the same `MediaProcessor` path used for masters
(`deriveRenditions(byte[] master)`, type sniffed from bytes as always), put the renditions, insert
the rows. One media at a time under the processing semaphore; a failure is logged and the media
is retried on the next start. Readiness is not affected. `media_rendition` has `UNIQUE (media_id,
kind)`; a conflicting insert (e.g. a concurrent upload finishing — impossible for old media, but
harmless) deletes its just-written objects. Only one backend instance is supported anyway.

### 4. Data model — `V10__media_renditions_and_lead_image.sql`

```sql
CREATE TABLE media_rendition (
    media_id     BIGINT NOT NULL REFERENCES media (id) ON DELETE CASCADE,
    kind         TEXT   NOT NULL CHECK (kind IN ('thumbnail', 'web', 'print')),
    object_key   TEXT   NOT NULL UNIQUE,
    content_type TEXT   NOT NULL CHECK (content_type IN ('image/jpeg', 'image/png')),
    width        INT    NOT NULL CHECK (width > 0),
    height       INT    NOT NULL CHECK (height > 0),
    byte_size    BIGINT NOT NULL CHECK (byte_size > 0),
    PRIMARY KEY (media_id, kind)
);

ALTER TABLE article_revision
    ADD COLUMN lead_image_media_id BIGINT REFERENCES media (id) ON DELETE RESTRICT,
    ADD COLUMN lead_image_caption  TEXT NOT NULL DEFAULT '',
    ADD CONSTRAINT article_revision_caption_needs_image
        CHECK (lead_image_media_id IS NOT NULL OR lead_image_caption = '');
CREATE INDEX article_revision_lead_image_idx ON article_revision (lead_image_media_id)
    WHERE lead_image_media_id IS NOT NULL;
```

`ON DELETE RESTRICT` keeps a referenced media undeletable once deletion exists. Additive; existing
revisions get no lead image.

Why on the revision and not on the article: the caption is content, and an image swapped on a
published article must not go live before the next publication (spec "New lead image stays hidden
until publication"). The architecture's "Article … later: lead image" note is updated accordingly.

### 5. REST contract

**Media** — `MediaDto` gains `renditions` (always all three once produced; `{}` while a backfill is
pending):
```json
{ "id": 17, "contentType": "image/jpeg", "width": 4096, "height": 2731, "size": 1834211,
  "uploadedBy": { "username": "papa", "displayName": "Papa" },
  "uploadedAt": "2026-09-27T14:03:11.402Z",
  "renditions": {
    "thumbnail": { "width": 480,  "height": 320,  "size": 31877 },
    "web":       { "width": 1600, "height": 1067, "size": 298114 },
    "print":     { "width": 3000, "height": 2000, "size": 861022 } } }
```
`GET /api/media/{id}/renditions/{kind}` → `200` bytes, headers as `/content`; `404` for unknown
id/kind or a rendition not produced yet (`ApiErrorDto`, `field` `null`); `403`/`401` as the other
media endpoints; `503` store unreachable.

**Articles** — request (`POST /api/articles`, `PUT /api/articles/{id}`):
```json
{ "kicker": "", "headline": "Minka is back", "subheadline": "", "lead": "", "body": { "version": 1, "blocks": [] },
  "leadImage": { "mediaId": 17, "caption": "Our cat Minka" },
  "sectionId": 3, "version": 5 }
```
`leadImage` absent or `null` ⇒ none. Errors (all collected with the other field errors, `400`):
`leadImage` not an object → field `leadImage`; `mediaId` missing / not a positive integer →
`leadImage.mediaId`; unknown media → `leadImage.mediaId` "does not exist" (checked in
`ArticleService` after the syntactic validation, one query); caption not a string, control
characters, > 300 characters → `leadImage.caption`; other keys → `leadImage.<key>` "unknown field".

Response — `ArticleDto` and `RevisionDto` gain:
```json
"leadImage": { "mediaId": 17, "caption": "Our cat Minka", "width": 4096, "height": 2731 }
```
or `null`. `width`/`height` are the master's (aspect ratio for the editor's layout).
`ArticleSummaryDto` is unchanged.

`ArticleContent` becomes `(kicker, headline, subheadline, lead, body, LeadImage leadImage)` with
`record LeadImage(long mediaId, String caption)`; `holds` compares it with `Objects.equals`, `apply`
copies it. Existing `ArticleContent` constructors in tests get a `null` lead image.

### 6. Reader route `GET /media/{id}/{kind}`

New `ReaderMediaResource` (`@Path("/media")`), no `@Produces` (as `MediaResource.content`):

1. `id` must match `\d{1,18}`, `kind` a `RenditionKind` — else `404`.
2. Visibility: same decision as the article page via `ReaderViewer` and the effective settings —
   private and not entitled (anonymous or no role) ⇒ `404` (no login redirect: it is an `<img>`).
3. One query: the rendition of media `id`/`kind` *if*
   `exists (article a join article_revision r on r.article_id = a.id and r.number = a.live_revision
   where a.status = 'PUBLISHED' and r.lead_image_media_id = :id)`; none ⇒ `404`.
4. Fetch bytes on a worker thread; `503` page-less response when the store is down.
5. Headers: `Content-Type`, `Content-Length`, `X-Content-Type-Options: nosniff`,
   `Cache-Control: public, max-age=3600` (public) or `private, max-age=3600` (private).

The `404` body is empty (not the HTML not-found page) — it is only ever loaded by `<img>`.
`/media/*` is added to `quarkus.oidc.reader.tenant-paths` so an entitled reader's session cookie is
recognised (the comment in `application.properties` asks for exactly this), and to the reader's
permitted paths; `ReaderTenantScope` is checked to pin it correctly.

Why an hour and not `immutable`: URL = media id + kind, the bytes never change, but *whether* they
may be served does (take offline, emergency brake, private switch). One hour bounds how long a
cache keeps serving an image that was taken offline; the reader pages themselves are not cached
longer. Alternative: URL per article (`/articles/{a}/lead-image/{kind}`) — rejected, the same media
may lead two articles and the media-centred URL keeps the check simple.

### 7. Reader rendering

- `ReaderArticle` gains `ReaderImage leadImage` (`mediaId`, `caption`, `webWidth`, `webHeight`,
  `thumbWidth`, `thumbHeight`), loaded in the same query as today via a left join on `media_rendition`
  (two joins, kinds `web`/`thumbnail`) — or, if the HQL gets unwieldy, one follow-up query for all
  media ids of the page (never N+1).
- Article page (`article.html`), after subheadline, before lead:
  ```html
  <figure class="lead-image">
    <img src="/media/17/web" srcset="/media/17/thumbnail 480w, /media/17/web 1600w"
         sizes="(min-width: 60rem) 60rem, 100vw" width="1600" height="1067" alt="">
    <figcaption class="lead-image__caption">Our cat Minka</figcaption>
  </figure>
  ```
  `alt=""` because the figcaption is the image's text and would otherwise be read twice; without
  caption the image is treated as decorative. A dedicated alt text is a later `profi` feature.
- Front page: `story.html` gets the figure (lead story `web`, cards `thumbnail`), `loading="lazy"`
  on cards.
- `reader.css`: `.lead-image img { width: 100%; height: auto; display: block }`, caption in the
  caption style of the design guidelines, `break-inside: avoid` for print. Classes `.lead-image`
  and `.lead-image__caption` are added to the documented styling API.

### 8. Admin editor

- API client: `ArticleContent.leadImage: LeadImageRequest?` (`mediaId`, `caption`), `ArticleDto`/
  `RevisionDto.leadImage: LeadImageDto?`, `MediaDto.renditions: Map<String, RenditionDto>`,
  `suspend fun mediaRendition(id, kind): ByteArray`. `leadImage` is always sent (explicit `null`),
  so the client never removes an image by omission.
- File picking: `expect fun pickImageFile(): PickedFile?` (`name`, `bytes`); the Wasm actual creates
  a hidden `<input type="file" accept="image/jpeg,image/png,image/webp">` via `kotlinx.browser`
  and awaits its `change` event. `accept` is only a hint; the server decides.
- Preview: rendition bytes via `mediaRendition(id, "thumbnail")` (the token is needed, so no plain
  image URL), decoded with Compose's `ByteArray.decodeToImageBitmap()` (to be confirmed against the
  Compose version in use during apply; fallback: an `<img>` with a `blob:` URL is not possible in
  the canvas UI, so the decode path must work). Cached per media id in the editor model.
- `EditorModel`: state `leadImage` (media id, caption, dims), `uploading`, `uploadError`; upload →
  on success set media id (caption kept when replacing), mark dirty → autosave. Error mapping:
  `413` → "too large (max. X)" with X from the effective settings (`media.max-size` is already in
  the newspaper settings DTO), `415` → unsupported type, `400` → damaged/too many pixels, `503`/
  network → server not reachable. Undo/redo covers the lead image like other fields.
- `EditorScreen`: the field sits between subheadline and lead (the reader's order). Big button
  with icon **and** word (starter guideline), preview ≤ 240 dp wide, caption field, "Replace" and
  "Remove".
- Revision view: preview + caption read-only.

## Risks / Trade-offs

- [Upload takes longer (three extra encodes + puts)] → encodes are from already scaled pixels
  (3000 px and smaller); measured during verification, acceptable for a family newspaper.
- [Storage grows by ~50 % per image] → noted in `docs/architecture.md`; no deploy change.
- [Backfill fails repeatedly for a broken master] → logged per media each start; the media simply
  has no renditions (`404` for them). There are no productive installations yet, so this is mostly
  for dev data.
- [Media enumeration via `/media/{id}`] → only published lead images answer; all else is an
  indistinguishable empty `404`.
- [Image cached up to an hour after taking an article offline] → documented; the emergency brake
  still removes the article itself immediately.
- [`decodeToImageBitmap` availability in Compose Wasm] → verified early in apply; the task list puts
  it first in the admin block.
- [Caption without alt text is weaker for screen readers when empty] → accepted for now; alt text
  field is listed as a later `profi` feature.

## Migration Plan

- `V10` is additive. On first start after the update the backfill produces renditions for existing
  media in the background.
- Admin app and backend ship together in one image, so the `leadImage` full-replace semantics never
  meet an old client.
- Rollback: previous image tag; the extra table and columns are ignored by the old version (lead
  images set meanwhile are lost to it, the old version does not show them anyway).
