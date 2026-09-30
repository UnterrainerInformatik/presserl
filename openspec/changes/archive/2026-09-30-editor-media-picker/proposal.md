## Why

Choosing a lead image or an image block in the editor opens the device's file dialog and uploads a
new file every time. In practice people rarely switch between writing and taking photos: they
either photograph (and upload in the **Images** view) or write. The writer should therefore choose
from the images the newspaper already has — including photos someone else took — instead of
uploading again, which also creates duplicates in the media library.

## What Changes

- New **media picker** in the admin editor: a dialog that shows the newspaper's images as a grid
  (thumbnail, uploader, date, usage count, tags) with the same search as the **Images** view (tags,
  text, "unused", "mine"), pages further on scroll, and returns the selected image. Cancelling
  returns nothing.
- **Lead image**: "Choose image" / "Replace" open the media picker instead of the file dialog; the
  picked media becomes the lead image (caption kept on replace). No upload happens in the editor.
- **Image block**: "Add block → Image" opens the media picker and inserts an image block with the
  picked media where the menu was opened; the block's "Replace image" opens the picker and keeps
  the caption.
- **Removed from the editor**: uploading from the device, its busy indicator and the upload error
  messages (too large, unsupported type, damaged, server not reachable). Uploading and "Take photo"
  stay where they already are — in the **Images** view — unchanged.
- An empty media library (or a filter matching nothing) says so in the picker and points to the
  **Images** view for uploading.

## Non-goals

- No change to the **Images** view, its upload dialog or "Take photo".
- No upload or camera shortcut inside the picker (deliberately: the editor chooses, the Images view
  adds).
- No prefilling of the caption from the media's description.
- No backend or REST contract change — the picker uses the existing `GET /api/media` list and
  `mediaRendition` thumbnails.
- No Android/iOS-specific picker (M8).

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `admin-articles`: new requirement "Media picker in the editor"; "Lead image in the editor" and
  "Image blocks in the editor" are replaced by "Lead image from the media library" and "Image
  blocks from the media library" (choose via the picker, no upload, upload error scenarios gone);
  "Field explanations for children" says "choosing images" instead of "uploading".

## Impact

- **admin**: new `MediaPickerDialog` (reusing `MediaGridModel`, `TagInput`, thumbnails and the tile
  layout of the media grid); `EditorScreen`/`EditorModel` lose the upload path (`UploadTarget`,
  `uploadImage`, `pickAndUpload`, `uploadingAt`/`uploadFailure`, `maxUploadSize` parameter);
  the single-file `pickImageFile` expect/actual is removed (only the editor used it); string
  resources for the picker added, editor upload strings removed; tests adjusted.
- **backend, reader, deploy, docs**: unchanged. `ai/primer/endpoints.md` unchanged (no REST change).
