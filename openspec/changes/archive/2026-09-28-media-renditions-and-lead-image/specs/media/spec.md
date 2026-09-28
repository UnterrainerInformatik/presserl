## MODIFIED Requirements

### Requirement: Who may upload and read media
`POST /api/media`, `GET /api/media/{id}`, `GET /api/media/{id}/content` and
`GET /api/media/{id}/renditions/{kind}` SHALL be available to every user whose `allowedActions`
contain `WRITE_ARTICLES`. Other authenticated users SHALL get `403`, requests without a valid
token `401` with an empty body. The `/api/media` endpoints SHALL NOT be reachable without a token.
Readers SHALL only ever receive renditions of published lead images through the reader route
(capability `reader-media`), never the stored master.

#### Scenario: Reporter uploads an image
- **WHEN** a user with the section role `REPORTER` in any section uploads a valid JPEG
- **THEN** the response is `201` with the new media's metadata and a `Location` header pointing to `/api/media/{id}`

#### Scenario: Reader-only user is refused
- **WHEN** a user whose only role is `READER` uploads an image or requests `/api/media/{id}/content` or `/api/media/{id}/renditions/web`
- **THEN** the response is `403` and nothing is stored

#### Scenario: No token
- **WHEN** a request to any `/api/media` endpoint carries no valid bearer token
- **THEN** the response is `401` with an empty body

### Requirement: Stored media and metadata
An accepted upload SHALL be stored durably and be described by a media record with: id, content
type of the stored image (`image/jpeg` or `image/png`), width and height in pixels, size of the
stored file in bytes, the uploader (username and display name at upload time), the upload time
and its renditions (per kind: width, height and size in bytes). `POST /api/media` and
`GET /api/media/{id}` SHALL return this record; an unknown id SHALL answer `404`. If storing the
image or one of its renditions fails, the upload SHALL fail with `503` and leave no media record
behind.

#### Scenario: Metadata after upload
- **WHEN** a writer `papa` uploads a 1200 × 800 PNG without transparency
- **THEN** `GET /api/media/{id}` returns content type `image/jpeg`, width `1200`, height `800`, the stored size, uploader `papa`, the upload time and the renditions `thumbnail` (480 × 320), `web` (1200 × 800) and `print` (1200 × 800)

#### Scenario: Unknown media
- **WHEN** a writer requests `GET /api/media/999999` and no such media exists
- **THEN** the response is `404`

#### Scenario: Object store unavailable
- **WHEN** the object store cannot be reached while a writer uploads a valid image
- **THEN** the response is `503` and no media record exists for the upload

## ADDED Requirements

### Requirement: Renditions
Every media SHALL have three renditions derived from its stored image: `thumbnail` with the longer
side at most 480 pixels, `web` at most 1600 pixels and `print` at most 3000 pixels, each keeping
the aspect ratio and never enlarging the image (a smaller image keeps its size). A rendition SHALL
have the content type of the stored image (JPEG, or PNG keeping the transparency) and SHALL
contain no metadata, like the stored image. Renditions SHALL be produced as part of the upload:
the upload response already lists them. Media stored before renditions existed SHALL receive
their renditions automatically after the backend starts, without blocking startup.

#### Scenario: Large photo
- **WHEN** a writer uploads a 6000 × 4000 JPEG
- **THEN** the media has the renditions `thumbnail` 480 × 320, `web` 1600 × 1067 and `print` 3000 × 2000, all `image/jpeg`

#### Scenario: Small image is not enlarged
- **WHEN** a writer uploads a 400 × 300 PNG without transparency
- **THEN** all three renditions are 400 × 300 JPEGs

#### Scenario: Transparent image
- **WHEN** a writer uploads a PNG with transparent pixels
- **THEN** every rendition is a PNG that keeps the transparency

#### Scenario: Media from before renditions
- **WHEN** the backend starts with a media record that has no renditions
- **THEN** shortly after startup that media has all three renditions, while the backend was ready without waiting for them

### Requirement: Reading a rendition
`GET /api/media/{id}/renditions/{kind}` with `kind` one of `thumbnail`, `web`, `print` SHALL return
the rendition's bytes with the headers of `GET /api/media/{id}/content` (content type,
`Content-Length`, `X-Content-Type-Options: nosniff`, `Content-Disposition: inline`,
`Cache-Control: private, max-age=31536000, immutable`). An unknown id or kind SHALL answer `404`;
a media whose renditions are not produced yet SHALL answer `404` as well.

#### Scenario: Thumbnail for the editor
- **WHEN** a writer requests `GET /api/media/{id}/renditions/thumbnail` of a 6000 × 4000 upload
- **THEN** the response is `200` with `Content-Type: image/jpeg` and a 480 × 320 image

#### Scenario: Unknown kind
- **WHEN** a writer requests `GET /api/media/{id}/renditions/huge`
- **THEN** the response is `404`
