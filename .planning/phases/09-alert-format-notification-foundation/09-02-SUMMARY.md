---
phase: 09-alert-format-notification-foundation
plan: 02
subsystem: notification, activity, ai, web
tags: [notification-service, logging-notification, preference-routing, cap150, missing-summary, claude-prompt]

# Dependency graph
requires:
  - phase: 09-01
    provides: "V21-V23 Flyway migrations, JPA entities, AlertText 3-component record, Alert.missingSummary field"
provides:
  - "NotificationService interface with dispatchForAlert and dispatchFromQueue"
  - "LoggingNotificationService with full routing: severity filter, quiet hours, digest queue"
  - "NotificationPreferenceService with admin-default + user-override merge logic"
  - "NotificationPayload record with human-readable render()"
  - "NotificationPreferenceController REST API (GET/PUT for user + admin)"
  - "AlertResponse DTO and frontend type with missingSummary field"
  - "Claude MISSING_SUMMARY prompt parsing in AlertGenerationAiService"
  - "cap150 enforcement on suggestedAction and missingSummary at both activity layers"
  - "Notification dispatch hooks on all alert creation paths"
affects: [09-03, 09-04]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "NotificationService interface with log-only implementation pattern"
    - "cap150() service-layer truncation with warning log"
    - "Three-section Claude prompt (DESCRIPTION, SUGGESTED_ACTION, MISSING_SUMMARY)"
    - "Admin-default + user-override preference merge via NotificationPreferenceService"
    - "PHI-safe notification dispatch: patientName/patientMrn as method params, never logged"

key-files:
  created:
    - "src/main/java/com/onconavigator/notification/NotificationService.java"
    - "src/main/java/com/onconavigator/notification/NotificationPayload.java"
    - "src/main/java/com/onconavigator/notification/NotificationPreferenceService.java"
    - "src/main/java/com/onconavigator/notification/LoggingNotificationService.java"
    - "src/main/java/com/onconavigator/web/NotificationPreferenceController.java"
  modified:
    - "src/main/java/com/onconavigator/web/dto/AlertResponse.java"
    - "src/main/java/com/onconavigator/service/AlertService.java"
    - "frontend/src/features/alerts/types.ts"
    - "src/main/java/com/onconavigator/ai/prompt/AlertPrompts.java"
    - "src/main/java/com/onconavigator/ai/service/AlertGenerationAiService.java"
    - "src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java"
    - "src/main/java/com/onconavigator/activity/AlertGenerationActivityImpl.java"
    - "src/main/java/com/onconavigator/activity/AlertGenerationActivity.java"
    - "src/test/java/com/onconavigator/activity/AlertGenerationActivityTest.java"
    - "src/test/java/com/onconavigator/activity/PathwayEvaluationActivityImplTest.java"
    - "src/test/java/com/onconavigator/activity/PathwayEvaluationActivityTest.java"
    - "src/test/java/com/onconavigator/activity/PathwayEvaluationStatusAwareTest.java"

key-decisions:
  - "buildAlertDescription() removed from PathwayEvaluationActivityImpl -- alert text logic moved inline into createAlertIfNotDuplicate() to capture full AlertText (all 3 fields)"
  - "RESULTS_NOT_READY alert path also updated with missingSummary and notification dispatch for consistency"
  - "NotificationPreferenceController uses JwtAuthenticationToken (not @AuthenticationPrincipal Jwt) -- both work, plan specified this pattern"

patterns-established:
  - "Every alert creation path calls notificationService.dispatchForAlert() after alertRepository.save()"
  - "cap150() truncation with warning log applied at both PathwayEvaluationActivityImpl and AlertGenerationActivityImpl"
  - "Claude prompt template with 3 structured output sections parsed by indexOf-based parser"

requirements-completed: [PW-ALL-007, PW-ALL-004]

# Metrics
duration: 10min
completed: 2026-05-06
---

# Phase 9 Plan 02: Notification Dispatch Pipeline and Alert Format Enforcement Summary

**Full notification routing pipeline wired to all alert creation paths with Claude MISSING_SUMMARY parsing, 150-char enforcement, and preference-based severity filtering, quiet-hours hold, and digest queue**

## Performance

- **Duration:** 10 min
- **Started:** 2026-05-05T23:57:47Z
- **Completed:** 2026-05-06T00:08:35Z
- **Tasks:** 2
- **Files modified:** 17

## Accomplishments

- NotificationService interface with dispatchForAlert and dispatchFromQueue methods; LoggingNotificationService implementation with complete routing pipeline: load preferences per channel, severity filter check, quiet-hours hold to notification_pending_queue, digest queue, immediate log-only dispatch with notification_log persistence
- NotificationPreferenceService with admin-default-to-user-override merge, severity filtering, quiet-hours detection with timezone support, and quiet-hours end computation
- NotificationPayload record with human-readable render() method for log-only and future channel output
- NotificationPreferenceController REST API: GET/PUT for user preferences, GET/PUT /defaults for admin (ROLE_ADMIN required), user identity from JWT subject to prevent privilege escalation
- AlertPrompts USER_TEMPLATE extended with MISSING_SUMMARY as third Claude output section; AlertGenerationAiService parser updated for 3-section response with defensive 150-char caps and description-fallback derivation
- PathwayEvaluationActivityImpl fully refactored: buildAlertDescription() replaced with inline 3-path text resolution (template-first, Claude, fallback), cap150 enforcement on both suggestedAction and missingSummary, notification dispatch after every alertRepository.save() including RESULTS_NOT_READY path
- AlertGenerationActivity interface and implementation updated with missingSummary parameter, cap150 enforcement, and PatientRepository for notification dispatch PHI loading

## Task Commits

Each task was committed atomically:

1. **Task 1: NotificationService interface, LoggingNotificationService, NotificationPreferenceService, NotificationPayload, NotificationPreferenceController, AlertResponse DTO + frontend type** - `8d331d4` (feat)
2. **Task 2: AlertPrompts extension, AlertGenerationAiService MISSING_SUMMARY parsing, cap150 enforcement, notification dispatch hooks** - `f9f1a36` (feat)

## Files Created/Modified

### Created
- `src/main/java/com/onconavigator/notification/NotificationService.java` - Interface with dispatchForAlert and dispatchFromQueue
- `src/main/java/com/onconavigator/notification/NotificationPayload.java` - Immutable value object with render() for notification content
- `src/main/java/com/onconavigator/notification/NotificationPreferenceService.java` - Admin-default + user-override merge, severity filter, quiet-hours logic
- `src/main/java/com/onconavigator/notification/LoggingNotificationService.java` - Log-only implementation with full routing pipeline
- `src/main/java/com/onconavigator/web/NotificationPreferenceController.java` - REST API for user and admin preference management

### Modified
- `src/main/java/com/onconavigator/web/dto/AlertResponse.java` - Added missingSummary field
- `src/main/java/com/onconavigator/service/AlertService.java` - toAlertResponse includes missingSummary
- `frontend/src/features/alerts/types.ts` - AlertResponse interface with missingSummary: string | null
- `src/main/java/com/onconavigator/ai/prompt/AlertPrompts.java` - USER_TEMPLATE with 3rd MISSING_SUMMARY section
- `src/main/java/com/onconavigator/ai/service/AlertGenerationAiService.java` - 3-section parser with cap150 and fallback derivation
- `src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java` - NotificationService dep, cap150, inline text resolution, dispatch hooks
- `src/main/java/com/onconavigator/activity/AlertGenerationActivity.java` - missingSummary parameter added to interface
- `src/main/java/com/onconavigator/activity/AlertGenerationActivityImpl.java` - cap150, notification dispatch, PatientRepository dep
- `src/test/java/com/onconavigator/activity/AlertGenerationActivityTest.java` - Updated constructor + 3 call sites for 7-arg signature
- `src/test/java/com/onconavigator/activity/PathwayEvaluationActivityImplTest.java` - Updated constructor for NotificationService mock
- `src/test/java/com/onconavigator/activity/PathwayEvaluationActivityTest.java` - Updated constructor for NotificationService mock
- `src/test/java/com/onconavigator/activity/PathwayEvaluationStatusAwareTest.java` - Updated constructor for NotificationService mock

## Decisions Made

- buildAlertDescription() removed from PathwayEvaluationActivityImpl -- inline 3-path resolution (template, Claude, fallback) in createAlertIfNotDuplicate() captures full AlertText record with all 3 fields
- RESULTS_NOT_READY alert path (patient-level, not step-level) also updated with missingSummary and notification dispatch for consistency with D-06 (every alert creation dispatches)
- NotificationPreferenceController uses JwtAuthenticationToken parameter type (from spring-security-oauth2-resource-server) rather than @AuthenticationPrincipal Jwt, consistent with plan specification

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Updated PathwayEvaluationActivityTest constructor for NotificationService**
- **Found during:** Task 2
- **Issue:** PathwayEvaluationActivityTest.java (not listed in plan's files) constructs PathwayEvaluationActivityImpl with 8 args -- compile fails with new 9-arg constructor
- **Fix:** Added NotificationService mock as 9th constructor parameter
- **Files modified:** src/test/java/com/onconavigator/activity/PathwayEvaluationActivityTest.java
- **Committed in:** f9f1a36 (Task 2 commit)

**2. [Rule 3 - Blocking] Updated PathwayEvaluationActivityImplTest constructor for NotificationService**
- **Found during:** Task 2
- **Issue:** PathwayEvaluationActivityImplTest.java (listed in plan Task 2 read_first but not files) needs updated constructor
- **Fix:** Added NotificationService mock as 9th constructor parameter
- **Files modified:** src/test/java/com/onconavigator/activity/PathwayEvaluationActivityImplTest.java
- **Committed in:** f9f1a36 (Task 2 commit)

**3. [Rule 3 - Blocking] Updated PathwayEvaluationStatusAwareTest constructor for NotificationService**
- **Found during:** Task 2
- **Issue:** PathwayEvaluationStatusAwareTest.java also constructs PathwayEvaluationActivityImpl with 8 args
- **Fix:** Added NotificationService mock as 9th constructor parameter
- **Files modified:** src/test/java/com/onconavigator/activity/PathwayEvaluationStatusAwareTest.java
- **Committed in:** f9f1a36 (Task 2 commit)

**4. [Rule 2 - Missing functionality] RESULTS_NOT_READY alert path notification dispatch**
- **Found during:** Task 2
- **Issue:** The plan only specified notification dispatch for createAlertIfNotDuplicate and AlertGenerationActivityImpl, but the RESULTS_NOT_READY patient-level alert has its own inline creation path that also needs dispatch
- **Fix:** Added missingSummary, cap150 enforcement, and notificationService.dispatchForAlert() to the RESULTS_NOT_READY block
- **Files modified:** src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java
- **Committed in:** f9f1a36 (Task 2 commit)

---

**Total deviations:** 4 auto-fixed (3 blocking constructor issues, 1 missing notification dispatch path)
**Impact on plan:** Essential fixes for compile correctness and D-06 compliance (every alert creation dispatches). No scope creep.

## Issues Encountered
None

## User Setup Required
None

## Next Phase Readiness
- NotificationService interface and LoggingNotificationService ready for Plan 03 (DigestDispatchWorkflow and DigestDispatchActivity)
- All alert creation paths now dispatch notifications through the full routing pipeline
- NotificationPreferenceController provides REST API for testing preference-based routing

## Self-Check: PASSED

All 5 created files verified on disk. Both commit hashes (8d331d4, f9f1a36) verified in git log.

---
*Phase: 09-alert-format-notification-foundation*
*Completed: 2026-05-06*
