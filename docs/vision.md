# Vision

## Problem

Children want to write, take pictures, report — and be read. The available options are either too open (social media, WordPress with plugins, comment sections, tracking) or too school-like/commercial (school-newspaper SaaS, print designers). None of them is *their own* newspaper that a family hosts and controls itself.

## Solution

Presserl is a self-hosted, multi-user newspaper platform that looks like a real newspaper — front page, sections, issues, bylines, print views. Children write articles; depending on who is involved and whom the others trust, they publish directly or after an approval.

## Principles

1. **Grows with complexity.** A single person holding the first account has no approval step at all: *Publish* and *Take offline* are one click each. Approval appears only when roles are split between people, and it shrinks again as they *trust* each other (see [roles-and-workflow.md](roles-and-workflow.md)). The UI shows only the actions that make sense in the current situation.
2. **Sensible defaults, everything overridable.** A fresh installation works with nothing but passwords, a hostname and the first publisher's credentials. Every behaviour has a default that can be overridden per deployment, per newspaper and per section (see [architecture.md](architecture.md#configuration)).
3. **Safe by design.**
   - Parents act as *publishers*: they administer accounts, approve their children's articles until they trust them, and hold an emergency brake that only a publisher can release.
   - No self-registration; accounts are created by people inside the newspaper; no e-mail addresses are collected.
   - No comments from strangers, no tracking, no third-party resources (fonts, CDNs).
   - **No advertising** — not even ad slots in the layout.
   - Uploaded images are re-encoded and stripped of EXIF/GPS data. Publishers can pixelate faces, name tags and number plates (and crop badly framed photos) in every image afterwards — also in articles already live; the change replaces the image everywhere and cannot be undone.
   - Newspapers are **public by default** — a newspaper wants to be read; private (readers need an account) by explicit choice.
4. **Made for 6–16.** Reading view and editor follow age-appropriate typography and interaction guidelines (see [design-guidelines.md](design-guidelines.md)).
5. **Forkable.** UnterrainerInformatik maintains upstream. A family, class or club creates a deployment repository like `presserl-deployment` from the templates in `deploy/` and customises only name, `.env` and theme — without touching code.

## Target audience

- Children aged 6–16 as the newsroom (editors-in-chief, section editors, reporters).
- Parents as publishers.
- Later: classes and teachers (school context).

## Milestones

| # | Goal | Outcome |
|---|---|---|
| M0 | Skeleton | backend + Qute reader stub + Compose web admin stub + `deploy/` (guide, templates) + publisher bootstrap + end-to-end login + CI images + fork repository wired |
| M1 | Solo newspaper | articles with revisions, editor, publish/offline, reader front page + article page |
| M2 | Accounts & sections | account creation with pass-phrase and printable slip, Keycloak service account, groups, sections, section roles, delegation |
| M3 | Approval chain | chain, trust switches, review queue and notes, emergency-brake lock, `allowedActions` |
| M4 | Look | reader theme tokens, fonts, `custom.css`, text size, dark mode |
| M5 | Images | upload, re-encoding, EXIF/GPS stripping, renditions, lead images, media view with crop and pixelation |
| M6 | Print | issues, print views |
| M7 | First fork | *Alex-Presse* live at `alexpresse.net` (`../alexpresse`, a fork of the staging repository `presserl-deployment`) with its own theme; installation guide verified end to end |
| M8 | Mobile & QR | QR code on the slip (address + credentials, implemented), Android/iOS targets reading it, store publishing (Google Play Families policy, Apple developer account) |

M1 deliberately delivers a usable solo newspaper — someone can start writing from then on while the rest grows around it. Accounts and approval come before *Look* because the family use case (parents approve the child's articles) is the first real deployment.
