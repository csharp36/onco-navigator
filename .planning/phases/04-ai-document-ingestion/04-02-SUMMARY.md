---
phase: 04-ai-document-ingestion
plan: 02
subsystem: testing
tags: [test-corpus, synthetic-data, clinical-documents, evaluation, classification]

# Dependency graph
requires:
  - phase: 04-ai-document-ingestion/01
    provides: CareEventType and CancerType enums used as classification targets
provides:
  - 16 synthetic clinical documents covering 5 document types and 3 cancer types
  - Ground-truth reference dataset (JSON) for AI classification evaluation
  - Format variant and edge case test documents for robustness testing
affects: [04-ai-document-ingestion/03, 04-ai-document-ingestion/04, 04-ai-document-ingestion/05]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Synthetic clinical document format: header/demographics/body/signature blocks"
    - "Test corpus naming convention: {type}-{description}-{sequence}.txt"
    - "Ground-truth reference dataset pattern: JSON array with classification labels"

key-files:
  created:
    - test-corpus/README.txt
    - test-corpus/pathology/pathology-breast-lumpectomy-01.txt
    - test-corpus/pathology/pathology-lung-biopsy-01.txt
    - test-corpus/radiology/radiology-ct-chest-01.txt
    - test-corpus/radiology/radiology-mammogram-01.txt
    - test-corpus/operative-notes/operative-lumpectomy-01.txt
    - test-corpus/operative-notes/operative-colectomy-01.txt
    - test-corpus/lab-results/lab-cbc-panel-01.txt
    - test-corpus/lab-results/lab-tumor-markers-01.txt
    - test-corpus/referral/referral-radiation-oncology-01.txt
    - test-corpus/referral/referral-medical-oncology-01.txt
    - test-corpus/variants/pathology-alternate-format-01.txt
    - test-corpus/variants/radiology-alternate-format-01.txt
    - test-corpus/date-ambiguity/operative-note-multiple-dates-01.txt
    - test-corpus/date-ambiguity/pathology-delayed-dictation-01.txt
    - test-corpus/edge-cases/document-no-mrn-01.txt
    - test-corpus/edge-cases/multi-section-path-rad-01.txt
    - src/test/resources/eval/reference-dataset.json
  modified: []

key-decisions:
  - "Used text files instead of PDFs for test corpus: text files are directly usable in unit tests without PDF extraction overhead, and the extraction pipeline tests text-to-classification step separately"
  - "EDGE-002 (multi-section document) expectedMrn set to TEST-016 matching document content rather than null as in plan table: corrects plan inconsistency for accurate ground-truth evaluation"
  - "Added expectedCancerType field to reference dataset beyond plan spec: enables cancer type classification testing alongside document type classification"

patterns-established:
  - "Test corpus organization: type subdirectories (pathology/, radiology/, operative-notes/, lab-results/, referral/) plus special categories (variants/, date-ambiguity/, edge-cases/)"
  - "Synthetic patient identifiers: MRN format TEST-001 through TEST-016 with obviously fake surnames"

requirements-completed: [DOC-01]

# Metrics
duration: 9min
completed: 2026-05-01
---

# Phase 04 Plan 02: Test Corpus Summary

**16 synthetic clinical documents with ground-truth reference dataset covering all 5 document types, 3 cancer types, format variants, date ambiguity, and edge cases**

## Performance

- **Duration:** 9 min
- **Started:** 2026-05-01T20:56:51Z
- **Completed:** 2026-05-01T21:06:26Z
- **Tasks:** 2
- **Files created:** 18

## Accomplishments
- Created 16 realistic synthetic clinical documents organized by type across 8 subdirectories
- Each document has proper clinical formatting (headers, demographics, findings, signatures) with 500+ characters
- Ground-truth reference dataset maps all 16 documents to expected classification fields (document type, MRN, patient name, DOB, event type, event date, cancer type, confidence)
- Covered format variants (cytology report as alt pathology, PET-CT with pathology in impression) and edge cases (missing MRN, combined pathology+radiology report) for robustness testing
- Date ambiguity documents test correct extraction of procedure dates vs report/dictation dates

## Task Commits

Each task was committed atomically:

1. **Task 1: Create synthetic clinical document text files organized by type** - `5c5cb80` (feat)
2. **Task 2: Create reference dataset JSON with ground-truth labels** - `2d5741b` (feat)

## Files Created/Modified
- `test-corpus/pathology/*.txt` (2 files) - Breast lumpectomy and lung biopsy pathology reports
- `test-corpus/radiology/*.txt` (2 files) - CT chest and diagnostic mammogram reports
- `test-corpus/operative-notes/*.txt` (2 files) - Breast lumpectomy and right hemicolectomy operative notes
- `test-corpus/lab-results/*.txt` (2 files) - CBC panel and tumor marker results
- `test-corpus/referral/*.txt` (2 files) - Radiation oncology and medical oncology referral letters
- `test-corpus/variants/*.txt` (2 files) - Cytology report (alt pathology format), PET-CT with path findings
- `test-corpus/date-ambiguity/*.txt` (2 files) - Operative note with 4 dates, delayed dictation pathology
- `test-corpus/edge-cases/*.txt` (2 files) - No-MRN referral, combined pathology+radiology report
- `test-corpus/README.txt` - Corpus inventory, naming convention, usage instructions
- `src/test/resources/eval/reference-dataset.json` - Ground-truth labels for all 16 documents

## Decisions Made
- Used text files instead of generated PDFs -- text files are directly readable in unit tests and the extraction pipeline will be tested separately
- EDGE-002 expectedMrn corrected from plan table's null to TEST-016 to match document content -- ensures ground-truth accuracy for evaluation
- Added expectedCancerType field to reference dataset beyond plan specification -- enables cancer type classification testing

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Corrected EDGE-002 expectedMrn inconsistency**
- **Found during:** Task 2 (reference dataset creation)
- **Issue:** Plan table specified expectedMrn as null for EDGE-002 (multi-section-path-rad-01.txt), but Task 1 instructions include MRN TEST-016 in the document text. A null expectedMrn would cause a correct classifier to be scored as wrong.
- **Fix:** Set EDGE-002 expectedMrn to "TEST-016" to match actual document content
- **Files modified:** src/test/resources/eval/reference-dataset.json
- **Verification:** Python validation script confirms all entries pass acceptance criteria
- **Committed in:** 2d5741b (Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 bug fix)
**Impact on plan:** Minor correction for ground-truth accuracy. No scope creep.

## Issues Encountered
None

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Test corpus is ready for use by downstream plans (04-03 through 04-05) that implement document classification and extraction
- Reference dataset provides automated evaluation capability for classification accuracy testing (E-01 through E-05 in AI-SPEC)
- All documents use clearly synthetic identifiers -- safe for version control

## Self-Check: PASSED

- All 18 created files verified present on disk
- Both task commits (5c5cb80, 2d5741b) verified in git log

---
*Phase: 04-ai-document-ingestion*
*Completed: 2026-05-01*
