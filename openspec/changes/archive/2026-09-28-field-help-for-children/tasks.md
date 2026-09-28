## 1. Admin — help model and texts

- [x] 1.1 Add `HelpPart` enum (reader order) under `ui/editor/` with `HeaderField.helpPart` and `BlockType.helpPart` mappings and the resource lookups for explanation, accessible label and sample text
- [x] 1.2 Add German strings (`values/strings.xml`): `help_<part>`, `help_label_<part>`, `sample_<part>` for all eleven parts and `help_image_rights` (wording per design §5)
- [x] 1.3 Add the matching English strings (`values-en/strings.xml`)

## 2. Admin — help UI

- [x] 2.1 Implement `SampleArticle(highlight)` rendering the sample in reader order with placeholder image, highlight (background, border, `▶` marker) and dimmed other parts
- [x] 2.2 Implement `FieldHelp(part, open, onOpenChange)`: `?` button ≥ 44 dp with accessible label; opens on hover, focus and click; hover-opened closes on leave (short delay), pinned closes on click, Escape or outside click; non-focusable popup, max 360 dp wide, scrollable, flips above when no room below
- [x] 2.3 Show `help_image_rights` as a highlighted note in the `LEAD_IMAGE` popup
- [x] 2.4 Hold one `openHelp: HelpPart?` state in the editable editor so at most one popup is open
- [x] 2.5 Place `FieldHelp` on the section chooser (row beside it), header fields and caption (`trailingIcon`), lead-image label and every `BlockCard` label; leave `ArticleView` unchanged

## 3. Admin — tests

- [x] 3.1 Kotlin test: every `HeaderField` and `BlockType` maps to a distinct `HelpPart`, and the mapped parts plus `SECTION`, `LEAD_IMAGE`, `CAPTION` cover all `HelpPart` entries
- [x] 3.2 Kotlin test: opening/closing help does not dispatch any `EditorIntent` or change the autosaver state (e.g. model/draft unchanged, no save scheduled)
- [x] 3.3 Script check (verification): every `help_*` and `sample_*` key exists in both `values/strings.xml` and `values-en/strings.xml`

## 4. Contract / Docs

- [x] 4.1 Add a "Field help" subsection to `docs/design-guidelines.md` §3: `?` next to every editor field and block type, child-level text (about age 10, one or two sentences), shared highlighted sample article, image-rights hint on images, and the rule that every new editor field gets a help entry
- [x] 4.2 Remove the entry "Explain kicker, headline, subheadline and lead for children" from `ai/open-proposals.md`
- [x] 4.3 No REST contract change: confirm `ai/primer/endpoints.md` and `http/` need no update

## 6. Admin shell fixes found during verification

- [x] 6.1 Browser focus guard in `wasmJsMain` (`Main.kt`): after `ComposeViewport`, refocus the canvas when browser focus falls to `BODY` after a `focusout` inside the app's shadow root
- [x] 6.2 Browser test (`wasmJsTest`): shadow root with canvas and focused input; removing the input leaves focus on the canvas; focus moved to an outside element stays there
- [x] 6.3 Narrow header in `App.kt`: below 720 dp two rows (name/user + Log out; navigation scrolling horizontally), single row from 720 dp
- [x] 6.4 Narrow editor bottom bar in `EditorScreen.kt`: below 720 dp one wrapping group (undo, redo, save state, then the actions, primary last), no broken words

## 5. Verification

- [x] 5.1 `cd admin && ./gradlew check` passes
- [x] 5.2 Headless UI check (Playwright against dev backend + admin dev server): open an article, open help for kicker (click), subheadline (focus + Escape), lead image (image-rights text present) and a quote block; check the highlight moves, only one popup is open, no `PUT /api/articles` is sent while only using help, and the English interface shows English texts; screenshots at desktop and phone width; additionally Tab from the subheadline field onto its `?`, Escape closes, a further Tab moves on; at 390 px the header is readable, the editor usable and the bottom bar shows unbroken words
- [x] 5.3 Stop every server and container started for the checks and verify with `ps`/`ss`/`docker`
