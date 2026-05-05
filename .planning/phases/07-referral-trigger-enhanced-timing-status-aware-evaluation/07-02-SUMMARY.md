---
phase: 07-referral-trigger-enhanced-timing-status-aware-evaluation
plan: 02
subsystem: ui
tags: [react, typescript, zod, react-hook-form, shadcn-ui, alerts, care-events]

# Dependency graph
requires:
  - phase: 04-alert-engine-and-nurse-dashboard
    provides: AlertCard, ResolveAlertModal, alert types infrastructure
  - phase: 05-per-patient-pathway-dag
    provides: CareEventResponse, CreateCareEventRequest, QuickAddCareEventDialog
  - phase: 06-ai-step-extraction-from-clinical-documents
    provides: PrefilledCareEventDialog with document-based care event creation
provides:
  - Extended TypeScript types for Phase 7 scheduling fields (referralReceivedAt, expectedCompletionDate, schedulingConfirmed, externalFacilityName)
  - Extended alert type unions supporting 7 alert types and 7 severity labels
  - Conditional scheduling form fields in care event dialogs
affects: [07-01, 07-03, 07-04]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Conditional form fields via form.watch() for status-dependent UI"
    - "Consistent severity badge variant mapping across AlertCard and ResolveAlertModal"

key-files:
  created: []
  modified:
    - frontend/src/features/patients/types.ts
    - frontend/src/features/alerts/types.ts
    - frontend/src/features/alerts/AlertCard.tsx
    - frontend/src/features/alerts/ResolveAlertModal.tsx
    - frontend/src/features/patients/QuickAddCareEventDialog.tsx
    - frontend/src/features/documents/PrefilledCareEventDialog.tsx

key-decisions:
  - "Used form.watch('status') for conditional field display rather than separate state variable"
  - "Applied z.boolean().default(false) for schedulingConfirmed to handle unchecked checkbox sending undefined (Pitfall 6)"
  - "Extended PrefilledCareEventDialog with same scheduling fields for API consistency"

patterns-established:
  - "Conditional form fields: form.watch() guards for status-dependent scheduling fields"
  - "Severity label mapping: OVERDUE/CANCELLED -> destructive, MISSING/RESULTS PENDING/DEADLINE -> default, others -> secondary"

requirements-completed: [PW-ALL-001, PW-ALL-003]

# Metrics
duration: 3min
completed: 2026-05-05
---

# Phase 7 Plan 02: Frontend Types and Alert Display Summary

**Extended TypeScript interfaces with 4 new alert types, 7 severity labels, and conditional scheduling fields in care event forms for Phase 7 status-aware evaluation**

## Performance

- **Duration:** 3 min
- **Started:** 2026-05-05T17:48:10Z
- **Completed:** 2026-05-05T17:51:37Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments
- Extended PatientResponse with referralReceivedAt, CareEventResponse with 3 scheduling fields, CreateCareEventRequest with 3 optional scheduling fields
- Extended AlertResponse alertType union from 3 to 7 types and severityLabel union from 3 to 7 labels
- Updated AlertCard and ResolveAlertModal severity badge variants and border colors for all 7 severity labels
- Added conditional expectedCompletionDate date picker and schedulingConfirmed checkbox (SCHEDULED/PENDING only) plus always-visible externalFacilityName text input to both QuickAddCareEventDialog and PrefilledCareEventDialog

## Task Commits

Each task was committed atomically:

1. **Task 1: TypeScript Types and Alert Display** - `98a337c` (feat)
2. **Task 2: QuickAddCareEventDialog Form Extension** - `02677cd` (feat)

## Files Created/Modified
- `frontend/src/features/patients/types.ts` - Added referralReceivedAt to PatientResponse, 3 scheduling fields to CareEventResponse and CreateCareEventRequest
- `frontend/src/features/alerts/types.ts` - Extended alertType and severityLabel unions with Phase 7 values
- `frontend/src/features/alerts/AlertCard.tsx` - Updated getSeverityBorderColor and getSeverityBadgeVariant for 7 severity labels
- `frontend/src/features/alerts/ResolveAlertModal.tsx` - Updated getSeverityBadgeVariant for 7 severity labels
- `frontend/src/features/patients/QuickAddCareEventDialog.tsx` - Extended Zod schema, handleSubmit, and form fields with scheduling coordination
- `frontend/src/features/documents/PrefilledCareEventDialog.tsx` - Extended Zod schema, handleSubmit, and form fields with scheduling coordination

## Decisions Made
- Used `form.watch('status')` for conditional field display rather than a separate state variable -- simpler, reactive, and consistent with existing form patterns
- Applied `z.boolean().default(false)` for `schedulingConfirmed` to handle the pitfall where an unchecked checkbox sends `undefined` which fails `z.boolean()` validation
- Extended PrefilledCareEventDialog alongside QuickAddCareEventDialog since both use the same `useCreateCareEvent` API hook and `CreateCareEventRequest` type

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Extended PrefilledCareEventDialog with Phase 7 scheduling fields**
- **Found during:** Task 2 (QuickAddCareEventDialog Form Extension)
- **Issue:** Plan noted to check PrefilledCareEventDialog and add fields if it uses the same API. It does -- same `useCreateCareEvent` hook and `CreateCareEventRequest` type.
- **Fix:** Added the same 3 Zod schema fields, 3 form fields (conditional display), and updated handleSubmit with conditional field passing
- **Files modified:** `frontend/src/features/documents/PrefilledCareEventDialog.tsx`
- **Verification:** `npx tsc --noEmit` passes with exit code 0
- **Committed in:** `02677cd` (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 missing critical)
**Impact on plan:** Necessary for API consistency. PrefilledCareEventDialog uses the same endpoint and would silently drop scheduling fields without this change. No scope creep.

## Issues Encountered
- `node_modules` not present in worktree -- ran `npm install` to enable TypeScript compilation verification. Standard worktree setup issue, resolved immediately.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Frontend types are ready to accept Phase 7 API response fields from Plans 01/03 (backend entity + migration changes)
- Alert display components handle all 7 severity labels -- backend can start returning new alert types
- Care event forms capture scheduling fields -- backend CareEvent creation endpoint (Plan 01) must accept these fields

## Self-Check: PASSED

All 6 modified files verified present on disk. Both task commits (98a337c, 02677cd) verified in git log. SUMMARY.md exists at expected path.

---
*Phase: 07-referral-trigger-enhanced-timing-status-aware-evaluation*
*Completed: 2026-05-05*
