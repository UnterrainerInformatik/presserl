## MODIFIED Requirements

### Requirement: Who may upload and read media
`POST /api/media`, `GET /api/media`, `GET /api/media/{id}`, `GET /api/media/{id}/usage`,
`GET /api/media/{id}/content` and `GET /api/media/{id}/renditions/{kind}` SHALL be available to
every user whose `allowedActions` contain `WRITE_ARTICLES`. Other authenticated users SHALL get
`403`, requests without a valid token `401` with an empty body. The `/api/media` endpoints SHALL NOT
be reachable without a token. Editing (`POST /api/media/{id}/edit`) follows its own rules
(capability `media-editing`). Readers SHALL only ever receive renditions of images used by
published articles (lead images and body images) through the reader route (capability
`reader-media`), never the stored master.

#### Scenario: Reporter uploads an image
- **WHEN** a user with the section role `REPORTER` in any section uploads a valid JPEG
- **THEN** the response is `201` with the new media's metadata and a `Location` header pointing to `/api/media/{id}`

#### Scenario: Reader-only user is refused
- **WHEN** a user whose only role is `READER` uploads an image or requests `/api/media/{id}/content` or `/api/media/{id}/renditions/web`
- **THEN** the response is `403` and nothing is stored

#### Scenario: No token
- **WHEN** a request to any `/api/media` endpoint carries no valid bearer token
- **THEN** the response is `401` with an empty body

### Requirement: Media list
`GET /api/media` SHALL list the newspaper's media for every user whose `allowedActions` contain
`WRITE_ARTICLES` (others `403`, no token `401`), newest upload first, each entry being the media
record plus `usageCount`, the number of distinct articles any of whose revisions uses the media as
lead image or in an image block of its body. The list SHALL be paged with the query parameters
`limit` (1–200, default 60) and `before` (a media id; only media with a smaller id are listed). The
response SHALL be `{"items": [...], "next": <id or null>}` where `next` is the `before` value for
the following page, `null` on the last page. An invalid `limit` or `before` SHALL answer `400`
naming the parameter.

#### Scenario: First page
- **WHEN** the newspaper has 75 media and a reporter requests `GET /api/media`
- **THEN** the response lists the 60 newest media, newest first, and `next` is the id of the 60th

#### Scenario: Last page
- **WHEN** the reporter requests `GET /api/media?before={next}`
- **THEN** the response lists the remaining 15 media and `next` is `null`

#### Scenario: Body use is counted
- **WHEN** media 18 is used in the body of article 5 (twice) and as lead image of article 8
- **THEN** its `usageCount` is `2`

#### Scenario: Invalid limit
- **WHEN** a writer requests `GET /api/media?limit=500`
- **THEN** the response is `400` for field `limit`

#### Scenario: Reader-only user
- **WHEN** a user whose only role is `READER` requests `GET /api/media`
- **THEN** the response is `403`

### Requirement: Media usage
`GET /api/media/{id}/usage` SHALL, for writers (`WRITE_ARTICLES`; others `403`, no token `401`,
unknown id `404`), return `mayEdit` (whether the caller may edit the media under the editing
rules) and `articles`: every article any of whose revisions uses the media as lead image or in an
image block of its body, most recently changed first, each with id, headline of its latest
revision, section (id, name, colour), author, status, pending level, published date (`null` if
never published), time of last change and where the media is used: `live` (the live revision uses
it), `latest` (the latest revision uses it) and `older` (only older revisions use it). Articles the
caller cannot see in the article list SHALL be included with the same fields, as the media is
shared across the newspaper.

#### Scenario: Image used live and in a new draft revision
- **WHEN** media 17 is the lead image of the live revision of published article 5 and of the latest revision of draft article 8
- **THEN** the usage lists article 5 with status `PUBLISHED`, its published date and `live` `true`, and article 8 with status `DRAFT`, published date `null` and `latest` `true`

#### Scenario: Image used in a body
- **WHEN** media 18 appears only in an image block of the latest revision of draft article 9
- **THEN** the usage lists article 9 with `latest` `true` and `live` `false`

#### Scenario: Image removed from an article
- **WHEN** article 5's lead image was changed from media 17 to media 20 in a later, not yet published revision
- **THEN** the usage of media 17 lists article 5 with `live` `true` and `latest` `false`

#### Scenario: Unused image
- **WHEN** a media is not used by any article
- **THEN** `articles` is empty
