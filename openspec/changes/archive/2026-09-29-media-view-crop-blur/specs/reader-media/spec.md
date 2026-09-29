## ADDED Requirements

### Requirement: Lead-image URLs carry the media version
Every reader page (front page, article page, issue page, print views) SHALL reference a lead-image
rendition as `/media/{id}/{kind}?v={version}` with the media's current version, in `src` and
`srcset` alike, so that after an edit browsers and proxies fetch the edited image instead of a
cached copy. `GET /media/{id}/{kind}` SHALL ignore the query string: it always serves the current
rendition under the rules of the reader route, also for a missing or outdated `v`.

#### Scenario: Freshly uploaded lead image
- **WHEN** a published article has media 17 at version 0 as lead image
- **THEN** its article page links `/media/17/web?v=0`, with `/media/17/thumbnail?v=0` in the `srcset`

#### Scenario: After an edit
- **WHEN** a publisher has pixelated media 17 (now version 1)
- **THEN** the next article page links `/media/17/web?v=1` and that URL returns the pixelated rendition

#### Scenario: Old URL
- **WHEN** a visitor requests `/media/17/web?v=0` after the edit
- **THEN** the response is the pixelated rendition, not the previous one
