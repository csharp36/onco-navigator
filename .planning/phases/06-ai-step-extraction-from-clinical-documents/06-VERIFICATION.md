---
phase: 06-ai-step-extraction-from-clinical-documents
verified: 2026-05-04T00:00:00Z
status: human_needed
score: 6/6 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Upload a clinical PDF with a linked patient and step-extraction.enabled=true, then check that proposed steps appear in the patient pathway UI with AI Proposed badge and source filename"
    expected: "PROPOSED steps appear in the pathway editor with dashed border, AI Proposed badge, and source document filename. Confirm/Edit/Reject buttons are visible. No ACTIVE steps are auto-created."
    why_human: "Requires a running Docker Compose stack with Anthropic API key, real or synthetic document text, and browser interaction to verify the full upload -> async extraction -> UI render pipeline."
  - test: "Confirm a PROPOSED step and verify DAG evaluation is triggered"
    expected: "Step transitions to ACTIVE. Pathway evaluation fires immediately (Temporal signal). Any time-window alerts recalculate. The confirmed step is no longer shown as PROPOSED."
    why_human: "Requires Temporal server running, workflow in-flight, and ability to inspect the Temporal UI or log output to confirm the pathwayStepsChanged signal was received and re-evaluation occurred."
  - test: "Reject a PROPOSED step, verify it disappears from main pathway view, and re-uploading a document does not re-propose the same event type"
    expected: "Rejected step moves to the 'Show N rejected steps' collapsible toggle. After rejecting SURGERY, a second document upload mentioning surgery does not create a new PROPOSED SURGERY step."
    why_human: "Requires two sequential document uploads and pathway state inspection; cannot be verified by static analysis."
  - test: "Verify 'Already in pathway' section renders when a document mentions already-tracked event types"
    expected: "When alreadyCoveredEventTypes is non-null for the source document, the 'Already in pathway' Card section appears in PathwayEditor above or below the step list."
    why_human: "Requires running extraction with a document that mentions events already tracked, then inspecting the browser UI to confirm the D-10 transparency card renders."
---

# Phase 6: AI Step Extraction from Clinical Documents — Verification Report

**Phase Goal:** When a clinical document is uploaded for a patient, Claude AI extracts ordered/planned care events and proposes them as new pathway steps. A nurse must confirm before steps become active.
**Verified:** 2026-05-04
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | After document classification and patient matching, the system calls Claude to extract pathway-relevant events from the document text | ✓ VERIFIED | `DocumentProcessingService` calls `stepExtractionTrigger.triggerAsync(doc.getId(), doc.getPatient().getId(), extraction.text)` after `doc.save()` when patient linked. `StepExtractionTriggerService` calls `extractionService.extractSteps()` which calls `stepExtractionClient.prompt()...entity(ExtractionResult.class)`. Full call chain wired. |
| 2 | Extracted steps appear in the patient's pathway as PROPOSED with source=AI_EXTRACTED and a link to the source document | ✓ VERIFIED | `createProposedSteps()` in `PatientPathwayService` sets `step.setStatus(PathwayStepStatus.PROPOSED)`, `step.setSource("AI_EXTRACTED")`, `step.setSourceDocumentId(documentId)`. `toStepResponse()` performs `documentRepository.findById(step.getSourceDocumentId()).map(doc -> doc.getOriginalFilename()).orElse(null)` to populate `sourceDocumentFilename`. TypeScript `PathwayStepStatus` interface includes all three fields. `StepRow.tsx` renders source filename below step name. |
| 3 | A nurse can confirm or reject each proposed step from the patient detail page | ✓ VERIFIED | `POST /patients/{id}/pathway/steps/{stepId}/confirm` and `PATCH /patients/{id}/pathway/steps/{stepId}/reject` exist in `PatientPathwayController`. Frontend has `useConfirmStep` and `useRejectStep` TanStack Query mutation hooks wired to `StepRow` Confirm/Edit/Reject buttons via `PathwayEditor` callbacks. |
| 4 | Confirmed steps become active in the DAG evaluation; rejected steps are excluded | ✓ VERIFIED | `confirmProposedStep()` sets `step.setStatus(PathwayStepStatus.ACTIVE)`, activates proposed edges via `activateProposedEdges()` with cycle detection, then calls `pathwayService.signalPathwayStepsChanged(patientId)`. `rejectProposedStep()` sets `step.setStatus(PathwayStepStatus.REJECTED)` (soft delete — excluded from dedup as REJECTED). `PathwayEditor` filters `visibleSteps = steps.filter(s => s.status !== 'REJECTED')`. |
| 5 | The system never auto-confirms AI-extracted steps — human-in-the-loop is non-negotiable | ✓ VERIFIED | `createProposedSteps()` only sets `PathwayStepStatus.PROPOSED` — there is no code path from AI extraction to `ACTIVE` without an explicit nurse action. `confirmProposedStep()` is the only method that transitions PROPOSED to ACTIVE and requires `@PreAuthorize("hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')")` with an explicit HTTP request. Test `confirmProposedStep_activeStep_throws409` verifies the 409 guard. Feature flag (`step-extraction.enabled=false` default) additionally prevents Claude API calls until BAA is signed. |
| 6 | A new pathwayStepsChanged Temporal signal triggers re-evaluation when steps are confirmed | ✓ VERIFIED | `PatientPathwayService.signalPathwayStepsChanged()` delegates to `PathwayService.signalPathwayStepsChanged()` which calls `workflow.pathwayStepsChanged()` on a `PatientPathwayWorkflow` stub. `PatientPathwayWorkflow.pathwayStepsChanged()` is a `@SignalMethod`. `PatientPathwayWorkflowImpl.pathwayStepsChanged()` sets `signalReceived = true` triggering the main evaluation loop. This same signal is fired by both `confirmProposedStep()` and `rejectProposedStep()`. |

**Score:** 6/6 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/resources/db/migration/V16__add_rejected_status_and_ai_source.sql` | Schema changes for Phase 6 | ✓ VERIFIED | Contains `ALTER TYPE pathway_step_status ADD VALUE IF NOT EXISTS 'REJECTED'`, all four `ADD COLUMN IF NOT EXISTS` statements, partial index, GRANTs |
| `src/main/java/com/onconavigator/domain/enums/PathwayStepStatus.java` | REJECTED as 5th enum value | ✓ VERIFIED | `ACTIVE, PROPOSED, COMPLETED, SKIPPED, REJECTED` all present with D-07/D-09 Javadoc |
| `src/main/java/com/onconavigator/domain/PatientPathwayStep.java` | source, sourceDocumentId, proposedEdgesJson fields | ✓ VERIFIED | All three fields with getters/setters present |
| `src/main/java/com/onconavigator/domain/ClinicalDocument.java` | alreadyCoveredEventTypes field | ✓ VERIFIED | Field, getter, setter present with D-10 Javadoc |
| `src/main/java/com/onconavigator/web/dto/PathwayStepResponse.java` | 20-param record with source fields | ✓ VERIFIED | `UUID sourceDocumentId`, `String extractionSource`, `String sourceDocumentFilename` are the 18th–20th parameters |
| `src/main/java/com/onconavigator/ai/model/ExtractionResult.java` | Structured output record for Claude | ✓ VERIFIED | Top-level record with `ProposedStep` and `ProposedEdge` inner records; `String eventType` (not enum) per Pitfall 5; all required `@JsonProperty(required=true)` annotations |
| `src/main/java/com/onconavigator/ai/prompt/ExtractionPrompts.java` | SYSTEM_PROMPT and USER_TEMPLATE | ✓ VERIFIED | `SYSTEM_PROMPT` contains all 12 `CareEventType` values; `USER_TEMPLATE` has `{documentText}` and `{existingSteps}` placeholders |
| `src/main/java/com/onconavigator/ai/config/AiClientConfig.java` | stepExtractionClient @Bean | ✓ VERIFIED | Third `@Bean` method present with `ExtractionPrompts.SYSTEM_PROMPT`, `temperature(0.1)`, `maxTokens(2000)` |
| `src/main/resources/application-local.yml` | step-extraction feature flag | ✓ VERIFIED | `step-extraction.enabled: ${ONCO_AI_STEP_EXTRACTION_ENABLED:false}` present as sibling to `document-classification` |
| `src/main/java/com/onconavigator/ai/service/StepExtractionService.java` | Claude call with circuit breaker | ✓ VERIFIED | `@CircuitBreaker(name="claude-api", fallbackMethod="extractFallback")`, `@Qualifier("stepExtractionClient")`, `@Value` feature flag, `isValidCareEventType()` validation, `truncateToTokenBudget()`, `extractFallback()` all present |
| `src/main/java/com/onconavigator/service/StepExtractionTriggerService.java` | Async orchestrator | ✓ VERIFIED | `@Async @Transactional triggerAsync()` calls extraction service, persists `alreadyCoveredEventTypes` on `ClinicalDocument`, calls `createProposedSteps()` and `signalPathwayStepsChanged()` |
| `src/main/java/com/onconavigator/service/PatientPathwayService.java` | 4 new methods | ✓ VERIFIED | `buildExistingStepsContext()`, `createProposedSteps()`, `confirmProposedStep()`, `rejectProposedStep()`, `activateProposedEdges()`, `signalPathwayStepsChanged()` delegation all present |
| `src/main/java/com/onconavigator/service/DocumentProcessingService.java` | Extraction trigger hook | ✓ VERIFIED | `StepExtractionTriggerService` field injected via constructor; `stepExtractionTrigger.triggerAsync()` called after `doc.save()` when patient linked and text non-blank |
| `src/main/java/com/onconavigator/web/PatientPathwayController.java` | Confirm/reject endpoints | ✓ VERIFIED | `@PostMapping("/steps/{stepId}/confirm")` with `@PreAuthorize("hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')")` and `@PatchMapping("/steps/{stepId}/reject")` with same auth — CARE_COORDINATOR excluded |
| `src/main/java/com/onconavigator/web/dto/DocumentSummaryResponse.java` | alreadyCoveredEventTypes field | ✓ VERIFIED | 9th record parameter `String alreadyCoveredEventTypes` present with D-10 Javadoc |
| `src/main/java/com/onconavigator/web/DocumentUploadController.java` | Updated constructor call sites | ✓ VERIFIED | All 3 call sites (`getDocument`, `linkDocumentToPatient`, `getDocumentsForPatient`) pass `doc.getAlreadyCoveredEventTypes()` as 9th argument |
| `frontend/src/features/patients/types.ts` | REJECTED in enum; source fields | ✓ VERIFIED | `PathwayStepStatusEnum` has `'REJECTED'`; both `PathwayStepStatus` and `PatientPathwayStep` interfaces have `sourceDocumentId`, `extractionSource`, `sourceDocumentFilename` |
| `frontend/src/features/patients/api.ts` | Confirm/reject/already-covered hooks | ✓ VERIFIED | `useConfirmStep` (POST), `useRejectStep` (PATCH), `useDocumentAlreadyCovered` (GET, `staleTime: Infinity`) all present with correct query invalidation |
| `frontend/src/features/patients/StepRow.tsx` | Confirm/Edit/Reject for PROPOSED | ✓ VERIFIED | PROPOSED block renders Confirm (Check icon, `variant="default"`), Edit (Pencil), Reject (X, destructive). Trash2 is NOT in the PROPOSED block — only in ACTIVE/COMPLETED/SKIPPED. REJECTED case in `PathwayStepIcon` (MinusCircle) and `stepNameClass` (line-through). Dashed border conditional on PROPOSED. AI Proposed Badge present. Source filename line present. |
| `frontend/src/features/patients/PathwayEditor.tsx` | Already-covered, rejected toggle, InlineStepEdit guard | ✓ VERIFIED | `useConfirmStep`, `useRejectStep`, `useDocumentAlreadyCovered` imported and initialized. `visibleSteps`/`rejectedSteps` split. Collapsible toggle for rejected steps. "Already in pathway" Card rendered when `alreadyCoveredTypes.length > 0`. InlineStepEdit guard updated to `step.status === 'ACTIVE' \|\| step.status === 'PROPOSED'`. Reject dialog present. |
| `src/test/java/com/onconavigator/ai/service/StepExtractionServiceTest.java` | 7 unit tests | ✓ VERIFIED | Tests present: feature flag disabled, blank text, null text, filters invalid CareEventType, null result, fallback, structural PHI check |
| `src/test/java/com/onconavigator/service/PatientPathwayServiceConfirmRejectTest.java` | 8 unit tests | ✓ VERIFIED | Tests present: confirm PROPOSED→ACTIVE, confirm non-PROPOSED throws 409, confirm COMPLETED throws 409, reject PROPOSED→REJECTED (proposedEdgesJson cleared), reject COMPLETED throws 409, dedup against ACTIVE/COMPLETED/REJECTED, sets PROPOSED+AI_EXTRACTED+sourceDocumentId, empty result no saves |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `DocumentProcessingService.processUpload` | `StepExtractionTriggerService.triggerAsync` | Fire-and-forget after `doc.save()` when patient linked and text non-blank | ✓ WIRED | Line 177 in DocumentProcessingService |
| `StepExtractionTriggerService.triggerAsync` | `StepExtractionService.extractSteps` | `extractionService.extractSteps(documentId, extractedText, existingStepsContext)` | ✓ WIRED | Line 67 in StepExtractionTriggerService |
| `StepExtractionTriggerService.triggerAsync` | `PatientPathwayService.createProposedSteps` | `pathwayService.createProposedSteps(patientId, documentId, result)` | ✓ WIRED | Line 79 in StepExtractionTriggerService |
| `StepExtractionTriggerService.triggerAsync` | `ClinicalDocument.setAlreadyCoveredEventTypes` | `doc.setAlreadyCoveredEventTypes(String.join(",", result.alreadyCoveredEventTypes()))` | ✓ WIRED | Lines 71-75 in StepExtractionTriggerService |
| `PatientPathwayController.confirmStep` | `PatientPathwayService.confirmProposedStep` | `patientPathwayService.confirmProposedStep(patientId, stepId, actorId)` | ✓ WIRED | Line 191 in PatientPathwayController |
| `PatientPathwayService.confirmProposedStep` | `PathwayService.signalPathwayStepsChanged` | Delegation via `PatientPathwayService.signalPathwayStepsChanged()` → `pathwayService.signalPathwayStepsChanged()` → `workflow.pathwayStepsChanged()` | ✓ WIRED | Lines 343, 477-478 in PatientPathwayService; lines 119-127 in PathwayService |
| `PatientPathwayService.toStepResponse` | `ClinicalDocumentRepository.findById` | `documentRepository.findById(step.getSourceDocumentId()).map(doc -> doc.getOriginalFilename()).orElse(null)` | ✓ WIRED | Confirmed by SUMMARY 06-03 — null placeholder replaced with real PK lookup |
| `StepRow onConfirm` | `useConfirmStep` | `PathwayEditor` passes `() => confirmStep.mutate(step.stepId)` as `onConfirm` callback | ✓ WIRED | Confirmed in PathwayEditor.tsx |
| `StepRow onReject` | `useRejectStep` | `PathwayEditor` opens reject dialog then calls `rejectStep.mutate(rejectingStepId)` | ✓ WIRED | Confirmed in PathwayEditor.tsx |
| `useDocumentAlreadyCovered` | `DocumentSummaryResponse.alreadyCoveredEventTypes` | `GET /api/documents/{documentId}` → `getDocument()` → `doc.getAlreadyCoveredEventTypes()` | ✓ WIRED | DocumentUploadController line 169-173 |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `StepExtractionService` | `ExtractionResult result` | `stepExtractionClient.prompt()...entity(ExtractionResult.class)` | Yes — live Anthropic API call; guarded by feature flag | ✓ FLOWING (when enabled) |
| `PatientPathwayService.createProposedSteps` | `existingEventTypes` | `stepRepository.findByPathway_Id(pathway.getId())` | Yes — real DB query | ✓ FLOWING |
| `PatientPathwayService.toStepResponse` | `sourceDocumentFilename` | `documentRepository.findById(step.getSourceDocumentId())` | Yes — PK lookup on ClinicalDocument | ✓ FLOWING |
| `PathwayEditor` / `StepRow` | `alreadyCoveredTypes` | `useDocumentAlreadyCovered` → `GET /api/documents/{documentId}` → `doc.getAlreadyCoveredEventTypes()` | Yes — populated by `StepExtractionTriggerService.triggerAsync()` | ✓ FLOWING |
| `PathwayEditor` | `visibleSteps` / `rejectedSteps` | `steps` from `usePathwayStatus` (TanStack Query) | Yes — existing pathway query | ✓ FLOWING |

### Behavioral Spot-Checks

Step 7b: SKIPPED — the extraction pipeline requires a running Anthropic API (external service) and Temporal server. Static code analysis and unit test verification substitutes.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| PW-ALL-002 | All 5 plans | Events extracted from documents — each patient needs unique sequence, AI extraction model | ✓ SATISFIED | Full async extraction pipeline: document upload → Claude extraction → PROPOSED steps. Steps are per-patient, not shared templates. Source tracking (sourceDocumentId, extractionSource) connects each step to its source document. |
| PW-BR-001 | All 5 plans | Steps from MD notes/orders/nurse notes — steps not from pre-defined list | ✓ SATISFIED | AI extraction proposes steps from clinical document content (not from a fixed template). Nurse confirms before activation. `ExtractionPrompts.SYSTEM_PROMPT` instructs Claude to extract only explicitly ordered/planned events from the document text. |

Note: `PW-ALL-002` and `PW-BR-001` are domain-level requirements from the oncologist clinical review (defined in `05-RESEARCH.md` and `06-CONTEXT.md`), not v1 product requirement IDs. They do not appear in `/planning/REQUIREMENTS.md` (which uses a different ID scheme). This was noted in the Phase 5 VERIFICATION.md. No orphaned REQUIREMENTS.md IDs for this phase.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `StepExtractionService.java` | ~71 | Feature flag disabled returns null | INFO | Intentional design — PHI transmission gated until BAA signed (T-06-01). Not a stub. |
| `PatientPathwayService.java` | ~535 | Comment typo: `/ Build dedup set` (missing `/` for `//`) | INFO | Minor — cosmetic comment syntax issue, not a code defect. No functional impact. |
| `PathwayEditor.tsx` | ~172 | Comment typo: `/ Phase 6: reject dialog state` (missing `/` for `//`) | INFO | Minor — cosmetic. No functional impact. |
| `PatientPathwayService.java` | ~162 | Comment typo: `/ New root step:` | INFO | Minor — cosmetic. No functional impact. |

No blockers. No PLACEHOLDER/TODO patterns. No hollow data sources. The feature flag `enabled=false` default is intentional security architecture (HIPAA/BAA compliance), not a stub.

### Human Verification Required

#### 1. End-to-End Document Upload → Proposed Steps Render

**Test:** Upload a synthetic clinical PDF (e.g., a test corpus document from `src/test/resources/test-corpus/`) for a patient with a linked pathway. Set `ONCO_AI_STEP_EXTRACTION_ENABLED=true` in Docker Compose environment. Wait 5-10 seconds for async extraction to complete. Navigate to the patient's pathway editor.
**Expected:** One or more PROPOSED steps appear with AI Proposed badge (outlined), dashed border, source document filename below the step name, and Confirm/Edit/Reject buttons. No ACTIVE steps are auto-created.
**Why human:** Requires Anthropic API key, running Docker Compose stack (PostgreSQL + Temporal + Spring Boot + React), and browser interaction to verify the full pipeline.

#### 2. Confirm Step → Temporal Re-evaluation

**Test:** Click the Confirm button on a PROPOSED step. Inspect the Temporal UI (http://localhost:8080) for the patient's workflow run to verify a `pathwayStepsChanged` signal event appears in the workflow history.
**Expected:** Step transitions to ACTIVE status in the UI. Temporal workflow history shows a `pathwayStepsChanged` signal event received after the confirm action. Pathway status recalculates (e.g., time-window alerts update if applicable).
**Why human:** Requires Temporal server running and workflow in-flight; Temporal UI or log inspection needed to confirm signal delivery and re-evaluation.

#### 3. Reject Step → Hidden + Re-proposal Prevention

**Test:** (a) Click Reject on a PROPOSED step. Confirm the dialog. Verify the step moves to the "Show N rejected step(s)" collapsible. (b) Upload a second document mentioning the same event type (e.g., SURGERY). Verify no new PROPOSED SURGERY step is created.
**Expected:** (a) Rejected step hidden from main list; toggle shows it. (b) Second extraction deduplicates against the REJECTED step — no re-proposal.
**Why human:** Requires two sequential document uploads and inspection of pathway state; dedup behavior against REJECTED steps is confirmed by unit tests but the full pipeline needs end-to-end validation.

#### 4. "Already in pathway" Section (D-10)

**Test:** Upload a document that mentions event types already tracked in the patient's pathway (e.g., a patient with SURGERY step already ACTIVE; upload a document that also mentions surgery). After extraction completes, inspect the PathwayEditor.
**Expected:** "Already in pathway" Card section appears above/below the step list listing the already-tracked event types Claude found (e.g., "surgery"). The text should read: "The document also mentioned these care events, which are already tracked: surgery."
**Why human:** Requires extraction to complete and `alreadyCoveredEventTypes` to be populated on the `ClinicalDocument`. UI rendering of the Card depends on live data.

---

## Gaps Summary

No gaps. All 6 success criteria are verified in the codebase. The 4 human verification items require a running environment and cannot be verified by static analysis alone — they do not indicate code defects or missing implementations.

**The phase goal is structurally achieved.** The full pipeline exists and is wired: document upload triggers async Claude extraction, steps are proposed as PROPOSED with source tracking, nurses can confirm (PROPOSED→ACTIVE with Temporal signal) or reject (PROPOSED→REJECTED, deduped on re-upload), the UI renders Confirm/Edit/Reject buttons, and rejected steps are hidden with a collapsible toggle. Human verification confirms the runtime behavior.

---

_Verified: 2026-05-04T00:00:00Z_
_Verifier: Claude (gsd-verifier)_
