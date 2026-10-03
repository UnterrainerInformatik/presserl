## ADDED Requirements

### Requirement: Content rating consequences for supervised accounts are documented
The repository SHALL record the content ratings Google Play issued for the app, per rating authority,
and SHALL explain which questionnaire answer leads to the PEGI rating "Parental guidance" and why
that answer stays as it is. It SHALL describe what this rating means for a child's Google account
supervised by Google Family Link: which Family Link setting blocks the app, which setting lets it
through, that parental approval does not help, and which error messages Google Play shows instead
of naming the cause. Findings that were observed SHALL be marked with their date and kept apart from
points that are not yet verified.

#### Scenario: Developer looks up a failed install
- **WHEN** a developer gets a report that the app cannot be installed on a child's phone with the message "enable Wi-Fi or mobile data"
- **THEN** the Play Console documentation names the PEGI "Parental guidance" rating as the likely cause, the Family Link setting to check, and a checklist of the other causes (tester list, Play Store account, opt-in link)

#### Scenario: Someone wants a lower rating
- **WHEN** a later change considers answering the user-generated-content question with "no" to get an age-based PEGI rating
- **THEN** the documentation states that the answer is true for the app, and that changing it would make the declaration false

### Requirement: Operators get a plain explanation for children's phones
A user-facing page, written for newspaper operators, parents and teachers without technical
background, SHALL explain why Google Play may refuse the admin app on a child's Family Link account.
It SHALL describe the symptoms, the cause in plain words, the exact Family Link steps that let the app
through, the fact that this setting applies to every app on that phone and not only to the admin
app, and the alternatives that avoid changing it. The installation guide for operators SHALL link to
this page.

#### Scenario: Parent sees the misleading error
- **WHEN** a parent tries to install the admin app on their child's supervised phone and Google Play says "enable Wi-Fi or mobile data" although the phone is online
- **THEN** the page describes this symptom, explains that the app's rating is blocked by the Family Link app filter, and gives the steps in the Family Link app to change it

#### Scenario: Parent weighs the trade-off
- **WHEN** a parent reads the page before changing the Family Link setting
- **THEN** the page tells them that the setting lets through every app with any rating on that phone, and names the alternatives to changing it

#### Scenario: Operator follows the installation guide
- **WHEN** an operator reads the installation guide while setting up a newspaper with child reporters
- **THEN** the guide points them to the page about the admin app on children's phones
