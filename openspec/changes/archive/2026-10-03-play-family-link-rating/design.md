## Context

See proposal.md (Why). The facts this change records:

- Ratings issued on 1 Oct 2026: ClassInd (Brazil) All ages; ESRB (North America) Everyone 10+;
  PEGI (Europe) Parental guidance; USK (Germany) Ages 6+; IARC Generic (rest of world) 12+;
  Google Play Russia 12+; Google Play South Korea 12+.
- PEGI assigns "Parental guidance recommended" to non-game apps whose content comes from users and
  cannot be rated by age (social networks, video platforms). The trigger is the answer "yes" to
  "users can interact or exchange content" in `docs/play-console.md` → Content rating. On 3 Oct 2026
  Gerald checked the submitted questionnaire against that section: every answer is as documented,
  so PG is the expected outcome, not a mistake.
- Observed on 3 Oct 2026 with a supervised child account in Austria, on the internal test track:
  1. With the Family Link app filter "up to PEGI 12", the install from the opt-in link failed with
     "An error occurred. Turn on Wi-Fi or mobile data and try again", although the phone was online.
     No approval request reached the parent's Family Link app.
  2. Setting Google Play approvals to "never" alone did not change anything.
  3. After the rating filter and approvals were both lifted, the error changed to "Item not found"
     ("Nicht gefunden"). Clearing the Play Store and Play services cache and opening the exact
     "Copy link" opt-in link on the phone did not help. A retry later is pending.
  4. The same release installs normally on the developer's own (not supervised) account.
- Not verified: whether supervised accounts can install from a test track at all. Third-party
  developer reports say they cannot, and Google's own help does not say so.

## Goals / Non-Goals

**Goals:**
- A parent or teacher who has never heard of PEGI can fix the problem or decide against it, using
  only `docs/admin-app.md`.
- A developer who gets a "the app won't install" report finds the cause and a checklist in
  `docs/play-console.md`.

**Non-Goals:**
- Instructions for the iOS app (does not exist yet).
- Covering other parental-control products (Samsung Kids, Microsoft Family Safety, …).

## Decisions

### D1 — Separate user-facing page `docs/admin-app.md`
Operators read `deploy/INSTALL.md` while setting up the server. Parents and teachers who only
install the app never read it. A separate page gives a self-contained link to send to a parent.
The page is named after the admin app, not after Family Link, so later end-user topics about the
app (e.g. iOS or login slips) can go there too.
*Alternative:* a section in `deploy/INSTALL.md`. Rejected: it is written for server operators and
the problem is big enough to need its own place (Gerald's decision).

### D2 — Structure of `docs/admin-app.md`
1. **Does this affect you?** Symptoms first, so a reader recognises the case: child's phone with
   Family Link, messages "enable Wi-Fi or mobile data" or "Item not found", no request in the
   parent's Family Link app.
2. **Why it happens.** Plain words: Google rates apps by age; because reporters' articles come from
   people, Europe's rating body gives the app "Parental guidance" instead of an age; Family Link's
   age filter treats this as "not allowed", and silently.
3. **What to do.** Numbered steps in the Family Link app (parent's phone): select the child →
   Controls → Content restrictions → Google Play → Apps & games → "Allow all", with a note that
   menu names vary with the Family Link version. Then install the app on the child's phone.
4. **What this setting means.** It lets through every app with every rating on that phone, not
   only Presserl. Suggest leaving Google Play approvals ("require approval") on, so the parent
   still decides app by app. (Whether approval then works for the admin app is not verified, so
   the page says so and does not promise it.)
5. **Alternatives.** Use a parent's or the school's phone/tablet for the child's account in the
   app; or use the admin app in the browser (web version on the newspaper's address) on a computer.
6. **During the test phase** (until the production release): supervised accounts may not be able
   to install test versions at all; then only the alternatives remain. This section is removed
   or updated once M8 releases to production.

Plain language, short sentences, no Play Console terms (no "IARC", "track", "opt-in" outside
section 6). The PEGI term "Parental guidance" is named once in quotes, since parents see it in the
Play Store.

### D3 — Developer view in `docs/play-console.md`
Extend "Content rating (IARC questionnaire)" with three subsections: *Issued ratings* (the table of
1 Oct 2026), *Why PEGI says "Parental guidance"* (trigger answer, why it stays "yes"), *Effect on
Family Link accounts* (observed list with date and the open point, marked as unverified). Add a
troubleshooting checklist to "Open points" or a new section "Install problems": tester on the list
and list ticked for the track, Play Store account on the phone equals the tester address, Family
Link filter, opt-in link opened on the phone, cache, signed universal APK from the App bundle
explorer as a last resort for developers. Link `docs/admin-app.md`.

### D4 — Backlog note instead of listing change
The store description gets a Family Link sentence only with the production release (M8), because
the listing is checked by `check.sh` and goes through review. This change only adds the note to the
M8 entry in `ai/open-proposals.md`, plus the tester recruitment hint.

## Risks / Trade-offs

- [Family Link menu names change or differ by language] → Name the path in English, add the
  German labels in brackets, and say that names may differ.
- [The pending retry shows that test tracks work for supervised accounts after all, or that they
  never do] → Section 6 of the page and the open point in `docs/play-console.md` are written as
  "not verified". The apply step updates them if the retry result is known by then. Otherwise a
  follow-up edit does it.
- [Recommending "Allow all" weakens a parent's protection] → The page states the trade-off
  openly, keeps approvals on as a recommendation and offers alternatives first-class, not as a
  footnote.
