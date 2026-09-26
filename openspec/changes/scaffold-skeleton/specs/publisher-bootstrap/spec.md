## Purpose

Guarantees that a fresh installation has a first publisher account, created from the
deployment's mandatory variables, so the newspaper can be administered without manual Keycloak work.

## ADDED Requirements

### Requirement: Publisher credentials are mandatory in production
The system SHALL refuse to start in the production profile when `PRESSERL_PUBLISHER_USERNAME`
or `PRESSERL_PUBLISHER_PASSWORD` is missing or blank, and SHALL name the missing variable.

#### Scenario: Password not set
- **WHEN** the backend starts in production with `PRESSERL_PUBLISHER_USERNAME` set and `PRESSERL_PUBLISHER_PASSWORD` unset
- **THEN** startup fails with an error naming `PRESSERL_PUBLISHER_PASSWORD`

### Requirement: First publisher is created when none exists
On startup, the system SHALL check whether the Keycloak group `publisher` has at least one
member. If it has none, the system SHALL create the user `PRESSERL_PUBLISHER_USERNAME` with
password `PRESSERL_PUBLISHER_PASSWORD` (not temporary, enabled) and add it to `publisher`; if a
user with that username already exists, the system SHALL add that user to `publisher` without
changing its password.

#### Scenario: Fresh realm
- **WHEN** the backend starts against a realm whose `publisher` group is empty and no user `papa` exists, with `PRESSERL_PUBLISHER_USERNAME=papa`
- **THEN** user `papa` exists, is enabled, is a member of `publisher` and can log in with `PRESSERL_PUBLISHER_PASSWORD`

#### Scenario: User exists but is not a publisher
- **WHEN** the `publisher` group is empty and user `papa` already exists with another password
- **THEN** `papa` becomes a member of `publisher` and keeps the existing password

### Requirement: Bootstrap is idempotent
The system SHALL NOT create, modify or re-add any user when the `publisher` group already has
at least one member, regardless of the configured publisher variables.

#### Scenario: Restart after bootstrap
- **WHEN** the backend restarts after a successful bootstrap, with a changed `PRESSERL_PUBLISHER_PASSWORD`
- **THEN** no user is created or changed and the publisher's password stays the original one

### Requirement: Bootstrap survives a late Keycloak
When Keycloak is unreachable or the `publisher` group does not exist, the system SHALL log the
reason, retry with increasing delay, and report the readiness health check as down until the
bootstrap has succeeded; the process SHALL keep running.

#### Scenario: Keycloak starts after the backend
- **WHEN** the backend starts while Keycloak is unreachable and Keycloak becomes reachable later
- **THEN** readiness is down until Keycloak is reachable, the bootstrap then completes, and readiness turns up

#### Scenario: Realm template not imported
- **WHEN** the configured realm has no `publisher` group
- **THEN** the backend logs an error naming the missing group and readiness stays down
