# Phase 4: AI Document Ingestion & Alert Enhancement — Pattern Map

**Mapped:** 2026-05-01
**Files analyzed:** 18 new/modified files
**Analogs found:** 16 / 18

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `src/main/java/.../domain/ClinicalDocument.java` | model | file-I/O | `src/.../domain/CareEvent.java` | exact |
| `src/main/java/.../web/DocumentUploadController.java` | controller | file-I/O | `src/.../web/CareEventController.java` | role-match |
| `src/main/java/.../web/dto/DocumentUploadResponse.java` | model | request-response | `src/.../web/dto/CareEventResponse.java` | exact |
| `src/main/java/.../service/DocumentProcessingService.java` | service | file-I/O | `src/.../service/PatientService.java` | role-match |
| `src/main/java/.../service/PdfExtractionService.java` | service | file-I/O | `src/.../service/PatientService.java` | partial |
| `src/main/java/.../service/OcrExtractionService.java` | service | file-I/O | `src/.../service/PatientService.java` | partial |
| `src/main/java/.../service/ClaudeDocumentService.java` | service | request-response | `src/.../activity/AlertGenerationActivityImpl.java` | partial |
| `src/main/java/.../config/AiConfig.java` | config | request-response | `src/.../config/EncryptionConfig.java` | role-match |
| `src/main/resources/db/migration/V9__create_clinical_documents.sql` | migration | CRUD | `src/.../db/migration/V8__add_mrn_hmac_token.sql` | exact |
| `src/main/resources/application-local.yml` (modify) | config | — | existing `application-local.yml` | exact |
| `src/main/java/.../activity/PathwayEvaluationActivityImpl.java` (modify) | service | event-driven | self | exact |
| `src/main/java/.../activity/AlertGenerationActivityImpl.java` (modify) | service | event-driven | self | exact |
| `frontend/src/features/documents/DocumentDropZone.tsx` | component | file-I/O | `frontend/.../features/alerts/ResolveAlertModal.tsx` | partial |
| `frontend/src/features/documents/DocumentProcessingModal.tsx` | component | event-driven | `frontend/.../features/alerts/ResolveAlertModal.tsx` | role-match |
| `frontend/src/features/documents/DocumentPatientMatchPanel.tsx` | component | request-response | `frontend/.../features/patients/QuickAddCareEventDialog.tsx` | role-match |
| `frontend/src/features/documents/api.ts` | utility | file-I/O | `frontend/.../features/patients/api.ts` | exact |
| `frontend/src/features/documents/types.ts` | model | — | `frontend/.../features/patients/types.ts` | exact |
| `frontend/src/lib/api-client.ts` (modify) | utility | file-I/O | self | exact |

---

## Pattern Assignments

### `src/main/java/com/onconavigator/domain/ClinicalDocument.java` (model, file-I/O)

**Analog:** `src/main/java/com/onconavigator/domain/CareEvent.java`

**Imports pattern** (CareEvent.java lines 1–21):
```java
package com.onconavigator.domain;

import com.onconavigator.domain.enums.CareEventStatus;
import com.onconavigator.domain.enums.CareEventType;
import com.onconavigator.security.EncryptionConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.envers.Audited;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
```

**Entity declaration + audit pattern** (CareEvent.java lines 35–38):
```java
@Entity
@Table(name = "care_events")
@Audited      // <-- REQUIRED on all ePHI-touching entities; creates _AUD table via Envers
public class CareEvent {
```

**UUID primary key pattern** (CareEvent.java lines 40–42):
```java
@Id
@GeneratedValue(strategy = GenerationType.UUID)
private UUID id;
```

**FK relationship pattern** (CareEvent.java lines 44–47):
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "patient_id", nullable = false)
private Patient patient;
```

**Encrypted PHI field pattern** (CareEvent.java lines 61–65):
```java
// For any String column that may contain PHI (notes, extracted_text):
@Convert(converter = EncryptionConverter.class)
@Column(name = "notes_encrypted", columnDefinition = "bytea")
private String notes;
```

**NOTE for ClinicalDocument:** The `content` (BYTEA blob) is `byte[]` not `String` — do NOT apply `EncryptionConverter` to it (converter operates on String). Rely on RDS KMS for storage-level encryption. The `extracted_text` field is PHI-bearing String — apply `@Convert(converter = EncryptionConverter.class)` to it (column: `extracted_text_encrypted`, columnDefinition = "bytea").

**Timestamp lifecycle pattern** (CareEvent.java lines 79–88):
```java
@PrePersist
void prePersist() {
    OffsetDateTime now = OffsetDateTime.now();
    this.createdAt = now;
    this.updatedAt = now;
}

@PreUpdate
void preUpdate() {
    this.updatedAt = OffsetDateTime.now();
}
```

---

### `src/main/java/com/onconavigator/web/DocumentUploadController.java` (controller, file-I/O)

**Analog:** `src/main/java/com/onconavigator/web/CareEventController.java`

**Imports + class declaration pattern** (CareEventController.java lines 1–40):
```java
package com.onconavigator.web;

import com.onconavigator.service.PatientService;
import com.onconavigator.web.dto.CareEventResponse;
import com.onconavigator.web.dto.CreateCareEventRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
public class DocumentUploadController {

    private final DocumentProcessingService documentProcessingService;

    public DocumentUploadController(DocumentProcessingService documentProcessingService) {
        this.documentProcessingService = documentProcessingService;
    }
```

**Auth + JWT actor extraction pattern** (CareEventController.java lines 71–79):
```java
@PostMapping
@PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('ADMIN')")
@ResponseStatus(HttpStatus.CREATED)
public CareEventResponse addCareEvent(
        @PathVariable UUID patientId,
        @Valid @RequestBody CreateCareEventRequest request,
        @AuthenticationPrincipal Jwt jwt) {
    UUID actorId = UUID.fromString(jwt.getSubject());
    return patientService.addCareEvent(patientId, request, actorId);
}
```

**Multipart upload endpoint deviation** — the upload handler differs from JSON POST: use `@RequestParam("file") MultipartFile file` instead of `@RequestBody`, and `consumes = MediaType.MULTIPART_FORM_DATA_VALUE`. Byte-streaming download endpoint uses `ResponseEntity<byte[]>` with explicit `Content-Type` and `Content-Disposition` headers.

**Logging pattern** (all controllers/services): Log only UUIDs, never PHI field values. Pattern: `log.info("Uploaded document {} for patient {}", documentId, patientId)`.

---

### `src/main/java/com/onconavigator/web/dto/DocumentUploadResponse.java` (model, request-response)

**Analog:** `src/main/java/com/onconavigator/web/dto/CareEventResponse.java`

Read `src/main/java/com/onconavigator/web/dto/CareEventResponse.java` for the Java record pattern used for all response DTOs. All response DTOs in this project are Java records with camelCase field names that Jackson serializes to camelCase JSON. No `@JsonProperty` annotations needed.

---

### `src/main/java/com/onconavigator/service/DocumentProcessingService.java` (service, file-I/O)

**Analog:** `src/main/java/com/onconavigator/service/PatientService.java`

**Service class declaration pattern** (PatientService.java lines 39–59):
```java
@Service
public class PatientService {

    private static final Logger log = LoggerFactory.getLogger(PatientService.class);

    private final PatientRepository patientRepository;
    private final CareEventRepository careEventRepository;
    private final AlertRepository alertRepository;
    private final PathwayService pathwayService;
    private final HmacTokenService hmacTokenService;

    public PatientService(PatientRepository patientRepository,
                          CareEventRepository careEventRepository,
                          AlertRepository alertRepository,
                          PathwayService pathwayService,
                          HmacTokenService hmacTokenService) {
        this.patientRepository = patientRepository;
        // ... assign all fields
    }
```

**HMAC MRN lookup pattern** (PatientService.java lines 135–141) — reuse exactly for document-to-patient MRN matching:
```java
public List<PatientResponse> findByMrn(String mrn) {
    String hmacToken = hmacTokenService.computeMrnToken(mrn);
    return patientRepository.findByMrnHmacToken(hmacToken)
            .map(this::toPatientResponse)
            .map(List::of)
            .orElse(List.of());
}
```

**In-memory name+DOB matching pattern** (PatientService.java lines 107–110) — used for fallback when MRN not found. Load all patients via `patientRepository.findAll()`, stream, decrypt fields (already decrypted by `EncryptionConverter` on load), compare strings case-insensitively. Acceptable at pilot scale per D-08.

**Error handling pattern** (PatientService.java lines 120–123):
```java
Patient patient = patientRepository.findById(patientId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));
```

**PHI log safety pattern** (PatientService.java lines 97, 189):
```java
// CORRECT — log UUIDs only
log.info("Created patient {} and started pathway monitoring", saved.getId());
log.info("Added care event {} for patient {}", saved.getId(), patientId);

// NEVER log: patient.getFirstName(), patient.getMrn(), extractedText, document content
```

---

### `src/main/java/com/onconavigator/service/PdfExtractionService.java` (service, file-I/O)

**Analog:** `src/main/java/com/onconavigator/service/PatientService.java` (class shape only; no existing PDF analog)

**Class shape pattern:** Same `@Service` + constructor injection + `Logger` declaration as `PatientService`. No `@Transactional` — this service is CPU-bound, no DB writes. Inject no repositories.

**PDFBox 3.x API** (from RESEARCH.md Pattern 2 — no existing codebase analog):
```java
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.io.RandomAccessReadBuffer;

// ALWAYS use Loader.loadPDF() — PDDocument.load() was removed in PDFBox 3.x
public String extractText(byte[] pdfBytes) throws IOException {
    try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(pdfBytes))) {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setSortByPosition(true);
        return stripper.getText(document);
    }
}
```

**Error wrapping pattern** (match `EncryptionConverter.java` lines 107–109): Wrap checked exceptions in an unchecked domain exception with a non-PHI message.

---

### `src/main/java/com/onconavigator/service/OcrExtractionService.java` (service, file-I/O)

**Analog:** `src/main/java/com/onconavigator/service/PatientService.java` (class shape only)

**Class shape pattern:** `@Service` + constructor injection. Inject `Tesseract` bean as `@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)` or create per-call to avoid thread-safety issues with virtual threads (see RESEARCH.md Pitfall 6).

**Tess4J OCR pattern** (from RESEARCH.md Pattern 4 — no existing codebase analog):
```java
// OcrResult as a Java record (same convention as other DTOs)
public record OcrResult(String text, int meanConfidence) {}

// Confidence threshold 60 — below this, escalate to Claude vision
public static final int OCR_CONFIDENCE_THRESHOLD = 60;
```

---

### `src/main/java/com/onconavigator/service/ClaudeDocumentService.java` (service, request-response)

**Analog:** `src/main/java/com/onconavigator/activity/AlertGenerationActivityImpl.java` (PHI logging pattern, `@Component` shape)

**Component shape pattern** (AlertGenerationActivityImpl.java lines 29–38):
```java
@Component
public class AlertGenerationActivityImpl implements AlertGenerationActivity {

    private static final Logger log = LoggerFactory.getLogger(AlertGenerationActivityImpl.class);

    private final AlertRepository alertRepository;

    public AlertGenerationActivityImpl(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }
```

**PHI safety comment pattern** (AlertGenerationActivityImpl.java lines 26–27):
```java
// PHI safety: All parameters are non-PHI. The deviationDescription and suggestedAction
// parameters contain pathway template text, not patient-specific data.
```

**For `ClaudeDocumentService`:** Use `@Service` (not `@Component`) to match the service layer. Two public methods:
1. `classify(String extractedText)` — full PHI path, BAA-covered, annotated `@CircuitBreaker`
2. `generateAlertDescription(String cancerType, String stepName, String alertType, long elapsedDays)` — ZERO PHI path, no BAA required, also annotated `@CircuitBreaker`

**Circuit breaker annotation pattern** (from RESEARCH.md Pattern 5):
```java
@CircuitBreaker(name = "claude-api", fallbackMethod = "classifyFallback")
public DocumentClassificationResult classify(String extractedText) {
    return chatClient.prompt()
        .system(CLASSIFICATION_SYSTEM_PROMPT)
        .user(extractedText)
        .call()
        .entity(DocumentClassificationResult.class);
}

// Fallback: same signature + Exception as final parameter
public DocumentClassificationResult classifyFallback(String extractedText, Exception e) {
    log.warn("Claude classification CB open: {}", e.getMessage());
    return null; // frontend shows manual classification dropdown
}
```

**Fallback logging pattern** (match PatientService.java lines 93–95):
```java
// Log warning with exception message only — never log extractedText content
log.warn("Claude classification CB open, returning null classification: {}", e.getMessage());
```

---

### `src/main/java/com/onconavigator/config/AiConfig.java` (config, request-response)

**Analog:** `src/main/java/com/onconavigator/config/EncryptionConfig.java`

Read `src/main/java/com/onconavigator/config/EncryptionConfig.java` for the `@Configuration` + `@Bean` + `@Value` injection pattern used by all config classes. The Spring AI `ChatClient` bean follows the same constructor-style factory pattern as `SecretKey` bean in `EncryptionConfig`.

**ChatClient bean pattern** (from RESEARCH.md Code Examples):
```java
@Configuration
public class AiConfig {

    @Bean
    public ChatClient claudeChatClient(AnthropicChatModel chatModel) {
        return ChatClient.builder(chatModel)
            .defaultOptions(AnthropicChatOptions.builder()
                .model("claude-3-5-sonnet-latest")
                .maxTokens(1024)
                .temperature(0.1)
                .build())
            .build();
    }
}
```

---

### `src/main/resources/db/migration/V9__create_clinical_documents.sql` (migration, CRUD)

**Analog:** `src/main/resources/db/migration/V8__add_mrn_hmac_token.sql` and `V1__create_base_schema.sql`

**Migration header comment pattern** (V8 lines 1–4):
```sql
-- V8__add_mrn_hmac_token.sql
-- Add deterministic HMAC index token for MRN equality search (per D-04).
-- MRN is AES-GCM encrypted with random IV making equality queries impossible.
-- HMAC-SHA256 token is deterministic and non-reversible — enables exact MRN lookup.
```

**Table creation pattern** (V1 lines 17–48) — UUID PK with `gen_random_uuid()`, `TIMESTAMPTZ NOT NULL DEFAULT now()`, FK with explicit `REFERENCES`:
```sql
CREATE TABLE care_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL REFERENCES patients(id),
    ...
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by UUID NOT NULL
);
```

**Index pattern** (V8 line 7, V1 line 60):
```sql
CREATE INDEX idx_clinical_documents_patient_id ON clinical_documents(patient_id);
CREATE INDEX idx_clinical_documents_care_event_id ON clinical_documents(care_event_id);
```

---

### `src/main/resources/application-local.yml` (config, modify)

**Analog:** existing `src/main/resources/application-local.yml`

**Property hierarchy pattern** (application-local.yml lines 1–60) — new Spring AI and Resilience4j blocks slot into the existing YAML structure:
```yaml
# Add under existing 'spring:' key:
spring:
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY:sk-ant-placeholder}
      chat:
        options:
          model: claude-3-5-sonnet-latest
          max-tokens: 1024
          temperature: 0.1
    retry:
      max-attempts: 3
      backoff:
        initial-interval: 1s
        multiplier: 2
        max-interval: 10s
      on-client-errors: false
  servlet:
    multipart:
      max-file-size: 20MB
      max-request-size: 22MB

# Add as top-level key:
resilience4j:
  circuitbreaker:
    instances:
      claude-api:
        slidingWindowSize: 10
        minimumNumberOfCalls: 3
        failureRateThreshold: 50
        waitDurationInOpenState: 30s
        permittedNumberOfCallsInHalfOpenState: 2
        automaticTransitionFromOpenToHalfOpenEnabled: true
        registerHealthIndicator: true

# Add under existing 'onconavigator:' key:
onconavigator:
  ai:
    document-classification:
      enabled: false  # Feature flag — set to true only after Anthropic BAA is in place

# Add under existing 'spring.temporal.workers' activity-beans list:
# - documentProcessingActivityImpl  (if document processing is wrapped as a Temporal activity)
```

**Jasypt property pattern** (application-local.yml lines 53–60) — `ANTHROPIC_API_KEY` is injected via env var (`${ANTHROPIC_API_KEY:sk-ant-placeholder}`), not Jasypt-encrypted, matching how `APP_DB_PASSWORD` is handled for local dev.

---

### `src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java` (modify, event-driven)

**Analog:** self (existing file is the canonical pattern)

**Modification target — `buildAlert` method** (PathwayEvaluationActivityImpl.java lines 320–328):
```java
// Current single-branch method:
private Alert buildAlert(UUID patientId, PathwayStep step, AlertType alertType) {
    Alert alert = new Alert();
    alert.setPatientId(patientId);
    alert.setAlertType(alertType);
    alert.setPathwayStepName(step.name());
    alert.setDeviationDescription(step.alertText());    // <-- template text
    alert.setSuggestedAction(step.suggestedAction());   // <-- template text
    return alert;
}
```

**New two-branch pattern** — add an overload that accepts Claude-generated text for non-standard deviations (when `step.alertText()` is null or empty). The primary single-argument call remains unchanged (AI-01 template-first behavior). The Claude path is invoked only when template text is absent (AI-02/AI-03):
```java
// Keep existing buildAlert(patientId, step, alertType) unchanged for standard deviations
// Add new call site in evaluate() for non-standard case:
String deviationDescription = (step.alertText() != null && !step.alertText().isBlank())
    ? step.alertText()
    : claudeDocumentService.generateAlertDescription(
          patient.getCancerType().name(),
          step.name(),
          alertType.name(),
          elapsedDays);
```

**Injection pattern:** Add `ClaudeDocumentService` to the constructor parameters alongside existing repositories (lines 69–82 pattern).

---

### `frontend/src/features/documents/DocumentDropZone.tsx` (component, file-I/O)

**Analog:** `frontend/src/features/alerts/ResolveAlertModal.tsx` (Dialog + form with mutation)

**Import pattern** (ResolveAlertModal.tsx lines 1–17):
```typescript
import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
// ... other shadcn imports
import { useResolveAlert } from './api';
import type { AlertResponse } from './types';
```

**Zod v4 schema pattern** (ResolveAlertModal.tsx line 19–21 and QuickAddCareEventDialog.tsx lines 27–32):
```typescript
// CORRECT: Zod v4 — use { error: 'message' } not 'message' shorthand
const schema = z.object({
  notes: z.string().min(10, { error: 'Describe the action taken (minimum 10 characters).' }),
});
```

**Tailwind conditional class pattern** (from RESEARCH.md Pattern 7):
```typescript
// isDragActive drives the visual feedback — same pattern as conditional className in existing components
className={`border-2 border-dashed rounded-lg p-8 text-center
  ${isDragActive ? 'border-primary bg-primary/5' : 'border-muted-foreground/25'}`}
```

**Props interface pattern** (ResolveAlertModal.tsx lines 25–28):
```typescript
interface DocumentDropZoneProps {
  patientId?: string;          // undefined on dashboard, set on patient detail page (D-09)
  onUploadComplete: (result: DocumentUploadResponse) => void;
}
```

---

### `frontend/src/features/documents/DocumentProcessingModal.tsx` (component, event-driven)

**Analog:** `frontend/src/features/alerts/ResolveAlertModal.tsx`

**Dialog with read-only info panel pattern** (ResolveAlertModal.tsx lines 95–107):
```typescript
{/* Read-only summary panel — use bg-muted/40 border rounded-md for info blocks */}
<div className="rounded-md border bg-muted/40 p-4 space-y-2">
  <div className="flex items-center gap-2">
    <Badge variant={getSeverityBadgeVariant(alert.severityLabel)}>
      {alert.severityLabel}
    </Badge>
    <span className="font-medium text-sm">{alert.pathwayStepName}</span>
  </div>
  <p className="text-sm text-muted-foreground">{alert.deviationDescription}</p>
</div>
```

**onInteractOutside block pattern** (ResolveAlertModal.tsx line 88) — use on the processing modal to prevent dismissal during upload:
```typescript
<DialogContent
  onInteractOutside={(e) => e.preventDefault()}
>
```

**Mutation pending state pattern** (ResolveAlertModal.tsx lines 130–136):
```typescript
<Button type="submit" disabled={resolveAlert.isPending}>
  {resolveAlert.isPending ? 'Resolving...' : 'Resolve Alert'}
</Button>
```

---

### `frontend/src/features/documents/DocumentPatientMatchPanel.tsx` (component, request-response)

**Analog:** `frontend/src/features/patients/QuickAddCareEventDialog.tsx`

**Form with defaultValues pre-fill pattern** (QuickAddCareEventDialog.tsx lines 75–83):
```typescript
// Phase 4 pre-fills from extracted document data — same defaultValues injection point
const form = useForm<CareEventFormValues>({
  resolver: zodResolver(careEventSchema),
  defaultValues: {
    eventType: '',    // replace with extractedResult.eventType ?? ''
    eventDate: '',    // replace with extractedResult.eventDate ?? ''
    status: '',
    notes: '',
  },
});
```

**onSuccess close + reset pattern** (QuickAddCareEventDialog.tsx lines 93–99):
```typescript
{
  onSuccess: () => {
    form.reset();
    onOpenChange(false);
  },
}
```

**Select component value-change pattern** (QuickAddCareEventDialog.tsx lines 120–122):
```typescript
onValueChange={(value) =>
  form.setValue('eventType', value, { shouldValidate: true })
}
```

**Error display pattern** (QuickAddCareEventDialog.tsx lines 140–144):
```typescript
{form.formState.errors.eventType && (
  <p className="text-destructive text-xs">
    {form.formState.errors.eventType.message}
  </p>
)}
```

---

### `frontend/src/features/documents/api.ts` (utility, file-I/O)

**Analog:** `frontend/src/features/patients/api.ts`

**useQuery pattern** (patients/api.ts lines 9–15):
```typescript
export function useDocument(documentId: string) {
  return useQuery({
    queryKey: ['documents', documentId],
    queryFn: () => apiClient.get<DocumentResponse>(`/documents/${documentId}`),
  });
}
```

**useMutation + cache invalidation pattern** (patients/api.ts lines 26–35):
```typescript
export function useUploadDocument() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (formData: FormData) =>
      apiClient.upload<DocumentUploadResponse>('/documents/upload', formData),
    onSuccess: () => {
      // Invalidate care events — a new document may be linked to a care event
      queryClient.invalidateQueries({ queryKey: ['patients'] });
    },
  });
}
```

**Upload uses `apiClient.upload()` not `apiClient.post()`** — the new multipart method added to `api-client.ts` (see below). Do NOT use `apiClient.post()` for FormData.

---

### `frontend/src/features/documents/types.ts` (model)

**Analog:** `frontend/src/features/patients/types.ts`

**Interface pattern** (patients/types.ts lines 1–15):
```typescript
// All response interfaces mirror the Java record/DTO field names exactly (camelCase)
export interface DocumentUploadResponse {
  documentId: string;
  classificationResult: DocumentClassificationResult | null;
  patientMatchStatus: 'EXACT' | 'CANDIDATES' | 'NO_MATCH';
  candidates: PatientCandidate[];
}

export interface DocumentClassificationResult {
  documentType: string;
  mrn: string | null;
  patientName: string | null;
  dateOfBirth: string | null;
  eventDate: string | null;
  eventType: string | null;
  extractedDetails: string | null;
}

// Use discriminated union pattern (same as CancerType union in patients/types.ts)
export type DocumentType =
  | 'PATHOLOGY_REPORT'
  | 'RADIOLOGY_REPORT'
  | 'REFERRAL_LETTER'
  | 'OPERATIVE_NOTE'
  | 'LAB_RESULT'
  | 'UNKNOWN';
```

---

### `frontend/src/lib/api-client.ts` (modify, file-I/O)

**Analog:** self (existing file lines 1–50)

**Existing `request` function structure** (api-client.ts lines 15–33):
```typescript
async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getAccessToken();

  const response = await fetch(`${API_BASE}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  });

  if (!response.ok) {
    throw new ApiError(response.status, await response.text());
  }

  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}
```

**New `requestMultipart` function to add** — critical: do NOT set `Content-Type` header (browser sets `multipart/form-data; boundary=...` automatically):
```typescript
async function requestMultipart<T>(path: string, formData: FormData): Promise<T> {
  const token = getAccessToken();
  const response = await fetch(`${API_BASE}${path}`, {
    method: 'POST',
    body: formData,
    // NO Content-Type header — browser sets multipart/form-data with boundary automatically
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!response.ok) throw new ApiError(response.status, await response.text());
  return response.json() as Promise<T>;
}
```

**Add to `apiClient` export object** (api-client.ts lines 35–50):
```typescript
export const apiClient = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body: unknown) => request<T>(path, {
    method: 'POST',
    body: JSON.stringify(body),
  }),
  // ... existing methods ...
  upload: <T>(path: string, formData: FormData): Promise<T> =>
    requestMultipart<T>(path, formData),  // ADD THIS
};
```

---

## Shared Patterns

### Authentication and Authorization
**Source:** `src/main/java/com/onconavigator/web/CareEventController.java` lines 55, 71–72, 94–95
**Apply to:** `DocumentUploadController`
```java
// Read-only endpoints:
@PreAuthorize("isAuthenticated()")

// Write endpoints (upload):
@PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('ADMIN')")

// JWT actor extraction (all write endpoints):
@AuthenticationPrincipal Jwt jwt
UUID actorId = UUID.fromString(jwt.getSubject());
```

### Error Handling (Backend)
**Source:** `src/main/java/com/onconavigator/web/GlobalExceptionHandler.java` lines 35–94
**Apply to:** All new controllers and services
```java
// ResponseStatusException for known 4xx conditions:
throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found");

// Generic Exception handler catches all else — logs class name only:
log.error("Unhandled exception: {}", ex.getClass().getSimpleName());
// Returns: Map.of("error", "An internal error occurred")
// NEVER leak ex.getMessage() to client (may contain PHI)
```

### PHI Boundary Enforcement
**Source:** `src/main/java/com/onconavigator/activity/AlertGenerationActivityImpl.java` lines 26–27 and `PatientService.java` lines 36–37
**Apply to:** `ClaudeDocumentService`, `DocumentProcessingService`, all new log statements
```java
// PHI safety: Log statements contain ONLY UUIDs and event type codes.
// Patient names, DOBs, and MRNs must never appear in log statements.
// For ClaudeDocumentService.generateAlertDescription(): accepts ONLY
// cancerType, pathwayStepName, alertType, elapsedDays — ZERO PHI parameters.
```

### Entity Encryption (PHI String Fields)
**Source:** `src/main/java/com/onconavigator/security/EncryptionConverter.java` (full file)
**Apply to:** Any new `String` PHI column on `ClinicalDocument` (specifically `extracted_text`)
```java
@Convert(converter = EncryptionConverter.class)
@Column(name = "extracted_text_encrypted", columnDefinition = "bytea")
private String extractedText;
```

### Hibernate Envers Audit Trail
**Source:** `src/main/java/com/onconavigator/domain/CareEvent.java` line 36–38 and `Patient.java` line 36–38
**Apply to:** `ClinicalDocument` entity (ePHI-touching)
```java
@Entity
@Table(name = "clinical_documents")
@Audited   // Creates clinical_documents_AUD table — HIPAA audit requirement
public class ClinicalDocument {
```

### Frontend Query Key Naming
**Source:** `frontend/src/features/patients/api.ts` lines 11–14
**Apply to:** `frontend/src/features/documents/api.ts`
```typescript
// Convention: ['resource-type', id] or ['resource-type', { filter }]
queryKey: ['documents', documentId],
queryKey: ['patients', patientId, 'documents'],
```

### Frontend Error Display
**Source:** `frontend/src/features/patients/QuickAddCareEventDialog.tsx` lines 208–211
**Apply to:** `DocumentDropZone.tsx`, `DocumentPatientMatchPanel.tsx`
```typescript
{mutation.isError && (
  <p className="text-destructive text-sm">
    An error occurred while saving. Your changes were not saved. Please try again.
  </p>
)}
```

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `test-corpus/` (synthetic PDFs) | test data | file-I/O | No existing test data generation scripts in repo; RESEARCH.md recommends Python `fpdf2` — no codebase analog |
| `src/main/java/.../service/OcrExtractionService.java` (Tess4J specifics) | service | file-I/O | No OCR code exists anywhere in the codebase; patterns come from RESEARCH.md Pattern 4 only |

---

## Metadata

**Analog search scope:** `src/main/java/com/onconavigator/` (all packages), `frontend/src/features/`, `frontend/src/lib/`, `src/main/resources/`
**Files scanned:** 68 Java source files, 42 TypeScript/TSX files, 9 Flyway migrations, 3 YAML configs
**Pattern extraction date:** 2026-05-01

### Key Observations for Planner

1. **Single EncryptionConverter limitation:** `EncryptionConverter` operates on `String → byte[]`. The `ClinicalDocument.content` field is `byte[]` (raw PDF blob) — it cannot use `EncryptionConverter`. Storage-level encryption (RDS KMS) is the only protection for the blob. Only `extracted_text` (a String) gets column-level encryption.

2. **Temporal worker registration:** The existing `application-local.yml` lists `activity-beans` by name (lines 34–38). Any new `@Component` activity classes (if document processing is wrapped as a Temporal activity) must be added to this list under `onco-pathway-worker`.

3. **Zod v4 throughout:** All existing frontend schemas use Zod v4's `{ error: 'message' }` syntax (confirmed in `QuickAddCareEventDialog.tsx` line 28 and `ResolveAlertModal.tsx` line 20). RESEARCH.md Pitfall 8 confirms project is on `zod: ^4.4.1`.

4. **`@RestControllerAdvice` covers new controllers automatically:** `GlobalExceptionHandler` applies globally — `DocumentUploadController` gets error handling for free. No per-controller try/catch needed.

5. **Two PHI boundaries are architecturally separate code paths:** `ClaudeDocumentService.classify()` (full PHI, BAA-gated by feature flag) vs `ClaudeDocumentService.generateAlertDescription()` (zero PHI, no BAA needed). These MUST be separate methods with separate `@CircuitBreaker` fallback methods and separate audit log statements.
