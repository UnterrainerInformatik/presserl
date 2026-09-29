# admin-media Specification

## Purpose

The admin app's media view: browse every image of the newspaper, see where each one is used, and —
where the server allows it — crop it or pixelate faces and other details before saving it
permanently over the original.

## Requirements

### Requirement: Media grid
The "Images" entry SHALL open a grid of the newspaper's media (`GET /api/media`), newest first,
each tile showing the `thumbnail` rendition, the uploader's display name, the upload date and how
many articles use the image (`usageCount`; "not used" for `0`). Further pages SHALL load when the
user scrolls to the end of the grid (using `next`). A media whose thumbnail is not produced yet
SHALL show a placeholder. Selecting a tile SHALL open the media detail.

#### Scenario: Browse images
- **WHEN** a reporter opens "Images" in a newspaper with 75 media
- **THEN** the 60 newest are shown as tiles with thumbnail, uploader, date and usage count, and scrolling to the end loads the remaining 15

#### Scenario: Unused image
- **WHEN** a media is used by no article
- **THEN** its tile says it is not used

### Requirement: Media detail with usage
The media detail SHALL show the image (its `web` rendition, scaled to fit), its size in pixels and
bytes, uploader, upload time and the list of articles using it from `GET /api/media/{id}/usage`:
headline (or a placeholder), section with its colour marker, author, status (with pending level
when waiting), published date when published, and whether the image is in the live version, the
current working version or only in older versions. Selecting an article SHALL open it in the
editor (read-only when its `allowedActions` lack `EDIT`). An "Edit" button SHALL be offered only
when `mayEdit` is `true`.

#### Scenario: Image used by a published article
- **WHEN** the user opens a media that is the lead image of the live version of published article "Our cat Minka", published on 27 September 2026
- **THEN** the detail lists that article with its section, author, status "Published", the date and the note that it is in the live version

#### Scenario: Reporter cannot edit a live image
- **WHEN** reporter `anna` opens her own media that is used live and the server answers `mayEdit` `false`
- **THEN** no "Edit" button is shown

### Requirement: Crop tool
The edit view SHALL show the whole stored image (the `print` rendition as preview, mapped to the
stored image's pixels) and SHALL offer a crop rectangle that the user drags and resizes by its
corners and edges, with the aspect choices free, 3:2, 4:3, 16:9 and 1:1. The area outside the
rectangle SHALL be dimmed. The rectangle SHALL stay inside the image and SHALL not become smaller
than 16 stored pixels on a side. Resetting SHALL remove the crop.

#### Scenario: Crop to 3:2
- **WHEN** the user chooses 3:2 and drags the rectangle's corner
- **THEN** the rectangle keeps a 3:2 ratio and stays inside the image

### Requirement: Pixelation tool
The edit view SHALL let the user draw ellipses by dragging over the image; each ellipse SHALL
show a pixelated preview of the covered area. Ellipses SHALL be selectable, movable, resizable and
removable before saving; at most 50 are allowed. Undo and redo SHALL cover crop and ellipse changes
until the edit is saved; after saving there is no undo.

#### Scenario: Cover two faces
- **WHEN** the user drags two ellipses over two faces
- **THEN** both areas appear pixelated in the preview and can still be moved or removed

#### Scenario: Remove an ellipse
- **WHEN** the user selects an ellipse and removes it
- **THEN** the area appears unpixelated again

### Requirement: Saving an edit
Saving SHALL first ask for confirmation, stating that the change is permanent, cannot be undone
and changes the image in every article that uses it (naming how many, and how many of them are
published). After confirmation the app SHALL send `POST /api/media/{id}/edit` with the media's
version, the crop and the ellipses in stored-image pixels, and on success SHALL show the edited
image in the detail and in the grid. Errors SHALL be shown in plain words and keep the unsaved
edit: someone else changed the image meanwhile (`409`, offering to reload), not allowed (`403`),
invalid area (`400`), server not reachable (`503` and network errors). Leaving the edit view with
unsaved changes SHALL ask before discarding them.

#### Scenario: Save a pixelation
- **WHEN** a publisher confirms saving two ellipses on a media used by one published article
- **THEN** the edit is sent with the media's version, and afterwards the detail and the grid show the pixelated image

#### Scenario: Concurrent edit
- **WHEN** the server answers `409`
- **THEN** the app says someone else changed the image meanwhile, keeps the unsaved edit and offers to reload the image

#### Scenario: Leave without saving
- **WHEN** the user presses back with an unsaved crop
- **THEN** the app asks whether to discard the changes
