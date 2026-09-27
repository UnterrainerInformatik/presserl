## ADDED Requirements

### Requirement: Theme directory in the reference deployment
The reference deployment SHALL contain a `theme/` directory next to the compose file that the
compose file mounts read-only as the theme directory of `presserl`. It SHALL contain a
`custom.css` starter that changes nothing and explains in comments the public tokens, `data-view`
values and documented classes; a README on how to theme and where fonts and images go; and the
example themes *Classic*, *Colourful* and *Night* as copy templates. `INSTALL.md` SHALL describe
how to theme the newspaper by editing `theme/custom.css`, how to start from an example theme, and
that a changed theme applies on the next page load without restarting.

#### Scenario: Starting with the unchanged starter
- **WHEN** an operator starts the reference deployment without touching `theme/`
- **THEN** the reader links `/theme/custom.css` and looks exactly like the built-in default theme

#### Scenario: Using an example theme
- **WHEN** the operator copies `theme/examples/night.css` over `theme/custom.css` and reloads a reader page
- **THEN** the reader shows the Night theme without a restart of `presserl`
