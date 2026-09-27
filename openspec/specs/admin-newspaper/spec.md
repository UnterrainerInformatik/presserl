# admin-newspaper Specification

## Purpose

Lets publishers and editors-in-chief change the newspaper-wide settings from the admin app; in
this change the default text size of the reader.

## Requirements

### Requirement: Newspaper settings screen
The "Newspaper" header entry SHALL open a settings screen that shows the effective default reader
text size from `GET /api/newspaper` and offers the choices "installation default", S, M, L and XL
(with their pixel sizes 17/19/22/26 px). The selected choice SHALL be "installation default" when
`overrides` contains no `reader.text-size`, and the overriding size otherwise. Choosing a size
SHALL send it with `PUT /api/newspaper/settings`; choosing "installation default" SHALL send
`null` for `reader.text-size`. After a successful save the screen SHALL show the effective values
from the response; a refused save SHALL show the server's message and keep the previous choice.
All texts SHALL be available in German and English.

#### Scenario: Setting a larger default
- **WHEN** a publisher opens "Newspaper" on a fresh installation and chooses L
- **THEN** the app sends `PUT /api/newspaper/settings` with `{"reader.text-size": "l"}` and afterwards shows L as selected and as effective size

#### Scenario: Back to the installation default
- **WHEN** the newspaper overrides `reader.text-size` with `l` and the publisher chooses "installation default"
- **THEN** the app sends `{"reader.text-size": null}` and afterwards shows "installation default" as selected with the effective size of the deployment

#### Scenario: Save refused
- **WHEN** the server answers the save with `403`
- **THEN** the screen shows an error message and the previous choice stays selected
