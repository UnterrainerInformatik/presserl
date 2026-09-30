## MODIFIED Requirements

### Requirement: Start screen chooses the server
Without a known server the Android app SHALL show a start screen that offers "Scan account slip",
entering a server address, and a link to the app's privacy policy. A server address SHALL be
accepted only when it uses `https` (a debug build additionally accepts `http`) and
`GET <address>/api/client-config` answers with an OIDC configuration. Otherwise the app SHALL stay
on the start screen and explain why:
- when no HTTP answer arrives at all (the name does not resolve, the connection is refused or
  times out, or the TLS handshake fails), the app SHALL say that the server at that address cannot
  be reached, show the address it tried and suggest checking the network or VPN;
- when the server answers, but not with an OIDC configuration (an error status, a body that is not
  a client configuration, or one without issuer or client id), the app SHALL say that no presserl
  newspaper was found at that address.

The check SHALL give up after a bounded time and then count as "cannot be reached". Each failed
check SHALL be written to the device log with its cause (kind of error and its message, or the
HTTP status); usernames, pass-phrases and tokens SHALL NOT appear in that log. The accepted
address SHALL be remembered across app restarts until the user logs out.

#### Scenario: Address of a newspaper
- **WHEN** the user enters `https://presserl.example.org` on the start screen
- **THEN** the app fetches `https://presserl.example.org/api/client-config` and continues with the login page of the issuer it names

#### Scenario: Address without presserl
- **WHEN** the user enters an address whose `/api/client-config` answers `404` or with a page that is not a client configuration
- **THEN** the app shows that no presserl newspaper was found there and stays on the start screen

#### Scenario: Server not reachable
- **WHEN** the user enters `https://presserl.example.org` and the phone cannot resolve or connect to that host (for example because the VPN is not active)
- **THEN** the app shows that `https://presserl.example.org` cannot be reached, suggests checking network or VPN, and stays on the start screen

#### Scenario: Server does not answer
- **WHEN** the connection to the entered address opens but no answer arrives within the time limit
- **THEN** the app stops waiting and shows that the server cannot be reached

#### Scenario: Scanned slip for an unreachable server
- **WHEN** the user scans the slip of a newspaper whose server the phone cannot reach
- **THEN** the app shows that the slip's server address cannot be reached and submits no credentials anywhere

#### Scenario: Cause in the device log
- **WHEN** a check fails
- **THEN** the device log contains the tried address and the cause of the failure, and no username or pass-phrase

#### Scenario: Plain http rejected
- **WHEN** the user of a release build enters `http://presserl.example.org`
- **THEN** the app refuses the address and asks for an `https` address

#### Scenario: Privacy policy
- **WHEN** the user taps the privacy policy link on the start screen
- **THEN** the privacy policy opens in the phone's browser, in German when the phone's language is German and in English otherwise
