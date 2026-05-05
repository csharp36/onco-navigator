---
phase: 07-referral-trigger-enhanced-timing-status-aware-evaluation
plan: 03
subsystem: api, temporal-activity
tags: [pathway-evaluation, status-aware, referral-detection, alert-types, HIPAA]

# Dependency graph
requires:
  - phase: 07-referral-trigger-enhanced-timing-status-aware-evaluation
    plan: 01
    provides: AlertType 7 enum values, Patient.referralReceivedAt, CareEvent scheduling fields, DTOs, severity sort
provides:
  - Status-aware PathwayEvaluationActivityImpl with all 7 alert type detection
  - resolveRootAnchor method (referralReceivedAt primary, diagnosisDate fallback)
  - CANCELLED_EVENT immediate alert with Pitfall 7 mutual exclusivity
  - SCHEDULED/PENDING suppress MISSING_EVENT; check DEADLINE_APPROACHING, DELAYED_EVENT, SCHEDULING_UNCONFIRMED
  - RESULTS_NOT_READY cross-event patient-level check with 14-day lookahead and sentinel dedup
  - Referral detection hook in DocumentProcessingService auto-setting referralReceivedAt
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Status-aware evaluation branching: CANCELLED -> continue (mutually exclusive with DELAYED_EVENT)"
    - "Patient-level alert dedup via sentinel step name (__RESULTS_NOT_READY__) in existsByPatientIdAndPathwayStepNameAndStatus"
    - "Root anchor fallback chain: referralReceivedAt.toLocalDate() -> diagnosisDate"
    - "Referral detection hook: re-fetch patient from repository to avoid LazyInitializationException on JPA proxy"

key-files:
  created: []
  modified:
    - src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java
    - src/main/java/com/onconavigator/service/DocumentProcessingService.java

key-decisions:
  - "CANCELLED branch fires CANCELLED_EVENT and continues immediately (Pitfall 7) -- mutually exclusive with DELAYED_EVENT"
  - "SCHEDULING_UNCONFIRMED checks both SCHEDULED and PENDING events (per Research A1)"
  - "RESULTS_NOT_READY uses sentinel step name __RESULTS_NOT_READY__ for dedup (per Pitfall 4)"
  - "Referral hook uses referralPatient variable name to avoid shadowing existing variables in processUpload"
  - "Only first REFERRAL_LETTER upload sets referralReceivedAt (null check prevents overwrite per D-02/T-07-11)"

patterns-established:
  - "Status-aware evaluation: branch by CareEventStatus before alert generation"
  - "Patient-level alert creation: manual Alert construction with sentinel step name (bypasses step-based createAlertIfNotDuplicate)"
  - "Referral detection hook: post-classification check in document processing pipeline"

requirements-completed: [PW-ALL-001, PW-ALL-003, PW-CR-001]

# Metrics
duration: 4min
completed: 2026-05-05
---

# Phase 7 Plan 03: Evaluation Engine Rewrite and Referral Detection Summary

**Status-aware PathwayEvaluationActivityImpl with 7 alert types, referralReceivedAt root anchor fallback, and REFERRAL_LETTER auto-detection hook in DocumentProcessingService -- compiles cleanly**

## Performance

- **Duration:** 3m 58s
- **Started:** 2026-05-05T17:57:54Z
- **Completed:** 2026-05-05T18:01:52Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments

- Rewrote PathwayEvaluationActivityImpl.evaluate() from flat MISSING/DELAYED detection to status-aware branching that handles CANCELLED, SCHEDULED/PENDING, and no-event cases with four new alert types
- Added resolveRootAnchor method: referralReceivedAt is primary anchor for root steps (D-03), diagnosisDate is fallback for patients without referral tracking
- CANCELLED_EVENT fires immediately with continue to prevent Pitfall 7 (parallel CANCELLED + DELAYED alerts)
- SCHEDULED/PENDING events suppress MISSING_EVENT and instead check: DEADLINE_APPROACHING (48h window), DELAYED_EVENT (expectedCompletionDate past-due), SCHEDULING_UNCONFIRMED (7-day clock from referral or eventDate)
- SCHEDULING_UNCONFIRMED includes externalFacilityName in description with "the outside facility" fallback
- RESULTS_NOT_READY cross-event check runs once per patient after per-step loop: filters upcoming visits (CONSULTATION/FOLLOW_UP within 14 days) against pending results (PATHOLOGY_REPORT/LAB_WORK/IMAGING) using sentinel dedup key
- Referral detection hook in DocumentProcessingService auto-sets referralReceivedAt when REFERRAL_LETTER classified and patient linked, with null guard for first-referral-only semantics
- Zero PHI in any log statement across both files (patient UUID only)
- Project compiles cleanly with `./mvnw compile` -- zero errors

## Task Commits

Each task was committed atomically:

1. **Task 1: PathwayEvaluationActivityImpl Status-Aware Rewrite** - `cfba8db` (feat)
2. **Task 2: DocumentProcessingService Referral Detection Hook** - `cf0edef` (feat)

## Files Modified

- `src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java` -- Status-aware evaluation with resolveRootAnchor, allEventsByType map, CANCELLED/SCHEDULED/PENDING/NONE branching, RESULTS_NOT_READY cross-event check
- `src/main/java/com/onconavigator/service/DocumentProcessingService.java` -- Referral detection hook after document classification/save, OffsetDateTime import added

## Decisions Made

- CANCELLED branch is mutually exclusive with DELAYED_EVENT via explicit `continue` after CANCELLED_EVENT alert creation (Pitfall 7 prevention). This ensures a cancelled event never also triggers a delayed alert in the same evaluation cycle.
- SCHEDULING_UNCONFIRMED applies to both SCHEDULED and PENDING events without the scheduling_confirmed flag, per Research assumption A1. Both statuses represent events that need external facility confirmation.
- Patient-level RESULTS_NOT_READY alert uses sentinel step name `__RESULTS_NOT_READY__` (double-underscore prefix that cannot be a real step name) for dedup via the existing existsByPatientIdAndPathwayStepNameAndStatus check. No schema change needed.
- Referral hook variable named `referralPatient` to avoid shadowing the `patient` variable used earlier in the processUpload method scope.

## Deviations from Plan

None -- plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None -- no external service configuration required.

## Next Phase Readiness

- All four new alert types are now operational in the evaluation engine
- The referral detection hook completes the D-02 auto-set pathway from document upload through to patient field update
- Plan 04 (frontend scheduling fields) can reference these backend changes for end-to-end integration

## Self-Check: PASSED

- src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java: FOUND
- src/main/java/com/onconavigator/service/DocumentProcessingService.java: FOUND
- Commit cfba8db (Task 1): verified in git log
- Commit cf0edef (Task 2): verified in git log

---
*Phase: 07-referral-trigger-enhanced-timing-status-aware-evaluation*
*Completed: 2026-05-05*
