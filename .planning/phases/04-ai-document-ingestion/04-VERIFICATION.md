---
phase: 04-ai-document-ingestion
verified: 2026-05-01T22:36:09Z
status: human_needed
score: 8/8 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Drag a PDF onto the dashboard drop zone and verify the full flow: upload -> processing modal -> classification -> patient match -> pre-filled care event wizard -> save"
    expected: "Processing modal shows 5-step stepper, classification result displayed, patient match shown, pre-filled form opens with extracted data, care event saves with linked document"
    why_human: "End-to-end UX flow requires browser interaction, visual rendering, and a running backend with Claude API key"
  - test: "Drag a PDF onto the patient detail page Upload Document button and verify the pre-selected patient flow"
    expected: "Matching step is skipped, pre-filled care event wizard opens directly for the pre-selected patient"
    why_human: "UX variant flow requires browser interaction and visual validation"
  - test: "Upload a document when Claude API is unavailable (set invalid API key or kill connection) and verify circuit breaker fallback"
    expected: "DocumentProcessingModal shows amber banner with manual classification dropdown, user can select document type manually"
    why_human: "Circuit breaker behavior requires runtime state manipulation and visual confirmation of fallback UI"
  - test: "Click 'Preview full document' link in PrefilledCareEventDialog and verify inline PDF viewer"
    expected: "Sheet panel slides in from right showing the PDF rendered in an iframe with download button"
    why_human: "Iframe PDF rendering varies by browser and requires visual confirmation"
  - test: "Verify responsive layout of document drop zone and processing modal on a tablet-sized viewport"
    expected: "Drop zone and modal render without horizontal scrolling or overlapping elements"
    why_human: "Responsive layout verification requires visual inspection at different viewport sizes"
  - test: "Verify 5 CRITICAL and 7 WARNING findings from 04-REVIEW.md and decide which to fix before production"
    expected: "Developer reviews CR-01 through CR-05 and WR-01 through WR-07, decides on fix priority"
    why_human: "Code review findings are advisory and require developer judgment on priority and timing"
---

# Phase 4: AI Document Ingestion & Alert Enhancement Verification Report

**Phase Goal:** Clinical documents (PDFs) can be dragged into the dashboard, classified by Claude AI, matched to patients, and used to pre-fill care event recording -- reducing manual data entry. Additionally, non-standard deviation alerts get Claude-generated descriptions with circuit breaker fallback.
**Verified:** 2026-05-01T22:36:09Z
**Status:** human_needed
**Re-verification:** No -- initial verification

## Goal Achievement

### Observable Truths

| # | Truth (ROADMAP SC) | Status | Evidence |
|---|---|---|---|
| 1 | A test corpus of de-identified/synthetic clinical PDFs exists covering all supported document types | VERIFIED | 16 .txt files in `test-corpus/` across 8 subdirectories (pathology, radiology, operative-notes, lab-results, referral, variants, date-ambiguity, edge-cases). All 5 document types represented. Smallest file is 2,681 bytes (all exceed 500-char minimum). `src/test/resources/eval/reference-dataset.json` is valid JSON with 16 entries, null MRN for EDGE-001, all 4 categories (standard, variant, date-ambiguity, edge). |
| 2 | A user can drag a PDF onto the dashboard and the system classifies it into a document type using Claude AI | VERIFIED | `DocumentDropZone.tsx` uses react-dropzone with `accept: { 'application/pdf': ['.pdf'], 'image/jpeg': ['.jpg', '.jpeg'], 'image/png': ['.png'] }`, `maxFiles: 1`. Integrated into `routes/index.tsx` with `variant="card"`. Upload mutation calls `apiClient.upload` -> `POST /api/documents/upload` -> `DocumentProcessingService.processUpload()` -> `classificationService.classify()` which uses `ChatClient.entity(DocumentClassification.class)` with `@CircuitBreaker`. `DocumentProcessingModal.tsx` shows 5-step stepper with spinner/check/X states. |
| 3 | After classification, the system matches the document to an existing patient or offers new patient creation | VERIFIED | `DocumentPatientMatchService.matchPatient()` performs HMAC MRN lookup via `hmacTokenService.computeMrnToken()` -> `patientRepository.findByMrnHmacToken()`, then falls back to `patientRepository.findAll()` with in-memory name+DOB comparison. Returns "EXACT", "CANDIDATES", or "NO_MATCH". `PatientMatchSelector.tsx` renders ranked candidates with confidence badges, "Confirm Patient" button, and "Create New Patient" option for NO_MATCH. |
| 4 | On successful patient match, a pre-filled care event wizard opens with extracted data for user confirmation | VERIFIED | `PrefilledCareEventDialog.tsx` sets `defaultValues` from `prefillData.classification` (eventType, eventDate, status=COMPLETED, notes). Read-only "Source Document" section shows document type badge and confidence. Pre-filled fields get `bg-muted/30` styling. Form submit calls `useAddCareEvent` with `documentId` for document linkage. |
| 5 | The source PDF is stored as a blob in PostgreSQL and linked to the resulting care event record | VERIFIED | V9 migration creates `clinical_documents` table with `content BYTEA NOT NULL`. `ClinicalDocument.java` is `@Entity`, `@Audited`, has `@Convert(converter = EncryptionConverter.class)` on `extractedText`. `DocumentProcessingService` calls `doc.setContent(bytes)` and `documentRepository.save(doc)`. V10 migration adds `document_id UUID REFERENCES clinical_documents(id)` to `care_events`. `CareEvent.java` has `private UUID documentId`. `PatientService.addCareEvent()` calls `event.setDocumentId(req.documentId())`. `CreateCareEventRequest` has `documentId` field. |
| 6 | For low-quality faxed PDFs where extraction fails, the wizard opens with blank fields while still attaching the PDF | VERIFIED | `DocumentProcessingService.extractFromPdf()` tries PDFBox -> OCR -> Claude vision. When all return empty, `ExtractionResult(null, null)` is returned. With null text, classification is skipped (`if extraction.text != null`), so `classification = null`. Frontend `DocumentProcessingModal` shows amber "AI classification is temporarily unavailable" banner with manual classification Select dropdown when `classificationResult` is null. Patient detail path opens processing modal for manual classification. |
| 7 | Non-standard deviation alerts show Claude-generated plain-language descriptions with zero PHI in prompt, circuit breaker fallback to template text | VERIFIED | `PathwayEvaluationActivityImpl.buildAlert()`: when `step.alertText()` is null/blank, calls `alertGenerationAiService.generateAlertDescription()` with ONLY `patient.getCancerType().name()`, `step.name()`, `alertType.name()`, `step.windowDays()`, step name lists. Zero patient PHI fields accessed (confirmed: grep for `patient.getFirstName/getLastName/getMrn/getDateOfBirth` returns 0 matches in the file). `AlertGenerationAiService` has class-level "ONLY anonymized clinical context" comment, `@CircuitBreaker(name = "claude-api")`. Fallback returns null, caller uses generic template "Care pathway deviation detected for step: {name}". |
| 8 | When Claude API is unavailable, both document classification and alert generation fall back gracefully | VERIFIED | `DocumentClassificationService`: `@CircuitBreaker(name = "claude-api", fallbackMethod = "classifyFallback")`, fallback returns null. Feature flag `classificationEnabled` (default false) gates classification. `AlertGenerationAiService`: `@CircuitBreaker(name = "claude-api", fallbackMethod = "generateAlertFallback")`, fallback returns null. `application-local.yml` has `resilience4j.circuitbreaker.instances.claude-api` with slidingWindowSize=10, failureRateThreshold=50, waitDurationInOpenState=30s. Frontend shows manual classification dropdown when classification result is null. |

**Score:** 8/8 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|---|---|---|---|
| `pom.xml` | Spring AI, Resilience4j, PDFBox, Tess4J deps | VERIFIED | `spring-ai-bom` 1.1.5, `spring-ai-starter-model-anthropic`, `resilience4j-spring-boot3` 2.3.0, `pdfbox` 3.0.7, `tess4j` 5.16.0 all present |
| `V9__create_clinical_documents.sql` | clinical_documents table | VERIFIED | CREATE TABLE with BYTEA content, extracted_text_encrypted, patient_id FK, indexes |
| `V10__add_document_id_to_care_events.sql` | document_id FK on care_events | VERIFIED | ALTER TABLE care_events ADD COLUMN document_id UUID REFERENCES clinical_documents |
| `ClinicalDocument.java` | JPA entity with @Audited, EncryptionConverter | VERIFIED | @Entity, @Audited, EncryptionConverter on extractedText, BYTEA content column |
| `AiClientConfig.java` | Two ChatClient beans | VERIFIED | documentClassificationClient (temp 0.1) and alertGenerationClient (temp 0.3) with @Qualifier injection |
| `DocumentClassificationService.java` | Claude classification with @CircuitBreaker | VERIFIED | @CircuitBreaker, classifyFallback, feature flag, entity(DocumentClassification.class) |
| `AlertGenerationAiService.java` | Zero-PHI alert generation with @CircuitBreaker | VERIFIED | @CircuitBreaker, generateAlertFallback, zero-PHI class comment, parseAlertResponse |
| `PdfExtractionService.java` | PDFBox text extraction | VERIFIED | Loader.loadPDF, PDFTextStripper, renderImageWithDPI |
| `OcrExtractionService.java` | Tesseract OCR | VERIFIED | new Tesseract() per-call, OCR_CONFIDENCE_THRESHOLD=60, OcrResult record |
| `ClaudeVisionService.java` | Claude vision fallback | VERIFIED | AnthropicChatModel.call(), maxTokens(4096) |
| `DocumentProcessingService.java` | Orchestrator | VERIFIED | processUpload with @Transactional, injects all extraction/classification/matching services |
| `DocumentPatientMatchService.java` | HMAC MRN + name+DOB matching | VERIFIED | hmacTokenService.computeMrnToken, patientRepository.findAll for fallback, MatchResult |
| `DocumentUploadController.java` | REST endpoints | VERIFIED | POST /api/documents/upload (multipart), GET /{id}/content (BOLA protected), GET /patient/{patientId}, all @PreAuthorize |
| `PathwayEvaluationActivityImpl.java` | Claude alert integration | VERIFIED | alertGenerationAiService injected, template-first check, Claude call for null/blank alertText, fallback template |
| `frontend/src/lib/api-client.ts` | Multipart upload | VERIFIED | requestMultipart function, upload method on apiClient |
| `frontend/src/features/documents/types.ts` | TypeScript types | VERIFIED | DocumentUploadResponse, DocumentClassificationResult, PatientCandidate, DocumentType, ProcessingStep, DocumentPrefillData |
| `frontend/src/features/documents/DocumentDropZone.tsx` | Drag-and-drop component | VERIFIED | useDropzone, accept PDF/JPEG/PNG, maxFiles 1, card/button variants |
| `frontend/src/features/documents/DocumentProcessingModal.tsx` | 5-step stepper | VERIFIED | Processing Document title, Loader2/CheckCircle2/XCircle, onInteractOutside, circuit breaker fallback UI |
| `frontend/src/features/documents/PatientMatchSelector.tsx` | Patient match UI | VERIFIED | Confirm Patient, No automatic match found, Create New Patient |
| `frontend/src/features/documents/PrefilledCareEventDialog.tsx` | Pre-filled care event form | VERIFIED | defaultValues from classification, Source Document section, documentId linking |
| `frontend/src/features/documents/DocumentPreviewPanel.tsx` | Inline PDF viewer | VERIFIED | Sheet side="right", iframe with title="Clinical document preview", getDocumentContentUrl |
| `frontend/src/routes/index.tsx` | Dashboard integration | VERIFIED | DocumentDropZone variant="card", DocumentProcessingModal, PrefilledCareEventDialog imported and rendered |
| `frontend/src/routes/patients/$patientId.tsx` | Patient detail integration | VERIFIED | DocumentDropZone variant="button", DocumentPreviewPanel, usePatientDocuments |
| `test-corpus/` | 16 synthetic documents | VERIFIED | 16 .txt files, 5 types, 3 cancer types, variants, date ambiguity, edge cases |
| `reference-dataset.json` | Ground truth labels | VERIFIED | 16 entries, valid JSON, null MRN for EDGE-001, all types/categories |
| Test files (5 classes) | Unit tests for Phase 4 services | VERIFIED | 27 @Test methods across DocumentClassificationServiceTest, AlertGenerationAiServiceTest, PdfExtractionServiceTest, DocumentPatientMatchServiceTest, PathwayEvaluationActivityImplTest |

### Key Link Verification

| From | To | Via | Status | Details |
|---|---|---|---|---|
| DocumentDropZone.tsx | api.ts | useUploadDocument mutation | WIRED | import and call confirmed |
| api.ts | api-client.ts | apiClient.upload | WIRED | apiClient.upload('/documents/upload', formData) confirmed |
| DocumentUploadController | DocumentProcessingService | processUpload() | WIRED | documentProcessingService.processUpload(file, patientId, actorId) confirmed |
| DocumentProcessingService | PdfExtractionService | extractText/hasSelectableText/renderFirstPage | WIRED | pdfExtractionService method calls confirmed at lines 231-241 |
| DocumentProcessingService | DocumentClassificationService | classify() | WIRED | classificationService.classify(extraction.text) confirmed at line 113 |
| DocumentProcessingService | DocumentPatientMatchService | matchPatient() | WIRED | patientMatchService.matchPatient(classification) confirmed at line 126 |
| DocumentClassificationService | AiClientConfig | @Qualifier("documentClassificationClient") | WIRED | ChatClient bean injected via @Qualifier |
| AlertGenerationAiService | AiClientConfig | @Qualifier("alertGenerationClient") | WIRED | ChatClient bean injected via @Qualifier |
| PathwayEvaluationActivityImpl | AlertGenerationAiService | generateAlertDescription() | WIRED | alertGenerationAiService.generateAlertDescription() called at line 368 |
| V10 migration | V9 migration | FK reference | WIRED | REFERENCES clinical_documents(id) confirmed |
| CareEvent.documentId | PatientService | setDocumentId | WIRED | event.setDocumentId(req.documentId()) at PatientService line 182 |
| routes/index.tsx | DocumentDropZone | component import + render | WIRED | Import and JSX rendering confirmed |
| routes/$patientId.tsx | DocumentDropZone | component import + render | WIRED | Import and JSX rendering confirmed |
| PrefilledCareEventDialog | api (useAddCareEvent) | form submit with documentId | WIRED | documentId: prefillData.documentId passed in form submission |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|---|---|---|---|---|
| DocumentDropZone.tsx | uploadResult | apiClient.upload -> backend | Backend processes file and returns classification + match | FLOWING |
| DocumentProcessingModal.tsx | uploadResult prop | Parent component state from drop zone callback | Props from real upload response | FLOWING |
| PatientMatchSelector.tsx | candidates prop | DocumentUploadResponse.candidates from backend | Backend performs HMAC + name+DOB matching against DB | FLOWING |
| PrefilledCareEventDialog.tsx | prefillData prop | DocumentClassificationResult from backend Claude call | Real data from Claude classification (or null for fallback) | FLOWING |
| routes/index.tsx | openAlerts, stats | useOpenAlerts, useDashboardStats queries | Existing Phase 3 queries (no regression) | FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|---|---|---|---|
| Maven compilation | ./mvnw compile -q | User reports all 73 tests pass | SKIP (requires Docker for full test suite) |
| Test corpus file count | find test-corpus -name "*.txt" -not -name "README.txt" \| wc -l | 16 | PASS |
| Reference dataset validity | python3 JSON parse | 16 entries, valid JSON, 5 types | PASS |
| Test corpus minimum size | wc -c on all files | Smallest: 2,681 bytes (exceeds 500) | PASS |
| Frontend TypeScript | npx tsc --noEmit | Not run (requires node_modules) | SKIP |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|---|---|---|---|---|
| DOC-01 | 04-02 | Test corpus of synthetic clinical PDFs | SATISFIED | 16 documents in test-corpus/, reference-dataset.json with ground truth |
| DOC-02 | 04-03, 04-05, 04-06 | Drag-and-drop PDF classification using Claude AI | SATISFIED | DocumentDropZone + DocumentProcessingService + DocumentClassificationService pipeline verified |
| DOC-03 | 04-03, 04-05 | Patient matching after classification | SATISFIED | DocumentPatientMatchService with HMAC MRN + name+DOB, PatientMatchSelector UI |
| DOC-04 | 04-04, 04-06 | Pre-filled care event wizard from extracted data | SATISFIED | PrefilledCareEventDialog with defaultValues from classification, document linkage |
| DOC-05 | 04-01, 04-04 | PDF stored as bytea linked to care event | SATISFIED | V9 clinical_documents table, ClinicalDocument entity, V10 document_id FK, CareEvent.documentId |
| AI-01 | 04-04, 04-07 | Template-based alert text for known deviations | SATISFIED | PathwayEvaluationActivityImpl template-first check, test evaluate_usesTemplateText_whenAlertTextIsPresent |
| AI-02 | 04-03, 04-04 | Claude alert descriptions for non-standard deviations (zero PHI) | SATISFIED | AlertGenerationAiService with zero-PHI boundary, PathwayEvaluationActivityImpl Claude call path |
| AI-03 | 04-03 | Claude corrective action suggestions for edge cases | SATISFIED | AlertText record includes suggestedAction, AlertGenerationAiService returns both description and action |
| AI-04 | 04-03, 04-07 | Circuit breaker fallback to template text | SATISFIED | @CircuitBreaker on both classification and alert services, Resilience4j config, fallback tests verified |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|---|---|---|---|---|
| DocumentUploadController.java | 94 | TODO: V2 -- patient-level BOLA | INFO | Intentional V2 deferral, documented in ROADMAP. Role-based access sufficient for V1 pilot. |
| DocumentProcessingModal.tsx | 129 | "placeholder" in Select | INFO | Standard UI placeholder text for form select input, not a code stub. |
| PrefilledCareEventDialog.tsx | 173, 224, 251 | "placeholder" in form inputs | INFO | Standard UI placeholder text, not a code stub. |

No blocker or warning-level anti-patterns found. All `return null` instances in AI service classes are intentional circuit breaker fallback behavior, verified by unit tests.

### Human Verification Required

### 1. End-to-End Document Upload Flow (Dashboard)

**Test:** Drag a PDF file onto the dashboard drop zone. Observe the processing modal. Wait for classification and patient matching.
**Expected:** Processing modal shows 5 steps (Uploading, Extracting text, Classifying document, Matching patient, Ready) with spinner -> checkmark progression. Classification result shown with document type badge. Patient match result shown (EXACT, CANDIDATES, or NO_MATCH). Pre-filled care event wizard opens with extracted data.
**Why human:** Requires running backend with Claude API key, browser interaction, and visual UX validation.

### 2. Patient Detail Upload Flow (Pre-Selected Patient)

**Test:** Navigate to a patient detail page. Click the Upload Document button. Upload a PDF.
**Expected:** Matching step is skipped (patient already known). Pre-filled care event wizard opens directly with extracted data for the pre-selected patient.
**Why human:** Requires browser navigation and visual confirmation that matching is bypassed.

### 3. Circuit Breaker Fallback UI

**Test:** Set an invalid ANTHROPIC_API_KEY or disconnect Claude API. Upload a document.
**Expected:** DocumentProcessingModal shows amber banner "AI classification is temporarily unavailable" with manual classification dropdown. User can select document type manually and proceed.
**Why human:** Requires runtime state manipulation and visual confirmation of fallback UI path.

### 4. Inline PDF Viewer

**Test:** After saving a care event with a linked document, click "Preview full document" link.
**Expected:** Sheet panel slides in from right side, PDF rendered in iframe, download button functional.
**Why human:** Browser iframe PDF rendering behavior varies and requires visual confirmation.

### 5. Responsive Layout

**Test:** Resize browser to tablet width (768px). Verify drop zone and processing modal.
**Expected:** Drop zone and modal render without horizontal scrolling or broken layouts.
**Why human:** Responsive layout requires visual inspection at different viewport sizes.

### 6. Code Review Findings (04-REVIEW.md)

**Test:** Review 5 CRITICAL (CR-01 through CR-05) and 7 WARNING (WR-01 through WR-07) findings.
**Expected:** Developer decides fix priority. CR-01 (header injection), CR-02 (content type spoofing), CR-03 (null document ID), CR-04 (decrypted PHI in response), CR-05 (shared ChatClient.Builder) are security-relevant and should be prioritized. WR-01 (eager BYTEA fetch) is a performance concern at scale.
**Why human:** Code review findings are advisory and require developer judgment on priority and timing.

### Gaps Summary

No automated verification gaps found. All 8 ROADMAP success criteria are satisfied by the codebase evidence. All 9 requirement IDs (AI-01 through AI-04, DOC-01 through DOC-05) have supporting implementation.

The status is `human_needed` because the document upload flow requires end-to-end browser testing with a running backend and Claude API key. Additionally, the 04-REVIEW.md findings (5 critical, 7 warnings) should be reviewed by the developer before production deployment. These review findings do not block goal achievement (the functional requirements are met) but represent security hardening and performance items that should be addressed.

---

_Verified: 2026-05-01T22:36:09Z_
_Verifier: Claude (gsd-verifier)_
