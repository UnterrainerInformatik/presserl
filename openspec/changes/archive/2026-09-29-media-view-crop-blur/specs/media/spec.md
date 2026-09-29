## ADDED Requirements

### Requirement: Media list
`GET /api/media` SHALL list the newspaper's media for every user whose `allowedActions` contain
`WRITE_ARTICLES` (others `403`, no token `401`), newest upload first, each entry being the media
record plus `usageCount`, the number of distinct articles any of whose revisions uses the media as
lead image. The list SHALL be paged with the query parameters `limit` (1–200, default 60) and
`before` (a media id; only media with a smaller id are listed). The response SHALL be
`{"items": [...], "next": <id or null>}` where `next` is the `before` value for the following page,
`null` on the last page. An invalid `limit` or `before` SHALL answer `400` naming the parameter.

#### Scenario: First page
- **WHEN** the newspaper has 75 media and a reporter requests `GET /api/media`
- **THEN** the response lists the 60 newest media, newest first, and `next` is the id of the 60th

#### Scenario: Last page
- **WHEN** the reporter requests `GET /api/media?before={next}`
- **THEN** the response lists the remaining 15 media and `next` is `null`

#### Scenario: Invalid limit
- **WHEN** a writer requests `GET /api/media?limit=500`
- **THEN** the response is `400` for field `limit`

#### Scenario: Reader-only user
- **WHEN** a user whose only role is `READER` requests `GET /api/media`
- **THEN** the response is `403`

### Requirement: Media usage
`GET /api/media/{id}/usage` SHALL, for writers (`WRITE_ARTICLES`; others `403`, no token `401`,
unknown id `404`), return `mayEdit` (whether the caller may edit the media under the editing
rules) and `articles`: every article any of whose revisions uses the media as lead image, most
recently changed first, each with id, headline of its latest revision, section (id, name, colour),
author, status, pending level, published date (`null` if never published), time of last change
and where the media is used: `live` (the live revision uses it), `latest` (the latest revision
uses it) and `older` (only older revisions use it). Articles the caller cannot see in the article
list SHALL be included with the same fields, as the media is shared across the newspaper.

#### Scenario: Image used live and in a new draft revision
- **WHEN** media 17 is the lead image of the live revision of published article 5 and of the latest revision of draft article 8
- **THEN** the usage lists article 5 with status `PUBLISHED`, its published date and `live` `true`, and article 8 with status `DRAFT`, published date `null` and `latest` `true`

#### Scenario: Image removed from an article
- **WHEN** article 5's lead image was changed from media 17 to media 20 in a later, not yet published revision
- **THEN** the usage of media 17 lists article 5 with `live` `true` and `latest` `false`

#### Scenario: Unused image
- **WHEN** a media is not used by any article
- **THEN** `articles` is empty

## MODIFIED Requirements

### Requirement: Stored media and metadata
An accepted upload SHALL be stored durably and be described by a media record with: id, version,
content type of the stored image (`image/jpeg` or `image/png`), width and height in pixels, size of
the stored file in bytes, the uploader (username and display name at upload time), the upload time
and its renditions (per kind: width, height and size in bytes). A new upload SHALL have version
`0`; every edit increments it. `POST /api/media` and `GET /api/media/{id}` SHALL return this
record; an unknown id SHALL answer `404`. If storing the image or one of its renditions fails, the
upload SHALL fail with `503` and leave no media record behind.

#### Scenario: Metadata after upload
- **WHEN** a writer `papa` uploads a 1200 × 800 PNG without transparency
- **THEN** `GET /api/media/{id}` returns version `0`, content type `image/jpeg`, width `1200`, height `800`, the stored size, uploader `papa`, the upload time and the renditions `thumbnail` (480 × 320), `web` (1200 × 800) and `print` (1200 × 800)

#### Scenario: Unknown media
- **WHEN** a writer requests `GET /api/media/999999` and no such media exists
- **THEN** the response is `404`

#### Scenario: Object store unavailable
- **WHEN** the object store cannot be reached while a writer uploads a valid image
- **THEN** the response is `503` and no media record exists for the upload

### Requirement: Who may upload and read media
`POST /api/media`, `GET /api/media`, `GET /api/media/{id}`, `GET /api/media/{id}/usage`,
`GET /api/media/{id}/content` and `GET /api/media/{id}/renditions/{kind}` SHALL be available to
every user whose `allowedActions` contain `WRITE_ARTICLES`. Other authenticated users SHALL get
`403`, requests without a valid token `401` with an empty body. The `/api/media` endpoints SHALL NOT
be reachable without a token. Editing (`POST /api/media/{id}/edit`) follows its own rules
(capability `media-editing`). Readers SHALL only ever receive renditions of published lead images
through the reader route (capability `reader-media`), never the stored master.

#### Scenario: Reporter uploads an image
- **WHEN** a user with the section role `REPORTER` in any section uploads a valid JPEG
- **THEN** the response is `201` with the new media's metadata and a `Location` header pointing to `/api/media/{id}`

#### Scenario: Reader-only user is refused
- **WHEN** a user whose only role is `READER` uploads an image or requests `/api/media/{id}/content` or `/api/media/{id}/renditions/web`
- **THEN** the response is `403` and nothing is stored

#### Scenario: No token
- **WHEN** a request to any `/api/media` endpoint carries no valid bearer token
- **THEN** the response is `401` with an empty body

### Requirement: Reading the image back
`GET /api/media/{id}/content` SHALL return the stored image bytes with the stored content type,
`Content-Length`, `X-Content-Type-Options: nosniff`, `Content-Disposition: inline`,
`Cache-Control: private, no-cache` and `ETag: "{id}-{version}"` (an edit changes the image). A
request whose `If-None-Match` matches the current ETag SHALL answer `304` without a body. An
unknown id SHALL answer `404`.

#### Scenario: Download the re-encoded image
- **WHEN** a writer requests the content of a media uploaded as a PNG without transparency
- **THEN** the response is `200` with `Content-Type: image/jpeg`, `Cache-Control: private, no-cache`, an `ETag` and exactly the stored bytes

#### Scenario: Unchanged image is revalidated
- **WHEN** a writer requests the content of media 17 at version 0 with `If-None-Match: "17-0"`
- **THEN** the response is `304` without a body

#### Scenario: Edited image is fetched again
- **WHEN** media 17 has been edited to version 1 and a writer sends `If-None-Match: "17-0"`
- **THEN** the response is `200` with the edited bytes and `ETag: "17-1"`

### Requirement: Reading a rendition
`GET /api/media/{id}/renditions/{kind}` with `kind` one of `thumbnail`, `web`, `print` SHALL return
the rendition's bytes with the headers of `GET /api/media/{id}/content` (content type,
`Content-Length`, `X-Content-Type-Options: nosniff`, `Content-Disposition: inline`,
`Cache-Control: private, no-cache`, `ETag: "{id}-{version}"`) and SHALL answer `304` to a matching
`If-None-Match` like that endpoint. An unknown id or kind SHALL answer `404`; a media whose
renditions are not produced yet SHALL answer `404` as well.

#### Scenario: Thumbnail for the editor
- **WHEN** a writer requests `GET /api/media/{id}/renditions/thumbnail` of a 6000 × 4000 upload
- **THEN** the response is `200` with `Content-Type: image/jpeg` and a 480 × 320 image

#### Scenario: Unknown kind
- **WHEN** a writer requests `GET /api/media/{id}/renditions/huge`
- **THEN** the response is `404`
