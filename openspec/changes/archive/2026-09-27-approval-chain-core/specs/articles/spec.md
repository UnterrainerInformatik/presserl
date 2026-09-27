## MODIFIED Requirements

### Requirement: Optimistic concurrency on save
A save SHALL carry the article `version` the client last received. If it differs from the
stored version, the save SHALL be rejected with `409` and change nothing. Every successful save,
publish, take-offline, submit, approve, reject and withdraw SHALL increase the version.

#### Scenario: Stale save from a second tab
- **WHEN** two clients load an article with version `3`, the first saves successfully and the second then saves with version `3`
- **THEN** the second save is answered with `409` and the first client's content is kept

#### Scenario: Submit increases the version
- **WHEN** the author submits an article with version `3`
- **THEN** the response carries a version greater than `3`

### Requirement: Only the author edits and deletes
Only the article's author SHALL save (`PUT`) or delete it, and only while they may write in the
article's section. Saving SHALL additionally require that no submission is pending. An article
SHALL be deletable only while it has never been published; deleting SHALL remove the article, its
revisions and its reviews, also while a submission is pending. Violations SHALL be answered with
`403` (not the author, or no write access to the article's section) or `409` (already published
once, or a submission is pending on save).

#### Scenario: Editor-in-chief edits the publisher's article
- **WHEN** an editor-in-chief saves an article authored by the publisher
- **THEN** the response is `403`

#### Scenario: Author lost the section role
- **WHEN** `reader` authored a draft in `Sport` while `REPORTER` there, the role was removed, and `reader` is still `REPORTER` in `Kultur` and saves the draft
- **THEN** the response is `403` and the draft is unchanged

#### Scenario: Delete a draft
- **WHEN** the author deletes an article that was never published
- **THEN** the response is `204` and `GET` on it returns `404`

#### Scenario: Delete a submitted article
- **WHEN** the author deletes their never-published article that waits for `SECTION_EDITOR`
- **THEN** the response is `204` and the article is gone

#### Scenario: Delete an offline article
- **WHEN** the author deletes an article that was published and is now offline
- **THEN** the response is `409` and the article still exists

### Requirement: Publishing without an approval level
An article SHALL be published directly when the requesting user is its author, may write in its
section, no submission is pending, and the article's chain (see approval-chain) is empty: the
status becomes `PUBLISHED` and the latest revision becomes live. Publishing SHALL require a
non-empty headline (`400` naming `headline` otherwise) and SHALL be refused with `409` when the
article is `PUBLISHED` without unpublished changes or a submission is pending. Any other user —
including an author whose chain is not empty, who has to submit instead — SHALL receive `403`.

#### Scenario: Solo publisher publishes
- **WHEN** the publisher publishes their own draft with headline `Hello`
- **THEN** the response is `200` with `status` `PUBLISHED`, `liveRevision` `1` and a `publishedAt` timestamp

#### Scenario: Editor-in-chief cannot publish yet
- **WHEN** an editor-in-chief who is not a publisher publishes their own draft while another account holds `PUBLISHER`
- **THEN** the response is `403` and the article stays `DRAFT`

#### Scenario: Publish without headline
- **WHEN** the publisher publishes a draft with an empty headline
- **THEN** the response is `400` naming `headline`

#### Scenario: Back online
- **WHEN** the publisher-author publishes their article that is `OFFLINE`
- **THEN** the status becomes `PUBLISHED` with the latest revision live

### Requirement: Taking an article offline
The author, the section editors of the article's section, any `EDITOR_IN_CHIEF` and any
`PUBLISHER` SHALL be able to take a `PUBLISHED` article offline without approval; its status
becomes `OFFLINE` and it keeps its live revision reference. A pending submission SHALL stay pending;
approving it later puts the article back online with its latest revision. Taking offline an article
that is not `PUBLISHED` SHALL be answered with `409`; any other user SHALL receive `403`.

#### Scenario: Editor-in-chief takes the publisher's article offline
- **WHEN** an editor-in-chief takes a published article of the publisher offline
- **THEN** the response is `200` with `status` `OFFLINE`

#### Scenario: Section editor takes an article of their section offline
- **WHEN** `nogroups` is `SECTION_EDITOR` in `Sport` and takes the publisher's published article in `Sport` offline
- **THEN** the response is `200` with `status` `OFFLINE`

#### Scenario: Draft cannot go offline
- **WHEN** the author takes a `DRAFT` offline
- **THEN** the response is `409`

#### Scenario: Offline while changes wait
- **WHEN** a published article whose changes wait for `PUBLISHER` is taken offline
- **THEN** `status` is `OFFLINE` and `pendingLevel` stays `PUBLISHER`

### Requirement: Server-computed allowed actions
Every article representation SHALL carry `allowedActions`, the subset of `EDIT`, `SUBMIT`,
`PUBLISH`, `WITHDRAW`, `APPROVE`, `REJECT`, `TAKE_OFFLINE` and `DELETE` (in this order) that the
requesting user may perform on the article in its current state under the rules above and those of
the approval chain. Clients SHALL be able to rely on an action being accepted (apart from content
validation, a missing headline, a missing note and concurrency) exactly when it is listed.

#### Scenario: Solo publisher on own draft
- **WHEN** the publisher fetches their own draft
- **THEN** `allowedActions` is `["EDIT", "PUBLISH", "DELETE"]`

#### Scenario: Editor-in-chief on the publisher's published article
- **WHEN** an editor-in-chief fetches a published article of the publisher
- **THEN** `allowedActions` is `["TAKE_OFFLINE"]`

#### Scenario: Published without changes
- **WHEN** the publisher fetches their own published article without unpublished changes
- **THEN** `allowedActions` is `["EDIT", "TAKE_OFFLINE"]`

#### Scenario: Reporter on own draft
- **WHEN** a reporter of `Sport` fetches their own draft in `Sport`
- **THEN** `allowedActions` is `["EDIT", "SUBMIT", "DELETE"]`

#### Scenario: Reporter on own submitted draft
- **WHEN** a reporter of `Sport` fetches their own draft in `Sport` that waits for `SECTION_EDITOR`
- **THEN** `allowedActions` is `["WITHDRAW", "DELETE"]`

#### Scenario: Section editor on a submitted article of their section
- **WHEN** a section editor of `Sport` fetches a reporter's article in `Sport` that waits for `SECTION_EDITOR`
- **THEN** `allowedActions` is `["APPROVE", "REJECT"]`

#### Scenario: Section editor on a published article of their section
- **WHEN** a section editor of `Sport` fetches the publisher's published article in `Sport`
- **THEN** `allowedActions` is `["TAKE_OFFLINE"]`

### Requirement: Listing and reading articles
`GET /api/articles` SHALL return summaries of the articles visible to the requesting user, newest
change first, optionally filtered by `status`, by `mine=true` (only articles the requesting
user authored) and by `pending=true` (only articles with a pending submission); filters combine.
An unknown `status` value SHALL be answered with `400`. `GET /api/articles/{id}` SHALL return the
article with the content of its latest revision; an unknown id or an article not visible to the
requesting user SHALL be answered with `404`.

#### Scenario: Filter own drafts
- **WHEN** the publisher and an editor-in-chief each have a draft and the editor-in-chief calls `GET /api/articles?status=DRAFT&mine=true`
- **THEN** only the editor-in-chief's draft is returned

#### Scenario: Filter pending articles
- **WHEN** one draft waits for `SECTION_EDITOR`, a published article has changes waiting for `PUBLISHER`, another draft is not submitted, and the publisher calls `GET /api/articles?pending=true`
- **THEN** exactly the two waiting articles are returned

#### Scenario: Unknown article
- **WHEN** a writer calls `GET /api/articles/999999`
- **THEN** the response is `404`

#### Scenario: Reporter reads another reporter's article
- **WHEN** `reader` and `nogroups` are both `REPORTER` in `Sport` and `reader` calls `GET /api/articles/{id}` on a draft of `nogroups`
- **THEN** the response is `404`

### Requirement: Article visibility
Publishers and editors-in-chief SHALL see every article. Any other writer SHALL see the articles
they authored and, in every section where they are `SECTION_EDITOR`, all articles of that section.
Every endpoint under `/api/articles/{id}` (reading, revisions, reviews, saving, deleting,
publishing, taking offline, submitting, approving, rejecting, withdrawing) SHALL answer `404` for
an article the requesting user does not see, without revealing whether it exists.

#### Scenario: Section editor lists their section
- **WHEN** `nogroups` is `SECTION_EDITOR` in `Sport`, `reader` is `REPORTER` in `Sport` and `Kultur`, `reader` has one draft in `Sport` and one in `Kultur`, and `nogroups` calls `GET /api/articles`
- **THEN** the response contains `reader`'s `Sport` draft and not the `Kultur` draft

#### Scenario: Reporter lists only own articles
- **WHEN** `reader` is `REPORTER` in `Sport` where the publisher has a published article, and `reader` calls `GET /api/articles`
- **THEN** the response contains only articles authored by `reader`

#### Scenario: Revisions of an invisible article
- **WHEN** a reporter calls `GET /api/articles/{id}/revisions` on the publisher's article
- **THEN** the response is `404`

#### Scenario: Approving an invisible article
- **WHEN** a reporter calls `POST /api/articles/{id}/approve` on another reporter's submitted article
- **THEN** the response is `404`
