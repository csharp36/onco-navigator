---
phase: 02-pathway-engine
plan: 03
subsystem: activity
tags: [temporal, activity, deviation-detection, hipaa, spring-component]

# Dependency graph
requires:
  - phase: 02-pathway-engine plan 01
    provides: PathwayTemplateRepository, PhysicianOverrideRepository, PathwayStep record, AnchorType enum
  - phase: 02-pathway-engine plan 02
    provides: PathwayEvaluationActivity interface, AlertGenerationActivity interface, SweepActivity interface, PathwayEvaluationResult DTO, TemporalConfig constants

provides:
  - PathwayEvaluationActivityImpl: @Component implementing deviation detection (MISSING_EVENT, DELAYED_EVENT, OUT_OF_ORDER) with physician override suppression and alert dedup
  - AlertGenerationActivityImpl: @Component providing standalone alert creation with dedup check
  - SweepActivityImpl: @Component using try-to-start-with-reject pattern for idempotent missing workflow startup
  - application-local.yml workers section: task-queue onco-pathway-queue, name onco-pathway-worker

affects:
  - 02-04 (integration tests exercise all three activity implementations against real DB via Testcontainers)
  - 03-xx (REST controllers trigger PathwayService which signals workflows; workflows call these activities)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Activity implementation pattern: @Component on concrete class, constructor injection of all JPA repositories, no Spring annotations on workflow impls"
    - "Deviation detection order: override check -> completion check -> OUT_OF_ORDER -> MISSING_EVENT/DELAYED_EVENT; each has its own dedup guard"
    - "AnchorType switch expression for anchor date resolution: DIAGNOSIS_DATE (patient.getDiagnosisDate()), PREVIOUS_STEP (stepNumber - 1), SPECIFIC_STEP (step.anchorStepId())"
    - "try-to-start-with-reject: WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE + catch WorkflowExecutionAlreadyStarted for idempotent sweep"

key-files:
  created:
    - src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java
    - src/main/java/com/onconavigator/activity/AlertGenerationActivityImpl.java
    - src/main/java/com/onconavigator/activity/SweepActivityImpl.java
  modified:
    - src/main/resources/application-local.yml

key-decisions:
  - "PathwayEvaluationActivityImpl creates alerts directly (not via AlertGenerationActivity) — cleaner, avoids activity-calling-activity, single-pass evaluation and creation; AlertGenerationActivityImpl remains as a standalone activity for future call paths"
  - "allStepsComplete requires ALL steps (required and optional) to have COMPLETED care events — conservative definition; required steps drive alerts, but natural completion (D-09) requires all steps done"
  - "OUT_OF_ORDER check does not prevent MISSING_EVENT/DELAYED_EVENT from also being evaluated on the same step — a step can be both out-of-order and delayed; the more complete alert set is more useful to nurse navigators"
  - "SweepActivityImpl injects WorkflowClient directly (not PathwayService) — needs REJECT_DUPLICATE policy vs PathwayService's ALLOW_DUPLICATE; avoids circular dependency"

patterns-established:
  - "Activity implementations: @Component on impl class, no Spring annotations elsewhere in the Temporal layer"
  - "PHI boundary enforced: only patient UUIDs, step IDs, step names, and alert counts appear in log statements"

requirements-completed: [PATH-03, PATH-04, PATH-05, PATH-06, PATH-07, PATH-08]

# Metrics
duration: 10min
completed: 2026-04-30
---

# Phase 02 Plan 03: Activity Implementations Summary

**Three Temporal activity beans implementing pathway deviation detection (MISSING_EVENT, DELAYED_EVENT, OUT_OF_ORDER) with physician override suppression, alert deduplication, structured evaluation logging, and idempotent daily sweep — zero PHI in any log or Temporal payload**

## Performance

- **Duration:** ~10 min
- **Started:** 2026-04-30T14:35:00Z
- **Completed:** 2026-04-30T14:45:00Z
- **Tasks:** 2 of 2
- **Files created:** 3, modified: 1

## Accomplishments

- PathwayEvaluationActivityImpl is the core deviation detection engine: evaluates all pathway steps for a patient in a single pass, checking for MISSING_EVENT (PATH-03), DELAYED_EVENT (PATH-04), and OUT_OF_ORDER (PATH-05) deviations with correct anchor date logic for all three AnchorType values (DIAGNOSIS_DATE, PREVIOUS_STEP, SPECIFIC_STEP)
- Physician override suppression (PATH-08) implemented first in the step loop — any step with a PhysicianOverride record is skipped entirely before any alert logic runs
- Alert deduplication (PATH-06) applied before each alert creation via `existsByPatientIdAndPathwayStepNameAndStatus` — idempotent under Temporal retries; 3 dedup checks in PathwayEvaluationActivityImpl (one per deviation type)
- Structured PATHWAY_EVALUATION log (PATH-07) with patientId, stepsEvaluated, alertsGenerated, and allComplete flag after every evaluation
- closeOpenAlerts resolves all OPEN alerts to RESOLVED with resolutionNotes when a patient is deactivated (D-08)
- AlertGenerationActivityImpl provides a standalone alert creation path with the same dedup logic
- SweepActivityImpl uses the try-to-start-with-reject pattern: WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE + catch WorkflowExecutionAlreadyStarted — idempotent, correct, and avoids race conditions
- application-local.yml updated with workers section (task-queue: onco-pathway-queue, name: onco-pathway-worker) for Temporal worker binding
- All files compile with `./mvnw compile` BUILD SUCCESS; zero PHI in log statements

## Task Commits

1. **Task 1: PathwayEvaluationActivityImpl and AlertGenerationActivityImpl** - `d6235e3` (feat)
2. **Task 2: SweepActivityImpl and YAML worker configuration** - `c36abb0` (feat)

**Plan metadata:** _(docs commit hash recorded below after state updates)_

## Files Created/Modified

- `src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java` — Core deviation detection: MISSING_EVENT/DELAYED_EVENT/DELAYED_EVENT with anchor date resolution, OUT_OF_ORDER prerequisite check, override suppression, alert dedup, PATHWAY_EVALUATION log
- `src/main/java/com/onconavigator/activity/AlertGenerationActivityImpl.java` — Standalone alert creation with dedup check; AlertType.valueOf(alertTypeStr) conversion from Temporal-serialized string
- `src/main/java/com/onconavigator/activity/SweepActivityImpl.java` — Daily safety-net sweep with REJECT_DUPLICATE + WorkflowExecutionAlreadyStarted catch; DAILY_SWEEP log with started/skipped counts
- `src/main/resources/application-local.yml` — Added `spring.temporal.workers` section: task-queue onco-pathway-queue, name onco-pathway-worker

## Decisions Made

- PathwayEvaluationActivityImpl creates alerts directly (using AlertRepository) rather than calling AlertGenerationActivity. This avoids the complexity of activity-calling-activity, performs deviation detection and alert creation in a single pass, and keeps AlertGenerationActivityImpl as a standalone activity available for future independent call paths.
- `allStepsComplete` requires ALL pathway steps (required and optional) to have COMPLETED care events. Required steps drive alert generation; the D-09 natural completion condition is more conservative — every step must be done before the workflow exits.
- OUT_OF_ORDER check does not short-circuit the MISSING_EVENT/DELAYED_EVENT check on the same step. A step scheduled out-of-order may also be delayed; both deviations are reported so the nurse navigator has complete information.
- SweepActivityImpl injects WorkflowClient directly (not PathwayService) to use REJECT_DUPLICATE policy (PathwayService uses ALLOW_DUPLICATE for re-enrollment). This also avoids a potential Spring circular dependency (PathwayService → SweepActivity → PathwayService).

## Deviations from Plan

None - plan executed exactly as written. The plan already identified the recommended implementation approach (direct AlertRepository usage in PathwayEvaluationActivityImpl, try-to-start-with-reject in SweepActivityImpl) and both were implemented as specified.

## Issues Encountered

None.

## User Setup Required

None — no external service configuration required. All three activity beans will be discovered automatically by the Temporal Spring Boot starter via the `workers-auto-discovery.packages` setting already in application-local.yml. The worker task queue binding is now explicit via the `workers` section added in Task 2.

## Known Stubs

None. All three activity implementations are fully wired — no placeholder data, hardcoded empty values, or TODO stubs that affect the plan's goal. The pathway engine is complete and ready for integration testing (Plan 02-04).

## Threat Flags

No new security-relevant surface detected beyond what the threat model covers. All activity implementations stay within the trust boundaries defined in the plan's threat model:
- Activity → PostgreSQL: read-only patient/template queries, alert writes — no new endpoints
- Activity → AlertRepository: alert creation writes non-PHI clinical text — covered by T-02-11
- SweepActivity → WorkflowClient: UUID-only workflow starts — covered by T-02-12

## Self-Check: PASSED

All 4 files (3 created, 1 modified) confirmed present on disk. Both task commits (d6235e3, c36abb0) confirmed in git log. `./mvnw compile` BUILD SUCCESS confirmed. Zero PHI in log statements confirmed (grep count = 0).

---
*Phase: 02-pathway-engine*
*Completed: 2026-04-30*
