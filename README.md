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
```

## Running locally

Requires Docker and Docker Compose.

```bash
docker compose up -d --build
```

This starts, in order (via healthchecks): Postgres, Kafka (KRaft, single node), Keycloak (realm
`workflow` auto-imported from `infra/keycloak/realm-export.json`), the backend on
`http://localhost:8090`, and the frontend on `http://localhost:5173`.

Backend health: `http://localhost:8090/actuator/health`. API docs: `http://localhost:8090/swagger-ui.html`.

### Demo users (Keycloak realm `workflow`)

| Username       | Password        | Role        |
|----------------|-----------------|-------------|
| `admin1`       | `admin123`      | ADMIN       |
| `manager1`     | `manager123`    | MANAGER     |
| `coordinator1` | `coordinator123`| COORDINATOR |
| `analyst1`     | `analyst123`    | ANALYST     |

### Seed a demo process

The module ships with no process templates out of the box (they're created through the UI/API).
A seed script creates one demo template end-to-end (SLA policy → process definition → routing rule
→ publish) so there's something to walk through immediately:

```bash
cd frontend/workflow-ui
npm install
npm run seed
```

### Golden-path walkthrough (maps to TZ §12 acceptance criteria)

The UI is in Russian; English names of the corresponding screen are given in brackets.

1. Open `http://localhost:5173`, log in as `coordinator1`.
2. **Процессы [Processes] → Запустить процесс [Start new process]** — pick the seeded template,
   give it a business key, set attributes to `{"requestType": "COMPLEX"}`, start it. The instance
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
`localhost:8090` in dev). `npm run build` / `npm run lint` / `npm run e2e` (Playwright, needs the
full stack running — see `docker-compose up` above).

## Design system

The UI follows a dark glassmorphism theme (mesh-gradient background, translucent blurred panels,
indigo accent, emerald/amber status badges) defined in `frontend/workflow-ui/src/colors.ts` and
wired into the MUI theme in `frontend/workflow-ui/src/theme.ts`.

## Known simplifications (documented, not oversights)

- **SLA scheduler** is a single-node `@Scheduled` poller, not a clustered job runner — adequate for
  a practice-project deployment, not for horizontally-scaled production use.
- **Process map visualization** is a linear MUI `Stepper` + event-history list rather than a full
  graph-diagram editor.
- **Routing-rule visual builder** supports one level of AND/OR grouping over flat conditions, with a
  raw-JSON fallback for deeper nesting (the backend's condition-tree evaluator itself supports
  arbitrary nesting).
- **Attribute-level access checks** (e.g., "this applicant may only see their own case") are left as
  a pluggable extension point since the owning org/user directory service isn't part of this module.

## Verification status

This was built without a local JDK 21 / Maven / Docker toolchain available in the authoring
environment. What was actually verified here:

- Frontend: `npm run build` and `npm run lint` both pass clean against the real toolchain.
- Backend, Docker Compose, and Playwright e2e: written and carefully reviewed, but **not
  compiled/executed locally**. Before relying on this, run:
  ```bash
  cd backend/workflow-service && mvn verify
  docker compose up -d --build
  ```
  and work through the golden-path walkthrough above.
