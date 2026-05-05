---
phase: 09-alert-format-notification-foundation
plan: 01
subsystem: database, domain
tags: [flyway, jpa, hibernate-envers, postgresql, encryption, notification]

# Dependency graph
requires:
  - phase: 08-template-inheritance
    provides: "Flyway V19-V20 migration baseline, Alert entity with @Audited"
provides:
  - "V21-V23 Flyway migrations for notification infrastructure"
  - "NotificationChannel enum (TEAMS, EMAIL)"
  - "NotificationPreference JPA entity with admin defaults"
  - "NotificationLog JPA entity with @Audited + EncryptionConverter"
  - "NotificationPendingQueue JPA entity for quiet-hours/digest holds"
  - "Three Spring Data repositories for notification entities"
  - "Alert.missingSummary field (three-field alert model)"
  - "AlertText record with 3 components (deviationDescription, suggestedAction, missingSummary)"
affects: [09-02, 09-03, 09-04]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "notification_channel PostgreSQL enum type for channel discrimination"
    - "Partial unique indexes for nullable admin-default rows"
    - "notification_pending_queue as durable hold table for quiet hours and digest"
    - "Three-field alert model: deviationDescription + suggestedAction + missingSummary"

key-files:
  created:
    - "src/main/resources/db/migration/V21__add_alert_missing_summary.sql"
    - "src/main/resources/db/migration/V22__notification_preferences.sql"
    - "src/main/resources/db/migration/V23__notification_log.sql"
    - "src/main/java/com/onconavigator/domain/enums/NotificationChannel.java"
    - "src/main/java/com/onconavigator/domain/NotificationPreference.java"
    - "src/main/java/com/onconavigator/domain/NotificationLog.java"
    - "src/main/java/com/onconavigator/domain/NotificationPendingQueue.java"
    - "src/main/java/com/onconavigator/repository/NotificationPreferenceRepository.java"
    - "src/main/java/com/onconavigator/repository/NotificationLogRepository.java"
    - "src/main/java/com/onconavigator/repository/NotificationPendingQueueRepository.java"
  modified:
    - "src/main/java/com/onconavigator/domain/Alert.java"
    - "src/main/java/com/onconavigator/ai/model/AlertText.java"
    - "src/main/java/com/onconavigator/ai/service/AlertGenerationAiService.java"
    - "src/test/java/com/onconavigator/activity/PathwayEvaluationActivityImplTest.java"

key-decisions:
  - "NotificationPreference entity NOT @Audited (no PHI, preference metadata only)"
  - "NotificationLog entity IS @Audited with EncryptionConverter on rendered_content (PHI)"
  - "AlertText constructor call sites updated with null missingSummary as temporary fix (Plan 02 adds parsing)"

patterns-established:
  - "notification_channel PostgreSQL enum shared across notification_preferences, notification_pending_queue, and notification_log tables"
  - "Partial unique indexes for admin defaults (user_id IS NULL) vs user overrides (user_id IS NOT NULL)"
  - "Three-component AlertText record for structured alert text generation pipeline"

requirements-completed: [PW-ALL-007, PW-ALL-004]

# Metrics
duration: 4min
completed: 2026-05-05
---

# Phase 9 Plan 01: Notification Schema and Alert Entity Extension Summary

**Flyway V21-V23 notification infrastructure schema with JPA entities, three-field alert model (missingSummary), and encrypted notification log for HIPAA-safe dispatch audit**

## Performance

- **Duration:** 4 min
- **Started:** 2026-05-05T23:48:48Z
- **Completed:** 2026-05-05T23:53:29Z
- **Tasks:** 2
- **Files modified:** 14

## Accomplishments
- Three Flyway migrations (V21-V23) establishing notification infrastructure: alert missing_summary column with backfill, notification_preferences table with admin defaults, notification_log with Envers AUD table, and notification_pending_queue for quiet-hours/digest holds
- Four new JPA entities: NotificationPreference (no @Audited), NotificationLog (@Audited + EncryptionConverter), NotificationPendingQueue (EncryptionConverter), NotificationChannel enum
- Alert entity extended with missingSummary field; AlertText record extended to three components; all constructor call sites updated across production and test code

## Task Commits

Each task was committed atomically:

1. **Task 1: Flyway migrations V21-V23 and JPA entities** - `b356f81` (feat)
2. **Task 2: Alert entity extension + AlertText record (3-component)** - `b9ffb74` (feat)

## Files Created/Modified
- `src/main/resources/db/migration/V21__add_alert_missing_summary.sql` - Alert missing_summary column + backfill + suggested_action truncation + AUD mirror
- `src/main/resources/db/migration/V22__notification_preferences.sql` - notification_preferences table + admin defaults seed + notification_pending_queue table
- `src/main/resources/db/migration/V23__notification_log.sql` - notification_log table with encrypted rendered_content + Envers AUD table
- `src/main/java/com/onconavigator/domain/enums/NotificationChannel.java` - TEAMS and EMAIL channel enum
- `src/main/java/com/onconavigator/domain/NotificationPreference.java` - JPA entity for user/admin notification preferences
- `src/main/java/com/onconavigator/domain/NotificationLog.java` - @Audited JPA entity with EncryptionConverter for PHI-safe dispatch log
- `src/main/java/com/onconavigator/domain/NotificationPendingQueue.java` - Operational queue entity for quiet-hours and digest holds
- `src/main/java/com/onconavigator/repository/NotificationPreferenceRepository.java` - Repository with admin default and user override queries
- `src/main/java/com/onconavigator/repository/NotificationLogRepository.java` - Repository for alert and user notification log queries
- `src/main/java/com/onconavigator/repository/NotificationPendingQueueRepository.java` - Repository for pending queue drain queries
- `src/main/java/com/onconavigator/domain/Alert.java` - Added missingSummary field with getter/setter
- `src/main/java/com/onconavigator/ai/model/AlertText.java` - Extended record to 3 components (deviationDescription, suggestedAction, missingSummary)
- `src/main/java/com/onconavigator/ai/service/AlertGenerationAiService.java` - Updated AlertText constructor to 3-arg (null missingSummary temporary)
- `src/test/java/com/onconavigator/activity/PathwayEvaluationActivityImplTest.java` - Updated 3 AlertText constructor calls to 3-arg form

## Decisions Made
- NotificationPreference entity omits @Audited annotation since it contains no ePHI (preference metadata only)
- NotificationLog entity uses @Audited + EncryptionConverter for rendered_content (contains patient name + MRN)
- AlertText constructor call sites pass null for missingSummary as temporary fix; Plan 02 will add full MISSING_SUMMARY parsing in AlertGenerationAiService
- V22 seeds TEAMS channel as enabled (TRUE) for log-only testing; EMAIL seeded as disabled per plan specification

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Updated test call sites for AlertText 3-arg constructor**
- **Found during:** Task 2 (Alert entity extension + AlertText record)
- **Issue:** PathwayEvaluationActivityImplTest.java has 3 call sites constructing AlertText with 2 args -- compile would fail
- **Fix:** Updated all 3 test call sites to pass null as the third argument (missingSummary)
- **Files modified:** src/test/java/com/onconavigator/activity/PathwayEvaluationActivityImplTest.java
- **Verification:** test-compile passes
- **Committed in:** b9ffb74 (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Essential fix for compile correctness. Test file was not listed in plan's files but required updating for AlertText record change. No scope creep.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Notification schema and entities ready for Plan 02 (NotificationService interface + LoggingNotificationService implementation)
- Alert.missingSummary field and AlertText 3-component record ready for Plan 02 (AI pipeline extension with MISSING_SUMMARY parsing)
- All three repositories ready for service-layer consumption in Plans 02-04

## Self-Check: PASSED

All 10 created files verified on disk. Both commit hashes (b356f81, b9ffb74) verified in git log.

---
*Phase: 09-alert-format-notification-foundation*
*Completed: 2026-05-05*
