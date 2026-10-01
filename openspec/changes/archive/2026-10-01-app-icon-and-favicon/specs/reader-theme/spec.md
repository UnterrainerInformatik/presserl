## ADDED Requirements

### Requirement: Fork icons replace the default icons
When the fork's theme directory contains `favicon.svg`, `favicon.ico` or `apple-touch-icon.png`
at its top level, reader pages SHALL link that file under `/theme/` instead of the default icon of
the same kind, and `/favicon.ico` at the root SHALL answer with the theme's `favicon.ico`. Icon
kinds the theme does not provide SHALL keep the default. Adding or removing such a file SHALL take
effect on the next page load, without a restart.

#### Scenario: Fork SVG favicon
- **WHEN** the theme directory contains `favicon.svg` and a browser loads the front page
- **THEN** the SVG icon link points to `/theme/favicon.svg`, while the ICO and Apple touch icon links keep the defaults

#### Scenario: Fork ICO at the root
- **WHEN** the theme directory contains `favicon.ico` and a client requests `GET /favicon.ico`
- **THEN** the response carries the theme's file

#### Scenario: No fork icons
- **WHEN** the theme directory contains none of the icon files
- **THEN** every icon link points to the default Presserl icon
