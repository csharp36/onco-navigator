# Phase 2: Pathway Engine - Context

**Gathered:** 2026-04-30
**Status:** Ready for planning

<domain>
## Phase Boundary

Build the Temporal.io durable workflow engine that monitors patient care pathways and detects deviations. This phase delivers: pathway template data model and seed data for three cancer types, a long-running Temporal workflow per patient that evaluates pathway compliance, deviation detection for missing/delayed/out-of-order events, alert generation with deduplication, and the physician override mechanism to suppress false-positive alerts.

This phase does NOT build the REST API or dashboard UI (Phase 3), does not integrate Claude AI (Phase 4), and does not handle SMS delivery (deferred to V2).

</domain>

<decisions>
## Implementation Decisions

### Pathway Template Structure
- **D-01:** Step ordering is **linear only** — steps are a simple ordered list where each step depends on the previous. No branching/DAG needed for V1. All three clinical pathways in the spec are linear (6 steps each).
- **D-02:** Time window anchoring is **configurable per step** — each step specifies its anchor: `previous_step` (default), `diagnosis_date` (for step 1), or a specific `stepId` (for cases like breast cancer step 5 which checks Oncotype DX status, not timing from previous step).
- **D-03:** Steps have a **`required` boolean flag** — required steps generate OPEN alerts, optional steps generate a softer warning. This satisfies PATH requirement P-05.
- **D-04:** JSONB `templateData` structure per step: `stepId`, `stepNumber`, `name`, `description`, `eventType` (maps to CareEventType enum), `windowDays` (int), `anchorType` (enum: PREVIOUS_STEP | DIAGNOSIS_DATE | SPECIFIC_STEP), `anchorStepId` (nullable, used when anchorType=SPECIFIC_STEP), `required` (boolean), `alertText`, `suggestedAction`, `prerequisites` (list of stepIds).

### Monitoring Cadence & Triggers
- **D-05:** Detection uses a **dual approach**: event-driven re-evaluation (via Temporal signal when a care event is added/updated) PLUS a daily timer sweep across all active patients to catch time-window expirations (missing/delayed steps). Fast feedback on data entry + safety net for calendar-based deadlines.
- **D-06:** Pathway enrollment is **automatic on patient creation** — when a care coordinator creates a patient with a cancer type, the system starts a Temporal workflow for that patient. No separate enrollment step.

### Workflow Lifecycle
- **D-07:** Workflow scope is **one Temporal workflow per patient**. The workflow holds the patient's pathway state, receives signals when care events change, and runs timers for each step.
- **D-08:** Patient deactivation **cancels the workflow** — sends a signal to the Temporal workflow which gracefully terminates (stops timers, closes open alerts, records final state).
- **D-09:** When all pathway steps are completed, the **workflow completes naturally**. Patient status can reflect "Pathway Complete". Clean lifecycle with no zombie workflows.

### Clinical Pathway Content
- **D-10:** Use the three pathway definitions from the V1 Feature Specification **as-is** — breast cancer (6 steps), lung cancer (6 steps), colorectal cancer (6 steps). Load via a Flyway seed migration into PathwayTemplate JSONB records. The oncologist co-author wrote these with clinically accurate trigger conditions, time windows, and alert text.
- **D-11:** Physician override uses a **simple override flag per step** — a physician (or nurse acting on physician instruction) can mark a specific pathway step as "intentionally reordered" or "skipped with reason" for a given patient. The workflow checks for this override before generating an alert. Stored as a record linking patient + pathway step + override reason.

### Claude's Discretion
- Temporal namespace and task queue naming conventions
- Retry policies for activities (idempotent activities, backoff strategy)
- How to structure the daily sweep workflow (single parent workflow vs. individual child workflows)
- PHI handling in Temporal — ensuring no PHI in workflow inputs/payloads (use UUIDs only, activities fetch from DB)
- Whether to use Temporal search attributes for patient/alert querying

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Clinical Pathway Definitions
- `docs/Onco-Navigator AI - V1 Feature Specification v2.docx` — Contains all three pathway definitions (breast, lung, colorectal) with exact step sequences, time windows, trigger conditions, alert text, and suggested actions. Also contains 3 example alert scenarios usable as test cases. **Convert to text with `textutil -convert txt` before reading.**
- `docs/Onco-Navigator AI - Concept Brief v3.docx` — Original concept brief from the oncologist co-author. Background on the clinical problem.

### Requirements
- `.planning/REQUIREMENTS.md` — PATH-01 through PATH-08 (pathway engine), INFR-03 and INFR-04 (durable workflows)

### Existing Code
- `src/main/java/com/onconavigator/domain/PathwayTemplate.java` — JSONB entity for pathway templates. `templateData` structure must match D-04.
- `src/main/java/com/onconavigator/domain/Alert.java` — Alert entity with `workflowRunId` field for Temporal linkage
- `src/main/java/com/onconavigator/repository/AlertRepository.java` — `existsByPatientIdAndPathwayStepNameAndStatus()` for dedup (PATH-06)
- `src/main/resources/application-local.yml` — Temporal connection config and worker auto-discovery packages

### Technology
- Temporal Java SDK 1.32.0 with `temporal-spring-boot-starter` — auto-discovers `@WorkflowImpl`/`@ActivityImpl` beans in `com.onconavigator.workflow` and `com.onconavigator.activity` packages

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `PathwayTemplate` entity with JSONB `templateData` — ready for structured pathway step data
- `Alert` entity with `workflowRunId` — links alerts back to Temporal workflow runs
- `AlertRepository.existsByPatientIdAndPathwayStepNameAndStatus()` — deduplication check for PATH-06
- `CareEventRepository.findByPatient_IdOrderByEventDateDesc()` — fetch all events for pathway evaluation
- `PatientRepository.findByStatus(PatientStatus.ACTIVE)` — get all active patients for daily sweep
- `AuditService.logAccess()` — async audit logging, usable from activities

### Established Patterns
- Hibernate Envers `@Audited` on all ePHI entities — new entities should follow this
- AES-GCM encryption via `@Convert(converter = EncryptionConverter.class)` on PHI fields
- `@Column(updatable = false)` on audit log entries — immutable by design
- Spring async with `REQUIRES_NEW` propagation for audit writes
- PostgreSQL ENUM types mapped to Java enums via `@Enumerated(EnumType.STRING)` with `columnDefinition`

### Integration Points
- Temporal worker auto-discovery: `com.onconavigator.workflow` and `com.onconavigator.activity` packages (configured, not yet created)
- Patient creation → should trigger workflow start (D-06)
- Care event add/update → should signal the patient's workflow (D-05)
- Patient deactivation → should signal workflow cancellation (D-08)
- Workflow alert generation → writes to `Alert` entity via repository

</code_context>

<specifics>
## Specific Ideas

- The three pathway definitions in the feature spec are written by the oncologist co-author and should be treated as clinically authoritative for V1
- Example scenarios A, B, and C from the spec should be used as integration test cases
- Scenario B (out-of-order: Med Onc Visit scheduled before Oncotype DX complete) is the most complex detection case — good litmus test for the workflow logic
- The daily sweep should be configurable (the spec suggests every 4 hours, but daily is sufficient when combined with event-driven detection)

</specifics>

<deferred>
## Deferred Ideas

- SMS alert delivery (F2-01 through F2-06) — deferred to V2, not in current roadmap
- Manual re-scan trigger (F3-07) — Phase 3 dashboard feature, not workflow engine scope
- REST API endpoints for patient/event CRUD — Phase 3
- Multi-pathway per patient support — V2 if needed

</deferred>

---

*Phase: 02-pathway-engine*
*Context gathered: 2026-04-30*
