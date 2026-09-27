## Why

The approval chain checks every article of a person on every staffed level forever. Parents who
supervise their child have no way to say "we trust her now" short of giving up the publisher role,
and a section editor cannot let an experienced reporter through while still checking a beginner.
`docs/roles-and-workflow.md` plans **trust** for exactly this: a per-person switch, default off, set
by a holder of an approving level on a person below; it is the last open part of M3. The
emergency-brake lock, which must override trust, is already in place.

## What Changes

- New stored **trust**: approving level × trusted account (× section for the section-editor level),
  with who set it and when. Trust set by one holder applies to the whole level.
- **Approval chain:** a level is also skipped when it trusts the author — `PUBLISHER` and
  `EDITOR_IN_CHIEF` newspaper-wide, `SECTION_EDITOR` per section (trust given in `Sport` skips the
  section-editor level only for the author's articles in `Sport`). The emergency-brake lock still
  keeps `PUBLISHER` in the chain of a locked article, trusted or not.
- **Who sets it:** only for the requester's own highest level — a publisher for the `PUBLISHER`
  level, an editor-in-chief (not publisher) for `EDITOR_IN_CHIEF`, a section editor (neither) for
  `SECTION_EDITOR` in each of their sections; only on a person below that level who writes there.
  Any holder of the level may clear a trust, whoever set it.
- New endpoint `PUT /api/accounts/{id}/trust` (`{level, sectionId, trusted}`), idempotent.
- `AccountDto` gains `trusts` (the account's trust entries) and `trustScopes` (the entries the
  requesting user may set or clear on it).
- Pending submissions are **not** moved when trust is set; like staffing, trust is applied the
  next time the chain is computed (submit, publish check, approval).
- **Admin app:** the account list shows an account's trust and offers a trust switch per entry of
  `trustScopes`; setting trust asks for confirmation, clearing does not.
- Docs: `docs/roles-and-workflow.md` and `docs/architecture.md` drop "planned"; primer and `.http`
  files document the endpoint and fields.
- `ai/open-proposals.md`: the M3 trust-switches entry is removed.

## Non-goals

- Moving or publishing pending submissions when trust is set.
- Trust set on behalf of a lower level (a publisher skipping the editor-in-chief level) — only
  the own highest level.
- Removing trust automatically when the trusted person changes roles or sections; stale entries
  are harmless (they skip nothing the person cannot reach) and can still be cleared.
- A trust history or notifications to the trusted person.
- Trust shown in the article editor or the review queue.

## Capabilities

### New Capabilities
<!-- none -->

### Modified Capabilities
- `approval-chain`: a level that trusts the author is skipped (lock still wins).
- `accounts`: `AccountDto` carries `trusts` and `trustScopes`; new requirements for setting and
  clearing trust via `PUT /api/accounts/{id}/trust`.
- `admin-accounts`: trust shown in the account list; trust switch with confirmation.

## Impact

- **backend**: Flyway migration `V8__trust.sql` (table `trust`, cascade on section delete); new
  `TrustStore`/entity, `Trust` value type; `Staffing`/`StaffingService` load the author's trust;
  `ApprovalChain.next` gains the trust skip; `AccountDto`, `AccountResource` (new endpoint, trust
  fields on every account response), trust policy; tests (`ApprovalChainTest`, resource tests).
- **admin**: DTOs (`trusts`, `trustScopes`), API client (`setTrust`), account list model and
  screen, German/English texts; tests.
- **reader**: none.
- **deploy**: none.
- **docs / primer / http**: `ai/primer/endpoints.md`, `docs/roles-and-workflow.md`,
  `docs/architecture.md`, `http/accounts.http` (or the existing accounts request file),
  `ai/open-proposals.md`.
- REST contract: additive (two new `AccountDto` fields, one new endpoint).
