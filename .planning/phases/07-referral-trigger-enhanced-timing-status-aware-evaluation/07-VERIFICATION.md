---
phase: 07-referral-trigger-enhanced-timing-status-aware-evaluation
verified: 2026-05-05T18:15:00Z
status: human_needed
score: 8/8
overrides_applied: 0
human_verification:
  - test: "Upload a REFERRAL_LETTER PDF from the test corpus via the document drop zone on a patient detail page and verify referralReceivedAt is auto-set"
    expected: "Patient record shows referralReceivedAt populated with current timestamp after upload"
    why_human: "End-to-end pipeline requires running services (Temporal, PostgreSQL, Keycloak, Claude API) and real document upload flow"
  - test: "Create a SCHEDULED care event with expectedCompletionDate and schedulingConfirmed=false, wait for pathway evaluation cycle, verify SCHEDULING_UNCONFIRMED alert appears"
    expected: "Alert queue shows SCHEDULING_UNCONFIRMED alert with external facility name after 7-day threshold"
    why_human: "Requires live Temporal workflow evaluation cycle and time-dependent logic that cannot be verified statically"
  - test: "Verify conditional form fields appear/disappear when changing care event status dropdown"
    expected: "expectedCompletionDate date picker and schedulingConfirmed checkbox appear only when status is SCHEDULED or PENDING; externalFacilityName is always visible"
    why_human: "Visual/interactive UI behavior requires browser rendering"
  - test: "Verify alert severity badge colors render correctly for all 7 alert types on the dashboard"
    expected: "OVERDUE/CANCELLED show destructive (red) badges; MISSING/RESULTS PENDING/DEADLINE show default badges; UNCONFIRMED/OUT OF ORDER show secondary badges"
    why_human: "CSS variable rendering and visual appearance require browser inspection"
---

# Phase 7: Referral Trigger + Enhanced Timing + Status-Aware Evaluation Verification Report

**Phase Goal:** The pathway clock starts from referral PDF receipt. The evaluation engine understands event statuses (Scheduled, Pending, Cancelled) and generates new alert types for results-before-visit, scheduling confirmation, and deadline escalation.
**Verified:** 2026-05-05T18:15:00Z
**Status:** human_needed
**Re-verification:** No -- initial verification

## Goal Achievement

### Observable Truths

| # | Truth (Roadmap SC) | Status | Evidence |
|---|-------------------|--------|----------|
| 1 | Patient record tracks referral_received_at timestamp, set when referral document uploaded | VERIFIED | `Patient.java` line 74-75: `@Column(name = "referral_received_at") private OffsetDateTime referralReceivedAt;` with getter/setter at lines 182-188. V18 migration adds column. `DocumentProcessingService.java` lines 174-183: auto-sets `referralReceivedAt` when REFERRAL_LETTER classified and patient linked. |
| 2 | Pathway steps can use REFERRAL_DATE as anchor type for time window calculation | VERIFIED | `PathwayEvaluationActivityImpl.java` lines 495-500: `resolveRootAnchor()` returns `referralReceivedAt.toLocalDate()` with `diagnosisDate` fallback. Called at line 202 for root steps. No explicit `REFERRAL_DATE` enum, but root steps automatically use referral date when available per D-03. Intent satisfied. |
| 3 | Care events track expected_completion_date, scheduling_confirmed, external_facility_name | VERIFIED | `CareEvent.java` lines 78-87: three fields with `@Column` annotations, default `Boolean.FALSE` for `schedulingConfirmed`, getters/setters at lines 176-198. V18 migration adds columns. `CreateCareEventRequest.java` lines 27-29 and `CareEventResponse.java` lines 28-30 include fields. `PatientService.java` maps fields in both directions (lines 204-206 for request-to-entity, lines 325-327 for entity-to-response). |
| 4 | System generates RESULTS_NOT_READY alert for results-before-visit conflicts | VERIFIED | `PathwayEvaluationActivityImpl.java` lines 317-369: cross-event check filters upcoming visits (CONSULTATION/FOLLOW_UP within 14-day lookahead) against pending results (PATHOLOGY_REPORT/LAB_WORK/IMAGING) with expectedCompletionDate after earliest visit. Uses sentinel step name `__RESULTS_NOT_READY__` for dedup. Test `testResultsNotReadyFires()` passes (line 393-434). |
| 5 | System generates SCHEDULING_UNCONFIRMED alert after 7 days | VERIFIED | `PathwayEvaluationActivityImpl.java` lines 268-287: checks `schedulingConfirmed` is false, calculates 7-day deadline from `referralReceivedAt` (root steps) or `eventDate` (subsequent), includes `externalFacilityName` with "the outside facility" fallback. Test `testSchedulingUnconfirmedAfter7DaysFromReferral()` passes (line 328-355). |
| 6 | System generates DEADLINE_APPROACHING alert 48 hours before deadline | VERIFIED | `PathwayEvaluationActivityImpl.java` lines 250-258 (SCHEDULED/PENDING branch) and lines 304-312 (no-event branch): fires when `daysLeft >= 0 && daysLeft <= 2`. Test `testDeadlineApproachingFiresAt48Hours()` passes (line 299-321). |
| 7 | CANCELLED event triggers immediate corrective action alert | VERIFIED | `PathwayEvaluationActivityImpl.java` lines 239-245: `CareEventStatus.CANCELLED` fires `AlertType.CANCELLED_EVENT` with description and `continue` for mutual exclusion with DELAYED_EVENT (Pitfall 7). Test `testCancelledEventFiresImmediateAlert()` passes (line 240-263). |
| 8 | SCHEDULED/PENDING event with past expected_completion_date triggers DELAYED alert | VERIFIED | `PathwayEvaluationActivityImpl.java` lines 260-266: within SCHEDULED/PENDING branch, checks `expectedCompletionDate != null && LocalDate.now().isAfter(expectedCompletionDate)` and fires `AlertType.DELAYED_EVENT`. |

**Score:** 8/8 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/resources/db/migration/V17__add_alert_type_values.sql` | Four new alert_type enum values | VERIFIED | 10 lines, `-- flyway:nonTransactional` directive, 4 `ALTER TYPE ADD VALUE IF NOT EXISTS` statements for RESULTS_NOT_READY, SCHEDULING_UNCONFIRMED, DEADLINE_APPROACHING, CANCELLED_EVENT |
| `src/main/resources/db/migration/V18__add_care_event_scheduling_fields.sql` | referral_received_at on patients, 3 columns on care_events, index | VERIFIED | 21 lines, `referral_received_at TIMESTAMP WITH TIME ZONE`, `expected_completion_date DATE`, `scheduling_confirmed BOOLEAN NOT NULL DEFAULT FALSE`, `external_facility_name VARCHAR(255)`, partial index on (patient_id, status, expected_completion_date) |
| `src/main/java/com/onconavigator/domain/enums/AlertType.java` | Extended enum with 7 values | VERIFIED | 19 lines, contains all 7 values: MISSING_EVENT, DELAYED_EVENT, OUT_OF_ORDER, RESULTS_NOT_READY, SCHEDULING_UNCONFIRMED, DEADLINE_APPROACHING, CANCELLED_EVENT |
| `src/main/java/com/onconavigator/domain/Patient.java` | referralReceivedAt field | VERIFIED | 229 lines, `@Column(name = "referral_received_at")` at line 74, `@Audited` at class level (line 37), getter/setter at lines 182-188 |
| `src/main/java/com/onconavigator/domain/CareEvent.java` | Three scheduling fields | VERIFIED | 215 lines, `expectedCompletionDate` (line 79), `schedulingConfirmed` with `Boolean.FALSE` default (line 83), `externalFacilityName` (line 87), all with `@Column` annotations, getters/setters at lines 176-198 |
| `src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java` | Status-aware evaluation with all four new alert types | VERIFIED | 501 lines, `resolveRootAnchor` at line 495, `allEventsByType` at line 182, status branches (CANCELLED/SCHEDULED-PENDING/NONE) at lines 227-314, RESULTS_NOT_READY at lines 317-369 |
| `src/main/java/com/onconavigator/service/DocumentProcessingService.java` | Referral detection hook | VERIFIED | Lines 174-183: checks `"REFERRAL_LETTER".equals(documentType)`, re-fetches patient, null guard for first-referral-only, PHI-safe logging |
| `src/main/java/com/onconavigator/web/dto/PatientResponse.java` | referralReceivedAt field | VERIFIED | Line 31: `OffsetDateTime referralReceivedAt` |
| `src/main/java/com/onconavigator/web/dto/CareEventResponse.java` | Three scheduling fields | VERIFIED | Lines 28-30: `expectedCompletionDate`, `schedulingConfirmed` (primitive boolean), `externalFacilityName` |
| `src/main/java/com/onconavigator/web/dto/CreateCareEventRequest.java` | Three scheduling fields | VERIFIED | Lines 27-29: `expectedCompletionDate`, `schedulingConfirmed` (nullable Boolean), `externalFacilityName` |
| `src/main/java/com/onconavigator/service/AlertService.java` | toSeverityLabel for 7 alert types | VERIFIED | Lines 147-159: switch expression maps all 7 AlertType values to display labels |
| `src/main/java/com/onconavigator/repository/AlertRepository.java` | Severity sort JPQL with 7 types | VERIFIED | Lines 87-103: CASE expression orders DELAYED(1), CANCELLED(2), RESULTS_NOT_READY(3), DEADLINE_APPROACHING(4), MISSING(5), SCHEDULING_UNCONFIRMED(6), OUT_OF_ORDER(7) |
| `src/main/java/com/onconavigator/ai/prompt/ClassificationPrompts.java` | REFERRAL_LETTER example | VERIFIED | Line 38: EXAMPLE 3 with `"documentType":"REFERRAL_LETTER"` |
| `src/main/java/com/onconavigator/service/PatientService.java` | Mapper wiring for new fields | VERIFIED | Lines 204-206: `addCareEvent` wires request to entity. Lines 302, 325-327: response mappers include new fields |
| `frontend/src/features/patients/types.ts` | TypeScript types with Phase 7 fields | VERIFIED | Line 10: `referralReceivedAt: string \| null`, lines 41-43: three CareEventResponse fields, lines 53-55: three CreateCareEventRequest optional fields |
| `frontend/src/features/alerts/types.ts` | 7 alert types and severity labels | VERIFIED | Line 6: 7-member alertType union, Line 7: 7-member severityLabel union |
| `frontend/src/features/alerts/AlertCard.tsx` | Badge variants for new severity labels | VERIFIED | Cases for CANCELLED, RESULTS PENDING, DEADLINE, UNCONFIRMED in both getSeverityBorderColor and getSeverityBadgeVariant |
| `frontend/src/features/alerts/ResolveAlertModal.tsx` | Badge variants for new severity labels | VERIFIED | Cases for CANCELLED, RESULTS PENDING, DEADLINE in getSeverityBadgeVariant |
| `frontend/src/features/patients/QuickAddCareEventDialog.tsx` | Conditional scheduling form fields | VERIFIED | Zod schema with 3 fields (lines 33-35), `form.watch('status')` conditional at line 221, handleSubmit passes fields conditionally (lines 101-103) |
| `src/test/java/com/onconavigator/activity/PathwayEvaluationStatusAwareTest.java` | 10 unit tests for Phase 7 evaluation | VERIFIED | 484 lines, 10 test methods, Mockito manual mock pattern, covers all 4 new alert types positive and negative cases |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| V17 migration | AlertType.java | PostgreSQL enum values match Java enum constants | VERIFIED | Both contain exactly: RESULTS_NOT_READY, SCHEDULING_UNCONFIRMED, DEADLINE_APPROACHING, CANCELLED_EVENT |
| V18 migration | Patient.java + CareEvent.java | Column names match JPA @Column annotations | VERIFIED | `referral_received_at` maps to `@Column(name = "referral_received_at")`, `expected_completion_date`/`scheduling_confirmed`/`external_facility_name` all match |
| PathwayEvaluationActivityImpl | AlertRepository | createAlertIfNotDuplicate calls existsByPatientIdAndPathwayStepNameAndStatus | VERIFIED | Line 414: `alertRepository.existsByPatientIdAndPathwayStepNameAndStatus(...)` |
| DocumentProcessingService | Patient.referralReceivedAt | setReferralReceivedAt when REFERRAL_LETTER classified | VERIFIED | Line 179: `referralPatient.setReferralReceivedAt(OffsetDateTime.now())` |
| resolveRootAnchor | Patient.getReferralReceivedAt | Fallback chain: referralReceivedAt -> diagnosisDate | VERIFIED | Lines 496-499: null check then toLocalDate(), fallback to getDiagnosisDate() |
| frontend types.ts | Java DTOs | JSON field names match | VERIFIED | `expectedCompletionDate`, `schedulingConfirmed`, `externalFacilityName`, `referralReceivedAt` all match between TypeScript and Java |
| frontend alerts/types.ts | AlertService.toSeverityLabel | alertType and severityLabel values match | VERIFIED | All 7 alertType values and 7 severityLabel strings match between frontend union and backend switch |
| PathwayEvaluationStatusAwareTest | PathwayEvaluationActivityImpl | Direct unit test of evaluate() | VERIFIED | Test class directly instantiates and calls `activity.evaluate(PATIENT_ID)` with mocked dependencies |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Backend compiles | `./mvnw compile` | BUILD SUCCESS | PASS |
| Phase 7 tests pass | `./mvnw test -Dtest=PathwayEvaluationStatusAwareTest` | Tests run: 10, Failures: 0, Errors: 0 | PASS |
| Frontend compiles | `cd frontend && npx tsc --noEmit` | Exit code 0, no output (no errors) | PASS |
| All commit hashes exist | `git log --oneline -30` | All 7 commit hashes (b241cfd, cdc055b, 98a337c, 02677cd, cfba8db, cf0edef, e627a70) found | PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| PW-ALL-001 | 07-01, 07-02, 07-03, 07-04 | Results-before-visit, scheduling confirmations, referral tracking, escalation | SATISFIED | RESULTS_NOT_READY cross-event check (lines 317-369), SCHEDULING_UNCONFIRMED 7-day check (lines 268-287), referralReceivedAt field and hook, DEADLINE_APPROACHING 48-hour warning (lines 250-258, 304-312) |
| PW-ALL-003 | 07-01, 07-02, 07-03, 07-04 | Event status tracking (Scheduled/Pending/Cancelled) drives evaluation branching | SATISFIED | Status-aware evaluation in PathwayEvaluationActivityImpl: CANCELLED branch (lines 239-245), SCHEDULED/PENDING branch (lines 247-290), no-event branch (lines 291-314). CareEventStatus enum already had all values. |
| PW-CR-001 | 07-01, 07-03, 07-04 | Pathway clock starts from referral PDF receipt | SATISFIED | `resolveRootAnchor()` uses `referralReceivedAt.toLocalDate()` as primary anchor (line 496-497), `DocumentProcessingService` auto-sets on REFERRAL_LETTER upload (lines 174-183), ClassificationPrompts has REFERRAL_LETTER example (line 38). |

Note: PW-ALL-001, PW-ALL-003, PW-CR-001 are domain-level requirements from the oncologist clinical review (docs/Pathway-Template-Review-Worksheet.md), not v1 product requirement IDs from REQUIREMENTS.md. This is consistent with the Phase 5 and Phase 6 verification approach. No orphaned REQUIREMENTS.md IDs for this phase.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| PathwayEvaluationActivityImpl.java | 416 | `return null` in createAlertIfNotDuplicate | INFO | Intentional -- signals "no alert created" when duplicate exists. Not a stub. |

No TODO/FIXME/PLACEHOLDER markers found in any Phase 7 modified files. No stub implementations detected.

### Human Verification Required

### 1. End-to-End Referral Detection

**Test:** Upload a REFERRAL_LETTER PDF from the test corpus via the document drop zone on a patient detail page. Check the patient record afterward.
**Expected:** Patient record shows `referralReceivedAt` populated with the upload timestamp. The alert evaluation on the next Temporal cycle uses this date as the root anchor.
**Why human:** Requires running services (Temporal, PostgreSQL, Keycloak, Claude API) and a real document upload flow through the entire pipeline.

### 2. Live Alert Generation Cycle

**Test:** Create a SCHEDULED care event with `expectedCompletionDate` in the past and `schedulingConfirmed=false`. Wait for or trigger a pathway evaluation cycle.
**Expected:** Alert queue shows DELAYED_EVENT (past expected completion) and SCHEDULING_UNCONFIRMED (if 7+ days have passed). Severity ordering places DELAYED above SCHEDULING_UNCONFIRMED.
**Why human:** Requires live Temporal workflow evaluation cycle with time-dependent logic that cannot be verified statically.

### 3. Conditional Form Fields

**Test:** Open QuickAddCareEventDialog. Select status=COMPLETED, then switch to status=SCHEDULED.
**Expected:** Expected Completion Date and Scheduling Confirmed fields appear only when SCHEDULED or PENDING is selected. External Facility Name is always visible.
**Why human:** Visual/interactive UI behavior requires browser rendering.

### 4. Alert Severity Badge Colors

**Test:** Create alerts of each new type (CANCELLED_EVENT, RESULTS_NOT_READY, DEADLINE_APPROACHING, SCHEDULING_UNCONFIRMED) and view the alert dashboard.
**Expected:** Badges render with correct colors: CANCELLED=destructive (red), RESULTS PENDING=default, DEADLINE=default, UNCONFIRMED=secondary.
**Why human:** CSS variable rendering and visual appearance require browser inspection.

### Gaps Summary

No blocking gaps found. All 8 roadmap success criteria are verified in the codebase with substantive implementations, correct wiring, and passing tests.

**Notable observations (INFO, not blocking):**

1. **No unit test for the referral detection hook** in DocumentProcessingService. The hook logic (lines 174-183) is tested only implicitly through the full pipeline. A dedicated unit test mocking the classification result would add confidence.

2. **SC#2 wording mismatch:** The roadmap says "Pathway steps can use REFERRAL_DATE as anchor type" suggesting a per-step configurable anchor, but the implementation makes ALL root steps automatically use referral date when available. This is the correct behavior per D-03 but the roadmap wording is slightly misleading. Not a gap -- the design decision (D-03) governs.

3. **Timezone sensitivity in resolveRootAnchor:** `referralReceivedAt.toLocalDate()` converts OffsetDateTime to LocalDate using the system default timezone. In production across timezone boundaries this could cause off-by-one-day discrepancies. Acceptable for pilot at a single practice.

---

_Verified: 2026-05-05T18:15:00Z_
_Verifier: Claude (gsd-verifier)_
