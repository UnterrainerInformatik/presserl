## Context

See proposal.md - Why. The header's identity block (`Header` in `ui/App.kt`) renders the user line
as the content of a Material3 `TextButton` with zero horizontal padding. A button's content is a
`Row` with `Arrangement.Center`; when the text needs more than one line, the lines are laid out
centred within a box narrower than the text's own layout, so the start of some lines falls outside
the button and is clipped. On the web at desktop width the line never wraps, which is why it went
unnoticed.

## Goals / Non-Goals

**Goals:** start-aligned, unclipped user line at any width; unchanged behaviour as the
"My account" control (click, button role, ≥ 44 dp touch height, ripple/hover feedback).

**Non-Goals:** introducing Compose UI test infrastructure in the admin (see D2).

## Decisions

### D1 — Clickable `Text` instead of `TextButton`
The user line becomes a plain `Text` (`textAlign = Start`, `fillMaxWidth` within the identity
column) with `Modifier.clickable(role = Role.Button, onClick = onMyAccount)`,
`heightIn(min = 44.dp)` and vertical padding 4 dp, keeping the primary colour the `TextButton` gave
it (`MaterialTheme.colorScheme.primary`, `labelLarge` weight dropped in favour of the current
`bodyMedium`).
Alternative: keep `TextButton` and force the inner `Text` to `fillMaxWidth()` + `TextAlign.Start`.
Rejected: it depends on the button's internal `Row` arrangement and minimum width, which change
between Material3 versions; a clickable `Text` states exactly what is wanted.

### D2 — Verification by screenshot, no new UI test setup
The admin has no Compose UI test dependency (`compose.uiTest`) and its Wasm tests run under Karma.
Adding the setup for one layout fix is out of proportion. The scenario is checked by: the web app
at 390 px with Playwright (screenshot, plus the semantics node of the user line has role button
and opens "My account"), and the Android app on the A54 with a three-section-role account
(Lena Berger on staging). A UI test setup can come with a later change that needs more of it.

## Risks / Trade-offs

- [Click feedback differs slightly from a `TextButton`] → `clickable` gives the standard ripple/
  indication; acceptable for a text link-like control.
- [Keyboard focus on the web] → `clickable` is focusable and reacts to Enter like the button did;
  checked with Tab in the Playwright run.

## Migration Plan

Admin-only UI change; ships with the next image and Android build. Rollback = revert the commit.
