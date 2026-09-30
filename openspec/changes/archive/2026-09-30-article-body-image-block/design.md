## Context

- The body is stored as `JSONB` in `article_revision.body` and validated by `ArticleBodyValidator`
  (allowlist per block type). The admin app models it in `Body.kt` (sealed `Block`, discriminator
  `type`) and edits it as `EditorBlock`s in `EditorModel`.
- The lead image is a pair of columns on `article_revision` (`lead_image_media_id` with
  `ON DELETE RESTRICT`, `lead_image_caption`). Every "is this media used?" rule queries that column
  directly:
  - `ReaderArticles.PUBLISHED_RENDITION` (reader route),
  - `MediaService` list `usageCount`, `uses()` (usage list) and `blockingUse()` (uploader's edit lock).
- The reader turns the body into typed blocks (`BodyRenderer`) and renders them in
  `tags/articleBody.html`; the article page and both print views share it. The lead image comes as a
  `ReaderImage` (renditions + media version) joined in `LIVE_PUBLISHED`.
- The "insert" menu of the open proposal is the editor's existing "+ Add block" dropdown
  (`AddBlockButton` in `EditorScreen.kt`), which lists `BlockType.entries`.

## Goals / Non-Goals

**Goals:**
- One place that answers "which media does revision (a, n) use?" for lead and body images, so the
  four use-based rules cannot drift apart.
- No change to the body format version and no rewrite of existing bodies.

**Non-Goals:**
- Changing the lead-image columns or `LeadImageDto`.
- Returning image dimensions inside the body (`GET` returns the body unchanged).

## Decisions

### 1. Block shape: `{"type": "image", "mediaId": 17, "caption": "..."}`

Mirrors `leadImage` (`mediaId`, `caption`) so the admin app and the primer describe one concept.
`caption` is optional; the admin omits it when empty (like `bold: false`), the backend stores the
body as sent (no trimming — body text is never trimmed today). Caption rules: string, ≤ 300
characters (`ArticleLimits.CAPTION_MAX`), no control character at all (same as the lead caption);
its length counts towards `BODY_TEXT_MAX`. `mediaId`: integral number > 0.

Format stays `version: 1`: the change is additive, old bodies stay valid, and only this backend
reads the format. *Alternative:* version 2 — rejected, it would force a migration of every stored
body for no reader-visible gain.

Error paths: `body.blocks[i].mediaId`, `body.blocks[i].caption`, unknown fields
`body.blocks[i].<name>` — all from the existing validator machinery. The existence check runs in
`ArticleService.requireMedia` (renamed scope: lead image + body images), after syntax validation,
with one query for the existing ids among the distinct ids used; every block (and the lead image)
whose media is unknown gets its own error entry.

### 2. Revision-to-media table maintained by a database trigger

New migration `V13__article_revision_media.sql`:

```sql
CREATE TABLE article_revision_media (
    article_id BIGINT NOT NULL,
    number     INT    NOT NULL,
    media_id   BIGINT NOT NULL REFERENCES media (id) ON DELETE RESTRICT,
    PRIMARY KEY (article_id, number, media_id),
    FOREIGN KEY (article_id, number) REFERENCES article_revision (article_id, number) ON DELETE CASCADE
);
CREATE INDEX article_revision_media_media_idx ON article_revision_media (media_id);
```

plus a `plpgsql` trigger `AFTER INSERT OR UPDATE OF lead_image_media_id, body ON article_revision`
that replaces the row set of `(article_id, number)` with the distinct ids of
`lead_image_media_id` and `jsonb_path_query(body, '$.blocks[*] ? (@.type == "image").mediaId')`.
The same migration backfills the table from existing revisions (only lead images can exist yet).

Why a trigger: the revision is written on two paths (`create` and `save`, which either updates the
latest or persists a new revision), and a missing call would silently open or close the reader
route. The trigger makes the table a pure function of the revision row, including the backfill.
*Alternatives:*
- Maintain the table from Java after each `apply(content)` — two code paths plus a mapped entity
  to keep consistent, and Hibernate Reactive flush ordering around the FK; rejected.
- Query the JSONB directly (`body @> '{"blocks":[{"type":"image","mediaId":17}]}'` with a GIN
  index) — needs native SQL in four places and gives no `RESTRICT` protection; rejected.

The lead-image column keeps its own `RESTRICT` FK; the new table adds the same protection for body
images.

### 3. Use-based queries switch to the table

A read-only entity `ArticleRevisionMediaEntity` (`@Immutable`, composite id) maps the table.
Replace `r.leadImageMediaId = :id` / `= m.id` with an `exists`/join over
`ArticleRevisionMediaEntity u where u.articleId = r.articleId and u.number = r.number and
u.mediaId = :id` in:
- `ReaderArticles.PUBLISHED_RENDITION` (reader route, spec reader-media),
- `MediaService` list count: `count(distinct u.articleId) from ArticleRevisionMediaEntity u where
  u.mediaId = m.id`,
- `MediaService.uses()`: select `u.articleId, u.number` from the table,
- `MediaService.blockingUse()`: join the table instead of the lead-image column.

`ArticleService.leadImage()` and `LIVE_PUBLISHED` stay on the lead-image columns (they need the
caption and the lead image specifically).

### 4. Reader: body images resolved per rendered article

`BodyRenderer` gains `Type.IMAGE` and a `Block.image` field (`ReaderImage`), and
`blocks(JsonNode body, Map<Long, ReaderImage> images)`; an image block whose id has no entry in the
map (media gone, renditions not produced) is skipped, like an unknown block today.

`ReaderArticles.bodyImages(List<JsonNode> bodies)` collects the distinct `mediaId`s of the image
blocks, loads `MediaEntity` versions and their renditions in two `in`-queries and builds
`ReaderImage`s with `ReaderImage.of(mediaId, caption, ...)` (caption per block, so the map holds
rendition data and the caption is taken from the block). The article page and both print views call
it before building blocks (the print issue view once for all its stories). The front page does not
render bodies and is unchanged.

Template (`tags/articleBody.html`) gets an `IMAGE` branch:

```html
<figure class="article__figure">
  <img src="/media/{id}/web?v={v}" srcset="…thumbnail?v={v} {tw}w, …web?v={v} {ww}w"
       sizes="(max-width: 48rem) 100vw, 48rem" width="{ww}" height="{wh}" alt="{caption}" loading="lazy">
  {#if caption}<figcaption class="article__caption">{caption}</figcaption>{/if}
</figure>
```

The print views pass a flag so the same tag emits the `print` rendition (`printKind`, which already
falls back to `web`). CSS in `reader.css`: `.article__figure` like `.lead-image` (full column width,
`img { width: 100%; height: auto }`), caption style shared with `.lead-image__caption`; in print
`break-inside: avoid` and `max-width: 100%` inside `.print-columns`.

### 5. Admin: upload first, then insert

`Block.Image(mediaId: Long, caption: String = "")` in `Body.kt` (default caption not encoded).
`EditorBlock.Image(id, mediaId, caption)` and `BlockType.IMAGE`. The "Add block" menu entry for
`IMAGE` does not dispatch `AddBlock` directly; it opens the file picker (the one the lead-image
field uses), uploads, and on success dispatches `AddImageBlock(afterId, mediaId)`. Intents
`SetBlockImage(blockId, mediaId)` (replace) and `SetImageCaption(blockId, value)` (typing-merged
like the lead caption for undo). Move/remove reuse the existing block intents, so undo/redo and
autosave need no special handling.

Why upload-before-insert: `mediaId` is required by the format, so an empty image block could not
be saved; inserting only after success keeps the draft always valid and makes "cancel" a no-op.
*Alternative:* an empty placeholder block excluded from the saved body — rejected, it creates a
draft state that differs from what the server holds and complicates undo.

Upload state: `EditorModel.uploading`/`uploadError` today describe the lead image only. They become
keyed by target (`UploadTarget.LeadImage`, `UploadTarget.NewBlock(afterId)`,
`UploadTarget.Block(blockId)`) so the busy indicator and error message appear where the user acted.
`uploadErrorOf` is reused unchanged.

Previews use `GET /api/media/{id}/renditions/thumbnail` like `LeadImageViews.kt`; the preview
composable is extracted so the lead-image field, the image block and the read-only views share it.
Save errors: the autosaver already maps field paths; `body.blocks[i].*` is mapped to the block at
index `i` of the saved body.

Field help: `HelpPart.IMAGE` (label "Bild"/"Image"), explanation plus the image-rights text shared
with `LEAD_IMAGE` (one string resource referenced from both). The sample article's body gets an
image placeholder with caption after the list, in reader order.

## Risks / Trade-offs

- [Trigger logic is invisible from Java] → Comment in the migration and on the entity; a
  `@QuarkusTest` asserts the table content after create, save-in-place, save-as-new-revision and
  delete; the reader-route and edit-lock tests exercise it end to end.
- [JSONB path with a non-numeric `mediaId` would break the trigger's cast] → The validator rejects
  such bodies before any write; the trigger casts via `(value #>> '{}')::bigint` only on validated
  data.
- [Many images make the article page heavy] → `loading="lazy"` and `srcset`; the 500-block limit
  caps the worst case.
- [Replacing an image leaves the old media unused but stored] → Same as the lead image today;
  unused media are visible as "not used" in the media grid.

## Migration Plan

Flyway migration `V13` runs on startup: creates table, index, trigger function and trigger, then
backfills from `lead_image_media_id`. Rollback of the application alone is safe (older code ignores
the table); a full rollback would need a manual `DROP` of trigger and table — not planned.
