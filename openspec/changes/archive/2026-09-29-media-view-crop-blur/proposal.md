## Why

Images reach the newspaper only through the lead-image field of the editor, and nobody can see
afterwards which images exist, who uploaded them or where they are used. Children photograph
friends, classmates, name tags and number plates; publishers (parents) need a way to review the
uploaded images and to make faces and other identifying details unrecognisable — also in articles
that are already live — and to fix a badly framed photo without asking the child to upload it again.

## What Changes

- New **media list** `GET /api/media` for writers: every media of the newspaper, newest first,
  paged, with uploader, upload time, size, dimensions and the number of articles using it.
- New **media usage** `GET /api/media/{id}/usage`: the articles whose revisions use the media as
  lead image — title, section, author, status, published date, whether the live revision, the
  working revision or a pending submission uses it — plus whether the caller may edit the media.
- New **media edit** `POST /api/media/{id}/edit`: crop to a rectangle and/or pixelate any number of
  ellipses, given in pixels of the stored image. The server applies the operations to the stored
  image, re-encodes it without metadata, regenerates all renditions and replaces the stored objects
  **under the same media id**, so every article using the image shows the edited version at once.
  The previous bytes are deleted; the pixelation is irreversible. Pixel block size is fixed by the
  server (coarse, not selectable).
- **Who may edit:** publishers and editors-in-chief every media; the uploader their own media only
  while no article's live revision and no pending submission uses it (so nobody changes approved or
  under-review content past the approval chain). Everybody else gets `403`.
- Media get a **version** that every edit increments. `MediaDto` carries it; an edit must name the
  version it was based on (`409` when someone else edited meanwhile).
- **Caching (BREAKING for cached clients only):** `/api/media/{id}/content` and
  `/api/media/{id}/renditions/{kind}` are no longer `immutable`; they answer
  `Cache-Control: private, no-cache` with an `ETag` and honour `If-None-Match`. Reader pages
  reference lead images as `/media/{id}/{kind}?v={version}` so an edited image is fetched fresh.
- **Admin app:** new header entry "Images" (for `WRITE_ARTICLES`) with a thumbnail grid of all
  media, a detail view (full-size preview, metadata, list of articles using the image with status
  and dates) and — where allowed — an edit view with a crop tool (free or the lead-image aspect
  ratios) and a pixelation tool (drag ellipses; move and remove before saving; undo before saving
  only), then save with a confirmation that the change is permanent and affects every article.

## Non-goals

- The photo-reporter role (a user without a section who uploads and manages images). It gets its
  own change; see `ai/open-proposals.md`.
- Deleting media, replacing an image with a different upload, choosing an existing media as lead
  image from the media view (picker), images inside the article body.
- Rotation, colour or brightness correction, drawing, text, other filters; selectable pixel size or
  blur instead of pixelation.
- Edit history or restoring an earlier version after saving.
- Purging copies already held by readers' browsers or proxies (the reader route keeps
  `max-age=3600`; new pages point to the new version at once).

## Capabilities

### New Capabilities
- `media-editing`: crop and pixelate a stored media server-side, replacing the image and its
  renditions under the same id; permissions, version check, pixelation strength.
- `admin-media`: the admin app's media view — grid, detail with usage, edit view with crop and
  pixelation tools.

### Modified Capabilities
- `media`: media list and usage endpoints; `version` in the media record; reading the image back
  and renditions switch from `immutable` to revalidated caching with `ETag`.
- `reader-media`: lead-image URLs in reader pages carry the media version.
- `admin-shell`: header entry "Images" added to the entries that follow `allowedActions`.

## Impact

- **Backend:** `media` package (list, usage, edit endpoints; `MediaProcessor` crop/pixelate;
  object replacement with cleanup), Flyway migration adding `media.version`, `MediaDto`,
  caching headers of `MediaResource`.
- **Reader:** `ReaderImage` and `tags/leadImage.html` add `?v={version}`; route unchanged.
- **Admin:** new `ui/media` screens (grid, detail, editor with crop/ellipse canvas), `ApiClient`
  and DTOs, `Navigation.kt` entry, German/English strings.
- **Contract/Docs:** `ai/primer/endpoints.md`, `http/` request files.
- **Deploy:** none (no new settings; RustFS and compose unchanged).
