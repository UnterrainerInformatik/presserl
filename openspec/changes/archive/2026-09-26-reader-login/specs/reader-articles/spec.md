## MODIFIED Requirements

### Requirement: Private newspaper shows no articles without login
When the effective `visibility` is `private`, what a visitor sees SHALL depend on their reader
session and roles:

- **Anonymous visitor**: the front page SHALL show only the masthead, a note that the newspaper is
  private and a link to `/login`, listing no article. Every `GET /articles/{id}` — whatever the id
  and the article's status — SHALL redirect to `/login?next=/articles/{id}`, so the response does
  not reveal whether the article exists.
- **Logged in and entitled** (`READER`, `EDITOR_IN_CHIEF` or `PUBLISHER`): the front page and the
  article pages SHALL behave exactly as for a public newspaper, including the `404` page for
  unknown or unpublished articles.
- **Logged in but not entitled**: the front page SHALL show only the masthead and a note that the
  account has no access to this newspaper, listing no article, and every `GET /articles/{id}`
  SHALL be answered with the `404` page.

#### Scenario: Private front page
- **WHEN** the visibility is `private`, an article is `PUBLISHED` and an anonymous visitor requests `GET /`
- **THEN** the response is `200` with the masthead, the private note and a link to `/login`, and does not contain the article's headline

#### Scenario: Private article page
- **WHEN** the visibility is `private` and an anonymous visitor requests the page of a published article
- **THEN** the response redirects to `/login?next=/articles/{id}` and contains none of the article's content

#### Scenario: Unknown article of a private newspaper
- **WHEN** the visibility is `private` and an anonymous visitor requests `/articles/999999`
- **THEN** the response is the same redirect to the login as for a published article

#### Scenario: Entitled reader
- **WHEN** the visibility is `private`, an article is `PUBLISHED` and the logged-in reader `oma` (group `reader`) requests `GET /` and the article page
- **THEN** the front page lists the article and the article page shows its live revision

#### Scenario: Entitled reader and a draft
- **WHEN** the visibility is `private` and the logged-in reader `oma` requests the page of a draft
- **THEN** the response is the `404` page

#### Scenario: Account without newspaper role
- **WHEN** the visibility is `private`, an article is `PUBLISHED` and a logged-in user without any newspaper group requests `GET /` and the article page
- **THEN** the front page shows the no-access note without the headline, and the article page answers `404`
