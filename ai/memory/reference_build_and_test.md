---
name: reference_build_and_test
description: Verified dev/test/build commands for backend (Maven/Quarkus), admin (Gradle/Compose Wasm), image, .http files and local e2e
metadata:
  type: reference
---

Verified 2026-09-26 on Gerald's machine (JDK 21, Docker running) unless marked otherwise.

- **Backend dev:** `cd backend && ./mvnw quarkus:dev` → http://localhost:8080; Dev Services start
  PostgreSQL and Keycloak (fixed port 8180, realm `presserl` from `src/main/resources/dev/`,
  admin console `admin`/`admin`, publisher `publisher`/`publisher`). No manual setup.
- **Backend tests:** `cd backend && ./mvnw verify` (needs Docker for Dev Services). Single class:
  `./mvnw test -Dtest=AdminDeliveryTest`. Surefire XML in `target/surefire-reports/` — check the
  file timestamp; stale reports from earlier runs stay there.
- **Admin dev:** `cd admin && ./gradlew wasmJsBrowserDevelopmentRun` → http://localhost:8081
  (calls the backend on :8080; `%dev` CORS allows it) — standard Kotlin/Wasm task, not re-run on 2026-09-26.
- **Admin tests:** `cd admin && ./gradlew check` (Karma, headless Chrome with SwiftShader via
  `composeApp/karma.config.d/`; Node cannot run the Compose runtime).
- **Image:** `docker build -t presserl:local .` from the repo root (~3 min cold).
- **.http files:** `cd http && docker run --rm --network host -v "$PWD":/workdir
  jetbrains/intellij-http-client --env-file http-client.env.json --env dev *.http`
  against a running `quarkus:dev`.
- **Local e2e of image + compose:** import `deploy/keycloak/presserl-realm.json` into the dev
  Keycloak under another realm name (hostname replaced), run `deploy/compose.yaml` with a
  `network_mode: host` override so the container reaches `localhost:8180`, drive the login with
  the `mcr.microsoft.com/playwright` image (Compose draws on a canvas: read texts via the
  accessibility snapshot, click by bounding box). Headless browsers need an explicit `locale`,
  otherwise Compose throws `RangeError: Incorrect locale information provided`.

See [[reference_machine_jdk]].
