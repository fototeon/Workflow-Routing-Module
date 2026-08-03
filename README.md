# Workflow & Routing Module (TZ-02-WORKFLOW)

Summer-practice project: a standalone `workflow-service` microservice implementing process templates,
task assignment, conditional routing, SLA/escalation, reassignment, sub-processes and the process
event journal, per `TZ-02-WORKFLOW` v1.0. Related services referenced in the TZ (request-service,
expertise-service, review-service, document-service, notification-service) are **out of scope** —
this module only implements its own inbound/outbound integration points (a Kafka listener for
`RequestAccepted`, and its own domain events).

## Structure

```
backend/workflow-service   Java 21 / Spring Boot 3 / Maven microservice
frontend/workflow-ui       React + TypeScript + Vite + MUI admin/operator UI
infra/keycloak             Realm export for local OIDC auth (roles + demo users)
docker-compose.yml         Local stack: Postgres, Kafka, Keycloak, backend, frontend
.gitlab-ci.yml             Build/test/package pipeline
TEST-DATA.md               Seeded demo dataset and what each item is meant to exercise
TZ-COMPLIANCE.md           Requirement-by-requirement audit against TZ-02-WORKFLOW v1.0
```

## Running locally

Requires Docker and Docker Compose. The first run also needs network access to Docker Hub, Maven
Central and the npm registry — the two application images are built from source.

```bash
docker compose up -d --build
```

This starts, in order (via healthchecks): Postgres, Kafka (KRaft, single node), Keycloak (realm
`workflow` auto-imported from `infra/keycloak/realm-export.json`), the backend on
`http://localhost:8090`, and the frontend on `http://localhost:5173`.

Then seed the demo template (see below) and open `http://localhost:5173`.

Backend health: `http://localhost:8090/actuator/health`. API docs: `http://localhost:8090/swagger-ui.html`.

Stop everything with `docker compose down` (add `-v` to drop the Postgres volume as well).

### Running the app from source (no image builds)

Useful while developing, and the only option if you cannot build the two application images. Start
the infrastructure in Docker and run backend + frontend on the host:

```
# 1. infrastructure only
docker compose up -d postgres kafka keycloak

# 2. backend on http://localhost:8090 (JDK 21 + Maven), in its own terminal
cd backend/workflow-service
mvn -DskipTests package
java -jar target/workflow-service-1.0.0.jar --server.port=8090

# 3. frontend on http://localhost:5173 (Node 22), in another terminal
cd frontend/workflow-ui
npm install
npm run dev
```

The commands above work as-is in bash, PowerShell and cmd. Port `8090` is not the service's own
default (`8080`) — it is what the Vite dev server proxies `/api` to, so the backend has to be
started with that argument or the UI cannot reach it. Everything else — Postgres, Kafka, the
Keycloak issuer — already defaults to the addresses the compose stack exposes on localhost.

Keycloak stays on `http://localhost:8081` (admin console: `admin` / `admin`) in both modes, so the
demo users and the seed script below work unchanged.

### If the UI shows an empty register and the Vite terminal logs `ECONNREFUSED`

```
[vite] http proxy error: /api/process-instances ... AggregateError [ECONNREFUSED]
```

The UI and Keycloak are fine (you were able to log in) — nothing is listening on `localhost:8090`.
Check, in order:

1. Is the backend running at all? `curl http://localhost:8090/actuator/health` should answer
   `{"status":"UP"}`. Open the same URL in a browser if you have no `curl`.
2. Is it on the right port? Started without `--server.port=8090` it listens on `8080`; its startup
   log line reads `Tomcat started on port 8090`. Either restart it with the argument, or point the
   proxy at your port in `frontend/workflow-ui/vite.config.ts`.
3. Running the full compose stack instead? `docker compose ps` must show `workflow-service` as
   running, and `docker compose logs workflow-service` shows why if it is not.
4. Backend up but requests still fail — check its own terminal: a database or Kafka connection
   error there means `docker compose ps` is worth a look too.

### Demo users (Keycloak realm `workflow`)

| Username       | Password        | Role        |
|----------------|-----------------|-------------|
| `admin1`       | `admin123`      | ADMIN       |
| `manager1`     | `manager123`    | MANAGER     |
| `coordinator1` | `coordinator123`| COORDINATOR |
| `analyst1`     | `analyst123`    | ANALYST     |

### Authentication

The UI carries its own sign-in screen instead of redirecting to Keycloak's hosted login page: the
form posts the credentials to Keycloak's token endpoint (OAuth2 direct access grant, enabled for
the `workflow-ui` client) and keeps the returned tokens in `sessionStorage`. Every API call carries
that access token, which is refreshed shortly before it expires and once more on a `401` before the
request is replayed; `Выйти` revokes the refresh token at Keycloak. Roles come from the access
token's `realm_access` claim — the same claim the backend authorizes on.

Because the browser talks to Keycloak directly, the `workflow-ui` client's **Web Origins** must
list the UI's origin (`http://localhost:5173` in the shipped realm) or the token request is blocked
by CORS. Note the trade-off this flow makes: the password passes through the application, and the
direct access grant is a legacy grant — no SSO, no MFA, no external identity providers. A hosted
login page (authorization code + PKCE) is what you want if any of those matter.

### Seed the demo dataset

The module ships with no data out of the box (templates and requests are created through the UI or
the API). The seed script fills a database that covers the module's features and every role — three
SLA policies, five process templates (including a deliberately unpublished draft) and nine requests
in different states — running, completed, cancelled, reassigned, suspended and a sub-process — one of
which breaches its SLA within five minutes so escalation is observable live:

```bash
cd frontend/workflow-ui
npm install
npm run seed
```

Re-running it is safe: existing items are skipped, not duplicated. **[TEST-DATA.md](TEST-DATA.md)**
lists every seeded item, the role scenarios it supports, and the expected outcomes (including error
codes for the negative cases).

### Golden-path walkthrough (maps to TZ §12 acceptance criteria)

The UI is in Russian; English names of the corresponding screen are given in brackets.

1. Open `http://localhost:5173`, log in as `coordinator1`.
2. **Процессы [Processes] → Запустить процесс [Start new process]** — pick the seeded
   `DEMO_EXPERTISE_REVIEW` template, give it a business key, set attributes to
   `{"requestType": "COMPLEX"}`, start it. The instance
   becomes `Выполняется` (RUNNING) and a task is created and routed via the JSON condition tree
   (REQ-02-001, REQ-02-004).
3. **Задачи [Tasks]** — the new task appears, assigned to the `COORDINATOR` role. Click
   **Выполнить [Complete]** (REQ-02-003). Since no further routing rule matches, the process
   transitions to `Завершён` (COMPLETED).
4. Back on **Процессы**, open the instance: status is `Завершён`, and **История событий [Event
   history]** lists every step (`ProcessStarted`, `TaskCreated`, `TaskCompleted`,
   `ProcessStateChanged`) with timestamps and correlation ids — the process journal (REQ-02-008,
   audit requirement in §10).
5. Log in as `manager1` and reassign a task from **Задачи** with a mandatory reason
   (REQ-02-007) — recorded as its own audit entry and event.
6. As `admin1`, visit **Шаблоны процессов [Templates]** to inspect/extend routing rules (visual
   builder or raw JSON) and **SLA политики [SLA Policies]** to see escalation steps; publishing a
   template requires at least one routing rule.
7. Each of these actions also produces a domain event delivered to Kafka via the transactional
   outbox (`workflow.process.events` / `workflow.task.events` / `workflow.sla.events`), and SLA
   breaches/escalations are applied automatically by a background scheduler (REQ-02-005/006).

### Inbound integration point

`workflow-service` consumes `RequestAccepted` events on the `request.request-accepted` topic to
start a process instance automatically (REQ-02-001). Since request-service isn't built here, this
is exercised in the backend's own integration tests by publishing a matching envelope directly to
that topic — see `RequestAcceptedListenerIntegrationTest`.

## Development

**Backend** — `cd backend/workflow-service && mvn test` (unit) / `mvn verify` (adds Testcontainers
integration tests: Postgres + Kafka, plus a WireMock-backed real-JWT security test). Requires
JDK 21, Maven, and Docker (for Testcontainers).

**Frontend** — `cd frontend/workflow-ui && npm install && npm run dev` (proxies `/api` to
`localhost:8090` in dev). `npm run build` / `npm run lint`.

**End-to-end** — `npm run e2e` (Playwright; first run needs `npx playwright install chromium`).
Requires the whole stack running *and* seeded (`npm run seed`), since the specs drive the demo
template through a real browser and a real Keycloak login. The specs share one backend, so they run
serially.

## Design system

The UI follows a dark glassmorphism theme (mesh-gradient background, translucent blurred panels,
indigo accent, emerald/amber status badges) defined in `frontend/workflow-ui/src/colors.ts` and
wired into the MUI theme in `frontend/workflow-ui/src/theme.ts`.

## Known simplifications (documented, not oversights)

- **Related services** (expertise-, review-, document-, notification-service) are not integrated —
  they do not exist within this work. The module implements its own side of the contract: it
  consumes `RequestAccepted` and publishes domain events plus notification intents.
- **Routing-rule visual builder** supports one level of AND/OR grouping over flat conditions, with a
  raw-JSON fallback for deeper nesting (the backend's condition-tree evaluator itself supports
  arbitrary nesting).
- **The organization/user directory** lives in another service, so the access model matches the
  organization claim it is handed rather than resolving hierarchies itself.
- **Public holidays** for the SLA calendar are a list in configuration, not a reference-data service.
- **The SLA scheduler** runs on every replica; work is idempotent per task and the outbox claims its
  batch with `SKIP LOCKED`, so replicas do not duplicate published events.

## Feature map

| Area | Where |
|------|-------|
| Templates, versions, routing rules, publication | `Шаблоны процессов`, `/api/process-definitions` |
| Start, suspend/resume, cancel, sub-processes | process card, `/api/process-instances` |
| Tasks: complete, reassign with a reason, bulk complete | `Задачи`, `/api/tasks` |
| SLA windows, business calendar, escalation, breach | `SLA политики`, background scheduler |
| Process journal and configuration journal | process card, `/{id}/journal` endpoints |
| Dashboard and CSV exports | `Аналитика`, `/api/analytics/summary`, `/export` endpoints |
| Domain events and notification intents | Kafka topics `workflow.*` |

## Verification status

Executed against a real toolchain (JDK 21, Maven 3.9, Node 22, Docker Engine 29):

- **Backend** — `mvn verify` is green: 37 unit tests plus 50 integration tests against Testcontainers
  Postgres 16 and Kafka. Coverage includes the golden path, the inbound `RequestAccepted` listener,
  JWT security with a WireMock issuer, a 39-case role/endpoint permission matrix, the OpenAPI and
  event-schema contract checks, pauses, cancellation cascade, the configuration journal, the
  dashboard, exports and the access model. Flyway migrations apply and `ddl-auto: validate` passes.
- **Frontend** — `npm run build` and `npm run lint` pass clean.
- **Running system** — Postgres, Kafka and Keycloak (realm auto-import included) started from
  `docker-compose.yml`, backend and Vite dev server run against them, the demo dataset seeded, and
  the Playwright suite passes 10/10: the golden path (start → task → complete → COMPLETED + event
  journal), reassignment with a mandatory reason, role restrictions, suspend/resume, sub-process
  start and the walk back to the parent, the route map, bulk completion, the analyst dashboard with
  a CSV download, and saved views.
- **Demo dataset** — every item in [TEST-DATA.md](TEST-DATA.md) was created and checked on that
  running stack, including the documented HTTP codes for the negative cases and the live SLA run
  (escalation to MANAGER at 40%, to ADMIN at 80%, breach at 100%).
- **Specification coverage** — [TZ-COMPLIANCE.md](TZ-COMPLIANCE.md) walks every section of
  TZ-02-WORKFLOW; what remains open there is scope that belongs to other services.
- **Not verified here** — the two application image builds (`docker compose up --build`): the
  environment used for this check could not reach Docker Hub and the Maven/npm registries from
  inside build containers. Everything the images run was exercised from source instead.
