## ADDED Requirements

### Requirement: Legal notice from the deployment's theme
When the theme directory contains a file `legal-notice.txt`, `GET /legal-notice` SHALL render its
text as a reader page with `data-view="legal-notice"` on `<main>`, under the masthead (linked to
`/`) and a localized heading ("Impressum" / "Legal notice"). The text SHALL be HTML-escaped. One or
more blank lines SHALL start a new paragraph, and a single line break SHALL stay a line break. The
file SHALL be read as UTF-8, and a change to it SHALL show on the next page load without a restart.
Without the file, or with a file that holds only whitespace, `GET /legal-notice` SHALL answer with
the reader's `404` page.

#### Scenario: Notice rendered
- **WHEN** the theme holds `legal-notice.txt` with the lines "Offenlegung gemäß § 25 Mediengesetz", an empty line, and "Medieninhaber: Gerald Unterrainer, Enns"
- **THEN** `GET /legal-notice` shows two paragraphs with exactly these texts

#### Scenario: Markup is shown as text
- **WHEN** `legal-notice.txt` contains `<script>alert(1)</script>`
- **THEN** the page shows that text literally and contains no `<script>` element from it

#### Scenario: No notice configured
- **WHEN** the theme has no `legal-notice.txt`
- **THEN** `GET /legal-notice` answers with the `404` page

### Requirement: Reader pages link the legal notice
When a legal notice exists, the front page, article pages, issue pages, the issue archive and the
`404` page SHALL end with a footer holding a link "Impressum" / "Legal notice" to `/legal-notice`.
Without a legal notice no footer link SHALL be shown. Print views SHALL NOT show the link.

#### Scenario: Footer link
- **WHEN** a legal notice exists and a German browser opens the front page
- **THEN** the page ends with a link "Impressum" to `/legal-notice`

#### Scenario: No link without notice
- **WHEN** the theme has no `legal-notice.txt`
- **THEN** no reader page contains a link to `/legal-notice`

### Requirement: Legal notice is public
`/legal-notice` SHALL be served to every visitor, including anonymous visitors of a private
newspaper, without a redirect to `/login`. It SHALL follow the reader's usual cache headers for a
public page, except for a logged-in visitor, who SHALL get `Cache-Control: private, no-store` as
on other reader pages.

#### Scenario: Private newspaper
- **WHEN** the newspaper is private and an anonymous visitor requests `/legal-notice`
- **THEN** the legal notice is shown, not the login redirect
