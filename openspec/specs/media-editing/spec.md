# media-editing Specification

## Purpose

Lets entitled staff crop a stored image and make areas of it unrecognisable by pixelation; the
edited image replaces the stored one and all its renditions under the same media id, irreversibly.

## Requirements

### Requirement: Edit request
`POST /api/media/{id}/edit` SHALL accept a JSON body with `version` (the media version the edit is
based on, required), an optional `crop` rectangle `{x, y, width, height}` and an optional list
`pixelate` of ellipses `{cx, cy, rx, ry}`, all integers in pixels of the stored image
(`x`/`y`/`cx`/`cy` from the top-left corner). At least one of `crop` and a non-empty `pixelate`
SHALL be given. The system SHALL refuse with `400` naming the offending field: a missing `version`;
a request with neither operation (`crop`); a crop that is not fully inside the image or smaller
than 16 pixels on a side (`crop`); more than 50 ellipses (`pixelate`); an ellipse with a radius
below 4 pixels or whose centre lies outside the image (`pixelate[i]`). An unknown id SHALL answer
`404`.

#### Scenario: Crop and pixelate together
- **WHEN** an editor-in-chief posts `{"version": 0, "crop": {"x": 100, "y": 50, "width": 1200, "height": 800}, "pixelate": [{"cx": 600, "cy": 400, "rx": 80, "ry": 110}]}` for a 1600 × 1067 media
- **THEN** the response is `200` with the media record: width `1200`, height `800`, version `1`

#### Scenario: Crop outside the image
- **WHEN** the crop of a 1600 × 1067 media is `{"x": 1000, "y": 0, "width": 800, "height": 500}`
- **THEN** the response is `400` for field `crop` and the media is unchanged

#### Scenario: Nothing to do
- **WHEN** the body is `{"version": 0, "pixelate": []}`
- **THEN** the response is `400` for field `crop` and the media is unchanged

### Requirement: Who may edit
Publishers and editors-in-chief SHALL be allowed to edit every media. The uploader of a media
(same token subject) SHALL be allowed to edit it only while no article's live revision uses it (as
lead image or in an image block of its body) and no article with a pending submission uses it in
its latest revision. Every other authenticated user SHALL get `403`, a request without a valid
token `401` with an empty body. A refused edit SHALL change nothing.

#### Scenario: Reporter edits their draft image
- **WHEN** reporter `anna` edits a media she uploaded that is only the lead image of her draft
- **THEN** the edit succeeds

#### Scenario: Reporter's image is live
- **WHEN** reporter `anna` edits a media she uploaded that is the lead image of a published article's live revision
- **THEN** the response is `403` and the media is unchanged

#### Scenario: Reporter's image is live in a body
- **WHEN** reporter `anna` edits a media she uploaded that is used in an image block of a published article's live body
- **THEN** the response is `403` and the media is unchanged

#### Scenario: Image under review
- **WHEN** reporter `anna` edits her media that is the lead image of the latest revision of an article waiting for approval
- **THEN** the response is `403`

#### Scenario: Body image under review
- **WHEN** reporter `anna` edits her media that is used in the body of the latest revision of an article waiting for approval
- **THEN** the response is `403`

#### Scenario: Someone else's image
- **WHEN** section editor `ben` edits a media uploaded by `anna`
- **THEN** the response is `403`

#### Scenario: Publisher pixelates a live image
- **WHEN** a publisher pixelates a face in the lead image of a published article
- **THEN** the edit succeeds

### Requirement: Concurrent edits
Every successful edit SHALL increment the media's version by one. An edit whose `version` differs
from the current version SHALL be refused with `409` and change nothing.

#### Scenario: Stale version
- **WHEN** two publishers both loaded media 17 at version 2 and the first saves an edit
- **THEN** the second edit with `version` `2` answers `409` and the media shows only the first edit

### Requirement: Applying the edit
The system SHALL apply the pixelation to the stored image first and then the crop, both in
coordinates of the stored image before the edit. Each ellipse SHALL be pixelated with square
blocks whose side is the larger of 12 pixels and one eighth of the ellipse's smaller diameter;
every pixel inside the ellipse SHALL take the average colour of its block (blocks aligned to the
ellipse's bounding box, clipped to the image), so the covered area cannot be restored from the
result. Parts of an ellipse outside the image SHALL be ignored. The result SHALL be re-encoded like
an upload (same format rules, no metadata, transparency kept) and SHALL get fresh `thumbnail`,
`web` and `print` renditions following the rendition rules.

#### Scenario: Pixelated area
- **WHEN** a publisher pixelates the ellipse `{"cx": 400, "cy": 300, "rx": 100, "ry": 60}`
- **THEN** inside that ellipse the stored image and every rendition consist of uniformly coloured 15 × 15 pixel blocks (scaled in the renditions), and pixels outside it are unchanged apart from re-encoding

#### Scenario: Small ellipse
- **WHEN** the ellipse has radii 20 × 20
- **THEN** its blocks are 12 × 12 pixels

#### Scenario: Transparent PNG stays PNG
- **WHEN** a media stored as PNG with transparency is cropped
- **THEN** the stored image and its renditions are PNGs that keep the transparency

### Requirement: Replacing under the same id
A successful edit SHALL keep the media id, uploader and upload time and SHALL replace content type,
width, height, size and renditions. Every article using the media SHALL show the edited image from
then on, in the admin app and in the reader, without any change to the articles or their
revisions. The previous image and renditions SHALL no longer be served by any route from the
moment the edit is answered, and SHALL be deleted from the object store; a deletion that fails
(object store unreachable, backend stopped) SHALL be retried later, also after a restart, until it
succeeds. When storing the edited image or a rendition fails, the edit SHALL
answer `503` and the media SHALL remain unchanged; objects already written for the failed edit
SHALL be removed.

#### Scenario: Live article shows the edit
- **WHEN** a publisher crops the lead image of a published article
- **THEN** the next request for that article's reader page links the lead image with the new version and `/media/{id}/web` returns the cropped rendition

#### Scenario: Old bytes are gone
- **WHEN** a media has been pixelated
- **THEN** neither `GET /api/media/{id}/content` nor any rendition route returns the unpixelated image, and the previous objects no longer exist in the object store

#### Scenario: Object store down during an edit
- **WHEN** the object store becomes unreachable while an edit is stored
- **THEN** the response is `503` and the media keeps its previous version, image and renditions
