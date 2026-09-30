## Why

An article can carry exactly one picture today, the lead image above the lead. Reports from a
school trip, a match or a class project usually need several photos placed next to the text they
belong to, so authors end up writing "see the photo" without being able to show it. The upload,
rendition and reader-route machinery exists since the lead image; the body format is the missing
piece.

## What Changes

- Body format v1 gains a fifth block type `image`: `{"type": "image", "mediaId": 17, "caption": "..."}`.
  It references an existing media like the lead image does; the caption follows the lead-image
  caption rules (plain text, single line, at most 300 characters, optional). Existing bodies stay
  valid; the format version stays `1` (the change is additive).
- Saving validates the block (syntax in the body validator, existence of the media in the service)
  and reports errors as `body.blocks[i].mediaId` / `body.blocks[i].caption`.
- The backend records which media every revision uses (lead image and body images) so that every
  rule built on "the media is used by a revision" treats body images like lead images:
  - the reader route `/media/{id}/{kind}` serves a rendition when the media is used by the live
    revision of a published article, as lead image **or in the body**;
  - `usageCount` in `GET /api/media` and the article list of `GET /api/media/{id}/usage` count
    body uses too;
  - the uploader's edit lock (live or pending use) covers body uses;
  - a media used in any revision's body cannot be deleted at database level, like a lead image.
- Reader: the article page renders an image block as a figure (`web` rendition, `srcset` with
  `thumbnail`/`web`, width/height, escaped caption, `?v={version}`); both print views render it
  with the `print` rendition.
- Admin editor: the "+ Add block" menu offers "Image". Choosing it opens the device's file picker,
  uploads the file and inserts an image block with preview (thumbnail rendition) and caption field;
  the block can be replaced, moved, removed and is covered by undo/redo and autosave. Upload errors
  are the ones of the lead-image field. The read-only views (read-only article, revision view) show
  image blocks as preview and caption.
- Children's field explanations: a new part "image" (question mark on the image block) with the
  image-rights hint of the lead image; the sample article gains an image in its body.

## Non-goals

- Picking an already uploaded image from the media grid (upload from the device only, as for the
  lead image today).
- Image layout options (width, float, alignment, galleries/side-by-side images) — an image block is
  always full column width.
- Showing body images on the front page or on issue pages (they show stories with lead images only).
- A separate limit on the number of images per article beyond the existing 500-block limit.
- Distinguishing lead-image and body use in the media usage response.
- The photo-reporter role (separate open proposal).

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `articles`: body format v1 allows the `image` block; saving checks that its media exists.
- `admin-articles`: the editor offers and edits image blocks; revision/read-only views show them;
  field explanations cover the image block.
- `reader-articles`: the article page renders image blocks as figures.
- `reader-print`: both print views render image blocks with the `print` rendition.
- `reader-media`: the reader route and versioned URLs cover body images of live revisions.
- `media`: `usageCount` and the usage list include body uses.
- `media-editing`: the uploader's edit lock includes body uses in live and pending revisions.

## Impact

- **backend**: `ArticleBodyValidator` (new block), `ArticleService` (media existence for body images,
  recording used media per revision), new Flyway migration with a revision-to-media table (backfilled
  from the lead images), `MediaService` usage/list/edit-lock queries, `ReaderArticles` reader-route
  query.
- **reader**: `BodyRenderer`, `ReaderArticles` (renditions and versions of body images of the live
  revision), `tags/articleBody.html`, reader CSS for body figures (screen and print).
- **admin**: `Body.kt` (new `Block.Image`), `EditorModel` (image block, per-block upload state,
  intents), `EditorScreen` (menu entry, image block view), `RevisionScreens` / read-only view,
  `HelpPart`/`FieldHelp` and string resources (de/en).
- **contract/docs**: `ai/primer/endpoints.md` (body format, media usage semantics), `.http` files.
- **deploy**: none (no new setting; fork themes may style `.article__figure`).
- **docs**: none beyond the primer.
