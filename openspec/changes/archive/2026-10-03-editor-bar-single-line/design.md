## Context

`BottomBar` in `admin/composeApp/.../ui/editor/EditorScreen.kt` has three branches: keyboard open
(`hideChrome()`: one `Row` with undo/redo `IconButton`s and the save state), compact
(`LocalCompactLayout`: one `FlowRow` holding the labelled undo/redo buttons, the save state and the
article actions), and wide (two `FlowRow`s side by side). Only the compact branch changes. Both the
Android app and the web app at phone width go through it. There are no Compose UI tests in the
admin project; layout checks so far run on the A54 and via Playwright.

## Goals / Non-Goals

**Goals:**
- Compact bar never takes more than one line.
- Fit by uniform scaling, so the bar looks like the same bar, just smaller.

**Non-Goals:**
- No adaptive dropping of actions or overflow menu.
- No change to the keyboard-open or wide branches.

## Decisions

**D1 — Undo/redo as icons in the compact bar.** Reuse the `IconButton` + `SymbolIcon` pair from
the keyboard-open branch (extracted into one small composable used by both). The labelled buttons
are the widest items in the bar; replacing them with icons usually makes the line fit without any
scaling. Alternative: keep labels and rely on scaling alone — rejected, it would shrink the bar
much more for no information gain (the icons are already used while typing and carry accessible
names).

**D2 — Scale-to-fit via a custom `Layout`.** A helper `FitWidth { … }` in `ui/` measures its
content with unbounded max width, computes `scale = fitScale(contentWidth, maxWidth)` and places the
content with `placeWithLayer` (`scaleX = scaleY = scale`, `transformOrigin = (0, 0)`), reporting
`contentWidth × scale` by `contentHeight × scale` as its own size. Because the scale is applied in
the graphics layer, pointer input is transformed with it, so taps land on the scaled buttons.
`fitScale` is a pure function (`min(1f, max / content)`, guarded against zero) and gets a unit test
in `commonTest`.
Alternatives: shrinking the font size step by step via `SubcomposeLayout` (several measure passes,
padding and icons do not shrink with the text); `Modifier.scale` alone (does not change the
measured size, the bar would still claim its full width and clip); horizontal scrolling (hides the
primary action, which sits at the end).

**D3 — No minimum scale.** The widest realistic case (publisher, German, published article:
icons + "Gespeichert" + "Löschen" + "Offline nehmen" + "Veröffentlichen") is expected to fit at
roughly 0.85–1.0 on a 360–412 dp phone. A floor would force either wrapping or clipping again,
which is exactly what the spec rules out. The scale is measured on the A54 during verification.

## Risks / Trade-offs

- [Scaled buttons fall below the 48 dp touch target] → D1 keeps the scale near 1 in practice; the
  verification records the scale actually reached on the A54 and at 360 px in the browser.
- [Save state text changes width while typing ("Saving…" vs. "Saved"), so the bar's scale jumps]
  → acceptable; if it is visibly jumpy, give the save state a fixed minimum width (decided during
  apply by looking at it, no spec impact).
