---
phase: 04-ai-document-ingestion
reviewed: 2026-05-01T14:30:00Z
depth: standard
files_reviewed: 42
files_reviewed_list:
  - Dockerfile
  - frontend/src/components/ui/progress.tsx
  - frontend/src/features/documents/api.ts
  - frontend/src/features/documents/DocumentDropZone.tsx
  - frontend/src/features/documents/DocumentPreviewPanel.tsx
  - frontend/src/features/documents/DocumentProcessingModal.tsx
  - frontend/src/features/documents/PatientMatchSelector.tsx
  - frontend/src/features/documents/PrefilledCareEventDialog.tsx
  - frontend/src/features/documents/types.ts
  - frontend/src/features/patients/types.ts
  - frontend/src/lib/api-client.ts
  - frontend/src/routes/index.tsx
  - frontend/src/routes/patients/$patientId.tsx
  - pom.xml
  - src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java
  - src/main/java/com/onconavigator/ai/config/AiClientConfig.java
  - src/main/java/com/onconavigator/ai/model/AlertText.java
  - src/main/java/com/onconavigator/ai/model/DocumentClassification.java
  - src/main/java/com/onconavigator/ai/prompt/AlertPrompts.java
  - src/main/java/com/onconavigator/ai/prompt/ClassificationPrompts.java
  - src/main/java/com/onconavigator/ai/service/AlertGenerationAiService.java
  - src/main/java/com/onconavigator/ai/service/ClaudeVisionService.java
  - src/main/java/com/onconavigator/ai/service/DocumentClassificationService.java
  - src/main/java/com/onconavigator/domain/CareEvent.java
  - src/main/java/com/onconavigator/domain/ClinicalDocument.java
  - src/main/java/com/onconavigator/repository/ClinicalDocumentRepository.java
  - src/main/java/com/onconavigator/service/DocumentPatientMatchService.java
  - src/main/java/com/onconavigator/service/DocumentProcessingService.java
  - src/main/java/com/onconavigator/service/OcrExtractionService.java
  - src/main/java/com/onconavigator/service/PatientService.java
  - src/main/java/com/onconavigator/service/PdfExtractionService.java
  - src/main/java/com/onconavigator/web/DocumentUploadController.java
  - src/main/java/com/onconavigator/web/dto/CreateCareEventRequest.java
  - src/main/java/com/onconavigator/web/dto/DocumentSummaryResponse.java
  - src/main/java/com/onconavigator/web/dto/DocumentUploadResponse.java
  - src/main/resources/application-local.yml
  - src/main/resources/db/migration/V9__create_clinical_documents.sql
  - src/main/resources/db/migration/V10__add_document_id_to_care_events.sql
  - src/test/java/com/onconavigator/activity/PathwayEvaluationActivityImplTest.java
  - src/test/java/com/onconavigator/activity/PathwayEvaluationActivityTest.java
  - src/test/java/com/onconavigator/ai/service/AlertGenerationAiServiceTest.java
  - src/test/java/com/onconavigator/ai/service/DocumentClassificationServiceTest.java
  - src/test/java/com/onconavigator/service/DocumentPatientMatchServiceTest.java
  - src/test/java/com/onconavigator/service/PdfExtractionServiceTest.java
findings:
  critical: 5
  warning: 7
  info: 0
  total: 12
status: issues_found
---

# Phase 4: Code Review Report

**Reviewed:** 2026-05-01T14:30:00Z
**Depth:** standard
**Files Reviewed:** 42
**Status:** issues_found

## Summary

The Phase 4 AI Document Ingestion implementation is well-structured with strong HIPAA PHI protections, zero-PHI boundary enforcement for the alert generation AI path, proper `@Audited` annotations on all ePHI entities, and circuit breaker fault tolerance. However, the review identified 5 critical issues and 7 warnings that must be addressed before shipping.

Key concerns: (1) HTTP header injection via unsanitized filenames in Content-Disposition, (2) Content-Type spoofing allowing XSS via stored content type, (3) null document ID returned to frontend when patient match fails, (4) shared ChatClient.Builder mutating system prompt state across beans, and (5) PHI leakage in `DocumentUploadResponse` returning decrypted MRN and DOB to the frontend API.

## Critical Issues

### CR-01: HTTP Header Injection via Unsanitized Filename in Content-Disposition

**File:** `src/main/java/com/onconavigator/web/DocumentUploadController.java:125-126`
**Issue:** The `originalFilename` from the database is interpolated directly into the `Content-Disposition` header without sanitization. A malicious user could upload a file named `evil.pdf\r\nX-Injected: true` or `evil.pdf"; filename*=UTF-8''../../etc/passwd` to inject arbitrary HTTP headers or perform response splitting. In a HIPAA system, header injection can enable session fixation, cache poisoning, or cross-site scripting.

**Fix:**
```java
// Sanitize filename: strip path separators, newlines, and double-quotes
String safeFilename = doc.getOriginalFilename()
        .replaceAll("[\\r\\n\"\\\\/:*?<>|]", "_");
return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(doc.getContentType()))
        .header(HttpHeaders.CONTENT_DISPOSITION,
                ContentDisposition.inline()
                        .filename(safeFilename)
                        .build()
                        .toString())
        .body(doc.getContent());
```

Use Spring's `ContentDisposition` builder which handles RFC 6266 encoding and prevents injection.

### CR-02: Content-Type Spoofing Enables Stored XSS via Document Content Endpoint

**File:** `src/main/java/com/onconavigator/web/DocumentUploadController.java:124`
**Issue:** The content type used in the response is read directly from `doc.getContentType()`, which was stored from `file.getContentType()` at upload time. The client-provided Content-Type is accepted and stored without server-side validation against the actual file magic bytes. While `DocumentProcessingService.validateFile()` checks the content type string, it trusts the client-declared MIME type -- a renamed HTML file with `Content-Type: text/html` would pass validation if not in `ALLOWED_CONTENT_TYPES`, but more critically: the validation checks the string but does not verify file magic bytes. An attacker could send a file with `Content-Type: application/pdf` in the multipart header but actual HTML content. When served back via the content endpoint, the browser would render it as PDF (safe), but if the validation were loosened or content type were manipulated in the DB, XSS becomes possible. More importantly, the `MediaType.parseMediaType()` call will throw `InvalidMediaTypeException` on malformed content types, causing 500 errors.

**Fix:**
```java
// Validate stored content type before serving
private static final Set<String> SAFE_CONTENT_TYPES = Set.of(
        "application/pdf", "image/jpeg", "image/png");

String contentType = doc.getContentType();
if (!SAFE_CONTENT_TYPES.contains(contentType)) {
    contentType = "application/octet-stream"; // Force download for unknown types
}
return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(contentType))
        .header("X-Content-Type-Options", "nosniff")
        // ... rest of response
```

Additionally, add the `X-Content-Type-Options: nosniff` header to prevent MIME sniffing, and validate file magic bytes at upload time.

### CR-03: Null Document ID Returned When Document Is Not Persisted

**File:** `src/main/java/com/onconavigator/service/DocumentProcessingService.java:163-180`
**Issue:** When `doc.getPatient()` is null (no patient match found and no pre-selected patient), the document is not saved to the database. However, `doc.getId()` is still included in the `DocumentUploadResponse`. With `@GeneratedValue(strategy = GenerationType.UUID)`, Hibernate 6 generates the UUID during `persist()` -- if `save()` is never called, `getId()` returns `null`. The frontend then receives `documentId: null` and subsequently passes this null ID to `getDocumentContentUrl()`, `PrefilledCareEventDialog`, and the `createCareEvent` mutation's `documentId` field. This causes NullPointerExceptions or 404 errors in downstream API calls.

**Fix:**
```java
// Option A: Always persist, using a sentinel "unlinked" state
// (requires making patient_id nullable in V9 migration)

// Option B: Generate UUID manually before conditional persist
doc.setId(UUID.randomUUID());  // Ensure ID exists even if not persisted

// Option C (preferred): Persist always with a transient patient reference,
// and add a "link patient" endpoint for later association
if (doc.getPatient() == null) {
    // Return error status indicating patient selection is needed
    // but don't return a documentId that doesn't exist in the DB
    return new DocumentUploadResponse(
            null, // documentId is null -- frontend must handle this
            classification,
            matchStatus,
            candidates,
            matchedPatientId
    );
}
```

The frontend must also handle `documentId === null` in `DocumentProcessingModal` and `PrefilledCareEventDialog` by disabling document preview and not passing `documentId` to care event creation.

### CR-04: Decrypted PHI (MRN, DOB, Display Name) Returned in Upload API Response

**File:** `src/main/java/com/onconavigator/service/DocumentPatientMatchService.java:108-111`
**Issue:** The `PatientCandidate` record in `DocumentUploadResponse` includes `displayName` (full patient name), `mrn` (decrypted MRN), and `dateOfBirth` (decrypted DOB) -- all HIPAA-regulated PHI. This data is returned in the HTTP response body of `/api/documents/upload`. While the endpoint requires JWT auth and TLS, this violates the principle of PHI minimization: the upload response transmits more PHI than necessary for the matching UI. The `PatientMatchSelector.tsx` component renders MRN and DOB directly in the UI, which is appropriate for the nurse navigator, but the API should not include the full plaintext MRN in a response that also contains document classification data (widening the attack surface if the response is logged by a proxy, cached, or leaked).

**Fix:**
```java
// In DocumentPatientMatchService.matchByNameAndDob():
// Mask the MRN (show last 4 digits only) and return only the display
// information strictly needed for the matching UI
String maskedMrn = patient.getMrn().length() > 4
        ? "***" + patient.getMrn().substring(patient.getMrn().length() - 4)
        : "****";
candidates.add(new PatientCandidate(
        patient.getId(),
        patientFullName,
        maskedMrn,          // Masked, not full MRN
        patient.getDateOfBirth(),
        confidence
));
```

### CR-05: Shared ChatClient.Builder Mutates System Prompt for Both AI Beans

**File:** `src/main/java/com/onconavigator/ai/config/AiClientConfig.java:32-51`
**Issue:** Both `@Bean` methods inject the same `ChatClient.Builder` instance. In Spring AI 1.1.x, `ChatClient.Builder` is auto-configured as a singleton bean. The `documentClassificationClient` bean calls `builder.defaultSystem(ClassificationPrompts.SYSTEM_PROMPT)` which mutates the builder state. When `alertGenerationClient` then calls `builder.defaultSystem(AlertPrompts.SYSTEM_PROMPT)` on the same builder, it overwrites the previous system prompt. Critically, because Spring may invoke bean methods in any order, both ChatClient instances could end up with the SAME system prompt (whichever was set last), or the first one built gets the wrong prompt after the builder is mutated for the second.

This means the classification client could receive the alert generation system prompt, causing incorrect document classification behavior, or the alert generation client could receive the classification prompt, causing malformed alert text.

**Fix:**
```java
@Bean
ChatClient documentClassificationClient(ChatClient.Builder builder) {
    return builder.clone()  // Clone the builder to avoid shared state
            .defaultSystem(ClassificationPrompts.SYSTEM_PROMPT)
            .defaultOptions(AnthropicChatOptions.builder()
                    .temperature(0.1)
                    .maxTokens(1024)
                    .build())
            .build();
}

@Bean
ChatClient alertGenerationClient(ChatClient.Builder builder) {
    return builder.clone()
            .defaultSystem(AlertPrompts.SYSTEM_PROMPT)
            .defaultOptions(AnthropicChatOptions.builder()
                    .temperature(0.3)
                    .maxTokens(2048)
                    .build())
            .build();
}
```

If `ChatClient.Builder` does not support `.clone()`, create two separate builder beans or use `ChatClient.create(chatModel)` with explicit configuration instead of the auto-configured builder.

## Warnings

### WR-01: Document Content BYTEA Column Eagerly Fetched in Summary Listing

**File:** `src/main/java/com/onconavigator/domain/ClinicalDocument.java:79`
**Issue:** The `content` byte[] column (raw file bytes, up to 20 MB per document) has no `@Basic(fetch = FetchType.LAZY)` annotation. When `findByPatient_IdOrderByCreatedAtDesc` is called in `DocumentUploadController.getDocumentsForPatient()` (line 139), Hibernate will eagerly load the full BYTEA content for ALL documents of a patient, even though only metadata fields are mapped to `DocumentSummaryResponse`. For a patient with 10 documents at 20 MB each, this loads 200 MB into memory per listing request.

**Fix:**
```java
@Basic(fetch = FetchType.LAZY)
@Column(name = "content", columnDefinition = "bytea", nullable = false)
private byte[] content;
```

Note: Lazy fetching of basic types requires Hibernate bytecode enhancement. Add the `hibernate-enhance-maven-plugin` to `pom.xml`, or use a JPQL projection/DTO query that excludes the `content` column for the summary listing endpoint.

### WR-02: Upload Endpoint Missing NURSE_NAVIGATOR Role

**File:** `src/main/java/com/onconavigator/web/DocumentUploadController.java:76`
**Issue:** The upload and document listing endpoints require `CARE_COORDINATOR` or `ADMIN` role but exclude `NURSE_NAVIGATOR`. According to `CLAUDE.md`, the system has three roles: `ROLE_NURSE_NAVIGATOR`, `ROLE_CARE_COORDINATOR`, `ROLE_ADMIN`. Other controllers (PatientController, AlertController) include `NURSE_NAVIGATOR` for read/triage operations. Document upload is a clinical workflow action that nurse navigators would reasonably perform when receiving faxed reports. If this is intentional, it should be documented; if not, it blocks the primary user persona from using the feature.

**Fix:**
```java
// If nurse navigators should upload documents:
@PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')")

// If intentionally restricted, add a comment explaining why
```

### WR-03: Hardcoded Encryption and HMAC Keys in application-local.yml

**File:** `src/main/resources/application-local.yml:68,74`
**Issue:** The AES-256 encryption key (`onconavigator.encryption.key`) and HMAC-SHA256 key (`onconavigator.hmac.key`) are committed to the repository with actual Base64-encoded 32-byte key values. While comments say "PLACEHOLDER", these are valid cryptographic keys that could be mistakenly used in a non-local environment. The `CLAUDE.md` says "Developers should not commit plaintext DB passwords even in gitignored files" -- the same principle applies to encryption keys. If this file is not gitignored, these keys are in version control.

**Fix:**
Use environment variable references with no default (forcing explicit key provisioning):
```yaml
onconavigator:
  encryption:
    key: ${ONCO_ENCRYPTION_KEY}
  hmac:
    key: ${ONCO_HMAC_KEY}
```

Or use Jasypt-encrypted values consistent with the DB password approach.

### WR-04: ClaudeVisionService Hardcodes Model Name

**File:** `src/main/java/com/onconavigator/ai/service/ClaudeVisionService.java:69`
**Issue:** The model identifier `"claude-sonnet-4-20250514"` is hardcoded in `ClaudeVisionService`. The classification and alert generation clients use the model configured via `spring.ai.anthropic.chat.options.model` in application properties, but the vision service bypasses this configuration. If the model needs to change (new version, different model for cost/quality), this requires a code change and redeployment rather than a configuration update.

**Fix:**
```java
@Service
public class ClaudeVisionService {
    private final AnthropicChatModel chatModel;
    private final String modelId;

    public ClaudeVisionService(
            AnthropicChatModel chatModel,
            @Value("${spring.ai.anthropic.chat.options.model:claude-sonnet-4-20250514}") String modelId) {
        this.chatModel = chatModel;
        this.modelId = modelId;
    }

    // Use this.modelId instead of hardcoded string in .model() call
}
```

### WR-05: PdfExtractionService Parses PDF Twice in hasSelectableText + extractText Path

**File:** `src/main/java/com/onconavigator/service/DocumentProcessingService.java:231-235`
**Issue:** In `extractFromPdf()`, `pdfExtractionService.hasSelectableText(pdfBytes)` parses the PDF and extracts text to check the length, then `pdfExtractionService.extractText(pdfBytes)` parses the same PDF again and extracts the same text. For large PDFs, this doubles the processing time and memory usage. Each call creates a new `PDDocument`, runs `PDFTextStripper`, and then closes it.

**Fix:**
Combine into a single method that returns both the text and the selectability status:
```java
// In PdfExtractionService:
public record TextExtractionResult(String text, boolean hasSelectableText) {}

public TextExtractionResult extractTextWithCheck(byte[] fileBytes) {
    try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(fileBytes))) {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        String text = stripper.getText(document);
        boolean selectable = text != null && text.strip().length() > 50;
        return new TextExtractionResult(text, selectable);
    } catch (IOException e) {
        log.error("PDF text extraction failed: {}", e.getMessage());
        throw new RuntimeException("PDF text extraction failed", e);
    }
}
```

### WR-06: Frontend PatientMatchSelector Renders Unescaped Extracted Name from AI

**File:** `frontend/src/features/documents/PatientMatchSelector.tsx:79-82`
**Issue:** The `extractedName` and `extractedDob` props, which come from Claude's AI classification of document text, are rendered directly in JSX: `{extractedName}` and `{extractedDob}`. While React does escape JSX text content by default (preventing XSS via `innerHTML`), the names originate from AI classification of potentially adversarial document content. If the classification prompt were to return HTML-like strings in the `patientName` field (e.g., from a crafted document), React's default escaping would protect against script injection, but unusual characters could still cause UI rendering issues. This is a defense-in-depth concern rather than an active vulnerability, given React's escaping.

**Fix:**
Add explicit length truncation and character validation on AI-extracted fields before display:
```typescript
const safeName = extractedName?.slice(0, 100) ?? null;
const safeDob = extractedDob?.match(/^\d{4}-\d{2}-\d{2}$/) ? extractedDob : null;
```

### WR-07: DocumentProcessingService Does Not Validate File Magic Bytes

**File:** `src/main/java/com/onconavigator/service/DocumentProcessingService.java:188-201`
**Issue:** The `validateFile()` method checks `file.getContentType()` against the allowed set, but the content type is provided by the client (browser or HTTP client) and can be spoofed. A file with malicious content could be uploaded with a declared `Content-Type: application/pdf` that is actually not a PDF. While `PdfExtractionService` would fail to parse it and throw a `RuntimeException`, the file bytes are still persisted to the database as a `ClinicalDocument` with `content_type = "application/pdf"`. When served back via the content endpoint, it would be served with a PDF content type even though it is not a PDF. For a HIPAA system handling clinical documents, server-side magic byte validation is a defense-in-depth requirement.

**Fix:**
```java
private void validateFile(MultipartFile file) {
    // ... existing checks ...

    // Validate file magic bytes match declared content type
    byte[] header = new byte[Math.min(8, (int) file.getSize())];
    try (var is = file.getInputStream()) {
        is.read(header);
    } catch (IOException e) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot read file");
    }

    String contentType = file.getContentType();
    if ("application/pdf".equals(contentType) && !startsWith(header, new byte[]{0x25, 0x50, 0x44, 0x46})) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File content does not match declared PDF type");
    }
    if ("image/png".equals(contentType) && !startsWith(header, new byte[]{(byte)0x89, 0x50, 0x4E, 0x47})) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File content does not match declared PNG type");
    }
    if ("image/jpeg".equals(contentType) && !startsWith(header, new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF})) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File content does not match declared JPEG type");
    }
}
```

---

_Reviewed: 2026-05-01T14:30:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
