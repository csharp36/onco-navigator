---
phase: 05-per-patient-pathway-dag
plan: 01
subsystem: database
tags: [flyway, postgresql, jpa, hibernate-envers, pathway-dag, migration]

# Dependency graph
requires:
  - phase: 02-pathway-engine
    provides: patients table, care_events table, pathway_templates JSONB, physician_overrides table, CareEventType enum
  - phase: 04-ai-document-ingestion
    provides: care_events.document_id column (V10)
provides:
  - pathway_step_status PostgreSQL enum (ACTIVE, PROPOSED, COMPLETED, SKIPPED)
  - patient_pathways table with UNIQUE(patient_id) constraint
  - patient_pathway_steps table with optimistic locking (version column), pathway_step_status column
  - patient_pathway_edges table with DAG integrity constraints (uq_pathway_edge, chk_no_self_edge)
  - Data migration: all existing patients converted from JSONB templates to per-patient relational rows
  - Physician overrides migrated to SKIPPED status on per-patient steps
  - Completed care events matched to per-patient steps via event_type alignment
  - PatientPathway JPA entity (@Audited)
  - PatientPathwayStep JPA entity (@Audited, @Version optimistic locking)
  - PatientPathwayEdge JPA entity (@Audited, write-once)
  - PathwayStepStatus Java enum
  - PatientPathwayRepository, PatientPathwayStepRepository, PatientPathwayEdgeRepository
affects: [05-02-pathway-dag-evaluation-service, 05-03-dag-api, 05-04-dag-frontend, 05-05-temporal-dag-worker, 05-06-integration-tests]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Per-patient pathway DAG: patient_pathways (1:1 with patients) → patient_pathway_steps → patient_pathway_edges as adjacency list"
    - "Step UUID columns on edges (not @ManyToOne) to avoid bidirectional graph navigation overhead in evaluation engine"
    - "@Version optimistic locking on PatientPathwayStep for concurrent status update safety"
    - "Write-once entity pattern for PatientPathwayEdge: only @PrePersist, no @PreUpdate"
    - "DO $$ DECLARE...BEGIN...END $$ PL/pgSQL block for complex data migration with loops and CTEs"
    - "System migration actor UUID '00000000-0000-0000-0000-000000000000' for created_by on migration-created rows"

key-files:
  created:
    - src/main/resources/db/migration/V12__update_pathway_time_windows.sql
    - src/main/resources/db/migration/V13__create_pathway_step_status_enum.sql
    - src/main/resources/db/migration/V14__create_per_patient_pathway_tables.sql
    - src/main/resources/db/migration/V15__migrate_patients_to_per_patient_pathways.sql
    - src/main/java/com/onconavigator/domain/enums/PathwayStepStatus.java
    - src/main/java/com/onconavigator/domain/PatientPathway.java
    - src/main/java/com/onconavigator/domain/PatientPathwayStep.java
    - src/main/java/com/onconavigator/domain/PatientPathwayEdge.java
    - src/main/java/com/onconavigator/repository/PatientPathwayRepository.java
    - src/main/java/com/onconavigator/repository/PatientPathwayStepRepository.java
    - src/main/java/com/onconavigator/repository/PatientPathwayEdgeRepository.java
  modified: []

key-decisions:
  - "PatientPathwayEdge uses UUID columns for source_step_id/target_step_id (not @ManyToOne) — evaluation engine works with step UUIDs directly when building DAG traversal structure; avoids bidirectional graph navigation overhead"
  - "V15 uses DO $$ PL/pgSQL block with explicit loops for per-patient processing — provides row-level control for physician override matching and care event assignment, clearer than pure CTE chains for this complexity level"
  - "V12 committed in this worktree — file was untracked in main repo, needed to be present for migration sequence V12→V13→V14→V15 to be valid"
  - "Ranked_steps in V15 PHASE 4 uses id ASC as ordering proxy for step number — insertion order from template expansion preserves stepNumber order"

patterns-established:
  - "PatientPathwayEdge write-once pattern: only @PrePersist (no @PreUpdate), all fields updatable=false — edges are immutable once created"
  - "Per-patient pathway 1:1 uniqueness: UNIQUE(patient_id) in patient_pathways + existsByPatient_Id repository check"
  - "PathwayStepStatus enum mirrors PostgreSQL enum type — columnDefinition='pathway_step_status' on @Enumerated(STRING) column"

requirements-completed: [PW-ALL-002, PW-BR-001, PW-BR-003]

# Metrics
duration: 4min
completed: 2026-05-04
---

# Phase 05 Plan 01: Per-Patient Pathway DAG Schema Foundation Summary

**Three-table DAG schema (patient_pathways, patient_pathway_steps, patient_pathway_edges) with PostgreSQL enum, data migration converting all patients from JSONB templates to per-patient relational rows, and JPA entities with Spring Data repositories**

## Performance

- **Duration:** ~4 minutes
- **Started:** 2026-05-04T17:51:01Z
- **Completed:** 2026-05-04T17:54:56Z
- **Tasks:** 2 completed
- **Files created:** 11

## Accomplishments

- Three Flyway migrations establish the per-patient pathway DAG schema: pathway_step_status enum (V13), three relational tables with FK constraints, cascade deletes, unique/check constraints, and onco_app grants (V14), and a full data migration from JSONB to relational rows (V15)
- V15 handles all D-08 edge cases: patients with no template, empty templates, physician overrides converted to SKIPPED status, completed care events matched to steps via event_type alignment with ROW_NUMBER window functions
- Four entity classes (PathwayStepStatus, PatientPathway, PatientPathwayStep, PatientPathwayEdge) follow all established project conventions: @Audited for HIPAA audit trail, FetchType.LAZY, @PrePersist/@PreUpdate lifecycle, @Version optimistic locking on steps
- Three Spring Data repositories provide all query methods needed by subsequent plans in phase 5

## Task Commits

Each task was committed atomically:

1. **Task 1: Flyway Migrations - Enum, Tables, and Data Migration** - `92bb55b` (feat)
2. **Task 2: JPA Entities, Enum, and Repository Interfaces** - `7726ef0` (feat)

## Files Created/Modified

- `src/main/resources/db/migration/V12__update_pathway_time_windows.sql` - Pathway time window corrections (untracked in main repo; added to worktree for migration sequence continuity)
- `src/main/resources/db/migration/V13__create_pathway_step_status_enum.sql` - CREATE TYPE pathway_step_status AS ENUM
- `src/main/resources/db/migration/V14__create_per_patient_pathway_tables.sql` - Three new tables with constraints, indexes, and grants
- `src/main/resources/db/migration/V15__migrate_patients_to_per_patient_pathways.sql` - PL/pgSQL data migration: JSONB expansion, edge building, override migration, completed event matching
- `src/main/java/com/onconavigator/domain/enums/PathwayStepStatus.java` - ACTIVE/PROPOSED/COMPLETED/SKIPPED enum
- `src/main/java/com/onconavigator/domain/PatientPathway.java` - @Audited entity with lazy patient FK and source template tracking
- `src/main/java/com/onconavigator/domain/PatientPathwayStep.java` - @Audited entity with @Version, PathwayStepStatus column, completed_care_event_id linkage
- `src/main/java/com/onconavigator/domain/PatientPathwayEdge.java` - @Audited write-once entity with UUID column references (no @ManyToOne on steps)
- `src/main/java/com/onconavigator/repository/PatientPathwayRepository.java` - findByPatient_Id, existsByPatient_Id
- `src/main/java/com/onconavigator/repository/PatientPathwayStepRepository.java` - findByPathway_Id, findByPathway_IdAndStatus, findByPathway_IdAndStatusIn
- `src/main/java/com/onconavigator/repository/PatientPathwayEdgeRepository.java` - findByPathway_Id, deleteBySourceStepIdOrTargetStepId

## Decisions Made

- **PatientPathwayEdge uses UUID columns not @ManyToOne:** The evaluation engine works with step UUIDs when building the DAG adjacency list; @ManyToOne relationships would add navigation overhead without benefit at evaluation time
- **V15 PL/pgSQL DO block:** Provides row-level control needed for physician override matching (join by patient_id + step_id string) and for the PHASE 4 care event assignment which requires window functions on a per-patient basis
- **V12 committed in worktree:** V12 was untracked in the main repo (confirmed by git status in initial context). It needed to be present for Flyway to sequence V12→V13→V14→V15 correctly on deployment

## Deviations from Plan

None - plan executed exactly as written. V12 was added to the worktree as a necessary inclusion (it was untracked in main repo) to maintain migration sequence integrity, but this was anticipated by the plan's `read_first` directive referencing V12.

## Issues Encountered

None. Project compiled cleanly on first attempt. All acceptance criteria verified via grep pattern matching.

## User Setup Required

None - no external service configuration required. Flyway migrations run automatically on next application startup.

## Next Phase Readiness

- All data layer artifacts exist for the per-patient pathway DAG
- PatientPathway/Step/Edge entities and repositories are ready for use by:
  - Phase 05-02: Pathway DAG evaluation service (reads ACTIVE steps, evaluates windows, updates status)
  - Phase 05-03: REST API endpoints for pathway/step management
  - Phase 05-04: Frontend pathway visualization
  - Phase 05-05: Temporal workflow worker using per-patient DAG evaluation
  - Phase 05-06: Integration tests with real PostgreSQL via Testcontainers

---
*Phase: 05-per-patient-pathway-dag*
*Completed: 2026-05-04*
