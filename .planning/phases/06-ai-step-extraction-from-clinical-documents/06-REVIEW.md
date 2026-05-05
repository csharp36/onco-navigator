---
phase: 06-ai-step-extraction-from-clinical-documents
reviewed: 2026-05-04T00:00:00Z
depth: standard
files_reviewed: 24
files_reviewed_list:
  - frontend/src/features/patients/api.ts
  - frontend/src/features/patients/PathwayEditor.tsx
  - frontend/src/features/patients/StepRow.tsx
  - frontend/src/features/patients/types.ts
  - src/main/java/com/onconavigator/ai/config/AiClientConfig.java
  - src/main/java/com/onconavigator/ai/model/ExtractionResult.java
  - src/main/java/com/onconavigator/ai/prompt/ExtractionPrompts.java
  - src/main/java/com/onconavigator/ai/service/StepExtractionService.java
  - src/main/java/com/onconavigator/domain/ClinicalDocument.java
  - src/main/java/com/onconavigator/domain/enums/PathwayStepStatus.java
  - src/main/java/com/onconavigator/domain/PatientPathwayStep.java
  - src/main/java/com/onconavigator/service/DocumentProcessingService.java
  - src/main/java/com/onconavigator/service/PatientPathwayService.java
  - src/main/java/com/onconavigator/service/StepExtractionTriggerService.java
  - src/main/java/com/onconavigator/web/DocumentUploadController.java
  - src/main/java/com/onconavigator/web/dto/DocumentSummaryResponse.java
  - src/main/java/com/onconavigator/web/dto/PathwayStepResponse.java
  - src/main/java/com/onconavigator/web/PatientPathwayController.java
  - src/main/resources/application-local.yml
  - src/main/resources/db/migration/V16__add_rejected_status_and_ai_source.sql
  - src/test/java/com/onconavigator/activity/PathwayEvaluationActivityImplTest.java
  - src/test/java/com/onconavigator/activity/PathwayEvaluationActivityTest.java
  - src/test/java/com/onconavigator/ai/service/StepExtractionServiceTest.java
  - src/test/java/com/onconavigator/service/PatientPathwayServiceConfirmRejectTest.java
findings:
  critical: 4
  warning: 8
  info: 3
  total: 15
status: issues_found
---

# Phase 06: Code Review Report

**Reviewed:** 2026-05-04
**Depth:** standard
**Files Reviewed:** 24
**Status:** issues_found

## Summary

Phase 6 implements AI-driven step extraction from clinical documents: Claude reads uploaded documents, proposes pathway steps, nurses confirm or reject proposals, and the system deduplicates against existing and rejected steps. The overall architecture is sound — feature flag gating (BAA readiness), circuit breaker protection, async decoupling, BOLA checks in the service layer, and zero-PHI log discipline are all present and correct.

Four blockers were found: two security issues (hardcoded cryptographic keys in a committed config file, and a missing BOLA check in `linkDocumentToPatient`), one data-corruption bug (the `@Async @Transactional` combination that will silently swallow transactional context in Spring's default `ThreadLocal`-based transaction management), and one JSON-injection flaw in the hand-rolled JSON builder that bypasses control characters. Eight warnings cover logic correctness gaps and maintainability concerns. Three info items note dead code and style issues.

---

## Critical Issues

### CR-01: Hardcoded cryptographic keys committed to version control

**File:** `src/main/resources/application-local.yml:68-74`
**Issue:** Both the AES-256-GCM column encryption key (`onconavigator.encryption.key`) and the HMAC-SHA256 MRN index key (`onconavigator.hmac.key`) are committed as literal Base64 values in this file. The comment says "PLACEHOLDER — replace with a real key before running" but these are syntactically valid 32-byte keys. They will be used as-is if a developer runs the stack locally without overriding them — and once committed to git history, they cannot be fully revoked without re-encrypting all PHI columns. HIPAA requires that cryptographic keys for ePHI protection not be stored with the data they protect. A key found in a committed config file is effectively compromised.

**Fix:** Replace the literal values with env-var-only references and add a startup guard:
```yaml
onconavigator:
  encryption:
    key: ${ONCO_ENCRYPTION_KEY}   # Required — no default. Fails fast at startup if not set.
  hmac:
    key: ${ONCO_HMAC_KEY}         # Required — no default.
```
Add a `@PostConstruct` check or `@ConfigurationProperties` validation (`@NotBlank`) so the application refuses to start if either value is absent. Update the developer README with `openssl rand -base64 32` instructions. The current git history containing these keys should be treated as compromised; rotate keys and re-encrypt affected columns.

---

### CR-02: `linkDocumentToPatient` lacks ownership and patient existence BOLA check

**File:** `src/main/java/com/onconavigator/web/DocumentUploadController.java:181-202`
**Issue:** The `PATCH /api/documents/{documentId}/link-patient` endpoint re-links any unlinked document to any patient UUID the caller supplies. There is no check that (a) the document is currently unlinked (`doc.getPatient() == null`), and more critically (b) the `patientId` in the request body actually belongs to a patient within the caller's practice. Any authenticated role can call this endpoint and re-link an already-linked document to a completely different patient — overwriting the existing patient association and potentially mixing PHI records across patients. This is a BOLA-class authorization bypass.

Furthermore, after linking, `StepExtractionTriggerService.triggerAsync()` is never called, so a document linked this way never goes through step extraction — an inconsistency with the upload path that will cause silent functional gaps.

**Fix:**
```java
// 1. Guard: only link if document is currently unlinked
if (doc.getPatient() != null) {
    throw new ResponseStatusException(HttpStatus.CONFLICT,
            "Document is already linked to a patient");
}
// 2. After linking, trigger step extraction if extracted text exists
doc.setPatient(patient);
doc = documentRepository.save(doc);
if (doc.getExtractedText() != null && !doc.getExtractedText().isBlank()) {
    stepExtractionTrigger.triggerAsync(doc.getId(), patient.getId(), doc.getExtractedText());
}
```
Also inject `StepExtractionTriggerService` into `DocumentUploadController` or delegate this operation to `DocumentProcessingService` which already has that dependency.

---

### CR-03: `@Async @Transactional` combination is broken — transaction is never started

**File:** `src/main/java/com/onconavigator/service/StepExtractionTriggerService.java:61-63`
**Issue:** `triggerAsync` is annotated with both `@Async` and `@Transactional`. In Spring, `@Transactional` works via a proxy that binds a transaction to the calling thread's `ThreadLocal` context. `@Async` dispatches the method to a different thread (virtual thread in this case). When the calling thread invokes the proxy, it starts a transaction and then immediately hands off execution to a new thread — which has no transaction context. The result: `@Transactional` is effectively ignored on async methods called via self-reference or external proxy invocation when the proxy machinery runs on the original thread.

The comment in the class acknowledges this is intended to run "AFTER the upload transaction commits", but there is no `@Transactional(propagation = REQUIRES_NEW)` or any mechanism to ensure the async thread gets its own transaction. The DB calls inside (`documentRepository.findById`, `documentRepository.save`, `pathwayService.createProposedSteps`) will run without a transaction, relying on auto-commit mode. This means partial failures (e.g., `alreadyCoveredEventTypes` saves but `createProposedSteps` throws) leave the database in a partially updated, inconsistent state with no rollback.

**Fix:** Remove `@Transactional` from `triggerAsync` and push transaction boundaries into the callee services (they already have `@Transactional` on `createProposedSteps`). For the `alreadyCoveredEventTypes` update, extract it into a dedicated `@Transactional` method in a separate service so each logical unit is independently committed:
```java
@Async
// No @Transactional here — each callee manages its own transaction boundary
public void triggerAsync(UUID documentId, UUID patientId, String extractedText) {
    // ...
    // documentUpdateService.persistAlreadyCovered(documentId, types); // own @Transactional
    // pathwayService.createProposedSteps(...);  // already @Transactional
}
```

---

### CR-04: Hand-rolled JSON builder does not escape control characters — JSON injection possible

**File:** `src/main/java/com/onconavigator/service/PatientPathwayService.java:947-950` and `498-513`
**Issue:** The `escapeJson` helper escapes only backslash and double-quote characters:
```java
return s.replace("\\", "\\\\").replace("\"", "\\\"");
```
It does not escape the JSON control characters `\t` (tab, U+0009), `\n` (newline, U+000A), `\r` (carriage return, U+000D), `\b` (backspace, U+0008), or `\f` (form feed, U+000C). A step name containing a literal newline (possible if created via the API with a multi-line name) will produce malformed JSON in `buildExistingStepsContext` and `serializeProposedEdges`. The malformed JSON is then persisted in `proposedEdgesJson` (TEXT column) and later parsed via `ObjectMapper.readTree()` in `activateProposedEdges`. Jackson will throw when parsing, causing the fallback `catch(Exception e)` to silently skip all proposed edges for that step — a silent data loss in edge activation.

The same malformed JSON is also sent to Claude as part of the `existingSteps` context, which may cause Claude's parsing of the context block to fail or misidentify steps.

**Fix:** Replace hand-rolled JSON construction throughout `PatientPathwayService` with Jackson `ObjectMapper`:
```java
// In buildExistingStepsContext:
ObjectMapper mapper = new ObjectMapper();
ArrayNode array = mapper.createArrayNode();
for (PatientPathwayStep step : steps) {
    if (relevantStatus(step)) {
        ObjectNode node = mapper.createObjectNode();
        node.put("stepName", step.getName());  // Jackson handles all escaping
        node.put("eventType", step.getEventType() != null ? step.getEventType().name() : "");
        node.put("status", step.getStatus().name());
        array.add(node);
    }
}
return mapper.writeValueAsString(array);
```
Inject a Spring-managed `ObjectMapper` bean via constructor instead of instantiating `new ObjectMapper()` in `activateProposedEdges` (line 379) — that bypasses any custom Jackson configuration registered globally.

---

## Warnings

### WR-01: `@Async` method called within the same `@Transactional` context — extraction fires before commit

**File:** `src/main/java/com/onconavigator/service/DocumentProcessingService.java:176-178`
**Issue:** `stepExtractionTrigger.triggerAsync(...)` is called at line 177 while the enclosing `@Transactional` method `processUpload` (line 97) has not yet committed. The `ClinicalDocument` entity is saved at line 169 but not yet visible to other transactions. The async task starts in a new thread and immediately calls `documentRepository.findById(documentId)` — this will see the document only if READ COMMITTED isolation or the async thread happens to run after the outer transaction commits. Under load, the async thread can start before the commit, read a stale snapshot, and find no document. This results in `alreadyCoveredEventTypes` being silently dropped.

**Fix:** Use Spring's `TransactionalEventListener` with `AFTER_COMMIT` phase to fire extraction only after the transaction has durably committed:
```java
// In processUpload, publish an event instead of calling triggerAsync directly:
applicationEventPublisher.publishEvent(new DocumentLinkedEvent(doc.getId(), patientId, extractedText));

// In StepExtractionTriggerService:
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Async
public void onDocumentLinked(DocumentLinkedEvent event) { ... }
```

---

### WR-02: `useDocumentAlreadyCovered` uses only the first PROPOSED step's source document

**File:** `frontend/src/features/patients/PathwayEditor.tsx:192-194`
**Issue:** When multiple PROPOSED steps come from different documents (e.g., a patient uploads two clinical documents in sequence), `sourceDocumentId` is taken only from `proposedSteps[0]`. The "Already in pathway" section will show data for the first document only, silently discarding coverage information from all subsequent documents. A nurse reviewing proposals from document 2 will see coverage data from document 1 — potentially misleading clinical context.

**Fix:** Collect the distinct set of source document IDs across all proposed steps and either query each or show a per-document breakdown. At minimum, if multiple distinct source document IDs are present, the current behavior must not silently suppress the others:
```typescript
const sourceDocumentIds = [...new Set(
  steps
    .filter(s => s.status === 'PROPOSED' && s.sourceDocumentId)
    .map(s => s.sourceDocumentId!)
)];
// Show coverage per document, or query the most recent document only with a clear label
```

---

### WR-03: `required` field is hardcoded to `true` regardless of original value in `handleUpdateStep`

**File:** `frontend/src/features/patients/PathwayEditor.tsx:229`
**Issue:** The expression `original?.prerequisiteStepIds !== undefined ? true : true` always evaluates to `true`. This is a ternary with identical branches — the `required` field of the original step is never read. Every step PUT via the inline edit form will override `required` to `true` even if the step was created as non-required (e.g., optional follow-ups). This silently changes the step's `required` attribute on every edit, which can incorrectly affect pathway completion logic and alerting.

**Fix:**
```typescript
required: original?.required ?? true,
```

---

### WR-04: `updateStep` response returns depth=0, sortOrder=0 regardless of actual position

**File:** `src/main/java/com/onconavigator/service/PatientPathwayService.java:213`
**Issue:** `updateStep`, `skipStep`, `unskipStep`, `confirmProposedStep`, and `rejectProposedStep` all return `toStepResponse(step, 0, 0, prereqIds)` with hard-coded depth and sortOrder of 0. These responses are returned as the mutation result to the frontend, which may use them to update the local cache. A step at depth 2 (e.g., three steps deep in the DAG) will appear to have depth 0 in the mutation response, causing incorrect indentation in the `StepRow` branch connector rendering until the next full `getSteps()` query refreshes the cache.

**Fix:** Either run the full `computeTopology` for the updated step and return accurate values, or explicitly invalidate the steps query in the mutation's `onSuccess` callback so the UI re-fetches. The frontend mutation `onSuccess` callbacks in `api.ts` for `useUpdateStep`, `useSkipStep`, etc. already invalidate `pathway-steps`, which triggers a fresh `getSteps()` fetch — this means the stale response value itself may be unused in practice. However the service method's contract is broken: it returns incorrect metadata. At minimum, document the limitation; better to use:
```java
// Run getPrerequisiteIds already called, so we have prereqIds.
// For depth/sortOrder, re-run computeTopology on the full step list:
List<StepWithDepth> ordered = computeTopology(
    stepRepository.findByPathway_Id(step.getPathway().getId()),
    edgeRepository.findByPathway_Id(step.getPathway().getId()));
StepWithDepth swd = ordered.stream()
    .filter(s -> s.step().getId().equals(stepId)).findFirst()
    .orElse(new StepWithDepth(step, 0, 0, prereqIds));
return toStepResponse(swd.step(), swd.depth(), swd.sortOrder(), swd.prerequisiteIds());
```

---

### WR-05: `activateProposedEdges` instantiates `new ObjectMapper()` inside a transaction per call

**File:** `src/main/java/com/onconavigator/service/PatientPathwayService.java:379`
**Issue:** A new `ObjectMapper` instance is created on every call to `activateProposedEdges`. `ObjectMapper` is documented as thread-safe after configuration but heavyweight to construct (it registers serializers, deserializers, and module discovery). Creating it per-transaction is wasteful. More importantly, any global Jackson configuration (custom date serializers, FAIL_ON_UNKNOWN_PROPERTIES settings) applied to the Spring-managed `ObjectMapper` bean will be absent on these locally constructed instances, potentially causing silent deserialization differences between the extraction path and the confirmation path.

**Fix:** Inject the Spring-managed `ObjectMapper` bean via the constructor:
```java
private final ObjectMapper objectMapper;

public PatientPathwayService(..., ObjectMapper objectMapper) {
    ...
    this.objectMapper = objectMapper;
}
```

---

### WR-06: `GRANT ALL` in migration is overly permissive

**File:** `src/main/resources/db/migration/V16__add_rejected_status_and_ai_source.sql:24-25`
**Issue:** `GRANT ALL ON patient_pathway_steps TO onco_app` and `GRANT ALL ON clinical_documents TO onco_app` grant DDL-adjacent privileges (TRUNCATE, REFERENCES, TRIGGER) to the application user in addition to the DML it needs. For a HIPAA system the application account should have minimum required privileges: `SELECT, INSERT, UPDATE, DELETE`. TRUNCATE in particular would allow bulk deletion of all ePHI data if the application account were compromised. The other already-committed migrations likely also used `GRANT ALL` — this should be treated as a pattern to fix.

**Fix:**
```sql
GRANT SELECT, INSERT, UPDATE, DELETE ON patient_pathway_steps TO onco_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON clinical_documents TO onco_app;
```

---

### WR-07: `createStep` sortOrder computation queries DB after save — off-by-zero ambiguity

**File:** `src/main/java/com/onconavigator/service/PatientPathwayService.java:163`
**Issue:** After saving the new step, `sortOrder` is computed as `stepRepository.findByPathway_Id(pathway.getId()).size() - 1`. This count includes the newly saved step, so a pathway with 0 steps before creation returns `sortOrder = 0` (correct). With 5 existing steps it returns `5` — but uses `size() - 1 = 5` which is the 0-indexed last position including the new step (correct). However, this is an extra DB query issued within a transaction that already loaded the pathway. If the transaction isolation level permits phantom reads (READ COMMITTED, which is PostgreSQL's default), a concurrent insert by another thread between the save and this count could produce an incorrect sort order. Additionally this sortOrder value is only used in the `createStep` response — subsequent `getSteps()` calls compute it via Kahn's algorithm, so the wrong value here feeds stale data to the client.

**Fix:** Track the count before the save, or simply omit the extra query and return `0` since the caller will immediately refetch via `invalidateQueries`:
```java
int existingCount = stepRepository.countByPathway_Id(pathway.getId()); // before save
step = stepRepository.save(step);
return toStepResponse(step, 0, existingCount, List.of());
```

---

### WR-08: `staleTime: Infinity` on `useDocumentAlreadyCovered` prevents showing updated coverage after re-upload

**File:** `frontend/src/features/patients/api.ts:231`
**Issue:** `staleTime: Infinity` means the `alreadyCoveredEventTypes` for a document will never be re-fetched once cached. While the comment says "Static data -- document extraction results don't change", this assumption can be violated: if the nurse rejects a step and a second document is uploaded for the same patient, the `alreadyCoveredEventTypes` could technically be updated by a second async extraction run. More critically, if the first extraction fails silently (circuit breaker open) and returns `null`, the cache will hold an empty result permanently. A subsequent page load will show "no coverage" even if extraction succeeds on retry.

`staleTime: Infinity` is appropriate only for truly immutable data. For data that is populated asynchronously after document upload, `staleTime` should be finite or at least bounded so the UI can recover from extraction failures.

**Fix:**
```typescript
staleTime: 5 * 60 * 1000,  // 5 minutes — covers the typical async extraction window
```

---

## Info

### IN-01: Dead ternary in `handleUpdateStep` — `prerequisiteStepIds` is always defined

**File:** `frontend/src/features/patients/PathwayEditor.tsx:229`
**Issue:** `original?.prerequisiteStepIds !== undefined ? true : true` — the `?.` optional chain is unnecessary because `original` can only be `undefined` if a step with that `stepId` is not found in `steps`, which cannot happen since `handleUpdateStep` is only called from `onSave` in `InlineStepEdit` which always has a valid `step` prop. This is dead code that obscures intent and was noted in WR-03. The fix for WR-03 resolves this as well.

---

### IN-02: `CARE_COORDINATOR` role can confirm and reject AI-proposed steps via UI but not API

**File:** `src/main/java/com/onconavigator/web/PatientPathwayController.java:185` and `207`; `frontend/src/features/patients/PathwayEditor.tsx:325-329`
**Issue:** The `/confirm` and `/reject` endpoints restrict to `NURSE_NAVIGATOR` and `ADMIN` only (by design — see controller Javadoc). However, `PathwayEditor.tsx` passes `onConfirm` and `onReject` callbacks to `StepRow` for all `PROPOSED` steps regardless of the caller's role, because the frontend does not gate on role. A `CARE_COORDINATOR` user will see the Confirm/Reject buttons, click them, and receive a 403 — with no user-facing error display because `confirmStep.isError` is not rendered anywhere in `PathwayEditor` for the confirm action (only `rejectStep.isError` is shown). This is a UX defect rather than a security gap (the API correctly rejects), but creates a confusing experience.

**Fix:** Either read the user's role from the JWT/auth context and conditionally pass `onConfirm`/`onReject` as `undefined` for `CARE_COORDINATOR`, or display an error toast when `confirmStep.isError` is true.

---

### IN-03: `REJECTED` and `SKIPPED` steps render identical icons

**File:** `frontend/src/features/patients/StepRow.tsx:59-72`
**Issue:** Both `REJECTED` and `SKIPPED` steps render a `MinusCircle` icon with identical styling (`h-5 w-5 text-muted-foreground shrink-0`). The aria-labels are different (`"Rejected"` vs `"Skipped"`) but the visual presentation is identical. In the collapsed "Show rejected steps" section, a nurse cannot distinguish a rejected AI proposal from a skipped step at a glance. Since these have different clinical meanings (rejected = AI was wrong; skipped = clinically intentional bypass), distinct iconography would improve clinical clarity.

**Fix:** Use a different icon for `REJECTED`, such as `XCircle` (already imported as `X` in the file), to visually distinguish rejected AI proposals from skipped steps.

---

_Reviewed: 2026-05-04_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
