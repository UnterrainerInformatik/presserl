## 1. Admin — shared media pieces

- [x] 1.1 Move `UploadError` / `uploadErrorOf` from `EditorModel.kt` to `ui/media/UploadErrors.kt`; verify `./gradlew check` in `admin/` still compiles and `MediaUploadModelTest` passes
- [x] 1.2 Extract the Images view's search block (tag input, debounced text, "Unused"/"Mine" chips, "Clear filters") into an internal `MediaSearchBar` and make `MediaTile` internal; `MediaGridScreen` uses them unchanged in behaviour; verify `MediaBrowserModelTest` passes and the Images view looks and filters as before (Playwright screenshot)

## 2. Admin — media picker

- [x] 2.1 Add `MediaPickerDialog(api, thumbnails, onPick, onDismiss)`: fresh `MediaGridModel` per opening with `MediaFilter()`, `MediaSearchBar`, tile grid with paging on scroll, load error with retry, "no images yet — add them in Images" for an empty library, "nothing matches" with "Clear filters", close button / Escape / outside dismiss; no upload or camera; verify with a Kotlin test that a picker grid model starts unfiltered and pages with `next`
- [x] 2.2 Add picker string resources (de + en): title, close, empty library; verify both locales resolve (build passes, no missing-resource warnings)

## 3. Admin — editor

- [x] 3.1 Rename `UploadTarget` to `ImageTarget`, add `EditorModel.useImage(target, media: MediaListItemDto)` dispatching `SetLeadImage` / `AddImageBlock` / `SetBlockImage`; remove `uploadImage`, `uploadLeadImage`, `pickAndUpload`, `uploadingAt`, `uploadFailure`, `uploading`, `uploadErrorAt`; verify with tests in `LeadImageEditorTest` and `ImageBlockEditorTest` (pick sets lead image with width/height, replace keeps caption, insert at the menu's position, replace block keeps caption, same media twice, undo of an inserted image)
- [x] 3.2 Rewrite `LeadImageEditorTest` / `ImageBlockEditorTest` cases that exercised uploads and upload errors (too large, unsupported, cancel picker) into picker-based cases or drop them where the behaviour is gone; verify `./gradlew check` passes
- [x] 3.3 `EditorScreen`: hold `picking: ImageTarget?`, open `MediaPickerDialog` from "Choose image"/"Replace" (lead image), "Add block → Image" and the image block's "Replace"; dismiss changes nothing; remove `UploadBusy`, `UploadErrorText`, the `maxUploadSize` parameter and its pass-through in `App.kt`; verify by compiling and the headless check in 5.2
- [x] 3.4 Remove the single-file `pickImageFile()` expect and its wasmJs actual, and editor-only upload strings no longer referenced (e.g. `lead_image_uploading`); verify with `grep` that nothing references them and the build passes
- [x] 3.5 Replace "uploading" by "choosing images" wherever the field-explanation texts or KDoc still describe the editor as uploading; verify with `grep -i upload` over `ui/editor/`

## 4. Contract/Docs

- [x] 4.1 Confirm no REST change: `ai/primer/endpoints.md`, `http/` and the backend stay untouched (`git diff --stat` shows only `admin/` and `openspec/`)

## 5. Verification

- [x] 5.1 Run `cd admin && ./gradlew check`; all admin tests pass
- [x] 5.2 Drive the admin headless (Playwright) against a dev backend with a few uploaded images: pick a lead image, replace it (caption kept), insert an image block via "Add block → Image", replace its image, close the picker with Escape (nothing changes), filter by tag and "Unused" in the picker; check the autosaved article via the API; stop every server/container started
