## MODIFIED Requirements

### Requirement: Stable styling API for forks
The reader SHALL expose a public styling API consisting of the public tokens, the `data-view`
attribute on `<main>` of every reader view (`frontpage`, `article`, `not-found`, `issue`,
`issues`, `print-article`, `print-issue`) and these documented classes, which SHALL stay stable
across releases: `.masthead`, `.masthead__tools`, `.masthead__viewer`, `.masthead__name`,
`.masthead__subtitle`, `.masthead__issue`, `.section-bar`, `.section-tag`, `.text-size-switch`,
`.stories`, `.lead-article`, `.article-card`, `.lead-image`, `.lead-image__caption`, `.kicker`,
`.headline`, `.subheadline`, `.lead`, `.byline`, `.article`, `.article__body`, `.issue-list`,
`.issue-list__item`, `.issue-list__label`, `.issue-list__headline`, `.note`. Each section tag SHALL
carry `data-section-color="<key>"`. Other markup details are internal and MAY change. The
`custom.css` starter of the reference deployment SHALL list exactly these `data-view` values and
classes.

#### Scenario: Front page carries the documented hooks
- **WHEN** a visitor requests `GET /` of a newspaper with published articles
- **THEN** `<main>` has `data-view="frontpage"`, the masthead has class `masthead`, the lead story has class `lead-article` and the other stories have class `article-card`

#### Scenario: Lead image carries the documented hooks
- **WHEN** a visitor opens an article that has a lead image with a caption
- **THEN** the image is wrapped in an element with class `lead-image` and its caption has class `lead-image__caption`

#### Scenario: Issue views carry the documented hooks
- **WHEN** a visitor requests `GET /issues` and `GET /issues/{id}` of a newspaper with a published issue
- **THEN** the archive's `<main>` has `data-view="issues"` and contains an element with class `issue-list` whose entries have class `issue-list__item`, and the issue page's `<main>` has `data-view="issue"` with a masthead showing an element of class `masthead__issue`

#### Scenario: Print views carry the documented hooks
- **WHEN** a visitor opens the print view of an article and of an issue
- **THEN** their `<main>` has `data-view="print-article"` and `data-view="print-issue"` respectively

#### Scenario: View-specific override
- **WHEN** `custom.css` contains `[data-view="frontpage"] .masthead { text-align: left; }`
- **THEN** the front page masthead is left-aligned while the article page masthead keeps the default alignment
