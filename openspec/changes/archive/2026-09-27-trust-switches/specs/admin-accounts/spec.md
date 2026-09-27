## ADDED Requirements

### Requirement: Trust in the account list
The account list SHALL show each account's `trusts` as "trusted by" markers with the localized
level label, for `SECTION_EDITOR` together with the section name ("Ressortleiter · Sport").
For every entry of the account's `trustScopes` the account SHALL offer a trust switch labelled with
the level (and section), on when the entry is among its `trusts`; values the app does not know
SHALL be ignored. Turning a switch on SHALL ask for confirmation naming the account and saying
that its articles will no longer wait for that level; cancelling SHALL send nothing. Turning a
switch off SHALL NOT ask. Either SHALL send `PUT /api/accounts/{id}/trust` with the entry's
`level`, `sectionId` and the new `trusted` value; on success the list SHALL show the returned
account, on error an error message and the list unchanged.

#### Scenario: Trust marker
- **WHEN** the publisher level trusts `chief` and the publisher opens the accounts screen (German browser)
- **THEN** `chief` is marked as trusted by "Herausgeber" and its trust switch for "Herausgeber" is on

#### Scenario: No switch without scope
- **WHEN** `nogroups` is `SECTION_EDITOR` in `Sport` and opens the accounts screen while the publisher level trusts `chief`
- **THEN** `chief` shows the trust marker but no trust switch

#### Scenario: Set trust with confirmation
- **WHEN** the publisher turns on the trust switch of `chief` and confirms
- **THEN** `PUT /api/accounts/<chief>/trust` is sent with `{"level": "PUBLISHER", "sectionId": null, "trusted": true}` and `chief` is shown as trusted

#### Scenario: Cancel setting trust
- **WHEN** the publisher turns on the trust switch of `chief` and cancels the confirmation
- **THEN** no request is sent and the switch stays off

#### Scenario: Clear trust without confirmation
- **WHEN** the publisher turns off the trust switch of a trusted `chief`
- **THEN** the request with `"trusted": false` is sent at once and `chief` is no longer marked as trusted

#### Scenario: Section editor's switch
- **WHEN** `nogroups` is `SECTION_EDITOR` in `Sport`, `reader` is `REPORTER` in `Sport`, and `nogroups` opens the accounts screen (German browser)
- **THEN** `reader` offers a trust switch "Ressortleiter · Sport"

#### Scenario: Refused trust change
- **WHEN** a trust change is answered with `403`
- **THEN** the app shows an error message and the switch keeps its previous state
