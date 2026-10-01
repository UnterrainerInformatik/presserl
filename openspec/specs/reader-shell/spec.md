# reader-shell Specification

## Purpose

Delivers the server-rendered reader entry page — the public face of the newspaper — with the
security properties every later reader view inherits.

## Requirements

### Requirement: Front page shows the masthead
The system SHALL serve `GET /` as server-rendered HTML (`text/html; charset=UTF-8`) that shows
the effective newspaper name as masthead and, if not empty, the subtitle, with `<main>` carrying
`data-view="frontpage"`. The page SHALL work without JavaScript.

#### Scenario: Fresh installation
- **WHEN** an anonymous visitor requests `GET /`
- **THEN** the response is `200` HTML whose masthead reads `My Newspaper` and whose `<main>` has `data-view="frontpage"`

#### Scenario: Name set by the deployment
- **WHEN** `PRESSERL_NEWSPAPER_NAME=Die Zwergenpost` and a visitor requests `GET /`
- **THEN** the masthead reads `Die Zwergenpost`

### Requirement: Reader pages load no third-party resources
Reader pages SHALL reference only same-origin resources and SHALL be sent with a
`Content-Security-Policy` that restricts scripts, styles, fonts, images and frames to `'self'`.

#### Scenario: CSP header on the front page
- **WHEN** a visitor requests `GET /`
- **THEN** the response has a `Content-Security-Policy` header whose `default-src` is `'self'` and the HTML references no absolute URL of another origin

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

### Requirement: Reader pages carry the Presserl icon
Every reader page, including the print views, the legal notice and the not-found page, SHALL link
a favicon as SVG, a favicon as ICO for browsers without SVG favicons, and a 180×180 Apple touch
icon. The icon files SHALL be served by the reader itself (same origin), without login, also when
the newspaper is private. A request for `/favicon.ico` at the root SHALL be answered with the
reader's ICO favicon.

#### Scenario: Icon links in the head
- **WHEN** a browser loads the front page
- **THEN** the head links an `image/svg+xml` icon, an ICO icon and an `apple-touch-icon`, all on the reader's own origin

#### Scenario: Icons of a private newspaper
- **WHEN** an anonymous client requests the linked icon files of a private newspaper
- **THEN** each response is `200` with the matching image content type, not a login redirect

#### Scenario: Root favicon
- **WHEN** a client requests `GET /favicon.ico`
- **THEN** the response is `200` with content type `image/x-icon`
