---
phase: 03-working-application
plan: 04
subsystem: ui
tags: [react, typescript, tanstack-query, tanstack-router, shadcn, zod, react-hook-form]

# Dependency graph
requires:
  - phase: 03-working-application plan 02
    provides: TypeScript types (PatientResponse, CareEventResponse, PathwayStepStatus), TanStack Query hooks (usePatients, usePatient, useCreatePatient, useDeactivatePatient, useCareEvents, useCreateCareEvent, usePathwayStatus), route scaffolds, shadcn UI components

provides:
  - Two-step patient creation wizard with per-step Zod v4 validation and post-create redirect
  - Patient list page with MRN search, status badges, and quick-add care event dialog
  - Patient detail page with demographics header, pathway visualization, care events list, and deactivate patient flow

affects: [care coordinators creating/viewing/deactivating patients, nurse navigators recording care events]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Zod v4 { error: '...' } syntax (NOT message) for schema validation messages
    - Two useForm instances for multi-step wizard (one per step), step 1 data preserved in useState
    - QuickAddCareEventDialog closes in mutation onSuccess callback (not useEffect)
    - hasRole() from auth.ts gates destructive UI actions client-side; server enforces via @PreAuthorize
    - Responsive layout: grid-cols-1 lg:grid-cols-5 for patient detail split

key-files:
  created:
    - frontend/src/features/patients/PatientWizard.tsx
    - frontend/src/features/patients/QuickAddCareEventDialog.tsx
  modified:
    - frontend/src/routes/patients/index.tsx
    - frontend/src/routes/patients/new.tsx
    - frontend/src/routes/patients/$patientId.tsx

key-decisions:
  - "Step 2 Assigned Navigator field is freetext Input (not Select) — V1 pilot has no user directory; care coordinator types navigator name. Maps to assignedNavigatorId on the request."
  - "PatientWizard uses two separate useForm instances (one per step) rather than a single form instance to simplify per-step validation without manual trigger() calls."
  - "Deactivate Patient button visibility guarded by hasRole (ROLE_CARE_COORDINATOR or ROLE_ADMIN) — consistent with threat model T-03-13: client-side role check is UX convenience only, server enforces authorization."

# Metrics
duration: 25min
completed: 2026-04-30
---

# Phase 3 Plan 04: Patient-Facing Frontend Pages Summary

**Two-step patient wizard, patient list with MRN search and quick-add, and patient detail with vertical pathway visualization and care events — all wired to TanStack Query hooks with Zod v4 validation.**

## Performance

- **Duration:** ~25 min
- **Completed:** 2026-04-30
- **Tasks:** 2 of 2
- **Files modified:** 5

## Accomplishments

- Built PatientWizard with two-step validation: step 1 (Demographics) and step 2 (Clinical Details), each with its own `useForm` and Zod v4 schema using `{ error: '...' }` syntax. Wizard navigates to `/patients/$patientId` on successful creation.
- QuickAddCareEventDialog with `useCreateCareEvent` mutation, `onOpenChange(false)` called in `onSuccess`, and dismiss button labeled "Discard Event" per UI-SPEC copywriting contract.
- Patient list page with MRN search bar, shadcn Table, status badges (On Track / Alert Active / Inactive), "Add Patient" link, and per-row "Record Event" button that opens the quick-add dialog.
- Patient detail page with compact demographics header, responsive `grid-cols-1 lg:grid-cols-5` split layout, vertical pathway stepped list with lucide-react icons (CheckCircle2/Clock/AlertTriangle/Circle), active-alert rows tinted `bg-amber-50`, care events list with inline status update select, and deactivate patient confirmation dialog with required reason.

## Task Commits

1. **Task 1: Patient wizard, patient list, QuickAddCareEventDialog** — `eb2d571`
2. **Task 2: Patient detail page** — `be7c478`

## Files Created/Modified

- `frontend/src/features/patients/PatientWizard.tsx` — Two-step wizard with zodResolver, Zod v4 schemas, step indicator (`bg-primary`), post-create navigate
- `frontend/src/features/patients/QuickAddCareEventDialog.tsx` — Quick-add dialog with useCreateCareEvent, closes in onSuccess
- `frontend/src/routes/patients/index.tsx` — Patient list with MRN search, Table, status badges, quick-add dialog
- `frontend/src/routes/patients/new.tsx` — Route wrapper for PatientWizard
- `frontend/src/routes/patients/$patientId.tsx` — Patient detail with split layout, pathway viz, care events, deactivate flow

## Decisions Made

- Assigned Navigator in step 2 is a freetext Input because V1 has no user directory. Value mapped to `assignedNavigatorId` in the API request payload.
- Separate `useForm` instances for each wizard step avoids triggering cross-step validation and simplifies the "Back" flow — step 1 data is held in `useState` and restored via `form1.reset(step1Data)` when the user returns.
- `hasRole()` gates the "Deactivate Patient" button at the client; server-side `@PreAuthorize` remains the authoritative boundary (per threat model T-03-13).

## Deviations from Plan

None — plan executed exactly as written. All acceptance criteria verified before commit.

## Threat Surface Scan

No new network endpoints or trust boundary changes. All mutations route through the existing `apiClient` which injects JWT. `hasRole()` used only for UX gating — no new auth surface introduced. Threat model entries T-03-13, T-03-14, T-03-15 apply as designed.

## Known Stubs

None. All page components are fully wired to TanStack Query hooks. Loading skeletons and error states are implemented. No hardcoded empty data flows to UI rendering.

## Self-Check

Files exist:
- frontend/src/features/patients/PatientWizard.tsx — FOUND
- frontend/src/features/patients/QuickAddCareEventDialog.tsx — FOUND
- frontend/src/routes/patients/index.tsx — FOUND
- frontend/src/routes/patients/new.tsx — FOUND
- frontend/src/routes/patients/$patientId.tsx — FOUND

Commits exist:
- eb2d571 — FOUND
- be7c478 — FOUND

`npx vite build` — PASSED (built in 225ms, 2073 modules transformed)

## Self-Check: PASSED
