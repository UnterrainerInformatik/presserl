## ADDED Requirements

### Requirement: Admin app shows the Presserl favicon
The admin web app's page SHALL link the Presserl favicon as SVG and as ICO, served from the admin
app's own path, so the browser tab shows the brand icon. The icons SHALL load under the admin
pages' Content-Security-Policy without a violation.

#### Scenario: Favicon in the admin tab
- **WHEN** a browser opens `/admin/`
- **THEN** the page links an SVG and an ICO favicon under `/admin/`, both answer `200`, and no CSP violation is reported
