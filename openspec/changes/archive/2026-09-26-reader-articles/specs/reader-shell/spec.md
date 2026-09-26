## ADDED Requirements

### Requirement: Localized reader texts
All texts of reader pages that do not come from the newspaper or its articles (notes, byline and
date wording, links, the `404` page) SHALL be available in German and English. A page SHALL use
English when the request's `Accept-Language` prefers English and German otherwise, format dates
in that language and set `<html lang>` to `de` or `en` accordingly.

#### Scenario: German browser
- **WHEN** a browser sending `Accept-Language: de-AT,de;q=0.9` requests `GET /` of an empty newspaper
- **THEN** the page has `<html lang="de">` and the empty note is German

#### Scenario: English browser
- **WHEN** a browser sending `Accept-Language: en-GB,en;q=0.9` requests `GET /` of an empty newspaper
- **THEN** the page has `<html lang="en">` and the empty note is English

#### Scenario: No language preference
- **WHEN** a client without `Accept-Language` requests `GET /`
- **THEN** the page has `<html lang="de">`
