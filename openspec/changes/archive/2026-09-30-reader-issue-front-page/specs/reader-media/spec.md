## MODIFIED Requirements

### Requirement: Reader route for lead images
The system SHALL serve `GET /media/{id}/{kind}` (outside `/api`, no token needed) with `kind` one
of `thumbnail`, `web`, `print`. It SHALL answer with the rendition's bytes only while media `{id}`
is used by the live revision of at least one article visible to readers (status `PUBLISHED` in a
published issue), as its lead image or in an image block of its body; for every other media —
unknown, only used in drafts, in unpublished revisions, in `OFFLINE` articles, in articles of issues
that are not published or of no issue, or in no article at all — and for an unknown kind or a
malformed id it SHALL answer `404` without revealing whether the media exists. The stored master
SHALL never be served by this route.

#### Scenario: Published lead image
- **WHEN** a visitor of a public newspaper requests `/media/{id}/web` for the lead image of a published article in a published issue
- **THEN** the response is `200` with the `web` rendition's bytes and its content type

#### Scenario: Published body image
- **WHEN** a visitor of a public newspaper requests `/media/{id}/print` for a media used only in an image block of the live body of a published article in a published issue
- **THEN** the response is `200` with the `print` rendition's bytes

#### Scenario: Image only in a draft
- **WHEN** a visitor requests `/media/{id}/web` for a media that is only the lead image of a draft
- **THEN** the response is `404`

#### Scenario: Body image only in a draft
- **WHEN** a visitor requests `/media/{id}/web` for a media used only in the body of a draft
- **THEN** the response is `404`

#### Scenario: Image in an unpublished revision
- **WHEN** a published article's live revision has no lead image and its newer working revision has lead image `{id}`
- **THEN** `/media/{id}/thumbnail` answers `404`

#### Scenario: Body image removed in a working revision
- **WHEN** a published article's live body contains media `{id}` and its newer working revision removed that image block
- **THEN** `/media/{id}/web` still answers `200` until the working revision is published

#### Scenario: Article taken offline
- **WHEN** the only article using media `{id}` as lead image is taken offline
- **THEN** `/media/{id}/web` answers `404` from then on

#### Scenario: Image of an article waiting for its issue
- **WHEN** the only article using media `{id}` is `PUBLISHED` in an issue that is not published
- **THEN** `/media/{id}/web` answers `404` until the issue is published

#### Scenario: Unknown kind or id
- **WHEN** a visitor requests `/media/{id}/original` or `/media/abc/web`
- **THEN** the response is `404`
