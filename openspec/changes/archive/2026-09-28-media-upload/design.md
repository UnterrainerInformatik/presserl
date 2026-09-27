## Context

See proposal.md for the motivation. Relevant current state:

- Backend: Quarkus 3.33, Quarkus REST, Hibernate Reactive Panache (reactive PG client), Flyway
  (last migration `V8__trust.sql`), JDK 21 in a Temurin 21 image.
- `presserl.media.max-size` (`10M`) is already a resolved, deployment-only setting
  (`NewspaperConfig.media().maxSize()`, exposed in the effective settings), but nothing uses it.
- `WRITE_ARTICLES` is computed in `Newsroom` (publisher, editor-in-chief, or any section role).
- The API error body is `ApiErrorDto` (`{"errors":[{"field","message"}]}`).
- Dev/test get PostgreSQL and Keycloak from Quarkus Dev Services; `deploy/compose.yaml` runs
  `presserl` + `postgres`.
- Gerald chose **RustFS** as the media store (S3-compatible, Apache-2.0, single container).

## Goals / Non-Goals

**Goals:**
- One safe processing pipeline every image passes through, reused later for renditions.
- Object storage behind a plain S3 interface so the store is replaceable by configuration.
- No request path where untrusted bytes reach the store or a client unprocessed.

**Non-Goals:**
- Streaming huge files, resumable uploads, client-side resizing.
- Serving media to readers or via pre-signed URLs (CSP stays `self`; the store stays private).

## Decisions

### 1. REST contract

`POST /api/media` — `multipart/form-data`, part `file`.

Response `201`, `Location: /api/media/17`:
```json
{ "id": 17, "contentType": "image/jpeg", "width": 4096, "height": 2731, "size": 1834211,
  "uploadedBy": { "username": "papa", "displayName": "Papa" },
  "uploadedAt": "2026-09-27T14:03:11.402Z" }
```
`GET /api/media/{id}` → `200` with the same `MediaDto`.
`GET /api/media/{id}/content` → `200`, body = stored bytes, headers `Content-Type`,
`Content-Length`, `Content-Disposition: inline`, `X-Content-Type-Options: nosniff`,
`Cache-Control: private, max-age=31536000, immutable`.

Errors (`ApiErrorDto`, `field` = `file` for validation, `null` otherwise):

| Status | When |
|---|---|
| `400` | no/empty/multiple `file` part; undecodable; > 50 MP or a side > 20000 px |
| `401` | no valid token (empty body, as everywhere) |
| `403` | no `WRITE_ARTICLES` |
| `404` | unknown id |
| `413` | file larger than `media.max-size` (also when the HTTP layer refuses the body) |
| `415` | type not JPEG / PNG / static WebP |
| `503` | object store unreachable during upload or download |

`uploadedBy` mirrors `AuthorDto` (username + display-name snapshot), matching article bylines.

### 2. Processing pipeline (`MediaProcessor`, pure Java, unit-testable without Quarkus)

1. **Sniff** the first bytes: JPEG `FF D8 FF`; PNG `89 50 4E 47 0D 0A 1A 0A`; WebP `RIFF????WEBP`
   with chunk `VP8 `/`VP8L`/`VP8X` — `VP8X` with the animation flag → `415`. Everything else → `415`.
   Declared content type and file name are never read.
2. **Dimensions first:** open an `ImageReader` for the sniffed type, read `getWidth(0)`/
   `getHeight(0)` from the header only; > 50 MP or a side > 20000 px → `400`. This bounds heap use
   (50 MP × 4 bytes ≈ 200 MB worst case, decoded one image at a time — see risk below).
3. **Orientation:** read EXIF orientation (JPEG, WebP) with `metadata-extractor`; missing/invalid → 1.
4. **Decode** with ImageIO + TwelveMonkeys (`imageio-jpeg` for CMYK/ICC-correct JPEG decoding into
   sRGB, `imageio-webp` for WebP). Any decode exception → `400`.
5. **Transform:** apply the orientation (rotate/flip), convert to sRGB `TYPE_INT_RGB` or
   `TYPE_INT_ARGB`; scale the longer side down to 4096 px with stepwise halving + bilinear
   (good quality without an extra library; `Math.round` for the other side → 6000×4000 ⇒ 4096×2731).
6. **Alpha check:** PNG/WebP with an alpha channel that contains at least one non-opaque pixel ⇒
   PNG output; otherwise JPEG, quality 0.85, baseline, 4:2:0.
7. **Encode** via ImageIO writers with *no* input metadata passed on (`IIOImage(img, null, null)`).
   The JPEG writer adds only the JFIF APP0 header; no EXIF/XMP/IPTC/ICC/comment is written.

Alternatives: `libvips`/ImageMagick via CLI (faster, HEIC support, but native binaries in the
image and a much larger attack surface); Thumbnailator (fine, but scaling is the only part we'd use).
Re-encoding from pixels is the metadata guarantee — we do not "strip" segments from the original.

### 3. Object store: S3 API against RustFS

- Client: Quarkiverse `quarkus-amazon-s3` with the **URL-connection (sync) client**, path-style
  access, static credentials, `endpoint-override` = `presserl.media.s3.endpoint`. Its LocalStack
  Dev Service is disabled.
- Configuration (`MediaConfig`, `@ConfigMapping(prefix = "presserl.media")`, next to the existing
  `max-size` which moves there from `NewspaperConfig` without changing the key):

  | Key | Default | Env |
  |---|---|---|
  | `presserl.media.s3.endpoint` | `http://rustfs:9000` | `PRESSERL_MEDIA_S3_ENDPOINT` |
  | `presserl.media.s3.region` | `us-east-1` | `PRESSERL_MEDIA_S3_REGION` |
  | `presserl.media.s3.bucket` | `presserl-media` | `PRESSERL_MEDIA_S3_BUCKET` |
  | `presserl.media.s3.access-key` | — (mandatory in prod) | `PRESSERL_MEDIA_S3_ACCESS_KEY` |
  | `presserl.media.s3.secret-key` | — (mandatory in prod) | `PRESSERL_MEDIA_S3_SECRET_KEY` |

- Object key `media/<uuid>.<jpg|png>` (random UUID, never derived from user input); objects are
  written once and never overwritten.
- Bucket: created on startup if missing (`HeadBucket` → `CreateBucket`), retried with the existing
  `Backoff` like the publisher bootstrap so a slow `rustfs` start does not kill the backend.
- Readiness: `MediaStoreHealthCheck` (`@Readiness`) does `HeadBucket` with a short timeout.

Alternatives: storing bytes in PostgreSQL (`bytea`) — one fewer service, but bloats the DB and its
backups and does not fit the planned renditions; local volume — simplest, but Gerald wants RustFS,
and S3 keeps later options (external S3, CDN) open. MinIO client library — S3-generic is enough.

### 4. Threading with Hibernate Reactive

Resource methods stay non-blocking. Decoding/encoding and the sync S3 calls run in
`vertx.executeBlocking(...)` (returns on the caller's duplicated context, so the subsequent
Panache call stays on the right Vert.x context). Upload order:
**process → put object → insert row** (`@WithTransaction`). If the insert fails, delete the object
best-effort (log on failure); an orphan object is harmless, a row without an object is not.
An S3 `SdkClientException`/`S3Exception` maps to `503`.

### 5. Size limits across layers

- Quarkus stores multipart files in its uploads directory (not in heap) and applies
  `quarkus.http.limits.max-body-size`. We set that global ceiling to `64M` and fail fast at start
  if `presserl.media.max-size` > `60M`.
- The resource checks the stored file size against the parsed `media.max-size` (`MemorySize`) → `413`
  with `ApiErrorDto`. A request above the HTTP ceiling gets Quarkus' own `413` (empty body) —
  documented in the primer.
- To keep the old 10 MB ceiling for all other endpoints, a `@ServerRequestFilter` rejects
  requests outside `/api/media` whose `Content-Length` exceeds `10M` with `413`.

### 6. Dev and test: RustFS via Compose Dev Services

`backend/compose-devservices.yml` with one `rustfs` service (pinned tag, fixed dev credentials);
Quarkus starts it in dev and test like PostgreSQL/Keycloak and maps its port into
`presserl.media.s3.endpoint` via compose Dev Service labels. `%dev,test` set the credentials.
Fallback if the label mapping does not cover our key: a `QuarkusTestResourceLifecycleManager`
starting `rustfs` with Testcontainers for tests, and the same compose file started by hand for dev
(recorded in `reference_build_and_test.md`).

### 7. Deployment

`deploy/compose.yaml` gains:
```yaml
  rustfs:
    image: rustfs/rustfs:<pinned version>
    restart: unless-stopped
    environment:
      RUSTFS_ACCESS_KEY: ${PRESSERL_MEDIA_S3_ACCESS_KEY:?...}
      RUSTFS_SECRET_KEY: ${PRESSERL_MEDIA_S3_SECRET_KEY:?...}
    volumes:
      - presserl-media:/data
    healthcheck: ...   # verified against the image during apply
```
no `ports:`; `presserl` gets `depends_on: rustfs: service_healthy`. The exact env names, data path
and health command are checked against the pinned RustFS image during implementation. The fork
`../presserl-deployment` gets the same service and the two new `.env` values.

## Risks / Trade-offs

- [Parallel uploads of large images exhaust heap] → pixel cap (50 MP) and a small semaphore
  (`presserl.media.max-concurrent-processing`, default 2) around decoding; excess waits.
- [RustFS is young; breaking changes or bugs] → pinned image tag, only the S3 API is used, so a
  switch to another S3 store is a configuration change; media volume in backups.
- [Image decoder vulnerabilities (malicious files)] → type allowlist by magic bytes, dimension check
  before decoding, maintained decoders (JDK + TwelveMonkeys), nothing of the original is served.
- [JPEG re-encoding loses a little quality] → quality 0.85 at ≤ 4096 px is visually fine for web
  and print; renditions derive from this master.
- [Headless AWT in the container] → `java.awt.headless=true`; the image build + an upload against
  the built image is part of verification.
- [Object written but row insert fails → orphan object] → best-effort delete; orphan cleanup comes
  with media deletion later.
- [Compose Dev Services label mapping may not fit] → fallback described in decision 6.

## Migration Plan

- `V9__media.sql` creates `media` (id `bigserial`, `object_key` unique, `content_type`, `width`,
  `height`, `byte_size`, `uploader_sub`, `uploader_username`, `uploader_display_name`,
  `created_at`). Additive; no data migration.
- Operators must add `PRESSERL_MEDIA_S3_ACCESS_KEY`/`SECRET_KEY` to `.env` and pull the new compose
  file before updating the image; `INSTALL.md` "Updating" says so. Without them the backend fails
  at start with a clear config error.
- Rollback: previous image tag; the extra table and `rustfs` service are harmless to the old version.
