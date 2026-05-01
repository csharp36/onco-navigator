---
phase: 04-ai-document-ingestion
plan: 07
subsystem: testing
tags: [junit5, mockito, pdfbox, spring-ai, circuit-breaker, zero-phi, hmac, unit-tests, hipaa]

# Dependency graph
requires:
  - phase: 04-ai-document-ingestion plan 03
    provides: DocumentClassificationService, AlertGenerationAiService, PdfExtractionService, DocumentPatientMatchService
  - phase: 04-ai-document-ingestion plan 04
    provides: PathwayEvaluationActivityImpl with Claude integration for non-standard deviations
provides:
  - Unit tests for DocumentClassificationService (circuit breaker fallback, feature flag, truncation)
  - Unit tests for AlertGenerationAiService (zero-PHI boundary enforcement, response parsing, fallback)
  - Unit tests for PdfExtractionService (PDFBox 3.x API, text extraction, selectable text detection)
  - Unit tests for DocumentPatientMatchService (HMAC MRN lookup, name+DOB fallback, candidate ranking)
  - Unit tests for PathwayEvaluationActivityImpl (template-first vs Claude-generated, fallback template, zero-PHI params)
affects: []

# Tech tracking
tech-stack:
  added: []
  patterns: [plain JUnit 5 + Mockito for service unit tests (no Spring context), in-memory PDFBox PDFs for extraction tests, ArgumentCaptor-based PHI boundary verification, fluent ChatClient mock chain pattern]

key-files:
  created:
    - src/test/java/com/onconavigator/ai/service/DocumentClassificationServiceTest.java
    - src/test/java/com/onconavigator/ai/service/AlertGenerationAiServiceTest.java
    - src/test/java/com/onconavigator/service/PdfExtractionServiceTest.java
    - src/test/java/com/onconavigator/service/DocumentPatientMatchServiceTest.java
    - src/test/java/com/onconavigator/activity/PathwayEvaluationActivityImplTest.java
  modified:
    - src/test/java/com/onconavigator/activity/PathwayEvaluationActivityTest.java

key-decisions:
  - "Created PathwayEvaluationActivityImplTest.java as a new test class (not modifying existing PathwayEvaluationActivityTest.java) to separate Claude integration tests from baseline deviation detection tests"
  - "Zero-PHI verification uses both ArgumentCaptor assertions and method signature reflection to prove no PHI can reach Claude via the generateAlertDescription API"
  - "PdfExtractionServiceTest creates PDFs in-memory using PDFBox API (no external test fixtures) for fully self-contained tests"

patterns-established:
  - "ChatClient fluent API mock chain: prompt() -> user(Consumer) -> call() -> entity/content"
  - "Zero-PHI boundary test pattern: capture all parameters passed to Claude service, assert no PHI patterns present"
  - "In-memory PDF generation for extraction tests: PDDocument + PDPageContentStream for text, empty PDPage for image-only"

requirements-completed: [AI-01, AI-04, DOC-02, DOC-03]

# Metrics
duration: 8min
completed: 2026-05-01
---

# Phase 4 Plan 07: Unit Tests for AI Services Summary

**27 unit tests covering classification circuit breaker, alert generation zero-PHI boundary, PDF extraction with PDFBox 3.x, HMAC patient matching, and pathway evaluation template-vs-Claude branching**

## Performance

- **Duration:** 8 min
- **Started:** 2026-05-01T21:49:11Z
- **Completed:** 2026-05-01T21:57:57Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments
- 5 test classes with 27 total test methods covering all Phase 4 core backend services
- Zero-PHI boundary enforcement verified via ArgumentCaptor and method signature reflection -- the alertGenerationPromptContainsNoPhi test and evaluate_passesOnlyAnonymizedContextToClaude test prove no patient identifiers can reach Claude
- Circuit breaker fallback behavior verified for both classification (returns null) and alert generation (returns null, triggers generic template)
- PDF extraction tests use in-memory PDFBox 3.x API (Loader.loadPDF pattern) -- no external test fixtures required
- Patient matching tests verify HMAC MRN fast path, name+DOB in-memory fallback, candidate ranking by confidence, and graceful no-match handling

## Task Commits

Each task was committed atomically:

1. **Task 1: Classification, alert generation PHI boundary, and PDF extraction tests** - `4a45817` (test)
2. **Task 2: Patient matching and pathway evaluation Claude integration tests** - `a93bbd5` (test)

## Files Created/Modified
- `src/test/java/com/onconavigator/ai/service/DocumentClassificationServiceTest.java` - 5 tests: classify success, exception null return, feature flag disabled, fallback null, text truncation
- `src/test/java/com/onconavigator/ai/service/AlertGenerationAiServiceTest.java` - 7 tests: response parsing, malformed response, empty response, description-only, exception, fallback, zero-PHI boundary
- `src/test/java/com/onconavigator/service/PdfExtractionServiceTest.java` - 4 tests: text extraction, hasSelectableText true/false, invalid input exception
- `src/test/java/com/onconavigator/service/DocumentPatientMatchServiceTest.java` - 6 tests: HMAC MRN match, name+DOB fallback, multiple candidates, no match, no identifiers, HMAC fallback
- `src/test/java/com/onconavigator/activity/PathwayEvaluationActivityImplTest.java` - 5 tests: template-first (AI-01), Claude call for null alertText, Claude call for blank alertText, fallback template (AI-04), zero-PHI params
- `src/test/java/com/onconavigator/activity/PathwayEvaluationActivityTest.java` - Fixed constructor to include AlertGenerationAiService mock (broken by Plan 04-04)

## Decisions Made
- Created PathwayEvaluationActivityImplTest.java as a separate test class rather than adding to the existing PathwayEvaluationActivityTest.java. The existing test covers Phase 2 baseline deviation detection; the new test covers Phase 4 Claude integration. Clean separation of concerns.
- Zero-PHI boundary verification uses two complementary approaches: (1) ArgumentCaptor captures actual parameters and asserts no PHI patterns, (2) reflection verifies the method signature itself only accepts anonymized parameter types.
- PdfExtractionServiceTest creates PDFs in-memory using PDFBox 3.x API rather than loading external fixture files. This makes tests fully self-contained and exercises the same PDFBox API used in production.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Fixed PathwayEvaluationActivityTest constructor mismatch**
- **Found during:** Task 1 (compilation of all test classes)
- **Issue:** Plan 04-04 added `AlertGenerationAiService` as 7th constructor parameter to PathwayEvaluationActivityImpl, but the existing PathwayEvaluationActivityTest.java still used the old 6-parameter constructor, causing compilation failure for ALL tests.
- **Fix:** Added `AlertGenerationAiService` mock to PathwayEvaluationActivityTest.setUp() and updated constructor call to include all 7 parameters.
- **Files modified:** src/test/java/com/onconavigator/activity/PathwayEvaluationActivityTest.java
- **Verification:** All 8 existing tests pass unchanged after constructor fix.
- **Committed in:** 4a45817 (Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Constructor fix was necessary for compilation. No scope creep. All 8 existing Phase 2 tests continue to pass.

## Issues Encountered

None - all tests compiled and passed on first attempt after the constructor fix.

## User Setup Required

None - unit tests run with `./mvnw test` using only mock dependencies. No external services required.

## Next Phase Readiness
- All 5 test classes provide the safety net for the AI integration layer
- 27 tests covering classification, alert generation, PDF extraction, patient matching, and pathway evaluation
- Zero-PHI boundary is now verified by automated tests -- any future regression will be caught
- Integration tests (with Testcontainers for real PostgreSQL, WireMock for Claude API) are a future enhancement beyond V1 scope

## Self-Check: PASSED

All 6 created/modified files verified present on disk. Both task commit hashes (4a45817, a93bbd5) verified in git log.

---
*Phase: 04-ai-document-ingestion*
*Completed: 2026-05-01*
