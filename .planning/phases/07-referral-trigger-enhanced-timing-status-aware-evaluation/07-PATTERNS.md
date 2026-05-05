# Phase 7: Referral Trigger + Enhanced Timing + Status-Aware Evaluation - Pattern Map

**Mapped:** 2026-05-05
**Files analyzed:** 11 new/modified files
**Analogs found:** 11 / 11

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/main/resources/db/migration/V17__add_alert_type_values.sql` | migration | batch | `src/main/resources/db/migration/V16__add_rejected_status_and_ai_source.sql` | exact |
| `src/main/resources/db/migration/V18__add_care_event_scheduling_fields.sql` | migration | batch | `src/main/resources/db/migration/V16__add_rejected_status_and_ai_source.sql` | exact |
| `src/main/java/com/onconavigator/domain/Patient.java` | model | CRUD | `src/main/java/com/onconavigator/domain/CareEvent.java` | exact |
| `src/main/java/com/onconavigator/domain/CareEvent.java` | model | CRUD | `src/main/java/com/onconavigator/domain/Patient.java` | exact |
| `src/main/java/com/onconavigator/domain/enums/AlertType.java` | utility | — | `src/main/java/com/onconavigator/domain/enums/CareEventStatus.java` | exact |
| `src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java` | service | event-driven | itself (rewrite of existing) | exact |
| `src/main/java/com/onconavigator/service/DocumentProcessingService.java` | service | request-response | itself (hook addition) | exact |
| `src/main/java/com/onconavigator/repository/AlertRepository.java` | utility | CRUD | itself (query update) | exact |
| `src/main/java/com/onconavigator/web/dto/CreateCareEventRequest.java` | utility | request-response | `src/main/java/com/onconavigator/web/dto/CareEventResponse.java` | exact |
| `src/main/java/com/onconavigator/web/dto/CareEventResponse.java` | utility | request-response | `src/main/java/com/onconavigator/web/dto/CreateCareEventRequest.java` | exact |
| `frontend/src/features/patients/types.ts` | utility | request-response | itself (extension) | exact |
| `frontend/src/features/patients/QuickAddCareEventDialog.tsx` | component | request-response | itself (extension) | exact |

---

## Pattern Assignments

### `src/main/resources/db/migration/V17__add_alert_type_values.sql` (migration, batch)

**Analog:** `src/main/resources/db/migration/V16__add_rejected_status_and_ai_source.sql`

**Key constraint from V16 (line 10):** V16 uses `ALTER TYPE ... ADD VALUE IF NOT EXISTS` but does NOT use the `-- flyway:nonTransactional` directive — it relies on placing the ADD VALUE statement first in the migration. RESEARCH.md (Pattern 1) calls for `-- flyway:nonTransactional` on V17. Use the non-transactional directive to be safe with four consecutive ADD VALUE statements; V16 only had one.

**Migration structure pattern** (V16, lines 1-10):
```sql
-- V16__add_rejected_status_and_ai_source.sql
-- <description of what is added and why>
--
-- IMPORTANT: ALTER TYPE ... ADD VALUE is not fully transactional in PostgreSQL.
-- Place it FIRST in this migration. ADD VALUE IF NOT EXISTS is safe in Flyway's
-- default transaction context when it is the first statement (PostgreSQL 16).

ALTER TYPE pathway_step_status ADD VALUE IF NOT EXISTS 'REJECTED';
```

**V17 must use the non-transactional directive** because four sequential ADD VALUE calls cannot be guaranteed safe without it. V16's single-statement trick does not generalize. Header pattern:
```sql
-- V17__add_alert_type_values.sql
-- flyway:nonTransactional
-- PostgreSQL ALTER TYPE ADD VALUE cannot run inside a transaction block (Flyway 10+).
-- IF NOT EXISTS makes this idempotent (safe to re-run if partially applied).

ALTER TYPE alert_type ADD VALUE IF NOT EXISTS 'RESULTS_NOT_READY';
ALTER TYPE alert_type ADD VALUE IF NOT EXISTS 'SCHEDULING_UNCONFIRMED';
ALTER TYPE alert_type ADD VALUE IF NOT EXISTS 'DEADLINE_APPROACHING';
ALTER TYPE alert_type ADD VALUE IF NOT EXISTS 'CANCELLED_EVENT';
```

---

### `src/main/resources/db/migration/V18__add_care_event_scheduling_fields.sql` (migration, batch)

**Analog:** `src/main/resources/db/migration/V16__add_rejected_status_and_ai_source.sql`

**Column addition pattern** (V16, lines 13-15):
```sql
ALTER TABLE patient_pathway_steps
    ADD COLUMN IF NOT EXISTS source              VARCHAR(50),
    ADD COLUMN IF NOT EXISTS source_document_id  UUID REFERENCES clinical_documents(id),
    ADD COLUMN IF NOT EXISTS proposed_edges_json  TEXT;
```

**Index pattern** (V16, lines 21-23):
```sql
CREATE INDEX IF NOT EXISTS idx_pathway_steps_source_doc
    ON patient_pathway_steps(source_document_id)
    WHERE source_document_id IS NOT NULL;
```

**GRANT pattern** (V16, lines 25-26):
```sql
GRANT ALL ON patient_pathway_steps TO onco_app;
GRANT ALL ON clinical_documents TO onco_app;
```

**Base column types reference** from `V1__create_base_schema.sql` (lines 37-48):
```sql
CREATE TABLE care_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL REFERENCES patients(id),
    event_type care_event_type NOT NULL,
    event_date DATE NOT NULL,
    status care_event_status NOT NULL DEFAULT 'PENDING',
    notes_encrypted BYTEA,
    pathway_step_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    ...
);
```

**V18 structure to follow:**
```sql
-- V18__add_care_event_scheduling_fields.sql

-- patients: referral received timestamp (not PHI — no encryption needed)
ALTER TABLE patients
    ADD COLUMN IF NOT EXISTS referral_received_at TIMESTAMP WITH TIME ZONE;

-- care_events: scheduling coordination fields
ALTER TABLE care_events
    ADD COLUMN IF NOT EXISTS expected_completion_date DATE,
    ADD COLUMN IF NOT EXISTS scheduling_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS external_facility_name VARCHAR(255);

-- Index for RESULTS_NOT_READY cross-event query
CREATE INDEX IF NOT EXISTS idx_care_events_patient_status_expected
    ON care_events(patient_id, status, expected_completion_date)
    WHERE expected_completion_date IS NOT NULL;

GRANT ALL ON patients TO onco_app;
GRANT ALL ON care_events TO onco_app;
```

---

### `src/main/java/com/onconavigator/domain/Patient.java` (model, CRUD)

**Analog:** `src/main/java/com/onconavigator/domain/Patient.java` (adding field to existing file)

**Existing plain (non-encrypted) timestamp field pattern** (Patient.java, lines 83-87):
```java
@Column(name = "created_at", nullable = false, updatable = false)
private OffsetDateTime createdAt;

@Column(name = "updated_at", nullable = false)
private OffsetDateTime updatedAt;
```

**Existing nullable plain column pattern** (Patient.java, lines 78-79):
```java
@Column(name = "treating_physician")
private String treatingPhysician;
```

**`referralReceivedAt` field to add** — nullable OffsetDateTime, not PHI, no `@Convert`:
```java
// After diagnosisDate field (line 71), before assignedNavigatorId (line 74)
@Column(name = "referral_received_at")
private OffsetDateTime referralReceivedAt;
```

**Getter/setter pattern** (Patient.java, lines 170-178):
```java
public LocalDate getDiagnosisDate() {
    return diagnosisDate;
}

public void setDiagnosisDate(LocalDate diagnosisDate) {
    this.diagnosisDate = diagnosisDate;
}
```

**Getter/setter to add for referralReceivedAt:**
```java
public OffsetDateTime getReferralReceivedAt() {
    return referralReceivedAt;
}

public void setReferralReceivedAt(OffsetDateTime referralReceivedAt) {
    this.referralReceivedAt = referralReceivedAt;
}
```

**`@Audited` is already on the class** (Patient.java, line 37) — new field is automatically captured in `patients_AUD`. No annotation change needed.

---

### `src/main/java/com/onconavigator/domain/CareEvent.java` (model, CRUD)

**Analog:** `src/main/java/com/onconavigator/domain/CareEvent.java` (adding three fields)

**Existing nullable plain column pattern** (CareEvent.java, lines 73-75):
```java
@Column(name = "pathway_step_id")
private String pathwayStepId;
```

**Existing LocalDate field pattern** (CareEvent.java, lines 53-54):
```java
@Column(name = "event_date", nullable = false)
private LocalDate eventDate;
```

**Three fields to add** — all nullable except `schedulingConfirmed` which has a DB default:
```java
// After pathwayStepId field, before createdAt

@Column(name = "expected_completion_date")
private LocalDate expectedCompletionDate;

// scheduling_confirmed has DEFAULT FALSE in DB (V18); Java mirrors that with Boolean field.
// No @Column(nullable=false) here — the DB default handles inserts without this field.
@Column(name = "scheduling_confirmed")
private Boolean schedulingConfirmed = Boolean.FALSE;

@Column(name = "external_facility_name")
private String externalFacilityName;
```

**Getter/setter pattern** (CareEvent.java, lines 124-132):
```java
public LocalDate getEventDate() {
    return eventDate;
}

public void setEventDate(LocalDate eventDate) {
    this.eventDate = eventDate;
}
```

**`@Audited` is already on the class** (CareEvent.java, line 20) — new fields auto-captured.

---

### `src/main/java/com/onconavigator/domain/enums/AlertType.java` (utility)

**Analog:** `src/main/java/com/onconavigator/domain/enums/CareEventStatus.java`

**Existing enum pattern** (CareEventStatus.java, lines 1-12):
```java
package com.onconavigator.domain.enums;

/**
 * Status of a care event in a patient's pathway.
 * Maps to the care_event_status PostgreSQL enum.
 */
public enum CareEventStatus {
    SCHEDULED,
    COMPLETED,
    CANCELLED,
    PENDING
}
```

**AlertType.java update** — append four values preserving existing three:
```java
package com.onconavigator.domain.enums;

/**
 * Types of pathway deviations that trigger alerts.
 * Maps to the alert_type PostgreSQL enum.
 *
 * Phase 7 additions: RESULTS_NOT_READY, SCHEDULING_UNCONFIRMED,
 * DEADLINE_APPROACHING, CANCELLED_EVENT.
 */
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

---

### `src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java` (service, event-driven)

**Analog:** itself — extensive rewrite of the `evaluate()` method body. All imports, constructor injection, and helper method signatures are preserved.

**Existing import block to preserve** (PathwayEvaluationActivityImpl.java, lines 1-41):
```java
import com.onconavigator.domain.enums.CareEventStatus;
import com.onconavigator.domain.enums.CareEventType;
// ... (full block as-is, no new imports needed)
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
// ... (all existing imports carry forward)
```

**Existing anchor resolution pattern** (lines 192-203) — root anchor line to change:
```java
// EXISTING (line 194):
if (prereqs.isEmpty()) {
    anchorDate = patient.getDiagnosisDate();  // <-- CHANGE THIS LINE
}
// REPLACE WITH:
if (prereqs.isEmpty()) {
    anchorDate = resolveRootAnchor(patient);  // D-03: referral date primary, diagnosis fallback
}
```

**New private helper method** (add after `buildAlertDescription`, following same visibility pattern):
```java
/**
 * Resolves the root anchor date for root pathway steps (no prerequisites).
 *
 * <p>D-03: referralReceivedAt is the primary time anchor when set.
 * Falls back to diagnosisDate for patients enrolled before referral tracking.
 *
 * @param patient the patient entity
 * @return the anchor date for root steps
 */
private LocalDate resolveRootAnchor(Patient patient) {
    if (patient.getReferralReceivedAt() != null) {
        return patient.getReferralReceivedAt().toLocalDate();
    }
    return patient.getDiagnosisDate(); // D-03 fallback
}
```

**Existing `completedEventsByType` map build pattern** (lines 172-175):
```java
Map<CareEventType, List<CareEvent>> completedEventsByType = careEvents.stream()
        .filter(e -> e.getStatus() == CareEventStatus.COMPLETED)
        .collect(Collectors.groupingBy(CareEvent::getEventType));
```

**Phase 7 adds a parallel map for non-COMPLETED events** (insert after line 175, before the per-step loop):
```java
// All care events indexed by eventType (includes non-completed) — for status-aware branching
Map<CareEventType, List<CareEvent>> allEventsByType = careEvents.stream()
        .collect(Collectors.groupingBy(CareEvent::getEventType));
```

**Existing per-step deviation detection block** (lines 179-241) — the three detection branches (OUT_OF_ORDER, MISSING_EVENT, DELAYED_EVENT) are rewritten with status-aware branching. The structural pattern for calling `createAlertIfNotDuplicate` and `continue` is preserved:

```java
// EXISTING pattern for single alert + continue (lines 210-217):
String summary = createAlertIfNotDuplicate(patient, step, AlertType.OUT_OF_ORDER,
        "Out of order: " + step.getName() + " completed before prerequisites");
if (summary != null) {
    alertsGenerated.add(summary);
}
// One alert type per step per cycle — skip MISSING/DELAYED for the same step
continue;
```

**Phase 7 status-aware branching replaces the MISSING_EVENT + DELAYED_EVENT block** (lines 219-241) with:
```java
// Find the highest-priority matching care event for this step's eventType
List<CareEvent> stepEvents = allEventsByType.getOrDefault(step.getEventType(), List.of());
CareEvent activeEvent = stepEvents.stream()
        .filter(e -> e.getStatus() != CareEventStatus.COMPLETED)
        .findFirst()
        .orElse(null);

if (activeEvent != null) {
    CareEventStatus eventStatus = activeEvent.getStatus();
    if (eventStatus == CareEventStatus.CANCELLED) {
        // D-05: CANCELLED triggers immediate corrective alert
        String summary = createAlertIfNotDuplicate(patient, step, AlertType.CANCELLED_EVENT,
                step.getName() + " was cancelled. Reschedule or update the pathway.");
        if (summary != null) alertsGenerated.add(summary);
        continue; // Pitfall 7: mutually exclusive with DELAYED_EVENT
    }
    if (eventStatus == CareEventStatus.SCHEDULED || eventStatus == CareEventStatus.PENDING) {
        // D-04: suppress MISSING_EVENT; check deadline and scheduling confirmation
        // DEADLINE_APPROACHING check (D-06)
        if (step.getWindowDays() != null) {
            long daysLeft = step.getWindowDays() - daysSinceAnchor;
            if (daysLeft >= 0 && daysLeft <= 2) {
                String summary = createAlertIfNotDuplicate(patient, step,
                        AlertType.DEADLINE_APPROACHING,
                        step.getName() + " is due within 48 hours.");
                if (summary != null) alertsGenerated.add(summary);
            }
        }
        // DELAYED_EVENT via expectedCompletionDate (D-04)
        if (activeEvent.getExpectedCompletionDate() != null
                && LocalDate.now().isAfter(activeEvent.getExpectedCompletionDate())) {
            String summary = createAlertIfNotDuplicate(patient, step, AlertType.DELAYED_EVENT,
                    "Delayed: " + step.getName() + " (expected by "
                    + activeEvent.getExpectedCompletionDate() + ")");
            if (summary != null) alertsGenerated.add(summary);
        }
        // SCHEDULING_UNCONFIRMED (D-11/D-12)
        boolean notConfirmed = !Boolean.TRUE.equals(activeEvent.getSchedulingConfirmed());
        if (notConfirmed) {
            LocalDate confirmDeadline;
            if (prereqs.isEmpty() && patient.getReferralReceivedAt() != null) {
                confirmDeadline = patient.getReferralReceivedAt().toLocalDate().plusDays(7); // D-11
            } else {
                confirmDeadline = activeEvent.getEventDate() != null
                        ? activeEvent.getEventDate().plusDays(7) : null; // D-12
            }
            if (confirmDeadline != null && LocalDate.now().isAfter(confirmDeadline)) {
                String summary = createAlertIfNotDuplicate(patient, step,
                        AlertType.SCHEDULING_UNCONFIRMED,
                        "Scheduling not confirmed for " + step.getName());
                if (summary != null) alertsGenerated.add(summary);
            }
        }
        continue; // Step is in progress — no MISSING_EVENT
    }
} else {
    // No active event — check MISSING_EVENT and DEADLINE_APPROACHING
    if (step.isRequired() && step.getWindowDays() != null
            && daysSinceAnchor > step.getWindowDays()) {
        String summary = createAlertIfNotDuplicate(patient, step, AlertType.MISSING_EVENT,
                "Missing: " + step.getName() + " (expected within " + step.getWindowDays() + " days)");
        if (summary != null) alertsGenerated.add(summary);
    } else if (step.getWindowDays() != null) {
        long daysLeft = step.getWindowDays() - daysSinceAnchor;
        if (daysLeft >= 0 && daysLeft <= 2) {
            String summary = createAlertIfNotDuplicate(patient, step,
                    AlertType.DEADLINE_APPROACHING,
                    step.getName() + " window expires in " + daysLeft + " day(s).");
            if (summary != null) alertsGenerated.add(summary);
        }
    }
}
```

**RESULTS_NOT_READY cross-event check** — add once after the per-step loop (before the `boolean allStepsComplete` line):
```java
// D-08/D-09: Cross-event RESULTS_NOT_READY check (once per patient, not per step)
LocalDate today = LocalDate.now();
LocalDate lookaheadCutoff = today.plusDays(14);

List<CareEvent> upcomingVisits = careEvents.stream()
        .filter(e -> e.getEventType() == CareEventType.CONSULTATION
                  || e.getEventType() == CareEventType.FOLLOW_UP)
        .filter(e -> e.getStatus() == CareEventStatus.SCHEDULED
                  || e.getStatus() == CareEventStatus.PENDING)
        .filter(e -> e.getEventDate() != null
                  && !e.getEventDate().isBefore(today)
                  && !e.getEventDate().isAfter(lookaheadCutoff))
        .toList();

if (!upcomingVisits.isEmpty()) {
    LocalDate earliestVisit = upcomingVisits.stream()
            .map(CareEvent::getEventDate)
            .min(LocalDate::compareTo)
            .orElse(null);
    if (earliestVisit != null) {
        boolean resultsNotReady = careEvents.stream()
                .filter(e -> e.getEventType() == CareEventType.PATHOLOGY_REPORT
                          || e.getEventType() == CareEventType.LAB_WORK
                          || e.getEventType() == CareEventType.IMAGING)
                .filter(e -> e.getStatus() == CareEventStatus.SCHEDULED
                          || e.getStatus() == CareEventStatus.PENDING)
                .filter(e -> e.getExpectedCompletionDate() != null)
                .anyMatch(e -> e.getExpectedCompletionDate().isAfter(earliestVisit));
        if (resultsNotReady) {
            // Patient-level alert: use sentinel step name (Pitfall 4)
            boolean isDuplicate = alertRepository.existsByPatientIdAndPathwayStepNameAndStatus(
                    patient.getId(), "__RESULTS_NOT_READY__", AlertStatus.OPEN);
            if (!isDuplicate) {
                Alert rnrAlert = new Alert();
                rnrAlert.setPatientId(patient.getId());
                rnrAlert.setAlertType(AlertType.RESULTS_NOT_READY);
                rnrAlert.setPathwayStepName("__RESULTS_NOT_READY__");
                rnrAlert.setDeviationDescription(
                        "Pending test results are not expected before an upcoming visit. " +
                        "Review with physician whether the visit should proceed or be rescheduled.");
                rnrAlert.setSuggestedAction("Contact the ordering facility for an estimated result date.");
                rnrAlert.setStatus(AlertStatus.OPEN);
                alertRepository.save(rnrAlert);
                alertsGenerated.add("RESULTS_NOT_READY: patient " + patient.getId());
                log.info("ALERT_CREATED: patient={} type=RESULTS_NOT_READY", patient.getId());
            }
        }
    }
}
```

**Existing `createAlertIfNotDuplicate` helper signature** (lines 285-306) — unchanged:
```java
private String createAlertIfNotDuplicate(Patient patient, PatientPathwayStep step,
        AlertType alertType, String defaultDescription) {
    boolean isDuplicate = alertRepository.existsByPatientIdAndPathwayStepNameAndStatus(
            patient.getId(), step.getName(), AlertStatus.OPEN);
    if (isDuplicate) return null;
    // ... builds and saves Alert ...
}
```

**PHI-safe log pattern** (lines 244-246) — no PHI in log, UUID only:
```java
log.info("PATHWAY_EVALUATION: patient={} readySteps={} alertsGenerated={} allComplete={}",
        patientId, readySteps.size(), alertsGenerated.size(), allStepsComplete);
```

---

### `src/main/java/com/onconavigator/service/DocumentProcessingService.java` (service, request-response)

**Analog:** itself — single hook insertion after line 169 (after `doc = documentRepository.save(doc)`).

**Existing patient re-fetch pattern** (lines 159-163) — same pattern used for the referral hook:
```java
UUID effectivePatientId = matchedPatientId != null ? matchedPatientId : preSelectedPatientId;
if (effectivePatientId != null) {
    Patient patient = patientRepository.findById(effectivePatientId).orElse(null);
    if (patient != null) {
        doc.setPatient(patient);
    }
}
```

**Existing PHI-safe log pattern** (line 171):
```java
log.info("Document {} processed: type={} matchStatus={} patientLinked={}",
        doc.getId(), documentType, matchStatus, doc.getPatient() != null);
```

**Hook to insert after line 171** (after the existing log statement, before the stepExtractionTrigger block at line 176):
```java
// Phase 7: Auto-set referral_received_at when a REFERRAL_LETTER is linked to a patient (D-02)
if ("REFERRAL_LETTER".equals(documentType) && doc.getPatient() != null) {
    // Re-fetch to avoid LazyInitializationException on proxy (Pattern 7, A5)
    Patient p = patientRepository.findById(doc.getPatient().getId()).orElse(null);
    if (p != null && p.getReferralReceivedAt() == null) {
        p.setReferralReceivedAt(OffsetDateTime.now());
        patientRepository.save(p);
        log.info("REFERRAL_DETECTED: set referral_received_at for patient {}", p.getId()); // UUID only — no PHI
    }
}
```

**`OffsetDateTime` import** is already present (used by `ExtractionResult` record indirectly; confirm at class level — if not present, add `import java.time.OffsetDateTime;`).

---

### `src/main/java/com/onconavigator/repository/AlertRepository.java` (utility, CRUD)

**Analog:** itself — update the `findByStatusOrderedBySeverity` JPQL query.

**Existing severity sort query** (lines 82-94):
```java
@Query("""
        SELECT a FROM Alert a
        WHERE a.status = :status
        ORDER BY
            CASE a.alertType
                WHEN 'DELAYED_EVENT' THEN 1
                WHEN 'MISSING_EVENT' THEN 2
                WHEN 'OUT_OF_ORDER'  THEN 3
                ELSE 4
            END ASC,
            a.createdAt ASC
        """)
List<Alert> findByStatusOrderedBySeverity(@Param("status") AlertStatus status);
```

**Replacement query with Phase 7 alert types slotted in**:
```java
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

**Existing Javadoc pattern** (lines 70-81) — update the comment to document new ordering:
```java
/**
 * Find alerts by status, ordered by clinical severity then creation time.
 *
 * <p>Severity ordering (per ALRT-01, Phase 7 update):
 * DELAYED_EVENT (overdue) → 1, CANCELLED_EVENT (immediate action) → 2,
 * RESULTS_NOT_READY (wasted visit risk) → 3, DEADLINE_APPROACHING (proactive) → 4,
 * MISSING_EVENT (not recorded) → 5, SCHEDULING_UNCONFIRMED (confirmation gap) → 6,
 * OUT_OF_ORDER (sequencing issue) → 7.
 * ...
 */
```

---

### `src/main/java/com/onconavigator/web/dto/CreateCareEventRequest.java` (utility, request-response)

**Analog:** itself (existing record — add three optional fields)

**Existing record structure** (lines 20-26):
```java
public record CreateCareEventRequest(
        @NotNull(message = "Event type is required") CareEventType eventType,
        @NotNull(message = "Event date is required") LocalDate eventDate,
        @NotNull(message = "Status is required") CareEventStatus status,
        String notes,
        UUID documentId
) {}
```

**Pattern for nullable optional fields** — `String notes` and `UUID documentId` have no `@NotNull`, no default. Same pattern for Phase 7 additions:

```java
public record CreateCareEventRequest(
        @NotNull(message = "Event type is required") CareEventType eventType,
        @NotNull(message = "Event date is required") LocalDate eventDate,
        @NotNull(message = "Status is required") CareEventStatus status,
        String notes,
        UUID documentId,
        // Phase 7 additions:
        LocalDate expectedCompletionDate,    // nullable — relevant only for SCHEDULED/PENDING
        Boolean schedulingConfirmed,         // nullable from request; entity defaults to FALSE
        String externalFacilityName          // nullable — optional external facility name
) {}
```

---

### `src/main/java/com/onconavigator/web/dto/CareEventResponse.java` (utility, request-response)

**Analog:** itself (existing record — mirror the same three additions)

**Existing record structure** (lines 18-27):
```java
public record CareEventResponse(
        UUID id,
        UUID patientId,
        CareEventType eventType,
        LocalDate eventDate,
        CareEventStatus status,
        String notes,
        String pathwayStepId,
        OffsetDateTime createdAt
) {}
```

**Phase 7 additions** follow the same unboxed primitive/nullable pattern:
```java
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
        boolean schedulingConfirmed,      // primitive false default on deserialize
        String externalFacilityName
) {}
```

---

### `frontend/src/features/patients/types.ts` (utility, request-response)

**Analog:** itself — extend existing `CareEventResponse` and `CreateCareEventRequest` interfaces.

**Existing `CareEventResponse` interface** (lines 30-39):
```typescript
export interface CareEventResponse {
  id: string;
  patientId: string;
  eventType: string;
  eventDate: string;
  status: 'SCHEDULED' | 'COMPLETED' | 'CANCELLED' | 'PENDING';
  notes: string | null;
  pathwayStepId: string | null;
  createdAt: string;
}
```

**Extended version**:
```typescript
export interface CareEventResponse {
  id: string;
  patientId: string;
  eventType: string;
  eventDate: string;
  status: 'SCHEDULED' | 'COMPLETED' | 'CANCELLED' | 'PENDING';
  notes: string | null;
  pathwayStepId: string | null;
  createdAt: string;
  // Phase 7 additions:
  expectedCompletionDate: string | null;  // ISO date string
  schedulingConfirmed: boolean;
  externalFacilityName: string | null;
}
```

**Existing `CreateCareEventRequest` interface** (lines 41-47):
```typescript
export interface CreateCareEventRequest {
  eventType: string;
  eventDate: string;
  status: 'SCHEDULED' | 'COMPLETED' | 'CANCELLED' | 'PENDING';
  notes?: string;
  documentId?: string;
}
```

**Extended version**:
```typescript
export interface CreateCareEventRequest {
  eventType: string;
  eventDate: string;
  status: 'SCHEDULED' | 'COMPLETED' | 'CANCELLED' | 'PENDING';
  notes?: string;
  documentId?: string;
  // Phase 7 additions:
  expectedCompletionDate?: string;   // ISO date string, only for SCHEDULED/PENDING
  schedulingConfirmed?: boolean;     // omit to default to false on backend
  externalFacilityName?: string;
}
```

**Phase 7 also needs `PatientResponse` to include `referralReceivedAt`:**
```typescript
// In PatientResponse interface (lines 1-15), add after diagnosisDate:
referralReceivedAt: string | null;  // ISO timestamp, null if not yet set
```

---

### `frontend/src/features/patients/QuickAddCareEventDialog.tsx` (component, request-response)

**Analog:** itself — extend Zod schema, add three conditional fields to the form.

**Existing Zod schema** (lines 27-32):
```typescript
const careEventSchema = z.object({
  eventType: z.string().min(1, { error: 'Event type is required.' }),
  eventDate: z.string().min(1, { error: 'Event date is required.' }),
  status: z.string().min(1, { error: 'Status is required.' }),
  notes: z.string().optional(),
});
```

**Extended schema with Phase 7 fields** — follow Pitfall 6 pattern for `schedulingConfirmed`:
```typescript
const careEventSchema = z.object({
  eventType: z.string().min(1, { error: 'Event type is required.' }),
  eventDate: z.string().min(1, { error: 'Event date is required.' }),
  status: z.string().min(1, { error: 'Status is required.' }),
  notes: z.string().optional(),
  // Phase 7 additions:
  expectedCompletionDate: z.string().optional(),   // shown conditionally for SCHEDULED/PENDING
  schedulingConfirmed: z.boolean().default(false),  // Pitfall 6: must use .default(false)
  externalFacilityName: z.string().optional(),
});
```

**Existing `handleSubmit` mutation call pattern** (lines 85-100):
```typescript
function handleSubmit(values: CareEventFormValues) {
  createCareEvent.mutate(
    {
      eventType: values.eventType,
      eventDate: values.eventDate,
      status: values.status as 'SCHEDULED' | 'COMPLETED' | 'CANCELLED' | 'PENDING',
      notes: values.notes || undefined,
    },
    {
      onSuccess: () => {
        form.reset();
        onOpenChange(false);
      },
    }
  );
}
```

**Extended `handleSubmit`** — pass new fields through:
```typescript
function handleSubmit(values: CareEventFormValues) {
  const isScheduledOrPending = values.status === 'SCHEDULED' || values.status === 'PENDING';
  createCareEvent.mutate(
    {
      eventType: values.eventType,
      eventDate: values.eventDate,
      status: values.status as 'SCHEDULED' | 'COMPLETED' | 'CANCELLED' | 'PENDING',
      notes: values.notes || undefined,
      // Phase 7: only include scheduling fields for relevant statuses
      expectedCompletionDate: isScheduledOrPending ? values.expectedCompletionDate : undefined,
      schedulingConfirmed: isScheduledOrPending ? values.schedulingConfirmed : undefined,
      externalFacilityName: values.externalFacilityName || undefined,
    },
    { onSuccess: () => { form.reset(); onOpenChange(false); } }
  );
}
```

**Existing Input field pattern** (lines 150-160) — copy for `expectedCompletionDate`:
```typescript
<div className="grid gap-2">
  <Label htmlFor="eventDate">Event Date</Label>
  <Input
    id="eventDate"
    type="date"
    {...form.register('eventDate')}
    aria-invalid={!!form.formState.errors.eventDate}
  />
  {form.formState.errors.eventDate && (
    <p className="text-destructive text-xs">
      {form.formState.errors.eventDate.message}
    </p>
  )}
</div>
```

**Existing Textarea pattern** (lines 199-205) — `externalFacilityName` uses `<Input>` (single line), not Textarea.

**Conditional display pattern** — wrap the three new fields in a conditional block. Use `form.watch('status')` to conditionally show `expectedCompletionDate` and `schedulingConfirmed`:
```typescript
{/* Phase 7: Scheduling fields — shown for SCHEDULED or PENDING only */}
{(form.watch('status') === 'SCHEDULED' || form.watch('status') === 'PENDING') && (
  <>
    <div className="grid gap-2">
      <Label htmlFor="expectedCompletionDate">
        Expected Completion Date{' '}
        <span className="text-muted-foreground font-normal">(optional)</span>
      </Label>
      <Input
        id="expectedCompletionDate"
        type="date"
        {...form.register('expectedCompletionDate')}
      />
    </div>
    <div className="flex items-center gap-2">
      <input
        id="schedulingConfirmed"
        type="checkbox"
        {...form.register('schedulingConfirmed')}
        className="h-4 w-4"
      />
      <Label htmlFor="schedulingConfirmed">
        Scheduling confirmed with external facility
      </Label>
    </div>
  </>
)}

{/* External facility name — always visible (optional) */}
<div className="grid gap-2">
  <Label htmlFor="externalFacilityName">
    External Facility{' '}
    <span className="text-muted-foreground font-normal">(optional)</span>
  </Label>
  <Input
    id="externalFacilityName"
    type="text"
    placeholder="e.g., Memorial Hospital Radiology"
    {...form.register('externalFacilityName')}
  />
</div>
```

---

## Shared Patterns

### @Transactional + UUID-only Logging (HIPAA)
**Source:** `src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java` lines 110, 244-246
**Apply to:** All service method additions in DocumentProcessingService and PathwayEvaluationActivityImpl

```java
@Override
@Transactional
public PathwayEvaluationResult evaluate(UUID patientId) { ... }

// Logging: UUID only, no PHI
log.info("REFERRAL_DETECTED: set referral_received_at for patient {}", p.getId());
log.info("ALERT_CREATED: patient={} step={} type={}", alertType, patient.getId(), step.getId());
```

### Alert Deduplication via `existsByPatientIdAndPathwayStepNameAndStatus`
**Source:** `src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java` lines 287-289
**Apply to:** All four new alert types in PathwayEvaluationActivityImpl
```java
boolean isDuplicate = alertRepository.existsByPatientIdAndPathwayStepNameAndStatus(
        patient.getId(), step.getName(), AlertStatus.OPEN);
if (isDuplicate) return null;
```

For RESULTS_NOT_READY (patient-level), use sentinel: `"__RESULTS_NOT_READY__"` instead of `step.getName()`.

### Bean Validation on DTO Records
**Source:** `src/main/java/com/onconavigator/web/dto/CreateCareEventRequest.java` lines 20-26
**Apply to:** `CreateCareEventRequest` — new optional fields need no `@NotNull`; follow the pattern of `String notes` (no annotation = nullable/optional)

### `@Audited` Carries Forward Automatically
**Source:** `src/main/java/com/onconavigator/domain/Patient.java` line 37; `CareEvent.java` line 20
**Apply to:** New columns on `Patient` (`referralReceivedAt`) and `CareEvent` (`expectedCompletionDate`, `schedulingConfirmed`, `externalFacilityName`) — no annotation change needed on the field level; the class-level `@Audited` captures all columns automatically.

### TanStack Query Cache Invalidation Pattern
**Source:** `frontend/src/features/patients/api.ts` lines 61-74
**Apply to:** `api.ts` if any new mutations are added for setting `referralReceivedAt` manually (PatientWizard or patient detail edit form). Follow the `useCreateCareEvent` pattern of invalidating related query keys on success.
```typescript
onSuccess: () => {
  queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'care-events'] });
  queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-status'] });
  queryClient.invalidateQueries({ queryKey: ['alerts'] });
  queryClient.invalidateQueries({ queryKey: ['alerts', 'count'] });
  queryClient.invalidateQueries({ queryKey: ['dashboard', 'stats'] });
},
```

### Flyway IF NOT EXISTS Idempotency
**Source:** `src/main/resources/db/migration/V16__add_rejected_status_and_ai_source.sql` lines 13-15
**Apply to:** All `ALTER TABLE ... ADD COLUMN` and `CREATE INDEX` statements in V17 and V18
```sql
ADD COLUMN IF NOT EXISTS ...
CREATE INDEX IF NOT EXISTS ...
ALTER TYPE ... ADD VALUE IF NOT EXISTS ...
```

---

## No Analog Found

All files for Phase 7 have close analogs in the existing codebase. No new architectural patterns are required.

---

## Metadata

**Analog search scope:** `src/main/java/com/onconavigator/`, `src/main/resources/db/migration/`, `frontend/src/features/patients/`
**Files scanned:** 15 source files read in full
**Pattern extraction date:** 2026-05-05
