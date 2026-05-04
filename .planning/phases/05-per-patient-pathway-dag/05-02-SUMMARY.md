---
phase: 05-per-patient-pathway-dag
plan: 02
subsystem: workflow
tags: [temporal, java, signal, workflow, pathway]

# Dependency graph
requires:
  - phase: 02-pathway-engine
    provides: PatientPathwayWorkflow interface and PathwayService with Temporal WorkflowClient wiring

provides:
  - pathwayStepsChanged @SignalMethod on PatientPathwayWorkflow interface
  - pathwayStepsChanged signal handler in PatientPathwayWorkflowImpl (sets signalReceived = true)
  - PathwayService.signalPathwayStepsChanged(UUID patientId) public API
affects:
  - 05-per-patient-pathway-dag (plans 03–06 that call signalPathwayStepsChanged after step/edge mutations)
  - Phase 6 AI extraction (will call signalPathwayStepsChanged after auto-extracted steps are persisted)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Temporal signal handler uses shared boolean flag (signalReceived) — no per-signal state required, all signals wake the same evaluation loop"
    - "Additive @SignalMethod declarations are replay-safe — Temporal ignores unrecognized signals on older workflow versions"

key-files:
  created: []
  modified:
    - src/main/java/com/onconavigator/workflow/PatientPathwayWorkflow.java
    - src/main/java/com/onconavigator/workflow/PatientPathwayWorkflowImpl.java
    - src/main/java/com/onconavigator/service/PathwayService.java

key-decisions:
  - "pathwayStepsChanged signal carries zero parameters — evaluation activity queries DB directly; no PHI enters Temporal event history (T-05-04 mitigated)"
  - "New signal reuses signalReceived boolean (same as careEventChanged) — main workflow loop is unchanged, no replay determinism concerns"

patterns-established:
  - "All pathway-mutating operations (step add/edit/remove, edge add/remove, template fork) MUST call signalPathwayStepsChanged after persisting the change"

requirements-completed: [PW-BR-001]

# Metrics
duration: 2min
completed: 2026-05-04
---

# Phase 5 Plan 02: pathwayStepsChanged Temporal Signal Summary

**pathwayStepsChanged @SignalMethod added to workflow interface and implementation, plus PathwayService.signalPathwayStepsChanged, enabling immediate re-evaluation after step/edge mutations**

## Performance

- **Duration:** 2 min
- **Started:** 2026-05-04T17:51:08Z
- **Completed:** 2026-05-04T17:53:35Z
- **Tasks:** 2
- **Files modified:** 3

## Accomplishments

- PatientPathwayWorkflow interface now declares 3 `@SignalMethod` methods: careEventChanged, deactivatePatient, pathwayStepsChanged
- PatientPathwayWorkflowImpl handles pathwayStepsChanged by setting `signalReceived = true` — same boolean flag, main loop unchanged
- PathwayService.signalPathwayStepsChanged(UUID patientId) follows the exact same pattern as signalCareEventChanged, with UUID-only logging per SEC-06

## Task Commits

Each task was committed atomically:

1. **Task 1: Add pathwayStepsChanged Signal to Workflow Interface and Implementation** - `bca5d7a` (feat)
2. **Task 2: Add signalPathwayStepsChanged Method to PathwayService** - `3b3f3fe` (feat)

**Plan metadata:** see final commit below

## Files Created/Modified

- `src/main/java/com/onconavigator/workflow/PatientPathwayWorkflow.java` - Added pathwayStepsChanged @SignalMethod declaration with Javadoc (replay safety, PHI-zero parameters)
- `src/main/java/com/onconavigator/workflow/PatientPathwayWorkflowImpl.java` - Added pathwayStepsChanged() handler setting signalReceived = true; monitorPathway body unchanged
- `src/main/java/com/onconavigator/service/PathwayService.java` - Added signalPathwayStepsChanged(UUID patientId) following careEventChanged pattern

## Decisions Made

- pathwayStepsChanged carries zero parameters — consistent with T-05-04 threat mitigation; evaluation activity reads DB state directly
- Reused signalReceived boolean (not a new flag) — the workflow loop already handles "something changed" generically; no loop logic changes needed

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None - Maven compiled cleanly on the first attempt.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Signal infrastructure is complete; plans 03-06 can call PathwayService.signalPathwayStepsChanged after any step/edge CRUD operation
- Phase 6 AI extraction will call signalPathwayStepsChanged after auto-extracted steps are persisted to trigger immediate re-evaluation

## Threat Surface Scan

No new network endpoints, auth paths, file access patterns, or schema changes introduced. The signal carries zero parameters — no PHI enters Temporal event history. T-05-04 mitigated as planned.

## Self-Check: PASSED

- `bca5d7a` — feat(05-02): add pathwayStepsChanged signal to workflow interface and implementation — confirmed in git log
- `3b3f3fe` — feat(05-02): add signalPathwayStepsChanged method to PathwayService — confirmed in git log
- PatientPathwayWorkflow.java contains `void pathwayStepsChanged()` — confirmed
- PatientPathwayWorkflowImpl.java contains `signalReceived = true` in pathwayStepsChanged handler — confirmed
- PathwayService.java contains `signalPathwayStepsChanged` — confirmed
- Project compiles without errors — confirmed

---
*Phase: 05-per-patient-pathway-dag*
*Completed: 2026-05-04*
