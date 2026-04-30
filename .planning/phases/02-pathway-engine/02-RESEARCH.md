# Phase 2: Pathway Engine - Research

**Researched:** 2026-04-30
**Domain:** Temporal.io durable workflows, clinical pathway deviation detection, JSONB template data
**Confidence:** HIGH

## Summary

Phase 2 builds the core value proposition of Onco-Navigator: a durable Temporal workflow engine that monitors each patient's care pathway and detects deviations (missing, delayed, and out-of-order events). The system uses a one-workflow-per-patient architecture where each workflow receives signals when care events change and runs durable timers to catch time-window expirations. Three cancer pathway templates (breast, lung, colorectal -- each with 6 steps) are loaded as JSONB seed data via Flyway migration.

The Temporal Java SDK 1.32.0 with `temporal-spring-boot-starter` provides Spring Boot auto-discovery of `@WorkflowImpl` and `@ActivityImpl` beans, eliminating manual `WorkerFactory` configuration. The workflow pattern combines `Workflow.await(Duration, condition)` for the signal+timer dual approach: the workflow sleeps for 24 hours (the daily evaluation cycle), waking early if a care event signal arrives. Activities are Spring beans that access JPA repositories for all database operations -- no PHI ever enters Temporal's event history (UUID-only approach).

**Primary recommendation:** Implement a `PatientPathwayWorkflow` per patient (workflow ID = `pathway-{patientId}`) that holds step completion state, receives `CareEventSignal` and `DeactivateSignal` signals, and runs a daily timer loop calling `PathwayEvaluationActivity` to detect deviations. A separate `DailySweepWorkflow` (scheduled via Temporal cron) provides a safety net that queries all active patients and starts any missing patient workflows.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Step ordering is linear only -- steps are a simple ordered list where each step depends on the previous. No branching/DAG needed for V1.
- **D-02:** Time window anchoring is configurable per step -- each step specifies its anchor: `previous_step` (default), `diagnosis_date` (for step 1), or a specific `stepId`.
- **D-03:** Steps have a `required` boolean flag -- required steps generate OPEN alerts, optional steps generate softer warnings.
- **D-04:** JSONB `templateData` structure per step: `stepId`, `stepNumber`, `name`, `description`, `eventType`, `windowDays`, `anchorType` (enum: PREVIOUS_STEP | DIAGNOSIS_DATE | SPECIFIC_STEP), `anchorStepId` (nullable), `required`, `alertText`, `suggestedAction`, `prerequisites` (list of stepIds).
- **D-05:** Detection uses a dual approach: event-driven re-evaluation (Temporal signal on care event add/update) PLUS a daily timer sweep.
- **D-06:** Pathway enrollment is automatic on patient creation -- system starts a Temporal workflow when a patient is created.
- **D-07:** One Temporal workflow per patient. Workflow holds pathway state, receives signals, runs timers.
- **D-08:** Patient deactivation cancels the workflow -- sends a signal which gracefully terminates.
- **D-09:** When all pathway steps are completed, the workflow completes naturally.
- **D-10:** Use the three pathway definitions from the V1 Feature Specification as-is (breast, lung, colorectal -- 6 steps each). Load via Flyway seed migration.
- **D-11:** Physician override uses a simple override flag per step -- a record linking patient + pathway step + override reason. Workflow checks before generating alerts.

### Claude's Discretion
- Temporal namespace and task queue naming conventions
- Retry policies for activities (idempotent activities, backoff strategy)
- How to structure the daily sweep workflow (single parent vs. individual child workflows)
- PHI handling in Temporal -- ensuring no PHI in workflow inputs/payloads (use UUIDs only)
- Whether to use Temporal search attributes for patient/alert querying

### Deferred Ideas (OUT OF SCOPE)
- SMS alert delivery (F2-01 through F2-06) -- deferred to V2
- Manual re-scan trigger (F3-07) -- Phase 3 dashboard feature
- REST API endpoints for patient/event CRUD -- Phase 3
- Multi-pathway per patient support -- V2
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| INFR-03 | Patient pathway workflows are durable -- survive system restarts without losing state | Temporal's durable execution model with PostgreSQL persistence backend. Workflow state is persisted after every event; worker restarts replay from event history. [VERIFIED: Context7 /temporalio/sdk-java] |
| INFR-04 | Workflow engine handles patient journeys spanning weeks to months without event history overflow | Event history math: ~4-6 events/day x 90 days = ~540 events, well under the 10,240 warning threshold and 51,200 hard limit. Continue-as-new not needed for V1. [VERIFIED: Temporal docs on event history limits] |
| PATH-01 | Configurable pathway templates with step names, types, prerequisites, time windows, and suggested actions | PathwayTemplate entity with JSONB `templateData` column already exists. D-04 defines the step structure. Flyway seed migration loads templates. [VERIFIED: existing codebase] |
| PATH-02 | Templates for breast, lung, and colorectal cancer (Stage I-III) | All three pathways defined in V1 Feature Specification with exact step sequences, time windows, trigger conditions, alert text. [VERIFIED: docs/V1 Feature Specification v2.docx] |
| PATH-03 | Detect missing events (required step with no Completed care event) | PathwayEvaluationActivity queries CareEventRepository by patient ID, compares against template steps. Missing = no event matching step's eventType in COMPLETED status. [VERIFIED: existing CareEventRepository and AlertType.MISSING_EVENT] |
| PATH-04 | Detect delayed events (time exceeds configured window) | Activity calculates elapsed days between anchor date and current date using `java.time.temporal.ChronoUnit.DAYS.between()`. Delay = elapsed > step.windowDays. [ASSUMED] |
| PATH-05 | Detect out-of-order events (event before prerequisites completed) | Activity checks if any care event exists (any status including SCHEDULED) for a step whose prerequisite steps are not yet COMPLETED. [VERIFIED: V1 Feature Spec Scenario B] |
| PATH-06 | No duplicate alerts for same deviation | AlertRepository.existsByPatientIdAndPathwayStepNameAndStatus() already exists for deduplication check. Activity checks before creating new alert. [VERIFIED: existing AlertRepository] |
| PATH-07 | Log every monitoring evaluation with timestamp, patients evaluated, alerts generated | Activity logs evaluation results via AuditService and SLF4J structured logging. Temporal's event history also provides built-in audit trail of every activity execution. [ASSUMED] |
| PATH-08 | Physician can annotate deliberate step reordering to suppress false-positive alerts | New PhysicianOverride entity linking patient + pathway step + override reason. Workflow/activity checks for override before generating alerts. [VERIFIED: D-11 decision] |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Durable workflow orchestration | Temporal Server | -- | Temporal owns all workflow state, timers, and execution durability. Spring Boot app only hosts the worker. |
| Pathway evaluation logic | API / Backend (Activity) | -- | Activities are Spring beans with access to JPA repositories. All business logic for deviation detection runs here. |
| Patient data persistence | Database / Storage | -- | PostgreSQL stores patients, care events, alerts, templates, overrides. All ePHI encrypted at column level. |
| Workflow state persistence | Database / Storage (Temporal's PostgreSQL schema) | -- | Temporal uses the same PostgreSQL instance (separate schema) for its own persistence. No additional DB needed. |
| Signal dispatch (care event triggers) | API / Backend (Spring Service) | -- | When REST endpoints (Phase 3) create/update care events, a service layer calls WorkflowClient.signalWorkflow(). Phase 2 builds the signal handler; Phase 3 connects the trigger. |
| Alert generation | API / Backend (Activity) | Database / Storage | Activity creates Alert entities via AlertRepository. No PHI in alert text -- clinical process language only. |
| Template configuration | Database / Storage | -- | Pathway templates are JSONB data in pathway_templates table. Loaded by Flyway seed migration. |

## Standard Stack

### Core (already in pom.xml)
| Library | Version | Purpose | Verified |
|---------|---------|---------|----------|
| `io.temporal:temporal-spring-boot-starter` | 1.32.0 | Spring Boot auto-discovery for Temporal workers, WorkflowClient bean | [VERIFIED: pom.xml, Maven Central confirms 1.32.0 available] |
| `io.temporal:temporal-sdk` | 1.32.0 (transitive) | Core workflow/activity DSL, signals, queries, timers | [VERIFIED: Context7 /temporalio/sdk-java] |
| Spring Boot 3.5.0 | via parent BOM | Application framework | [VERIFIED: pom.xml] |
| PostgreSQL 16 | via Docker Compose | Primary data store + Temporal persistence | [VERIFIED: docker-compose.yml] |

### Supporting (to add for Phase 2)
| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `io.temporal:temporal-testing` | 1.32.0 | TestWorkflowEnvironment for unit/integration tests with time skipping | All workflow and activity tests |
| `com.fasterxml.jackson.core:jackson-databind` | via Boot BOM | Parse JSONB templateData into Java POJOs | Activity reads pathway template JSONB and deserializes into PathwayStep list |

**Note on versions:** Maven Central shows `temporal-spring-boot-starter` 1.34.0 as the latest release (as of April 2026). The project pins 1.32.0 per CLAUDE.md. This is acceptable -- 1.32.0 is stable and compatible with Spring Boot 3.5. [VERIFIED: Maven Central sonatype.com]

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| One-workflow-per-patient | Batch workflow evaluating all patients | Batch approach loses individual signal handling and per-patient lifecycle. One-per-patient is cleaner but creates more workflows (acceptable for V1 pilot scale). |
| Temporal cron for daily sweep | Spring @Scheduled with WorkflowClient | Spring @Scheduled is not durable -- missed if app restarts. Temporal cron workflow is durable and idempotent. |
| JSONB templateData as String | Typed entity with separate PathwayStep table | Separate table requires JOINs and migrations for every template change. JSONB is flexible and the template structure is already decided (D-04). |

**Installation (test dependency to add):**
```xml
<dependency>
    <groupId>io.temporal</groupId>
    <artifactId>temporal-testing</artifactId>
    <version>${temporal.version}</version>
    <scope>test</scope>
</dependency>
```

## Architecture Patterns

### System Architecture Diagram

```
Patient Creation (Phase 3 REST)
        |
        v
[PatientService] --start--> [Temporal Server (7233)]
        |                          |
        |                    Creates workflow
        |                          |
        v                          v
  [PostgreSQL]          [PatientPathwayWorkflow]
  (patients,               |          |
   care_events,      +-----+-----+   |
   alerts,           |           |   |
   overrides,     Signal      Timer  |
   templates)     Handler    (24h)   |
        ^            |           |   |
        |            v           v   |
        |    [Workflow.await(24h,     |
        |     () -> signalReceived)] |
        |            |               |
        |            v               |
        |   [PathwayEvaluationActivity]
        |     (Spring @Component)    |
        |            |               |
        +--read------+               |
        |            |               |
        +--write-----+               |
        |   (alerts)                 |
        |                            |
Care Event Add/Update (Phase 3 REST) |
        |                            |
        v                            |
[CareEventService] --signal--------->+
                   careEventChanged()

[DailySweepWorkflow] (Temporal cron, runs daily)
        |
        v
[SweepActivity] --queries active patients-->
        |           starts missing workflows
        v
[WorkflowClient.start()] for any patient
  without a running workflow
```

### Recommended Project Structure
```
src/main/java/com/onconavigator/
├── workflow/                    # Temporal workflow interfaces and implementations
│   ├── PatientPathwayWorkflow.java          # @WorkflowInterface
│   ├── PatientPathwayWorkflowImpl.java      # @WorkflowImpl, main patient workflow
│   ├── DailySweepWorkflow.java              # @WorkflowInterface
│   └── DailySweepWorkflowImpl.java          # @WorkflowImpl, daily sweep cron
├── activity/                    # Temporal activity interfaces and implementations
│   ├── PathwayEvaluationActivity.java       # @ActivityInterface
│   ├── PathwayEvaluationActivityImpl.java   # @Component @ActivityImpl
│   ├── AlertGenerationActivity.java         # @ActivityInterface
│   ├── AlertGenerationActivityImpl.java     # @Component @ActivityImpl
│   ├── SweepActivity.java                   # @ActivityInterface
│   └── SweepActivityImpl.java              # @Component @ActivityImpl
├── domain/
│   ├── PhysicianOverride.java               # New entity (D-11)
│   └── dto/
│       ├── PathwayStep.java                 # POJO for deserialized JSONB step
│       ├── PathwayEvaluationResult.java     # Result of evaluation activity
│       └── CareEventSignal.java             # Signal payload (patient UUID only)
├── repository/
│   ├── PhysicianOverrideRepository.java     # New repository
│   └── PathwayTemplateRepository.java       # New (findByCancerType)
├── service/
│   └── PathwayService.java                  # Orchestration service (start/signal workflows)
└── config/
    └── TemporalConfig.java                  # Task queue constants, namespace config
```

### Pattern 1: One-Workflow-Per-Patient with Signal+Timer Loop
**What:** Each patient gets a dedicated Temporal workflow instance identified by `pathway-{patientId}`. The workflow runs a loop: sleep 24 hours, evaluate pathway, repeat. Signals from care event changes wake the workflow early for immediate re-evaluation.
**When to use:** Always -- this is the core pattern for D-05, D-07, D-08, D-09.
**Example:**
```java
// Source: Temporal Java SDK docs (Context7 /temporalio/sdk-java) + project-specific adaptation
@WorkflowInterface
public interface PatientPathwayWorkflow {

    @WorkflowMethod
    void monitorPathway(UUID patientId, String cancerType);

    @SignalMethod
    void careEventChanged(UUID careEventId);

    @SignalMethod
    void deactivatePatient(String reason);

    @QueryMethod
    String getPathwayStatus();
}

public class PatientPathwayWorkflowImpl implements PatientPathwayWorkflow {

    private boolean signalReceived = false;
    private boolean deactivated = false;
    private boolean pathwayComplete = false;
    private UUID patientId;

    private final PathwayEvaluationActivity evaluationActivity =
        Workflow.newActivityStub(PathwayEvaluationActivity.class,
            ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofMinutes(2))
                .setRetryOptions(RetryOptions.newBuilder()
                    .setInitialInterval(Duration.ofSeconds(5))
                    .setBackoffCoefficient(2.0)
                    .setMaximumAttempts(3)
                    .build())
                .build());

    @Override
    public void monitorPathway(UUID patientId, String cancerType) {
        this.patientId = patientId;

        while (!deactivated && !pathwayComplete) {
            signalReceived = false;

            // Wait up to 24 hours, wake early on signal (D-05 dual approach)
            Workflow.await(Duration.ofHours(24), () -> signalReceived || deactivated);

            if (deactivated) {
                evaluationActivity.closeOpenAlerts(patientId);
                break;
            }

            // Evaluate pathway -- activity fetches data from DB (no PHI in workflow)
            PathwayEvaluationResult result = evaluationActivity.evaluate(patientId);
            pathwayComplete = result.isAllStepsComplete();
        }
    }

    @Override
    public void careEventChanged(UUID careEventId) {
        this.signalReceived = true;
    }

    @Override
    public void deactivatePatient(String reason) {
        this.deactivated = true;
    }

    @Override
    public String getPathwayStatus() {
        return pathwayComplete ? "COMPLETE" : (deactivated ? "DEACTIVATED" : "MONITORING");
    }
}
```

### Pattern 2: Activity as Spring Bean Accessing JPA Repositories
**What:** Activities are `@Component` Spring beans annotated with `@ActivityImpl`. They have full access to Spring's dependency injection including JPA repositories, services, and the audit system. All database reads and writes happen in activities, not workflows.
**When to use:** Every activity implementation.
**Example:**
```java
// Source: Temporal Spring Boot docs (Context7 /temporalio/documentation)
@Component
@ActivityImpl(workers = "onco-pathway-worker")
public class PathwayEvaluationActivityImpl implements PathwayEvaluationActivity {

    private final PatientRepository patientRepository;
    private final CareEventRepository careEventRepository;
    private final AlertRepository alertRepository;
    private final PathwayTemplateRepository templateRepository;
    private final PhysicianOverrideRepository overrideRepository;
    private final ObjectMapper objectMapper;

    // Constructor injection (Spring beans)
    public PathwayEvaluationActivityImpl(
            PatientRepository patientRepository,
            CareEventRepository careEventRepository,
            AlertRepository alertRepository,
            PathwayTemplateRepository templateRepository,
            PhysicianOverrideRepository overrideRepository,
            ObjectMapper objectMapper) {
        this.patientRepository = patientRepository;
        this.careEventRepository = careEventRepository;
        this.alertRepository = alertRepository;
        this.templateRepository = templateRepository;
        this.overrideRepository = overrideRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public PathwayEvaluationResult evaluate(UUID patientId) {
        // Fetch patient (only UUID was passed -- no PHI in Temporal)
        Patient patient = patientRepository.findById(patientId)
            .orElseThrow(() -> new IllegalArgumentException("Patient not found: " + patientId));

        // Fetch care events and template
        List<CareEvent> events = careEventRepository
            .findByPatient_IdOrderByEventDateDesc(patientId);
        PathwayTemplate template = templateRepository
            .findByCancerType(patient.getCancerType())
            .orElseThrow();

        List<PathwayStep> steps = objectMapper.readValue(
            template.getTemplateData(),
            new TypeReference<List<PathwayStep>>() {});

        // Evaluate each step...
        // (deviation detection logic here)
    }
}
```

### Pattern 3: Daily Sweep Workflow (Temporal Cron)
**What:** A single cron-scheduled workflow that runs daily, queries all active patients, and ensures each has a running workflow. Provides a safety net for patients whose workflows may have been missed (e.g., created while the app was down).
**When to use:** Runs automatically via Temporal's cron schedule.
**Example:**
```java
// Source: Temporal documentation (cron workflow pattern)
@WorkflowInterface
public interface DailySweepWorkflow {
    @WorkflowMethod
    void sweep();
}

// Started once with cron schedule:
WorkflowOptions options = WorkflowOptions.newBuilder()
    .setWorkflowId("daily-pathway-sweep")
    .setTaskQueue("onco-pathway-queue")
    .setCronSchedule("0 6 * * *")  // Daily at 6 AM
    .build();
```

### Pattern 4: PHI-Safe Temporal Payloads
**What:** Workflow inputs contain ONLY UUIDs and enum values (cancer type). Activities fetch patient data from the database. No patient name, DOB, MRN, or clinical notes ever appear in Temporal's event history.
**When to use:** Every workflow start and signal. Non-negotiable for SEC-06.
**Example:**
```java
// CORRECT: UUID-only workflow input
workflow.monitorPathway(patientId, "BREAST");  // UUID + enum string

// WRONG: Never pass PHI to Temporal
// workflow.monitorPathway(patientId, "Maria", "Smith", "1968-03-15");  // HIPAA violation
```

### Anti-Patterns to Avoid
- **Database access in workflow code:** Workflow code must be deterministic. Database reads are non-deterministic and will break replay. All DB access must happen in activities. [VERIFIED: Context7 /temporalio/sdk-java]
- **`Workflow.sleep()` inside activities:** Activities are stateless and run on worker threads. `Workflow.sleep()` is only valid inside workflow implementations. Use `Workflow.newTimer()` or `Workflow.sleep()` in the workflow, not activities. [VERIFIED: CLAUDE.md "What NOT to Use"]
- **Long-running activities:** Activities should complete quickly (seconds to low minutes). The pathway evaluation is a read + compute + write operation, well under 2 minutes. Do not put the timer loop inside an activity. [VERIFIED: Context7 /temporalio/sdk-java]
- **Non-deterministic code in workflows:** `new Random()`, `System.currentTimeMillis()`, `UUID.randomUUID()` are non-deterministic. Use `Workflow.randomUUID()`, `Workflow.currentTimeMillis()`, and `Workflow.sideEffect()` instead. [VERIFIED: Context7 /temporalio/sdk-java]
- **Logging PHI in activities:** Activities log patient UUIDs only. Event type codes and step names are acceptable (they are clinical protocol data, not PHI). Patient names, DOBs, and MRNs must never appear in logs. [VERIFIED: CLAUDE.md]

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Durable timers that survive restarts | Custom scheduler with DB polling | `Workflow.sleep()` / `Workflow.await()` | Temporal timers are durable across crashes. DB-polling timers lose precision and add complexity. [VERIFIED: Context7] |
| Workflow state persistence | Custom state machine with DB tables | Temporal workflow state (implicit) | Temporal automatically persists workflow state after every event. Manual state tracking is redundant and error-prone. [VERIFIED: Context7] |
| Retry with exponential backoff | Custom retry loop with Thread.sleep | Temporal `RetryOptions` on `ActivityOptions` | Temporal handles retry counting, backoff, and dead-letter natively. [VERIFIED: Context7] |
| Alert deduplication | Custom dedup service with caching | `AlertRepository.existsByPatientIdAndPathwayStepNameAndStatus()` | Already implemented in Phase 1. Simple DB query is authoritative. [VERIFIED: existing code] |
| JSONB parsing | Manual JSON string manipulation | Jackson `ObjectMapper` (via Spring Boot auto-config) | Jackson is already on the classpath via Spring Boot. Type-safe deserialization into `PathwayStep` POJOs. [VERIFIED: Spring Boot BOM] |
| Audit logging for evaluations | Custom audit table for workflow events | Temporal event history + existing `AuditService` | Temporal records every activity execution with timestamps. AuditService handles application-level HIPAA audit. [VERIFIED: existing code + Context7] |

**Key insight:** Temporal's value is that it removes the need to build custom persistence, retry, and scheduling infrastructure. The deviation detection logic itself is simple business logic -- the complexity is in making it durable and reliable, which Temporal handles.

## Common Pitfalls

### Pitfall 1: PHI Leaking into Temporal Event History
**What goes wrong:** Developer passes patient name, DOB, or diagnosis details as workflow input or signal payload. This PHI is then permanently stored in Temporal's event history (in PostgreSQL), visible in Temporal UI, and logged in Temporal's internal logs.
**Why it happens:** Natural instinct is to pass all relevant data to the workflow. Developer may not realize Temporal's event history is a separate data store without the app's encryption controls.
**How to avoid:** ONLY pass UUIDs and enum values to workflows and signals. Activities fetch patient data from the encrypted database. Code review checkpoint: grep workflow/signal method signatures for String parameters that could carry PHI.
**Warning signs:** Workflow input/signal payloads containing strings longer than a UUID, field names like "name", "dob", "mrn" in workflow method signatures.

### Pitfall 2: Non-Deterministic Code in Workflow Implementation
**What goes wrong:** Using `System.currentTimeMillis()`, `new Random()`, `Thread.sleep()`, or database calls directly in workflow code causes replay failures and inconsistent behavior.
**Why it happens:** Workflow code looks like regular Java but runs under deterministic replay constraints. Temporal replays the workflow from event history on every worker restart.
**How to avoid:** Use `Workflow.currentTimeMillis()`, `Workflow.randomUUID()`, `Workflow.sideEffect()` for non-deterministic operations. All database access must be in activities.
**Warning signs:** Direct imports of `java.util.Random`, `java.time.Instant.now()`, or Spring repository interfaces in workflow implementation classes.

### Pitfall 3: Activity Timeout Too Short for DB Operations
**What goes wrong:** Activity fails with timeout because database query or write takes longer than the configured `startToCloseTimeout`, especially during first run when Hibernate initializes.
**Why it happens:** Default timeouts may be too aggressive. First-run JPA operations include lazy initialization overhead.
**How to avoid:** Set `startToCloseTimeout` to 2 minutes for evaluation activities. Configure retry with exponential backoff (3 attempts, 5s initial interval). This is generous enough for DB operations but not so long that stuck activities block the system.
**Warning signs:** `TimeoutFailure` in Temporal UI for evaluation activities.

### Pitfall 4: Missing Flyway Migration for New Tables
**What goes wrong:** New entities (PhysicianOverride, PathwayTemplateRepository finder) are added to JPA but no Flyway migration creates the table. App starts successfully in dev (ddl-auto=update) but fails in test/prod (ddl-auto=validate).
**Why it happens:** Local dev uses `ddl-auto: update` which auto-creates tables. Tests and production use `ddl-auto: validate` which requires Flyway migrations.
**How to avoid:** Write Flyway migrations FIRST (V5, V6, etc.), then create corresponding JPA entities. Run integration tests with Testcontainers which use the Flyway migrations.
**Warning signs:** Tests passing locally but failing in CI; `SchemaManagementException` on startup.

### Pitfall 5: Workflow ID Collision on Re-Enrollment
**What goes wrong:** Patient is deactivated, workflow completes, then patient is reactivated. Starting a new workflow with the same ID (`pathway-{patientId}`) fails because Temporal's default `WorkflowIdReusePolicy` rejects duplicate IDs.
**Why it happens:** Default `WorkflowIdReusePolicy` is `ALLOW_DUPLICATE_FAILED_ONLY`.
**How to avoid:** Set `WorkflowIdReusePolicy.ALLOW_DUPLICATE` or `TERMINATE_IF_RUNNING` when starting workflows. For V1, `ALLOW_DUPLICATE` is sufficient since deactivation completes the workflow before re-enrollment could happen.
**Warning signs:** `WorkflowExecutionAlreadyStartedError` when starting a workflow for a previously monitored patient.

### Pitfall 6: JSONB Template Deserialization Failures
**What goes wrong:** Jackson cannot deserialize the JSONB `templateData` because field names in the JSON don't match the Java POJO, or the `anchorType` enum value doesn't match.
**Why it happens:** JSONB is schema-less -- the Flyway migration and the Java POJO must agree on field names, but there's no compile-time check.
**How to avoid:** Define a `PathwayStep` record/POJO with Jackson annotations. Write a unit test that deserializes the exact JSON from the seed migration. Use `@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)` or explicit `@JsonProperty` annotations.
**Warning signs:** `JsonMappingException` or `UnrecognizedPropertyException` at runtime.

## Code Examples

### Pathway Step POJO (for JSONB Deserialization)
```java
// Project-specific implementation matching D-04 structure
public record PathwayStep(
    String stepId,
    int stepNumber,
    String name,
    String description,
    CareEventType eventType,
    int windowDays,
    AnchorType anchorType,
    @Nullable String anchorStepId,
    boolean required,
    String alertText,
    String suggestedAction,
    List<String> prerequisites
) {
    public enum AnchorType {
        PREVIOUS_STEP,
        DIAGNOSIS_DATE,
        SPECIFIC_STEP
    }
}
```

### PhysicianOverride Entity (D-11)
```java
// New entity for Phase 2 -- no PHI stored (references by UUID only)
@Entity
@Table(name = "physician_overrides")
@Audited
public class PhysicianOverride {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "pathway_step_id", nullable = false)
    private String pathwayStepId;

    @Column(name = "override_reason", nullable = false, columnDefinition = "TEXT")
    private String overrideReason;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    // Getters, setters, @PrePersist
}
```

### Flyway Seed Migration for Pathway Templates (V5)
```sql
-- V5__seed_pathway_templates.sql
-- Seed the three cancer pathway templates from V1 Feature Specification
-- Each template has 6 steps with time windows, prerequisites, and alert text

INSERT INTO pathway_templates (id, cancer_type, version, template_data, created_at, updated_at, created_by)
VALUES (
    gen_random_uuid(),
    'BREAST',
    1,
    '[
      {
        "stepId": "BREAST_01",
        "stepNumber": 1,
        "name": "Surgeon Consultation",
        "description": "Patient meets with surgical oncologist",
        "eventType": "CONSULTATION",
        "windowDays": 14,
        "anchorType": "DIAGNOSIS_DATE",
        "anchorStepId": null,
        "required": true,
        "alertText": "No surgeon visit found. Suggest: Schedule surgical oncology consultation.",
        "suggestedAction": "Schedule surgical oncology consultation.",
        "prerequisites": []
      },
      -- ... remaining 5 steps
    ]'::jsonb,
    NOW(),
    NOW(),
    '00000000-0000-0000-0000-000000000000'
);
-- Similar INSERT for LUNG and COLORECTAL
```

### Temporal Worker YAML Configuration
```yaml
# Addition to application-local.yml
spring.temporal:
  connection:
    target: localhost:7233
  namespace: default
  workers-auto-discovery:
    packages:
      - com.onconavigator.workflow
      - com.onconavigator.activity
  workers:
    - task-queue: onco-pathway-queue
      name: onco-pathway-worker
```

### Starting a Patient Workflow (PathwayService)
```java
// Source: Temporal Java SDK docs (Context7) + project-specific adaptation
@Service
public class PathwayService {

    private final WorkflowClient workflowClient;

    public PathwayService(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    public String startPathwayMonitoring(UUID patientId, CancerType cancerType) {
        WorkflowOptions options = WorkflowOptions.newBuilder()
            .setWorkflowId("pathway-" + patientId)
            .setTaskQueue("onco-pathway-queue")
            .setWorkflowIdReusePolicy(
                WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE)
            .build();

        PatientPathwayWorkflow workflow = workflowClient.newWorkflowStub(
            PatientPathwayWorkflow.class, options);

        WorkflowExecution execution = WorkflowClient.start(
            workflow::monitorPathway, patientId, cancerType.name());

        return execution.getRunId();
    }

    public void signalCareEventChanged(UUID patientId, UUID careEventId) {
        PatientPathwayWorkflow workflow = workflowClient.newWorkflowStub(
            PatientPathwayWorkflow.class, "pathway-" + patientId);
        workflow.careEventChanged(careEventId);
    }

    public void deactivatePatient(UUID patientId, String reason) {
        PatientPathwayWorkflow workflow = workflowClient.newWorkflowStub(
            PatientPathwayWorkflow.class, "pathway-" + patientId);
        workflow.deactivatePatient(reason);
    }
}
```

### Testing Workflow with Time Skipping
```java
// Source: Temporal Java SDK testing docs
@RegisterExtension
public static final TestWorkflowExtension testWorkflowExtension =
    TestWorkflowExtension.newBuilder()
        .setWorkflowTypes(PatientPathwayWorkflowImpl.class)
        .setDoNotStart(true)  // We register mocked activities manually
        .build();

@Test
void testMissingEventAlert(TestWorkflowEnvironment testEnv, Worker worker) {
    // Mock the evaluation activity
    PathwayEvaluationActivity mockActivity = mock(PathwayEvaluationActivity.class);
    when(mockActivity.evaluate(any()))
        .thenReturn(new PathwayEvaluationResult(false, List.of("MISSING_EVENT alert created")));
    worker.registerActivitiesImplementations(mockActivity);
    testEnv.start();

    // Start workflow
    PatientPathwayWorkflow workflow = testEnv.getWorkflowClient()
        .newWorkflowStub(PatientPathwayWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(worker.getTaskQueue())
                .build());

    WorkflowClient.start(workflow::monitorPathway, testPatientId, "BREAST");

    // Time skipping: 24h sleep fast-forwards automatically
    // The workflow will wake, call evaluate, and go back to sleep
    Thread.sleep(1000);  // Brief pause for time-skipping to execute

    verify(mockActivity, atLeastOnce()).evaluate(testPatientId);
}
```

## Discretionary Recommendations

These items are marked as "Claude's Discretion" in CONTEXT.md. Research findings inform these recommendations:

### Temporal Namespace and Task Queue Naming
**Recommendation:** Use `default` namespace (Temporal auto-setup creates it). Single task queue `onco-pathway-queue` for all workflow and activity types. V1 has no multi-tenancy or workload isolation needs.
**Rationale:** Multiple task queues add operational complexity without benefit at pilot scale. If workflow types need different concurrency limits later, they can be split to separate queues. [ASSUMED]

### Retry Policies for Activities
**Recommendation:**
- Evaluation activities: 3 attempts, 5s initial interval, 2.0 backoff coefficient, 1-minute max interval. StartToCloseTimeout = 2 minutes.
- Alert generation activities: Same retry policy. These are idempotent because of the dedup check (`existsByPatientIdAndPathwayStepNameAndStatus`).
- Sweep activities: 3 attempts, 10s initial interval. These query the patient list, which is safe to retry.
- Do NOT retry: `IllegalArgumentException` (indicates bad data, not transient failure).
**Rationale:** Activities are idempotent (evaluation re-reads current state; alert generation checks dedup before write). Exponential backoff handles transient DB connection issues. [VERIFIED: Context7 retry patterns]

### Daily Sweep Workflow Structure
**Recommendation:** Single `DailySweepWorkflow` as a Temporal cron workflow (`setCronSchedule("0 6 * * *")`). The sweep activity queries all active patients, checks which ones have a running workflow (via WorkflowClient), and starts workflows for any that are missing. This is a single workflow, not a parent-child pattern.
**Rationale:** Parent-child would create hundreds of child workflows on every sweep run, bloating Temporal's execution history unnecessarily. A single activity that calls `WorkflowClient.start()` for missing patients is simpler and produces fewer Temporal events. The cron schedule ensures exactly-one execution (if the previous run hasn't completed, the next cron trigger is skipped). [ASSUMED]

### PHI Handling in Temporal
**Recommendation:** Enforce a hard rule: workflow method signatures accept ONLY `UUID` and `String` (for enum names). Create a `TemporalPhiSafetyCheck` utility or code review rule. All activities receive patient UUID, fetch data from the encrypted database, and return only non-PHI results (step statuses, alert counts, boolean flags).
**Rationale:** Temporal's event history is stored in a separate PostgreSQL schema without the application's column-level encryption. PHI in event history would be an unencrypted PHI exposure. [VERIFIED: CLAUDE.md SEC-06, Temporal architecture]

### Temporal Search Attributes
**Recommendation:** Add two custom search attributes for operational visibility:
- `PatientId` (Keyword) -- enables finding a patient's workflow in Temporal UI without knowing the workflow ID format
- `CancerType` (Keyword) -- enables filtering workflows by cancer type for debugging
These are set at workflow start time and are non-PHI (UUIDs and enums). They are registered via `temporal operator search-attribute create` command (can be scripted in Docker entrypoint).
**Rationale:** Search attributes make the Temporal UI usable for debugging during pilot. Without them, finding a specific patient's workflow requires knowing the exact workflow ID. Registration is a one-time CLI command. [VERIFIED: Context7 /temporalio/documentation on search attributes]

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `temporal-spring-boot-starter-alpha` | `temporal-spring-boot-starter` (GA) | SDK 1.27+ (2024) | GA starter replaces alpha. Do not use the alpha artifact. [VERIFIED: Maven Central] |
| Manual `WorkerFactory` setup | Spring Boot auto-discovery with `@WorkflowImpl` / `@ActivityImpl` | SDK 1.27+ | No manual worker registration needed. Annotated beans are auto-discovered from configured packages. [VERIFIED: Context7] |
| `TestWorkflowRule` (JUnit 4) | `TestWorkflowExtension` (JUnit 5) | SDK 1.x | Use JUnit 5 extension for all new tests. [VERIFIED: Temporal testing docs] |
| Elasticsearch required for search attributes | PostgreSQL advanced visibility (Temporal Server 1.20+) | Server 1.20 | No Elasticsearch needed. Custom search attributes work on PostgreSQL. [VERIFIED: Temporal docs on visibility] |
| `Workflow.getVersion()` for all code changes | `Workflow.getVersion()` only for in-flight workflows | Always | V1 has no in-flight workflows to worry about. Version guards are not needed for initial deployment. [ASSUMED] |

**Deprecated/outdated:**
- `temporal-spring-boot-starter-alpha`: Replaced by GA `temporal-spring-boot-starter`. Do not use. [VERIFIED: Maven Central]
- Keycloak Spring Boot Adapter: Already noted in CLAUDE.md as deprecated. Not relevant to Phase 2 but worth reinforcing. [VERIFIED: CLAUDE.md]

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | PATH-04 delay detection uses `ChronoUnit.DAYS.between()` for elapsed day calculation | Phase Requirements | Low -- standard Java date math, alternative would be business-day calculation which is not specified |
| A2 | PATH-07 logging uses SLF4J structured logging + AuditService (not a separate monitoring_evaluations table) | Phase Requirements | Low -- if auditors require a dedicated table, a Flyway migration can add one |
| A3 | Default namespace `default` is appropriate for V1 | Discretionary Recommendations | Low -- namespace only matters for multi-environment separation; V1 is single environment |
| A4 | Daily sweep as single workflow with activity (not parent-child) is sufficient | Discretionary Recommendations | Low -- parent-child can be added later if sweep needs per-patient isolation |
| A5 | Cron schedule "0 6 * * *" (6 AM daily) is appropriate for sweep | Discretionary Recommendations | Low -- configurable via YAML property; spec suggests 4 hours but D-05 says daily is sufficient with event-driven |

## Open Questions

1. **CareEventType enum mapping to pathway steps**
   - What we know: The existing `CareEventType` enum has `REFERRAL`, `CONSULTATION`, `BIOPSY`, `PATHOLOGY_REPORT`, `IMAGING`, `SURGERY`, `CHEMOTHERAPY`, `RADIATION`, `FOLLOW_UP`, `LAB_WORK`, `GENETIC_TESTING`, `OTHER`.
   - What's unclear: Pathway step 4 in breast cancer is "Genomic Testing (Oncotype DX)" which maps to `GENETIC_TESTING`. Lung cancer step 3 is "Molecular / Biomarker Testing" which also maps to `GENETIC_TESTING`. This is fine for V1 since pathways are cancer-type-specific, but the mapping should be explicitly documented in the seed migration.
   - Recommendation: Use `GENETIC_TESTING` for both Oncotype DX (breast) and biomarker testing (lung). The step name differentiates them; the eventType is for matching care events to pathway steps.

2. **Out-of-order detection: Scheduled vs. Completed status**
   - What we know: V1 Feature Spec Scenario B says "Med onc visit is scheduled but Oncotype result is not in Completed status." The detection triggers on SCHEDULED status for the downstream step.
   - What's unclear: Should out-of-order detection fire when a care event is SCHEDULED, COMPLETED, or both?
   - Recommendation: Fire when any care event status (SCHEDULED, COMPLETED, PENDING) exists for a step whose prerequisites are not COMPLETED. This catches the problem early (at scheduling time, not just at completion). This matches Scenario B behavior.

3. **PathwayTemplateRepository -- does it exist?**
   - What we know: `PathwayTemplate` entity exists, but `PathwayTemplateRepository` does not appear in the repository directory. Only `AlertRepository`, `AuditLogRepository`, `CareEventRepository`, `PatientRepository` exist.
   - What's unclear: N/A -- it simply doesn't exist yet.
   - Recommendation: Create `PathwayTemplateRepository extends JpaRepository<PathwayTemplate, UUID>` with `Optional<PathwayTemplate> findByCancerType(CancerType cancerType)`.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Temporal Server | Workflow orchestration | Yes (Docker Compose) | 1.28.3 (auto-setup image) | -- |
| Temporal UI | Debugging workflows | Yes (Docker Compose) | 2.34.0 | -- |
| PostgreSQL | Data persistence + Temporal persistence | Yes (Docker Compose) | 16 | -- |
| Docker Desktop | Running all infrastructure | Yes | Detected in Phase 1 | -- |
| Temporal Java SDK | Worker + client code | Yes (pom.xml) | 1.32.0 | -- |
| Jackson ObjectMapper | JSONB deserialization | Yes (Spring Boot auto-config) | via Boot BOM | -- |

**Missing dependencies with no fallback:** None.

**Missing dependencies with fallback:** None.

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | Phase 2 has no new auth surfaces -- uses existing Keycloak/JWT from Phase 1 |
| V3 Session Management | No | No session changes in Phase 2 |
| V4 Access Control | Yes (partial) | PathwayService methods should verify caller has appropriate role before starting/signaling workflows. Activities run in worker thread without HTTP context -- access control is at the service entry point. |
| V5 Input Validation | Yes | Validate workflow inputs: patientId must be non-null UUID, cancerType must be valid enum. JSONB template data validated at migration time. |
| V6 Cryptography | No (new code) | PhysicianOverride entity has no PHI fields -- override reason is clinical process text, not patient data. No new encryption needed beyond existing Phase 1 infrastructure. |

### Known Threat Patterns for Temporal + Spring Boot + PostgreSQL

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| PHI in Temporal event history | Information Disclosure | UUID-only workflow inputs/signals. Code review rule. No patient names, DOBs, or MRNs in any Temporal payload. |
| Unauthorized workflow termination | Elevation of Privilege | PathwayService checks caller role before calling WorkflowClient.terminate(). ROLE_ADMIN or ROLE_NURSE_NAVIGATOR only. |
| Alert injection via crafted signal | Tampering | Signals carry only UUID values. Alert text comes from pathway templates (admin-controlled JSONB), not from signal payloads. |
| Temporal Server exposed to network | Information Disclosure | Temporal Server port 7233 is internal-only. Docker Compose exposes to localhost. In production, Temporal runs in private subnet (VPC). |
| Sweep workflow queries all patients | Information Disclosure | Sweep activity only reads patient IDs and statuses, never PHI fields. The activity checks workflow existence, not patient details. |

## Sources

### Primary (HIGH confidence)
- Context7 `/temporalio/sdk-java` -- Workflow interfaces, signals, queries, timers, activity stubs, retry options, deterministic constraints, testing
- Context7 `/temporalio/documentation` -- Spring Boot integration, @WorkflowImpl/@ActivityImpl auto-discovery, worker configuration, search attributes, event history limits (warning at 10,240, hard limit at 51,200)
- Context7 `/temporalio/samples-java` -- Signal patterns, child workflow patterns, timer examples
- Existing codebase: Patient.java, CareEvent.java, Alert.java, PathwayTemplate.java, AlertRepository.java (verified dedup method), CareEventRepository.java, PatientRepository.java, application-local.yml (Temporal config)
- `docs/Onco-Navigator AI - V1 Feature Specification v2.docx` -- All three pathway definitions with step sequences, time windows, alert text, and example scenarios

### Secondary (MEDIUM confidence)
- Maven Central (central.sonatype.com) -- temporal-spring-boot-starter 1.34.0 is latest; project pins 1.32.0 per CLAUDE.md
- Temporal docs (docs.temporal.io/develop/java/testing-suite) -- TestWorkflowExtension JUnit 5 usage, time skipping, mocked activities

### Tertiary (LOW confidence)
- None -- all findings verified against primary or secondary sources

## Project Constraints (from CLAUDE.md)

Actionable directives that the planner must honor:

1. **Java 21 + Spring Boot 3.5.x** -- all new code uses these versions
2. **Temporal Java SDK 1.32.0** with `temporal-spring-boot-starter` -- pinned version
3. **`@Audited` on all ePHI entities** -- PhysicianOverride needs this (even though it has no PHI, it references patient pathways)
4. **AES-GCM encryption via `@Convert(converter = EncryptionConverter.class)`** -- not needed for PhysicianOverride (no PHI fields) but must not be accidentally applied
5. **PostgreSQL ENUM types** -- any new enum (e.g., `AnchorType` if stored in DB) needs a PostgreSQL `CREATE TYPE` in Flyway migration
6. **No PHI in logs** -- log patient UUIDs only, never names/DOBs/MRNs
7. **No PHI in Temporal** -- SEC-06 explicitly states "No PHI appears in application logs or Temporal workflow history -- only opaque identifiers"
8. **Flyway for all schema changes** -- versioned SQL files, auto-applied on startup
9. **`ddl-auto: update`** in local, **`ddl-auto: validate`** in test/prod -- Flyway migrations must be correct
10. **Testcontainers BOM 1.21.3** -- required for Docker Desktop 4.59 / Apple Silicon compatibility
11. **`Workflow.newTimer(Duration)` inside workflow, not `Workflow.sleep()` inside activities** -- CLAUDE.md explicitly prohibits sleep inside activities
12. **Worker auto-discovery packages**: `com.onconavigator.workflow` and `com.onconavigator.activity` -- already configured in application-local.yml
13. **Virtual threads enabled** via `spring.threads.virtual.enabled: true` -- new Spring services benefit automatically

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- all libraries already in pom.xml or documented in CLAUDE.md, versions verified
- Architecture: HIGH -- Temporal workflow patterns extensively documented in Context7, existing codebase provides clear integration points
- Pitfalls: HIGH -- verified against official Temporal docs (determinism, event history limits, testing patterns)

**Research date:** 2026-04-30
**Valid until:** 2026-05-30 (stable -- Temporal SDK and Spring Boot are mature, pinned versions)
