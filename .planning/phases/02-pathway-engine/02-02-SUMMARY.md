---
phase: 02-pathway-engine
plan: 02
subsystem: workflow
tags: [temporal, workflow, activity, spring-service, durable-execution]

# Dependency graph
requires:
  - phase: 02-pathway-engine plan 01
    provides: PhysicianOverrideRepository, PathwayTemplateRepository, PathwayStep record, AnchorType enum (used by activity implementations in plan 03)
  - phase: 01-hipaa-foundation
    provides: CancerType enum, AlertType enum, AlertStatus enum, Patient entity, AuditService pattern

provides:
  - PatientPathwayWorkflow: @WorkflowInterface with monitorPathway, careEventChanged, deactivatePatient, getPathwayStatus (UUID-only params, no PHI)
  - PatientPathwayWorkflowImpl: signal+timer dual approach (D-05), deactivation (D-08), natural completion (D-09)
  - DailySweepWorkflow: @WorkflowInterface for daily cron safety-net sweep
  - DailySweepWorkflowImpl: minimal cron workflow delegating to SweepActivity
  - PathwayEvaluationActivity: @ActivityInterface with evaluate and closeOpenAlerts methods
  - AlertGenerationActivity: @ActivityInterface with deduplication-aware generateAlert method
  - SweepActivity: @ActivityInterface with findAndStartMissingWorkflows
  - PathwayEvaluationResult: DTO record (allStepsComplete, alertsGenerated)
  - CareEventSignal: DTO record carrying UUID only
  - TemporalConfig: task queue name, workflow ID prefix, sweep workflow ID, cron schedule constants
  - PathwayService: @Service with startPathwayMonitoring, signalCareEventChanged, deactivatePatient, startDailySweep

affects:
  - 02-03 (activity implementations implement PathwayEvaluationActivity, AlertGenerationActivity, SweepActivity interfaces defined here)
  - 02-04 (integration tests exercise PathwayService and workflow lifecycle)
  - 03-xx (REST controllers call PathwayService.startPathwayMonitoring, signalCareEventChanged, deactivatePatient)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Temporal workflow interface: @WorkflowInterface with UUID-only method parameters for PHI safety — clinical data stays in encrypted DB, never enters Temporal event history"
    - "Dual approach pattern (D-05): Workflow.await(24h, condition) wakes on signal OR timer expiry, whichever comes first"
    - "Activity stub creation in workflow initializer field (not in method body) to satisfy Temporal's determinism checker"
    - "Temporal RetryOptions with DoNotRetry for IllegalArgumentException — programming errors are not retried"
    - "WorkflowIdReusePolicy.ALLOW_DUPLICATE for patient re-enrollment after deactivation (D-08)"
    - "alertTypeStr as String parameter (not enum) for Temporal serialization robustness across schema evolution"

key-files:
  created:
    - src/main/java/com/onconavigator/config/TemporalConfig.java
    - src/main/java/com/onconavigator/workflow/PatientPathwayWorkflow.java
    - src/main/java/com/onconavigator/workflow/PatientPathwayWorkflowImpl.java
    - src/main/java/com/onconavigator/workflow/DailySweepWorkflow.java
    - src/main/java/com/onconavigator/workflow/DailySweepWorkflowImpl.java
    - src/main/java/com/onconavigator/activity/PathwayEvaluationActivity.java
    - src/main/java/com/onconavigator/activity/AlertGenerationActivity.java
    - src/main/java/com/onconavigator/activity/SweepActivity.java
    - src/main/java/com/onconavigator/domain/dto/PathwayEvaluationResult.java
    - src/main/java/com/onconavigator/domain/dto/CareEventSignal.java
    - src/main/java/com/onconavigator/service/PathwayService.java
  modified: []

key-decisions:
  - "alertTypeStr passed as String to AlertGenerationActivity.generateAlert (not AlertType enum) — Temporal serializes activity params; String enum name is more robust to schema evolution. Implementation calls AlertType.valueOf(alertTypeStr)."
  - "Activity stubs declared as instance fields (not inside monitorPathway) — Temporal's determinism checker requires stubs be created consistently, not conditionally inside methods"
  - "PathwayService uses WorkflowClient.start (async) not workflow.monitorPathway() (sync) — sync would block the calling thread until workflow completes (weeks), which is never correct for pathway workflows"
  - "deactivatePatient reason parameter not stored in workflow state — reason is not needed for cleanup and storing it in workflow fields creates a PHI risk if coded values are ever accidentally replaced with free text"

patterns-established:
  - "Workflow implementations: no Spring annotations, no DB access, no non-deterministic APIs. Temporal's context only."
  - "PHI boundary: UUID crosses Spring→Temporal boundary; PHI stays in encrypted DB, fetched by Spring-managed activity beans"
  - "PathwayService is the sole Spring-to-Temporal gateway for pathway workflow lifecycle"

requirements-completed: [INFR-03, INFR-04, PATH-03, PATH-04, PATH-05, PATH-06]

# Metrics
duration: 6min
completed: 2026-04-30
---

# Phase 02 Plan 02: Temporal Workflow Layer Summary

**Temporal per-patient workflow with 24h timer + signal dual approach, three activity interfaces (evaluation, alert generation, sweep), and PathwayService as the Spring-to-Temporal gateway — zero PHI in any Temporal payload**

## Performance

- **Duration:** ~6 min
- **Started:** 2026-04-30T14:26:47Z
- **Completed:** 2026-04-30T14:32:57Z
- **Tasks:** 2 of 2
- **Files created:** 11, modified: 0

## Accomplishments

- PatientPathwayWorkflowImpl implements the signal+timer dual approach (D-05): `Workflow.await(Duration.ofHours(24), () -> signalReceived || deactivated)` wakes early on care event signals, ensuring timely deviation detection without polling overhead
- Workflow handles deactivation cleanly (D-08): closes OPEN alerts via `evaluationActivity.closeOpenAlerts(patientId)` before terminating; and natural completion (D-09): exits loop when `result.allStepsComplete()` returns true
- Three activity interfaces (`PathwayEvaluationActivity`, `AlertGenerationActivity`, `SweepActivity`) define clear contracts for Plan 03 implementations — all use UUID-only parameters, no PHI crosses into Temporal
- PathwayService provides the sole Spring-to-Temporal gateway with `WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE` for patient re-enrollment support and UUID-only log statements
- All 11 files compile with `./mvnw compile` BUILD SUCCESS, zero non-deterministic code in workflow implementations

## Task Commits

1. **Task 1: Temporal interfaces, DTOs, and config constants** - `4ba0466` (feat)
2. **Task 2: Workflow implementations and PathwayService** - `ae9b776` (feat)

**Plan metadata:** _(docs commit hash recorded below after state updates)_

## Files Created/Modified

- `src/main/java/com/onconavigator/config/TemporalConfig.java` — Task queue "onco-pathway-queue", workflow ID prefix "pathway-", sweep ID, daily 6AM cron schedule constants
- `src/main/java/com/onconavigator/workflow/PatientPathwayWorkflow.java` — @WorkflowInterface: monitorPathway(UUID, String), careEventChanged(UUID), deactivatePatient(String), getPathwayStatus() — UUID-only params
- `src/main/java/com/onconavigator/workflow/PatientPathwayWorkflowImpl.java` — Signal+timer loop with Workflow.await(24h), deactivation branch with alert cleanup, natural completion check
- `src/main/java/com/onconavigator/workflow/DailySweepWorkflow.java` — @WorkflowInterface for cron sweep
- `src/main/java/com/onconavigator/workflow/DailySweepWorkflowImpl.java` — Minimal cron workflow calling sweepActivity.findAndStartMissingWorkflows()
- `src/main/java/com/onconavigator/activity/PathwayEvaluationActivity.java` — @ActivityInterface: evaluate(UUID) returns PathwayEvaluationResult, closeOpenAlerts(UUID) for deactivation
- `src/main/java/com/onconavigator/activity/AlertGenerationActivity.java` — @ActivityInterface: generateAlert with deduplication contract, alertTypeStr as String for serialization robustness
- `src/main/java/com/onconavigator/activity/SweepActivity.java` — @ActivityInterface: findAndStartMissingWorkflows() with 5-min timeout for large patient sets
- `src/main/java/com/onconavigator/domain/dto/PathwayEvaluationResult.java` — record(allStepsComplete, alertsGenerated), no PHI
- `src/main/java/com/onconavigator/domain/dto/CareEventSignal.java` — record(careEventId UUID), SEC-06 compliant
- `src/main/java/com/onconavigator/service/PathwayService.java` — @Service with WorkflowClient injection: startPathwayMonitoring (ALLOW_DUPLICATE), signalCareEventChanged, deactivatePatient, startDailySweep

## Decisions Made

- `alertTypeStr` passed as `String` (not `AlertType` enum) to `AlertGenerationActivity.generateAlert` — Temporal serializes activity parameters via Jackson; using the enum name string avoids issues if the enum class is renamed or moved. Implementation converts with `AlertType.valueOf(alertTypeStr)`.
- Activity stubs declared as instance fields rather than inside `monitorPathway()` — Temporal's determinism checker verifies that activity stub creation is consistent across replay; declaring in the method body would work but fields are the conventional pattern in the Temporal Java SDK.
- `PathwayService.startPathwayMonitoring` uses `WorkflowClient.start()` (async) rather than calling the workflow method directly (which would block until the workflow completes, potentially weeks later).
- `deactivatePatient` reason parameter is not stored in workflow fields — it's only passed to the signal and not needed for cleanup operations. Storing it risks PHI leakage if the coded reason convention is ever violated.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required. Temporal worker auto-discovery is already configured in application-local.yml. The activity implementations (Plan 02-03) are required before Temporal can actually execute these workflows.

## Next Phase Readiness

- All three activity interfaces (`PathwayEvaluationActivity`, `AlertGenerationActivity`, `SweepActivity`) are ready for implementation in Plan 02-03
- `PathwayService` is ready for use by Plan 02-03 REST controllers (Phase 3)
- The `workflow` and `activity` packages are registered in `application-local.yml` for Temporal worker auto-discovery — new `@WorkflowImpl` and `@ActivityImpl` beans will be discovered automatically
- All temporal constants are centralized in `TemporalConfig` — no magic strings in workflow or service code

## Self-Check: PASSED

All 11 files confirmed present on disk. Both task commits (4ba0466, ae9b776) confirmed in git log.

---
*Phase: 02-pathway-engine*
*Completed: 2026-04-30*
