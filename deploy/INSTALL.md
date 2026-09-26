# Installing Presserl

Presserl runs as two containers under docker compose: `presserl` (API, reader and admin app in
one image) and `postgres`. You provide the rest:

- **Keycloak** for logins — Presserl gets its own realm in your existing Keycloak.
- **A TLS-terminating reverse proxy** (Traefik, Caddy, …) in front of `presserl`.

## 1. Prerequisites

- Docker with the compose plugin (`docker compose version`).
- A running Keycloak (version 26 or newer) you can administer, reachable over HTTPS.
- A reverse proxy that terminates TLS, e.g. Traefik with a certificate resolver.
- A DNS name for Presserl pointing to the proxy, e.g. `news.example.org`. In this guide it
  is called `<hostname>`.
- The `presserl` container must be able to reach Keycloak under its public URL (check
  hairpin NAT if Keycloak runs on the same host behind the same proxy).

Copy this directory (`compose.yaml`, `.env.example`, `keycloak/`) to the host, e.g. to
`/opt/presserl`, and run all commands from there.

## 2. Import the realm

The template `keycloak/presserl-realm.json` defines the realm `presserl` with the groups
`publisher`, `editor-in-chief` and `reader`, the public client `presserl-admin` (login for the
admin app), the confidential client `presserl-reader` (login for the reader) and the confidential
client `presserl-backend` (used by the backend to manage users).

1. Put your hostname into the template:
   ```sh
   sed -i 's/presserl\.example\.org/<hostname>/g' keycloak/presserl-realm.json
   ```
2. In the Keycloak admin console: realm dropdown → **Create realm** → **Browse…** →
   select `keycloak/presserl-realm.json` → **Create**.
   (To use a different realm name, change `"realm"` in the file before importing; the issuer URL
   below then carries that name.)
3. In the new realm: **Clients** → `presserl-backend` → **Credentials** → copy the
   **Client secret**, then do the same for **Clients** → `presserl-reader`. Keycloak generated
   both secrets during the import; they go into `.env` below (`PRESSERL_OIDC_BACKEND_SECRET` and
   `PRESSERL_OIDC_READER_SECRET`).

Your issuer URL is `https://<your-keycloak>/realms/presserl`.

### 2a. Alternative: the realm already exists

If your Keycloak administrator created an (empty) realm for you, fill it from the same template.
Steps 1 and 3 above stay the same; instead of step 2, do the following in that realm
(Keycloak 26 admin console, labels may differ slightly between versions):

1. **Realm settings → Login:** switch off *User registration*, *Forgot password*,
   *Remember me*, *Email as username*, *Login with email*, *Verify email* and *Edit username*;
   switch on *Duplicate emails*. Save.
2. **Realm settings → Security defenses → Brute force detection:** mode *Lockout temporarily*,
   *Max login failures* 10, *Wait increment* 1 minute, *Max wait* 15 minutes,
   *Failure reset time* 12 hours. Save.
3. **Realm settings → Tokens:** *Access token lifespan* 5 minutes. **Realm settings → Sessions:**
   *SSO session idle* 30 minutes, *SSO session max* 10 hours. Save.
4. **Realm settings → User profile:** open each of the attributes `email`, `firstName` and
   `lastName`, switch off *Required field*, save. The first publisher is created with a username
   only; with required fields left on, Keycloak asks them for these values at their first login
   (*Verify profile*). The partial import in the next step does not bring the template's user
   profile along, so this has to be done by hand.
5. **Realm settings → Action (top right) → Partial import:** choose
   `keycloak/presserl-realm.json`, tick *Users*, *Clients* and *Groups*, set
   *If a resource exists* to *Skip*, import. The result lists the groups `publisher`,
   `editor-in-chief`, `reader`, the clients `presserl-admin`, `presserl-reader`,
   `presserl-backend` and the user `service-account-presserl-backend`.
6. **Check** under **Clients → presserl-backend → Service accounts roles** that
   `realm-management` grants `manage-users`, `view-users`, `query-users` and `query-groups`;
   assign missing ones with *Assign role → Filter by clients*.
7. Continue with step 3 above (copy the client secrets of `presserl-backend` and
   `presserl-reader`).

## 3. Fill in `.env`

```sh
cp .env.example .env
chmod 600 .env
```

Set every value in `.env`; the comments explain each one. Choose a strong database password and
the first publisher's username and password. All other settings have working defaults; the
optional ones are listed at the end of the file.

## 4. Start

```sh
docker compose up -d
docker compose ps
```

`postgres` becomes healthy within seconds. `presserl` becomes healthy once the database is
migrated and the first publisher exists in Keycloak. Check without the proxy:

```sh
curl http://localhost:8080/api/newspaper
```

## 5. Attach the reverse proxy

### Traefik (labels)

Create `compose.override.yaml` next to `compose.yaml`; docker compose merges it automatically.
Adjust the entrypoint (`websecure`), certificate resolver (`letsencrypt`) and network name
(`traefik`) to your Traefik setup:

```yaml
services:
  presserl:
    # Traefik reaches the container over its network; no host port needed
    ports: !reset []
    labels:
      - traefik.enable=true
      - traefik.docker.network=traefik
      - traefik.http.routers.presserl.rule=Host(`${PRESSERL_HOSTNAME}`)
      - traefik.http.routers.presserl.entrypoints=websecure
      - traefik.http.routers.presserl.tls=true
      - traefik.http.routers.presserl.tls.certresolver=letsencrypt
      - traefik.http.services.presserl.loadbalancer.server.port=8080
    networks:
      - default
      - traefik

networks:
  traefik:
    external: true
```

Then `docker compose up -d` again. Traefik sends `X-Forwarded-*` headers, which Presserl uses to
build its public URLs.

### Caddy (optional alternative)

If Caddy runs as a container, attach it to the compose network `presserl_default` and use:

```
<hostname> {
    reverse_proxy presserl:8080
}
```

If Caddy runs on the host, use `reverse_proxy localhost:8080` instead. Caddy obtains the
certificate and sets the forwarding headers by itself.

## 6. First login

- Open `https://<hostname>/` — the reader's front page with your newspaper's name.
- Open `https://<hostname>/admin/` — you are sent to Keycloak. Log in with
  `PRESSERL_PUBLISHER_USERNAME` / `PRESSERL_PUBLISHER_PASSWORD`. Back in the admin app you see
  the newspaper name, your name and the role `PUBLISHER`.

The publisher is created only once. Changing the variables later does not change the account;
manage it in Keycloak.

### Private newspaper and readers

A newspaper is public by default: everyone can read the reader without logging in. To make it
private, set `PRESSERL_NEWSPAPER_VISIBILITY=private` in `.env` and run `docker compose up -d`.
The reader's front page then shows only the masthead, a note that the newspaper is private and a
**Log in** link.

Readers are Keycloak users of the Presserl realm. For each reader, in the Keycloak admin console:
**Users** → **Create new user** (username, first and last name) → **Credentials** → set a
password → **Groups** → **Join group** → `reader`. Members of `editor-in-chief` and `publisher`
may read as well. A reader logs in via the front page's **Log in** link and sees their name and a
**Log out** link at the top of every page. Accounts without one of these groups can log in, but
see a note that they have no access.

## 7. Updating

Set the new version in `.env` (`PRESSERL_IMAGE=gufalcon/presserl:<tag>`), then:

```sh
docker compose pull
docker compose up -d
```

Database migrations run automatically at start. The database lives in the named volume
`presserl-db` and survives `docker compose down` (but not `docker compose down -v`).

The admin app's entry files (`index.html`, `composeApp.js`, …) are sent with
`Cache-Control: no-cache`, so a new version takes effect on the next page load. Versions before
that sent them as cacheable for a day: if a CDN or caching proxy sits in front of Presserl, purge
its cache for `/admin/*` once when upgrading from such a version. Later updates need no purge.

## 8. Troubleshooting

| Symptom | Cause and fix |
|---|---|
| Keycloak shows **Invalid parameter: redirect_uri** | The hostname in the realm was not replaced (step 2.1) or differs from the one you open. Fix **Valid redirect URIs**, **Valid post logout redirect URIs** and **Web origins** of `presserl-admin` in Keycloak. |
| `presserl` stays **unhealthy** / `/q/health/ready` reports `publisher-bootstrap` DOWN | The backend cannot create the first publisher. `docker compose logs presserl` names the cause: Keycloak unreachable from the container, wrong `PRESSERL_OIDC_BACKEND_SECRET`, or the group `publisher` missing in the realm. It retries automatically; fix the cause and wait. |
| `presserl` exits at start naming `PRESSERL_OIDC_READER_SECRET` / `presserl.oidc.reader-secret` | The reader client secret is missing (new in this version). Import the client `presserl-reader` into the realm if it is missing (partial import as in step 2a.5), copy its secret (step 2.3) into `.env` and start again. |
| Keycloak shows **Invalid parameter: redirect_uri** on the reader's **Log in** link | **Valid redirect URIs** / **Valid post logout redirect URIs** of `presserl-reader` do not match `https://<hostname>/*`. Fix them in Keycloak. |
| `presserl` exits at start naming a variable | A mandatory variable in `.env` is missing or empty, or an optional one has an invalid value; the message lists the allowed values. |
| Admin app loads but API calls answer **401** | `PRESSERL_OIDC_ISSUER` differs from the issuer in the tokens (check scheme, host and realm name), or the realm was changed so `presserl-admin` tokens no longer carry the `presserl-backend` audience. |
| Admin app stays blank; the browser console shows **WebAssembly … unsupported MIME type 'text/html'** or a `.wasm` request answers **404** | A stale cached `composeApp.js` from the previous version references `.wasm` files the new image no longer contains. Hard-reload the page (Ctrl+Shift+R); if a CDN or caching proxy is in front, purge its cache for `/admin/*` (see step 7). |
