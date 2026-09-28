## 1. Backend — renditions

- [x] 1.1 Flyway `V10__media_renditions_and_lead_image.sql` (`media_rendition`, `article_revision.lead_image_media_id` + `lead_image_caption` with the caption check and partial index); `MediaRenditionEntity`; `RenditionKind` enum (`thumbnail` 480, `web` 1600, `print` 3000)
- [x] 1.2 `MediaProcessor`: `Processed` gains the three renditions derived from the scaled master pixels (successive downscaling, never enlarging, same format, metadata-free encoder); `deriveRenditions(byte[] master)` for the backfill (sniff + decode via the existing path)
- [x] 1.3 Unit tests: 6000×4000 JPEG → thumbnail 480×320, web 1600×1067, print 3000×2000; 400×300 opaque PNG → three 400×300 JPEGs; transparent PNG → three PNGs with alpha; portrait image keeps orientation of sides; renditions carry no EXIF/XMP/IPTC (metadata-extractor)
- [x] 1.4 `MediaService.upload`: put master + renditions, insert media + rendition rows in one transaction, best-effort delete of every written key on failure; `503` on store errors
- [x] 1.5 `MediaDto.renditions` (map kind → width, height, size; `{}` when none yet); `GET /api/media/{id}/renditions/{kind}` in `MediaResource` with the `/content` headers; `404` for unknown kind / missing rendition
- [x] 1.6 `MediaRenditionBackfill` on `StartupEvent` (after the bucket bootstrap): background worker, one media at a time under the processing semaphore, failures logged and left for the next start
- [x] 1.7 `@QuarkusTest`s: upload response lists the three renditions with sizes; rendition download (bytes, headers, dimensions) for each kind; unknown kind `404`; reader-only `403`, no token `401`; store failure during a rendition put leaves no row and no object; backfill produces renditions for a media inserted without them (ready before the backfill finishes)

## 2. Backend — lead image on articles

- [x] 2.1 `ArticleContent.LeadImage(mediaId, caption)`; `ArticleContentValidator` reads `leadImage` (null/absent, object with `mediaId` + optional `caption`, unknown nested fields, caption rules ≤ 300 chars, errors named `leadImage.*`); `ArticleLimits.CAPTION_MAX`
- [x] 2.2 `ArticleService`: existence check for `mediaId` (`400` `leadImage.mediaId`) on create and save; `ArticleRevisionEntity` columns, `holds` and `apply` include the lead image
- [x] 2.3 `ArticleDto` and `RevisionDto` return `leadImage` (`mediaId`, `caption`, master `width`/`height`) without N+1 (load the media of the returned revisions together)
- [x] 2.4 Validator unit tests: absent/null, only `mediaId`, caption trimmed, caption 301 chars, line break in caption, `mediaId` 0/string/missing, unknown nested field, `leadImage` not an object; all errors reported together with other field errors
- [x] 2.5 `@QuarkusTest`s: set/replace/remove lead image on a draft; unknown media `400` and nothing saved; caption-only change on a published article creates revision 2 and `hasUnpublishedChanges`; unchanged save with lead image creates no revision; publish makes it live; `RevisionDto` of an older revision shows its own lead image; `POST /api/articles` with `leadImage`

## 3. Reader

- [x] 3.1 `ReaderMediaResource` `GET /media/{id}/{kind}`: id/kind parsing, visibility via `ReaderViewer` + effective settings, single query "rendition of a media that is the lead image of a live revision of a PUBLISHED article", empty `404` otherwise, bytes on a worker thread, headers (`nosniff`, `Cache-Control` public/private `max-age=3600`)
- [x] 3.2 Add `/media/*` to `quarkus.oidc.reader.tenant-paths` and the reader's permitted paths; check `ReaderTenantScope` handles it
- [x] 3.3 `ReaderArticle` gains the lead image (media id, caption, `web` and `thumbnail` dimensions), loaded without N+1 for the front page
- [x] 3.4 Templates: `article.html` figure (after subheadline, before lead) with `src`, `srcset`, `sizes`, `width`/`height`, `alt=""`, `figcaption` only for a non-empty caption; `story.html` figure (lead story `web`, cards `thumbnail` + `loading="lazy"`)
- [x] 3.5 `reader.css`: `.lead-image`, `.lead-image__caption` (responsive width, caption style, `break-inside: avoid`)
- [x] 3.6 Tests for the route: published lead image `200` + headers for each kind; draft-only media, working-revision-only media, offline article, unknown media, unknown kind, malformed id → `404`; private newspaper: anonymous `404`, logged-in without role `404`, entitled reader `200` with `private, max-age=3600`; the master is never served
- [x] 3.7 Tests for the pages: article page with image + caption (escaped `<b>`), without caption (no figcaption), without image (no figure); unpublished image change references only the live media; front page lead story uses `web`, card uses `thumbnail`

## 4. Admin

- [x] 4.1 Spike first: confirm `ByteArray.decodeToImageBitmap()` works in the Wasm target with the Compose version in use (record the result in design.md if a different decode path is needed)
- [x] 4.2 API client: `LeadImageRequest`/`LeadImageDto`, `ArticleContent.leadImage` (always serialised, explicit `null`), `ArticleDto`/`RevisionDto.leadImage`, `MediaDto.renditions` + `RenditionDto`, `mediaRendition(id, kind)`; `ApiClientTest` cases (request JSON with and without lead image, DTO decoding, rendition download with bearer token)
- [x] 4.3 `expect fun pickImageFile()` with the Wasm actual (hidden `<input type="file" accept=…>`, awaits `change`, reads bytes); no-op/`null` actual for other targets if they exist
- [x] 4.4 `EditorModel`: lead image state (media id, caption, dims), upload flow (busy flag, success sets media id and keeps caption, marks dirty), error mapping `413` (with `media.max-size`), `415`, `400`, `503`/network; remove; undo/redo; thumbnail cache per media id
- [x] 4.5 `EditorScreen`: lead-image field between subheadline and lead on every editor level — choose button (icon + word), busy indicator, preview, caption field (single line, ≤ 300), replace and remove; UI strings in the admin's existing string resources
- [x] 4.6 Revision view shows the revision's lead image preview and caption read-only
- [x] 4.7 Kotlin tests for `EditorModel`: upload success sets lead image and triggers autosave with it; each error status keeps the previous image and yields the right message; remove sends `null`; reopen + save unchanged sends identical `leadImage`; caption length/line-break limits

## 5. Contract and docs

- [x] 5.1 `ai/primer/endpoints.md`: `MediaDto.renditions`, `GET /api/media/{id}/renditions/{kind}`, `leadImage` in article requests/`ArticleDto`/`RevisionDto` with errors, reader route `GET /media/{id}/{kind}`
- [x] 5.2 `http/media.http` (rendition downloads), `http/articles.http` (set/remove lead image, unknown media), `http/reader.http` (published lead image, draft-only `404`); run them against a live dev backend
- [x] 5.3 `docs/architecture.md`: data model (Media renditions, lead image on ArticleRevision instead of Article), API list, reader routes, security checklist (reader only gets published renditions; storage growth)
- [x] 5.4 `docs/design-guidelines.md`: add `.lead-image`, `.lead-image__caption` to the public styling API

## 6. Verification

- [x] 6.1 `./mvnw verify` in `backend/` and `./gradlew check` in `admin/` (the change touches both broadly)
- [x] 6.2 Headless UI check (Playwright) in dev: upload a phone photo with GPS in the editor, add a caption, publish; reader article page and front page show the image; the downloaded `web` rendition has no EXIF; a HEIC upload shows the unsupported-type message
- [x] 6.3 Build the image and upload once against it (headless AWT, renditions produced); stop every server/container started for verification
