## MODIFIED Requirements

### Requirement: Media grid
The "Images" entry SHALL open a grid of the newspaper's media (`GET /api/media` with the active
search filters), newest first, each tile showing the `thumbnail` rendition, the uploader's display
name, the upload date, how many articles use the image (`usageCount`; "not used" for `0`) and up to
three of its tags. Further pages SHALL load when the user scrolls to the end of the grid (using
`next` with the same filters). A media whose thumbnail is not produced yet SHALL show a placeholder.
Selecting a tile SHALL open the media detail. Above the grid the view SHALL offer "Upload images",
"Take photo" and the search bar. When no media matches active filters, the grid SHALL say so and
offer to clear the filters.

#### Scenario: Browse images
- **WHEN** a reporter opens "Images" in a newspaper with 75 media and no filter is active
- **THEN** the 60 newest are shown as tiles with thumbnail, uploader, date and usage count, and scrolling to the end loads the remaining 15

#### Scenario: Unused image
- **WHEN** a media is used by no article
- **THEN** its tile says it is not used

#### Scenario: Nothing matches
- **WHEN** the filter `Feuerwehr` + "Unused" matches no media
- **THEN** the grid says that no image matches and offers to clear the filters

### Requirement: Media detail with usage
The media detail SHALL show the image (its `web` rendition, scaled to fit), its size in pixels and
bytes, uploader, upload time, its description and tags, and the list of articles using it from
`GET /api/media/{id}/usage`: headline (or a placeholder), section with its colour marker, author,
status (with pending level when waiting), published date when published, and whether the image is
in the live version, the current working version or only in older versions. Selecting an article
SHALL open it in the editor (read-only when its `allowedActions` lack `EDIT`). Selecting a tag
SHALL return to the grid filtered by that tag alone. An "Edit" button (crop and pixelate) SHALL be
offered only when `mayEdit` is `true`; editing description and tags SHALL be offered to every user
of the view.

#### Scenario: Image used by a published article
- **WHEN** the user opens a media that is the lead image of the live version of published article "Our cat Minka", published on 27 September 2026
- **THEN** the detail lists that article with its section, author, status "Published", the date and the note that it is in the live version

#### Scenario: Reporter cannot edit a live image
- **WHEN** reporter `anna` opens her own media that is used live and the server answers `mayEdit` `false`
- **THEN** no "Edit" button is shown, but description and tags can still be edited

#### Scenario: Browse by tag
- **WHEN** the user selects the tag `Feuerwehr` in a media detail
- **THEN** the grid opens filtered by `Feuerwehr` only

## ADDED Requirements

### Requirement: Uploading images from the images view
"Upload images" SHALL open the device's file picker allowing several files at once (JPEG, PNG and
WebP offered; the server decides what it accepts). Cancelling the picker SHALL change nothing.
After choosing, an upload dialog SHALL list the chosen files by name and size and offer tag input
(with suggestions as in the search bar) and a description, both applied to every chosen file.
Starting the upload SHALL send the files one after another (`POST /api/media` with `file`,
`description` and `tag` parts), showing per file whether it is waiting, uploading, done or failed.
A failed file SHALL show the reason in the words the article editor uses for uploads (too large,
naming the newspaper's `media.max-size`; not a supported image type; damaged or too many pixels;
server not reachable; other refusals with their status) and SHALL NOT stop the remaining files;
failed files SHALL be retryable from the dialog. Every uploaded media SHALL appear at the top of the
grid with "not used", even when the active filters would exclude it. Closing the dialog while files
are still waiting SHALL ask before discarding them.

#### Scenario: Three photos with tags
- **WHEN** a reporter chooses three JPEGs, enters the tag `Sportfest` and the description `Foto: Anna` and starts the upload
- **THEN** the three files are uploaded one after another with that tag and description, each shows "done", and the three new images appear at the top of the grid as not used

#### Scenario: One file too large
- **WHEN** the newspaper's `media.max-size` is `10M` and the second of three chosen files is 12 MB
- **THEN** the second file shows that it is larger than 10M, the first and third are uploaded, and the second can be retried or removed

#### Scenario: Cancel the picker
- **WHEN** the user opens "Upload images" and closes the picker without choosing
- **THEN** no dialog opens and nothing is uploaded

### Requirement: Taking a photo
"Take photo" SHALL ask the device for a new photo from its (rear) camera where the browser supports
it and SHALL fall back to the file picker otherwise. The photo SHALL continue through the same
upload dialog as a chosen file.

#### Scenario: Phone browser
- **WHEN** a reporter selects "Take photo" in a mobile browser and takes a picture
- **THEN** the upload dialog opens with that photo, ready for tags and description

#### Scenario: Desktop browser
- **WHEN** a reporter selects "Take photo" in a desktop browser without camera capture support
- **THEN** the file picker opens

### Requirement: Search bar
The search bar SHALL offer: a tag input that adds a tag as a chip on Enter or when a suggestion is
chosen, with suggestions from `GET /api/media/tags?prefix=` while typing (the most used tags when
the field is focused and empty); a text field searching descriptions and tags (`q`); and the
toggles "Unused" and "My uploads". Every change SHALL reload the grid from its first page with all
active filters (`tag` per chip, `q`, `unused=true`, `mine=true`); typing in the text field SHALL
reload only after a short pause. Removing a chip or clearing all filters SHALL reload likewise. The
filters SHALL be kept while the user visits a media detail or edit view and returns.

#### Scenario: Filter by two tags and unused
- **WHEN** the user adds the chips `Feuerwehr` and `Einsatz` and switches on "Unused"
- **THEN** the grid shows only unused media carrying both tags, newest first

#### Scenario: Suggestion while typing
- **WHEN** the user types `feu` into the tag input
- **THEN** the existing tags starting with `feu` (or having a word starting with it) are suggested, most used first

#### Scenario: Filters survive the detail
- **WHEN** the user opens a media from a filtered grid and goes back
- **THEN** the grid shows the same filters and the same results

### Requirement: Editing description and tags
The media detail SHALL let every user of the view change description and tags (tag input with
suggestions as in the search bar) and save them with `PUT /api/media/{id}/details`. On success the
detail and the grid tile SHALL show the saved values (as normalised by the server). Errors SHALL be
shown in plain words and keep the unsaved input: invalid value (`400`, naming the field), not
allowed (`403`), server not reachable (`503` and network errors). Leaving the detail with unsaved
changes SHALL ask before discarding them.

#### Scenario: Add a tag in another spelling
- **WHEN** the newspaper uses `Feuerwehr` and the user adds `feuerwehr` to a media and saves
- **THEN** the detail and the tile show the tag `Feuerwehr`

#### Scenario: Leave with unsaved tags
- **WHEN** the user added a tag without saving and presses back
- **THEN** the app asks whether to discard the changes
