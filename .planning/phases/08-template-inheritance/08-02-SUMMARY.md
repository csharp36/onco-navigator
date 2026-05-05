---
phase: 08-template-inheritance
plan: 02
subsystem: api
tags: [template-inheritance, fork-service, rest-endpoint, spring-boot, mockito]
dependency_graph:
  requires:
    - phase: 08-template-inheritance/01
      provides: TemplateMergeService, TemplateDiff DTOs, PathwayTemplateRepository queries
  provides:
    - PathwayForkService merge integration (child template fork with live inheritance)
    - GET /api/pathway-templates REST endpoint for frontend template listing
    - PathwayTemplateResponse DTO
    - CreatePatientRequest templateId field for variant selection
    - Backward-compatible root template fallback in PatientService
  affects: [08-template-inheritance/03, frontend-TemplatePicker]
tech_stack:
  added: []
  patterns: [template-id-based-fork, root-template-fallback, sorted-template-listing]
key_files:
  created:
    - src/main/java/com/onconavigator/web/PathwayTemplateController.java
    - src/main/java/com/onconavigator/web/dto/PathwayTemplateResponse.java
    - src/test/java/com/onconavigator/service/PathwayForkServiceTest.java
  modified:
    - src/main/java/com/onconavigator/service/PathwayForkService.java
    - src/main/java/com/onconavigator/service/PatientService.java
    - src/main/java/com/onconavigator/web/dto/CreatePatientRequest.java
key_decisions:
  - "PatientService and CreatePatientRequest changes included in Task 1 commit (Rule 3: compilation dependency required both to be applied together)"
  - "PathwayForkService catch block re-throws ResponseStatusException and IllegalStateException to preserve HTTP status codes and specific error messages"
patterns-established:
  - "Template ID-based fork: forkFromTemplate accepts explicit templateId rather than deriving from cancer type"
  - "Root template fallback: null templateId in request resolved to root template for backward compatibility"
  - "Template listing sorted: root first, then children alphabetically by name"
requirements-completed: [PW-CR-004]
metrics:
  duration: 6min
  completed: "2026-05-05T20:21:00Z"
  tasks: 2
  files: 6
---

# Phase 08 Plan 02: Fork Service Merge Integration and Template REST Endpoint Summary

**PathwayForkService wired to TemplateMergeService for child template live inheritance, new GET /api/pathway-templates endpoint for frontend variant selection, and 4 unit tests covering root/child/error flows.**

## Performance

- **Duration:** 6 min
- **Started:** 2026-05-05T20:15:22Z
- **Completed:** 2026-05-05T20:21:22Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments
- PathwayForkService now loads templates by ID and merges parent+child diff via TemplateMergeService for child templates
- GET /api/pathway-templates?cancerType=X endpoint returns sorted template list (root first) with @PreAuthorize authentication
- CreatePatientRequest accepts optional templateId for explicit variant selection
- PatientService resolves null templateId to root template via findByCancerTypeAndParentTemplateIdIsNull (backward compat)
- 4 unit tests covering root template direct parse, child template merge flow, template not found, and parent not found

## Task Commits

Each task was committed atomically:

1. **Task 1: PathwayForkService merge integration and PathwayTemplateController** - `740bf6f` (feat)
2. **Task 2: PathwayForkServiceTest unit tests** - `903d3d5` (test)

## Files Created/Modified
- `src/main/java/com/onconavigator/service/PathwayForkService.java` - Template ID-based fork with merge engine integration for child templates
- `src/main/java/com/onconavigator/service/PatientService.java` - Added PathwayTemplateRepository dependency and root template fallback logic
- `src/main/java/com/onconavigator/web/PathwayTemplateController.java` - REST endpoint for listing templates by cancer type
- `src/main/java/com/onconavigator/web/dto/PathwayTemplateResponse.java` - Response DTO with id, cancerType, name, description, isRoot
- `src/main/java/com/onconavigator/web/dto/CreatePatientRequest.java` - Added optional templateId field
- `src/test/java/com/onconavigator/service/PathwayForkServiceTest.java` - 4 Mockito unit tests for fork service

## Decisions Made
- PatientService and CreatePatientRequest changes were included in Task 1 commit rather than Task 2 because PathwayForkService's new signature made compilation impossible without both changes simultaneously. This is a Rule 3 (blocking issue) deviation from the plan which split them across tasks.
- PathwayForkService catch block re-throws ResponseStatusException and IllegalStateException before the generic Exception catch, preserving HTTP 404 status for missing templates and specific error messages for missing parents.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] PatientService and CreatePatientRequest changes moved to Task 1**
- **Found during:** Task 1 (compile verification)
- **Issue:** PathwayForkService.forkFromTemplate new signature (3 params) broke PatientService compilation. Task 1 acceptance criteria required compile success.
- **Fix:** Applied PatientService + CreatePatientRequest changes as part of Task 1 commit to achieve compilation
- **Files modified:** PatientService.java, CreatePatientRequest.java
- **Verification:** `./mvnw compile -pl . -q` exits 0
- **Committed in:** 740bf6f (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** No scope change. Same work was done, just committed in a different task grouping due to compilation dependency.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- PathwayForkService fully supports root and child template forking with merge
- GET /api/pathway-templates endpoint ready for frontend consumption (Plan 03)
- CreatePatientRequest.templateId field ready for frontend to pass selected template variant
- All existing tests continue to pass (no regressions)

---
*Phase: 08-template-inheritance*
*Completed: 2026-05-05*
