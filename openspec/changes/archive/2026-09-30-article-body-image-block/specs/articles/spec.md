## MODIFIED Requirements

### Requirement: Body format version 1 with allowlist validation
The system SHALL accept an article body only in the structured format version 1:
an object `{"version": 1, "blocks": [...]}` whose blocks are
- `{"type": "paragraph", "content": [runs]}`,
- `{"type": "subhead", "text": "..."}`,
- `{"type": "quote", "content": [runs]}`,
- `{"type": "list", "items": [[runs], ...]}` (bullet list, at least one item),
- `{"type": "image", "mediaId": <id>, "caption": "..."}` (`caption` optional, default empty),

where a run is `{"text": "...", "bold": true|false}` (`bold` optional, default `false`) with
non-empty `text`. An image block's `mediaId` SHALL be a positive integer and its `caption` a string
of at most 300 characters without any control character; the caption SHALL count towards the body's
text. Any other block type, any unknown field, a missing required field, a `version` other than
`1`, control characters other than line feed inside paragraph, quote or list runs, more than 500
blocks or a body larger than 200 000 characters of text SHALL be rejected with `400`. Raw HTML
SHALL never be interpreted; markup characters are stored as text.

#### Scenario: Valid standard body
- **WHEN** an article is saved with a body containing a paragraph with a bold run, a subhead, a quote and a two-item list
- **THEN** the response is `200` and a subsequent `GET` returns the body unchanged

#### Scenario: Body with an image block
- **WHEN** an article is saved with a body containing a paragraph, an image block `{"type": "image", "mediaId": 17, "caption": "The finish line"}` for an existing media 17 and a second paragraph
- **THEN** the response is `200` and a subsequent `GET` returns the body unchanged

#### Scenario: Image block without caption
- **WHEN** an article is saved with the image block `{"type": "image", "mediaId": 17}`
- **THEN** the response is `200` and a subsequent `GET` returns the block unchanged, without a caption

#### Scenario: Image block without media id
- **WHEN** an article is saved with the block `{"type": "image", "caption": "x"}` at index 2
- **THEN** the response is `400` naming `body.blocks[2].mediaId` and nothing is stored

#### Scenario: Image caption with a line break
- **WHEN** an article is saved with an image block at index 0 whose caption is `one\ntwo`
- **THEN** the response is `400` naming `body.blocks[0].caption`

#### Scenario: Image caption too long
- **WHEN** an article is saved with an image block at index 0 whose caption has 301 characters
- **THEN** the response is `400` naming `body.blocks[0].caption`

#### Scenario: Unknown block type
- **WHEN** an article is saved with a block `{"type": "html", "html": "<script>alert(1)</script>"}`
- **THEN** the response is `400` naming `body.blocks[0].type` and nothing is stored

#### Scenario: Unknown mark
- **WHEN** an article is saved with a run `{"text": "x", "italic": true}`
- **THEN** the response is `400` naming the offending field

#### Scenario: Unknown field in an image block
- **WHEN** an article is saved with the image block `{"type": "image", "mediaId": 17, "src": "http://evil"}` at index 0
- **THEN** the response is `400` naming `body.blocks[0].src`

#### Scenario: Markup in text is kept as text
- **WHEN** an article is saved with a paragraph run `<b>hi</b>`
- **THEN** the response is `200` and the run's `text` is returned as `<b>hi</b>` verbatim

## ADDED Requirements

### Requirement: Images in the body
An image block SHALL reference an existing media; any existing media MAY be used, whoever uploaded
it, and the same media MAY appear several times in a body and also as the lead image. A `mediaId`
that names no existing media SHALL be refused with `400` naming `body.blocks[i].mediaId`, after the
syntax checks and without storing anything. A media used by an image block of any revision SHALL
count as used by that revision in every rule that depends on use (reader delivery, usage, editing
rights), exactly like a lead image. A body image SHALL become visible to readers only with the
revision that carries it being published.

#### Scenario: Unknown media in the body
- **WHEN** the author saves a body whose block at index 1 is `{"type": "image", "mediaId": 999999}` and no such media exists
- **THEN** the response is `400` naming `body.blocks[1].mediaId` and nothing is saved

#### Scenario: Same image twice
- **WHEN** the author saves an article with media 17 as lead image and as an image block in the body
- **THEN** the response is `200`

#### Scenario: New body image stays hidden until publication
- **WHEN** the author adds an image block with media 18 to a published article whose live revision has no image
- **THEN** a new working revision carries the block, and the live revision and the reader stay without the image until the next publication
