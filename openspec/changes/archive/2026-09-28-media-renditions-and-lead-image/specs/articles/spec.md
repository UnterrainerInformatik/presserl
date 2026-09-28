## MODIFIED Requirements

### Requirement: Article content fields
An article revision SHALL consist of `kicker`, `headline`, `subheadline` and `lead` as plain
text, `body` as a structured document and an optional lead image (`leadImage`). Text fields SHALL
default to an empty string, SHALL be stored trimmed and SHALL NOT contain any control character
(including line breaks); `kicker`, `headline` and `subheadline` SHALL be at most 200 characters
and `lead` at most 1000. A missing `body` SHALL mean an empty document
(`{"version":1,"blocks":[]}`). A missing or `null` `leadImage` SHALL mean the revision has no lead
image. A draft MAY have an empty headline.

#### Scenario: Create without content
- **WHEN** the publisher calls `POST /api/articles` with `{}`
- **THEN** the response is `201` with all text fields empty, an empty body and `leadImage` `null`

#### Scenario: Headline too long
- **WHEN** an article is saved with a headline of 201 characters
- **THEN** the response is `400` naming the field `headline`

#### Scenario: Line break in a headline
- **WHEN** an article is saved with the headline `Hello\nWorld`
- **THEN** the response is `400` naming the field `headline`

### Requirement: Working revision and live revision
Each article SHALL have numbered revisions starting at `1`. Saving an article (`PUT`) SHALL
overwrite its latest revision while that revision has never been published; if the latest
revision has been published, saving SHALL create a new revision numbered one higher — unless the
saved content (kicker, headline, subheadline, lead, body and lead image with caption) equals the
latest revision's content, in which case no revision SHALL be created or changed. Publishing
SHALL make the latest revision the article's live revision and mark it as published. The live
revision SHALL remain unchanged by later saves until the next publish.

#### Scenario: Autosave on a draft
- **WHEN** the author saves a draft article three times
- **THEN** the article still has only revision `1`, containing the last saved content

#### Scenario: Editing a published article
- **WHEN** the author saves a published article whose live revision is `1`
- **THEN** a revision `2` with the new content exists, `liveRevision` stays `1` and `hasUnpublishedChanges` is `true`

#### Scenario: Saving a published article unchanged
- **WHEN** the author saves a published article whose live revision is `1` with exactly its current content
- **THEN** the article still has only revision `1` and `hasUnpublishedChanges` is `false`

#### Scenario: Changing only the caption of a published article
- **WHEN** the author saves a published article whose live revision is `1` with only the lead image's caption changed
- **THEN** a revision `2` exists with the new caption, `liveRevision` stays `1` and `hasUnpublishedChanges` is `true`

#### Scenario: Republishing
- **WHEN** the publisher-author publishes that article again
- **THEN** `liveRevision` is `2` and `hasUnpublishedChanges` is `false`

## ADDED Requirements

### Requirement: Lead image with caption
`POST /api/articles` and `PUT /api/articles/{id}` SHALL accept `leadImage` as `null` or as an
object with exactly the fields `mediaId` (required, id of an existing media) and `caption`
(optional plain text, default empty, stored trimmed, no control characters, at most 300
characters). A `mediaId` that is missing, not a positive integer or names no existing media SHALL
be refused with `400` naming `leadImage.mediaId`; an invalid caption with `400` naming
`leadImage.caption`; unknown fields inside `leadImage` with `400` naming them
(`leadImage.<name>`). Any existing media MAY be used, whoever uploaded it. `ArticleDto` (latest
revision) and `RevisionDto` SHALL return `leadImage` as `null` or as
`{"mediaId", "caption", "width", "height"}` with the media's width and height. The lead image SHALL
become visible to readers only with the revision that carries it being published.

#### Scenario: Set a lead image
- **WHEN** the author saves a draft with `leadImage` `{"mediaId": 17, "caption": "Our cat Minka"}` and media 17 is 1600 × 1067
- **THEN** the response is `200` and `leadImage` is `{"mediaId": 17, "caption": "Our cat Minka", "width": 1600, "height": 1067}`

#### Scenario: Lead image without caption
- **WHEN** the author saves `leadImage` `{"mediaId": 17}`
- **THEN** the saved lead image has the caption `""`

#### Scenario: Remove the lead image
- **WHEN** the author saves an article that had a lead image with `leadImage` `null` or without the field
- **THEN** the latest revision has no lead image

#### Scenario: Unknown media
- **WHEN** the author saves `leadImage` `{"mediaId": 999999}` and no such media exists
- **THEN** the response is `400` naming `leadImage.mediaId` and nothing is saved

#### Scenario: Caption too long
- **WHEN** the author saves a caption of 301 characters
- **THEN** the response is `400` naming `leadImage.caption`

#### Scenario: New lead image stays hidden until publication
- **WHEN** the author adds a lead image to a published article whose live revision has none
- **THEN** a new working revision carries the image, and the live revision and the reader stay without image until the next publication
