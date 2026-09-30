## Context

The upload pipeline (`POST /api/media`, re-encoding, renditions, object store) and the paged list
(`GET /api/media`, keyset paging by id with a correlated `usageCount` subquery) exist; see the
`media` spec. The admin app already uploads from the article editor through
`pickImageFile()` (a hidden `<input type="file">` on the Wasm target) and `ApiClient.uploadMedia`,
and maps upload failures with `uploadErrorOf` to texts that name `media.max-size`. The images view
(`MediaGridModel`, `MediaScreens.kt`) is read-only apart from crop/pixelate. Media rows carry
`uploader_sub`, so "my uploads" needs no new data. Motivation: see proposal.md.

## Goals / Non-Goals

**Goals:**
- One additive REST extension that the article editor's future "choose from stock" dialog can
  reuse unchanged (list filters + tag suggestions).
- Tags that stay consistent without a management screen (spelling adoption, case-insensitive).
- Batch upload on phones without holding every chosen photo in memory at once.

**Non-Goals:**
- Search ranking, stemming, fuzzy matching or a search index; filters are exact/substring.
- Optimistic locking for description/tags (last write wins, see Risks).

## Decisions

### Data model: tags as rows of the media, no tag table
Migration `V16__media_details.sql`:
```sql
ALTER TABLE media ADD COLUMN description TEXT;
CREATE TABLE media_tag (
    media_id BIGINT NOT NULL REFERENCES media (id) ON DELETE CASCADE,
    name     TEXT   NOT NULL,   -- spelling as stored
    name_key TEXT   NOT NULL,   -- lower(name), the comparison key
    PRIMARY KEY (media_id, name_key)
);
CREATE INDEX media_tag_name_key ON media_tag (name_key text_pattern_ops);
```
A tag "exists" exactly while a row carries it, so unused tags vanish with no cleanup job and no
race between "delete orphan tag" and "attach tag". *Alternative:* a `tag` table plus join table —
cleaner normalisation, but needs orphan deletion that races with concurrent attaches; rejected for
a newspaper-sized stock. `name_key` is computed in Java (`toLowerCase(Locale.ROOT)` after
normalisation) so validation, adoption and filters use the same key.

**Spelling adoption:** when setting tags, for each key the service looks up
`select name from media_tag where name_key = :key and media_id <> :id limit 1` and stores that
spelling if found. Two simultaneous first uses of a new tag in different spellings can both win;
suggestions group by `name_key` and show the most frequent spelling, and the next save of either
media converges it. Accepted.

### Validation shared by upload and details
One `MediaDetailsValidator` (like `MediaEditValidator`) normalises and checks description (≤ 1000
code points, blank → `null`) and tags (≤ 20 after case-insensitive de-duplication, each 1–40 code
points, no control characters, no comma), collecting `FieldError`s (`description`, `tags`,
`tags[i]`). The upload validates its text parts **before** reading pixels, so a bad tag never costs
a decode or an object-store write. The media row and its tag rows are inserted in the existing
upload transaction.

### REST shapes
`MediaDto` and `MediaListItemDto` gain two fields (additive):
```json
{ "id": 17, "version": 0, "…": "…",
  "description": "Einsatz am Dorfplatz, Foto: Anna",
  "tags": ["Einsatz", "Feuerwehr"] }
```
`POST /api/media` — multipart, unchanged `file` part plus optional text parts:
```
--b  Content-Disposition: form-data; name="file"; filename="IMG_0412.jpg"   (bytes)
--b  Content-Disposition: form-data; name="description"                      Einsatz am Dorfplatz
--b  Content-Disposition: form-data; name="tag"                              Feuerwehr
--b  Content-Disposition: form-data; name="tag"                              Einsatz
```
Read with `@RestForm String description` and `@RestForm("tag") List<String> tags` next to the
existing `FileUpload.ALL` list (text parts have no filename, so they do not count as files).

`PUT /api/media/{id}/details`
```json
// request (both fields required, unknown fields → 400)
{ "description": "Einsatz am Dorfplatz, Foto: Anna", "tags": ["feuerwehr", "Einsatz"] }
// 200 → MediaDto with the stored (normalised, adopted, sorted) values
```
PUT with the whole state rather than PATCH or tag add/remove endpoints: the admin edits both fields
in one form, and a full replace is idempotent. It does not touch `version`, so image ETags and
reader `?v=` links stay valid.

`GET /api/media/tags?prefix=feu&limit=20`
```json
{ "items": [ { "name": "Feuerwehr", "count": 12 }, { "name": "Freiwillige Feuerwehr", "count": 3 } ] }
```
Query: `group by name_key`, `count(*)`, spelling = most frequent `name` in the group; prefix match
`name_key like :p || '%' or name_key like '% ' || :p || '%'` with `%`, `_` and `\` escaped in
`:p`. Wrapped in `items` for room to grow, like `MediaPageDto`. `@Path("/tags")` is a literal
segment and wins over `/{id}` in JAX-RS matching.

`GET /api/media?tag=Feuerwehr&tag=Einsatz&q=dorfplatz&unused=true&mine=true&before=63&limit=60`
— response unchanged (`{"items", "next"}`). Filters become `where` clauses in the existing query:
one `exists (select 1 from media_tag t where t.media_id = m.id and t.name_key = :tagN)` per tag;
per word of `q`: `(lower(m.description) like :wN escape '\' or exists (… t.name_key like :wN …))`;
`unused` reuses the usage subquery `= 0`; `mine` compares `uploader_sub` with the token `sub`.
Keyset paging by id is unaffected by filters. *Alternative:* PostgreSQL full-text search or
`pg_trgm` — unnecessary at thousands of rows and would add extension setup to deployments.

### Admin: picker returns lazy handles
New `expect suspend fun pickImageFiles(camera: Boolean): List<PickableFile>?` where
`PickableFile(name, size, suspend read(): ByteArray)`. The Wasm actual sets `multiple` (not with
`camera`) and, for the camera, `capture="environment"` with `accept="image/*"` — mobile browsers
open the camera, desktop browsers ignore `capture` and show the picker. Bytes are read from the
browser `File` only right before each upload, so 20 phone photos are never in Wasm memory together.
The existing single-file `pickImageFile()` for the editor stays.

### Admin: upload queue model
`MediaUploadModel` (common code, testable with fake API): list of entries
`(file, state = Waiting | Uploading | Done(media) | Failed(UploadError))`, shared tags/description,
`start()` uploads sequentially, `retry(entry)`, `remove(entry)`, `hasPending`. Each `Done` calls
`MediaGridModel.prepend(item with usageCount 0)`. Error mapping reuses `uploadErrorOf` and the
editor's texts (moved from `EditorScreen.kt` to a shared place in `ui/media`). Sequential rather than
parallel uploads: phone uplinks are the bottleneck, and ordering keeps the grid predictable.

### Admin: filters in the grid model
`MediaGridModel` gets a `MediaFilter(tags, q, unused, mine)` state; `setFilter` resets pages and
reloads; the loader signature becomes `(filter, before) -> MediaPage`. The text field debounces
(~400 ms) in the screen before calling `setFilter`. Because the grid model is already kept while
detail/edit are open, filters survive navigation without extra work. A shared `TagInput`
composable (chips + suggestion dropdown backed by `GET /api/media/tags`) is used by the search bar,
the upload dialog and the detail editor.

## Risks / Trade-offs

- [Last write wins on details: two people tagging the same image at once lose one set] → Low
  likelihood for metadata; the response returns the stored state, and adding a version check later
  is additive (`version` field in the PUT body).
- [iPhones deliver HEIC from some pickers; the server refuses HEIC with `415`] → With
  `accept="image/*"` Safari converts camera captures and library picks to JPEG; remaining HEIC
  files show the existing "not a supported image" text.
- [`like '%word%'` scans all media rows] → Fine for thousands of images; a trigram index can be
  added later without contract change.
- [Case folding with `Locale.ROOT` does not unify `ß`/`SS`] → Acceptable; spelling adoption makes
  real collisions rare.

## Migration Plan

`V16` is additive (nullable column, new table); existing media get `description` `null` and no
tags. Old admin clients ignore the new fields (`ignoreUnknownKeys`). Rollback = redeploy the previous
image; the extra column/table are ignored by it.
