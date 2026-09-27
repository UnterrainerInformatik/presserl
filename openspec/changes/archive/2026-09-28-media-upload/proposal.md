## Why

Children want to take pictures and put them into their newspaper (M5 — Images). Uploading a
photo straight from a phone must not leak where it was taken: the vision promises that every
uploaded image is re-encoded and stripped of EXIF/GPS data. This change lays the foundation —
a safe upload path and a place to keep the images — on which renditions and lead images build.

## What Changes

- New endpoint `POST /api/media` (multipart, one file) for everyone who may write articles
  (`WRITE_ARTICLES`). The server:
  - enforces the size limit `presserl.media.max-size` (default `10M`, already a resolved setting),
  - sniffs the real type from the file's bytes (JPEG, PNG, WebP accepted; the declared
    `Content-Type` and file name are ignored),
  - rejects decompression bombs by checking the pixel dimensions before decoding,
  - applies the EXIF orientation, then re-encodes the pixels into a fresh file (JPEG, or PNG when
    the image has transparency) with the long side capped — no metadata survives (EXIF, GPS,
    XMP, IPTC, ICC comments, thumbnails),
  - stores the re-encoded image in an S3-compatible object store and a `media` row in PostgreSQL.
- New endpoints `GET /api/media/{id}` (metadata) and `GET /api/media/{id}/content` (the image
  bytes) for users with `WRITE_ARTICLES`. Public delivery to readers comes with lead images.
- **Object storage: RustFS.** The reference deployment gains a `rustfs` service (S3-compatible,
  data on a named volume). The backend talks plain S3 (endpoint, bucket, access key, secret key),
  so an operator may point it at any S3-compatible store instead. The backend creates the bucket
  on start if it is missing and reports the store in its readiness check.
- Dev and test start RustFS automatically (Dev Services), like PostgreSQL and Keycloak today.
- Admin API client gains the three media calls and the `MediaDto`; no upload UI yet.
- `ai/open-proposals.md`: the M5 entry shrinks to what is still open (renditions, lead images,
  captions), naming the planned follow-up changes `media-renditions` and `article-lead-image`.

## Capabilities

### New Capabilities
- `media`: uploading images (who may, size and type limits, sniffing, re-encoding, metadata
  stripping), storing them in the object store and reading them back.

### Modified Capabilities
- `deployment`: the compose deployment runs `presserl`, `postgres` and `rustfs` (media on a named
  volume); `.env.example` gains the mandatory object-store credentials; `INSTALL.md` covers the
  media volume in backups.

## Non-goals

- Renditions (thumbnail / web / print) — follow-up change `media-renditions`.
- Lead images, captions, images in the article body, reader delivery of images — follow-up change
  `article-lead-image`.
- Upload UI in the admin app (comes with the lead image in the editor).
- Deleting media and cleaning up orphans (needs the article link first).
- HEIC/HEIF, GIF (incl. animation), SVG, video.
- Public or pre-signed object-store URLs; the store is never exposed to browsers.

## Impact

- **backend:** new `media` package (resource, service, image processor, object-store client),
  Flyway migration `V9__media.sql`, new dependencies (S3 client, WebP decoder, EXIF reader),
  configuration keys `presserl.media.*`, readiness check, HTTP body limit aligned with
  `presserl.media.max-size`, Compose Dev Service for RustFS in dev/test.
- **reader:** none.
- **admin:** API client methods and `MediaDto` plus tests; no UI.
- **deploy:** `deploy/compose.yaml` (`rustfs` service, `presserl-media` volume), `.env.example`,
  `INSTALL.md`; the fork `../presserl-deployment` follows (compose + `.env`).
- **docs:** `docs/architecture.md` (stack, configuration keys, data model, API, security
  checklist/backups), `ai/primer/endpoints.md`, `http/media.http`, memory
  `project_deployment_docker_compose.md` (compose no longer "presserl + postgres only").
