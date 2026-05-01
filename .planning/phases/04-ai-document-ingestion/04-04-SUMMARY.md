---
phase: 04-ai-document-ingestion
plan: 04
subsystem: web, activity, database
tags: [spring-mvc, multipart-upload, flyway, jpa, claude-ai, zero-phi, hipaa, bola-mitigation, rest-controller]

# Dependency graph
requires:
  - phase: 04-ai-document-ingestion plan 01
    provides: ClinicalDocument entity, ClinicalDocumentRepository, V9 migration, ChatClient beans, AI model records
  - phase: 04-ai-document-ingestion plan 03
    provides: DocumentProcessingService, AlertGenerationAiService, DocumentPatientMatchService
provides:
  - DocumentUploadController with multipart upload, content streaming, and patient document listing
  - V10 Flyway migration adding document_id FK column to care_events
  - CareEvent entity document linkage via optional documentId field
  - CreateCareEventRequest extended with optional documentId
  - PatientService.addCareEvent wiring documentId through to CareEvent
  - PathwayEvaluationActivityImpl Claude integration for non-standard deviations
  - DocumentSummaryResponse DTO for document metadata listing
affects: [04-05, 04-06, 04-07]

# Tech tracking
tech-stack:
  added: []
  patterns: [multipart upload controller with @PreAuthorize role checks, BOLA defense-in-depth with in-method role verification, zero-PHI Claude call from Temporal activity, generic fallback template for circuit breaker open state]

key-files:
  created:
    - src/main/java/com/onconavigator/web/DocumentUploadController.java
    - src/main/java/com/onconavigator/web/dto/DocumentSummaryResponse.java
    - src/main/resources/db/migration/V10__add_document_id_to_care_events.sql
  modified:
    - src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java
    - src/main/java/com/onconavigator/domain/CareEvent.java
    - src/main/java/com/onconavigator/web/dto/CreateCareEventRequest.java
    - src/main/java/com/onconavigator/service/PatientService.java

key-decisions:
  - "DocumentUploadController uses hasRole CARE_COORDINATOR or ADMIN on ALL three endpoints, not just upload -- consistent role enforcement"
  - "Content streaming endpoint has additional in-method role extraction and verification as BOLA defense-in-depth (T-04-11)"
  - "CareEvent.documentId is a plain UUID column (not @ManyToOne) matching the ClinicalDocument.careEventId pattern -- avoids bidirectional relationship complexity"
  - "buildAlert method gains 3 new parameters but standard deviation path (template text non-null) executes identically to before -- AI-01 behavior preserved"
  - "Generic fallback template includes step name and window days for minimal useful context when Claude is unavailable"

patterns-established:
  - "BOLA defense-in-depth: @PreAuthorize for Spring Security enforcement + in-method role check for programmatic verification"
  - "Zero-PHI activity integration: Temporal activity calls Claude service with only enum names and step labels, never Patient PHI fields"
  - "Circuit breaker fallback in activity: null Claude result triggers generic template text, not exception propagation"
  - "Document-to-event linkage: optional UUID FK in both directions (CareEvent.documentId, ClinicalDocument.careEventId) without bidirectional JPA relationship"

requirements-completed: [DOC-04, DOC-05, AI-01, AI-02]

# Metrics
duration: 4min
completed: 2026-05-01
---

# Phase 4 Plan 04: REST Controller, Activity Integration, and Schema Linkage Summary

**DocumentUploadController with multipart upload and BOLA-protected content streaming, PathwayEvaluationActivityImpl Claude integration for non-standard deviations with zero-PHI boundary, V10 Flyway migration linking care events to documents**

## Performance

- **Duration:** 4 min
- **Started:** 2026-05-01T21:22:04Z
- **Completed:** 2026-05-01T21:26:48Z
- **Tasks:** 2
- **Files modified:** 8

## Accomplishments
- DocumentUploadController provides POST /api/documents/upload (multipart, ACCEPTED status), GET /api/documents/{id}/content (byte streaming with Content-Type/Content-Disposition), and GET /api/documents/patient/{patientId} (metadata listing) -- all three with hasRole CARE_COORDINATOR or ADMIN
- PathwayEvaluationActivityImpl now uses Claude-generated alert text for non-standard deviations (null/blank alertText) while preserving template-first behavior for standard deviations unchanged
- Zero-PHI boundary enforced: only cancer type enum, step name, alert type enum, window days, and step names sent to Claude -- no patient identifiers referenced
- V10 Flyway migration adds document_id FK column to care_events with ON DELETE SET NULL and index
- CareEvent-to-document linkage wired end-to-end: CreateCareEventRequest -> PatientService -> CareEvent entity

## Task Commits

Each task was committed atomically:

1. **Task 1: DocumentUploadController, V10 migration, and CareEvent document linkage** - `bb86f54` (feat)
2. **Task 2: PathwayEvaluationActivityImpl Claude integration for non-standard deviations** - `4f09232` (feat)

## Files Created/Modified
- `src/main/java/com/onconavigator/web/DocumentUploadController.java` - REST controller with multipart upload, content streaming, and patient document listing
- `src/main/java/com/onconavigator/web/dto/DocumentSummaryResponse.java` - DTO for document metadata listing without blob content
- `src/main/resources/db/migration/V10__add_document_id_to_care_events.sql` - Adds document_id UUID FK to care_events referencing clinical_documents
- `src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java` - Added AlertGenerationAiService integration with zero-PHI boundary and circuit breaker fallback
- `src/main/java/com/onconavigator/domain/CareEvent.java` - Added optional documentId UUID field
- `src/main/java/com/onconavigator/web/dto/CreateCareEventRequest.java` - Added optional documentId field to record
- `src/main/java/com/onconavigator/service/PatientService.java` - Wires documentId from request through to CareEvent entity on save

## Decisions Made
- DocumentUploadController uses role-based access (hasRole) on ALL endpoints including GET content and GET patient documents -- not just isAuthenticated. This is more restrictive than the plan's research pattern (which showed isAuthenticated on content endpoint) but matches the plan action's upgraded BOLA mitigation.
- Content endpoint extracts roles via helper method that handles both flat and nested Keycloak JWT claim structures.
- CareEvent.documentId added as plain UUID (consistent with ClinicalDocument.careEventId pattern) rather than @ManyToOne -- avoids bidirectional JPA complexity.
- PatientService.addCareEvent directly sets documentId on the CareEvent without validating the UUID exists in clinical_documents. Document validation is the frontend's responsibility (documentId comes from a prior upload response). Database FK constraint provides integrity enforcement.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None - all files compiled cleanly on first attempt. No dependency conflicts.

## User Setup Required

None - no external service configuration required for compilation. Existing ANTHROPIC_API_KEY environment variable needed at runtime for Claude API calls in PathwayEvaluationActivityImpl (placeholder used for development).

## Next Phase Readiness
- Upload endpoint ready for frontend integration in Plans 05-06 (drag-and-drop, processing modal)
- Content streaming endpoint ready for inline PDF viewer (Plan 06)
- Patient documents listing endpoint ready for document history panel (Plan 06)
- PathwayEvaluationActivityImpl Claude integration ready for end-to-end testing in Plan 07
- V10 migration ready for integration tests with CareEvent document linkage

## Self-Check: PASSED

All 7 created/modified files verified present on disk. Both task commit hashes (bb86f54, 4f09232) verified in git log.

---
*Phase: 04-ai-document-ingestion*
*Completed: 2026-05-01*
