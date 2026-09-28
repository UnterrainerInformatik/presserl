## Purpose

Delivers images to readers: renditions of the lead images of published articles, under the same
visibility rules as the article pages, without ever exposing unpublished media or the master.

## ADDED Requirements

### Requirement: Reader route for lead images
The system SHALL serve `GET /media/{id}/{kind}` (outside `/api`, no token needed) with `kind` one
of `thumbnail`, `web`, `print`. It SHALL answer with the rendition's bytes only while media `{id}`
is the lead image of the live revision of at least one `PUBLISHED` article; for every other
media — unknown, only used in drafts, in unpublished revisions, in `OFFLINE` articles or in no
article at all — and for an unknown kind or a malformed id it SHALL answer `404` without revealing
whether the media exists. The stored master SHALL never be served by this route.

#### Scenario: Published lead image
- **WHEN** a visitor of a public newspaper requests `/media/{id}/web` for the lead image of a published article
- **THEN** the response is `200` with the `web` rendition's bytes and its content type

#### Scenario: Image only in a draft
- **WHEN** a visitor requests `/media/{id}/web` for a media that is only the lead image of a draft
- **THEN** the response is `404`

#### Scenario: Image in an unpublished revision
- **WHEN** a published article's live revision has no lead image and its newer working revision has lead image `{id}`
- **THEN** `/media/{id}/thumbnail` answers `404`

#### Scenario: Article taken offline
- **WHEN** the only article using media `{id}` as lead image is taken offline
- **THEN** `/media/{id}/web` answers `404` from then on

#### Scenario: Unknown kind or id
- **WHEN** a visitor requests `/media/{id}/original` or `/media/abc/web`
- **THEN** the response is `404`

### Requirement: Lead images of a private newspaper
When the effective `visibility` is `private`, `GET /media/{id}/{kind}` SHALL answer `404` to anonymous
visitors and to logged-in visitors without a newspaper role, and SHALL behave as for a public
newspaper for entitled readers (`READER`, `EDITOR_IN_CHIEF` or `PUBLISHER`).

#### Scenario: Anonymous visitor of a private newspaper
- **WHEN** the visibility is `private` and an anonymous visitor requests the `web` rendition of a published lead image
- **THEN** the response is `404` and contains no image

#### Scenario: Entitled reader
- **WHEN** the visibility is `private` and the logged-in reader `oma` requests the same URL
- **THEN** the response is `200` with the image

### Requirement: Reader image headers
A delivered rendition SHALL carry `Content-Type`, `Content-Length`, `X-Content-Type-Options: nosniff`
and `Cache-Control: public, max-age=3600` for a public newspaper or `private, max-age=3600` for a
private one, so that an image of an article taken offline disappears from caches within an hour.

#### Scenario: Caching in a public newspaper
- **WHEN** a visitor of a public newspaper receives a lead image
- **THEN** the response carries `Cache-Control: public, max-age=3600` and `X-Content-Type-Options: nosniff`

#### Scenario: Caching in a private newspaper
- **WHEN** an entitled reader of a private newspaper receives a lead image
- **THEN** the response carries `Cache-Control: private, max-age=3600`
