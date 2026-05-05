---
phase: 07-referral-trigger-enhanced-timing-status-aware-evaluation
plan: 01
subsystem: database, api
tags: [flyway, postgresql, jpa, hibernate-envers, alerttype, dto, classification-prompts]

# Dependency graph
requires:
  - phase: 06-ai-step-extraction-from-clinical-documents
    provides: ClassificationPrompts structure, DocumentProcessingService pipeline, clinical_documents table
  - phase: 05-per-patient-pathway-dag
    provides: PathwayEvaluationActivityImpl with DAG evaluation, anchor date resolution
provides:
  - Four new AlertType PostgreSQL enum values via V17 migration (non-transactional, idempotent)
  - referral_received_at column on patients table via V18 migration
  - expected_completion_date, scheduling_confirmed, external_facility_name columns on care_events via V18 migration
  - Partial index idx_care_events_patient_status_expected for RESULTS_NOT_READY cross-event queries
  - Extended AlertType Java enum with 7 values
  - Patient entity with referralReceivedAt field
  - CareEvent entity with 3 scheduling coordination fields
  - Updated DTOs (PatientResponse, CareEventResponse, CreateCareEventRequest) with Phase 7 fields
  - AlertService.toSeverityLabel mapping for all 7 alert types
  - AlertRepository severity sort JPQL with 7-type clinical priority ordering
  - ClassificationPrompts REFERRAL_LETTER EXAMPLE 3 for referral PDF detection
affects: [07-02-frontend-scheduling-fields, 07-03-evaluation-rewrite, 07-04-referral-detection-hook]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Non-transactional Flyway migration for PostgreSQL ALTER TYPE ADD VALUE (flyway:nonTransactional directive)"
    - "Partial index on care_events for status-specific queries with WHERE clause"

key-files:
  created:
    - src/main/resources/db/migration/V17__add_alert_type_values.sql
    - src/main/resources/db/migration/V18__add_care_event_scheduling_fields.sql
  modified:
    - src/main/java/com/onconavigator/domain/enums/AlertType.java
    - src/main/java/com/onconavigator/domain/Patient.java
    - src/main/java/com/onconavigator/domain/CareEvent.java
    - src/main/java/com/onconavigator/web/dto/CreateCareEventRequest.java
    - src/main/java/com/onconavigator/web/dto/CareEventResponse.java
    - src/main/java/com/onconavigator/web/dto/PatientResponse.java
    - src/main/java/com/onconavigator/service/PatientService.java
    - src/main/java/com/onconavigator/service/AlertService.java
    - src/main/java/com/onconavigator/repository/AlertRepository.java
    - src/main/java/com/onconavigator/ai/prompt/ClassificationPrompts.java

key-decisions:
  - "Alert severity ordering: DELAYED > CANCELLED > RESULTS_NOT_READY > DEADLINE_APPROACHING > MISSING > SCHEDULING_UNCONFIRMED > OUT_OF_ORDER (clinical priority)"
  - "schedulingConfirmed defaults to Boolean.FALSE in both entity and migration, preventing null-related issues"
  - "CareEventResponse uses primitive boolean for schedulingConfirmed (Boolean.TRUE.equals guard in mapper) while entity uses nullable Boolean for JPA compatibility"

patterns-established:
  - "Non-transactional Flyway migration: use '-- flyway:nonTransactional' as first comment for ALTER TYPE ADD VALUE"
  - "Record DTO extension: add new fields at logical positions (after related fields), maintain backward-compatible JSON serialization"

requirements-completed: [PW-ALL-001, PW-ALL-003, PW-CR-001]

# Metrics
duration: 4min
completed: 2026-05-05
---

# Phase 7 Plan 01: Schema and Data Layer Contracts Summary

**Flyway migrations (V17/V18) for 4 new AlertType enum values and 5 new columns, with all Java entities, DTOs, severity ordering, and REFERRAL_LETTER classification prompt updated -- compiles cleanly**

## Performance

- **Duration:** 4m 24s
- **Started:** 2026-05-05T17:47:56Z
- **Completed:** 2026-05-05T17:52:20Z
- **Tasks:** 2
- **Files modified:** 12

## Accomplishments
- Two idempotent Flyway migrations: V17 (non-transactional enum extension with IF NOT EXISTS) and V18 (column additions with partial index for RESULTS_NOT_READY queries)
- AlertType enum extended from 3 to 7 values, with full parity between PostgreSQL enum and Java enum
- All DTOs (PatientResponse, CareEventResponse, CreateCareEventRequest) and service mappers (PatientService, AlertService) updated for Phase 7 fields
- AlertRepository severity sort JPQL updated to 7-type clinical priority ordering
- ClassificationPrompts augmented with REFERRAL_LETTER EXAMPLE 3 for downstream referral detection hook
- Project compiles cleanly with `./mvnw compile` -- zero errors

## Task Commits

Each task was committed atomically:

1. **Task 1: Flyway Migrations + Entity and Enum Updates** - `b241cfd` (feat)
2. **Task 2: DTOs, Service Mappers, Alert Severity, and ClassificationPrompts** - `cdc055b` (feat)

## Files Created/Modified

### Created
- `src/main/resources/db/migration/V17__add_alert_type_values.sql` - Non-transactional migration adding 4 alert_type enum values
- `src/main/resources/db/migration/V18__add_care_event_scheduling_fields.sql` - Migration adding referral_received_at, 3 care_event columns, partial index

### Modified
- `src/main/java/com/onconavigator/domain/enums/AlertType.java` - Extended enum: 3 to 7 values
- `src/main/java/com/onconavigator/domain/Patient.java` - Added referralReceivedAt (nullable OffsetDateTime)
- `src/main/java/com/onconavigator/domain/CareEvent.java` - Added expectedCompletionDate, schedulingConfirmed, externalFacilityName
- `src/main/java/com/onconavigator/web/dto/CreateCareEventRequest.java` - Added 3 optional scheduling fields
- `src/main/java/com/onconavigator/web/dto/CareEventResponse.java` - Added 3 scheduling response fields
- `src/main/java/com/onconavigator/web/dto/PatientResponse.java` - Added referralReceivedAt
- `src/main/java/com/onconavigator/service/PatientService.java` - Updated toPatientResponse, toCareEventResponse, addCareEvent
- `src/main/java/com/onconavigator/service/AlertService.java` - Updated toSeverityLabel for 7 alert types
- `src/main/java/com/onconavigator/repository/AlertRepository.java` - Updated severity sort JPQL to 7-type ordering
- `src/main/java/com/onconavigator/ai/prompt/ClassificationPrompts.java` - Added REFERRAL_LETTER EXAMPLE 3

## Decisions Made
- Alert severity ordering follows clinical priority: DELAYED (most urgent) > CANCELLED > RESULTS_NOT_READY > DEADLINE_APPROACHING > MISSING > SCHEDULING_UNCONFIRMED > OUT_OF_ORDER (least urgent). This ordering was specified in the plan's action section and matches the oncologist's clinical priorities.
- CareEventResponse uses primitive `boolean` for schedulingConfirmed (with `Boolean.TRUE.equals()` null-safe guard in mapper) while the JPA entity uses nullable `Boolean` -- this prevents NPE in JSON serialization while maintaining JPA column-level nullability.
- All new fields on CreateCareEventRequest are optional (no `@NotNull`), matching the pilot approach where nurses may not always have scheduling information at the time of care event recording.

## Deviations from Plan

None -- plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None -- no external service configuration required.

## Next Phase Readiness
- All data layer contracts are in place for Plan 02 (frontend scheduling fields), Plan 03 (evaluation rewrite), and Plan 04 (referral detection hook)
- The 7 AlertType values in Java and PostgreSQL are in sync -- downstream evaluation code can reference the new types immediately
- ClassificationPrompts REFERRAL_LETTER example enables Plan 04's referral detection hook in DocumentProcessingService

## Self-Check: PASSED

- All 12 files verified present on disk
- Commit b241cfd (Task 1) verified in git log
- Commit cdc055b (Task 2) verified in git log

---
*Phase: 07-referral-trigger-enhanced-timing-status-aware-evaluation*
*Completed: 2026-05-05*
