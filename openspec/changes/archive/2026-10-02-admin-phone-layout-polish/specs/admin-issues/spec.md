## ADDED Requirements

### Requirement: Publication date from a date picker
Wherever the admin app asks for an issue's publication date (the "New issue" dialog and the issue
detail screen), the date field SHALL offer a calendar button next to it. The button SHALL open a
date picker preselected with the date in the field. When the field is empty or invalid, the picker
SHALL open at the current month with no date selected. Confirming a date SHALL put it into the field as `YYYY-MM-DD`;
cancelling SHALL leave the field unchanged. Typing the date SHALL keep working as before, and
saving SHALL behave the same whether the date was typed or picked.

#### Scenario: Pick the date of a new issue
- **WHEN** `chief` chooses "New issue", opens the calendar, picks 12 October 2026 and confirms, then creates the issue
- **THEN** the field shows `2026-10-12` and the new issue has publication date 12 October 2026

#### Scenario: Picker starts at the current date
- **WHEN** issue 4 has the date 2026-10-12 and `chief` opens the calendar on its detail screen
- **THEN** the picker shows October 2026 with the 12th selected

#### Scenario: Cancel the picker
- **WHEN** `chief` opens the calendar and cancels
- **THEN** the date field keeps its previous content

#### Scenario: Typing still works
- **WHEN** `chief` types `2026-11-02` into the date field and saves
- **THEN** the issue's publication date is 2 November 2026
