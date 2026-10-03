## 1. Contract/Docs — developer view

- [x] 1.1 `docs/play-console.md` → "Content rating (IARC questionnaire)": add *Issued ratings* (all seven ratings of 1 Oct 2026, PEGI highlighted as the one for Austria/Europe)
- [x] 1.2 Same section: *Why PEGI says "Parental guidance"* — the trigger answer (user-generated content), why it stays "yes", and that changing it would be a false declaration (D3)
- [x] 1.3 Same section: *Effect on Family Link accounts* — the observations of 3 Oct 2026 (steps 1–4 of design Context) with date, and the open point "test tracks on supervised accounts" marked as unverified, with the latest retry result if known by then
- [x] 1.4 New section "Install problems": checklist (tester list + list ticked for the track, Play Store account = tester address, Family Link filter, opt-in link from "Copy link" opened on the phone, cache, signed universal APK from the App bundle explorer as last resort for developers) and a link to `docs/admin-app.md`

## 2. Contract/Docs — user-facing page

- [x] 2.1 Create `docs/admin-app.md` with sections 1–6 of design D2, in plain language without Play Console terms outside section 6
- [x] 2.2 Family Link steps with English labels and the German labels in brackets, plus a note that menu names vary by version
- [x] 2.3 Trade-off section: "Allow all" applies to every app on the phone; recommend keeping approvals on, without promising that approval works for the admin app
- [x] 2.4 Alternatives: a parent's or the school's device; the admin app in the browser at `https://<newspaper address>/admin/`

## 3. Deploy

- [x] 3.1 `deploy/INSTALL.md`: short pointer to `docs/admin-app.md` for newspapers with child reporters (near "6. First login" or under "9. Troubleshooting")

## 4. Backlog

- [x] 4.1 `ai/open-proposals.md` → "M8 — Play production release": add the PG/Family Link sentence for the store description (within `check.sh` limits) and the hint that supervised accounts probably cannot be closed-test testers; remove or update section 6 of `docs/admin-app.md` with the production release

## 5. Verification

- [x] 5.1 Read `docs/admin-app.md` as a parent without technical background: every step can be followed and no unexplained term is left
- [x] 5.2 Check all relative links between `docs/admin-app.md`, `docs/play-console.md` and `deploy/INSTALL.md`
- [x] 5.3 Compare every factual statement with design.md Context: observed points carry their date, and unverified points are marked as such
