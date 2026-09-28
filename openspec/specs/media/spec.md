# media Specification

## Purpose

Lets newspaper staff upload images safely: every upload is limited in size and type, re-encoded
and stripped of all metadata (EXIF, GPS and the like) before it is stored and read back.

## Requirements

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

### Requirement: Upload request
`POST /api/media` SHALL accept `multipart/form-data` with exactly one file part named `file`.
A request without that part, with an empty file or with more than one file SHALL be refused with
`400` naming the field `file`.

#### Scenario: Missing file part
- **WHEN** a writer posts a multipart request without a `file` part
- **THEN** the response is `400` with an error for field `file` and nothing is stored

### Requirement: Size limit
The system SHALL refuse an upload whose file is larger than the effective setting
`media.max-size` (default `10M`) with `413`, without storing anything. Files up to and including
the limit SHALL be accepted as far as size is concerned.

#### Scenario: File above the limit
- **WHEN** `media.max-size` is `10M` and a writer uploads an 11 MiB JPEG
- **THEN** the response is `413` and neither a media record nor a stored object exists afterwards

#### Scenario: Smaller limit configured by the deployment
- **WHEN** `PRESSERL_MEDIA_MAX_SIZE=1M` is set and a writer uploads a 2 MiB PNG
- **THEN** the response is `413`

### Requirement: Type detection from content
The system SHALL determine the image type from the file's bytes only and SHALL ignore the
declared content type and the file name. Accepted input types SHALL be JPEG, PNG and WebP (lossy,
lossless, with or without transparency; not animated). Any other content — including GIF, HEIC,
SVG, PDF, animated WebP and files whose bytes do not match an accepted type — SHALL be refused with
`415` naming the field `file`.

#### Scenario: PNG uploaded with a misleading name
- **WHEN** a writer uploads a valid PNG named `photo.jpg` with the part content type `image/jpeg`
- **THEN** the upload is accepted and processed as a PNG

#### Scenario: Script disguised as an image
- **WHEN** a writer uploads an HTML file named `cat.jpg` with the part content type `image/jpeg`
- **THEN** the response is `415` with an error for field `file`

#### Scenario: HEIC photo
- **WHEN** a writer uploads a HEIC file
- **THEN** the response is `415`

### Requirement: Decoding safeguards
Before decoding pixels the system SHALL read the image dimensions and refuse images with more than
50 megapixels or a side longer than 20000 pixels with `400` naming the field `file`. A file of an
accepted type that cannot be decoded (truncated, corrupt) SHALL be refused with `400` naming the
field `file`. No refused upload SHALL leave a media record or stored object behind.

#### Scenario: Decompression bomb
- **WHEN** a writer uploads a 60 KiB PNG that declares 30000 × 30000 pixels
- **THEN** the response is `400` for field `file`, the pixels are never decoded and nothing is stored

#### Scenario: Truncated JPEG
- **WHEN** a writer uploads the first half of a valid JPEG
- **THEN** the response is `400` for field `file` and nothing is stored

### Requirement: Re-encoding without metadata
Every accepted upload SHALL be re-encoded from its decoded pixels; the original bytes SHALL never
be stored or served. Before re-encoding the system SHALL apply the EXIF orientation so the image
appears upright. The stored image SHALL be a JPEG when the image has no transparency and a PNG when
it has, in the sRGB colour space, with the longer side scaled down to at most 4096 pixels
(keeping the aspect ratio; smaller images are not enlarged). The stored file SHALL contain no
metadata: no EXIF (including GPS, camera, date, orientation), XMP, IPTC, comments or embedded
thumbnails.

#### Scenario: Phone photo with GPS position
- **WHEN** a writer uploads a JPEG with EXIF GPS coordinates, camera model and capture date
- **THEN** the stored image contains none of these data and no EXIF, XMP or IPTC segment at all

#### Scenario: Rotated phone photo
- **WHEN** a writer uploads a 4000 × 3000 JPEG whose EXIF orientation says "rotate 90° clockwise"
- **THEN** the stored image is 3000 × 4000 pixels, shows the subject upright and has no orientation tag

#### Scenario: Large image is scaled down
- **WHEN** a writer uploads a 6000 × 4000 JPEG
- **THEN** the stored image is a JPEG of 4096 × 2731 pixels

#### Scenario: Transparent WebP
- **WHEN** a writer uploads a WebP with an alpha channel
- **THEN** the stored image is a PNG that keeps the transparency

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

### Requirement: Reading the image back
`GET /api/media/{id}/content` SHALL return the stored image bytes with the stored content type,
`Content-Length`, `X-Content-Type-Options: nosniff`, `Content-Disposition: inline` and
`Cache-Control: private, max-age=31536000, immutable` (stored images never change). An unknown id
SHALL answer `404`.

#### Scenario: Download the re-encoded image
- **WHEN** a writer requests the content of a media uploaded as a PNG without transparency
- **THEN** the response is `200` with `Content-Type: image/jpeg` and exactly the stored bytes

### Requirement: Object store readiness
The backend SHALL create its media bucket on start when it does not exist and SHALL report the
object store in its readiness check: `/q/health/ready` SHALL be `DOWN` while the store or bucket is
unreachable.

#### Scenario: Fresh object store
- **WHEN** the backend starts against an empty RustFS
- **THEN** the media bucket exists afterwards and the readiness check reports the object store `UP`

#### Scenario: Object store down
- **WHEN** the object store stops while the backend is running
- **THEN** `/q/health/ready` answers `DOWN` with a check naming the object store

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
