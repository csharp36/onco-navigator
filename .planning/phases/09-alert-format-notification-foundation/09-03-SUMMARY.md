---
phase: 09-alert-format-notification-foundation
plan: 03
subsystem: workflow, activity, config
tags: [temporal-schedule, digest-dispatch, notification-queue, temporal-schedules-api]

# Dependency graph
requires:
  - phase: 09-01
    provides: "NotificationPendingQueue entity, NotificationPendingQueueRepository, NotificationChannel enum"
  - phase: 09-02
    provides: "NotificationService interface with dispatchFromQueue method, LoggingNotificationService implementation"
provides:
  - "DigestDispatchWorkflow Temporal workflow interface + implementation"
  - "DigestDispatchActivity interface + implementation draining notification_pending_queue"
  - "DigestScheduleRegistrar startup bean for idempotent 30-minute schedule registration"
  - "TemporalConfig.DIGEST_SCHEDULE_ID constant"
  - "digestDispatchActivityImpl registered in worker activity-beans"
affects: [09-04]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Temporal Schedules API (ScheduleClient.createSchedule) for periodic workflow execution"
    - "ScheduleAlreadyRunningException catch for idempotent schedule registration on restart"
    - "ScheduleOverlapPolicy.SKIP to prevent concurrent digest sweep executions"

key-files:
  created:
    - "src/main/java/com/onconavigator/workflow/DigestDispatchWorkflow.java"
    - "src/main/java/com/onconavigator/workflow/DigestDispatchWorkflowImpl.java"
    - "src/main/java/com/onconavigator/activity/DigestDispatchActivity.java"
    - "src/main/java/com/onconavigator/activity/DigestDispatchActivityImpl.java"
    - "src/main/java/com/onconavigator/config/DigestScheduleRegistrar.java"
  modified:
    - "src/main/java/com/onconavigator/config/TemporalConfig.java"
    - "src/main/resources/application-local.yml"

key-decisions:
  - "ScheduleIntervalSpec uses constructor (not builder) per verified Temporal SDK 1.32.0 API"
  - "ScheduleOverlapPolicy imported from io.temporal.api.enums.v1 (protobuf-generated, not io.temporal.client.schedules)"
  - "DigestDispatchActivityImpl delegates to NotificationService interface (not LoggingNotificationService directly) for implementation flexibility"

patterns-established:
  - "Temporal Schedules API registration pattern with ApplicationRunner + idempotent ScheduleAlreadyRunningException catch"
  - "Activity draining a pending queue table with grouping by hold type and user"

requirements-completed: [PW-ALL-004]

# Metrics
duration: 6min
completed: 2026-05-06
---

# Phase 9 Plan 03: Temporal Digest Dispatch Workflow Summary

**Temporal scheduled workflow draining notification_pending_queue every 30 minutes with idempotent startup registration via Schedules API**

## Performance

- **Duration:** 6 min
- **Started:** 2026-05-06T00:12:47Z
- **Completed:** 2026-05-06T00:19:06Z
- **Tasks:** 2
- **Files modified:** 7

## Accomplishments
- DigestDispatchWorkflow and Activity following the exact DailySweepWorkflow pattern: minimal workflow delegates to activity, activity handles all DB access and business logic
- DigestDispatchActivityImpl drains notification_pending_queue, separates QUIET_HOURS (dispatch individually) from DIGEST (group by user), delegates all dispatch to NotificationService.dispatchFromQueue for consistent notification_log persistence
- DigestScheduleRegistrar registers the 30-minute Temporal Schedule on application startup using the Schedules API (ScheduleClient.createSchedule) with idempotent ScheduleAlreadyRunningException handling and SKIP overlap policy

## Task Commits

Each task was committed atomically:

1. **Task 1: DigestDispatchWorkflow and Activity (interface + implementation)** - `c4b8d63` (feat)
2. **Task 2: DigestScheduleRegistrar + TemporalConfig constant + activity-beans update** - `58573f1` (feat)

## Files Created/Modified

### Created
- `src/main/java/com/onconavigator/workflow/DigestDispatchWorkflow.java` - @WorkflowInterface for digest dispatch with runDigestPass() method
- `src/main/java/com/onconavigator/workflow/DigestDispatchWorkflowImpl.java` - @WorkflowImpl delegating to DigestDispatchActivity with 5-min timeout and 3 retry attempts
- `src/main/java/com/onconavigator/activity/DigestDispatchActivity.java` - @ActivityInterface with drainPendingQueue() method
- `src/main/java/com/onconavigator/activity/DigestDispatchActivityImpl.java` - @Component draining pending queue, grouping by hold type and user, delegating to NotificationService.dispatchFromQueue
- `src/main/java/com/onconavigator/config/DigestScheduleRegistrar.java` - ApplicationRunner registering 30-min Temporal Schedule idempotently on startup

### Modified
- `src/main/java/com/onconavigator/config/TemporalConfig.java` - Added DIGEST_SCHEDULE_ID constant
- `src/main/resources/application-local.yml` - Added digestDispatchActivityImpl to activity-beans list

## Decisions Made
- ScheduleIntervalSpec uses constructor `new ScheduleIntervalSpec(Duration.ofMinutes(30))` rather than builder pattern, as verified against the actual Temporal SDK 1.32.0 API
- ScheduleOverlapPolicy imported from `io.temporal.api.enums.v1` (protobuf-generated enum), not from `io.temporal.client.schedules` as the plan's pseudo-code suggested
- DigestDispatchActivityImpl injects NotificationService interface (not the concrete LoggingNotificationService) for clean abstraction and future implementation swapping

## Deviations from Plan

None - plan executed exactly as written. The only adjustments were import paths and constructor patterns per the actual Temporal SDK 1.32.0 API (ScheduleIntervalSpec constructor vs builder, ScheduleOverlapPolicy package location), which the plan explicitly anticipated: "If these exact imports don't resolve at compile time, check the Temporal SDK 1.32.0 API."

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Digest dispatch infrastructure complete: Temporal Schedule registers on startup, workflow runs every 30 minutes, activity drains pending queue and dispatches via NotificationService
- Ready for Plan 04 (integration testing / final verification)
- All notification dispatch paths are now covered: immediate dispatch via LoggingNotificationService.dispatchForAlert, and deferred dispatch via DigestDispatchActivityImpl draining the pending queue

## Self-Check: PASSED

All 5 created files verified on disk. Both commit hashes (c4b8d63, 58573f1) verified in git log.

---
*Phase: 09-alert-format-notification-foundation*
*Completed: 2026-05-06*
