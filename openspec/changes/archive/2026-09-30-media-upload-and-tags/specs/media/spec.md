## MODIFIED Requirements

### Requirement: Who may upload and read media
`POST /api/media`, `GET /api/media`, `GET /api/media/tags`, `GET /api/media/{id}`,
`PUT /api/media/{id}/details`, `GET /api/media/{id}/usage`, `GET /api/media/{id}/content` and
`GET /api/media/{id}/renditions/{kind}` SHALL be available to every user whose `allowedActions`
contain `USE_MEDIA`: writers and sectionless reporters. Other authenticated users SHALL get `403`,
and requests without a valid token `401` with an empty body. The `/api/media` endpoints SHALL NOT be
reachable without a token. Editing (`POST /api/media/{id}/edit`) follows its own rules (capability
`media-editing`). Readers SHALL only ever receive renditions of images used by published articles
(lead images and body images) through the reader route (capability `reader-media`), never the
stored master, and never a media's description or tags.

#### Scenario: Reporter uploads an image
- **WHEN** a user with the section role `REPORTER` in any section uploads a valid JPEG
- **THEN** the response is `201` with the new media's metadata and a `Location` header pointing to `/api/media/{id}`

#### Scenario: Sectionless reporter uploads an image
- **WHEN** a user whose only role is the `sectionlessReporter` marker uploads a valid JPEG and then lists `GET /api/media`
- **THEN** the upload is answered with `201` and the list contains the new media

#### Scenario: Reader-only user is refused
- **WHEN** a user whose only role is `READER` uploads an image or requests `/api/media/{id}/content`, `/api/media/{id}/renditions/web` or `/api/media/tags`
- **THEN** the response is `403` and nothing is stored

#### Scenario: No token
- **WHEN** a request to any `/api/media` endpoint carries no valid bearer token
- **THEN** the response is `401` with an empty body

### Requirement: Upload request
`POST /api/media` SHALL accept `multipart/form-data` with exactly one file part named `file`.
A request without that part, with an empty file or with more than one file SHALL be refused with
`400` naming the field `file`. The request MAY additionally carry at most one text part
`description` and any number of text parts `tag`, which SHALL be validated and normalised like
`PUT /api/media/{id}/details` (capability `media-details`) before the image is processed; an invalid
value SHALL be refused with `400` naming `description`, `tags` or `tags[i]` (i counting the `tag`
parts from 0), and nothing SHALL be stored. Other parts SHALL be ignored.

#### Scenario: Missing file part
- **WHEN** a writer posts a multipart request without a `file` part
- **THEN** the response is `400` with an error for field `file` and nothing is stored

#### Scenario: Upload with tags and description
- **WHEN** a reporter uploads a valid JPEG with the parts `description` = `Einsatz am Dorfplatz, Foto: Anna` and `tag` = `Feuerwehr`, `tag` = `Einsatz`
- **THEN** the response is `201` and the media record has that description and the tags `Einsatz` and `Feuerwehr`

#### Scenario: Invalid tag on upload
- **WHEN** a reporter uploads a valid JPEG with a `tag` part of 41 characters
- **THEN** the response is `400` for field `tags[0]` and neither a media record nor a stored object exists afterwards

### Requirement: Stored media and metadata
An accepted upload SHALL be stored durably and be described by a media record with: id, version,
content type of the stored image (`image/jpeg` or `image/png`), width and height in pixels, size of
the stored file in bytes, the uploader (username and display name at upload time), the upload time,
its renditions (per kind: width, height and size in bytes), its `description` (`null` if none) and
its `tags` (sorted case-insensitively, `[]` if none). A new upload SHALL have version `0`; every
edit of the image increments it, setting description or tags does not. `POST /api/media`,
`GET /api/media/{id}` and `PUT /api/media/{id}/details` SHALL return this record; an unknown id
SHALL answer `404`. If storing the image or one of its renditions fails, the upload SHALL fail with
`503` and leave no media record behind.

#### Scenario: Metadata after upload
- **WHEN** a writer `papa` uploads a 1200 × 800 PNG without transparency and without description or tags
- **THEN** `GET /api/media/{id}` returns version `0`, content type `image/jpeg`, width `1200`, height `800`, the stored size, uploader `papa`, the upload time, the renditions `thumbnail` (480 × 320), `web` (1200 × 800) and `print` (1200 × 800), description `null` and tags `[]`

#### Scenario: Unknown media
- **WHEN** a writer requests `GET /api/media/999999` and no such media exists
- **THEN** the response is `404`

#### Scenario: Object store unavailable
- **WHEN** the object store cannot be reached while a writer uploads a valid image
- **THEN** the response is `503` and no media record exists for the upload

### Requirement: Media list
`GET /api/media` SHALL list the newspaper's media for every user whose `allowedActions` contain
`USE_MEDIA` (others `403`, no token `401`), newest upload first. Each entry SHALL be the media
record plus `usageCount`: the number of distinct articles any of whose revisions uses the media as
lead image or in an image block of its body. The list SHALL be paged with the query parameters
`limit` (1–200, default 60) and `before` (a media id; only media with a smaller id are listed). The
response SHALL be `{"items": [...], "next": <id or null>}`, where `next` is the `before` value for
the following page and `null` on the last page. An invalid `limit` or `before` SHALL answer `400`
naming the parameter.

The list SHALL accept these optional filters, combined with AND, and paging SHALL apply to the
filtered list:
- `tag` (repeatable): only media carrying every given tag (normalised and compared
  case-insensitively like stored tags); an unknown tag yields an empty list. More than 10 `tag`
  parameters or an empty one SHALL answer `400` for field `tag`.
- `q`: split into words at whitespace; only media where every word occurs case-insensitively in
  the description or in one of the tags. A blank `q` is ignored; more than 200 code points SHALL
  answer `400` for field `q`.
- `unused=true`: only media with `usageCount` `0`.
- `mine=true`: only media uploaded by the caller (same token `sub`).
`unused` and `mine` SHALL accept `true` and `false` only (`false` = no filter); other values SHALL
answer `400` naming the parameter.

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

#### Scenario: Two tags
- **WHEN** media 17 carries `Feuerwehr` and `Einsatz`, media 20 only `Feuerwehr`, and a reporter requests `GET /api/media?tag=feuerwehr&tag=Einsatz`
- **THEN** the list contains media 17 and not media 20

#### Scenario: Words in the description
- **WHEN** media 17 has the description `Einsatz am Dorfplatz, Foto: Anna` and a reporter requests `GET /api/media?q=dorfplatz%20anna`
- **THEN** the list contains media 17

#### Scenario: Own unused images
- **WHEN** reporter `anna` requests `GET /api/media?mine=true&unused=true`
- **THEN** the list contains exactly the media `anna` uploaded that no article uses, newest first

#### Scenario: Filtered paging
- **WHEN** 70 media carry the tag `Sport` and a reporter requests `GET /api/media?tag=Sport` and then the same filter with `before={next}`
- **THEN** the first page lists the 60 newest of them and the second the remaining 10 with `next` `null`

#### Scenario: Invalid flag
- **WHEN** a writer requests `GET /api/media?unused=yes`
- **THEN** the response is `400` for field `unused`
