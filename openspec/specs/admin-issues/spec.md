# admin-issues Specification

## Purpose

Lets editors-in-chief and publishers assemble issues in the administration app: create them,
date them, switch them live, choose and order their articles, and open them in the reader and its
print view.

## Requirements

### Requirement: Issue list
The issues screen SHALL list the issues from `GET /api/issues` in the server's order (highest
number first), each with its localized label ("Ausgabe 3" / "Issue 3"), its publication date in
the app's locale or a "no date" hint, whether it is live ("Live" / "Nicht live"), its article
count and a marker on the newest issue explaining that newly published articles land there. It
SHALL offer "New issue", which asks for an optional publication date and sends
`POST /api/issues`; the new issue SHALL then be opened. Choosing an issue SHALL open its detail
screen. Errors SHALL be shown as a message.

#### Scenario: Blog mode list
- **WHEN** `chief` opens the issues screen of a newspaper with only issue 1, live, 12 articles
- **THEN** the list shows issue 1 as live and newest with 12 articles

#### Scenario: Start the next issue
- **WHEN** `chief` chooses "New issue" without a date while issue 2 is the highest
- **THEN** issue 3 is created, marked newest, not live, and its detail screen is shown

### Requirement: Issue details
The detail screen of an issue SHALL show its label, a publication date field that can be set and
cleared (`PUT /api/issues/{id}`), and a live switch that sends `POST /api/issues/{id}/publish` or
`/unpublish`. While the issue is live it SHALL offer opening the issue page (`/issues/{id}`) and
the issue print view (`/print/issue/{id}`) of the reader in a new browser tab. While it is not
live it SHALL offer "Delete", which asks for confirmation inside the app, sends
`DELETE /api/issues/{id}` and returns to the list. Errors SHALL be shown as a message and the
issue reloaded.

#### Scenario: Publish a planned issue
- **WHEN** `chief` sets the date of issue 4 to 12 October 2026 and switches it live
- **THEN** the detail screen shows issue 4 as live with that date and offers the reader and print links

#### Scenario: Live issue has no delete
- **WHEN** `chief` opens a live issue
- **THEN** no "Delete" is offered

#### Scenario: Delete a planned issue
- **WHEN** `chief` deletes issue 4, which is not live, and confirms
- **THEN** issue 4 is no longer listed

### Requirement: Articles of an issue in the admin app
The detail screen SHALL list the issue's articles in order with headline, section marker and
localized status, the first one marked as lead story; articles that are not `PUBLISHED` SHALL be
marked as not shown to readers. It SHALL offer moving an article one place up or down, removing it
from the issue, and "Add articles": a picker listing the articles from `GET /api/articles` that
are not in this issue, showing for each the issue it currently belongs to, if any. Every change
SHALL send the complete new order as `PUT /api/issues/{id}/articles` and show the returned list.
Choosing an article that belongs to another issue SHALL say that it moves from that issue.

#### Scenario: Make another article the lead story
- **WHEN** `chief` moves the second article of issue 2 one place up
- **THEN** it is shown first and marked as lead story, also after reloading

#### Scenario: Add an article from another issue
- **WHEN** `chief` adds article "Schulfest", which belongs to issue 1, to issue 2
- **THEN** the picker names issue 1, and afterwards "Schulfest" is the last article of issue 2 and no longer in issue 1

#### Scenario: Remove an article
- **WHEN** `chief` removes "Schulfest" from issue 2
- **THEN** issue 2 no longer lists it and the article itself is unchanged

### Requirement: Issue texts are localized
All texts of the issue screens SHALL come from the German and English resources of the admin
app, following the app's language choice.

#### Scenario: German labels
- **WHEN** a user whose browser prefers German opens the issues screen
- **THEN** labels read e.g. "Ausgaben", "Neue Ausgabe" and "Erscheinungsdatum"

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
