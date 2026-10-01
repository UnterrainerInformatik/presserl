## ADDED Requirements

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
