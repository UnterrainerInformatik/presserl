## Context

Media (M5) are written once: `MediaService.upload` decodes, re-encodes without metadata and
produces three renditions, puts all objects under random keys into RustFS and then inserts
`media` + `media_rendition` rows in one transaction. Articles reference media only through
`article_revision.lead_image_media_id` (any revision, `ON DELETE RESTRICT`). Because the bytes
never change, `/api/media/{id}/content|renditions/{kind}` answer `immutable` for a year, and the
admin app caches decoded thumbnails per media id (`Thumbnails`). The reader route
`/media/{id}/{kind}` answers `max-age=3600` and is referenced by Qute templates without a version.

Permissions today: every writer (`WRITE_ARTICLES`: administrators or any section role) may read
every media and use any media id as lead image. Publishers and editors-in-chief are
"administrators" in `Newsroom`.

## Goals / Non-Goals

**Goals:**
- Browse all media with usage information; crop and pixelate server-side; the edited image
  replaces the stored one under the same id everywhere at once; no route ever serves the old bytes
  after the edit; old objects are reliably removed.
- Keep the approval chain intact: only publishers/editors-in-chief change content that is live or
  under review.

**Non-Goals:** see proposal (photo-reporter role, deletion, picker, history, other filters).

## Decisions

### D1 Same media id, new objects, version column
An edit writes the new image and renditions under **new random keys**, then in one transaction
updates the `media` row (content type, width, height, size, `object_key`, `version = version + 1`
guarded by `WHERE version = :expected`), replaces the three `media_rendition` rows and records the
**old keys** in a new table `media_object_trash`. After commit the old keys are deleted.
*Alternative:* a new media row swapped into all revisions — rejected: it rewrites revision history
(published revisions are immutable records) and needs a second id space in the reader.
*Alternative:* overwrite objects under the same keys — rejected: not atomic across four objects,
and a failure mid-way leaves mixed old/new renditions.

Migration `V12__media_version_and_trash.sql`:
```sql
ALTER TABLE media ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
-- Objects of replaced media images, deleted after the replacing transaction commits; rows that
-- remain (object store down, backend stopped) are retried on start and periodically.
CREATE TABLE media_object_trash (
    object_key TEXT        PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL
);
```
A `MediaTrashSweeper` deletes trash rows' objects after each edit (best effort, immediately) and
on `StartupEvent` plus every 10 minutes (a Vert.x periodic timer, like the rendition backfill runs
off the startup path — no `quarkus-scheduler` dependency), removing the row only after the object is
deleted (a missing object counts as deleted). The `version` check makes the stale-version `409`
race-free; if the update matches no row the new objects are discarded like a failed upload.

### D2 Processing on the server
The client sends only geometry. `MediaProcessor` gains `edit(storedBytes, crop, ellipses)`:
decode the stored image (already sRGB, no metadata, ≤ 4096 px, so no bomb checks needed beyond the
existing ones), pixelate, crop, then reuse the existing encode + rendition path. Runs under the
same processing semaphore on a worker thread. *Alternative:* edit client-side in Compose and upload
— rejected: the Wasm client would have to encode full-size JPEG/PNG, bypasses the server's
re-encoding guarantees and needs a "replace" upload path anyway.

Pixelation: block side `b = max(12, floor(min(2rx, 2ry) / 8))`; blocks tile the ellipse's bounding
box from its top-left corner (clipped to the image); each block's average is computed over the
block's pixels **inside the image** (all of them, not only those inside the ellipse, so no edge
detail leaks through small partial blocks); pixels inside the ellipse (`((x-cx)/rx)² + ((y-cy)/ry)² ≤ 1`,
pixel centres) take that average, including alpha. Ellipses are applied in request order;
overlaps are simply pixelated twice. Then crop. Coordinates are validated against the image before
the edit.

### D3 Permissions
`MediaService.mayEdit(newsroom, media)`: `newsroom.isAdministrator()` → true; else uploader
(`uploaderSub == user.sub`) and no **blocking use**. Blocking use, one query:
```sql
SELECT EXISTS (SELECT 1 FROM article a JOIN article_revision r ON r.article_id = a.id
  WHERE r.lead_image_media_id = :id
    AND ((a.live_revision = r.number)
      OR (a.pending_level IS NOT NULL AND r.number = (SELECT max(number) FROM article_revision
                                                      WHERE article_id = a.id))))
```
Checked in the edit transaction (row locked with `SELECT … FOR UPDATE` on `media`) so a publish
racing the edit cannot slip through. Non-writers get `403` before anything else.

### D4 Caching
- Admin endpoints: `Cache-Control: private, no-cache`, `ETag: "{id}-{version}"`, `If-None-Match`
  → `304` before reading the object store (only the row is loaded). Browsers' `fetch` (Ktor Js
  engine) revalidates transparently, so the admin client needs no URL change.
- `Thumbnails` in the admin app is keyed by `(mediaId, version)` so an edit in the same session
  shows the new thumbnail; the grid and the editor pass the version they know (`MediaDto.version`;
  the article's `leadImage` has none → the editor fetches `GET /api/media/{id}` it already needs
  for size, or key by id and `invalidate(id)` after a save — implementation picks the simpler).
- Reader: `ReaderImage` carries `version`; `tags/leadImage.html` appends `?v={version}`. The
  route ignores the query. `max-age=3600` stays (offline articles must still disappear); readers
  see the edit at the next page load because the URL changed. Proxies keyed by full URL are fine.

### D5 REST shapes
`MediaDto` gains `"version": 0` (after `id`).

`GET /api/media?limit=60&before=123` →
```json
{ "items": [ { "id": 122, "version": 0, "contentType": "image/jpeg", "width": 4096, "height": 2731,
               "size": 1834211, "uploadedBy": { "username": "anna", "displayName": "Anna" },
               "uploadedAt": "2026-09-27T14:03:11.402Z", "renditions": { "thumbnail": { … }, … },
               "usageCount": 2 } ],
  "next": 63 }
```
Keyset on `id` (ids grow with upload time); `usageCount` = `count(DISTINCT article_id)` in one
grouped subquery.

`GET /api/media/{id}/usage` →
```json
{ "mayEdit": false,
  "articles": [ { "id": 5, "headline": "Our cat Minka",
                  "section": { "id": 2, "name": "Tiere", "color": "#e67e22" },
                  "author": { "username": "anna", "displayName": "Anna" },
                  "status": "PUBLISHED", "pendingLevel": null,
                  "publishedAt": "2026-09-27T15:00:00Z", "updatedAt": "2026-09-28T08:12:00Z",
                  "live": true, "latest": false, "older": false } ] }
```
`older` is true when only revisions other than live and latest use it. Ordered by `updatedAt` desc.

`POST /api/media/{id}/edit` body:
```json
{ "version": 0,
  "crop": { "x": 100, "y": 50, "width": 1200, "height": 800 },
  "pixelate": [ { "cx": 600, "cy": 400, "rx": 80, "ry": 110 } ] }
```
→ `200` with the updated `MediaDto`. Errors: `400` (field `version`, `crop`, `pixelate`,
`pixelate[i]`), `403`, `404`, `409` (field `version`, message `media 17 was changed meanwhile`),
`503`. The 10M body limit applies (the body is small).

### D6 Admin UI
New `Route.Media`, `Route.MediaDetail(id)`, `Route.MediaEdit(id)`; `NavEntry.IMAGES` after
`ARTICLES`. The edit canvas shows the `print` rendition (≤ 3000 px) scaled to fit and maps
pointer positions to stored pixels via `stored.width / print.width`. Ellipse preview pixelation is
drawn client-side on the preview bitmap with the same block rule scaled to preview pixels — only a
preview; the server result is authoritative. Crop aspect presets: free, 3:2, 4:3, 16:9, 1:1.
Undo/redo stack of edit states in the screen model (pure Kotlin, unit-tested). German labels:
"Bilder", "Zuschneiden", "Verpixeln", "Speichern", confirmation "Das Bild wird in allen Artikeln
dauerhaft geändert. Das kann nicht rückgängig gemacht werden."

## Risks / Trade-offs

- **Generation loss:** each edit re-encodes a JPEG once more → quality drops slightly per edit.
  Accepted; editing is rare. Encode quality stays that of uploads.
- **Cached old images:** readers' browsers and proxies may keep the unedited rendition up to one
  hour under the old URL (`?v=0`) if they already fetched it. New page loads never reference it,
  and the server no longer serves it. Stated as a non-goal; a publisher who needs it gone faster
  can additionally take the article offline for an hour.
- **Admin revalidation cost:** every thumbnail view now costs a `304` round trip instead of none.
  Acceptable for family-sized newspapers; the `304` path touches only the database.
- **Usage visibility:** the usage list reveals headlines of articles a reporter might not see in
  "All articles". Accepted by the user (all writers see all images); only headline, section,
  author and status are shown, no content.
- **Trash retry:** an object store outage leaves trash rows; the sweeper retries every 10 minutes
  and at start. Old objects are unreachable through every route meanwhile.
