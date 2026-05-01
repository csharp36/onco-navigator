# Phase 4: AI Document Ingestion & Alert Enhancement — Research

**Researched:** 2026-05-01
**Domain:** PDF/image extraction, OCR, Spring AI (Claude), Resilience4j circuit breaker, drag-and-drop UX
**Confidence:** HIGH (all stack components verified against Maven Central/npm; Spring AI version confirmed live)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**PDF Text Extraction Pipeline**
- D-01: Support both digitally-generated PDFs AND scanned/faxed documents. Two-path extraction: Apache PDFBox for text-selectable PDFs, Tesseract OCR for scanned/image-based documents.
- D-02: Hybrid OCR approach — Try Tesseract locally first. If extraction quality is low (confidence below threshold or insufficient content), escalate to Claude vision API as fallback.
- D-03: Accept PDF + image files (JPEG, PNG) — not just PDFs. Photographed documents are converted to a processable format before extraction.
- D-04: When both Tesseract and Claude vision fail, open the care event wizard with blank fields while still attaching the source file. The failure detection heuristic is at Claude's discretion.

**Patient Matching**
- D-05: Multi-field extraction from document text — extract MRN, patient name, and DOB. Try MRN match first via existing HMAC token lookup (Phase 3 D-04). Fall back to name+DOB matching if MRN not found.
- D-06: When match is uncertain or multiple patients match, show ranked candidates with confidence indicators. User picks correct patient or searches manually. User confirmation always required.
- D-07: When no patient match found, offer new patient creation — pre-fill existing Phase 3 patient creation wizard with extracted demographics.
- D-08: Name+DOB matching uses in-memory decryption — load patient records, decrypt PHI fields, compare in memory. Acceptable at pilot scale (<500 patients).

**Drag-and-Drop UX**
- D-09: Drop zones on both dashboard AND patient detail page.
- D-10: Processing progress shown via inline stepper modal: Uploading → Extracting text → Classifying → Matching patient → Ready.
- D-11: Pre-filled care event wizard reuses existing Phase 3 QuickAddCareEventDialog forms — add read-only source classification panel and PDF thumbnail preview.
- D-12: After care event saved, inline PDF viewer available on care event detail or patient detail page. Browser native PDF renderer in modal/panel. Download button also available.

**Claude Prompt Design**
- D-13: Document classification sends full extracted text to Claude — Anthropic BAA is a hard prerequisite for going live. Code can be built and tested against synthetic data without BAA.
- D-14: Alert generation maintains zero-PHI boundary — sends only anonymized clinical context (cancer type, pathway step name, deviation type, time window details). NO patient name, MRN, DOB, or identifiers.
- D-15: Non-standard alert generation is catch-all for unmatched templates — template flow stays primary, Claude is enhancement only.
- D-16: Resilience4j circuit breaker for all Claude API calls. Document classification fallback: manual classification dropdown. Alert generation fallback: template text. All fallbacks logged.

### Claude's Discretion
- Extraction failure detection heuristic (confidence score vs content length vs hybrid)
- Tesseract confidence threshold for Claude vision escalation
- Exact Resilience4j circuit breaker parameters (failure count, timeout window, half-open retry count)
- PDF preview thumbnail generation approach
- Image-to-processable-format conversion pipeline details
- Claude prompt template structure and response schema design
- Test corpus composition (number of documents per type, de-identification approach)
- Spring AI ChatClient configuration and model selection (claude-3-5-sonnet vs claude-3-7-sonnet)
- Document entity schema design (bytea storage, metadata columns)

### Deferred Ideas (OUT OF SCOPE)
- SMS notifications (V2)
- EMR integration (V2)
- Pathway template admin UI (V2 ADV-01)
- Predictive risk scoring (V2 ADV-03)
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| DOC-01 | Test corpus of de-identified/synthetic clinical PDFs in repository covering pathology reports, radiology reports, referral letters, operative notes, lab results for breast/lung/colorectal cancer | Synthea for demographic data; hand-authored PDF templates per document type; Python reportlab or fpdf2 for PDF generation |
| DOC-02 | User can drag-and-drop a PDF onto dashboard; system classifies it using Claude AI into document type | react-dropzone 15.0.0 for drop zone; Spring AI 1.1.5 ChatClient.entity() for structured classification response |
| DOC-03 | After classification, system extracts patient identifiers and matches to existing patient or offers new patient creation | HmacTokenService.computeMrnToken() reused; PatientService.findByMrn() for HMAC match; in-memory name+DOB matching at <500 patients |
| DOC-04 | On successful patient match, pre-filled care event wizard opens with extracted data for user confirmation | QuickAddCareEventDialog reused with defaultValues injection; new document source panel added |
| DOC-05 | Source PDF stored as bytea in PostgreSQL linked to care event, with original filename, content type, classification metadata | New clinical_documents table; @Lob byte[] on JPA entity; Flyway V9 migration; CareEvent FK |
| AI-01 | System uses template-based alert text for known deviation types (existing behavior) | Existing PathwayEvaluationActivityImpl.buildAlert() unchanged for standard deviations |
| AI-02 | System uses Claude API to generate plain-language alert descriptions for non-standard deviations (zero PHI in prompts) | Spring AI ChatClient with zero-PHI prompt; modify PathwayEvaluationActivityImpl to detect non-standard case |
| AI-03 | System uses Claude API to suggest corrective actions for edge cases not covered by pathway template suggestions | Same Claude call as AI-02; response schema includes both deviationDescription and suggestedAction fields |
| AI-04 | System falls back to template text when Claude API is unavailable (circuit breaker pattern) | Resilience4j 2.3.0 @CircuitBreaker with fallbackMethod; fallback returns template text stored as local variable before CB call |
</phase_requirements>

---

## Summary

Phase 4 builds a two-capability system: (1) clinical document ingestion via drag-and-drop, with a multi-stage extraction pipeline (PDFBox → Tesseract OCR → Claude vision fallback) feeding a single Claude classification+extraction call, (2) Claude-generated descriptions for non-standard pathway deviations with Resilience4j circuit breaking for fault tolerance.

The stack is already 80% in place from prior phases. The primary new additions are: Spring AI 1.1.5 (with new artifact ID `spring-ai-starter-model-anthropic`), Resilience4j 2.3.0, Apache PDFBox 3.0.7, Tess4J 5.16.0, and react-dropzone 15.0.0 on the frontend. The biggest integration complexity is the dual-PHI-boundary architecture: document classification calls Claude with full PHI (BAA-covered), while alert generation must maintain zero-PHI prompts (no BAA needed for that call path). These two Claude call sites must be implemented, reviewed, and tested separately.

The Anthropic BAA is a prerequisite for production deployment of document classification only. Development and testing against synthetic data proceeds without it.

**Primary recommendation:** Use Spring AI 1.1.5 BOM + `spring-ai-starter-model-anthropic` with `ChatClient.entity()` for structured JSON responses. Use Resilience4j `@CircuitBreaker` with annotation-driven fallback. Implement PDFBox 3.x API (uses `Loader.loadPDF()`, not deprecated `PDDocument.load()`). Tess4J requires `tesseract-ocr` installed in the Docker image — plan this in the Docker Compose evolution.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| File upload (drag-and-drop) | Browser/Client | — | File selection, drag events, multipart POST is a client-side UX responsibility |
| PDF/image text extraction | API/Backend | — | PDFBox and Tess4J are JVM libraries; extraction is CPU-bound, belongs server-side |
| Claude classification+extraction | API/Backend | — | Spring AI ChatClient calls happen in the backend service layer; never expose API key to frontend |
| Patient matching (HMAC + in-memory) | API/Backend | Database | HMAC lookup is a DB query; in-memory name/DOB comparison loads decrypted records into app memory |
| Document blob storage | Database/Storage | — | PostgreSQL bytea column; served back via backend streaming endpoint |
| Processing progress stepper UX | Browser/Client | — | Client-side state machine tracking upload → extract → classify → match → ready steps |
| Pre-filled care event wizard | Browser/Client | API/Backend | Form state client-side; final save is POST to existing care event API endpoint |
| Circuit breaker (Claude API) | API/Backend | — | Resilience4j wraps Spring AI calls in the service layer; fallback is also backend |
| Alert description generation | API/Backend | — | Called from PathwayEvaluationActivityImpl (Temporal activity = backend only) |
| Inline PDF viewer | Browser/Client | API/Backend | Browser's native PDF renderer receives blob bytes served from a backend streaming endpoint |

---

## Standard Stack

### Core New Dependencies (Phase 4 additions)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `spring-ai-starter-model-anthropic` | 1.1.5 (via BOM) | Claude API integration | GA, on Maven Central, correct new artifact ID (not old `spring-ai-anthropic-spring-boot-starter` pre-1.0.0 name) |
| `spring-ai-bom` | 1.1.5 | Spring AI dependency management | Added to `<dependencyManagement>` alongside Spring Boot BOM |
| `resilience4j-spring-boot3` | 2.3.0 | Circuit breaker for Claude calls | Spring Boot 3 compatible, annotation-driven via `@CircuitBreaker` |
| `pdfbox` | 3.0.7 | Digital PDF text extraction | Apache project, most widely used Java PDF library; 3.x uses `Loader.loadPDF()` API |
| `pdfbox-io` | 3.0.7 | PDFBox 3.x I/O support | Separate module in PDFBox 3.x; required alongside `pdfbox` |
| `pdfbox-tools` | 3.0.7 | PDF rendering to images (for thumbnail generation) | Contains `PDFRenderer`; needed to render PDF page → `BufferedImage` for thumbnails |
| `tess4j` | 5.16.0 | Tesseract OCR for scanned documents | JNA wrapper for Tesseract; requires `tesseract-ocr` native library in Docker image |
| `spring-boot-starter-aop` | via Boot BOM | AOP proxy for Resilience4j annotations | Required by `resilience4j-spring-boot3`; without it, `@CircuitBreaker` annotations are no-ops |
| `react-dropzone` | 15.0.0 | Drag-and-drop file upload UX | De facto standard; integrates with shadcn/ui; supports `accept` prop for PDF/image filtering |

### Existing Dependencies Already Present (reused, no change)

| Library | Version | Phase 4 Usage |
|---------|---------|--------------|
| `spring-boot-starter-web` | Boot BOM | `@RequestParam MultipartFile` upload endpoint |
| `spring-boot-starter-data-jpa` | Boot BOM | `ClinicalDocument` entity persistence, `@Lob byte[]` |
| `spring-boot-starter-validation` | Boot BOM | Request body validation on upload endpoint |
| `hibernate-envers` | Boot BOM | `@Audited` on new `ClinicalDocument` entity |
| `flyway-core` + `flyway-database-postgresql` | Boot BOM | V9 migration for `clinical_documents` table |
| `react-hook-form` | 7.x | Pre-filled care event wizard (existing form reused) |
| `zod` | 4.x | Form schema validation (note: project is on Zod v4, not v3) |
| `@tanstack/react-query` | 5.x | Upload mutation, document fetch query |
| `shadcn/ui` components | current | Dialog, Stepper, Badge, Card, Button for processing UX |

### Version Verification

All versions confirmed against Maven Central and npm registry as of 2026-05-01:

```bash
# Backend
curl -s "https://repo1.maven.org/maven2/org/springframework/ai/spring-ai-bom/1.1.5/spring-ai-bom-1.1.5.pom" | head -5   # HTTP 200
curl -s "https://repo1.maven.org/maven2/io/github/resilience4j/resilience4j-spring-boot3/2.3.0/..." # HTTP 200
# PDFBox 3.0.7 confirmed via Maven Central search API
# Tess4J 5.16.0 confirmed via Maven Central search API

# Frontend
npm view react-dropzone version  # → 15.0.0
```

[VERIFIED: Maven Central] Spring AI BOM 1.1.5 on `repo1.maven.org`
[VERIFIED: Maven Central] `spring-ai-starter-model-anthropic` is the correct artifact in BOM 1.1.5 (confirmed via BOM POM inspection)
[VERIFIED: Maven Central] Resilience4j 2.3.0 on Maven Central
[VERIFIED: Maven Central] PDFBox 3.0.7 on Maven Central
[VERIFIED: Maven Central] Tess4J 5.16.0 on Maven Central
[VERIFIED: npm] react-dropzone 15.0.0 on npm

### Installation

**Backend pom.xml additions:**

```xml
<!-- Spring AI BOM — add alongside testcontainers BOM in <dependencyManagement> -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-bom</artifactId>
    <version>1.1.5</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>

<!-- AI integration -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-anthropic</artifactId>
</dependency>

<!-- Circuit breaker — requires spring-boot-starter-aop -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.3.0</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>

<!-- PDF extraction -->
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.7</version>
</dependency>
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox-io</artifactId>
    <version>3.0.7</version>
</dependency>
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox-tools</artifactId>
    <version>3.0.7</version>
</dependency>

<!-- OCR -->
<dependency>
    <groupId>net.sourceforge.tess4j</groupId>
    <artifactId>tess4j</artifactId>
    <version>5.16.0</version>
</dependency>
```

**Frontend:**

```bash
npm install react-dropzone@15.0.0
```

Note: `react-dropzone` is not currently in `frontend/package.json` and must be added.

---

## Architecture Patterns

### System Architecture Diagram

```
Browser (drag + drop)
        │
        │ POST /api/documents/upload
        │ multipart/form-data (file bytes)
        ▼
DocumentUploadController
        │
        ▼
DocumentProcessingService
        │
        ├──[PDF? check hasText()]──────────────────────────────────┐
        │                                                          │
        │  YES: has selectable text                                │  NO: image-only or scanned
        ▼                                                          ▼
PDFBox PDFTextStripper                                  Tess4J Tesseract
   extracts text                                  OCR → text + meanConfidence
        │                                                          │
        │                                          LOW confidence? ▼
        │                                         ├── threshold met → use Tesseract text
        │                                         └── below threshold ──────────────────┐
        │                                                                                │
        ├────────────────────────────────────────────────────────────┐                  │
        │                                                            │                  │
        ▼                                                            ▼                  │
  extracted text (high quality)                        Claude Vision API               │
                                                      (image/jpeg or image/png)        │
                                                       extract text fallback            │
                                                                 │                      │
                                                                 ▼                      │
                                               empty/failed? ───► blank wizard path ◄──┘
                                                                 │
        └────────────────────────────────────────────────────────┘
                                         │
                                         ▼
                               Claude Classification+Extraction
                           (single API call: full extracted text)
                            returns: DocumentClassificationResult
                              { documentType, mrn, patientName,
                                dateOfBirth, eventDate, eventType,
                                extractedDetails }
                                         │
                               ┌─────────┴─────────┐
                               │                   │
                         MRN present?           No MRN
                               │                   │
                               ▼                   ▼
                     HmacTokenService        Name+DOB in-memory
                   .computeMrnToken(mrn)       patient scan
                   PatientRepository          (decrypt all, compare)
                   .findByMrnHmacToken()              │
                               │                      │
                      ┌────────┴──────┐     ┌─────────┴──────────┐
                      │               │     │                    │
                 exact match   no match     single match    multiple/no match
                      │               │         │                │
                      ▼               ▼         ▼                ▼
               link to patient  candidate    link to        show ranked
               (auto-select)      picker    patient         candidates UI
                                    │
                                    ▼
                            Pre-filled QuickAddCareEventDialog
                              (defaultValues from extracted data)
                              User confirms/corrects → POST /api/patients/{id}/care-events
                                         │
                                         ▼
                              clinical_documents row saved
                              (bytea blob, care_event_id FK)
```

**Alert Generation (separate flow — zero-PHI):**

```
PathwayEvaluationActivityImpl.evaluate()
        │
        ├──[standard deviation: template text exists]──► buildAlert(step) — unchanged
        │
        └──[non-standard deviation: template text null/absent]
                         │
                         ▼
              ClaudeAlertGenerationService.generateAlertText(
                cancerType, pathwayStepName, alertType,
                elapsedDays, windowDays)    ← ZERO PHI
                         │
                         ▼
              @CircuitBreaker → ChatClient.call()
              (fallback: return step.alertText() or generic template)
                         │
                         ▼
              buildAlert(step, claudeText, claudeSuggestedAction)
```

### Recommended Project Structure (new files only)

```
src/main/java/com/onconavigator/
├── domain/
│   └── ClinicalDocument.java          # new entity — PDF blob + metadata
├── service/
│   ├── DocumentProcessingService.java  # orchestrates extraction → classify → match
│   ├── PdfExtractionService.java       # PDFBox text extraction
│   ├── OcrExtractionService.java       # Tess4J OCR + confidence
│   └── ClaudeDocumentService.java      # Spring AI calls for classification + alert gen
├── web/
│   └── DocumentUploadController.java   # POST /api/documents/upload (multipart)
└── activity/
    └── (modify PathwayEvaluationActivityImpl) # add Claude path for non-standard alerts

src/main/resources/db/migration/
└── V9__create_clinical_documents.sql   # clinical_documents table + FK to care_events

frontend/src/features/documents/
├── DocumentDropZone.tsx               # react-dropzone component
├── DocumentProcessingModal.tsx        # inline stepper: upload → extract → classify → match
├── DocumentPatientMatchPanel.tsx      # ranked candidates or auto-match confirmation
├── api.ts                             # useMutation for upload, useQuery for doc download
└── types.ts                           # DocumentClassificationResult, DocumentType enums

test-corpus/                            # synthetic PDFs (DOC-01)
├── pathology/
├── radiology/
├── referral/
├── operative-notes/
└── lab-results/
```

### Pattern 1: Spring AI ChatClient Structured Classification (single call)

```java
// Source: https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html
// Source: https://docs.spring.io/spring-ai/reference/api/chat/anthropic-chat.html

// Define the response schema as a Java record
@JsonPropertyOrder({"documentType", "mrn", "patientName", "dateOfBirth",
                    "eventDate", "eventType", "extractedDetails"})
public record DocumentClassificationResult(
    String documentType,   // PATHOLOGY_REPORT | RADIOLOGY_REPORT | REFERRAL_LETTER | OPERATIVE_NOTE | LAB_RESULT
    String mrn,            // null if not found
    String patientName,    // null if not found
    String dateOfBirth,    // ISO date string or null
    String eventDate,      // ISO date string or null
    String eventType,      // maps to CareEventType enum name
    String extractedDetails // narrative summary for notes field
) {}

// In ClaudeDocumentService:
@CircuitBreaker(name = "claude-api", fallbackMethod = "classifyFallback")
public DocumentClassificationResult classify(String extractedText) {
    return chatClient.prompt()
        .system(CLASSIFICATION_SYSTEM_PROMPT)
        .user(extractedText)
        .call()
        .entity(DocumentClassificationResult.class);
}

// Fallback — triggered when circuit breaker is open
public DocumentClassificationResult classifyFallback(String extractedText, Exception e) {
    log.warn("Claude classification CB open, returning null classification: {}", e.getMessage());
    return null; // frontend shows manual dropdown
}
```

### Pattern 2: PDFBox 3.x Text Extraction

```java
// Source: Context7 /apache/pdfbox — verified against 3.x API
// IMPORTANT: PDFBox 3.x uses Loader.loadPDF(), not PDDocument.load()

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.io.RandomAccessReadBuffer;

public String extractText(byte[] pdfBytes) throws IOException {
    try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(pdfBytes))) {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        return stripper.getText(document);
    }
}

// Check if PDF has selectable text (to decide path)
public boolean hasSelectableText(byte[] pdfBytes) throws IOException {
    try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(pdfBytes))) {
        PDFTextStripper stripper = new PDFTextStripper();
        String text = stripper.getText(document);
        return text != null && text.strip().length() > 50; // heuristic: >50 chars = text-based
    }
}
```

### Pattern 3: PDFBox 3.x Render Page to Image (for Tesseract and thumbnail)

```java
// Source: Context7 /apache/pdfbox — renderImageWithDPI
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.rendering.ImageType;
import java.awt.image.BufferedImage;

public BufferedImage renderFirstPage(byte[] pdfBytes) throws IOException {
    try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(pdfBytes))) {
        PDFRenderer renderer = new PDFRenderer(document);
        renderer.setSubsamplingAllowed(true);
        return renderer.renderImageWithDPI(0, 300, ImageType.RGB); // page 0, 300 DPI
    }
}
```

### Pattern 4: Tess4J OCR with Confidence Score

```java
// Source: MEDIUM confidence — Tess4J 5.x docs + GitHub source review
// Tess4J requires tessdata files on disk and native libtesseract installed (Docker: apt-get install tesseract-ocr)

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

@Bean
public Tesseract tesseract(@Value("${onconavigator.tessdata.path:/usr/share/tesseract-ocr/5/tessdata}") 
                            String tessDataPath) {
    Tesseract tesseract = new Tesseract();
    tesseract.setDatapath(tessDataPath);
    tesseract.setLanguage("eng");
    tesseract.setOcrEngineMode(1); // LSTM engine
    return tesseract;
}

public record OcrResult(String text, int meanConfidence) {}

public OcrResult performOcr(BufferedImage image) throws TesseractException {
    Tesseract t = applicationContext.getBean(Tesseract.class);
    String text = t.doOCR(image);
    int confidence = t.getAPI().TessBaseAPIMeanTextConf(t.getAPI().getHandle());
    return new OcrResult(text, confidence);
}

// Usage: confidence < 60 → escalate to Claude vision
```

### Pattern 5: Resilience4j Circuit Breaker Configuration

```yaml
# application-local.yml additions
resilience4j:
  circuitbreaker:
    instances:
      claude-api:
        slidingWindowSize: 10
        minimumNumberOfCalls: 3
        failureRateThreshold: 50          # open after 50% failures
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 2
        automaticTransitionFromOpenToHalfOpenEnabled: true
        registerHealthIndicator: true

management:
  health:
    circuitbreakers:
      enabled: true

spring:
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY}
      chat:
        options:
          model: claude-opus-4-5          # or claude-3-5-sonnet-latest
          max-tokens: 1024
          temperature: 0.1               # low temperature for classification accuracy
```

```java
// @CircuitBreaker annotation (requires spring-boot-starter-aop)
@CircuitBreaker(name = "claude-api", fallbackMethod = "generateAlertFallback")
public String generateAlertDescription(String cancerType, String stepName,
                                       String alertType, long elapsedDays) {
    return chatClient.prompt()
        .system(ALERT_GENERATION_SYSTEM_PROMPT)
        .user(buildZeroPhiAlertPrompt(cancerType, stepName, alertType, elapsedDays))
        .call()
        .content();
}

// Fallback method — same signature + Exception parameter
private String generateAlertFallback(String cancerType, String stepName,
                                      String alertType, long elapsedDays, Exception e) {
    log.warn("Claude alert gen CB open, using template fallback: {}", e.getMessage());
    return null; // caller falls back to step.alertText()
}
```

### Pattern 6: Multipart Upload Endpoint

```java
// Source: Spring MVC docs — Spring Boot 3 standard pattern
@RestController
@RequestMapping("/api/documents")
public class DocumentUploadController {

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public DocumentUploadResponse uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "patientId", required = false) UUID patientId,
            @AuthenticationPrincipal Jwt jwt) {
        // patientId non-null when dropped on patient detail page (D-09)
        UUID actorId = UUID.fromString(jwt.getSubject());
        return documentProcessingService.processUpload(file, patientId, actorId);
    }

    @GetMapping("/{documentId}/content")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> getDocumentContent(@PathVariable UUID documentId) {
        ClinicalDocument doc = documentService.findById(documentId);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(doc.getContentType()))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                    "inline; filename=\"" + doc.getOriginalFilename() + "\"")
            .body(doc.getContent());
    }
}
```

### Pattern 7: react-dropzone with TypeScript

```typescript
// Source: react-dropzone 15.x docs — ASSUMED (API stable across 14-15)
import { useDropzone } from 'react-dropzone';

interface DocumentDropZoneProps {
  patientId?: string;          // present on patient detail page (D-09)
  onUploadComplete: (result: DocumentUploadResponse) => void;
}

export function DocumentDropZone({ patientId, onUploadComplete }: DocumentDropZoneProps) {
  const uploadDocument = useUploadDocument();

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    accept: {
      'application/pdf': ['.pdf'],
      'image/jpeg': ['.jpg', '.jpeg'],
      'image/png': ['.png'],
    },
    maxFiles: 1,
    maxSize: 20 * 1024 * 1024, // 20 MB
    onDrop: (acceptedFiles) => {
      if (acceptedFiles.length === 0) return;
      const formData = new FormData();
      formData.append('file', acceptedFiles[0]);
      if (patientId) formData.append('patientId', patientId);
      uploadDocument.mutate(formData, { onSuccess: onUploadComplete });
    },
  });

  return (
    <div {...getRootProps()} className={`border-2 border-dashed rounded-lg p-8 text-center
      ${isDragActive ? 'border-primary bg-primary/5' : 'border-muted-foreground/25'}`}>
      <input {...getInputProps()} />
      <p className="text-sm text-muted-foreground">
        Drop a clinical document here, or click to select
      </p>
      <p className="text-xs text-muted-foreground mt-1">PDF, JPEG, or PNG up to 20 MB</p>
    </div>
  );
}
```

### Pattern 8: api-client.ts multipart extension

```typescript
// Extend existing api-client.ts — add upload method that skips Content-Type header
// (browser sets multipart boundary automatically when not specified)
export const apiClient = {
  // ... existing methods ...
  upload: <T>(path: string, formData: FormData): Promise<T> =>
    requestMultipart<T>(path, formData),
};

async function requestMultipart<T>(path: string, formData: FormData): Promise<T> {
  const token = getAccessToken();
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    body: formData,
    headers: token ? { Authorization: `Bearer ${token}` } : {},
    // DO NOT set Content-Type — browser sets multipart/form-data with boundary automatically
  });
  if (!response.ok) throw new ApiError(response.status, await response.text());
  return response.json() as Promise<T>;
}
```

### Anti-Patterns to Avoid

- **Using `PDDocument.load()` (PDFBox 2.x API):** In PDFBox 3.x this method was removed. Always use `Loader.loadPDF(new RandomAccessReadBuffer(bytes))`. [VERIFIED: PDFBox migration guide]
- **Setting `Content-Type: application/json` on multipart uploads:** This breaks multipart boundary parsing. Omit Content-Type for FormData requests; the browser sets it correctly.
- **Calling Claude with PHI for alert generation:** Alert generation must be zero-PHI (D-14). Sending patient name or MRN would violate the established security boundary even with a BAA, since BAA coverage for the alert flow is not assumed.
- **Using old Spring AI artifact `spring-ai-anthropic-spring-boot-starter`:** This artifact exists only up to 1.0.0-M6 (pre-release). For 1.0.x and 1.1.x GA, the artifact is `spring-ai-starter-model-anthropic`. [VERIFIED: Maven Central BOM inspection]
- **Instantiating `Tesseract` as a Spring Bean directly without thread safety:** Tesseract instances are not thread-safe. Use a prototype-scoped bean or create instances per-call and discard. With virtual threads enabled, this is especially important.
- **Storing PDF files in the filesystem (not DB):** The decision (D-05 / DOC-05) specifies bytea in PostgreSQL. Filesystem storage would create HIPAA audit and backup gaps.
- **Missing `spring-boot-starter-aop` when using Resilience4j annotations:** Without AOP, `@CircuitBreaker` annotations are silently ignored (no proxy is created). The circuit breaker appears to work in tests but doesn't actually protect production calls.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| PDF text extraction | Custom PDF parser | Apache PDFBox 3.0.7 | PDF binary format is complex; font encoding, compression, encryption, cross-references require mature library |
| OCR from scanned images | Direct Tesseract JNI binding | Tess4J 5.16.0 | JNA wrapper handles native library loading, page segmentation modes, tessdata management |
| Circuit breaker state machine | Custom failure counter + timer | Resilience4j `@CircuitBreaker` | Sliding window, half-open state, automatic transition, actuator health integration require non-trivial implementation |
| Structured JSON from Claude | Manual JSON parsing of free-form LLM output | `ChatClient.entity(DocumentClassificationResult.class)` with Spring AI `BeanOutputConverter` | Free-form parsing fails on schema drift; BeanOutputConverter generates JSON Schema and enforces it |
| Claude API retry with backoff | Manual `Thread.sleep` retry loop | Spring AI `spring.ai.retry.*` properties + Resilience4j CB | Spring AI handles transient HTTP failures; Resilience4j handles sustained outage; combining both prevents retry storms |
| Image MIME type detection | Magic byte inspection | PDFBox + react-dropzone `accept` prop | PDFBox renders PDF pages as images for OCR; react-dropzone restricts accepted types at drop time |
| Patient search by name (CONTAINS) | LIKE query on encrypted column | In-memory decrypt-and-compare (D-08) | AES-GCM encrypted columns cannot be equality-searched; established pattern from Phase 3 |

**Key insight:** The document extraction pipeline has three distinct failure modes (no text, low-confidence OCR, and Claude outage). Each must be handled independently. Any attempt to collapse them into a single code path produces silent quality degradation without user notification.

---

## Common Pitfalls

### Pitfall 1: Tesseract Native Library Not Found in Docker
**What goes wrong:** `java.lang.UnsatisfiedLinkError: Unable to load library 'tesseract'` at runtime in Docker container.
**Why it happens:** Tess4J is a JNA wrapper for `libtesseract.so`. The shared library must be installed at the OS level — the JAR ships with Windows DLLs, not Linux `.so` files.
**How to avoid:** Add to Dockerfile / docker-compose app service: `apt-get install -y tesseract-ocr tesseract-ocr-eng libtesseract-dev`. Also set `tessdata` path to `/usr/share/tesseract-ocr/5/tessdata` (Ubuntu 22+) via application property.
**Warning signs:** OCR service starts but fails on first file; exception mentions `libtesseract` or `UnsatisfiedLinkError`.

### Pitfall 2: PDFBox 3.x API Incompatibility (`PDDocument.load()`)
**What goes wrong:** `NoSuchMethodError: PDDocument.load(InputStream)` at runtime.
**Why it happens:** PDFBox 3.x removed `PDDocument.load()`. All online tutorials and StackOverflow answers show the 2.x API. Developers copy examples and get compilation errors or runtime failures.
**How to avoid:** Always use `Loader.loadPDF(new RandomAccessReadBuffer(bytes))`. Check all PDF-handling code for `PDDocument.load` before committing.
**Warning signs:** `NoSuchMethodError` or `MissingMethodException` referencing `PDDocument.load`.

### Pitfall 3: Resilience4j Annotations Silently No-Op (Missing AOP)
**What goes wrong:** Circuit breaker never trips; all Claude API calls fail with exceptions propagating to the user; no fallback executes.
**Why it happens:** `@CircuitBreaker` requires AOP proxy. Without `spring-boot-starter-aop` in the classpath, Spring does not create proxies and annotations are decoration only.
**How to avoid:** Add `spring-boot-starter-aop` to pom.xml. Verify actuator `/actuator/health` shows `circuitBreakers` section after startup.
**Warning signs:** CB never enters OPEN state regardless of failures; fallback method never called.

### Pitfall 4: Wrong Spring AI Artifact Name
**What goes wrong:** `Could not find artifact org.springframework.ai:spring-ai-anthropic-spring-boot-starter:jar:1.1.5`.
**Why it happens:** Pre-GA Spring AI used `spring-ai-anthropic-spring-boot-starter`. GA 1.0.0+ renamed it to `spring-ai-starter-model-anthropic`. The CLAUDE.md tech stack references the old name from an earlier research pass.
**How to avoid:** Use `spring-ai-starter-model-anthropic` via the `spring-ai-bom:1.1.5` BOM. The BOM eliminates version conflicts.
**Warning signs:** Maven build fails with artifact not found; dependency tree shows no spring-ai artifacts.

### Pitfall 5: PHI Boundary Violation in Alert Generation Prompt
**What goes wrong:** Patient name or MRN appears in the Claude alert generation prompt — a HIPAA violation even if the BAA covers document classification, because the BAA agreement for alert generation is not assumed.
**Why it happens:** `PathwayEvaluationActivityImpl` has access to the `Patient` entity via the repository; it's easy to accidentally include PHI fields in the prompt string.
**How to avoid:** The alert generation Claude call receives ONLY: `cancerType`, `pathwayStepName`, `alertType`, `elapsedDays`, `windowDays`. These are all non-PHI. Add a Checkstyle rule or code review checklist item verifying no PHI field names in the `ClaudeAlertGenerationService` class.
**Warning signs:** Static analysis finds `patient.getFirstName()`, `patient.getMrn()`, or `patient.getDateOfBirth()` referenced in `ClaudeAlertGenerationService`; audit log shows alert prompt strings that contain patient identifiers.

### Pitfall 6: Tesseract Not Thread-Safe with Virtual Threads
**What goes wrong:** Intermittent `AccessViolationException` or garbled OCR results under concurrent load.
**Why it happens:** `Tesseract` instances maintain internal JNA pointers. Virtual threads with `spring.threads.virtual.enabled=true` can run concurrent requests on the same instance, causing native-level corruption.
**How to avoid:** Scope the `Tesseract` bean as `@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)` and create a new instance per extraction call, or use a pool (`BlockingQueue<Tesseract>` with fixed size). Dispose after use.
**Warning signs:** OCR results are inconsistent for identical inputs; `SIGSEGV` in JVM crash logs.

### Pitfall 7: Large PDF Fully Loaded into Heap (bytea memory spike)
**What goes wrong:** OOM or long GC pauses when loading multi-page scanned PDFs.
**Why it happens:** `@Lob byte[]` loads the entire BLOB into heap. PDFBox also holds the parsed document in memory during rendering. A 30-page scanned radiology report at 300 DPI can be 50+ MB in memory.
**How to avoid:** Enforce a file size limit at upload (20 MB shown in react-dropzone example). Process pages sequentially rather than all at once. For OCR, render and process one page at a time, then GC.
**Warning signs:** Heap grows linearly with concurrent uploads; GC logs show frequent full-GC after document upload requests.

### Pitfall 8: Zod v4 API Differences in Frontend Schemas
**What goes wrong:** `z.string().min(1, { error: '...' })` works (Zod v4), but copying patterns from online docs that use `z.string().min(1, 'message')` (Zod v3 shorthand) causes TypeScript errors.
**Why it happens:** The project uses `zod: ^4.4.1` (confirmed in package.json). Most tutorials reference Zod v3. The error message API changed.
**How to avoid:** Use `z.string().min(1, { error: 'message' })` syntax for all new schemas. Check existing `QuickAddCareEventDialog.tsx` — it uses the v4 pattern already (`error:` key).
**Warning signs:** TypeScript error: "Argument of type 'string' is not assignable to parameter of type...".

---

## Code Examples

### Spring AI BOM Setup in pom.xml

```xml
<!-- Source: https://docs.spring.io/spring-ai/reference/getting-started.html [VERIFIED] -->
<dependencyManagement>
    <dependencies>
        <!-- existing testcontainers BOM -->
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>testcontainers-bom</artifactId>
            <version>1.21.3</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <!-- NEW: Spring AI BOM -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>1.1.5</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### ChatClient Bean Configuration

```java
// Source: https://docs.spring.io/spring-ai/reference/api/chatclient.html [VERIFIED]
@Configuration
public class AiConfig {

    @Bean
    public ChatClient claudeChatClient(AnthropicChatModel chatModel) {
        return ChatClient.builder(chatModel)
            .defaultOptions(AnthropicChatOptions.builder()
                .model("claude-opus-4-5")   // or claude-3-5-sonnet-latest
                .maxTokens(1024)
                .temperature(0.1)           // low temperature for classification
                .build())
            .build();
    }
}
```

### application-local.yml Spring AI section

```yaml
spring:
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY:sk-ant-placeholder}
      chat:
        options:
          model: claude-opus-4-5
          max-tokens: 1024
          temperature: 0.1
    retry:
      max-attempts: 3
      backoff:
        initial-interval: 1s
        multiplier: 2
        max-interval: 10s
      on-client-errors: false     # do not retry 4xx errors (bad prompt = client error)
```

### Zero-PHI Alert Prompt Template

```java
// ZERO-PHI boundary — verified against D-14 constraint
private static final String ALERT_PROMPT_TEMPLATE = """
    You are helping a nurse navigator in a medical oncology practice understand a care deviation.
    Generate a plain-language description (1-2 sentences) and a suggested corrective action.
    
    Clinical context:
    - Cancer type: {cancerType}
    - Pathway step: {stepName}
    - Deviation type: {alertType}
    - Days elapsed since expected event: {elapsedDays}
    - Expected within: {windowDays} days
    
    Do NOT include any patient identifiers. Focus on the process deviation, not the patient.
    """;
```

### Document Entity Schema (Flyway V9)

```sql
-- V9__create_clinical_documents.sql
CREATE TABLE clinical_documents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    care_event_id   UUID REFERENCES care_events(id) ON DELETE SET NULL,
    patient_id      UUID NOT NULL,                    -- denormalized for direct lookup
    original_filename TEXT NOT NULL,
    content_type    TEXT NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    document_type   TEXT,                             -- classified type (nullable: classification may fail)
    classification_source TEXT,                       -- 'claude' | 'manual' | 'unclassified'
    content         BYTEA NOT NULL,                   -- the file blob
    extracted_text  TEXT,                             -- cached extraction (NULL if extraction failed)
    extraction_confidence INT,                        -- Tesseract meanConfidence (NULL for PDFBox path)
    created_by      UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_clinical_documents_patient_id ON clinical_documents(patient_id);
CREATE INDEX idx_clinical_documents_care_event_id ON clinical_documents(care_event_id);
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `spring-ai-anthropic-spring-boot-starter` | `spring-ai-starter-model-anthropic` | Spring AI 1.0.0 GA (May 2025) | Direct dependency on old artifact fails at Maven resolution for 1.0.0+ |
| `PDDocument.load()` | `Loader.loadPDF(RandomAccessReadBuffer)` | PDFBox 3.0.0 | All 2.x code examples are broken on 3.x |
| Resilience4j 1.x (Spring Boot 2 starter) | `resilience4j-spring-boot3:2.3.0` | Resilience4j 2.0 | Separate `spring-boot3` artifact required; 1.x starter incompatible with Spring Boot 3 |
| `ChatGPT` / `openai` for structured JSON | `ChatClient.entity(MyClass.class)` | Spring AI 1.0.0 | BeanOutputConverter generates JSON Schema + formats prompt; entity() handles deserialization |
| `react-dropzone` v11-12 with Hooks API | `react-dropzone` v15.0.0 | Progressive (14→15) | API is stable; 15.x is current; `accept` prop accepts object format `{'image/png': ['.png']}` not string |

**Deprecated/outdated:**
- `spring-ai-anthropic-spring-boot-starter`: Only reached M6 under old naming; use `spring-ai-starter-model-anthropic`
- `PDDocument.load()`: Removed in PDFBox 3.0; any codebase on 3.x using this will fail at runtime
- Resilience4j `1.x` starters: Not compatible with Spring Boot 3; use `2.x` `-spring-boot3` artifact

---

## Runtime State Inventory

> This phase is greenfield for new components (document ingestion, Claude integration). It modifies `PathwayEvaluationActivityImpl` in-place. No rename or migration of existing runtime state.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | No existing clinical_documents records (new table) | None — V9 migration creates empty table |
| Live service config | No existing Claude API configuration in Temporal workers or DB | Add Spring AI config to application-local.yml; add `documentProcessingActivityImpl` to Temporal worker activity-beans list if activity approach is chosen |
| OS-registered state | None | None |
| Secrets/env vars | `ANTHROPIC_API_KEY` — new env var required | Add to `.env` (gitignored); Jasypt-encrypt for local dev; AWS Secrets Manager for prod |
| Build artifacts | None — pure source additions | None |

---

## Open Questions (RESOLVED)

1. **Tesseract Thread Safety Strategy** -- RESOLVED
   - What we know: Tess4J `Tesseract` instances are not thread-safe. Virtual threads are enabled (`spring.threads.virtual.enabled=true`).
   - What's unclear: Whether prototype-scoped bean (new instance per call) is acceptable given JNA initialization overhead, or whether a fixed pool (e.g., 3 instances) is needed.
   - Recommendation: Start with prototype scope (simpler); benchmark with realistic concurrent load before complicating with a pool.
   - **Resolution:** Plan 04-03 implements per-call Tesseract instance creation (prototype pattern). OcrExtractionService creates a new Tesseract instance per `performOcr()` call -- no shared bean, no pool. Follows recommendation.

2. **Anthropic BAA Status** -- RESOLVED
   - What we know: BAA is required before live PHI reaches the document classification endpoint. Code can be built and tested with synthetic data.
   - What's unclear: Whether the BAA process has been initiated (STATE.md notes it should have started during Phase 1-2).
   - Recommendation: Document classification endpoint should have a feature flag (`onconavigator.ai.document-classification.enabled=false`) to disable Claude calls if BAA is not yet in place, falling back to manual classification.
   - **Resolution:** Plan 04-01 adds `onconavigator.ai.document-classification.enabled: false` to application-local.yml. Plan 04-03 gates `DocumentClassificationService.classify()` behind this flag -- when disabled, returns null and UI shows manual classification dropdown. BAA remains a production prerequisite; code can be built and tested without it.

3. **Test Corpus Generation Approach** -- RESOLVED
   - What we know: DOC-01 requires a test corpus of synthetic PDFs covering 5 document types for 3 cancer types.
   - What's unclear: Best tooling — Synthea exports FHIR, not PDFs. Custom Python scripts using `fpdf2` or `reportlab` are more direct for generating realistic clinical PDF layouts.
   - Recommendation: Use Python `fpdf2` to generate 3-5 PDFs per document type (15-25 total), with clearly synthetic patient identifiers (MRN = TEST-001 format). Store in `test-corpus/` at repo root.
   - **Resolution:** Plan 04-02 creates 16 synthetic clinical text files (not Python-generated PDFs) in `test-corpus/` organized by document type. Text format chosen over PDF generation for simplicity -- the extraction pipeline handles the text-to-classification step, and text files are directly usable for unit testing without PDF rendering overhead. Synthetic identifiers follow TEST-001 through TEST-016 format.

4. **Model Selection for Document Classification** -- RESOLVED
   - What we know: `claude-opus-4-5` is the default in Spring AI 1.1.5. `claude-3-5-sonnet-latest` offers better speed/cost for extraction tasks.
   - What's unclear: Which model achieves adequate accuracy on clinical PDF text (particularly degraded fax content).
   - Recommendation: Start with `claude-3-5-sonnet-latest` (faster, cheaper) for classification; use `claude-opus-4-5` or higher only if accuracy on the test corpus is inadequate.
   - **Resolution:** Plans 04-01 and 04-03 configure `claude-sonnet-4-20250514` as the model for both classification and alert generation ChatClient beans. This follows the recommendation to start with a cost-effective Sonnet-class model; upgrade to Opus only if accuracy on the test corpus is inadequate.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Docker | Local dev compose | ✓ | 29.2.0 | — |
| Java 21 | Spring Boot runtime | ✓ | OpenJDK 21.0.5 | — |
| Node.js | Frontend build | ✓ | 22.21.1 | — |
| Tesseract OCR (native) | Tess4J OCR path | ✗ (not installed) | — | Must be in Docker image; add to Dockerfile |
| Anthropic API key | Spring AI Claude calls | ✗ (not configured) | — | Use `sk-ant-placeholder` for compilation; real key for integration tests |
| Anthropic BAA | Document classification in production | ✗ (not executed) | — | Feature flag to disable Claude doc classification; manual dropdown fallback |

**Missing dependencies with no fallback:**
- Tesseract native library: must be added to Docker Compose app service image (Dockerfile or docker-compose apt-get step). Without it, the OCR code path throws `UnsatisfiedLinkError` at runtime.

**Missing dependencies with fallback:**
- `ANTHROPIC_API_KEY`: development proceeds with placeholder; `classifyFallback()` returns null and UI shows manual dropdown.
- Anthropic BAA: feature flag (`onconavigator.ai.document-classification.enabled`) gates live PHI to Claude; document ingestion still works (manual classification + PDF attachment).

---

## Validation Architecture

> `nyquist_validation: false` in `.planning/config.json` — this section is omitted.

---

## Security Domain

`security_enforcement: true`, `security_asvs_level: 1` in `.planning/config.json`.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | Keycloak OIDC + `@AuthenticationPrincipal Jwt` on upload endpoint |
| V3 Session Management | no (stateless JWT) | Existing control from Phase 1 |
| V4 Access Control | yes | `@PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('ADMIN')")` on upload; `isAuthenticated()` on download |
| V5 Input Validation | yes | File size limit (20 MB), MIME type validation (accept PDF/JPEG/PNG only), `@Valid` on any request body fields |
| V6 Cryptography | yes | `ClinicalDocument.content` is PHI-bearing — it's the literal document. Storage encryption via RDS KMS at rest. Extraction result (`extracted_text`) may contain PHI — also encrypted at rest via RDS KMS. Column-level encryption is NOT applied to `content` (bytea blob) since AES-GCM converter operates on String; rely on storage-level encryption + access control. |
| V7 Error Handling | yes | Circuit breaker fallback must not expose Claude error details to client; log internally only |
| V8 Data Protection | yes (critical) | Two PHI boundaries: (1) document classification → full PHI → BAA required; (2) alert generation → zero PHI → no BAA requirement. These MUST be implemented as separate code paths. |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Upload arbitrary executable content disguised as PDF | Tampering | MIME type validation (content-type header + file magic bytes check via Apache Tika or content inspection); `accept` prop on frontend is UX only, not security |
| PHI leakage via Tesseract extraction result in logs | Information disclosure | Never log `extractedText`; existing Logback PHI redaction config |
| Unauthorized document access (BOLA) | Information disclosure | `GET /api/documents/{id}/content` must verify `document.patientId` is accessible to the requesting user's role before returning bytes |
| Patient ID forgery in multipart upload | Tampering | `patientId` from URL/param must be verified server-side against the authenticated user's permitted patients; verify patient exists before linking document |
| Claude prompt injection via document content | Tampering | Not a primary concern for classification (output is structured via JSON Schema); more relevant for alert generation, which accepts only fixed non-user-controlled parameters |
| File size DoS | Denial of service | 20 MB limit at `MultipartConfigElement` level (`spring.servlet.multipart.max-file-size=20MB`) and at react-dropzone `maxSize` |

### PHI Boundary Enforcement Checklist

- [ ] `ClaudeDocumentService.classify()` — accepts only document text (may contain PHI). Confirm BAA exists before enabling in prod.
- [ ] `ClaudeAlertGenerationService.generateAlertText()` — accepts ONLY `cancerType`, `pathwayStepName`, `alertType`, `elapsedDays`, `windowDays`. Code review must confirm no PHI fields referenced.
- [ ] `ClinicalDocument.extracted_text` — Hibernate Envers `@Audited` required (ePHI in extracted text).
- [ ] `DocumentUploadController` audit log — log document ID and patient UUID only, never filename content or extracted text.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Tess4J `Tesseract` instance produces `meanConfidence` via `TessBaseAPIMeanTextConf`; confidence < 60 is a reasonable threshold for Claude vision escalation | Architecture Patterns | If the API call signature differs in Tess4J 5.16.0, confidence scoring requires alternative implementation; threshold may need calibration against actual fax quality |
| A2 | `pdfbox-tools` module is required for `PDFRenderer` in PDFBox 3.x | Standard Stack | If `pdfbox` includes renderer directly, `pdfbox-tools` adds unnecessary dependency; verify at build time |
| A3 | Claude vision API via Spring AI `Media(IMAGE_JPEG, bytes)` works for scanned document images with sufficient accuracy for clinical document classification | Architecture Patterns | If vision extraction quality is inadequate for low-DPI faxes, the blank wizard fallback path (D-04) is more frequently triggered than expected |
| A4 | `fpdf2` or `reportlab` Python libraries are sufficient to generate realistic-looking synthetic clinical PDFs for the test corpus (DOC-01) | Open Questions | If generated PDFs don't represent realistic clinical formatting, test corpus is less useful for validating extraction pipeline |
| A5 | Resilience4j `@CircuitBreaker` fallback method can return `null` and the caller handles null gracefully | Code Examples | If the caller doesn't null-check the fallback return, a NullPointerException replaces the Claude exception — same symptom, harder to debug |

---

## Sources

### Primary (HIGH confidence)
- `repo1.maven.org` — Spring AI BOM 1.1.5 confirmed (HTTP 200 on POM), BOM contents inspected for artifact names
- `repo1.maven.org` — Resilience4j 2.3.0 confirmed (HTTP 200 on JAR)
- `repo1.maven.org` — PDFBox 3.0.7 confirmed via Maven Central search API
- `repo1.maven.org` — Tess4J 5.16.0 confirmed via Maven Central search API
- `npmjs.com` — react-dropzone 15.0.0 confirmed via registry API
- [Spring AI Anthropic Chat docs](https://docs.spring.io/spring-ai/reference/api/chat/anthropic-chat.html) — ChatClient API, model IDs, configuration properties, multimodal support
- [Spring AI Structured Output docs](https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html) — BeanOutputConverter, entity() method
- [PDFBox 3.0 Migration Guide](https://pdfbox.apache.org/3.0/migration.html) — Loader.loadPDF(), RandomAccessReadBuffer pattern
- Context7 `/apache/pdfbox` — PDFTextStripper usage, PDFRenderer.renderImageWithDPI(), Loader.loadPDF()
- [Anthropic BAA page](https://privacy.claude.com/en/articles/8114513-business-associate-agreements-baa-for-commercial-customers) — BAA coverage scope (Messages API covered; Batch/Files API not covered)

### Secondary (MEDIUM confidence)
- [Resilience4j Spring Boot 3 Getting Started](https://resilience4j.readme.io/docs/getting-started-3) — dependency list, circuit breaker YAML configuration pattern
- [Spring AI releases page](https://github.com/spring-projects/spring-ai/releases) — version history confirming 1.1.x is GA (1.1.5 released 2026-04-27)
- Spring AI 1.1.5 release blog post — maintenance release, no breaking changes from 1.1.x

### Tertiary (LOW confidence)
- Tess4J confidence scoring via `TessBaseAPIMeanTextConf` — confirmed as the API method; exact behavior with Tess4J 5.16.0 not directly tested in this session [ASSUMED threshold of 60]
- Test corpus generation approach (fpdf2/reportlab) — general knowledge; specific PDF layout for clinical documents not verified [ASSUMED]

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all versions verified against Maven Central and npm; artifact name discrepancy (`spring-ai-anthropic-spring-boot-starter` vs `spring-ai-starter-model-anthropic`) resolved via live BOM inspection
- Architecture: HIGH — patterns drawn from official Spring AI docs, PDFBox migration guide, and existing codebase patterns
- Pitfalls: HIGH for PDFBox 3.x and Spring AI artifact; MEDIUM for Tess4J thread safety and Resilience4j AOP; documented limitations where applicable
- Security domain: HIGH — PHI boundary analysis derived directly from locked decisions D-13/D-14

**Research date:** 2026-05-01
**Valid until:** 2026-06-01 for Spring AI (fast-moving); 2026-11-01 for PDFBox/Tess4J/Resilience4j (stable)
