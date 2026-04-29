# Architecture Research

**Domain:** HIPAA-compliant healthcare workflow monitoring system (Java + Temporal.io)
**Researched:** 2026-04-29
**Confidence:** HIGH (Temporal Spring Boot integration verified via official docs; HIPAA patterns verified via multiple authoritative sources)

## Standard Architecture

### System Overview

```
┌──────────────────────────────────────────────────────────────────────┐
│                        PRESENTATION LAYER                            │
│  ┌──────────────────────────────────────────────────────────────┐    │
│  │          React + TypeScript Dashboard (SPA)                  │    │
│  │   Alert Cards | Pathway Viz | Patient List | Admin Config    │    │
│  └──────────────────────┬───────────────────────────────────────┘    │
└─────────────────────────│────────────────────────────────────────────┘
                          │ HTTPS + WebSocket (STOMP/SockJS)
┌─────────────────────────▼────────────────────────────────────────────┐
│                        APPLICATION LAYER                             │
│  ┌────────────────┐  ┌────────────────┐  ┌────────────────────────┐  │
│  │  REST API      │  │  WebSocket     │  │  Security Layer        │  │
│  │  Controllers   │  │  Broker        │  │  Spring Security+RBAC  │  │
│  │  (Spring MVC)  │  │  (STOMP)       │  │  JWT / Audit Filter    │  │
│  └───────┬────────┘  └───────┬────────┘  └────────────────────────┘  │
│          │                  │                                        │
│  ┌───────▼──────────────────▼────────────────────────────────────┐   │
│  │                     Service Layer                             │   │
│  │  PatientService | AlertService | PathwayConfigService |       │   │
│  │  AuditService | ClaudeAlertService                           │   │
│  └───────┬────────────────────────────────────────┬─────────────┘   │
└──────────│────────────────────────────────────────│─────────────────┘
           │ WorkflowClient.start() / signal()      │ query()
┌──────────▼────────────────────────────────────────▼─────────────────┐
│                     WORKFLOW ORCHESTRATION LAYER                     │
│                    (Temporal.io — self-hosted)                       │
│  ┌──────────────────────────────────────────────────────────────┐    │
│  │              Temporal Server (Frontend+History+Matching)     │    │
│  │                    Task Queue: "pathway-worker"              │    │
│  └───────────────────────────────┬──────────────────────────────┘    │
│                                  │ poll                              │
│  ┌────────────────────────────────▼─────────────────────────────┐    │
│  │                    Spring Boot Worker Process                │    │
│  │  ┌───────────────────────┐  ┌───────────────────────────┐   │    │
│  │  │  PatientPathway       │  │  Activities               │   │    │
│  │  │  Workflow (impl)      │  │  DeviationDetector        │   │    │
│  │  │  - @WorkflowImpl      │  │  AlertPersistence         │   │    │
│  │  │  - Timers (sleep)     │  │  ClaudeAlertGenerator     │   │    │
│  │  │  - Signal handlers    │  │  AuditLogger              │   │    │
│  │  │  - Query handlers     │  │  PathwayEvaluator         │   │    │
│  │  │  - Continue-As-New    │  │  @ActivityImpl            │   │    │
│  │  └───────────────────────┘  └───────────────────────────┘   │    │
│  └──────────────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────────────┘
           │                             │
┌──────────▼─────────────────────────────▼────────────────────────────┐
│                         DATA LAYER                                   │
│  ┌───────────────────────┐  ┌────────────────────┐  ┌────────────┐  │
│  │  PostgreSQL (app DB)  │  │  Temporal DB       │  │  External  │  │
│  │  patients             │  │  (PostgreSQL)      │  │  Claude    │  │
│  │  care_events          │  │  workflow state    │  │  API       │  │
│  │  alerts               │  │  event history     │  │            │  │
│  │  audit_log            │  │  task queues       │  └────────────┘  │
│  │  pathway_templates    │  └────────────────────┘                  │
│  │  (encrypted at rest)  │                                          │
│  └───────────────────────┘                                          │
└──────────────────────────────────────────────────────────────────────┘
```

### Component Responsibilities

| Component | Responsibility | Notes |
|-----------|----------------|-------|
| React Dashboard | Alert management, patient pathway visualization, manual event entry, admin pathway config | Reads via REST + receives push via WebSocket |
| Spring MVC Controllers | REST API entry points for CRUD on patients, events, alerts, pathways | Validates input, enforces RBAC, delegates to services |
| WebSocket Broker (STOMP/SockJS) | Push real-time alert notifications to connected nurse navigators | Spring `@EnableWebSocketMessageBroker`; `/topic/alerts` per role |
| Spring Security + Audit Filter | RBAC enforcement, JWT validation, request-level audit capture | Every PHI access logged before reaching controller logic |
| Service Layer | Orchestrates domain operations; translates app requests into Temporal workflow commands | The boundary between app and Temporal — calls `WorkflowClient` |
| Temporal Server | Durable execution: task scheduling, history persistence, timers, retries | Self-hosted; two PostgreSQL schemas: one for workflow state, one for visibility |
| PatientPathwayWorkflow | One durable workflow instance per patient enrollment; owns the pathway state machine | Pure orchestration — no I/O, no DB calls; only Activity invocations |
| Activities | All side effects: DB writes, deviation evaluation, alert generation, Claude API calls, audit writes | Must be idempotent; each has its own retry policy |
| App PostgreSQL | Patient demographics, care events (entered manually), alerts, audit log, pathway templates | Application-owned data; separate from Temporal's DB |
| Claude API | Generates natural-language alert text for non-template deviations | Called from `ClaudeAlertGeneratorActivity` only; template text used for known deviations |

## Recommended Project Structure

```
src/main/java/com/onconavigator/
├── api/                        # REST controllers (Spring MVC)
│   ├── PatientController.java
│   ├── CareEventController.java
│   ├── AlertController.java
│   └── PathwayConfigController.java
│
├── websocket/                  # WebSocket configuration and push
│   ├── WebSocketConfig.java
│   └── AlertNotificationService.java
│
├── service/                    # Application services (orchestration boundary)
│   ├── PatientService.java
│   ├── AlertService.java
│   ├── PathwayEnrollmentService.java    # starts Temporal workflows
│   ├── PathwayConfigService.java
│   └── ClaudeAlertService.java
│
├── workflow/                   # Temporal workflow interfaces + implementations
│   ├── PatientPathwayWorkflow.java      # @WorkflowInterface
│   ├── PatientPathwayWorkflowImpl.java  # @WorkflowImpl
│   └── model/
│       ├── PathwayState.java           # serializable workflow state (for CAN)
│       └── CareEventSignal.java        # signal payload
│
├── activity/                   # Temporal activity interfaces + implementations
│   ├── DeviationDetectorActivity.java
│   ├── DeviationDetectorActivityImpl.java
│   ├── AlertPersistenceActivity.java
│   ├── AlertPersistenceActivityImpl.java
│   ├── ClaudeAlertGeneratorActivity.java
│   ├── ClaudeAlertGeneratorActivityImpl.java
│   └── AuditLogActivity.java
│   └── AuditLogActivityImpl.java
│
├── worker/                     # Worker registration + Spring Boot wiring
│   └── WorkerConfig.java       # registers workflow + activity impls with task queue
│
├── domain/                     # Domain entities and JPA models
│   ├── Patient.java
│   ├── CareEvent.java
│   ├── Alert.java
│   ├── AuditLogEntry.java
│   └── PathwayTemplate.java
│
├── repository/                 # Spring Data JPA repositories
│   ├── PatientRepository.java
│   ├── CareEventRepository.java
│   ├── AlertRepository.java
│   └── AuditLogRepository.java
│
├── security/                   # RBAC + audit filter
│   ├── SecurityConfig.java
│   ├── JwtAuthFilter.java
│   └── AuditLoggingFilter.java         # writes PHI-access events before controller
│
├── config/                     # App-wide configuration
│   ├── TemporalConfig.java             # WorkflowClient bean, WorkerFactory bean
│   ├── EncryptionConfig.java           # AES key management
│   └── ClaudeApiConfig.java
│
└── pathway/                    # Pathway template definitions (config-as-data)
    ├── PathwayTemplateLoader.java
    └── templates/
        ├── breast-cancer.json
        ├── lung-cancer.json
        └── colorectal-cancer.json
```

### Structure Rationale

- **workflow/ vs activity/**: Temporal's hard contract — workflows must be deterministic (no I/O), activities handle all side effects. Separation by package enforces this discipline at the filesystem level.
- **service/**: The seam between the REST/WebSocket world and Temporal. Controllers never touch `WorkflowClient` directly — always through a service. This keeps controllers thin and makes Temporal replaceable in tests.
- **worker/**: Worker registration is explicit configuration, not magic. `@WorkflowImpl` / `@ActivityImpl` annotations enable Spring Boot auto-discovery, but a single `WorkerConfig` class owns the task queue name, keeping it auditable.
- **pathway/templates/**: Pathways are JSON data, not Java code. This is the key extensibility decision — adding a new cancer type is a data operation, not a code change.
- **security/**: `AuditLoggingFilter` runs before controllers so that even rejected requests generate audit entries, meeting HIPAA's requirement to log access *attempts*.

## Architectural Patterns

### Pattern 1: One Workflow Per Patient Enrollment

**What:** When a patient is enrolled on a pathway, the service layer starts a `PatientPathwayWorkflow` execution using the patient ID as the Workflow ID (`patient-{id}-{pathwayType}`). This workflow instance runs for the entire pathway duration (weeks to months).

**When to use:** Always — this is the foundational design. Every pathway check, timer wait, and deviation detection happens inside this workflow.

**Trade-offs:** Durable and crash-safe by default; workflow history grows with each care event. Mitigated with Continue-As-New after ~40K events (well below the 50K limit).

**Example:**
```java
@WorkflowInterface
public interface PatientPathwayWorkflow {
    @WorkflowMethod
    void execute(PathwayEnrollment enrollment);

    @SignalMethod
    void recordCareEvent(CareEventSignal event);   // pushed when staff enters event

    @QueryMethod
    PathwayState getPathwayState();                // dashboard polls current status

    @SignalMethod
    void resolveAlert(String alertId, String resolution);
}
```

### Pattern 2: Signal-Driven Event Ingestion

**What:** When a nurse navigator enters a care event (e.g., "surgery completed on 2026-05-15"), the REST controller calls `PatientService.recordCareEvent()`, which sends a Temporal Signal to the running workflow via `WorkflowClient`. The workflow handler updates its state and re-evaluates the pathway.

**When to use:** Every time external data arrives that changes the patient's pathway state. Signals are asynchronous, durable, and survive worker restarts.

**Trade-offs:** Fire-and-forget from the caller's perspective (signal delivery is guaranteed, processing is async). Use Temporal Updates instead if the REST API needs a synchronous acknowledgment that the event was processed.

**Data flow:**
```
Nurse enters care event
    → POST /api/care-events
    → CareEventController
    → PatientService.recordCareEvent()
    → workflowStub.recordCareEvent(signal)    [Temporal Signal]
    → workflow receives signal, updates PathwayState
    → workflow calls DeviationDetectorActivity
    → if deviation: calls AlertPersistenceActivity → DB
    → AlertPersistenceActivity publishes to WebSocket broker
    → nurse navigators receive real-time alert push
```

### Pattern 3: Timer-Based Deviation Detection

**What:** After recording that a prerequisite step completed (e.g., surgical referral sent), the workflow calls `Workflow.sleep(Duration.ofDays(14))`. When the timer fires, the workflow checks whether the expected next step (surgical consultation) was recorded. If not, it creates a "delayed event" alert.

**When to use:** For every time-window constraint in the pathway template (e.g., "consultation should occur within 14 days of referral").

**Trade-offs:** Timers survive worker and server restarts — no cron jobs, no polling. The timer sleep does not consume thread resources. History cost: ~10 events per timer, well within limits for a pathway with ~20 steps.

**Example:**
```java
// Inside PatientPathwayWorkflowImpl
Workflow.sleep(Duration.ofDays(template.getConsultationWindowDays()));
if (!state.hasEvent(CareEventType.SURGICAL_CONSULTATION)) {
    activities.createDeviationAlert(state.getPatientId(), AlertType.DELAYED_CONSULTATION);
}
```

### Pattern 4: Activities as Idempotent Side-Effect Boundaries

**What:** Every database write, external API call, and audit log entry happens inside an Activity, never in the Workflow. Each Activity uses a deterministic idempotency key (`workflowId + activityId`) so retries produce the same outcome.

**When to use:** All the time — this is non-negotiable in Temporal's model.

**Trade-offs:** Requires writing Activity interfaces even for simple DB calls. The payoff is that Temporal can retry failed activities automatically without replaying workflow history.

### Pattern 5: Continue-As-New for History Management

**What:** The workflow checks `Workflow.isContinueAsNewSuggested()` (or checks event count threshold) before sleeping for the next timer. If the threshold is approaching, it serializes `PathwayState` and calls `Workflow.continueAsNew()` with the current state as input to the next run.

**When to use:** For patients on long pathways (3-6 months of treatment). Less critical for pathways that complete in 4-6 weeks, but implement from the start.

**Trade-offs:** Requires `PathwayState` to be a complete, self-contained snapshot of all workflow state. Signal draining before CAN is mandatory to avoid losing in-flight events.

## Data Flow

### Request Flow: Care Event Entry

```
Nurse Navigator (browser)
    │
    ▼
POST /api/care-events  {patientId, eventType, occurredAt, notes}
    │  Spring Security: validates JWT, extracts role
    │  AuditLoggingFilter: writes PHI-access audit entry
    ▼
CareEventController.recordEvent()
    │  validates payload, persists CareEvent to PostgreSQL
    ▼
PatientService.notifyWorkflow(patientId, careEvent)
    │  looks up Temporal workflow ID for patient
    ▼
WorkflowClient → Signal: recordCareEvent(CareEventSignal)
    │  Temporal persists signal, delivers to worker
    ▼
PatientPathwayWorkflowImpl.recordCareEvent(signal)
    │  updates in-memory PathwayState
    ▼
DeviationDetectorActivity.evaluate(state, template)
    │  compares current state against pathway template rules
    │  returns List<Deviation>
    ▼
[if deviations found]
AlertPersistenceActivity.persist(deviations)
    │  writes Alert records to PostgreSQL
    │  publishes to WebSocket broker: /topic/alerts
    ▼
AlertNotificationService → STOMP broadcast
    │
    ▼
React Dashboard: alert card appears in real time
```

### Request Flow: Dashboard Alert Query

```
Nurse Navigator loads dashboard
    │
    ▼
GET /api/alerts?status=OPEN&assignedTo=me
    │  JWT validated, role checked (NURSE_NAVIGATOR)
    │  AuditLoggingFilter: logs data access
    ▼
AlertController → AlertService → AlertRepository → PostgreSQL
    │
    ▼
JSON response: List<Alert> with patient context
```

### Request Flow: Pathway State Query (Real-Time)

```
Dashboard: PatientPathwayView component mounts
    │
    ▼
GET /api/patients/{id}/pathway-state
    │
    ▼
PatientService.getPathwayState(patientId)
    │  resolves Temporal workflow ID
    ▼
WorkflowClient → Query: getPathwayState()
    │  Temporal routes to worker, executes query handler synchronously
    │  returns PathwayState (step completion, current step, pending alerts)
    ▼
Controller serializes and returns
    │
    ▼
Dashboard renders pathway timeline visualization
```

### HIPAA Audit Data Flow

```
Every HTTP request
    │
    ▼
AuditLoggingFilter (before controller)
    │  captures: user, role, endpoint, patientId (from path/body), timestamp, ip
    ▼
Async write to audit_log table (separate DB connection, append-only)
    │
    ▼
audit_log: id | user_id | action | resource_type | resource_id |
               timestamp | ip_address | success | detail_hash
```

### Alert Generation Flow (Claude API Path)

```
DeviationDetectorActivity detects non-standard deviation
    │  no template match for this deviation pattern
    ▼
ClaudeAlertGeneratorActivity.generate(deviation, patientContext)
    │  sends sanitized context to Claude API (no raw PHI in prompt)
    │  Claude returns natural-language alert text + suggested action
    ▼
AlertPersistenceActivity.persist(alert with claudeGeneratedText)
```

## Scaling Considerations

| Scale | Architecture Adjustments |
|-------|--------------------------|
| 1 pilot practice, ~200 active patients | Single Spring Boot process hosting both API and Worker; Docker Compose; Temporal with PostgreSQL backend. No changes needed from day-one architecture. |
| 5-10 practices, ~2000 active patients | Separate Worker process from API process (same codebase, different Spring profiles). Scale Workers horizontally by adding replicas — Temporal load-balances via task queues automatically. |
| 50+ practices, 20K+ patients | Consider namespace-per-practice isolation in Temporal. Add read replicas for PostgreSQL. Introduce caching layer (Redis) for frequently-queried alert lists. Temporal visibility via Elasticsearch. |

### Scaling Priorities

1. **First bottleneck:** Temporal Worker CPU under high signal throughput. Fix: add Worker replicas — they're stateless and pick up tasks from the same task queue automatically.
2. **Second bottleneck:** PostgreSQL write throughput on audit_log table (append-heavy). Fix: partition audit_log by month; consider TimescaleDB or separate logging store.

## Anti-Patterns

### Anti-Pattern 1: I/O Inside Workflow Code

**What people do:** Call a database or REST API directly from `PatientPathwayWorkflowImpl` using `@Autowired` repositories.

**Why it's wrong:** Workflows must be deterministic (replayed from history). Non-deterministic I/O in workflow code causes replay failures and corrupted state. Temporal's determinism checker will throw `DeterminismViolationError`.

**Do this instead:** Wrap every side effect in an Activity. The workflow calls the Activity stub; Temporal handles retry and history recording.

### Anti-Pattern 2: Storing PHI in Workflow Input/History

**What people do:** Pass full patient records (name, DOB, diagnosis) as workflow arguments or in Signal payloads.

**Why it's wrong:** Temporal workflow history is stored in Temporal's database (not the application DB). This creates a second PHI store with potentially different encryption controls, complicating HIPAA scope.

**Do this instead:** Pass only opaque identifiers (patient ID, care event ID) in workflow inputs and signals. Activities look up full records from the application PostgreSQL database when needed. Temporal history contains only IDs, not PHI.

### Anti-Pattern 3: Cron Jobs for Deviation Detection

**What people do:** Write a scheduled `@Scheduled` Spring task that queries all patients every hour and checks for overdue events.

**Why it's wrong:** Stateless polling can't reliably handle the full temporal logic (e.g., "14 days after step X, if step Y hasn't occurred"). It races with event entry. It requires the application to be running when the deadline passes. It can't survive restarts mid-check.

**Do this instead:** Use Temporal `Workflow.sleep()` timers anchored to actual event times. The timer fires exactly when the deadline passes, regardless of worker restarts.

### Anti-Pattern 4: Shared Workflow ID Namespace

**What people do:** Use simple IDs like `patient-123` as the Temporal Workflow ID.

**Why it's wrong:** When re-enrolling a patient on a second pathway (e.g., recurrence), the old workflow ID collides. Also exposes patient IDs in Temporal logs and history without isolation.

**Do this instead:** Use compound IDs: `patient-{patientId}-{pathwayType}-{enrollmentId}`. This scopes each workflow to a specific enrollment and prevents collision.

### Anti-Pattern 5: Alert Delivery Only Through Temporal

**What people do:** Route alert delivery (WebSocket push, email) through Temporal Activities to leverage retry semantics.

**Why it's wrong:** WebSocket push is inherently ephemeral — if the client disconnected, no retry helps. This conflates durable storage (persist the alert in the DB) with ephemeral delivery (push to connected clients).

**Do this instead:** Activity persists the alert to PostgreSQL. The Service layer listens for DB-written alerts (via an application event, not polling) and pushes to WebSocket. Clients poll on reconnect.

## Integration Points

### External Services

| Service | Integration Pattern | Notes |
|---------|---------------------|-------|
| Claude API | HTTP from `ClaudeAlertGeneratorActivity`; called only for non-template deviations | No PHI in prompts — pass anonymized clinical context. Retry with exponential backoff. |
| Temporal Server (self-hosted) | `WorkflowClient` bean via `temporal-spring-boot-starter`; auto-configured from `application.yml` | Two separate PostgreSQL databases: one for Temporal, one for app data |
| PostgreSQL (app) | Spring Data JPA; connection pool via HikariCP | Encryption at rest via filesystem/volume encryption + column-level for sensitive fields |
| React Frontend | REST (Spring MVC) + WebSocket (STOMP over SockJS) | CORS locked to known origins; JWT required on every REST call |

### Internal Boundaries

| Boundary | Communication | Notes |
|----------|---------------|-------|
| Controller → Service | Direct Java call (Spring DI) | Controllers never touch repositories or Temporal directly |
| Service → Temporal | `WorkflowClient.start()`, `stub.signal()`, `stub.query()` | Workflow ID constructed deterministically from patient + enrollment identifiers |
| Workflow → Activity | Temporal Activity stub (generated by SDK) | Each activity has explicit `startToCloseTimeout` and retry policy |
| Activity → PostgreSQL | Spring Data JPA (Activities are Spring beans via `@ActivityImpl`) | Audit activities use a separate, append-only connection pool |
| Activity → WebSocket | Application event published to Spring's `ApplicationEventPublisher`; `AlertNotificationService` handles | Decouples Temporal activity execution from WebSocket infrastructure |
| Activity → Claude API | `ClaudeAlertGeneratorActivityImpl` calls `ClaudeApiClient` (HTTP) | Wrapped in circuit breaker; falls back to template text on failure |

## Build Order Implications

Dependencies between components determine which phases must come first:

1. **Foundation first:** Domain models (Patient, CareEvent, Alert, AuditLogEntry) + PostgreSQL schema + Spring Security skeleton. Everything else depends on this.
2. **Temporal infrastructure second:** `TemporalConfig`, Worker registration, and the workflow/activity skeleton with no-op implementations. Proves the Temporal integration works before any business logic.
3. **Core workflow loop third:** `PatientPathwayWorkflow` with signal handling, one pathway template (breast cancer), and `DeviationDetectorActivity`. This is the system's heart — get it right before building UI on top.
4. **REST API fourth:** Controllers and services that allow staff to enter data and trigger workflow signals. The workflow must exist before the API can signal it.
5. **Dashboard fifth:** React UI is a consumer of the API and WebSocket. It can be built in parallel with the REST API if API contracts are defined first (OpenAPI spec).
6. **HIPAA hardening throughout:** Audit filter, encryption, and RBAC are not a phase — they are built into phases 1-4. The audit filter goes in at step 1; encryption at step 1 (schema design); RBAC at step 1 (Spring Security).
7. **Claude API last:** Template-based alert text covers the standard cases. Claude integration for edge cases is the final layer added once the core deviation detection is validated.

## Sources

- [Temporal Spring Boot Integration — Official Docs](https://docs.temporal.io/develop/java/spring-boot-integration) — HIGH confidence
- [Temporal Workflow Message Passing — Official Docs](https://docs.temporal.io/encyclopedia/workflow-message-passing) — HIGH confidence
- [Managing Very Long-Running Workflows — Temporal Blog](https://temporal.io/blog/very-long-running-workflows) — HIGH confidence
- [Enterprise Workflows with Temporal Architecture — Xgrid](https://www.xgrid.co/resources/enterprise-workflows-with-temporal-architecture/) — MEDIUM confidence
- [Activity Count / Granularity — Temporal Blog](https://temporal.io/blog/how-many-activities-should-i-use-in-my-temporal-workflow) — HIGH confidence
- [HIPAA-Ready Spring Boot Security — AccountableHQ](https://www.accountablehq.com/post/how-to-configure-spring-boot-security-for-healthcare-hipaa-ready-setup-with-oauth2-and-jwt) — MEDIUM confidence
- [Building a HIPAA-Grade Audit Logging System — Medium/Keshav Agrawal](https://medium.com/@keshavagrawal/building-a-hipaa-grade-audit-logging-system-lessons-from-the-healthcare-trenches-d5a8bb691e3b) — MEDIUM confidence
- [Temporal + Spring Boot Demo — GitHub (official)](https://github.com/temporalio/spring-boot-demo) — HIGH confidence
- [Temporal Architecture Separation of Concerns — Community Forum](https://community.temporal.io/t/architecture-design-for-separation-of-business-logic-of-workflow-from-worker/7996) — MEDIUM confidence
- [WebSocket Real-Time Notifications with Spring Boot — Dev.to](https://dev.to/javafullstackdev/real-time-notifications-with-websocket-in-spring-boot-40ao) — MEDIUM confidence

---
*Architecture research for: HIPAA-compliant oncology care pathway monitoring system (Onco-Navigator)*
*Researched: 2026-04-29*
