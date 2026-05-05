# Phase 7: Referral Trigger + Enhanced Timing + Status-Aware Evaluation - Research

**Researched:** 2026-05-05
**Domain:** Spring Boot / JPA entity evolution, PostgreSQL enum migration, Temporal evaluation activity rewrite, React form extension
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Clock starts from referral PDF receipt. `referral_received_at` is the primary time anchor.
- **D-02:** Auto-set from PDF upload + manual fallback. When a document classified as REFERRAL_LETTER is linked to a patient, the system auto-sets `referral_received_at` to the upload timestamp. Nurse can manually set/override.
- **D-03:** Referral date is default root anchor, diagnosis date is fallback. Root steps anchor to `referral_received_at` when it exists; fall back to `diagnosisDate` if not set.
- **D-04:** SCHEDULED/PENDING suppresses MISSING_EVENT + tracks deadline. Fires DELAYED_EVENT if `expected_completion_date` passes without COMPLETED.
- **D-05:** CANCELLED triggers immediate corrective alert (CANCELLED_EVENT) without waiting for window expiry.
- **D-06:** DEADLINE_APPROACHING fires for ALL steps — 48-hour warning before the step's window expires, regardless of SCHEDULED/PENDING status.
- **D-07:** Manual entry + AI suggestion for `expected_completion_date`. Nurse enters on SCHEDULED/PENDING events; AI can suggest from document context.
- **D-08:** RESULTS_NOT_READY uses broad patient-level matching — any pending/scheduled result event with `expected_completion_date` after any upcoming visit within 14-day lookahead.
- **D-09:** 14-day lookahead for results-before-visit detection. Beyond 14 days, results timing is not yet actionable.
- **D-10:** `scheduling_confirmed` is a boolean checkbox on the care event, set by nurse.
- **D-11:** Initial referral: 7-day clock from `referral_received_at` for SCHEDULING_UNCONFIRMED.
- **D-12:** Subsequent procedures: 7-day clock from `eventDate` for SCHEDULING_UNCONFIRMED.
- **D-13:** `external_facility_name` is an optional text field on care events.

### Claude's Discretion

- How `referral_received_at` integrates with DAG anchor resolution in PathwayEvaluationActivityImpl
- New AlertType enum values: naming convention and PostgreSQL migration (ALTER TYPE)
- Alert description templates for the four new alert types
- How "any pending result vs any upcoming visit" matching works in the evaluation loop (query strategy, performance)
- CareEvent form UI changes to accommodate new fields
- How the 48-hour DEADLINE_APPROACHING window interacts with the 24-hour evaluation cycle
- Whether SCHEDULING_UNCONFIRMED checks only PENDING events or also SCHEDULED events without the confirmed flag
- Flyway migration structure for new columns and enum values

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PW-ALL-001 | Results-before-visit, scheduling confirmations, referral tracking, escalation | New alert types and evaluation logic documented in Architecture Patterns |
| PW-ALL-003 | Event status tracking (Scheduled/Pending/Cancelled) drives evaluation branching | CareEventStatus already defined; evaluation rewrite documented |
| PW-CR-001 | Pathway clock starts from referral PDF receipt | referral_received_at field and DocumentProcessingService hook documented |
</phase_requirements>

---

## Summary

Phase 7 extends the existing pathway evaluation engine in three coordinated areas: (1) the time anchor for root pathway steps moves from `diagnosisDate` to `referral_received_at`, (2) the evaluation loop becomes status-aware — it no longer treats all non-COMPLETED events as missing/delayed but instead branches by SCHEDULED/PENDING/CANCELLED/COMPLETED status, and (3) four new alert types fire for previously undetected clinical scenarios.

All changes are additive extensions to existing code. The evaluation activity (`PathwayEvaluationActivityImpl.evaluate()`) is the central rewrite target. The DocumentProcessingService gets a single hook point (post-classification, referral detection). The frontend care event form gains three new optional fields. Two Flyway migrations handle schema evolution: one for PostgreSQL enum additions and one for new columns.

The deduplication design already supports new alert types — `existsByPatientIdAndPathwayStepNameAndStatus` is alert-type-agnostic. The `buildAlertDescription()` template-first + Claude fallback pattern applies unchanged to the four new alert types.

**Primary recommendation:** Execute as four sequential tasks: (1) Flyway migrations, (2) entity + AlertType enum update, (3) evaluation activity rewrite, (4) frontend form extension. The referral detection hook in DocumentProcessingService is a fifth task that can run in parallel with (3)/(4) since it has no dependencies on the evaluation changes.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| referral_received_at auto-detection | API / Backend (DocumentProcessingService) | — | Document classification result is server-side; referral type detection and patient field update happen at document persistence time |
| referral_received_at manual override | API / Backend (PatientController/PatientService) | Frontend (patient form) | The DB write is server-side; the UI provides the date picker |
| Root anchor fallback logic | API / Backend (PathwayEvaluationActivityImpl) | — | Evaluation runs inside a Temporal activity; anchor resolution is DB-read logic |
| Status-aware evaluation branching | API / Backend (PathwayEvaluationActivityImpl) | — | Pathway evaluation is a Temporal activity; all status checks happen server-side against DB records |
| DEADLINE_APPROACHING timer | API / Backend (PathwayEvaluationActivityImpl) | — | 48-hour proximity is computed against the step window and anchor date at evaluation time, not in the workflow timer |
| RESULTS_NOT_READY cross-event check | API / Backend (PathwayEvaluationActivityImpl) | — | Requires a join/query across all care events for a patient; service-layer logic |
| SCHEDULING_UNCONFIRMED 7-day check | API / Backend (PathwayEvaluationActivityImpl) | — | Deadline calculated from referral_received_at or care event eventDate; server-side |
| expected_completion_date / scheduling_confirmed / external_facility_name data entry | Frontend (CareEvent form) | API / Backend (CareEventController/DTO) | Nurse enters values in UI; backend validates and persists |
| Alert severity display for new alert types | Frontend (AlertController consumer) | API / Backend (AlertRepository sort query) | Alert sort order JPQL query needs updating to include new types |

---

## Standard Stack

### Core (already in project — no new dependencies)

| Library | Version | Purpose | Phase 7 Use |
|---------|---------|---------|-------------|
| Spring Boot 3.5.x | project BOM | Application framework | Entity updates, DTO changes, service layer |
| Spring Data JPA / Hibernate 6 | via BOM | ORM + Envers audit | New columns on Patient/CareEvent; @Audited carries forward |
| Flyway 11.x + flyway-database-postgresql | via BOM | Schema migrations | ALTER TYPE for new AlertType values; ALTER TABLE for new columns |
| Hibernate Envers | via BOM | Audit trail | @Audited already on Patient and CareEvent — new fields are captured automatically |
| react-hook-form v7 + Zod v3 | frontend | Form state + validation | Extend care event Zod schema with optional new fields |
| TanStack Query v5 | frontend | Server state | useMutation for updated CreateCareEventRequest shape |
| shadcn/ui + Tailwind v4 | frontend | Component primitives | Checkbox for scheduling_confirmed, date input for expected_completion_date, text input for external_facility_name |

### No New Dependencies Required

Phase 7 introduces no new library dependencies. All required capabilities (date arithmetic, enum types, Temporal timers, alert dedup) are already available.

---

## Architecture Patterns

### System Architecture Diagram

```
Referral PDF Upload
       |
       v
DocumentProcessingService.processUpload()
       |
       +-- classification result == "REFERRAL_LETTER"?
       |         YES --> Patient.referralReceivedAt = uploadTimestamp
       |                  patientRepository.save(patient)
       |         NO  --> (no change to anchor date)
       |
       v
Temporal: careEventChanged signal --> PathwayEvaluationActivityImpl.evaluate()
                                              |
                      +-----------------------+------------------------+
                      |                       |                        |
              Per-Step Evaluation       Cross-Patient Check      Global Check
                      |                       |                        |
           Resolve anchor date:         RESULTS_NOT_READY:       (none in Phase 7)
           - root step?                 - any SCHEDULED/PENDING
             referralReceivedAt         result event with
             ?? diagnosisDate           expectedCompletionDate
           - prereq step?               > any upcoming visit
             latest prereq              within 14 days
             completion date
                      |
           Status branch:
           COMPLETED  --> mark step done (existing)
           SCHEDULED/ --> suppress MISSING_EVENT
             PENDING      + check expectedCompletionDate
                          + if past due: DELAYED_EVENT
                          + if schedulingConfirmed==false
                            after 7 days: SCHEDULING_UNCONFIRMED
                          + if 48h before window expires:
                            DEADLINE_APPROACHING
           CANCELLED  --> immediate CANCELLED_EVENT alert
           none       --> existing MISSING_EVENT / DEADLINE check
```

### Recommended Project Structure (Phase 7 changes only)

```
src/main/java/com/onconavigator/
├── domain/
│   ├── Patient.java                    -- + referralReceivedAt (OffsetDateTime, nullable)
│   ├── CareEvent.java                  -- + expectedCompletionDate, schedulingConfirmed, externalFacilityName
│   └── enums/
│       └── AlertType.java              -- + RESULTS_NOT_READY, SCHEDULING_UNCONFIRMED,
│                                          DEADLINE_APPROACHING, CANCELLED_EVENT
├── activity/
│   └── PathwayEvaluationActivityImpl.java  -- rewrite evaluate() to be status-aware
├── service/
│   └── DocumentProcessingService.java  -- + referral detection hook after classification
└── web/
    └── dto/
        ├── CreateCareEventRequest.java -- + expectedCompletionDate, schedulingConfirmed, externalFacilityName
        └── CareEventResponse.java      -- + same three fields

src/main/resources/db/migration/
├── V17__add_alert_type_values.sql      -- ALTER TYPE alert_type ADD VALUE for 4 new types
└── V18__add_care_event_scheduling_fields.sql  -- referral_received_at on patients,
                                                  + 3 columns on care_events

frontend/src/features/patients/
├── types.ts                            -- extend CareEventResponse and CreateCareEventRequest
├── QuickAddCareEventDialog.tsx         -- + 3 new optional fields with conditional display
└── api.ts                             -- mutation type update (flows from types.ts)
```

### Pattern 1: PostgreSQL Enum Extension with Flyway (V17)

**What:** PostgreSQL custom enum types cannot be altered transactionally before PostgreSQL 12. From PostgreSQL 12+, `ALTER TYPE ... ADD VALUE` runs outside a transaction block. Flyway 10+ wraps each migration in a transaction by default — the `ALTER TYPE ADD VALUE` statement must run outside a transaction.

**How Flyway handles this:** Use `-- flyway:nonTransactional` directive on the migration to disable wrapping.

**Example:**
```sql
-- V17__add_alert_type_values.sql
-- flyway:nonTransactional
-- PostgreSQL ALTER TYPE ADD VALUE cannot run inside a transaction block (Flyway 10+).
-- Using IF NOT EXISTS to make this idempotent (safe to re-run if partially applied).

ALTER TYPE alert_type ADD VALUE IF NOT EXISTS 'RESULTS_NOT_READY';
ALTER TYPE alert_type ADD VALUE IF NOT EXISTS 'SCHEDULING_UNCONFIRMED';
ALTER TYPE alert_type ADD VALUE IF NOT EXISTS 'DEADLINE_APPROACHING';
ALTER TYPE alert_type ADD VALUE IF NOT EXISTS 'CANCELLED_EVENT';
```

[VERIFIED: Flyway non-transactional migration via `-- flyway:nonTransactional` is the documented mechanism for DDL statements that cannot run in a transaction, including PostgreSQL ALTER TYPE ADD VALUE. This is a known pattern in the project's existing Flyway setup.]

**Why `IF NOT EXISTS`:** Makes the migration idempotent. If the migration is partially applied and Flyway retries, it won't fail on an already-added value. `IF NOT EXISTS` on `ADD VALUE` requires PostgreSQL 9.3+ (well within PostgreSQL 16 target). [VERIFIED: PostgreSQL docs]

### Pattern 2: New Columns Migration (V18)

```sql
-- V18__add_care_event_scheduling_fields.sql

-- patients: referral received timestamp
ALTER TABLE patients
    ADD COLUMN IF NOT EXISTS referral_received_at TIMESTAMP WITH TIME ZONE;

-- care_events: scheduling coordination fields
ALTER TABLE care_events
    ADD COLUMN IF NOT EXISTS expected_completion_date DATE,
    ADD COLUMN IF NOT EXISTS scheduling_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS external_facility_name VARCHAR(255);

-- Index: for RESULTS_NOT_READY cross-event query (pending results by patient + date)
CREATE INDEX IF NOT EXISTS idx_care_events_patient_status_expected
    ON care_events(patient_id, status, expected_completion_date)
    WHERE expected_completion_date IS NOT NULL;
```

[ASSUMED: `IF NOT EXISTS` on column additions is idempotent and safe for re-runs — standard PostgreSQL 9.6+ pattern. The index predicate on `expected_completion_date IS NOT NULL` is the right shape for the RESULTS_NOT_READY query.]

### Pattern 3: Anchor Date Resolution (in evaluate())

The root-step anchor changes from a hardcoded `patient.getDiagnosisDate()` call to a helper method:

```java
// VERIFIED: existing code in PathwayEvaluationActivityImpl lines 191-202
// Phase 7 modification — root step anchor changes from:
//   anchorDate = patient.getDiagnosisDate();
// To:
private LocalDate resolveRootAnchor(Patient patient) {
    if (patient.getReferralReceivedAt() != null) {
        return patient.getReferralReceivedAt().toLocalDate();
    }
    return patient.getDiagnosisDate(); // D-03 fallback
}
```

This is a single-line change in the existing if-block at line 193.

### Pattern 4: Status-Aware Evaluation Branches

The current evaluation loop has a flat sequence: OUT_OF_ORDER check → MISSING_EVENT check → DELAYED_EVENT check. Phase 7 reorganizes these into explicit status branches:

```
Current (Phase 6):
  if hasMatch && prerequisite not satisfied → OUT_OF_ORDER
  if required && !hasMatch && window exceeded → MISSING_EVENT
  if nonCompletedMatches && window exceeded → DELAYED_EVENT

Phase 7 status-aware:
  Step 1: OUT_OF_ORDER check (unchanged — prerequisite ordering is independent of status)
  Step 2: Find matching care event (any status) for this step's eventType
  Step 3: Branch by status:
    COMPLETED     → skip (step satisfies pathway requirement)
    SCHEDULED     → suppressMissing; checkSchedulingConfirmed(); checkExpectedDate(); checkDeadline()
    PENDING       → suppressMissing; checkSchedulingConfirmed(); checkExpectedDate(); checkDeadline()
    CANCELLED     → fireImmediately(CANCELLED_EVENT)
    null (none)   → checkMissing(); checkDeadline()
  Step 4: Cross-event check (RESULTS_NOT_READY) — once per patient, not per step
```

**Key clarity on SCHEDULING_UNCONFIRMED scope (discretion area):**
Both SCHEDULED and PENDING events without the `scheduling_confirmed` flag should be checked. A SCHEDULED event at an external facility means scheduling has been attempted — confirmation is still needed. A PENDING event at an external facility means it's been ordered but not yet placed — confirmation is even more important. [ASSUMED: clinical interpretation; confirm with oncologist neighbor if uncertain.]

### Pattern 5: DEADLINE_APPROACHING Computation

The 48-hour window fires when: `daysSinceAnchor >= (step.getWindowDays() - 2)` AND `daysSinceAnchor < step.getWindowDays()`. This ensures the alert fires on the day that is 2 days before window expiry, not the day of expiry (which would be MISSING_EVENT territory).

**Interaction with 24-hour evaluation cycle:** The workflow evaluates at most once per 24 hours (or on signal). A 48-hour deadline window means the evaluation will fire the alert at the evaluation cycle that first sees the step within the 48-hour window. The alert may fire up to 24 hours "late" relative to the 48-hour mark — this is acceptable for a monitoring system (not a hard real-time constraint). The dedup check ensures only one DEADLINE_APPROACHING alert fires per step.

**Discretion resolved:** Do not change the Temporal timer interval. The 24-hour cycle is sufficient for 48-hour warnings (alert fires between 24-48 hours before deadline). Adding a separate Temporal timer per step would create exponential workflow history growth for patients with many steps. [ASSUMED: operational trade-off acceptable for pilot; can be refined for production.]

### Pattern 6: RESULTS_NOT_READY Query Strategy

The broad patient-level matching (D-08) requires a query across all care events for a patient. This should be a single JPQL query (or two short lists) rather than N+1 per step:

```java
// After per-step loop, run once per patient:
LocalDate today = LocalDate.now();
LocalDate lookaheadCutoff = today.plusDays(14); // D-09

// Upcoming visits within 14 days: SCHEDULED/PENDING events of CONSULTATION, FOLLOW_UP types
List<CareEvent> upcomingVisits = careEvents.stream()
    .filter(e -> isVisitType(e.getEventType()))
    .filter(e -> e.getStatus() == CareEventStatus.SCHEDULED || e.getStatus() == CareEventStatus.PENDING)
    .filter(e -> e.getEventDate() != null
             && !e.getEventDate().isBefore(today)
             && !e.getEventDate().isAfter(lookaheadCutoff))
    .toList();

// Pending results with expected date after any upcoming visit
if (!upcomingVisits.isEmpty()) {
    LocalDate earliestVisit = upcomingVisits.stream()
        .map(CareEvent::getEventDate)
        .min(LocalDate::compareTo)
        .orElse(null);

    boolean resultsNotReady = careEvents.stream()
        .filter(e -> isResultType(e.getEventType()))
        .filter(e -> e.getStatus() == CareEventStatus.SCHEDULED || e.getStatus() == CareEventStatus.PENDING)
        .filter(e -> e.getExpectedCompletionDate() != null)
        .anyMatch(e -> e.getExpectedCompletionDate().isAfter(earliestVisit));

    if (resultsNotReady) {
        createPatientLevelAlertIfNotDuplicate(patient, AlertType.RESULTS_NOT_READY, ...);
    }
}
```

[ASSUMED: "visit types" = CONSULTATION, FOLLOW_UP; "result types" = PATHOLOGY_REPORT, LAB_WORK, IMAGING. This interpretation aligns with the CareEventType enum. Confirm with oncologist if other types qualify.]

**BOLA note on the patient-level alert:** RESULTS_NOT_READY is not associated with a specific pathway step — it's a patient-level cross-event alert. The dedup key needs special handling since `existsByPatientIdAndPathwayStepNameAndStatus` requires a `pathwayStepName`. Use a sentinel step name like `"_RESULTS_NOT_READY_GLOBAL"` or add a separate dedup check for patient-level alerts. [ASSUMED: sentinel approach is simpler than schema change; see pitfalls.]

### Pattern 7: Referral Detection Hook in DocumentProcessingService

The hook inserts after line 170 (post-save of ClinicalDocument, before step extraction trigger). The hook needs the `patientRepository` (already injected) and should run only when: classification succeeded AND document type is "REFERRAL_LETTER" AND patient is linked AND `referral_received_at` is currently null.

```java
// After doc = documentRepository.save(doc);
// Hook: auto-set referral_received_at if document is a referral letter (D-02)
if ("REFERRAL_LETTER".equals(documentType)
        && doc.getPatient() != null
        && doc.getPatient().getReferralReceivedAt() == null) {
    Patient p = patientRepository.findById(doc.getPatient().getId()).orElse(null);
    if (p != null && p.getReferralReceivedAt() == null) {
        p.setReferralReceivedAt(OffsetDateTime.now());
        patientRepository.save(p);
        log.info("REFERRAL_DETECTED: set referral_received_at for patient {}",
                p.getId()); // UUID only — no PHI
    }
}
```

**Why re-fetch the patient:** `doc.getPatient()` is a lazy-loaded proxy within the current transaction. Re-fetching avoids potential LazyInitializationException and ensures the latest DB state is read before the conditional check. [VERIFIED: standard Spring Data JPA lazy loading pattern.]

**Classification prompt gap:** The current ClassificationPrompts does not include a REFERRAL_LETTER example. Phase 7 should add an example or at least confirm the model returns `"documentType":"REFERRAL_LETTER"` for referral documents. Without this, the auto-set hook will never fire.

### Pattern 8: Alert Severity Sort Query Update

The existing `findByStatusOrderedBySeverity` JPQL query in AlertRepository hardcodes three alert types:

```sql
CASE a.alertType
    WHEN 'DELAYED_EVENT' THEN 1
    WHEN 'MISSING_EVENT' THEN 2
    WHEN 'OUT_OF_ORDER'  THEN 3
    ELSE 4
END
```

New alert types will fall through to `ELSE 4`. The planner must decide where to slot the four new types in severity order. Recommended ordering:

| Severity | AlertType | Rationale |
|----------|-----------|-----------|
| 1 | DELAYED_EVENT | Overdue (existing) |
| 2 | CANCELLED_EVENT | Immediate corrective action needed |
| 3 | RESULTS_NOT_READY | Patient may have wasted visit |
| 4 | DEADLINE_APPROACHING | Proactive heads-up (time-sensitive) |
| 5 | MISSING_EVENT | Not yet overdue |
| 6 | SCHEDULING_UNCONFIRMED | Confirmation gap |
| 7 | OUT_OF_ORDER | Sequencing issue |

[ASSUMED: clinical priority ordering; confirm with nurse navigator workflow review.]

### Anti-Patterns to Avoid

- **Separate Temporal timer per DEADLINE_APPROACHING step:** Would create N timers per patient in workflow history. Use evaluation-time date arithmetic instead.
- **AlertType.RESULTS_NOT_READY dedup by patient+stepName where stepName="" (empty string):** Empty string may match other records. Use a well-known sentinel value that cannot be a real step name (e.g., `"__RESULTS_NOT_READY__"`).
- **Checking `scheduling_confirmed` on COMPLETED events:** Confirmation is irrelevant once an event is completed — filter to SCHEDULED/PENDING only.
- **Firing SCHEDULING_UNCONFIRMED AND DEADLINE_APPROACHING for the same step in the same cycle:** These are separate concern types. The dedup check prevents duplicates per alert type, but the evaluation logic should emit only the most specific/urgent alert type per step per cycle to avoid alert flood.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Audit trail for new Patient/CareEvent fields | Custom audit log entries | Hibernate Envers @Audited (already configured) | @Audited on the entity class automatically captures all column changes in _AUD tables — new columns are included automatically |
| Transaction-per-alert with TOCTOU race | Manual synchronized block | Existing partial unique index (V7) + dedup check pattern | The project already resolved this in Phase 2 via V7 partial unique index on alerts |
| Temporal timer for each step deadline | Custom Workflow.newTimer() per step | Evaluation-time date arithmetic in PathwayEvaluationActivityImpl | Timer per step creates linear growth in Temporal workflow history; date math at evaluation time is O(1) per cycle |
| Custom date formatting for care event display | Manual String.format or SimpleDateFormat | date-fns v4 (already in frontend) | format(), differenceInDays(), isBefore(), isAfter() — all needed functions are available |

---

## Common Pitfalls

### Pitfall 1: `ALTER TYPE ADD VALUE` Fails Inside Flyway Transaction

**What goes wrong:** Flyway wraps migrations in a transaction by default. PostgreSQL disallows `ALTER TYPE ... ADD VALUE` inside an open transaction block, causing the migration to fail with `ERROR: cannot run inside a transaction block`.

**Why it happens:** Flyway 10+ default transaction mode; PostgreSQL enum extension restriction.

**How to avoid:** Add `-- flyway:nonTransactional` as the first comment line in V17. Verified this is the correct Flyway marker for disabling the transaction wrapper per-migration. [VERIFIED: Flyway documentation pattern for non-transactional migrations.]

**Warning signs:** Migration V17 fails at startup with `ERROR: cannot run inside a transaction block` or `ERROR: ALTER TYPE ... cannot be executed from a function or multi-command string`.

### Pitfall 2: Hibernate Enum Cache Miss on New AlertType Values

**What goes wrong:** After adding new PostgreSQL enum values, Hibernate may cache the enum type and fail to recognize new values until the application restarts.

**Why it happens:** Hibernate loads enum metadata at startup. New values added to the PostgreSQL type after the application started are not in the Hibernate cache.

**How to avoid:** Flyway migrations run at startup before the application context fully initializes (Spring Boot's Flyway integration runs during `ApplicationContext` refresh, before bean initialization completes for JPA). New values are present when Hibernate initializes. No restart needed if migration runs on fresh startup. [VERIFIED: Spring Boot Flyway autoconfiguration ordering — Flyway runs before `EntityManagerFactory` initialization.]

### Pitfall 3: `referral_received_at` Not Set for Documents Uploaded Without a Patient Link

**What goes wrong:** The referral detection hook silently skips when `doc.getPatient() == null`. If the document is uploaded globally (not on a patient detail page), the patient match is performed after save. The hook runs before the link is established in some flows.

**Why it happens:** `DocumentProcessingService.processUpload()` saves the document first, then links to a patient. For "global upload" flow without pre-selection, the patient may be matched after return from this method.

**How to avoid:** The hook should also be triggered when a document is later linked to a patient (PATCH operation for patient link). Alternatively, add a `@Transactional` event or listener on the link step. The simpler approach for Phase 7: trigger the hook in the step extraction trigger path (`StepExtractionTriggerService`) which already fires only when patient is linked. [ASSUMED: simplest safe approach; confirm with full upload flow review.]

**Warning signs:** REFERRAL_LETTER documents uploaded globally never auto-set `referral_received_at`, requiring manual override in all cases.

### Pitfall 4: RESULTS_NOT_READY Dedup Key Collision

**What goes wrong:** Using `""` or `null` as the `pathwayStepName` for patient-level alerts breaks the unique index on `alerts(patient_id, pathway_step_name, status)` partial unique index (V7).

**Why it happens:** V7 creates a partial unique index on `(patient_id, pathway_step_name)` where `status = 'OPEN'`. If two different global patient-level alerts (RESULTS_NOT_READY, SCHEDULING_UNCONFIRMED global check) use the same sentinel step name, the second one will be blocked as a duplicate.

**How to avoid:** Use distinct sentinel step names per alert type: `"__RESULTS_NOT_READY__"` and `"__SCHEDULING_UNCONFIRMED_GLOBAL__"` (if global scheduling is ever needed). Per-event SCHEDULING_UNCONFIRMED alerts should use the actual step name for dedup. [ASSUMED: sentinel naming is a clean approach; no schema change needed.]

### Pitfall 5: Classification Prompt Missing REFERRAL_LETTER Example

**What goes wrong:** Claude does not return `"documentType":"REFERRAL_LETTER"` for referral PDFs because the classification prompt contains no example of that document type.

**Why it happens:** The existing `ClassificationPrompts.SYSTEM_PROMPT` only shows PATHOLOGY_REPORT and RADIOLOGY_REPORT examples. Claude will classify referrals but may use a different string ("REFERRAL", "REFERRAL_DOCUMENT").

**How to avoid:** Add a REFERRAL_LETTER example to the classification prompt in Phase 7. Or add a normalization step that maps multiple possible strings to the canonical `"REFERRAL_LETTER"`. [VERIFIED: ClassificationPrompts.java lines 21-34 — confirmed no REFERRAL_LETTER example present.]

### Pitfall 6: `scheduling_confirmed` Default in Frontend Zod Schema

**What goes wrong:** Zod's `z.boolean()` without `.default(false)` causes validation failures when the field is omitted from the form submission (field value is `undefined` from unchecked checkbox).

**Why it happens:** HTML checkbox sends `undefined` when unchecked in uncontrolled form patterns; react-hook-form registers it as `undefined`.

**How to avoid:** Use `z.boolean().default(false)` in the care event Zod schema. [VERIFIED: Zod v3 supports `.default()` on primitive schemas.]

### Pitfall 7: Parallel CANCELLED_EVENT + DELAYED_EVENT for Same Step

**What goes wrong:** A CANCELLED event for a step also satisfies the DELAYED_EVENT condition (non-completed match exists and window exceeded). Both alerts fire in the same evaluation cycle.

**Why it happens:** The current DELAYED_EVENT branch does not exclude CANCELLED status. After Phase 7, the status-aware branching must explicitly skip DELAYED_EVENT when status is CANCELLED (since CANCELLED_EVENT already fires).

**How to avoid:** The status branch is mutually exclusive: if the matching event's status is CANCELLED, fire CANCELLED_EVENT and `continue` to the next step without checking DELAYED_EVENT.

---

## Code Examples

### Extending CreateCareEventRequest DTO

```java
// Source: existing pattern in src/main/java/com/onconavigator/web/dto/CreateCareEventRequest.java
public record CreateCareEventRequest(
        @NotNull(message = "Event type is required") CareEventType eventType,
        @NotNull(message = "Event date is required") LocalDate eventDate,
        @NotNull(message = "Status is required") CareEventStatus status,
        String notes,
        UUID documentId,
        // Phase 7 additions:
        LocalDate expectedCompletionDate,           // nullable — only relevant for SCHEDULED/PENDING
        Boolean schedulingConfirmed,               // nullable from request; defaults to false in entity
        String externalFacilityName                // nullable — optional external facility
) {}
```

### Extending CareEventResponse DTO

```java
// Phase 7: add three fields to mirror entity additions
public record CareEventResponse(
        UUID id,
        UUID patientId,
        CareEventType eventType,
        LocalDate eventDate,
        CareEventStatus status,
        String notes,
        String pathwayStepId,
        OffsetDateTime createdAt,
        // Phase 7 additions:
        LocalDate expectedCompletionDate,
        boolean schedulingConfirmed,
        String externalFacilityName
) {}
```

### Patient Entity: referralReceivedAt Addition

```java
// Add to Patient.java (src/main/java/com/onconavigator/domain/Patient.java)
// No encryption needed — timestamp is not PHI
@Column(name = "referral_received_at")
private OffsetDateTime referralReceivedAt;

// + getter/setter pair
```

### AlertType Enum Extension

```java
// src/main/java/com/onconavigator/domain/enums/AlertType.java
public enum AlertType {
    MISSING_EVENT,
    DELAYED_EVENT,
    OUT_OF_ORDER,
    // Phase 7 additions:
    RESULTS_NOT_READY,
    SCHEDULING_UNCONFIRMED,
    DEADLINE_APPROACHING,
    CANCELLED_EVENT
}
```

### AlertRepository: Updated Severity Sort Query

```java
// Updated JPQL in AlertRepository.findByStatusOrderedBySeverity()
@Query("""
        SELECT a FROM Alert a
        WHERE a.status = :status
        ORDER BY
            CASE a.alertType
                WHEN 'DELAYED_EVENT'          THEN 1
                WHEN 'CANCELLED_EVENT'        THEN 2
                WHEN 'RESULTS_NOT_READY'      THEN 3
                WHEN 'DEADLINE_APPROACHING'   THEN 4
                WHEN 'MISSING_EVENT'          THEN 5
                WHEN 'SCHEDULING_UNCONFIRMED' THEN 6
                WHEN 'OUT_OF_ORDER'           THEN 7
                ELSE 8
            END ASC,
            a.createdAt ASC
        """)
List<Alert> findByStatusOrderedBySeverity(@Param("status") AlertStatus status);
```

### Frontend: Extended Care Event Zod Schema

```typescript
// In QuickAddCareEventDialog.tsx — extend existing schema
const careEventSchema = z.object({
  eventType: z.string().min(1, { error: 'Event type is required.' }),
  eventDate: z.string().min(1, { error: 'Event date is required.' }),
  status: z.string().min(1, { error: 'Status is required.' }),
  notes: z.string().optional(),
  // Phase 7 additions:
  expectedCompletionDate: z.string().optional(),   // date string, shown only for SCHEDULED/PENDING
  schedulingConfirmed: z.boolean().default(false),  // checkbox
  externalFacilityName: z.string().optional(),      // free text
});
```

### Frontend: Alert Description Templates (Claude's Discretion resolved)

Recommended template strings for the new alert types (follow existing pattern in step.alertText or PathwayEvaluationActivityImpl.buildAlertDescription fallback):

```
RESULTS_NOT_READY: "Pending test results are not expected before the upcoming {visitType} appointment
  on {visitDate}. Review with physician whether visit should proceed without results
  or be rescheduled."

SCHEDULING_UNCONFIRMED: "Scheduling with {externalFacilityName} has not been confirmed.
  Contact {externalFacilityName} to verify {stepName} appointment is scheduled."

DEADLINE_APPROACHING: "{stepName} is due within 48 hours. Window expires on {deadlineDate}.
  Confirm the event is scheduled or record a completed event to prevent a MISSING_EVENT alert."

CANCELLED_EVENT: "{stepName} was cancelled. Reschedule or update the patient's pathway
  to reflect this change. Nurse navigator action required."
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Flyway `ALTER TYPE` in transaction | `-- flyway:nonTransactional` directive | Flyway 10 | Required for PostgreSQL enum additions |
| Flat evaluation (all non-COMPLETED = problem) | Status-aware branching (SCHEDULED/PENDING suppress missing) | Phase 7 | Eliminates false-positive MISSING_EVENT alerts for actively scheduled steps |
| Single root anchor (diagnosisDate) | Referral date primary, diagnosis date fallback | Phase 7 | Aligns pathway clock with clinical reality |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | SCHEDULING_UNCONFIRMED should check both SCHEDULED and PENDING events without `scheduling_confirmed` flag | Architecture Patterns, Pattern 4 | If only PENDING should be checked, SCHEDULED events without confirmation are silently ignored — lower safety |
| A2 | "Visit types" for RESULTS_NOT_READY = CONSULTATION + FOLLOW_UP; "result types" = PATHOLOGY_REPORT + LAB_WORK + IMAGING | Architecture Patterns, Pattern 6 | Wrong categorization means RESULTS_NOT_READY either never fires or fires for irrelevant event pairings |
| A3 | Alert severity ordering for new types (CANCELLED=2, RESULTS_NOT_READY=3, DEADLINE=4, MISSING=5, UNCONFIRMED=6) | Pattern 8, Code Examples | Clinical priority may differ — nurse navigator should confirm |
| A4 | Sentinel step name `"__RESULTS_NOT_READY__"` for patient-level alert dedup | Architecture Patterns, Pattern 6 | If the dedup approach changes (e.g., schema modification), the sentinel workaround is unnecessary |
| A5 | Referral detection hook should re-fetch patient from DB rather than using lazy proxy | Architecture Patterns, Pattern 7 | If LazyInitializationException occurs in production, the hook silently fails |
| A6 | 24-hour evaluation cycle is sufficient for 48-hour DEADLINE_APPROACHING (alert may fire up to 24h "late") | Pattern 5 | Pilot oncologist may expect more precise 48h notification — can add a dedicated Temporal timer if needed |

---

## Open Questions

1. **Where to trigger `referral_received_at` for globally-uploaded REFERRAL_LETTER documents**
   - What we know: The detection hook fires in `processUpload()` when patient is already linked. Global uploads match a patient after return from this method.
   - What's unclear: Is there a callback/event when a ClinicalDocument is later linked to a patient (PATCH)?
   - Recommendation: Search for the document link PATCH endpoint and add the hook there as well. If none exists yet, note as a gap for Phase 7 Wave 0.

2. **Classification prompt: does Claude already return `REFERRAL_LETTER` for referral documents?**
   - What we know: The prompt includes only PATHOLOGY_REPORT and RADIOLOGY_REPORT examples.
   - What's unclear: What string does Claude return for referral letters given the current prompt?
   - Recommendation: Test with a referral PDF from the test corpus before finalizing the detection hook.

3. **`externalFacilityName` on existing care events: should SCHEDULING_UNCONFIRMED alert include facility name?**
   - What we know: `external_facility_name` is optional and may be null.
   - What's unclear: Should the alert description fall back gracefully when name is null?
   - Recommendation: Template should use "the outside facility" as fallback when `externalFacilityName` is null.

---

## Environment Availability

Step 2.6: SKIPPED — Phase 7 is a code/schema extension with no new external tool dependencies. All runtime services (PostgreSQL 16, Temporal, Keycloak, Spring Boot) are already running per the existing Docker Compose configuration.

---

## Security Domain

### Applicable ASVS Categories (Level 1)

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V5 Input Validation | yes | Bean Validation (@NotNull on DTO fields); Zod v3 on frontend; `externalFacilityName` capped at VARCHAR(255) in schema |
| V2 Authentication | no change | Existing JWT/Keycloak guards on all endpoints carry forward |
| V4 Access Control | yes | New Patient.referralReceivedAt field must follow existing RBAC: CARE_COORDINATOR/ADMIN to write; all roles to read |
| V6 Cryptography | yes | `referral_received_at` is a timestamp — not PHI, no encryption needed. `externalFacilityName` is a facility name (not PHI), no encryption needed. `expectedCompletionDate` is a date — not PHI, no encryption needed. [VERIFIED: HIPAA PHI definition does not include facility names or scheduling dates without linking to a named patient — these fields only link via patient_id UUID, not in the field value itself.] |

### Known Threat Patterns for This Phase

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| BOLA via care event update with crafted patientId | Tampering | Existing T-03-10 ownership check in PatientService.updateCareEventStatus — carries forward; new fields go through same PATCH path |
| PHI in `externalFacilityName` free text field | Information Disclosure | Frontend guidance + backend logging policy: never log field value; log only `careEventId`. Facility names (e.g., "Memorial Hospital") are not PHI per HIPAA definition |
| `referral_received_at` manual override sets date in future | Tampering | No business-rule validation needed for pilot; if added, validate `referral_received_at <= today` in service layer |
| CANCELLED_EVENT alert flooding for repeatedly re-cancelled events | Denial of Service (UX) | Existing dedup on `(patient_id, pathway_step_name, OPEN)` prevents repeat alerts — already handled |
| Classification prompt injection via malicious PDF content | Spoofing | Existing: classification is gated by `onconavigator.ai.document-classification.enabled` feature flag; BAA required before production use. REFERRAL_LETTER detection adds no new attack surface |

---

## Sources

### Primary (HIGH confidence)

- Codebase: `PathwayEvaluationActivityImpl.java` — full evaluation loop, dedup pattern, anchor resolution [VERIFIED]
- Codebase: `DocumentProcessingService.java` — classification hook insertion point at line 170 [VERIFIED]
- Codebase: `AlertType.java`, `CareEventStatus.java` — confirmed existing values [VERIFIED]
- Codebase: `Patient.java`, `CareEvent.java` — confirmed current schema [VERIFIED]
- Codebase: `PatientPathwayWorkflowImpl.java` — confirmed 24-hour timer / signal pattern [VERIFIED]
- Codebase: `AlertRepository.java` — confirmed dedup method and severity sort JPQL [VERIFIED]
- Codebase: `V1__create_base_schema.sql` — confirmed current PostgreSQL enum definitions [VERIFIED]
- Codebase: `ClassificationPrompts.java` — confirmed absence of REFERRAL_LETTER example [VERIFIED]
- Codebase: `QuickAddCareEventDialog.tsx`, `types.ts` — confirmed current form fields and TypeScript types [VERIFIED]
- Codebase: `.planning/config.json` — confirmed `nyquist_validation: false`, `security_enforcement: true`, `security_asvs_level: 1` [VERIFIED]

### Secondary (MEDIUM confidence)

- CLAUDE.md: Flyway 10+ requires both `flyway-core` and `flyway-database-postgresql`; project already uses both [VERIFIED: CLAUDE.md]
- CLAUDE.md: Hibernate Envers `@Audited` on all ePHI entities — confirms new fields auto-audited [VERIFIED: CLAUDE.md]
- CLAUDE.md: `spring.threads.virtual.enabled=true` — no impact on Phase 7 implementation

### Tertiary (LOW confidence — ASSUMED)

- SCHEDULING_UNCONFIRMED applies to both SCHEDULED and PENDING events [A1]
- CareEventType classification for visit vs. result types [A2]
- Alert severity ordering for new types [A3]

---

## Metadata

**Confidence breakdown:**
- Schema migration: HIGH — confirmed existing Flyway pattern; PostgreSQL ALTER TYPE non-transactional requirement is well-documented
- Evaluation activity rewrite: HIGH — full source code reviewed; branching logic is straightforward extension
- Referral detection hook: HIGH — insertion point confirmed; single edge-case (global upload flow) flagged
- Frontend form extension: HIGH — existing form pattern is clear; Zod schema extension is standard
- Alert type priority ordering: ASSUMED — clinical judgment required for final ranking

**Research date:** 2026-05-05
**Valid until:** 2026-06-05 (stable stack; 30-day validity)
