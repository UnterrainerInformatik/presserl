## ADDED Requirements

### Requirement: Account deletion page
`GET /account-deletion` SHALL render a reader page with `data-view="account-deletion"` on `<main>`,
under the masthead (linked to `/`), in the reader's language (German / English), explaining:

- that accounts of this newspaper are created by its newsroom and deleted by its publishers;
- how to request deletion: in the presserl app under "My account", or by asking the newspaper's
  publishers or operator — with a link to `/legal-notice` when a legal notice exists;
- that the account's articles and images stay with the byline "former newsroom member", and that
  deleting cannot be undone;
- that the data the app stores on a phone is removed by logging out or uninstalling the app.

The page SHALL be served to every visitor without login, including anonymous visitors of a private
newspaper, with the same cache rules as `/legal-notice`.

#### Scenario: Page with legal notice
- **WHEN** a legal notice exists and a German browser opens `/account-deletion`
- **THEN** the page explains the request in the app and links "Impressum" to `/legal-notice`

#### Scenario: Page without legal notice
- **WHEN** the theme has no `legal-notice.txt`
- **THEN** `/account-deletion` is shown without a link to `/legal-notice`

#### Scenario: Private newspaper
- **WHEN** the newspaper is private and an anonymous visitor requests `/account-deletion`
- **THEN** the page is shown, not the login redirect

### Requirement: Legal notice links the account deletion page
The legal notice page SHALL end with a link "Konto löschen" / "Delete an account" to
`/account-deletion`.

#### Scenario: Link on the legal notice
- **WHEN** a legal notice exists and an English browser opens `/legal-notice`
- **THEN** the page holds a link "Delete an account" to `/account-deletion`
