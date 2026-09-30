## MODIFIED Requirements

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
