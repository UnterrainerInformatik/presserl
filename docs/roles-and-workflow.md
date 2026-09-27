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
- **Delegation.** Everyone from section editor up may create accounts and assign roles **at or below their own level, within their own scope** — a section editor assigns section editors and reporters only in their own sections and no newspaper-wide roles. Reporters and readers do not delegate. There is no confirmation step; publishers see every account and can lock it.
- **Implementation split.** Newspaper-wide roles (`PUBLISHER`, `EDITOR_IN_CHIEF`, `READER`) are Keycloak groups and end up in the token. Per-section roles (`SECTION_EDITOR`, `REPORTER`) and trust switches live in the Presserl database. The backend manages Keycloak users and groups through a service account, so nobody needs the Keycloak admin console.

## Accounts

- **No e-mail anywhere.** Username = first name (lowercase, ASCII-folded; collisions get a suffix: `anna`, `anna-2`; editable).
- **Default password** = four words from a kid-friendly German word list, joined by dashes (`tiger-wolke-apfel-leiter`). Users may change it.
- **Hand-over** on a printable slip: newspaper name, web address, username, password.
- **Password reset** by anyone above the person (delegation rule). No self-registration, no "forgot password".
- **Setup**: on first start the backend creates the first account as publisher from `PRESSERL_PUBLISHER_USERNAME` / `PRESSERL_PUBLISHER_PASSWORD`. That account holds all roles. The publisher logs in and creates an editor-in-chief.
- Later: a QR code on the slip replaces typing (see M8 in [vision.md](vision.md#milestones)).

## Article lifecycle

![Article lifecycle](diagrams/article-lifecycle.svg)

States: `DRAFT → (SUBMITTED →) PUBLISHED ⇄ OFFLINE`. `SUBMITTED` carries the approval level the article currently waits for; *Locked* in the diagram is `OFFLINE` with the emergency-brake lock set.

## Approval chain

![Approval chain](diagrams/review-decision.svg)

Levels, bottom to top: **section editor (of the article's section) → editor-in-chief → publisher.** For an article by author *A* in section *S*:

1. Start at the level directly above *A*'s highest role in *S*.
2. A level is **skipped** when
   - *A* holds that level's role, or
   - any person of that level has set **trust** on *A*, or
   - (section-editor level only) *S* has no section editor — the editor-in-chief level takes over.
3. Each remaining level needs the approval of **one** person holding that role (other than *A*). Rejection sends the article back to *Draft* with a note.
4. When no level remains, the article is **published**.

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
- **Back online** otherwise follows the approval chain.
- **Editing a published article** creates a new revision; the live revision stays until the new one passes the chain.
- **The server decides.** Responses carry `allowedActions`; clients only render what the server lists and never re-implement the chain.

## Overrides

| Key | Default | Alternatives | Effect |
|---|---|---|---|
| `presserl.retract.author-can-retract` | `true` | `false` | whether reporters may take their own articles offline |

Can be set per deployment and per newspaper (see [architecture.md](architecture.md#configuration)). Approval itself is not configured by keys but by roles and trust.
