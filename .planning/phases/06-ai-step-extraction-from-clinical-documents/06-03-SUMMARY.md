---
phase: 06-ai-step-extraction-from-clinical-documents
plan: "03"
subsystem: backend-step-confirmation
tags: [pathway, ai-extraction, confirm-reject, rest-api, dto]
dependency_graph:
  requires: [06-01, 06-02]
  provides: [confirmProposedStep, rejectProposedStep, sourceDocumentFilename-lookup, alreadyCoveredEventTypes-DTO]
  affects: [PatientPathwayService, PatientPathwayController, DocumentSummaryResponse, DocumentUploadController]
tech_stack:
  added: []
  patterns:
    - Status transition guard (PROPOSED check) following skipStep/unskipStep pattern
    - activateProposedEdges parses proposedEdgesJson with Jackson ObjectMapper, applies cycle detection per edge
    - toStepResponse performs PK lookup on documentRepository for sourceDocumentFilename (null-safe)
    - DocumentSummaryResponse extended with alreadyCoveredEventTypes field (9th parameter)
key_files:
  created: []
  modified:
    - src/main/java/com/onconavigator/service/PatientPathwayService.java
    - src/main/java/com/onconavigator/web/PatientPathwayController.java
    - src/main/java/com/onconavigator/web/dto/DocumentSummaryResponse.java
    - src/main/java/com/onconavigator/web/DocumentUploadController.java
decisions:
  - "[06-03]: activateProposedEdges passes actorId to PatientPathwayEdge.setCreatedBy — edge's createdBy is NOT NULL, actorId is the confirming nurse"
  - "[06-03]: activateProposedEdges rebuilds existingEdges list after each save to keep cycle detection current within batch — prevents second edge in same proposedEdgesJson from defeating detection"
  - "[06-03]: wouldCreateCycle uses existing signature (sourceStepId, targetStepId, List<PatientPathwayEdge>) — plan's interface spec showed (UUID pathwayId, UUID sourceId, UUID targetId) but actual implementation uses edge list; adaptation applied"
  - "[06-03]: CARE_COORDINATOR excluded from confirm/reject — clinical step activation is a nurse decision; differs from skip/unskip which allow CARE_COORDINATOR"
metrics:
  duration: "~10 min"
  completed: "2026-05-04"
  tasks_completed: 2
  files_modified: 4
---

# Phase 06 Plan 03: Confirm/Reject Endpoints and DocumentSummaryResponse Update Summary

Human-in-the-loop step confirmation flow complete: nurse navigators can confirm PROPOSED steps to ACTIVE (activating proposed DAG edges with cycle detection) or reject them to REJECTED (soft-deleted, audit-preserved, blocks re-proposal) via REST API with NURSE_NAVIGATOR/ADMIN-only authorization and BOLA protection. DocumentSummaryResponse extended with alreadyCoveredEventTypes for D-10 transparency display.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | confirmProposedStep, rejectProposedStep, toStepResponse sourceDocumentFilename | 063ce2d | PatientPathwayService.java |
| 2 | Confirm/reject endpoints, DocumentSummaryResponse, DocumentUploadController updates | 1107136 | PatientPathwayController.java, DocumentSummaryResponse.java, DocumentUploadController.java |

## What Was Built

### Task 1: PatientPathwayService

**`confirmProposedStep(UUID patientId, UUID stepId, UUID actorId)`**
- Ownership check via `requireStep(patientId, stepId)` (BOLA: T-06-11)
- 409 CONFLICT guard if step status is not PROPOSED (T-06-09)
- Transitions step to ACTIVE status
- Calls `activateProposedEdges` to resolve and create DAG edges from `proposedEdgesJson`
- Clears `proposedEdgesJson` after edge activation
- Signals Temporal via `pathwayService.signalPathwayStepsChanged(patientId)`

**`activateProposedEdges(UUID patientId, PatientPathwayStep step, UUID actorId)`**
- Parses `proposedEdgesJson` with Jackson `ObjectMapper`
- For each proposed edge: resolves predecessor by UUID first, then by name (case-insensitive)
- Runs `wouldCreateCycle` for each edge before creating it (T-06-12)
- Creates `PatientPathwayEdge` with `setSourceStepId`, `setTargetStepId`, `setCreatedBy(actorId)`
- Silently skips edges with unresolvable predecessors or cycle-creating edges with WARN log
- Maintains running `existingEdges` list so within-batch cycle detection is accurate

**`rejectProposedStep(UUID patientId, UUID stepId, UUID actorId)`**
- Same BOLA check and PROPOSED guard as confirm
- Transitions step to REJECTED status (soft delete — preserved for audit trail and dedup)
- Clears `proposedEdgesJson` (edges not needed for rejected steps)
- Signals Temporal via `signalPathwayStepsChanged`

**`toStepResponse` update**
- Replaced the `null` placeholder for `sourceDocumentFilename` with actual PK lookup:
  `documentRepository.findById(step.getSourceDocumentId()).map(doc -> doc.getOriginalFilename()).orElse(null)`
- Only performs DB lookup when `sourceDocumentId != null` (non-AI steps skip the lookup entirely)

**Constructor injection**: Added `ClinicalDocumentRepository documentRepository` as a sixth constructor parameter.

### Task 2: REST Layer and DTO

**`PatientPathwayController`**
- `POST /api/patients/{patientId}/pathway/steps/{stepId}/confirm` — `@PreAuthorize("hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')")`
- `PATCH /api/patients/{patientId}/pathway/steps/{stepId}/reject` — `@PreAuthorize("hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')")`
- CARE_COORDINATOR intentionally excluded (clinical step activation is a nurse decision, not data entry)
- Both extract `actorId` from `jwt.getSubject()` and delegate to PatientPathwayService

**`DocumentSummaryResponse`**
- Added 9th record field: `String alreadyCoveredEventTypes`
- Documented as Phase 6 D-10 field — comma-separated CareEventType values from StepExtractionTriggerService

**`DocumentUploadController`**
- Updated all three constructor call sites (`getDocument`, `linkDocumentToPatient`, `getDocumentsForPatient`) to pass `doc.getAlreadyCoveredEventTypes()` as the 9th argument

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Adapted wouldCreateCycle signature**
- **Found during:** Task 1 implementation
- **Issue:** Plan spec showed `wouldCreateCycle(UUID pathwayId, UUID sourceId, UUID targetId)` but actual existing method signature is `wouldCreateCycle(UUID sourceStepId, UUID targetStepId, List<PatientPathwayEdge> existingEdges)`
- **Fix:** Called the existing method with the correct signature — also needed to load `existingEdges` in `activateProposedEdges` via `edgeRepository.findByPathway_Id()`
- **Files modified:** PatientPathwayService.java

**2. [Rule 2 - Critical] Added actorId to activateProposedEdges for NOT NULL createdBy**
- **Found during:** Task 1 implementation
- **Issue:** `PatientPathwayEdge.createdBy` is NOT NULL. Plan's `activateProposedEdges` code used `setSourceStep(predecessor)` (entity reference) but actual entity uses `setSourceStepId(UUID)`. Also plan did not pass actorId to edges.
- **Fix:** Used `setSourceStepId(predecessor.getId())`, `setTargetStepId(step.getId())`, and `setCreatedBy(actorId)`. Added `actorId` parameter to `activateProposedEdges` private method signature.
- **Files modified:** PatientPathwayService.java

**3. [Rule 1 - Bug] Running existingEdges list in activateProposedEdges**
- **Found during:** Task 1 implementation
- **Issue:** Plan's cycle detection call was `wouldCreateCycle(pathway.getId(), predecessor.getId(), step.getId())` — does not update the edge list after each save, so the 2nd edge in a multi-edge proposedEdgesJson would not see the 1st edge in cycle detection
- **Fix:** Maintain a mutable copy of `existingEdges` list and add each newly saved edge before processing the next one
- **Files modified:** PatientPathwayService.java

## Known Stubs

None — all fields are fully wired. The `sourceDocumentFilename` field performs a real DB lookup (was `null` in 06-02 as a documented placeholder; resolved in this plan).

## Self-Check

### Created Files
None (no new files)

### Modified Files
- /Users/csharpl/Desktop/Source_Code/Java/Onco-Navigator/src/main/java/com/onconavigator/service/PatientPathwayService.java: confirmed `confirmProposedStep`, `rejectProposedStep`, `activateProposedEdges` methods present
- /Users/csharpl/Desktop/Source_Code/Java/Onco-Navigator/src/main/java/com/onconavigator/web/PatientPathwayController.java: confirmed `@PostMapping("/steps/{stepId}/confirm")` and `@PatchMapping("/steps/{stepId}/reject")` present
- /Users/csharpl/Desktop/Source_Code/Java/Onco-Navigator/src/main/java/com/onconavigator/web/dto/DocumentSummaryResponse.java: confirmed `String alreadyCoveredEventTypes` as 9th field
- /Users/csharpl/Desktop/Source_Code/Java/Onco-Navigator/src/main/java/com/onconavigator/web/DocumentUploadController.java: confirmed all three call sites pass `doc.getAlreadyCoveredEventTypes()`

### Build Verification
`./mvnw compile` exits BUILD SUCCESS (verified after each task)

### Commits
- 063ce2d: feat(06-03): add confirmProposedStep, rejectProposedStep, and sourceDocumentFilename lookup
- 1107136: feat(06-03): add confirm/reject endpoints, alreadyCoveredEventTypes to DocumentSummaryResponse

## Self-Check: PASSED
