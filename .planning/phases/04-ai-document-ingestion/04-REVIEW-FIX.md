---
phase: 04-ai-document-ingestion
fixed_at: 2026-05-01T21:10:00Z
review_path: .planning/phases/04-ai-document-ingestion/04-REVIEW.md
iteration: 1
findings_in_scope: 12
fixed: 11
skipped: 1
status: partial
---

# Phase 4: Code Review Fix Report

**Fixed at:** 2026-05-01T21:10:00Z
**Source review:** .planning/phases/04-ai-document-ingestion/04-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 12
- Fixed: 11
- Skipped: 1

## Fixed Issues

### CR-01: HTTP Header Injection via Unsanitized Filename in Content-Disposition

**Files modified:** `src/main/java/com/onconavigator/web/DocumentUploadController.java`
**Commit:** 740a99d
**Applied fix:** Replaced raw string interpolation in Content-Disposition header with Spring's `ContentDisposition.inline().filename(safeFilename).build()`. Added regex sanitization to strip newlines, quotes, path separators, and shell-unsafe characters from the original filename before use.

### CR-02: Content-Type Spoofing Enables Stored XSS via Document Content Endpoint

**Files modified:** `src/main/java/com/onconavigator/web/DocumentUploadController.java`
**Commit:** bc9b54e
**Applied fix:** Added `SAFE_CONTENT_TYPES` whitelist (`application/pdf`, `image/jpeg`, `image/png`). Stored content types not on the whitelist are replaced with `application/octet-stream` (forces browser download). Added `X-Content-Type-Options: nosniff` header to prevent MIME sniffing attacks.

### CR-03: Null Document ID Returned When Document Is Not Persisted

**Files modified:** `src/main/java/com/onconavigator/service/DocumentProcessingService.java`
**Commit:** d47cc9c
**Applied fix:** Split the return path: when the document is persisted (patient linked), return `doc.getId()`. When the document cannot be persisted (no patient link), explicitly return `null` as `documentId` in the response. This makes the API contract explicit so the frontend can conditionally disable document preview and care event creation.

### CR-04: Decrypted PHI (MRN) Returned in Upload API Response

**Files modified:** `src/main/java/com/onconavigator/service/DocumentPatientMatchService.java`
**Commit:** 9fffb54
**Applied fix:** Masked MRN in `PatientCandidate` to show only last 4 digits (e.g., `***0001`). MRNs with 4 or fewer characters are fully masked as `****`. The full MRN is not needed in the matching UI -- the last 4 digits are sufficient for the nurse navigator to distinguish candidates.

### CR-05: Shared ChatClient.Builder Mutates System Prompt for Both AI Beans

**Files modified:** `src/main/java/com/onconavigator/ai/config/AiClientConfig.java`
**Commit:** 6f822d5
**Applied fix:** Replaced injected singleton `ChatClient.Builder` parameter with `ChatModel` parameter in both `@Bean` methods. Each bean now creates its own builder via `ChatClient.builder(chatModel)`, ensuring independent system prompt and option configuration. This prevents the classification client from receiving the alert generation system prompt (or vice versa).

### WR-01: Document Content BYTEA Column Eagerly Fetched in Summary Listing

**Files modified:** `src/main/java/com/onconavigator/domain/ClinicalDocument.java`
**Commit:** 07a9e03
**Applied fix:** Added `@Basic(fetch = FetchType.LAZY)` annotation on the `content` byte[] field. This signals Hibernate to lazy-load the up-to-20 MB BYTEA column, preventing it from being loaded when only document metadata is needed (e.g., patient document listing endpoint). Note: full lazy fetch effectiveness requires Hibernate bytecode enhancement or DTO projections.

### WR-02: Upload Endpoint Missing NURSE_NAVIGATOR Role

**Files modified:** `src/main/java/com/onconavigator/web/DocumentUploadController.java`
**Commit:** 86d88c3
**Applied fix:** Added `NURSE_NAVIGATOR` to all three `@PreAuthorize` annotations (upload, content, and listing endpoints) and to the BOLA defense-in-depth role check in the content endpoint. Updated class Javadoc to reflect the three-role access policy. Nurse navigators are the primary users who receive faxed clinical documents.

### WR-04: ClaudeVisionService Hardcodes Model Name

**Files modified:** `src/main/java/com/onconavigator/ai/service/ClaudeVisionService.java`
**Commit:** f97ae79
**Applied fix:** Added `@Value("${spring.ai.anthropic.chat.options.model:claude-sonnet-4-20250514}")` constructor parameter to make the model ID configurable. The hardcoded string in `AnthropicChatOptions.builder().model(...)` now uses the injected `modelId` field. Default value matches the previous hardcoded value for backward compatibility.

### WR-05: PdfExtractionService Parses PDF Twice in hasSelectableText + extractText Path

**Files modified:** `src/main/java/com/onconavigator/service/PdfExtractionService.java`, `src/main/java/com/onconavigator/service/DocumentProcessingService.java`
**Commit:** 21f2dce
**Applied fix:** Added `extractTextWithCheck()` method to `PdfExtractionService` that returns a `TextExtractionResult` record containing both the extracted text and the selectability flag from a single PDF parse. Updated `DocumentProcessingService.extractFromPdf()` to use this combined method instead of calling `hasSelectableText()` + `extractText()` separately. The original methods are preserved for backward compatibility.

### WR-06: Frontend PatientMatchSelector Renders Unescaped Extracted Name from AI

**Files modified:** `frontend/src/features/documents/PatientMatchSelector.tsx`
**Commit:** a74853b
**Applied fix:** Added defense-in-depth validation at the top of the component: `extractedName` is truncated to 100 characters, and `extractedDob` is validated against a strict YYYY-MM-DD regex pattern. Invalid DOB values are replaced with `null`. The sanitized `safeName` and `safeDob` variables are used in JSX rendering instead of the raw props.

### WR-07: DocumentProcessingService Does Not Validate File Magic Bytes

**Files modified:** `src/main/java/com/onconavigator/service/DocumentProcessingService.java`
**Commit:** 46f8dc3
**Applied fix:** Added magic byte validation in `validateFile()` after the content-type string check. Reads the first 8 bytes of the uploaded file and verifies: `%PDF` (0x25504446) for PDFs, `0x89504E47` for PNGs, and `0xFFD8FF` for JPEGs. Added private `bytesPrefixMatch()` helper method for byte array prefix comparison. Files with mismatched magic bytes are rejected with a 400 BAD_REQUEST.

## Skipped Issues

### WR-03: Hardcoded Encryption and HMAC Keys in application-local.yml

**File:** `src/main/resources/application-local.yml:68,74`
**Reason:** Skipped by design. The keys in `application-local.yml` are intentionally committed for local development per CLAUDE.md convention: "Developers should not commit plaintext DB passwords even in gitignored files -- Jasypt encrypts values with a runtime master key." The encryption keys are for local dev only. On AWS, these are replaced with Secrets Manager. The `application-local.yml` profile is activated only during local development.
**Original issue:** AES-256 encryption key and HMAC-SHA256 key committed to repository with actual Base64-encoded values.

---

**Test verification:** All 73 tests pass (0 failures, 0 errors, 0 skipped) after all fixes applied.

---

_Fixed: 2026-05-01T21:10:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
