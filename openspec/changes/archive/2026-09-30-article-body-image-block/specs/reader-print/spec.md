## ADDED Requirements

### Requirement: Body images in print
Both print views SHALL render the image blocks of every printed article's live body at their place
in the body, as figures with the `print` rendition (`/media/{id}/print?v={version}`, falling back to
`web` while the print rendition is not produced yet), the rendition's `width` and `height` and the
caption below the image when it is not empty, escaped like all other text. Under the print layout a
body figure SHALL span one column, be at most as wide as the column and SHALL NOT be split across
pages.

#### Scenario: Print an article with a body image
- **WHEN** a visitor requests `/print/article/{id}` of a published article whose live body contains an image block with media 18 and caption `The finish line`
- **THEN** the page contains, within the body, an `<img>` pointing to `/media/18/print` and the caption `The finish line`

#### Scenario: Issue print with a body image
- **WHEN** a published issue contains a published article whose live body has an image block with media 18
- **THEN** `/print/issue/{id}` contains an `<img>` pointing to `/media/18/print` within that article's body
