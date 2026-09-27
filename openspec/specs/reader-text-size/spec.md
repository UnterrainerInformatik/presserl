# reader-text-size Specification

## Purpose

Lets every reader choose the body text size of the reader (S/M/L/XL) without JavaScript and
remembers the choice in the browser, with the newspaper's default as fallback.

## Requirements

### Requirement: Effective text size of a reader page
Every reader page SHALL render its effective text size as `data-text-size` (`s`, `m`, `l` or `xl`)
on the `<html>` element. The effective size SHALL be the reader's own choice from the text-size
cookie when it holds an allowed value, and otherwise the newspaper's effective
`reader.text-size`. The sizes SHALL mean a body text size of 17, 19, 22 and 26 px respectively,
with increased letter spacing at XL.

#### Scenario: No choice made
- **WHEN** the newspaper's effective `reader.text-size` is `l` and a visitor without text-size cookie requests `GET /`
- **THEN** the page has `<html … data-text-size="l">`

#### Scenario: Reader's own choice wins
- **WHEN** the newspaper's effective `reader.text-size` is `l` and the visitor's text-size cookie holds `s`
- **THEN** every reader page has `data-text-size="s"`

#### Scenario: Tampered cookie
- **WHEN** the text-size cookie holds `huge`
- **THEN** the page uses the newspaper's effective `reader.text-size`

### Requirement: Text-size switch without JavaScript
Every reader page SHALL offer a text-size switch with the four choices S, M, L and XL that works
without JavaScript and marks the effective size as current (also for assistive technology). Choosing
a size SHALL submit `POST /text-size` with the size and the path of the current page. The
endpoint SHALL require no login, SHALL store the size in a first-party cookie valid for one year
(path `/`, `HttpOnly`, `SameSite=Lax`, `Secure` in production) and SHALL answer `303` to the given
path when it is a same-origin path, and to `/` otherwise. A size outside `s`, `m`, `l`, `xl` SHALL
leave the cookie unchanged and still redirect. The switch SHALL also work on a private newspaper
for anonymous visitors.

#### Scenario: Choosing a larger size
- **WHEN** a visitor on `/articles/7` chooses XL
- **THEN** the browser posts `size=xl` and `next=/articles/7` to `/text-size`, receives `303` to `/articles/7` with the text-size cookie set to `xl`, and the article page renders with `data-text-size="xl"`

#### Scenario: Open redirect is refused
- **WHEN** a client posts `size=l` and `next=https://evil.example/` to `/text-size`
- **THEN** the response is `303` to `/` and sets the text-size cookie to `l`

#### Scenario: Unknown size
- **WHEN** a client posts `size=huge` and `next=/` to `/text-size`
- **THEN** the response is `303` to `/` and sets no cookie

#### Scenario: Current size is marked
- **WHEN** a page renders with effective size `m`
- **THEN** the switch marks M as current with `aria-pressed="true"` and the other choices with `aria-pressed="false"`
