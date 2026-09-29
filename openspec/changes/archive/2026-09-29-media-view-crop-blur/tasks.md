## 1. Backend — data and processing

- [x] 1.1 Add migration `V12__media_version_and_trash.sql` (`media.version`, `media_object_trash`) and `version` on `MediaEntity`; verify with `@QuarkusTest` startup and an existing media reading version `0`
- [x] 1.2 Add `MediaDto.version`; verify `POST /api/media` and `GET /api/media/{id}` return `version` `0` in the media tests
- [x] 1.3 Implement pixelation in `MediaProcessor` (block side `max(12, min diameter / 8)`, bounding-box aligned, block average over in-image pixels incl. alpha, clipping); verify with unit tests: uniform blocks inside the ellipse, unchanged pixels outside, 12 px minimum, transparent PNG keeps alpha
- [x] 1.4 Implement crop and `edit(stored, crop, ellipses)` (pixelate first, crop second, re-encode + renditions via the upload path); verify with unit tests for dimensions, format (JPEG/PNG) and absence of metadata
- [x] 1.5 Implement edit validation (`version` required, at least one operation, crop inside and ≥ 16 px, ≤ 50 ellipses, radius ≥ 4, centre inside); verify each `400` field in unit tests

## 2. Backend — endpoints

- [x] 2.1 `GET /api/media` with `limit`/`before` keyset paging and `usageCount`; verify with `@QuarkusTest`: first/last page, `next`, `400` for bad `limit`/`before`, `403` reader-only, `401` without token
- [x] 2.2 `GET /api/media/{id}/usage` with `mayEdit` and `live`/`latest`/`older` flags, ordered by `updatedAt`; verify with `@QuarkusTest` covering live + draft use, image removed in a newer revision, unused media, `404`
- [x] 2.3 Edit permission (`mayEdit`: administrators always; uploader without live or pending-latest use), checked inside the edit transaction with the media row locked; verify with `@QuarkusTest` for every scenario of "Who may edit"
- [x] 2.4 `POST /api/media/{id}/edit`: store new objects, update row with version guard (`409`), replace renditions, record old keys in trash, commit, delete old objects; discard new objects on any failure; verify with `@QuarkusTest`: success returns version `1` and new size, stale version `409` leaves media unchanged, object store down `503` leaves media unchanged
- [x] 2.5 `MediaTrashSweeper` (after each edit, on start, every 10 minutes; row removed only after the object is gone); verify with a test that a trash row whose delete failed is removed on the next sweep and its object no longer exists
- [x] 2.6 Switch content and rendition responses to `Cache-Control: private, no-cache` + `ETag: "{id}-{version}"` and answer `304` to a matching `If-None-Match` without reading the object store; verify with `@QuarkusTest` for `200`/`304`/`200` after an edit

## 3. Reader

- [x] 3.1 Carry the media version in `ReaderImage` (and the queries feeding it) and append `?v={version}` to every lead-image URL in `tags/leadImage.html` (src and srcset, all variants); verify with reader tests that article page, front page card, issue page and print view link `?v=0`, and `?v=1` after an edit
- [x] 3.2 Verify `/media/{id}/{kind}?v=0` serves the current (edited) rendition and still follows all visibility rules (existing reader-media tests pass with query strings added)

## 4. Admin

- [x] 4.1 DTOs and `ApiClient`: `MediaDto.version`, `MediaPage`, `MediaUsageDto`, `EditMediaRequest`, `listMedia(limit, before)`, `mediaUsage(id)`, `editMedia(id, request)` with error mapping (`400`/`403`/`409`/`503`); verify with `ApiClientTest` and `DtoTest`
- [x] 4.2 Make `Thumbnails` version-aware (key or invalidate by media id after a save); verify with a unit test that a saved edit re-fetches the thumbnail
- [x] 4.3 `NavEntry.IMAGES` after "Articles" for `WRITE_ARTICLES`; verify the updated navigation tests (reporter now sees "Articles" and "Images")
- [x] 4.4 Media grid screen with paging on scroll, thumbnail placeholder, uploader, date and usage count / "not used"; verify with a Compose UI or model test for loading the next page
- [x] 4.5 Media detail screen: `web` preview, metadata, usage list (section colour, status, pending level, published date, live/working/older), open article in editor, "Edit" only with `mayEdit`; verify with a model test for the flags → labels and the edit button
- [x] 4.6 Edit model (pure Kotlin): crop rectangle with aspect presets and bounds/min-size clamping, ellipses add/move/resize/remove (max 50), preview→stored pixel mapping, undo/redo, dirty flag; verify with unit tests
- [x] 4.7 Edit screen: canvas on the `print` rendition with dimmed outside-crop area, handles, ellipse drawing with client-side pixelated preview, reset; confirmation dialog naming affected and published article counts; save and error messages (`409` with reload, `403`, `400`, `503`); discard confirmation on back; verify by driving the Wasm app headless (Playwright) against a local backend
- [x] 4.8 German and English strings for all new texts; verify the resource test/build passes

## 5. Contract / Docs

- [x] 5.1 Update `ai/primer/endpoints.md`: `MediaDto.version`, `GET /api/media`, `GET /api/media/{id}/usage`, `POST /api/media/{id}/edit`, new caching headers and `304`, reader `?v=`; verify by reading it against design D5
- [x] 5.2 Extend `http/media.http` with list, usage and edit requests (success, `409`, `400`) and run them against a local backend; verify the responses match the primer
- [x] 5.3 Update `docs/` where images are described (vision/architecture mention of media editing and the permission rule in `roles-and-workflow.md`); verify the texts match the specs

## 6. Verification

- [x] 6.1 Run backend media, reader and article tests and the admin tests (`reference_build_and_test.md`); all green
- [x] 6.2 End-to-end on a local stack: upload an image, use it in a published article, pixelate and crop it as publisher, confirm the reader page links the new version and shows the edited image, the old objects are gone from RustFS, and a reporter gets no "Edit" for their live image
- [x] 6.3 `openspec validate media-view-crop-blur --strict` passes
