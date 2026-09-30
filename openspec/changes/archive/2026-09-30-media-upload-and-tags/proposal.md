## Why

Images can only enter Presserl from inside an article (lead image or image block), so a photographer
or reporter cannot put photos into the newspaper's image stock ahead of a story. The images outlive
the issue they were first used in, yet nothing describes them, so once the grid holds a few hundred
tiles, finding "the fire brigade photo from spring" again means scrolling. Tags and a description,
searchable from the images view, make the stock reusable.

## What Changes

- Admin "Images" view gets **"Upload images"** (multi-select file picker) and **"Take photo"**
  (opens the device camera in mobile browsers via `capture`, the file picker elsewhere). The chosen
  files go through an upload dialog where tags and a description can be set for all of them; they
  are uploaded one by one with a per-file state and error, and appear at the top of the grid.
- Every media gets an optional **description** (free text: subject, photographer, credit) and a
  set of free **tags**. Tags are normalised (trimmed, inner whitespace collapsed) and matched
  case-insensitively; a tag the newspaper already uses keeps its existing spelling.
- `MediaDto` (and list items) carry `description` and `tags` (additive, not breaking).
- `POST /api/media` accepts optional form fields `description` and `tag` (repeatable), validated
  before anything is stored.
- New `PUT /api/media/{id}/details` sets description and tags; allowed for every `USE_MEDIA` user
  (metadata only, the image and its `version`/`ETag` stay unchanged).
- New `GET /api/media/tags?prefix=&limit=` returns the newspaper's tags with their usage count for
  autocompletion.
- `GET /api/media` gets filters: `tag` (repeatable, AND), `q` (words in the description),
  `unused=true` and `mine=true`; paging with `before` keeps working on the filtered list.
- Admin images view gets a **search bar** (tag chips with suggestions, description text, "Unused" and
  "My uploads" toggles) and the media detail shows and edits description and tags.

## Non-goals

- Choosing an existing image from the stock inside the article editor (lead image or image block).
  This is the natural follow-up; the search built here is meant to be reused there.
- A curated tag vocabulary, renaming/merging tags, or tag management screens.
- Deleting media from the images view.
- Separate structured credit/copyright fields or showing description/tags to readers.
- Native camera access on Android/iOS (the admin app ships the Wasm web target only).
- Full-text search engines; plain PostgreSQL queries suffice for a newspaper's stock.

## Capabilities

### New Capabilities
- `media-details`: description and tags of a media — validation and normalisation, setting them
  (`PUT /api/media/{id}/details`), tag suggestions (`GET /api/media/tags`).

### Modified Capabilities
- `media`: upload accepts `description`/`tag` fields; the media record carries description and tags;
  the media list gains the `tag`, `q`, `unused` and `mine` filters.
- `admin-media`: upload and "take photo" on the images view, the search bar on the grid, and
  showing/editing description and tags in the media detail.

## Impact

- **backend**: Flyway migration (`media.description`, `media_tag` table), `MediaService`,
  `MediaResource`, `MediaDto`/`MediaListItemDto`, new tag suggestion query, tests.
- **admin**: file picker (multiple files, camera capture), API client (upload fields, details, tags,
  list filters), `MediaGridModel` filters, upload dialog, detail editing, string resources, tests.
- **REST contract**: additive fields and two new endpoints → `ai/primer/endpoints.md` and
  `http/media.http` updated in the same change.
- **reader, deploy, docs**: not affected.
