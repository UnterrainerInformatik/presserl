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
admin app) and the confidential client `presserl-backend` (used by the backend to manage users).

1. Put your hostname into the template:
   ```sh
   sed -i 's/presserl\.example\.org/<hostname>/g' keycloak/presserl-realm.json
   ```
2. In the Keycloak admin console: realm dropdown → **Create realm** → **Browse…** →
   select `keycloak/presserl-realm.json` → **Create**.
   (To use a different realm name, change `"realm"` in the file before importing; the issuer URL
   below then carries that name.)
3. In the new realm: **Clients** → `presserl-backend` → **Credentials** → copy the
   **Client secret**. Keycloak generated it during the import; it goes into `.env` below.

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
4. **Realm settings → User profile:** open the attribute `email`, switch off *Required field*,
   save. The first publisher is created without an e-mail address.
5. **Realm settings → Action (top right) → Partial import:** choose
   `keycloak/presserl-realm.json`, tick *Users*, *Clients* and *Groups*, set
   *If a resource exists* to *Skip*, import. The result lists the groups `publisher`,
   `editor-in-chief`, `reader`, the clients `presserl-admin`, `presserl-backend` and the user
   `service-account-presserl-backend`.
6. **Check** under **Clients → presserl-backend → Service accounts roles** that
   `realm-management` grants `manage-users`, `view-users`, `query-users` and `query-groups`;
   assign missing ones with *Assign role → Filter by clients*.
7. Continue with step 3 above (copy the client secret of `presserl-backend`).

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

## 7. Updating

Set the new version in `.env` (`PRESSERL_IMAGE=gufalcon/presserl:<tag>`), then:

```sh
docker compose pull
docker compose up -d
```

Database migrations run automatically at start. The database lives in the named volume
`presserl-db` and survives `docker compose down` (but not `docker compose down -v`).

## 8. Troubleshooting

| Symptom | Cause and fix |
|---|---|
| Keycloak shows **Invalid parameter: redirect_uri** | The hostname in the realm was not replaced (step 2.1) or differs from the one you open. Fix **Valid redirect URIs**, **Valid post logout redirect URIs** and **Web origins** of `presserl-admin` in Keycloak. |
| `presserl` stays **unhealthy** / `/q/health/ready` reports `publisher-bootstrap` DOWN | The backend cannot create the first publisher. `docker compose logs presserl` names the cause: Keycloak unreachable from the container, wrong `PRESSERL_OIDC_BACKEND_SECRET`, or the group `publisher` missing in the realm. It retries automatically; fix the cause and wait. |
| `presserl` exits at start naming a variable | A mandatory variable in `.env` is missing or empty, or an optional one has an invalid value; the message lists the allowed values. |
| Admin app loads but API calls answer **401** | `PRESSERL_OIDC_ISSUER` differs from the issuer in the tokens (check scheme, host and realm name), or the realm was changed so `presserl-admin` tokens no longer carry the `presserl-backend` audience. |
