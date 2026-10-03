## Why

On a phone the editor's bottom bar (undo, redo, save state, then the article actions such as
"Delete", "Take offline" and "Publish") wraps onto two or three lines. Every extra line is taken
away from the article text, which is already short on space at phone width.

## What Changes

- On narrow screens (below the compact width) with the keyboard closed, the editor's bottom bar
  stays on **one line**. Undo and redo are shown as icons (as already done while the keyboard is
  open), followed by the save state and the article actions, the primary action last.
- When that line is wider than the screen, the whole bar is scaled down uniformly until it fits,
  instead of wrapping. Words are never broken and nothing is cut off; tap targets scale with it.
- The keyboard-open bar (undo/redo icons + save state) and the wide-screen layout stay as they are.

## Non-goals

- No change to which actions are offered or to their order.
- No overflow menu ("⋯") for the article actions.
- No change to the wide (desktop) layout or to the header.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `admin-shell`: requirement "Header and editor bar fit narrow screens" — the editor's bottom bar
  on narrow screens is a single line that scales down to fit, instead of wrapping as one group.

## Impact

- **admin**: `ui/editor/EditorScreen.kt` (`BottomBar`, compact branch) and a small scale-to-fit
  layout helper in `ui/`; applies to the Android app and to the web app at phone width alike,
  since both use the same compact layout.
- **backend, reader, deploy, docs**: not affected. No REST contract change.
