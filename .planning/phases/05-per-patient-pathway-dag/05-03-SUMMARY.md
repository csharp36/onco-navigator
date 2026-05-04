---
phase: 05-per-patient-pathway-dag
plan: "03"
subsystem: pathway-service-layer
tags: [pathway, fork, template, dag, cycle-detection, crud, temporal, hipaa]
dependency_graph:
  requires: [05-01, 05-02]
  provides: [PathwayForkService, PatientPathwayService, pathway-mode-createPatient]
  affects: [PatientService, AlertRepository, CreatePatientRequest]
tech_stack:
  added: []
  patterns:
    - Kahn's algorithm for topological sort with depth metadata
    - DFS cycle detection for DAG edge creation
    - BOLA ownership verification (step/edge -> pathway -> patient)
    - Cascade-resolve alerts on step removal/skip
    - Optimistic locking via @Version for concurrent step edits
key_files:
  created:
    - src/main/java/com/onconavigator/service/PathwayForkService.java
    - src/main/java/com/onconavigator/service/PatientPathwayService.java
    - src/main/java/com/onconavigator/web/dto/PathwayStepRequest.java
    - src/main/java/com/onconavigator/web/dto/PathwayStepResponse.java
    - src/main/java/com/onconavigator/web/dto/PathwayEdgeRequest.java
    - src/main/java/com/onconavigator/web/dto/PathwayEdgeResponse.java
  modified:
    - src/main/java/com/onconavigator/web/dto/CreatePatientRequest.java
    - src/main/java/com/onconavigator/service/PatientService.java
    - src/main/java/com/onconavigator/repository/AlertRepository.java
decisions:
  - "PathwayForkService wraps JSONB parse in try-catch throwing IllegalStateException (not retried by Temporal per existing convention)"
  - "createEdge deduplication check uses in-memory stream scan on existing edges (pathway edge count bounded by step count)"
  - "updateStep uses null-check partial update pattern to avoid overwriting fields not in the request"
  - "AlertRepository.findByPatientIdAndPathwayStepNameAndStatus added as Rule 2 (correctness requirement for deleteStep/skipStep alert resolution)"
  - "getPrerequisiteIds helper re-queries edges for single-step responses (updateStep/skipStep) to avoid full topology recomputation"
metrics:
  duration: "4 minutes"
  completed_date: "2026-05-04"
  tasks: 2
  files: 9
---

# Phase 05 Plan 03: Pathway Service Layer Summary

## One-liner

Template fork service with deep-copy + UUID remapping, per-patient step/edge CRUD service with Kahn's topological sort and DFS cycle detection, patched into PatientService with pathwayMode routing.

## What Was Built

### Task 1: PathwayForkService, CreatePatientRequest Update, PatientService Integration

**PathwayForkService** implements the template fork operation (D-05):
- `forkFromTemplate(Patient, UUID)`: loads the cancer-type template, parses its JSONB `template_data` into `List<PathwayStep>`, deep-copies each step as a `PatientPathwayStep` with status=ACTIVE, then iterates prerequisites to create `PatientPathwayEdge` rows. Template string step IDs are remapped to the new UUIDs via a `Map<String, UUID>` built during step copy. Source template ID and version are recorded on the `PatientPathway` header.
- `createEmptyPathway(Patient, UUID)`: creates a `PatientPathway` header with no steps for the AI extraction flow (D-06).
- Both methods are `@Transactional` with IOException wrapped as `IllegalStateException` on parse failure.

**CreatePatientRequest**: added optional `String pathwayMode` field with `effectivePathwayMode()` compact method that defaults to `"template"` for backward compatibility with existing API clients.

**PatientService.createPatient**: routes through `forkFromTemplate` or `createEmptyPathway` after patient save, before Temporal workflow start. In template mode, signals `pathwayStepsChanged` inside the try block after workflow start so the evaluation engine immediately sees the forked steps.

New DTOs: `PathwayStepRequest`, `PathwayStepResponse`, `PathwayEdgeRequest`, `PathwayEdgeResponse` providing the API surface for Task 2 and Plan 05 REST controllers.

### Task 2: PatientPathwayService — Step/Edge CRUD with Cycle Detection

**PatientPathwayService** provides 8 public mutation methods plus 1 read method:

**Step CRUD:**
- `getSteps`: runs `computeTopology` (Kahn's algorithm) to return all steps in topological order with depth=0 for root nodes and max(predecessor depths)+1 for others.
- `createStep`: creates ACTIVE root-node step, signals Temporal.
- `updateStep`: partial update of mutable fields (null values skip the field), signals Temporal.
- `deleteStep`: resolves OPEN alerts → deletes edges via `deleteBySourceStepIdOrTargetStepId` → deletes step → signals Temporal.
- `skipStep`: validates ACTIVE status → sets SKIPPED + skipReason → resolves OPEN alerts → signals Temporal.
- `unskipStep`: validates SKIPPED status → restores ACTIVE, clears skipReason → signals Temporal.

**Edge CRUD:**
- `createEdge`: verifies both steps belong to patient's pathway → self-edge guard → dedup check → DFS cycle detection (`wouldCreateCycle`) → saves edge → signals Temporal.
- `deleteEdge`: ownership verification through pathway → deletes edge → signals Temporal.

**Private helpers:**
- `computeTopology`: Kahn's BFS with depth tracking via `Map<UUID, Integer>`. Predecessors map computed during adjacency build for O(1) prerequisite ID lookup.
- `wouldCreateCycle` + `dfsReaches`: DFS with visited-set cycle detection (T-05-06).
- `resolveAlertsForStep`: queries `AlertRepository.findByPatientIdAndPathwayStepNameAndStatus` for OPEN alerts and resolves them with a system note.
- `requirePathway` / `requireStep`: BOLA ownership verification helpers throwing 404 on mismatch (T-05-07).

**AlertRepository**: added `findByPatientIdAndPathwayStepNameAndStatus` Spring Data method (Rule 2 — required for deleteStep/skipStep correctness).

## Deviations from Plan

### Auto-added Missing Critical Functionality

**1. [Rule 2 - Missing functionality] AlertRepository.findByPatientIdAndPathwayStepNameAndStatus**
- **Found during:** Task 2 implementation
- **Issue:** Plan specified `alertRepository.findByPatientIdAndPathwayStepNameAndStatus(...)` in deleteStep/skipStep but this method did not exist in AlertRepository. `existsByPatientIdAndPathwayStepNameAndStatus` existed for dedup checks but not the `find` variant needed for resolution.
- **Fix:** Added Spring Data query method to AlertRepository.
- **Files modified:** `src/main/java/com/onconavigator/repository/AlertRepository.java`
- **Commit:** b02895a

**2. [Rule 2 - Missing DTOs] PathwayStepRequest, PathwayStepResponse, PathwayEdgeRequest, PathwayEdgeResponse**
- **Found during:** Task 1 (needed by both Task 1 return types and Task 2 method signatures)
- **Issue:** Plan specified these DTO types as parameters and return types but they did not yet exist.
- **Fix:** Created all four DTO records with appropriate field sets and Javadoc.
- **Files modified:** 4 new files in `src/main/java/com/onconavigator/web/dto/`
- **Commit:** dddfd04

## Threat Surface Scan

No new security-relevant surface beyond what was planned. The plan's threat model (T-05-06 through T-05-09) was fully implemented:
- T-05-06 (cycle injection): DFS cycle detection in `createEdge`
- T-05-07 (BOLA): `requireStep`/`requirePathway` ownership checks in all mutation methods
- T-05-08 (concurrent edit): `@Version` on `PatientPathwayStep` provides optimistic locking
- T-05-09 (PHI in logs): all log statements use patient UUID and step/edge UUID only

## Known Stubs

None — all implemented functionality is fully wired.

## Self-Check: PASSED

**Created files exist:**
- `src/main/java/com/onconavigator/service/PathwayForkService.java` — FOUND
- `src/main/java/com/onconavigator/service/PatientPathwayService.java` — FOUND
- `src/main/java/com/onconavigator/web/dto/PathwayStepRequest.java` — FOUND
- `src/main/java/com/onconavigator/web/dto/PathwayStepResponse.java` — FOUND
- `src/main/java/com/onconavigator/web/dto/PathwayEdgeRequest.java` — FOUND
- `src/main/java/com/onconavigator/web/dto/PathwayEdgeResponse.java` — FOUND

**Commits exist:**
- `dddfd04` — Task 1 (PathwayForkService + CreatePatientRequest + PatientService)
- `b02895a` — Task 2 (PatientPathwayService + AlertRepository)

**Compilation:** `./mvnw compile` passes with no errors.
