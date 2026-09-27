## 1. Backend — setup

- [x] 1.1 Add dependencies: Quarkiverse `quarkus-amazon-s3` + `url-connection-client` (version matching Quarkus 3.33), TwelveMonkeys `imageio-jpeg` and `imageio-webp`, `metadata-extractor`; disable the S3 LocalStack Dev Service
- [x] 1.2 Create `backend/compose-devservices.yml` with a pinned `rustfs` service; map its port to `presserl.media.s3.endpoint` and set `%dev,test` credentials; verify `quarkus:dev` and `@QuarkusTest` get a running RustFS (else apply the fallback from design decision 6 and record it)
- [x] 1.3 Introduce `MediaConfig` (`presserl.media.*`: `max-size` moved from `NewspaperConfig` without changing the key, `s3.endpoint|region|bucket|access-key|secret-key`, `max-concurrent-processing`); keep `EffectiveSettings` output unchanged; fail fast when `max-size` > `60M`
- [x] 1.4 Set `quarkus.http.limits.max-body-size=64M`, `java.awt.headless=true`; add the request filter that keeps `10M` for every path outside `/api/media`
- [x] 1.5 Flyway `V9__media.sql` and `MediaEntity`

## 2. Backend — processing pipeline

- [x] 2.1 `MediaProcessor`: magic-byte sniffing (JPEG, PNG, static WebP; animated WebP and all else → unsupported)
- [x] 2.2 Header-only dimension check (> 50 MP or side > 20000 px → refused) before any decode
- [x] 2.3 EXIF orientation read, decode, orientation applied, sRGB conversion, downscale to 4096 px long side
- [x] 2.4 Alpha detection and encoding (JPEG q 0.85 / PNG) without any metadata
- [x] 2.5 Unit tests with generated fixtures (`src/test/resources/media/`): each accepted type, misleading name/type, HTML-as-JPEG, GIF, HEIC header, animated WebP, bomb PNG (declared 30000×30000), truncated JPEG, EXIF GPS + camera + date (assert no Exif/XMP/IPTC directories in the output via metadata-extractor), orientation 6 → rotated dimensions, 6000×4000 → 4096×2731, transparent WebP → PNG with alpha, opaque PNG → JPEG

## 3. Backend — storage and API

- [x] 3.1 `MediaStore` (S3): put, get, delete, ensure bucket; key `media/<uuid>.<ext>`
- [x] 3.2 Bucket bootstrap on start with `Backoff` retries; `@Readiness` check for the object store
- [x] 3.3 `MediaService`: permission (`WRITE_ARTICLES` via `Newsroom`), size check against `media.max-size`, processing in `executeBlocking` behind the concurrency semaphore, put → insert, best-effort object delete on insert failure, S3 failures → `503`
- [x] 3.4 `MediaResource`: `POST /api/media` (multipart `file`, `201` + `Location`), `GET /api/media/{id}`, `GET /api/media/{id}/content` with the headers from the spec; `MediaDto`; exception mappers to `ApiErrorDto` (`400`/`403`/`404`/`413`/`415`/`503`)
- [x] 3.5 `@QuarkusTest` `MediaResourceTest`: reporter uploads (201, Location, metadata), reader-only 403, no token 401, missing/multiple/empty part 400, over limit 413 (and nothing stored: no row, no object), 415, bomb 400, unknown id 404, content round trip (bytes + headers), stored object has no EXIF
- [x] 3.6 Tests for readiness (store up → `UP`; store unreachable → `DOWN`) and bucket creation on an empty store
- [x] 3.7 Test that a non-media endpoint still refuses bodies > 10M with `413`

## 4. Admin

- [x] 4.1 `MediaDto` and `ApiClient.uploadMedia(bytes, fileName)` (Ktor multipart, part `file`), `media(id)`, `mediaContent(id)`
- [x] 4.2 Kotlin tests in `ApiClientTest` with a mock engine: multipart shape, bearer token, DTO decoding, error propagation

## 5. Deploy

- [x] 5.1 Pick and pin a current RustFS image tag; verify its env names, data path, health command and that it runs without published ports
- [x] 5.2 `deploy/compose.yaml`: `rustfs` service, `presserl-media` volume, `presserl` depends on healthy `rustfs`, `PRESSERL_MEDIA_S3_ENDPOINT` wiring; header comment updated
- [x] 5.3 `deploy/.env.example`: empty `PRESSERL_MEDIA_S3_ACCESS_KEY` / `PRESSERL_MEDIA_S3_SECRET_KEY` with comments; optional `PRESSERL_MEDIA_MAX_SIZE` example
- [x] 5.4 `deploy/INSTALL.md`: new values in "Fill in `.env`", a "Backups" section (PostgreSQL + `presserl-media`), external S3 alternative, "Updating" note for existing installations
- [x] 5.5 `../presserl-deployment`: same compose service/volume and the two `.env` values (secrets generated into its `.env`, never committed)

## 6. Contract and docs

- [x] 6.1 `ai/primer/endpoints.md`: new "Media" section with the three endpoints, `MediaDto`, headers, error table (incl. Quarkus' empty-body `413` above the HTTP ceiling)
- [x] 6.2 `http/media.http` with small fixture images (JPEG with EXIF GPS, PNG, HTML-as-JPEG) covering 201/403/415/404 and the content download
- [x] 6.3 `docs/architecture.md`: stack (RustFS/S3), configuration keys `presserl.media.*`, data model Media, API list, security checklist/backups; `docs/vision.md` only if wording conflicts
- [x] 6.4 Update `ai/memory/project_deployment_docker_compose.md` (compose now ships `presserl`, `postgres`, `rustfs`) and `reference_build_and_test.md` (how RustFS runs in dev/test)
- [x] 6.5 `ai/open-proposals.md`: reduce the M5 entry to renditions (`media-renditions`) and lead images/captions (`article-lead-image`)

## 7. Verification

- [x] 7.1 `cd backend && ./mvnw verify` green
- [x] 7.2 `cd admin && ./gradlew check` green
- [x] 7.3 Run `http/media.http` against `quarkus:dev`; confirm with `exiftool`/metadata-extractor that a downloaded image has no EXIF/GPS
- [x] 7.4 Build the image and run the local e2e compose (presserl + postgres + rustfs): healthy start, upload via `.http`, restart, media still readable
- [x] 7.5 `openspec validate media-upload --strict`; stop every server/container started during verification
