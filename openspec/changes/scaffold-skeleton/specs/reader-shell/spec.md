## Purpose

Delivers the server-rendered reader entry page — the public face of the newspaper — with the
security properties every later reader view inherits.

## ADDED Requirements

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
