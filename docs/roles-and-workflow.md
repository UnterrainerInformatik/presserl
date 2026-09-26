# Roles and editorial workflow

## Roles

![Roles](diagrams/roles.svg)

Hierarchy: **Publisher > Section editor > Reporter**. Every higher role may do everything the roles below it may do.

| Role | Enum | German UI label | Typically | May |
|---|---|---|---|---|
| **Operator** (technical) | `OPERATOR` | Betreiber | parents / admin | create and lock accounts, deployment, theme, backups; **emergency brake**: take any article offline. Not part of the editorial flow. |
| **Publisher** | `PUBLISHER` | Herausgeber | the child who owns the newspaper | head of the newspaper: publishes directly everywhere, takes anything offline, creates sections, assigns roles, reviews sections without a section editor |
| **Section editor** | `SECTION_EDITOR` | Chefredakteur | an older child / friend | leads **exactly one section**: publishes directly there, reviews that section's reporters, takes articles offline there |
| **Reporter** | `REPORTER` | Redakteur | friends, siblings | writes for 1..n sections, submits, may take own articles offline at any time |
| **Reader** | `READER` | Leser | family, friends | only needed for private newspapers: reads published articles |

Implementation split: Keycloak knows only *who* someone is (identity plus the realm roles `presserl-user` and `presserl-operator`). *What* someone may do in which newspaper and section is stored by Presserl itself (`Membership`) — so the publisher can assign roles in the app without being a Keycloak admin.

## Article lifecycle

![Article lifecycle](diagrams/article-lifecycle.svg)

States: `DRAFT → (SUBMITTED →) PUBLISHED ⇄ OFFLINE`.

## Review rule

**Default (`review.mode = auto`):** a review happens only if the author may *not* publish in the target section themselves. Otherwise the article goes online immediately.

![Review decision](diagrams/review-decision.svg)

| Stage | Newsroom | Flow |
|---|---|---|
| **1 — Solo** (default after installation) | publisher only, section "General" | *Write → Publish → Take offline*. No review; no *Submit* button or review queue is ever shown. |
| **2 — Small newsroom** | publisher + reporters | Reporters submit; the publisher approves or rejects with a note. |
| **3 — Sections** | publisher + section editors + reporters | Reporter → section editor of their section. Section without an editor → publisher. Section editors and publishers publish directly. |

Further rules:

- **Taking offline never needs a review** — author (own articles), section editor (own section), publisher (everything), operator (emergency brake). Withdrawing is the safe direction.
- **Back online** follows the same rule as publishing.
- **Editing a published article** creates a new revision; the live revision stays until the new one is published (under the same rule).
- **The server decides.** `POST /articles/{id}/publish` returns either `PUBLISHED` or `SUBMITTED`; the client only renders the actions the server lists as allowed.

## Overrides

| Key | Default | Alternatives | Effect |
|---|---|---|---|
| `presserl.review.mode` | `auto` | `always`, `never` | `always`: every publish except the publisher's needs a review; `never`: nobody needs one |
| `presserl.review.chief-needs-publisher` | `false` | `true` | four-eyes principle for section editors too |
| `presserl.retract.author-can-retract` | `true` | `false` | whether reporters may take their own articles offline |

All three can be set per deployment, per newspaper and per section (see [architecture.md](architecture.md#configuration)).
