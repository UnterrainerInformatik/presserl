## Context

Today the editor's lead-image field, the "Add block → Image" menu entry and the image block's
"Replace" button all call `EditorModel.pickAndUpload(target, ::pickImageFile, api::uploadMedia)`:
single-file device picker, `POST /api/media`, then `SetLeadImage` / `AddImageBlock` /
`SetBlockImage`. `UploadTarget`, `uploadingAt` and `uploadFailure` exist only so the busy indicator
and the error text appear at the right field. The **Images** view already has "Upload images",
"Take photo", a search bar (`TagInput`, text, "Unused", "Mine") and a paged grid backed by
`MediaGridModel` over `GET /api/media`. `UploadError` / `uploadErrorOf` are declared in
`EditorModel.kt` but are also used by `MediaUploadModel` in the Images view.

## Goals / Non-Goals

**Goals:**
- One media picker composable used by all three places in the editor.
- The picker reuses the Images view's grid model, search inputs and tile look, so both stay alike.
- The editor model keeps its intents (`SetLeadImage`, `AddImageBlock`, `SetBlockImage`) unchanged,
  so undo/redo and autosave behave exactly as before.

**Non-Goals:**
- Sharing state between the picker and the Images view (each has its own grid and filters).
- Multi-select in the picker.

## Decisions

1. **Picker as a dialog over the editor, not a route.** The editor keeps its scroll position,
   focus and autosave timer; nothing about navigation or the "leave with unsaved changes" logic
   changes. Alternative — navigating to the Images view in a "choose" mode — would unmount the
   editor and need round-trip state; rejected.

2. **A fresh `MediaGridModel` per opening.** Created with `remember` inside the dialog, it starts
   with `MediaFilter()` every time (spec: filters start empty) and is dropped on close, so the
   Images view's grid and filters are never touched. Paging on scroll reuses the Images view's
   `snapshotFlow` pattern.

3. **Extract the shared pieces of `MediaGridScreen`.** The search block (tag input, text field with
   debounce, "Unused"/"Mine" chips, "Clear filters") and `MediaTile` become internal composables in
   the media package (`MediaSearchBar`, `MediaTile` made `internal`), used by both the Images view
   and the picker. The picker adds only its own title, close button and empty-library text
   pointing to "Images". Alternative — copying the code into the picker — rejected (two search UIs
   drifting apart).

4. **Picker API: `MediaPickerDialog(api, thumbnails, onPick: (MediaListItemDto) -> Unit,
   onDismiss: () -> Unit)`.** `MediaListItemDto` carries `id`, `width` and `height`, which is all
   `SetLeadImage` needs; no extra `GET /api/media/{id}`.

5. **Editor state: a nullable "picking for" slot instead of upload state.** `UploadTarget` is
   renamed to `ImageTarget` (same three cases: `LeadImage`, `NewBlock(afterId)`, `Block(blockId)`).
   `EditorScreen` holds `var picking by remember { mutableStateOf<ImageTarget?>(null) }`; the three
   buttons set it, the dialog is shown while it is non-null, and `onPick` calls a new
   `EditorModel.useImage(target, media)` that dispatches the matching existing intent.
   `uploadImage`, `uploadLeadImage`, `pickAndUpload`, `uploadingAt`, `uploadFailure`, `uploading`
   and `uploadErrorAt` are removed; so are `UploadBusy` and `UploadErrorText` in the editor and the
   `maxUploadSize` parameter of `EditorScreen` (and its computation in `App.kt` for the editor
   route). Buttons are enabled whenever the article is editable.

6. **`UploadError` / `uploadErrorOf` move to `ui/media/UploadErrors.kt`.** They stay for the Images
   view's upload dialog; only their home changes.

7. **Remove the single-file `pickImageFile()` expect/actual.** Only the editor used it; the Images
   view uses `pickImageFiles(camera)`. Dead platform code is not kept for M8 — the mobile targets
   will implement `pickImageFiles` anyway.

8. **Strings.** New resources (de + en) for the picker title, close, empty library ("No images yet —
   add them in \"Images\".") and reuse of the existing media search/filter strings. Editor-only
   upload strings that become unused (`lead_image_uploading` and upload-error texts used only by the
   editor) are removed; texts still used by the Images view stay. The "Choose image" / "Replace"
   labels stay.

## Risks / Trade-offs

- [A writer who wants a fresh photo must now switch to "Images" first] → Intended by the proposal;
  the empty-library text names the Images view. The editor autosaves, so leaving it loses nothing.
- [A large media library makes picking slow] → Same paging (60 per page) and search as the Images
  view; the "Mine" and "Unused" chips narrow quickly.
- [Picking an image the writer may not use (rights)] → Unchanged responsibility; the field
  explanations' image-rights note stays on the lead image and image block.
- [Removing `maxUploadSize` from the editor route changes `App.kt`] → Small, covered by compiling
  and the existing editor tests.

## Migration Plan

Admin-only UI change; no data or API migration. Ships with the next admin image; rollback is the
previous image.
