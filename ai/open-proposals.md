# Open proposals

Drafted-but-not-yet-proposed work. Delete an entry as soon as it becomes an `/opsx:propose`
change — never tick it off.

Milestones from `docs/vision.md`; details in `docs/`.

## M8 — Mobile & QR
Android/iOS targets of the admin app; the app reads the account slip's QR code
(`<base>/qr?u=<username>#pw=<pass-phrase>`: `<base>` = server address, `u` = username, `pw` =
pass-phrase) and logs in without typing; store publishing (Google Play Families policy, Apple
developer account). The slip's QR code itself is done (qr-slip-login); no token exchange.

## Media view with crop and blur
A media view in the admin app for reviewing uploaded images (list/grid of the newspaper's media
with uploader, upload time, size and the articles using each image; open one full size). Two
editing tools on the opened image:
- **Crop** — drag a rectangle (free or fixed aspect ratios such as the lead-image ratio).
- **Round pixelation** — draw circles/ellipses (by dragging) over faces, name tags, number
  plates or similar; each covered area is pixelated irreversibly. Several areas per image,
  movable and removable before saving.
Afterwards the edited image is saved *over* the original, so every article using it shows the
edited version; the pixelation must be baked into the stored bytes and all renditions (never a
reversible overlay).
Open points: media objects are written once today (random key, rows written after objects) and
published revisions reference them — "save over" means either a new object behind the same media
id (renditions regenerated, caches busted) or a new media row swapped into all references;
who may edit (uploader, reviewers of the article, publishers); whether editing must happen
server-side (re-encode path, EXIF stripping) or client-side with a fresh upload; undo before
saving only, no history after.
