---
phase: 04-ai-document-ingestion
plan: 03
subsystem: ai, service
tags: [spring-ai, anthropic, pdfbox, tess4j, resilience4j, circuit-breaker, ocr, hipaa, zero-phi]

# Dependency graph
requires:
  - phase: 04-ai-document-ingestion plan 01
    provides: Spring AI ChatClient beans, Resilience4j config, PDFBox/Tess4J deps, ClinicalDocument entity, AI model records, prompt constants
provides:
  - PdfExtractionService with PDFBox 3.x text extraction and page rendering
  - OcrExtractionService with per-call Tesseract instances and confidence scoring
  - ClaudeVisionService with Spring AI multimodal API for scanned document fallback
  - DocumentClassificationService with @CircuitBreaker and BAA feature flag
  - AlertGenerationAiService with zero-PHI boundary and @CircuitBreaker
  - DocumentPatientMatchService with HMAC MRN lookup and name+DOB in-memory fallback
  - DocumentProcessingService orchestrating full extraction-classify-match pipeline
affects: [04-04, 04-05, 04-06, 04-07]

# Tech tracking
tech-stack:
  added: []
  patterns: [per-call Tesseract instances for virtual thread safety, multi-stage extraction pipeline (PDFBox->OCR->Claude vision), dual-PHI-boundary services, Resilience4j @CircuitBreaker with public fallback methods, extraction result record pattern]

key-files:
  created:
    - src/main/java/com/onconavigator/service/PdfExtractionService.java
    - src/main/java/com/onconavigator/service/OcrExtractionService.java
    - src/main/java/com/onconavigator/ai/service/ClaudeVisionService.java
    - src/main/java/com/onconavigator/ai/service/DocumentClassificationService.java
    - src/main/java/com/onconavigator/ai/service/AlertGenerationAiService.java
    - src/main/java/com/onconavigator/service/DocumentPatientMatchService.java
    - src/main/java/com/onconavigator/service/DocumentProcessingService.java
  modified: []

key-decisions:
  - "Per-call Tesseract instances (new Tesseract() per performOcr call) for virtual thread safety -- no shared bean, no pool"
  - "OCR confidence heuristic based on extracted text length (>100 chars = 75, else 40) since Tess4J getAPI() unreliable across builds"
  - "ClaudeVisionService uses AnthropicChatModel.call() directly (not ChatClient) for multimodal vision with UserMessage builder API"
  - "DocumentProcessingService does not persist ClinicalDocument without patient link (patient_id NOT NULL constraint) -- returns response for frontend to prompt user"
  - "Resilience4j fallback methods are public (CGLIB proxy requirement) and return null to signal caller for graceful degradation"

patterns-established:
  - "Extraction pipeline: PDFBox text -> OCR -> Claude vision, each with independent failure handling"
  - "Service-level BAA feature flag gating: classificationEnabled check before any Claude call with PHI"
  - "Zero-PHI service contract: AlertGenerationAiService accepts only anonymized parameters, enforced by method signature"
  - "MatchResult record pattern: status + optional matched ID + candidate list for multi-outcome matching"

requirements-completed: [DOC-02, DOC-03, AI-02, AI-03, AI-04]

# Metrics
duration: 7min
completed: 2026-05-01
---

# Phase 4 Plan 03: Backend Service Layer Summary

**PDFBox/OCR/Claude vision extraction pipeline, document classification with circuit breaker and BAA feature flag, HMAC+name+DOB patient matching, zero-PHI alert generation, and processing orchestrator**

## Performance

- **Duration:** 7 min
- **Started:** 2026-05-01T21:09:57Z
- **Completed:** 2026-05-01T21:17:48Z
- **Tasks:** 2
- **Files modified:** 7

## Accomplishments
- Seven service classes implementing the full extraction-classify-match pipeline compile cleanly
- Multi-stage text extraction: PDFBox for text-selectable PDFs, Tesseract OCR for scanned documents, Claude vision API fallback for low-confidence OCR
- @CircuitBreaker annotations on both Claude service classes (DocumentClassificationService, AlertGenerationAiService) with public fallback methods
- BAA feature flag gates document classification (onconavigator.ai.document-classification.enabled=false by default)
- Zero-PHI boundary enforced on AlertGenerationAiService via method signature accepting only anonymized parameters
- Patient matching supports HMAC MRN lookup (fast path) with name+DOB in-memory fallback (pilot scale)

## Task Commits

Each task was committed atomically:

1. **Task 1: PDF/OCR extraction services and Claude vision fallback** - `eb7d231` (feat)
2. **Task 2: Classification, alert generation, patient matching, and processing orchestrator** - `4d2cfbd` (feat)

## Files Created/Modified
- `src/main/java/com/onconavigator/service/PdfExtractionService.java` - PDFBox 3.x text extraction (Loader.loadPDF), page rendering at 300 DPI, selectable text detection
- `src/main/java/com/onconavigator/service/OcrExtractionService.java` - Per-call Tesseract OCR with confidence heuristic scoring, OCR_CONFIDENCE_THRESHOLD=60
- `src/main/java/com/onconavigator/ai/service/ClaudeVisionService.java` - Claude vision API fallback using Spring AI multimodal API with synchronous .call()
- `src/main/java/com/onconavigator/ai/service/DocumentClassificationService.java` - Claude classification with @CircuitBreaker, BAA feature flag, token budget truncation
- `src/main/java/com/onconavigator/ai/service/AlertGenerationAiService.java` - Zero-PHI alert text generation with @CircuitBreaker and DESCRIPTION/SUGGESTED_ACTION response parsing
- `src/main/java/com/onconavigator/service/DocumentPatientMatchService.java` - HMAC MRN lookup with in-memory name+DOB fallback, confidence-ranked candidates
- `src/main/java/com/onconavigator/service/DocumentProcessingService.java` - @Transactional orchestrator: validate upload, extract text, classify, match patient, persist document

## Decisions Made
- Per-call Tesseract instances rather than pooled/prototype-scoped beans -- simplest correct approach for virtual threads; benchmark before adding complexity
- OCR confidence heuristic (text length > 100 chars = 75 confidence) instead of Tess4J native API -- getAPI() is unreliable across Tess4J builds
- ClaudeVisionService uses AnthropicChatModel directly (not ChatClient) because multimodal vision requires low-level Prompt construction with Media objects
- DocumentProcessingService skips persistence when no patient is linked (ClinicalDocument.patient_id is NOT NULL) -- frontend handles the "select patient" flow
- Resilience4j fallback methods are public (not private) because CGLIB proxies cannot invoke private methods on the target class

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None - all services compiled on first attempt with correct Spring AI 1.1.5 API usage.

## User Setup Required

None - no external service configuration required for compilation. ANTHROPIC_API_KEY environment variable needed at runtime for Claude API calls (placeholder used for development). Tesseract native library required in Docker image (already configured in Dockerfile from Plan 01).

## Next Phase Readiness
- All seven service classes are ready for Plan 04 (controller endpoints and activity integration)
- DocumentProcessingService.processUpload() is the entry point for the upload controller
- AlertGenerationAiService.generateAlertDescription() is ready for PathwayEvaluationActivityImpl integration
- Plan 05-06 frontend work depends on controller endpoints from Plan 04, not directly on these services
- Plan 07 unit tests will test these services with mocked dependencies

## Self-Check: PASSED

All 7 created files verified present on disk. Both task commit hashes (eb7d231, 4d2cfbd) verified in git log.

---
*Phase: 04-ai-document-ingestion*
*Completed: 2026-05-01*
