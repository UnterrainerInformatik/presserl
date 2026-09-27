# Roles and editorial workflow

## Roles

![Roles](diagrams/roles.svg)

Hierarchy: **Publisher > Editor-in-chief > Section editor > Reporter > Reader.** Roles are cumulative — every role includes everything the roles below it may do (an editor-in-chief may write in every section; a section editor writes in their own sections).

| Role | Enum | German UI label | Stored in | Scope | Typically | Adds to the role below |
|---|---|---|---|---|---|---|
| **Publisher** | `PUBLISHER` | Herausgeber | Keycloak group `publisher` | newspaper + technology | parents / administrator | administration (create and lock accounts, reset passwords, theme, backups), **emergency brake**, final approval level |
| **Editor-in-chief** | `EDITOR_IN_CHIEF` | Chefredakteur | Keycloak group `editor-in-chief` | whole newspaper | the child who owns the newspaper | creates sections, approves section editors' articles, stands in for sections without a section editor |
| **Section editor** | `SECTION_EDITOR` | Ressortleiter | Presserl DB (per section) | 1..n sections | an older sibling / friend | approves the reporters of their sections |
| **Reporter** | `REPORTER` | Redakteur | Presserl DB (per section) | 1..n sections | friends, siblings | writes, submits, takes own articles offline |
| **Reader** | `READER` | Leser | Keycloak group `reader` | newspaper | family, friends | reads a private newspaper |

- **Several people per role.** Two publishers (both parents), two editors-in-chief, several section editors per section are all fine.
- **Several roles per person.** A person can hold roles on several levels; for an article, the highest role the author holds *in the article's section* counts.
- **Delegation.** Everyone from section editor up may create accounts and assign roles **at or below their own level, within their own scope** — a section editor assigns section editors and reporters only in their own sections and no newspaper-wide roles. Reporters and readers do not delegate. There is no confirmation step; publishers see every account and can lock it — except other publishers and their own.
- **Implementation split.** Newspaper-wide roles (`PUBLISHER`, `EDITOR_IN_CHIEF`, `READER`) are Keycloak groups and end up in the token. Per-section roles (`SECTION_EDITOR`, `REPORTER`) and trust switches live in the Presserl database. The backend manages Keycloak users and groups through a service account, so nobody needs the Keycloak admin console.

## Accounts

- **No e-mail anywhere.** Username = first name (lowercase, ASCII-folded; collisions get a suffix: `anna`, `anna-2`; editable).
- **Default password** = four words from a kid-friendly German word list, joined by dashes (`tiger-wolke-apfel-leiter`). Users may change it.
- **Hand-over** on a printable slip: newspaper name, web address, username, password.
- **Password reset** by anyone above the person (delegation rule): publishers reset every account except publishers, editors-in-chief every account holding neither publisher nor editor-in-chief, section editors only reporters who belong to their own sections only. Nobody resets their own password here (users change it in the Keycloak account console); a publisher who is locked out needs the Keycloak admin console. The reset hands out a new pass-phrase on the same slip. No self-registration, no "forgot password".
- **Changing roles** of an existing account follows the password-reset rule (anyone above the person, never oneself, never a publisher) in one form for newspaper-wide and section roles together, all or nothing. Every role that is added or removed must be one the changer may assign; roles left unchanged need none, so a section editor can promote their reporter who also reads the newspaper. At least one role remains. No session is ended: section roles apply at once, newspaper-wide roles with the person's next token refresh (a few minutes).
- **Locking** by publishers only, never of a publisher or of their own account. A locked account cannot log in; unlocking restores it with its password.
- **Setup**: on first start the backend creates the first account as publisher from `PRESSERL_PUBLISHER_USERNAME` / `PRESSERL_PUBLISHER_PASSWORD`. That account holds all roles. The publisher logs in and creates an editor-in-chief.
- Later: a QR code on the slip replaces typing (see M8 in [vision.md](vision.md#milestones)).

## Article lifecycle

![Article lifecycle](diagrams/article-lifecycle.svg)

States: `DRAFT → (SUBMITTED →) PUBLISHED ⇄ OFFLINE`. Every pending submission carries the approval level the article currently waits for (*pending level*). `SUBMITTED` is the status of a never-published article that waits; a `PUBLISHED` or `OFFLINE` article keeps its status (and the reader keeps its live revision) while changes, or its way back online, wait. *Locked* in the diagram is `OFFLINE` with the emergency-brake lock set.

## Approval chain

![Approval chain](diagrams/review-decision.svg)

Levels, bottom to top: **section editor (of the article's section) → editor-in-chief → publisher.** For an article by author *A* in section *S*:

1. Start at the level directly above *A*'s highest role in *S*.
2. A level is **skipped** when
   - no account other than *A* holds that level's role — e.g. *S* has no section editor (the editor-in-chief level takes over), or *A* is the only editor-in-chief; locked accounts count as holders, or
   - any person of that level has set **trust** on *A* (planned).
3. Each remaining level needs the approval of **one** person (other than *A*) holding that level's role **or a higher one**. An approval settles every level up to the approver's own: after a section editor the article waits for the editor-in-chief, after an editor-in-chief for the publisher, and a publisher's approval publishes at once. Which levels remain is decided again at every approval, so role changes act at once.
4. When no level remains, the article is **published**: its latest revision becomes live.

While a submission is pending, the article's content and section are **frozen**; the revision under review is always the latest one. **Rejection** needs a note and ends the submission: a never-published article returns to *Draft*, a published or offline article keeps its status and live revision. The author may **withdraw** the submission at any time (no note, no record). Approvals and rejections are recorded with level, revision, reviewer and note; the author sees them in the editor.

**Trust** is a per-person switch, default **off**. A holder of an approving level sets it on a specific person below (trust the 16-year-old, keep checking the 7-year-old). Trust set by one holder applies to the whole level.

Consequences:

- The first account holds all roles → it publishes immediately.
- Parents as publishers check every article until they trust the child.
- A section editor's article goes editor-in-chief → publisher.
- The UI never shows *Submit* or a review queue when no level applies.

## Growth stages — a family example

| Stage | Newsroom | Flow |
|---|---|---|
| **1 — Solo** (default after installation) | one person with the bootstrap account (all roles) | *Write → Publish → Take offline.* No approval; no *Submit* button, no review queue. |
| **2 — Parents supervise** | parents = publishers, child = editor-in-chief | The child writes; one parent approves. Once the parents trust the child, it publishes directly. |
| **3 — Second chief / section editor** | + an older sibling as second editor-in-chief or as section editor for *Sports* | The sibling's articles: section editor → editor-in-chief (the child) → publisher (parents), minus trusted levels. |
| **4 — Reporters** | + friends as reporters in some sections | Reporter → section editor of the section (or editor-in-chief if the section has none) → publisher, each level skipped once it trusts the reporter. |

## Taking offline and the emergency brake

- **Taking offline never needs approval** — author (own articles), section editors of the section, editors-in-chief, publishers. Withdrawing is the safe direction.
- **Emergency brake:** an article taken offline by a publisher is **locked**; only a publisher can put it back online.
- **Back online** otherwise follows the approval chain. A submission pending when the article is taken offline stays pending; approving it puts the article back online with its latest revision.
- **Editing a published article** creates a new revision; the live revision stays until the new one passes the chain.
- **The server decides.** Responses carry `allowedActions`; clients only render what the server lists and never re-implement the chain.

## Overrides

| Key | Default | Alternatives | Effect |
|---|---|---|---|
| `presserl.retract.author-can-retract` | `true` | `false` | whether reporters may take their own articles offline |

Can be set per deployment and per newspaper (see [architecture.md](architecture.md#configuration)). Approval itself is not configured by keys but by roles and trust.
