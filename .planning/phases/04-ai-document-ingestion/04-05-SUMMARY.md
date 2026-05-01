---
phase: 04-ai-document-ingestion
plan: 05
subsystem: ui, api
tags: [react-dropzone, tanstack-query, shadcn-ui, multipart-upload, drag-and-drop, document-processing, patient-matching]

# Dependency graph
requires:
  - phase: 04-ai-document-ingestion plan 01
    provides: ClinicalDocument entity, DocumentUploadResponse DTO, DocumentClassification record
  - phase: 04-ai-document-ingestion plan 03
    provides: DocumentProcessingService, DocumentPatientMatchService
  - phase: 04-ai-document-ingestion plan 04
    provides: DocumentUploadController with multipart upload, content streaming, and patient document listing endpoints
provides:
  - apiClient.upload multipart method on api-client.ts
  - TypeScript types matching backend DTOs (DocumentUploadResponse, DocumentClassificationResult, PatientCandidate, etc.)
  - TanStack Query hooks (useUploadDocument mutation, usePatientDocuments query)
  - DocumentDropZone component with card and button variants
  - DocumentProcessingModal with 5-step inline stepper and circuit breaker fallback
  - PatientMatchSelector with exact match, ranked candidates, and new patient creation
affects: [04-06, 04-07]

# Tech tracking
tech-stack:
  added: [react-dropzone 14.3.8, shadcn/ui progress component]
  patterns: [multipart FormData upload without Content-Type header, processing modal with inline stepper, patient match candidate ranking UI]

key-files:
  created:
    - frontend/src/features/documents/types.ts
    - frontend/src/features/documents/api.ts
    - frontend/src/features/documents/DocumentDropZone.tsx
    - frontend/src/features/documents/DocumentProcessingModal.tsx
    - frontend/src/features/documents/PatientMatchSelector.tsx
    - frontend/src/components/ui/progress.tsx
  modified:
    - frontend/src/lib/api-client.ts
    - frontend/package.json
    - frontend/package-lock.json

key-decisions:
  - "react-dropzone 14.3.8 installed instead of 15.0.0 (plan version not available on npm); API is compatible"
  - "DocumentDropZone supports both card and button variants to serve dashboard and patient detail page drop zones (D-09)"
  - "Processing modal derives step state from isUploading/uploadResult booleans since backend processes steps 2-4 synchronously"
  - "PatientMatchSelector is a non-modal subcomponent rendered inside DocumentProcessingModal rather than a separate dialog"

patterns-established:
  - "Multipart upload pattern: requestMultipart function that omits Content-Type header, letting browser set multipart boundary"
  - "Drop zone variant pattern: single component with card/button variants for different page contexts"
  - "Circuit breaker fallback UI: amber banner with manual Select dropdown when AI classification returns null"
  - "Patient match ranking: confidence-based Badge variants (secondary for high/medium, outline for low)"

requirements-completed: [DOC-02, DOC-03, DOC-04]

# Metrics
duration: 4min
completed: 2026-05-01
---

# Phase 4 Plan 05: Frontend Document Upload Infrastructure Summary

**react-dropzone document drop zone with card/button variants, 5-step processing modal with circuit breaker fallback, patient match selector with ranked candidates and new patient creation**

## Performance

- **Duration:** 4 min
- **Started:** 2026-05-01T21:31:39Z
- **Completed:** 2026-05-01T21:36:04Z
- **Tasks:** 2
- **Files modified:** 9

## Accomplishments
- Extended api-client.ts with multipart upload support (requestMultipart function + upload method) that correctly omits Content-Type header for browser boundary setting
- Created complete TypeScript type system for document flow matching backend DTOs: DocumentUploadResponse, DocumentClassificationResult, PatientCandidate, DocumentSummaryResponse, ProcessingStep, DocumentPrefillData
- Built DocumentDropZone with react-dropzone supporting PDF/JPEG/PNG, 20MB limit, card and button variants, keyboard accessibility with aria-label, and inline error display for rejections
- Built DocumentProcessingModal with 5-step inline stepper (Uploading/Extracting/Classifying/Matching/Ready), Progress bar, circuit breaker fallback UI with manual document type dropdown, and PatientMatchSelector integration
- Built PatientMatchSelector handling three states: exact match with high-confidence badge and confirm button, ranked candidates (up to 3) with confidence badges and select buttons, and no-match with Create New Patient option

## Task Commits

Each task was committed atomically:

1. **Task 1: api-client.ts upload method, TypeScript types, and TanStack Query hooks** - `7273fb4` (feat)
2. **Task 2: DocumentDropZone, DocumentProcessingModal, and PatientMatchSelector components** - `20ef8ac` (feat)

## Files Created/Modified
- `frontend/src/lib/api-client.ts` - Added requestMultipart function and upload method to apiClient
- `frontend/src/features/documents/types.ts` - TypeScript types for document upload flow (6 types/interfaces)
- `frontend/src/features/documents/api.ts` - TanStack Query hooks: useUploadDocument mutation and usePatientDocuments query
- `frontend/src/features/documents/DocumentDropZone.tsx` - Drag-and-drop file upload with card and button variants
- `frontend/src/features/documents/DocumentProcessingModal.tsx` - 5-step processing stepper modal with circuit breaker fallback
- `frontend/src/features/documents/PatientMatchSelector.tsx` - Patient match candidate selection (exact/candidates/no-match)
- `frontend/src/components/ui/progress.tsx` - shadcn Progress component (Radix UI)
- `frontend/package.json` - Added react-dropzone dependency
- `frontend/package-lock.json` - Lock file updated

## Decisions Made
- react-dropzone 14.3.8 installed (15.0.0 specified in plan not available on npm); API is fully compatible with the useDropzone hook pattern used
- DocumentProcessingModal determines current step from two state variables (isUploading + uploadResult) rather than tracking 5 individual step states, because the backend processes extraction/classification/matching synchronously in a single upload call
- PatientMatchSelector rendered as a subcomponent inside the processing modal dialog rather than as a separate dialog, keeping the user flow within a single modal interaction
- shadcn Progress component was installed to an incorrect path by npx shadcn CLI and manually moved to src/components/ui/ (shadcn config issue with @ alias resolution)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] shadcn Progress component installed to wrong path**
- **Found during:** Task 2 (component installation)
- **Issue:** `npx shadcn add progress` created file at `frontend/@/components/ui/progress.tsx` instead of `frontend/src/components/ui/progress.tsx`
- **Fix:** Moved file to correct location and removed spurious `@/` directory
- **Files modified:** frontend/src/components/ui/progress.tsx
- **Verification:** TypeScript compilation passes with import from `@/components/ui/progress`
- **Committed in:** 20ef8ac (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Minor path fix. No scope creep.

## Issues Encountered

None beyond the shadcn path issue documented above.

## User Setup Required

None - no external service configuration required. All components are frontend-only and connect to the backend endpoints created in Plan 04.

## Next Phase Readiness
- Document upload components ready for integration into existing pages in Plan 06 (dashboard and patient detail drop zones)
- DocumentProcessingModal ready to wire into document upload flow with QuickAddCareEventDialog pre-fill
- PatientMatchSelector ready for patient selection triggering care event creation
- useUploadDocument and usePatientDocuments hooks ready for consumption by page-level components

## Self-Check: PASSED

All 8 created/modified files verified present on disk. Both task commit hashes (7273fb4, 20ef8ac) verified in git log.

---
*Phase: 04-ai-document-ingestion*
*Completed: 2026-05-01*
