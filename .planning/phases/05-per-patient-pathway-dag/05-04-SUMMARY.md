---
phase: 05-per-patient-pathway-dag
plan: 04
subsystem: pathway-evaluation-engine
tags: [dag, evaluation, topological-sort, service-layer, dto]
dependency_graph:
  requires:
    - 05-01  # PatientPathway, PatientPathwayStep, PatientPathwayEdge entities + repos
  provides:
    - DAG-based pathway evaluation engine (PathwayEvaluationActivityImpl)
    - Per-patient pathway status with depth (PathwayStatusService)
    - DAG-aware step status DTO (PathwayStepStatus)
  affects:
    - PatientPathwayWorkflowImpl (consumes PathwayEvaluationResult from evaluate())
    - PatientController (returns PathwayStatusResponse from PathwayStatusService)
tech_stack:
  added: []
  patterns:
    - Kahn's BFS topological sort with depth tracking
    - DAG prerequisite map from PatientPathwayEdge records
    - Latest-prerequisite anchor date resolution (D-11)
    - Zero-PHI circuit-breaker Claude integration preserved
key_files:
  created: []
  modified:
    - src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java
    - src/main/java/com/onconavigator/service/PathwayStatusService.java
    - src/main/java/com/onconavigator/web/dto/PathwayStepStatus.java
decisions:
  - "Kept PathwayEvaluationResult(boolean allStepsComplete, List<String> alertsGenerated) signature unchanged — Temporal workflow uses result.allStepsComplete(), changing the record would require workflow recompile"
  - "Used fully-qualified com.onconavigator.domain.enums.PathwayStepStatus in service to resolve name collision with web.dto.PathwayStepStatus; avoids renaming either class"
  - "Kept PathwayStatusResponse with UUID patientId (not String) to preserve controller contract"
  - "SKIPPED steps count as 'satisfied' prerequisites alongside COMPLETED steps"
  - "OUT_OF_ORDER detection short-circuits MISSING/DELAYED per-cycle to avoid conflicting nurse navigator alerts"
metrics:
  duration: "3m 56s"
  completed: "2026-05-04T18:04:42Z"
  tasks_completed: 2
  tasks_total: 2
  files_modified: 3
---

# Phase 05 Plan 04: DAG Evaluation Engine and Status Service Summary

**One-liner:** DAG-based pathway evaluation using Kahn's BFS topological sort on per-patient relational step/edge tables, replacing JSONB template iteration.

## Tasks Completed

| # | Task | Commit | Files |
|---|------|--------|-------|
| 1 | Rewrite PathwayEvaluationActivityImpl for DAG Evaluation | be2c0eb | PathwayEvaluationActivityImpl.java |
| 2 | Rewrite PathwayStatusService and Update DTOs | 71043e9 | PathwayStatusService.java, PathwayStepStatus.java |

## What Was Built

### Task 1: PathwayEvaluationActivityImpl DAG Rewrite

The evaluation activity now operates entirely on per-patient relational data:

- **Removed:** `PathwayTemplateRepository`, `PhysicianOverrideRepository`, `AnchorType`, `PathwayStep` JSONB DTO
- **Added:** `PatientPathwayRepository`, `PatientPathwayStepRepository`, `PatientPathwayEdgeRepository`
- Queries only `ACTIVE` steps for evaluation (PROPOSED steps skipped by status, COMPLETED/SKIPPED already terminal)
- Builds a prerequisite map from `PatientPathwayEdge` records
- Identifies "ready" steps: ACTIVE steps where all prerequisite step IDs are in the `satisfiedStepIds` set (COMPLETED ∪ SKIPPED)
- Anchor date resolution (D-11): root steps (no prerequisites) → `patient.getDiagnosisDate()`; steps with prerequisites → latest `completedAt.toLocalDate()` among completed prerequisites
- Empty pathway (no `PatientPathway` record) returns `PathwayEvaluationResult(false, List.of())` immediately
- Alert dedup preserved via `existsByPatientIdAndPathwayStepNameAndStatus`
- `buildAlertDescription()` preserves zero-PHI Claude integration with circuit breaker fallback (AI-01/AI-04)
- `closeOpenAlerts()` unchanged for workflow deactivation signal (D-08)

### Task 2: PathwayStatusService and DTO Update

**PathwayStepStatus DTO** — Complete record replacement:
- Removed: `stepNumber` field
- Added: `depth` (DAG level), `sortOrder` (topological position), `skipReason`, `prerequisiteStepIds`
- Status values now use PathwayStepStatus enum names: ACTIVE, COMPLETED, PROPOSED, SKIPPED

**PathwayStatusService** — Complete rewrite:
- Removed: `PathwayTemplateRepository`, `ObjectMapper`, `CareEventRepository` dependencies
- Added: `PatientPathwayRepository`, `PatientPathwayStepRepository`, `PatientPathwayEdgeRepository`
- Returns ALL steps (not just ACTIVE) for frontend visualization (COMPLETED, PROPOSED, SKIPPED are shown)
- Kahn's BFS algorithm builds topological order and depth map simultaneously
- Orphaned steps (not reached in BFS, indicating cycles or disconnected subgraphs) are appended at depth 0
- `computeTimingInfo()` resolves anchor date per D-11 and returns: "Due in N days", "N days overdue", "Due today", "Waiting on prerequisites", "Completed YYYY-MM-DD", "Skipped: reason", "Pending confirmation"

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Resolved PathwayStepStatus class name collision**
- **Found during:** Task 2 compilation
- **Issue:** `PathwayStatusService` imported both `com.onconavigator.domain.enums.PathwayStepStatus` (enum) and `com.onconavigator.web.dto.PathwayStepStatus` (DTO record). Java reported ambiguous reference on all enum comparisons in `computeTimingInfo()`.
- **Fix:** Removed the domain enum import; replaced all enum comparison references with fully qualified `com.onconavigator.domain.enums.PathwayStepStatus.COMPLETED` etc. The DTO import remains as the short-name import, resolving all ambiguity.
- **Files modified:** PathwayStatusService.java
- **Commit:** 71043e9

**2. [Rule 1 - Bug] Corrected PathwayEvaluationResult constructor arguments**
- **Found during:** Task 1 review
- **Issue:** Plan pseudocode used `new PathwayEvaluationResult(alertsCreated, allStepsComplete)` with `int` alertsCreated, but the existing record signature is `PathwayEvaluationResult(boolean allStepsComplete, List<String> alertsGenerated)` — changing it would require recompiling `PatientPathwayWorkflowImpl`.
- **Fix:** Kept `alertsGenerated` as `List<String>` with descriptive alert summary strings. Used count from list size for logging. Return statement uses correct record field order.
- **Files modified:** PathwayEvaluationActivityImpl.java
- **Commit:** be2c0eb

**3. [Rule 1 - Bug] Corrected Alert field setter names**
- **Found during:** Task 1 review of Alert entity
- **Issue:** Plan pseudocode used `alert.setPatient(patient)` and `alert.setDescription(...)` but Alert entity has `setPatientId(UUID)` and `setDeviationDescription(String)`.
- **Fix:** Used correct setter names per existing Alert entity API.
- **Files modified:** PathwayEvaluationActivityImpl.java
- **Commit:** be2c0eb

**4. [Rule 1 - Scope] PathwayStatusResponse UUID vs String patientId**
- **Found during:** Task 2 review
- **Issue:** Plan's rewrite used `new PathwayStatusResponse(patientId.toString(), List.of())` (String), but existing record uses `UUID patientId`. Changing would alter the serialized JSON `patientId` field format potentially breaking frontend clients.
- **Fix:** Kept `UUID patientId` in PathwayStatusResponse. Passed UUID directly to constructor. No PathwayStatusResponse.java changes needed.
- **Files modified:** None (no change)

## Self-Check

### Files Exist

- `src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java` — FOUND
- `src/main/java/com/onconavigator/service/PathwayStatusService.java` — FOUND
- `src/main/java/com/onconavigator/web/dto/PathwayStepStatus.java` — FOUND

### Commits Exist

- `be2c0eb` — FOUND (feat(05-04): rewrite PathwayEvaluationActivityImpl)
- `71043e9` — FOUND (feat(05-04): rewrite PathwayStatusService with DAG topology)

### Build Verification

`./mvnw compile` passes cleanly — no errors or warnings.

## Self-Check: PASSED

All files found. All commits verified. Build passes.

## Threat Surface Scan

No new network endpoints, auth paths, file access patterns, or schema changes introduced in this plan. Changes are purely service-layer refactors within existing transactional boundaries. Threat model items T-05-10, T-05-11, T-05-12 satisfied:

- **T-05-10:** All log statements use only patient UUID (`patientId`) and step UUID (`step.getId()`). Step names are not logged. Zero-PHI boundary preserved for AlertGenerationAiService calls.
- **T-05-11:** Alert dedup check `existsByPatientIdAndPathwayStepNameAndStatus` preserved unchanged. DB-level partial unique index from V7 migration still enforces at storage level.
- **T-05-12:** PathwayStatusService is an internal Spring bean. No direct user input reaches DB queries — patientId comes from the authenticated controller path variable.
