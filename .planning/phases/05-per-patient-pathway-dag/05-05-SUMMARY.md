---
phase: 05-per-patient-pathway-dag
plan: 05
subsystem: pathway-api
tags: [rest-api, controller, frontend, tanstack-query, shadcn, rbac]
dependency-graph:
  requires: [05-03]
  provides: [PatientPathwayController, pathway-step-edge-api, frontend-pathway-hooks, TemplatePicker]
  affects: [frontend-patient-wizard, pathway-dag-visualization]
tech-stack:
  added: [shadcn/RadioGroup, shadcn/Collapsible]
  patterns: [Spring @RestController, @PreAuthorize RBAC, TanStack Query useMutation/useQuery, React controlled state]
key-files:
  created:
    - src/main/java/com/onconavigator/web/PatientPathwayController.java
    - src/main/java/com/onconavigator/web/dto/SkipStepRequest.java
    - frontend/src/features/patients/TemplatePicker.tsx
    - frontend/src/components/ui/radio-group.tsx
    - frontend/src/components/ui/collapsible.tsx
  modified:
    - src/main/java/com/onconavigator/service/PatientPathwayService.java
    - frontend/src/features/patients/types.ts
    - frontend/src/features/patients/api.ts
    - frontend/src/features/patients/PatientWizard.tsx
decisions:
  - PatientPathwayService.getEdges() added to support GET /edges endpoint — the Plan 03 service had all step methods but was missing the corresponding edges read method; added as a thin @Transactional(readOnly=true) query delegating to the existing edge repository
  - shadcn CLI installed components to frontend/@/components/ui/ (literal @ directory) instead of frontend/src/components/ui/ due to path alias resolution in CLI context; Rule 3 fix applied by copying files to the correct src/ path and not staging the @ directory
metrics:
  duration: 3 minutes
  completed: 2026-05-04
  tasks-completed: 2
  files-changed: 9
---

# Phase 05 Plan 05: REST Controller, DTOs, Frontend Hooks, and TemplatePicker Summary

REST API bridging backend DAG services to frontend: PatientPathwayController with 9 endpoints, SkipStepRequest DTO, getEdges() service method, 9 TanStack Query hooks for step/edge CRUD, TemplatePicker RadioGroup component (D-07), and PatientWizard pathwayMode integration.

## What Was Built

### Task 1: Backend Controller and DTOs

**PatientPathwayController** — 9 REST endpoints under `/api/patients/{patientId}/pathway`:
- `GET /steps` — topological sort with depth metadata
- `POST /steps` — create step as root node (ACTIVE)
- `PUT /steps/{stepId}` — update mutable fields
- `DELETE /steps/{stepId}` — cascade-removes edges, resolves alerts
- `PATCH /steps/{stepId}/skip` — skip with required reason
- `PATCH /steps/{stepId}/unskip` — restore SKIPPED to ACTIVE
- `GET /edges` — list all prerequisite edges
- `POST /edges` — add prerequisite with cycle detection
- `DELETE /edges/{edgeId}` — remove prerequisite

Every endpoint has `@PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')")` satisfying D-03.

**SkipStepRequest.java** — `@NotBlank` on reason field.

**PatientPathwayService.getEdges()** — added `@Transactional(readOnly = true)` method returning `List<PathwayEdgeResponse>` for the GET /edges endpoint.

The 4 request/response DTOs (`PathwayStepRequest`, `PathwayStepResponse`, `PathwayEdgeRequest`, `PathwayEdgeResponse`) already existed from wave 1/2 — no changes needed.

### Task 2: Frontend Types, API Hooks, TemplatePicker, Wizard

**types.ts** — Added `PathwayStepStatusEnum`, `PatientPathwayStep`, `PatientPathwayEdge`, `CreateStepRequest`, `CreateEdgeRequest`, `SkipStepRequest`. Updated `PathwayStepStatus` (removed `stepNumber`, added `depth`, `sortOrder`, `prerequisiteStepIds`, `skipReason`). Added `pathwayMode` to `CreatePatientRequest`.

**api.ts** — Added 9 TanStack Query hooks with proper query key invalidation on mutations:
- `usePathwaySteps`, `usePathwayEdges` (queries)
- `useCreateStep`, `useUpdateStep`, `useDeleteStep` (step mutations)
- `useSkipStep`, `useUnskipStep` (step status mutations)
- `useCreateEdge`, `useDeleteEdge` (edge mutations)

**TemplatePicker.tsx** — RadioGroup component for D-07 pathway mode selection. Renders only when cancer type is selected. Two options: "Start from [Cancer Type] Pathway" or "Build from documents (empty pathway)".

**PatientWizard.tsx** — Import TemplatePicker, `pathwayMode` state (default 'template'), TemplatePicker rendered after cancer type Select in Step 2, `pathwayMode` reset to 'template' when cancer type changes, `pathwayMode` included in `createPatient` payload.

**Shadcn components** — RadioGroup and Collapsible installed and placed at `frontend/src/components/ui/`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Missing getEdges() service method**
- **Found during:** Task 1 — controller spec requires `GET /edges` endpoint delegating to `patientPathwayService.getEdges()`
- **Issue:** `PatientPathwayService` had all step operations but no `getEdges()` method
- **Fix:** Added `@Transactional(readOnly = true) public List<PathwayEdgeResponse> getEdges(UUID patientId)` to the service — thin query using existing `edgeRepository.findByPathway_Id()` and `toEdgeResponse()` mapper
- **Files modified:** `src/main/java/com/onconavigator/service/PatientPathwayService.java`
- **Commit:** 6801aa9

**2. [Rule 3 - Blocking] shadcn CLI installed to wrong path**
- **Found during:** Task 2 — after running `npx shadcn@latest add radio-group -y`, files landed at `frontend/@/components/ui/radio-group.tsx` instead of `frontend/src/components/ui/radio-group.tsx`
- **Issue:** shadcn CLI resolved the `@` alias as a literal directory name in the CLI context rather than using the `vite.config.ts` path alias (`@` = `./src`)
- **Fix:** Copied both `radio-group.tsx` and `collapsible.tsx` from `frontend/@/components/ui/` to `frontend/src/components/ui/`. The `frontend/@/` directory was not staged or committed.
- **Files modified:** Added `src/components/ui/radio-group.tsx`, `src/components/ui/collapsible.tsx`
- **Commit:** 99a3ced

## Verification

- Backend: `./mvnw compile` passes — PASSED
- Frontend: `npx tsc --noEmit` passes — PASSED
- `@PreAuthorize` count in controller: 9 (matches endpoint count)
- shadcn RadioGroup and Collapsible at `src/components/ui/`

## Known Stubs

None — all hooks target live REST endpoints backed by the service implementations from Plans 03/04.

## Threat Flags

No new security-relevant surface beyond what was planned. All 9 endpoints are protected by `@PreAuthorize` with three clinical roles. BOLA protection is enforced in the service layer (T-05-15 mitigated).

## Self-Check: PASSED

- `src/main/java/com/onconavigator/web/PatientPathwayController.java` — EXISTS
- `src/main/java/com/onconavigator/web/dto/SkipStepRequest.java` — EXISTS
- `frontend/src/features/patients/TemplatePicker.tsx` — EXISTS
- `frontend/src/components/ui/radio-group.tsx` — EXISTS
- `frontend/src/components/ui/collapsible.tsx` — EXISTS
- Task 1 commit 6801aa9 — EXISTS
- Task 2 commit 99a3ced — EXISTS
