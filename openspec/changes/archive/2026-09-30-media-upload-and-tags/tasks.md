## 1. Backend

- [x] 1.1 Add Flyway migration `V16__media_details.sql` (`media.description`, `media_tag` with `name_key` index) and the `description` field / `MediaTagEntity`; verify the backend starts against an existing dev database and `@QuarkusTest` suite boots
- [x] 1.2 Implement `MediaDetailsValidator` (normalisation, case-insensitive de-duplication, limits, `description`/`tags`/`tags[i]` errors) with unit tests covering every rule in the `media-details` spec
- [x] 1.3 Implement tag storage with spelling adoption in `MediaService` (replace tags of a media, adopt the newspaper's existing spelling, sorted output); verify with `@QuarkusTest` "Tag spelling of the newspaper is kept" and "Duplicates in one request"
- [x] 1.4 Extend `MediaDto`/`MediaListItemDto` with `description` and `tags` (list page loads tags in one query per page); verify `GET /api/media/{id}` and `GET /api/media` return both, `null`/`[]` for old media
- [x] 1.5 Accept `description` and `tag` parts on `POST /api/media`, validated before decoding, stored in the upload transaction; verify tests "Upload with tags and description" and "Invalid tag on upload" (nothing stored)
- [x] 1.6 Add `PUT /api/media/{id}/details` (USE_MEDIA, required fields, unknown fields refused, version unchanged); verify tests for success on someone else's live image, `400` cases, `403` reader, `404`, `401`
- [x] 1.7 Add `GET /api/media/tags` (prefix incl. word prefix with LIKE escaping, count ordering, `limit` validation); verify tests "Suggest while typing", "Most used tags", invalid `limit`, `403`
- [x] 1.8 Add list filters `tag`, `q`, `unused`, `mine` to `GET /api/media` with parameter validation; verify tests "Two tags", "Words in the description", "Own unused images", "Filtered paging", "Invalid flag", too many tags, `q` too long

## 2. Admin

- [x] 2.1 Extend DTOs and `ApiClient`: `description`/`tags` on media, `uploadMedia` with description and tags, `setMediaDetails`, `mediaTags(prefix, limit)`, `listMedia(filter, before)`; verify `ApiClientTest` checks multipart parts, query parameters and JSON bodies
- [x] 2.2 Add `pickImageFiles(camera)` returning lazy `PickableFile`s (multiple, `capture="environment"` for the camera) on the Wasm target; verify manually in Chromium (desktop picker, multi-select) and with mobile emulation that `capture` is set
- [x] 2.3 Move the upload error texts out of `EditorScreen.kt` into a shared media UI file and keep the editor using them; verify the editor tests still pass
- [x] 2.4 Add `MediaFilter` to `MediaGridModel` (`setFilter` resets and reloads, `prepend`); verify `MediaBrowserModelTest` for filtered loading, paging with filters and prepending
- [x] 2.5 Implement `MediaUploadModel` (sequential upload, per-file states, retry/remove, pending check, prepend on success); verify tests "Three photos with tags", "One file too large" and "Cancel the picker" with a fake API
- [x] 2.6 Build the shared `TagInput` composable (chips, suggestions from `mediaTags`, Enter to add) and a small suggestion model with tests for adding, de-duplicating and removing chips
- [x] 2.7 Build the grid header: "Upload images", "Take photo", search bar (tags, debounced text, "Unused", "My uploads"), tags on tiles, empty result with "Clear filters"; verify by driving the admin headless (Playwright) against a dev backend
- [x] 2.8 Build the upload dialog (file list with size and state, shared tags/description, start, retry, remove, discard confirmation); verify headless with three files, one of them too large
- [x] 2.9 Show and edit description and tags in the media detail (save, error texts, discard confirmation, tag click filters the grid); verify with a model test and a headless run
- [x] 2.10 Add German and English string resources for all new texts; verify the build has no missing resources

## 3. Contract/Docs

- [x] 3.1 Update `ai/primer/endpoints.md`: `MediaDto` fields, `POST /api/media` parts and errors, `PUT /api/media/{id}/details`, `GET /api/media/tags`, list filters; verify every shape matches design.md and the implementation
- [x] 3.2 Extend `http/media.http` with upload including tags, details update, tag suggestions and filtered lists; verify by running them against a live dev backend

## 4. Verification

- [x] 4.1 Run the backend media tests and the admin tests (`reference_build_and_test.md` commands); verify all green
- [x] 4.2 End-to-end headless check: upload two photos with tags from "Images", find them via tag chip and description text, edit a tag in the detail, confirm the grid reflects it; stop all servers started for the check
