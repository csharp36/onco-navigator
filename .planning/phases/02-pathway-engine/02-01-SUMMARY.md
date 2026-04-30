---
phase: 02-pathway-engine
plan: 01
subsystem: database
tags: [flyway, postgresql, jpa, hibernate-envers, jsonb, spring-data]

# Dependency graph
requires:
  - phase: 01-hipaa-foundation
    provides: patients table (FK target for physician_overrides), PathwayTemplate entity, CareEventType/CancerType enums, Hibernate Envers @Audited pattern, JPA repository conventions

provides:
  - physician_overrides table (V5 migration) with UNIQUE index on (patient_id, pathway_step_id)
  - Three seeded pathway templates: BREAST (6 steps), LUNG (6 steps), COLORECTAL (6 steps) per V1 Feature Specification
  - PhysicianOverride JPA entity (@Audited, write-once, no PHI)
  - AnchorType enum (DTO-level, PREVIOUS_STEP / DIAGNOSIS_DATE / SPECIFIC_STEP)
  - PathwayStep Java record for JSONB deserialization with all D-04 fields
  - PathwayTemplateRepository with findByCancerType
  - PhysicianOverrideRepository with existsByPatientIdAndPathwayStepId, findByPatientId, findByPatientIdAndPathwayStepId

affects:
  - 02-02 (Temporal workflow activities query PhysicianOverrideRepository and PathwayTemplateRepository)
  - 02-03 (pathway engine REST API uses these repositories)
  - 02-04 (integration tests run against seeded template data)

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "DTO-level enum in domain.dto package (vs PostgreSQL enum in domain.enums) for JSONB-only deserialization types"
    - "Write-once JPA entity: all fields updatable=false, no @PreUpdate lifecycle method"
    - "Java record for JSONB deserialization: canonical constructor matched by Jackson field names from seed data"

key-files:
  created:
    - src/main/resources/db/migration/V5__create_physician_overrides.sql
    - src/main/resources/db/migration/V6__seed_pathway_templates.sql
    - src/main/java/com/onconavigator/domain/PhysicianOverride.java
    - src/main/java/com/onconavigator/domain/dto/AnchorType.java
    - src/main/java/com/onconavigator/domain/dto/PathwayStep.java
    - src/main/java/com/onconavigator/repository/PathwayTemplateRepository.java
    - src/main/java/com/onconavigator/repository/PhysicianOverrideRepository.java
  modified: []

key-decisions:
  - "AnchorType placed in domain.dto (not domain.enums) — it is a JSONB deserialization type, not a PostgreSQL enum column type; mixing the two packages would create confusion about what maps to DB types"
  - "PathwayStep uses Java record canonical constructor — no @JsonProperty annotations needed because JSONB field names in V6 seed match record component names exactly"
  - "PhysicianOverride all fields updatable=false — overrides are write-once by design; using a UNIQUE index rather than soft-delete prevents duplicate suppression records"

patterns-established:
  - "DTO-level enums for JSONB deserialization live in com.onconavigator.domain.dto, not domain.enums"
  - "Java records in com.onconavigator.domain.dto for typed JSONB payloads, relying on Jackson canonical constructor"

requirements-completed: [PATH-01, PATH-02, PATH-08]

# Metrics
duration: 15min
completed: 2026-04-30
---

# Phase 02 Plan 01: Pathway Data Layer Summary

**Flyway migrations for physician_overrides schema and three seeded cancer pathway templates (BREAST/LUNG/COLORECTAL, 6 steps each), plus PhysicianOverride entity, PathwayStep JSONB record, AnchorType enum, and Spring Data repositories**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-04-30T14:08:00Z
- **Completed:** 2026-04-30T14:23:19Z
- **Tasks:** 2 of 2
- **Files modified:** 7 created, 0 modified

## Accomplishments

- V5 migration: `physician_overrides` table with UUID PK, FK to `patients`, UNIQUE index on `(patient_id, pathway_step_id)`, and `GRANT ALL TO onco_app` following project convention
- V6 migration: Three pathway templates seeded with exact alert text and suggested actions from the V1 Feature Specification; BREAST step 5 uses `SPECIFIC_STEP` anchor to BREAST_04 (Oncotype DX) to capture the clinical sequencing dependency correctly
- PhysicianOverride entity is `@Audited` and write-once — satisfies T-02-03 threat mitigation; no PHI stored
- PathwayStep Java record with all D-04 fields enables typed JSONB deserialization without explicit `@JsonProperty` annotations
- All five Java files compile with `./mvnw compile` BUILD SUCCESS

## Task Commits

1. **Task 1: Flyway migrations (V5 physician_overrides schema, V6 pathway template seeds)** - `862fe6c` (feat)
2. **Task 2: JPA entities, DTOs, enums, and repositories** - `b3df34a` (feat)

**Plan metadata:** _(final docs commit hash recorded below after state updates)_

## Files Created/Modified

- `src/main/resources/db/migration/V5__create_physician_overrides.sql` - physician_overrides table schema, UNIQUE index, GRANT
- `src/main/resources/db/migration/V6__seed_pathway_templates.sql` - INSERT for BREAST (6), LUNG (6), COLORECTAL (6) templates per V1 spec
- `src/main/java/com/onconavigator/domain/PhysicianOverride.java` - @Entity @Audited write-once entity, all fields updatable=false
- `src/main/java/com/onconavigator/domain/dto/AnchorType.java` - JSONB-only enum (PREVIOUS_STEP, DIAGNOSIS_DATE, SPECIFIC_STEP)
- `src/main/java/com/onconavigator/domain/dto/PathwayStep.java` - Java record with 12 D-04 fields for JSONB deserialization
- `src/main/java/com/onconavigator/repository/PathwayTemplateRepository.java` - JpaRepository with findByCancerType
- `src/main/java/com/onconavigator/repository/PhysicianOverrideRepository.java` - JpaRepository with existsByPatientIdAndPathwayStepId, findByPatientId, findByPatientIdAndPathwayStepId

## Decisions Made

- AnchorType placed in `domain.dto` (not `domain.enums`) because it is a JSONB deserialization enum, not a PostgreSQL column type. Mixing them would imply a DB enum mapping that does not exist.
- PathwayStep uses Java record canonical constructor so Jackson maps JSONB field names directly without `@JsonProperty` — the V6 seed uses matching camelCase keys.
- PhysicianOverride fields all set `updatable = false` — overrides are write-once by clinical design. The UNIQUE index on `(patient_id, pathway_step_id)` prevents duplicate suppression records.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None.

## User Setup Required

None - no external service configuration required. Flyway migrations will apply automatically on next application startup.

## Next Phase Readiness

- PathwayTemplateRepository and PhysicianOverrideRepository are ready for use by Temporal workflow activities in Plan 02-02
- All three cancer pathway templates are seeded with clinically accurate step definitions; alert text and suggested actions sourced directly from the V1 Feature Specification
- The `dto` package is established as the home for JSONB deserialization types, ready for additional DTO records in later plans

## Self-Check: PASSED

All 7 files confirmed present on disk. Both task commits (862fe6c, b3df34a) confirmed in git log.

---
*Phase: 02-pathway-engine*
*Completed: 2026-04-30*
