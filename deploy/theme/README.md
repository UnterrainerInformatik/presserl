# Theme

This directory is your newspaper's theme. The compose file mounts it read-only into the
`presserl` container; Presserl serves its files at `https://<hostname>/theme/…`.

- **`custom.css`** is loaded on every reader page after the built-in default theme, so its rules
  win. As shipped it only holds comments that list everything you can change: the design tokens
  (fonts, colours, text size, line length, columns, section colours), the `data-view` values of
  the pages and the class names that stay stable across Presserl versions.
- **`examples/`** holds complete themes to start from: *Classic* (the default theme written out),
  *Colourful* (Andika, bright accent, rounded cards) and *Night* (always dark). Copy one over
  `custom.css`:

  ```sh
  cp theme/examples/night.css theme/custom.css
  ```

- **`fonts/`** is for your own font files (`.woff2` preferred). Load them in `custom.css` with
  `@font-face { font-family: "My Font"; src: url("fonts/my-font.woff2") format("woff2"); }`
  and use them via `--presserl-font-body` or `--presserl-font-headline`. Only use fonts whose
  licence allows web embedding.
- Images (a logo, a background) can go anywhere in this directory; reference them relative to
  `custom.css`, e.g. `url("logo.svg")`.

Only these file types are served: `css`, `woff2`, `woff`, `ttf`, `otf`, `png`, `jpg`, `jpeg`,
`gif`, `webp`, `svg`, `ico`. Everything else answers `404`.

Changes apply on the next page load — no restart. Browsers revalidate theme files on every load,
so a hard reload is not needed. Delete `custom.css` to go back to the plain default theme.

Keep the reader readable for children: body text and muted text need a contrast of at least
4.5:1 against the paper colour, and dark mode needs its own colours (see
`examples/classic.css`).
