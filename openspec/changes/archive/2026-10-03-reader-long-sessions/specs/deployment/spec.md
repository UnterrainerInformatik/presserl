## MODIFIED Requirements

### Requirement: Realm template for an existing Keycloak
The deployment SHALL provide a realm template that an operator imports into their own Keycloak.
It SHALL define the groups `publisher`, `editor-in-chief` and `reader`; a public admin client
(authorization code with PKCE `S256` only, no direct access grants) with the backend audience and
a groups claim in its tokens; a confidential reader client (authorization code only, no direct
access grants, no implicit flow, no service account) with a groups claim in its ID token, whose
redirect and post-logout redirect URIs are limited to the installation's hostname; a confidential
backend client whose service account may only manage and query users and groups of that realm;
self-registration, e-mail login and password reset disabled; brute-force detection enabled; an
access token lifespan of 5 minutes; and an SSO session idle timeout and SSO session max lifespan
of 180 days each, so refresh tokens stay usable for half a year while locking an account or
resetting its password takes effect within the access token lifespan.
The template SHALL contain no client secrets.

#### Scenario: Import into an existing Keycloak
- **WHEN** an operator imports the template (after setting their hostname as documented) into their Keycloak
- **THEN** the realm contains the three groups and three clients as described, the backend can bootstrap the publisher with the backend client's secret, and readers can log in to the reader with the reader client's secret

#### Scenario: Token and session lifetimes
- **WHEN** an operator imports the template into their Keycloak
- **THEN** the realm's access token lifespan is 5 minutes and its SSO session idle and SSO session max are 180 days each
