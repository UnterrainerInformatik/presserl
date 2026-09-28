## ADDED Requirements

### Requirement: Lead image in the editor
The editor SHALL offer a lead-image field at every editor level: a button to choose an image file
from the device, which uploads it (`POST /api/media`) and sets it as the article's lead image; a
preview of the chosen image (its `thumbnail` rendition); a single-line caption field (at most 300
characters, no line breaks); and buttons to replace and to remove the image. While the upload runs
the field SHALL show that it is busy. The lead image and caption SHALL be saved by the autosave
like the other fields. An upload that fails SHALL leave the current lead image unchanged and show
a plain message: too large (`413`, naming the maximum size), not a supported image type (`415`),
image damaged or too big in pixels (`400`), or the server not reachable (`503` and network errors).

#### Scenario: Add a lead image
- **WHEN** the author chooses a JPEG photo in the lead-image field and types the caption `Our cat Minka`
- **THEN** the image is uploaded, its preview is shown and the next autosave sends `leadImage` with the new media id and the caption

#### Scenario: File too large
- **WHEN** the author chooses a file the server refuses with `413`
- **THEN** the editor says the image is too large, names the maximum size and keeps the previous lead image

#### Scenario: Unsupported file
- **WHEN** the author chooses a HEIC file and the server answers `415`
- **THEN** the editor says this kind of image is not supported and keeps the previous lead image

#### Scenario: Remove the lead image
- **WHEN** the author removes the lead image
- **THEN** the preview and caption field disappear and the next autosave sends `leadImage` `null`

#### Scenario: Reopen keeps the lead image
- **WHEN** the author reopens an article with a lead image and caption
- **THEN** the editor shows its preview and caption, and saving without changes sends the same `leadImage`

## MODIFIED Requirements

### Requirement: Revision history
The editor SHALL offer the article's revision history (`GET /api/articles/{id}/revisions`)
showing number, last change, publication time and which revision is live, and SHALL show a
selected revision read-only (`GET /api/articles/{id}/revisions/{n}`), including its lead image
preview and caption when it has one.

#### Scenario: View an earlier revision
- **WHEN** the author of an article with revisions `2` (working) and `1` (live) opens revision `1`
- **THEN** its content is shown read-only and marked as live

#### Scenario: Earlier revision with a different lead image
- **WHEN** revision `1` has lead image 17 and revision `2` has lead image 18, and the author opens revision `1`
- **THEN** the read-only view shows the preview of media 17 and revision `1`'s caption
