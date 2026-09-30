## 1. Backend — body format and save

- [x] 1.1 Extend `ArticleBodyValidator` with the `image` block (`mediaId` positive integer, optional `caption` ≤ 300 chars without control characters, counted towards body text, unknown fields rejected); verify with unit tests for every scenario of "Body format version 1 with allowlist validation" (valid block, no caption, missing `mediaId`, line break/301-char caption, unknown field `src`).
- [x] 1.2 Extend `ArticleService.requireMedia` to check lead image and all body image `mediaId`s in one query, one error per unknown block (`body.blocks[i].mediaId`); verify with a `@QuarkusTest` for "Unknown media in the body" (400, nothing saved) and "Same image twice" (200).
- [x] 1.3 Add a `@QuarkusTest` for "New body image stays hidden until publication" (working revision carries the block, live revision unchanged) and verify it passes.

## 2. Backend — revision-to-media table

- [x] 2.1 Add migration `V13__article_revision_media.sql` (table, index, `plpgsql` trigger on insert/update of `lead_image_media_id`/`body`, backfill from lead images) as in design §2; verify the backend starts on an existing dev database and the table holds the lead-image rows.
- [x] 2.2 Add the read-only `ArticleRevisionMediaEntity`; verify with a `@QuarkusTest` that the table matches lead + body images after create, save in place, save as a new revision, removing an image block, and is empty after deleting the article.
- [x] 2.3 Switch `MediaService` list `usageCount`, `uses()` and `blockingUse()` to the table; verify with `@QuarkusTest`s for "Body use is counted" (`usageCount` 2), "Image used in a body" (usage `latest` true) and the edit-lock scenarios "Reporter's image is live in a body" and "Body image under review" (403), with the existing media tests still green.

## 3. Reader

- [x] 3.1 Switch `ReaderArticles.PUBLISHED_RENDITION` to the table; verify with `@QuarkusTest`s for "Published body image" (200), "Body image only in a draft" (404) and "Body image removed in a working revision" (still 200), existing reader-media tests green.
- [x] 3.2 Add `Type.IMAGE` to `BodyRenderer` and `ReaderArticles.bodyImages(...)` (versions + renditions of the body media, missing ones skipped); verify with unit tests for skip-on-missing and block order.
- [x] 3.3 Render the `IMAGE` branch in `tags/articleBody.html` (figure, `web` src + `srcset`, width/height, alt, `loading="lazy"`, escaped optional figcaption, `?v=`) and wire it into the article page; verify with `@QuarkusTest`s for "All block types", "Image block with caption", "Image block without caption", "Caption with markup", "Unpublished body image" and "Body image after an edit".
- [x] 3.4 Render body images with the `print` rendition in both print views (issue view resolves images once for all stories); verify with `@QuarkusTest`s for "Print an article with a body image" and "Issue print with a body image".
- [x] 3.5 Add `.article__figure` / `.article__caption` styles to `reader.css` (screen, dark mode unaffected, print: column width, `break-inside: avoid`); verify by viewing an article with a body image in the reader and its print preview in a headless browser (screenshot).

## 4. Admin

- [x] 4.1 Add `Block.Image(mediaId, caption = "")` to `Body.kt`; verify with `BodyTest` round-trips (caption omitted when empty, present otherwise, mixed with other blocks).
- [x] 4.2 Add `EditorBlock.Image`, `BlockType.IMAGE` and intents `AddImageBlock`, `SetBlockImage`, `SetImageCaption` (single-line, ≤ 300, typing-merged undo); key upload state by target (lead image, new block, block); verify with `EditorModelTest` for insert position, replace keeps caption, caption limits, undo/redo of insert and caption, move and remove.
- [x] 4.3 Extract the thumbnail preview from `LeadImageViews.kt` and build the image block view (preview, caption field, replace button, busy state, upload error messages) plus the "Image" entry in the "Add block" menu (file picker → upload → insert; cancel inserts nothing); verify with `LeadImageEditorTest`-style tests for success, cancel and `415`.
- [x] 4.4 Map save errors `body.blocks[i].*` to the image block at index `i`; verify with an `EditorModelTest`/autosaver test that a `400` naming `body.blocks[1].mediaId` shows at that block.
- [x] 4.5 Show image blocks (preview + caption) in the read-only article view and in `RevisionScreens`; verify with a test or UI check for "Earlier revision with a body image" and "Read-only article with an image block".
- [x] 4.6 Add `HelpPart.IMAGE` with de/en label and explanation, share the image-rights text with the lead image, add the image placeholder with caption to the sample body; verify with `FieldHelpTest` (part order, same sample, rights text on both image parts, English strings).

## 5. Contract / Docs

- [x] 5.1 Update `ai/primer/endpoints.md`: image block in body format v1 with error paths, media usage/`usageCount` and edit lock counting body images, reader route for body images; verify by reading the sections against the specs.
- [x] 5.2 Extend `http/articles.http` (save with an image block, unknown media → 400) and `http/media.http` (usage of a body image); verify by running them against a live dev backend.

## 6. Verification

- [x] 6.1 Run `cd backend && ./mvnw verify` and the admin tests (`cd admin && ./gradlew allTests` or the project's test task from `reference_build_and_test.md`); both green.
- [x] 6.2 End to end, headless (Playwright): as a reporter, insert an image after the first paragraph, type a caption, move it, publish via the publisher, open the article page and the print view and confirm the figure and caption appear at the right place; stop every server started for it.
- [x] 6.3 Run `openspec validate article-body-image-block --strict`; valid.
