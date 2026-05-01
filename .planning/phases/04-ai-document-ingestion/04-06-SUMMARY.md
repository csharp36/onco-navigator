---
phase: 04-ai-document-ingestion
plan: 06
subsystem: ui
tags: [react, shadcn-ui, tanstack-router, document-upload, pre-fill, pdf-viewer, drag-and-drop, care-event]

# Dependency graph
requires:
  - phase: 04-ai-document-ingestion plan 04
    provides: DocumentUploadController REST endpoints, CareEvent document linkage via documentId, V10 migration
  - phase: 04-ai-document-ingestion plan 05
    provides: DocumentDropZone, DocumentProcessingModal, PatientMatchSelector, api-client multipart upload, document TypeScript types
provides:
  - PrefilledCareEventDialog with document source section, pre-filled fields, and document linkage
  - DocumentPreviewPanel inline PDF viewer in Sheet panel with download button
  - Dashboard page document drop zone integration with full processing flow
  - Patient detail page document drop zone with patient pre-selection (bypasses matching)
  - View Document buttons on care events with attached documents
affects: [04-07]

# Tech tracking
tech-stack:
  added: []
  patterns: [pre-filled form with visual distinction for AI-extracted values, inline PDF viewer via Sheet with browser native iframe, patient-context upload bypassing match step]

key-files:
  created:
    - frontend/src/features/documents/PrefilledCareEventDialog.tsx
    - frontend/src/features/documents/DocumentPreviewPanel.tsx
  modified:
    - frontend/src/routes/index.tsx
    - frontend/src/routes/patients/$patientId.tsx
    - frontend/src/features/patients/types.ts

key-decisions:
  - "PrefilledCareEventDialog tracks user-modified fields via Set<string> to toggle bg-muted/30 visual distinction between AI-extracted and user-edited values"
  - "Patient detail upload bypasses DocumentProcessingModal entirely when classification succeeds, going straight to pre-filled form (D-09)"
  - "Manual classification on patient detail page auto-opens pre-filled form immediately after type selection instead of showing intermediate modal"

patterns-established:
  - "Pre-fill visual distinction: bg-muted/30 on untouched fields, reverts to default on user interaction via onChange tracking"
  - "Patient-context upload: when patientId is known, skip matching step entirely and open pre-filled form directly"
  - "Document-to-event linkage: documentId passed through CreateCareEventRequest on form submission"

requirements-completed: [DOC-02, DOC-04, DOC-05]

# Metrics
duration: 4min
completed: 2026-05-01
---

# Phase 4 Plan 06: UI Integration and Pre-fill Flow Summary

**PrefilledCareEventDialog with document source section and AI-extracted field distinction, DocumentPreviewPanel inline PDF viewer, and document drop zone integration into dashboard and patient detail pages with full upload-to-save flow**

## Performance

- **Duration:** 4 min
- **Started:** 2026-05-01T21:39:04Z
- **Completed:** 2026-05-01T21:43:28Z
- **Tasks:** 2
- **Files modified:** 5

## Accomplishments
- PrefilledCareEventDialog reuses Phase 3 care event form pattern with read-only "Source Document" section showing classification type and confidence, pre-filled fields with bg-muted/30 visual distinction, and document linkage via documentId in save request
- DocumentPreviewPanel renders clinical documents inline using browser's native PDF renderer in a Sheet side panel with download button and graceful fallback for unsupported browsers
- Dashboard page has document drop zone above Urgent Alerts with full flow: drop -> processing modal -> patient match -> pre-filled form -> save event
- Patient detail page has Upload Document button in Care Events card header that bypasses patient matching (patient already known) and goes directly to pre-filled form
- Care events with attached documents show a View Document button (FileText icon) that opens the inline PDF viewer

## Task Commits

Each task was committed atomically:

1. **Task 1: PrefilledCareEventDialog and DocumentPreviewPanel components** - `3254064` (feat)
2. **Task 2: Integrate drop zones into dashboard and patient detail pages** - `5d4035e` (feat)

## Files Created/Modified
- `frontend/src/features/documents/PrefilledCareEventDialog.tsx` - Care event form pre-filled with document classification data, source document section, and document linkage
- `frontend/src/features/documents/DocumentPreviewPanel.tsx` - Inline PDF viewer in Sheet panel with download button and iframe fallback
- `frontend/src/routes/index.tsx` - Dashboard with DocumentDropZone (card variant), DocumentProcessingModal, and PrefilledCareEventDialog integration
- `frontend/src/routes/patients/$patientId.tsx` - Patient detail with DocumentDropZone (button variant), document preview panel, and View Document buttons on linked care events
- `frontend/src/features/patients/types.ts` - Added optional documentId field to CreateCareEventRequest to match backend DTO

## Decisions Made
- PrefilledCareEventDialog uses a `Set<string>` to track which fields the user has modified, toggling the bg-muted/30 background on untouched pre-filled fields. This gives a clear visual signal of AI-extracted vs. user-edited values without requiring a separate "confirm" step.
- On patient detail page, when classification succeeds, the upload flow bypasses DocumentProcessingModal entirely and opens the pre-filled form directly. When classification fails (null result), the modal opens for manual classification.
- After manual classification on the patient detail page, the flow immediately opens the pre-filled form instead of requiring the user to interact with the processing modal further, since the patient is already known.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added documentId to frontend CreateCareEventRequest type**
- **Found during:** Task 1 (PrefilledCareEventDialog implementation)
- **Issue:** Backend CreateCareEventRequest already has optional documentId field (from Plan 04), but frontend TypeScript type was missing it, which would prevent document-to-event linkage on save
- **Fix:** Added `documentId?: string` to CreateCareEventRequest in frontend/src/features/patients/types.ts
- **Files modified:** frontend/src/features/patients/types.ts
- **Verification:** TypeScript compilation passes; documentId flows from pre-fill dialog through to API call
- **Committed in:** 3254064 (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 missing critical)
**Impact on plan:** Essential for document-to-event linkage correctness. No scope creep.

## Issues Encountered

None -- all files compiled cleanly on first attempt. No dependency conflicts.

## User Setup Required

None -- no external service configuration required. All components are frontend-only and connect to the backend endpoints created in Plan 04.

## Next Phase Readiness
- Complete document upload flow is wired: drop -> process -> match -> pre-fill -> save with document linkage
- Dashboard and patient detail pages both have document ingestion capability
- Inline PDF viewer available for any attached document
- Ready for Plan 07 end-to-end verification and integration testing

## Self-Check: PASSED

All 5 created/modified files verified present on disk. Both task commit hashes (3254064, 5d4035e) verified in git log.

---
*Phase: 04-ai-document-ingestion*
*Completed: 2026-05-01*
