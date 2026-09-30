## Purpose

Describes the newspaper's images beyond their pixels: an optional free-text description and a set of
free tags per media, so images that outlive an issue can be found again and reused.

## ADDED Requirements

### Requirement: Description and tags of a media
Every media SHALL have a `description` (`null` or a text of 1 to 1000 code points after trimming;
an empty or blank text SHALL be stored as `null`) and a set of `tags` (0 to 20). A tag SHALL be
normalised before it is checked and stored: leading and trailing whitespace removed and every run of
inner whitespace replaced by one space. A normalised tag SHALL be 1 to 40 code points long and SHALL
contain no control characters and no comma. Tags SHALL be compared case-insensitively: a media
SHALL NOT carry two tags that differ only in case (duplicates are collapsed to one), and when a tag
is set that the newspaper already uses in another spelling, the existing spelling SHALL be stored.
A tag no media carries any more SHALL no longer exist. `tags` SHALL be returned sorted
case-insensitively. A new upload without these fields SHALL have `description` `null` and no tags.
Description and tags SHALL NOT change the media's `version` or its `ETag`.

#### Scenario: Tag spelling of the newspaper is kept
- **WHEN** media 17 carries the tag `Feuerwehr` and a reporter sets the tags `feuerwehr ` and `Hochwasser  2026` on media 20
- **THEN** media 20 carries the tags `Feuerwehr` and `Hochwasser 2026`

#### Scenario: Duplicates in one request
- **WHEN** a reporter sets the tags `Schule`, `schule` and `SCHULE` on a media
- **THEN** the media carries exactly one tag, `Schule`

#### Scenario: Invalid tag
- **WHEN** a reporter sets a tag `Feuer, Wasser`
- **THEN** the response is `400` for field `tags[0]` and nothing changes

#### Scenario: Blank description
- **WHEN** a reporter sets the description `"   "`
- **THEN** the media's description is `null`

### Requirement: Setting description and tags
`PUT /api/media/{id}/details` with a JSON body `{"description": <text or null>, "tags": [<text>...]}`
SHALL replace description and tags of the media and answer `200` with the updated media record. It
SHALL be available to every user whose `allowedActions` contain `USE_MEDIA`, independent of who
uploaded the media and of the articles using it (others `403`, no token `401`, unknown id `404`).
Both fields SHALL be required (`tags` may be empty); a missing field, a wrong type, an unknown field
or an invalid value SHALL be refused with `400` naming the field (`description`, `tags` or
`tags[i]`), and nothing SHALL change. Concurrent requests SHALL NOT merge: the last request wins.

#### Scenario: Reporter tags someone else's live image
- **WHEN** reporter `anna` sets the tags `Feuerwehr` and `Einsatz` on media 17 uploaded by `papa` and used live by a published article
- **THEN** the response is `200`, the media carries both tags and its `version` is unchanged

#### Scenario: Too many tags
- **WHEN** a writer sends 21 distinct tags
- **THEN** the response is `400` for field `tags` and nothing changes

#### Scenario: Reader-only user
- **WHEN** a user whose only role is `READER` sends `PUT /api/media/17/details`
- **THEN** the response is `403`

### Requirement: Tag suggestions
`GET /api/media/tags` SHALL list the newspaper's tags for users with `USE_MEDIA` (others `403`, no
token `401`), each as `{"name": <tag>, "count": <number of media carrying it>}`, ordered by count
descending, then name case-insensitively. The optional query parameter `prefix` SHALL restrict the
list to tags whose name, or any word of it, starts with the prefix (case-insensitive, after
trimming). `limit` (1–100, default 20) SHALL limit the list; an invalid `limit` SHALL answer `400`
for field `limit`.

#### Scenario: Suggest while typing
- **WHEN** the tags `Feuerwehr` (12 media), `Freiwillige Feuerwehr` (3 media) and `Fest` (5 media) exist and a reporter requests `GET /api/media/tags?prefix=feu`
- **THEN** the response lists `Feuerwehr` with count `12` and then `Freiwillige Feuerwehr` with count `3`

#### Scenario: Most used tags
- **WHEN** a reporter requests `GET /api/media/tags` without a prefix
- **THEN** the response lists at most 20 tags, the most used first
