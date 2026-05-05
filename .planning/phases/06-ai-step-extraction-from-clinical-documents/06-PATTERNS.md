# Phase 6: AI Step Extraction from Clinical Documents - Pattern Map

**Mapped:** 2026-05-04
**Files analyzed:** 12 new/modified files
**Analogs found:** 12 / 12

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/main/java/com/onconavigator/ai/model/ExtractionResult.java` | model | transform | `src/main/java/com/onconavigator/ai/model/DocumentClassification.java` | exact |
| `src/main/java/com/onconavigator/ai/prompt/ExtractionPrompts.java` | config | transform | `src/main/java/com/onconavigator/ai/prompt/ClassificationPrompts.java` | exact |
| `src/main/java/com/onconavigator/ai/service/StepExtractionService.java` | service | request-response | `src/main/java/com/onconavigator/ai/service/DocumentClassificationService.java` | exact |
| `src/main/java/com/onconavigator/service/StepExtractionTriggerService.java` | service | event-driven | `src/main/java/com/onconavigator/config/AsyncConfig.java` + `DocumentProcessingService.java` | role-match |
| `src/main/java/com/onconavigator/ai/config/AiClientConfig.java` | config | — | `src/main/java/com/onconavigator/ai/config/AiClientConfig.java` | exact (modify) |
| `src/main/java/com/onconavigator/domain/enums/PathwayStepStatus.java` | model | — | `src/main/java/com/onconavigator/domain/enums/PathwayStepStatus.java` | exact (modify) |
| `src/main/java/com/onconavigator/domain/PatientPathwayStep.java` | model | CRUD | `src/main/java/com/onconavigator/domain/PatientPathwayStep.java` | exact (modify) |
| `src/main/java/com/onconavigator/service/PatientPathwayService.java` | service | CRUD | `src/main/java/com/onconavigator/service/PatientPathwayService.java` | exact (modify) |
| `src/main/java/com/onconavigator/web/PatientPathwayController.java` | controller | request-response | `src/main/java/com/onconavigator/web/PatientPathwayController.java` | exact (modify) |
| `src/main/java/com/onconavigator/web/dto/PathwayStepResponse.java` | model | transform | `src/main/java/com/onconavigator/web/dto/PathwayStepResponse.java` | exact (modify) |
| `src/main/resources/db/migration/V16__add_rejected_status_and_ai_source.sql` | migration | CRUD | `src/main/resources/db/migration/V13__create_pathway_step_status_enum.sql` + `V14__create_per_patient_pathway_tables.sql` | role-match |
| `frontend/src/features/patients/types.ts` | model | transform | `frontend/src/features/patients/types.ts` | exact (modify) |
| `frontend/src/features/patients/api.ts` | utility | request-response | `frontend/src/features/patients/api.ts` | exact (modify) |
| `frontend/src/features/patients/StepRow.tsx` | component | event-driven | `frontend/src/features/patients/StepRow.tsx` | exact (modify) |
| `frontend/src/features/patients/PathwayEditor.tsx` | component | event-driven | `frontend/src/features/patients/PathwayEditor.tsx` | exact (modify) |

---

## Pattern Assignments

### `src/main/java/com/onconavigator/ai/model/ExtractionResult.java` (model, transform)

**Analog:** `src/main/java/com/onconavigator/ai/model/DocumentClassification.java`

**Imports pattern** (lines 1–7):
```java
package com.onconavigator.ai.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
```

**Core pattern — structured output record** (lines 17–28):
```java
// Spring AI's ChatClient.entity() generates JSON Schema from this record.
// @JsonPropertyOrder keeps fields deterministic for schema generation.
// CRITICAL: declare eventType as String, NOT as CareEventType enum.
// Jackson throws InvalidDefinitionException if Claude returns an unrecognised enum value.
// Service layer validates and maps the String to CareEventType after deserialization.
@JsonPropertyOrder({"documentType", "confidence", "mrn", "patientName",
                     "dateOfBirth", "eventType", "eventDate", "extractedNotes"})
public record DocumentClassification(
    @JsonProperty(required = true, value = "documentType") String documentType,
    @JsonProperty(required = true, value = "confidence") String confidence,
    @JsonProperty(value = "mrn") String mrn,
    ...
) {}
```

**What ExtractionResult.java must replicate:**
- Top-level record with `@JsonPropertyOrder` listing all fields in schema-generation order
- Required fields use `@JsonProperty(required = true, value = "fieldName")`
- Optional fields use `@JsonProperty(value = "fieldName")` with no `required`
- Nested list elements (ProposedStep, ProposedEdge) are inner records within the same file
- `eventType` field declared as `String`, not `CareEventType` — service validates after parse
- HIPAA comment stating that instances may contain PHI and must not be logged

---

### `src/main/java/com/onconavigator/ai/prompt/ExtractionPrompts.java` (config, transform)

**Analog:** `src/main/java/com/onconavigator/ai/prompt/ClassificationPrompts.java`

**Imports and class shell pattern** (lines 1–13):
```java
package com.onconavigator.ai.prompt;

/**
 * Prompt constants for Claude step extraction.
 *
 * <p>HIPAA note: The extraction flow sends full document text (which contains PHI) to Claude.
 * This requires an Anthropic BAA to be in place before production use with real patient data.
 */
public final class ExtractionPrompts {
    private ExtractionPrompts() {}

    public static final String SYSTEM_PROMPT = """
        ...
        """;

    public static final String USER_TEMPLATE = """
        ...
        {documentText}
        ...
        {existingSteps}
        ...
        """;
}
```

**Core prompt pattern** (lines 16–45):
```java
// SYSTEM_PROMPT: one-shot instruction block, ends with output format example.
// Pattern: "Do NOT..." rules before "EXAMPLE" fenced block.
// USER_TEMPLATE: uses {parameterName} Thymeleaf-style placeholders matching
//   the .param("documentText", truncated).param("existingSteps", context) calls.
// Both constants are text blocks (triple-quote strings).
// No runtime logic in this class — pure constants only.
public static final String SYSTEM_PROMPT = """
    You are a clinical document classifier for an oncology practice.
    You will receive the extracted text of a clinical document.
    ...
    Respond ONLY with the requested JSON structure. Do not include \
    explanation or commentary.
    ...
    EXAMPLE 1:
    Input: "..."
    Output: {...}
    """;

public static final String USER_TEMPLATE = """
    Classify the following clinical document and extract fields.

    DOCUMENT TEXT:
    ---
    {documentText}
    ---
    """;
```

**Key difference from ClassificationPrompts:** USER_TEMPLATE for extraction takes a second parameter `{existingSteps}` containing the serialized existing step context (non-PHI: step names, statuses, event types). The system prompt rules must instruct Claude not to re-propose steps already in `{existingSteps}`.

---

### `src/main/java/com/onconavigator/ai/service/StepExtractionService.java` (service, request-response)

**Analog:** `src/main/java/com/onconavigator/ai/service/DocumentClassificationService.java`

**Imports pattern** (lines 1–12):
```java
package com.onconavigator.ai.service;

import com.onconavigator.ai.model.ExtractionResult;
import com.onconavigator.ai.prompt.ExtractionPrompts;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
```

**Constructor injection pattern** (lines 48–53):
```java
// @Qualifier matches the bean name in AiClientConfig ("stepExtractionClient")
// @Value reads the feature flag with a safe default of false
public StepExtractionService(
        @Qualifier("stepExtractionClient") ChatClient stepExtractionClient,
        @Value("${onconavigator.ai.step-extraction.enabled:false}") boolean extractionEnabled) {
    this.stepExtractionClient = stepExtractionClient;
    this.extractionEnabled = extractionEnabled;
}
```

**Core pattern — @CircuitBreaker + feature flag + entity() call** (lines 70–88):
```java
@CircuitBreaker(name = "claude-api", fallbackMethod = "extractFallback")
public ExtractionResult extractSteps(UUID documentId, String extractedText, String existingStepsContext) {
    if (!extractionEnabled) {
        log.info("Step extraction disabled (BAA not in place)");
        return null;
    }

    try {
        String truncated = truncateToTokenBudget(extractedText);
        return stepExtractionClient.prompt()
                .user(u -> u.text(ExtractionPrompts.USER_TEMPLATE)
                            .param("documentText", truncated)
                            .param("existingSteps", existingStepsContext))
                .call()
                .entity(ExtractionResult.class);
    } catch (Exception e) {
        log.error("Step extraction failed for document {}: {}", documentId, e.getMessage());
        return null;
    }
}
```

**Fallback pattern** (lines 102–106):
```java
// Must be public for Resilience4j CGLIB proxy. Signature: original params + Exception.
// Returns null — StepExtractionTriggerService.triggerAsync() guards with null check.
public ExtractionResult extractFallback(UUID documentId, String extractedText,
                                        String existingStepsContext, Exception e) {
    log.warn("Claude extraction CB open for document {}: {}", documentId, e.getMessage());
    return null;
}
```

**Token budget truncation pattern** (lines 116–124):
```java
// Copied verbatim from DocumentClassificationService — same 4 chars/token estimate.
private static final int MAX_INPUT_TOKENS = 150_000;
private static final int CHARS_PER_TOKEN_ESTIMATE = 4;

private String truncateToTokenBudget(String text) {
    int maxChars = MAX_INPUT_TOKENS * CHARS_PER_TOKEN_ESTIMATE;
    if (text.length() > maxChars) {
        log.warn("Document text truncated from {} to {} chars for token budget", text.length(), maxChars);
        return text.substring(0, maxChars);
    }
    return text;
}
```

**HIPAA logging rule:** log only `documentId` (UUID) and `e.getMessage()`. Never log `extractedText`, `existingStepsContext`, or any field from ExtractionResult. Follow DocumentClassificationService exactly.

---

### `src/main/java/com/onconavigator/service/StepExtractionTriggerService.java` (service, event-driven)

**Analog:** Pattern synthesized from `DocumentProcessingService.java` (pipeline orchestrator) and `AsyncConfig.java` (@EnableAsync confirmation). No direct analog; closest structural match is `AuditService` async write pattern.

**Note:** `@EnableAsync` is ALREADY present in `src/main/java/com/onconavigator/config/AsyncConfig.java` (line 18). The RESEARCH.md pitfall about it being missing was written against an older codebase state. No additional `@EnableAsync` annotation is needed.

**Imports pattern:**
```java
package com.onconavigator.service;

import com.onconavigator.ai.model.ExtractionResult;
import com.onconavigator.ai.service.StepExtractionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
```

**Core @Async pattern** (from RESEARCH.md Section Pattern 1):
```java
// @Async runs this method on the virtual thread executor AFTER the upload transaction commits.
// @Transactional here starts a NEW transaction for the DB reads and writes inside.
// Never call Claude inside the @Transactional block of DocumentProcessingService.processUpload()
// — that holds a HikariCP connection during the 2–8 second Claude wait.
@Service
public class StepExtractionTriggerService {

    @Async
    @Transactional
    public void triggerAsync(UUID documentId, UUID patientId, String extractedText) {
        String existingStepsContext = pathwayService.buildExistingStepsContext(patientId);
        ExtractionResult result =
                extractionService.extractSteps(documentId, extractedText, existingStepsContext);
        if (result != null) {
            pathwayService.createProposedSteps(patientId, documentId, result);
            pathwayService.signalPathwayStepsChanged(patientId);
        }
    }
}
```

**Constructor injection pattern:** Follow `DocumentProcessingService` constructor injection (lines 68–82) — no field injection, all dependencies via constructor.

---

### `src/main/java/com/onconavigator/ai/config/AiClientConfig.java` (config, modify)

**Analog:** `src/main/java/com/onconavigator/ai/config/AiClientConfig.java` (the file being modified)

**Existing bean pattern to copy** (lines 37–46):
```java
@Bean
ChatClient documentClassificationClient(ChatModel chatModel) {
    return ChatClient.builder(chatModel)          // Static factory — fresh builder per bean (CR-05)
            .defaultSystem(ClassificationPrompts.SYSTEM_PROMPT)
            .defaultOptions(AnthropicChatOptions.builder()
                    .temperature(0.1)             // Low temp for deterministic extraction
                    .maxTokens(1024)
                    .build())
            .build();
}
```

**New bean to add alongside existing beans:**
```java
// Add as third @Bean in AiClientConfig — identical structure, different qualifier name,
// different system prompt constant, different maxTokens (2000 per RESEARCH.md).
@Bean
ChatClient stepExtractionClient(ChatModel chatModel) {
    return ChatClient.builder(chatModel)
            .defaultSystem(ExtractionPrompts.SYSTEM_PROMPT)
            .defaultOptions(AnthropicChatOptions.builder()
                    .temperature(0.1)   // Deterministic extraction
                    .maxTokens(2000)    // Bounded JSON output for step list
                    .build())
            .build();
}
```

**Critical:** Use `ChatClient.builder(chatModel)` static factory — NOT the auto-configured `ChatClient.Builder` singleton. Calling `.defaultSystem()` on the singleton mutates shared state (CR-05 comment in existing AiClientConfig line 27).

---

### `src/main/java/com/onconavigator/domain/enums/PathwayStepStatus.java` (model, modify)

**Analog:** `src/main/java/com/onconavigator/domain/enums/PathwayStepStatus.java` (file being modified)

**Current file** (lines 1–17):
```java
public enum PathwayStepStatus {
    ACTIVE,
    PROPOSED,
    COMPLETED,
    SKIPPED
}
```

**Required change:** Add `REJECTED` as the fifth value and update the Javadoc comment:
```java
// Add REJECTED after SKIPPED. Update class Javadoc to describe the new value.
// REJECTED: Step was proposed by AI and explicitly rejected by a nurse navigator.
//           Persisted for audit trail; blocks re-proposal from subsequent document uploads (D-07, D-09).
public enum PathwayStepStatus {
    ACTIVE,
    PROPOSED,
    COMPLETED,
    SKIPPED,
    REJECTED
}
```

---

### `src/main/java/com/onconavigator/domain/PatientPathwayStep.java` (model, modify)

**Analog:** `src/main/java/com/onconavigator/domain/PatientPathwayStep.java` (file being modified)

**Existing column pattern to copy** (lines 64–115):
```java
// Pattern for new nullable columns mapped to DB columns added by V16 migration.
// Follow the existing @Column style: explicit columnDefinition only for custom SQL types,
// otherwise let JPA infer. Use @ManyToOne(fetch = LAZY) for FK relationships.

@Enumerated(EnumType.STRING)
@Column(name = "event_type", columnDefinition = "care_event_type")
private CareEventType eventType;           // nullable — existing nullable column pattern

@Column(name = "source_template_step_id", length = 100)
private String sourceTemplateStepId;      // nullable VARCHAR — existing pattern
```

**New fields to add** (following the existing nullable column style):
```java
// source: 'TEMPLATE', 'MANUAL', 'AI_EXTRACTED' — maps to VARCHAR(50) in V16
@Column(name = "source", length = 50)
private String source;

// sourceDocumentId: UUID FK to clinical_documents.id — nullable
@Column(name = "source_document_id")
private UUID sourceDocumentId;

// proposedEdgesJson: JSONB stored as TEXT — transient proposed edges
// for Option A (JSONB column vs new table per RESEARCH.md Pattern 4)
@Column(name = "proposed_edges_json", columnDefinition = "TEXT")
private String proposedEdgesJson;
```

**Getter/setter pattern** (lines 161–296): Add getters and setters for each new field following the exact same style as existing getters/setters — no Lombok, plain Java.

---

### `src/main/java/com/onconavigator/service/PatientPathwayService.java` (service, CRUD)

**Analog:** `src/main/java/com/onconavigator/service/PatientPathwayService.java` (file being modified)

**Status transition pattern to copy** (lines 241–264, skipStep):
```java
// Pattern: requireStep() ownership check → status guard (throw 409 if wrong status)
// → mutate status → save → signal → log (UUID only) → return toStepResponse()
@Transactional
public PathwayStepResponse skipStep(UUID patientId, UUID stepId, String skipReason, UUID actorId) {
    PatientPathwayStep step = requireStep(patientId, stepId);  // BOLA check

    if (step.getStatus() != PathwayStepStatus.ACTIVE) {
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Only ACTIVE steps can be skipped; step " + stepId + " is " + step.getStatus());
    }

    step.setStatus(PathwayStepStatus.SKIPPED);
    step.setSkipReason(skipReason);
    step = stepRepository.save(step);

    resolveAlertsForStep(patientId, step.getName());
    pathwayService.signalPathwayStepsChanged(patientId);

    log.info("Skipped step {} for patient {}", stepId, patientId);  // UUID only, no PHI

    List<UUID> prereqIds = getPrerequisiteIds(step.getPathway().getId(), stepId);
    return toStepResponse(step, 0, 0, prereqIds);
}
```

**New methods follow this exact pattern:**
- `confirmProposedStep()`: status guard `PROPOSED`, transition to `ACTIVE`, activate proposed edges, signal, return `toStepResponse()`
- `rejectProposedStep()`: status guard `PROPOSED`, transition to `REJECTED`, signal, return `toStepResponse()`
- `createProposedSteps()`: `@Transactional`, dedup query against ACTIVE/COMPLETED/REJECTED by event type, create PROPOSED steps with `source="AI_EXTRACTED"` and `sourceDocumentId`, save, signal
- `buildExistingStepsContext()`: `@Transactional(readOnly = true)`, query steps for patient, serialize to non-PHI string (names + statuses + event types only)

**Dedup query pattern** (from createEdge lines 327–337 for the deduplicate check style):
```java
// For createProposedSteps() dedup — include REJECTED in the status set (D-09)
// Same pattern as the existing edge dedup: load existing, stream/filter, check
List<PatientPathwayStep> existingSteps = stepRepository.findByPathway_Id(pathway.getId());
Set<String> existingEventTypes = existingSteps.stream()
        .filter(s -> s.getStatus() == PathwayStepStatus.ACTIVE
                  || s.getStatus() == PathwayStepStatus.COMPLETED
                  || s.getStatus() == PathwayStepStatus.REJECTED)
        .filter(s -> s.getEventType() != null)
        .map(s -> s.getEventType().name())
        .collect(Collectors.toSet());
```

**Signal call pattern** (lines 144, 194, 227, 259, 289): Every mutating method ends with `pathwayService.signalPathwayStepsChanged(patientId)`. Confirm and reject are no exception.

**Ownership guard pattern** (lines 579–589):
```java
// requireStep() is already defined. All new methods use it for BOLA protection.
private PatientPathwayStep requireStep(UUID patientId, UUID stepId) {
    PatientPathway pathway = requirePathway(patientId);
    PatientPathwayStep step = stepRepository.findById(stepId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Step not found"));
    if (!step.getPathway().getId().equals(pathway.getId())) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Step not found");
    }
    return step;
}
```

---

### `src/main/java/com/onconavigator/web/PatientPathwayController.java` (controller, request-response)

**Analog:** `src/main/java/com/onconavigator/web/PatientPathwayController.java` (file being modified)

**Imports pattern** (lines 1–27):
```java
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
```

**Status-transition endpoint pattern to copy** (lines 141–149, skipStep / unskipStep):
```java
// Pattern: @PatchMapping with sub-resource action in path, narrow @PreAuthorize,
// extract actorId from JWT subject, delegate to service, return DTO.
@PatchMapping("/steps/{stepId}/skip")
@PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')")
public PathwayStepResponse skipStep(
        @PathVariable UUID patientId,
        @PathVariable UUID stepId,
        @Valid @RequestBody SkipStepRequest request,
        @AuthenticationPrincipal Jwt jwt) {
    UUID actorId = UUID.fromString(jwt.getSubject());
    return patientPathwayService.skipStep(patientId, stepId, request.reason(), actorId);
}

@PatchMapping("/steps/{stepId}/unskip")
@PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')")
public PathwayStepResponse unskipStep(
        @PathVariable UUID patientId,
        @PathVariable UUID stepId,
        @AuthenticationPrincipal Jwt jwt) {
    UUID actorId = UUID.fromString(jwt.getSubject());
    return patientPathwayService.unskipStep(patientId, stepId, actorId);
}
```

**New endpoints to add — confirm and reject:**
```java
// POST for confirm (creates new ACTIVE step + edges — semantically a creation action)
// PATCH for reject (modifies existing step status)
// Authorization: NURSE_NAVIGATOR and ADMIN only (not CARE_COORDINATOR — clinical decision)
@PostMapping("/steps/{stepId}/confirm")
@PreAuthorize("hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')")
public PathwayStepResponse confirmStep(
        @PathVariable UUID patientId,
        @PathVariable UUID stepId,
        @AuthenticationPrincipal Jwt jwt) {
    UUID actorId = UUID.fromString(jwt.getSubject());
    return patientPathwayService.confirmProposedStep(patientId, stepId, actorId);
}

@PatchMapping("/steps/{stepId}/reject")
@PreAuthorize("hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')")
public PathwayStepResponse rejectStep(
        @PathVariable UUID patientId,
        @PathVariable UUID stepId,
        @AuthenticationPrincipal Jwt jwt) {
    UUID actorId = UUID.fromString(jwt.getSubject());
    return patientPathwayService.rejectProposedStep(patientId, stepId, actorId);
}
```

---

### `src/main/java/com/onconavigator/web/dto/PathwayStepResponse.java` (model, modify)

**Analog:** `src/main/java/com/onconavigator/web/dto/PathwayStepResponse.java` (file being modified)

**Current record structure** (lines 32–50):
```java
public record PathwayStepResponse(
        UUID id,
        UUID pathwayId,
        String name,
        String description,
        String eventType,
        Integer windowDays,
        boolean required,
        String status,
        String skipReason,
        String alertText,
        String suggestedAction,
        OffsetDateTime completedAt,
        UUID completedCareEventId,
        int depth,
        int sortOrder,
        List<UUID> prerequisiteIds,
        OffsetDateTime createdAt
) {}
```

**Fields to append** (add after `createdAt`, before closing `)`):
```java
// New Phase 6 fields — nullable for backward compatibility with template/manual steps
UUID sourceDocumentId,          // UUID of ClinicalDocument if AI_EXTRACTED; null otherwise
String extractionSource,         // 'TEMPLATE', 'MANUAL', 'AI_EXTRACTED', or null
String sourceDocumentFilename    // Original filename for "Source: {filename}" UI link (non-PHI)
```

**Mapping update:** The `toStepResponse()` helper in `PatientPathwayService` (lines 594–615) must be updated to pass these three fields. Follow existing null-safe field access pattern:
```java
// Follow existing pattern: step.getEventType() != null ? step.getEventType().name() : null
step.getSourceDocumentId(),      // null for non-AI steps
step.getSource(),                // null for non-AI steps
null                             // sourceDocumentFilename — populated via doc lookup or denormalized
```

---

### `src/main/resources/db/migration/V16__add_rejected_status_and_ai_source.sql` (migration, CRUD)

**Analog:** `src/main/resources/db/migration/V13__create_pathway_step_status_enum.sql` + `V14__create_per_patient_pathway_tables.sql`

**Enum creation pattern** (V13, line 11):
```sql
CREATE TYPE pathway_step_status AS ENUM ('ACTIVE', 'PROPOSED', 'COMPLETED', 'SKIPPED');
```

**Column addition pattern** (V14, lines 33–52):
```sql
ALTER TABLE patient_pathway_steps
    ADD COLUMN IF NOT EXISTS source_template_step_id VARCHAR(100);
```

**Grants pattern** (V14, lines 80–83):
```sql
GRANT ALL ON patient_pathway_steps TO onco_app;
```

**V16 migration structure:**
```sql
-- V16__add_rejected_status_and_ai_source.sql
-- Adds REJECTED to the pathway_step_status PostgreSQL enum and adds source tracking
-- columns to patient_pathway_steps for Phase 6 AI extraction support.
--
-- IMPORTANT: ALTER TYPE ... ADD VALUE is not fully transactional in PostgreSQL.
-- Place it FIRST in this migration and run it alone before the ALTER TABLE statements.
-- Verified behavior on PostgreSQL 16: ADD VALUE IF NOT EXISTS is safe in Flyway's
-- default transaction context when it is the first statement.

ALTER TYPE pathway_step_status ADD VALUE IF NOT EXISTS 'REJECTED';

ALTER TABLE patient_pathway_steps
    ADD COLUMN IF NOT EXISTS source              VARCHAR(50),
    ADD COLUMN IF NOT EXISTS source_document_id  UUID REFERENCES clinical_documents(id),
    ADD COLUMN IF NOT EXISTS proposed_edges_json TEXT;

CREATE INDEX IF NOT EXISTS idx_pathway_steps_source_doc
    ON patient_pathway_steps(source_document_id)
    WHERE source_document_id IS NOT NULL;

GRANT ALL ON patient_pathway_steps TO onco_app;
```

---

### `frontend/src/features/patients/types.ts` (model, modify)

**Analog:** `frontend/src/features/patients/types.ts` (file being modified)

**Current enum type** (line 64):
```typescript
export type PathwayStepStatusEnum = 'ACTIVE' | 'PROPOSED' | 'COMPLETED' | 'SKIPPED';
```

**Modified enum type:**
```typescript
export type PathwayStepStatusEnum = 'ACTIVE' | 'PROPOSED' | 'COMPLETED' | 'SKIPPED' | 'REJECTED';
```

**Current PatientPathwayStep interface** (lines 79–97):
```typescript
export interface PatientPathwayStep {
  id: string;
  pathwayId: string;
  name: string;
  // ... existing fields ...
  createdAt: string;
}
```

**Fields to add to PatientPathwayStep** (after `createdAt`):
```typescript
// Phase 6: AI extraction source tracking
sourceDocumentId: string | null;         // UUID of ClinicalDocument if AI_EXTRACTED
extractionSource: 'TEMPLATE' | 'MANUAL' | 'AI_EXTRACTED' | null;
sourceDocumentFilename: string | null;   // For "Source: {filename}" display link
```

**PathwayStepStatus interface also needs REJECTED** (line 66–77):
The `PathwayStepStatus` interface (used by `StepRow.tsx`) references `status: PathwayStepStatusEnum`. Since `PathwayStepStatusEnum` is updated, `StepRow.tsx` switch/if branches need a REJECTED case added.

---

### `frontend/src/features/patients/api.ts` (utility, request-response)

**Analog:** `frontend/src/features/patients/api.ts` (file being modified)

**Mutation hook pattern to copy** (lines 157–169, useSkipStep):
```typescript
export function useSkipStep(patientId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ stepId, reason }: { stepId: string; reason: string }) =>
      apiClient.patch<PatientPathwayStep>(
        `/patients/${patientId}/pathway/steps/${stepId}/skip`, { reason }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-steps'] });
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-status'] });
      queryClient.invalidateQueries({ queryKey: ['alerts'] });
      queryClient.invalidateQueries({ queryKey: ['alerts', 'count'] });
    },
  });
}
```

**No-body PATCH pattern** (lines 172–183, useUnskipStep):
```typescript
// When there is no request body, pass {} as the second argument to apiClient.patch
export function useUnskipStep(patientId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (stepId: string) =>
      apiClient.patch<PatientPathwayStep>(
        `/patients/${patientId}/pathway/steps/${stepId}/unskip`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-steps'] });
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-status'] });
    },
  });
}
```

**New hooks to add:**
```typescript
// useConfirmStep: POST (confirm creates ACTIVE step + edges — use apiClient.post)
export function useConfirmStep(patientId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (stepId: string) =>
      apiClient.post<PatientPathwayStep>(
        `/patients/${patientId}/pathway/steps/${stepId}/confirm`, {}),
    onSuccess: () => {
      // Invalidate steps (PROPOSED→ACTIVE), status (workflow re-eval), and edges (new edges created)
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-steps'] });
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-status'] });
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-edges'] });
    },
  });
}

// useRejectStep: PATCH (reject modifies step status only — no edges created)
export function useRejectStep(patientId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (stepId: string) =>
      apiClient.patch<PatientPathwayStep>(
        `/patients/${patientId}/pathway/steps/${stepId}/reject`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-steps'] });
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-status'] });
    },
  });
}
```

---

### `frontend/src/features/patients/StepRow.tsx` (component, event-driven)

**Analog:** `frontend/src/features/patients/StepRow.tsx` (file being modified)

**Existing PROPOSED rendering pattern** (lines 46–55, 136–150, 222–232):
```tsx
// Status icon: PROPOSED already has a dashed-circle pattern
if (step.status === 'PROPOSED') {
  return (
    <Circle
      className="h-5 w-5 text-muted-foreground shrink-0 icon-dashed"
      aria-label="Proposed - pending confirmation"
    />
  );
}

// Step name: PROPOSED already uses muted-foreground style
case 'PROPOSED':
  return 'text-sm text-muted-foreground';

// Edit-mode PROPOSED block: currently only shows Remove button
{step.status === 'PROPOSED' && (
  <Button variant="ghost" size="sm" className="h-8 w-8 p-0 text-destructive hover:text-destructive"
    aria-label={`Remove ${step.stepName}`} onClick={onRemove}>
    <Trash2 className="h-4 w-4" />
  </Button>
)}
```

**Button style pattern to follow** (lines 181–210, ACTIVE buttons):
```tsx
// Icon-only action button: variant="ghost", size="sm", h-8 w-8 p-0, aria-label required
<Button variant="ghost" size="sm" className="h-8 w-8 p-0"
  aria-label={`Edit ${step.stepName}`} onClick={onEdit}>
  <Pencil className="h-4 w-4" />
</Button>

// Text+icon action button: h-8 px-2 text-xs for labeled actions
<Button variant="ghost" size="sm" className="h-8 px-2 text-xs" onClick={onSkip}>
  Skip
</Button>
```

**Changes to make in the PROPOSED block:**
```tsx
// Replace the single Remove button in the PROPOSED block with Confirm + Reject + Remove:
// - Confirm: Check icon (lucide-react Check), text-green variant or default ghost
// - Reject: X icon (lucide-react X), text-destructive variant
// Add REJECTED case to stepNameClass() switch and PathwayStepIcon
{step.status === 'PROPOSED' && (
  <>
    <Button variant="ghost" size="sm" className="h-8 w-8 p-0 text-green-600"
      aria-label={`Confirm ${step.stepName}`} onClick={onConfirm}>
      <Check className="h-4 w-4" />
    </Button>
    <Button variant="ghost" size="sm" className="h-8 w-8 p-0 text-destructive hover:text-destructive"
      aria-label={`Reject ${step.stepName}`} onClick={onReject}>
      <X className="h-4 w-4" />
    </Button>
  </>
)}
```

**Props interface change:** Add `onConfirm?: () => void` and `onReject?: () => void` to `StepRowProps` (line 83–91), following the existing optional callback style (`onEdit?`, `onRemove?`, `onSkip?`, `onUnskip?`).

**REJECTED step rendering:** Add a new status case for REJECTED. Use `MinusCircle` with a strikethrough text style — closest existing analog is SKIPPED rendering (lines 59–63, 72–76). REJECTED is hidden by default (filtered in PathwayEditor), shown only when "show rejected" toggle is on.

---

### `frontend/src/features/patients/PathwayEditor.tsx` (component, event-driven)

**Analog:** `frontend/src/features/patients/PathwayEditor.tsx` (file being modified)

**Existing import pattern** (lines 1–37):
```tsx
import { useState } from 'react';
import { Plus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, ... } from '@/components/ui/dialog';
import { StepRow } from './StepRow';
import { useCreateStep, useUpdateStep, useDeleteStep, useSkipStep, useUnskipStep, ... } from './api';
```

**Inline edit form pattern** (lines 54–79): The `InlineStepEdit` internal component is the reuse point for edit-before-confirm (D-06). PROPOSED steps use the same form before confirming. No new form component needed.

**Changes to make:**
- Add `useConfirmStep`, `useRejectStep` to imports from `./api`
- Add `Check`, `X` to lucide-react imports
- Add `Collapsible` from `@/components/ui/collapsible` for the "show rejected" toggle
- Pass `onConfirm` and `onReject` callbacks to each `StepRow` whose `step.status === 'PROPOSED'`
- Add "already covered" section: a read-only list of step names returned by `createProposedSteps()` that were deduplicated (from the confirmation mutation response)
- Add "show rejected" Collapsible: filter `steps` to show REJECTED only when toggle is open; reuse `Collapsible` component already installed (confirmed in `frontend/src/components/ui/collapsible.tsx`)

---

## Shared Patterns

### Feature Flag Pattern
**Source:** `src/main/java/com/onconavigator/ai/service/DocumentClassificationService.java` (lines 46–53, 71–74)
**Apply to:** `StepExtractionService.java`
```java
@Value("${onconavigator.ai.step-extraction.enabled:false}") boolean extractionEnabled
// Guard at top of public method:
if (!extractionEnabled) {
    log.info("Step extraction disabled (BAA not in place)");
    return null;
}
```
New property key: `onconavigator.ai.step-extraction.enabled` (add to `application-local.yml`)

### Circuit Breaker Pattern
**Source:** `src/main/java/com/onconavigator/ai/service/DocumentClassificationService.java` (lines 70–102)
**Apply to:** `StepExtractionService.java`
```java
@CircuitBreaker(name = "claude-api", fallbackMethod = "extractFallback")
// Shared breaker name "claude-api" — do not create a separate breaker name.
// Fallback must be public, match original signature + Exception parameter, return null.
```

### PHI Logging Exclusion
**Source:** `DocumentClassificationService.java` (lines 67, 85–86), `PatientPathwayService.java` (lines 146, 199, 228)
**Apply to:** `StepExtractionService.java`, `StepExtractionTriggerService.java`, new `PatientPathwayService` methods
```java
// Log only: document UUID, step UUID, patient UUID, error message strings.
// NEVER log: extractedText, step names from extraction, extractionRationale, existingStepsContext.
log.error("Step extraction failed for document {}: {}", documentId, e.getMessage());
log.info("Created {} proposed steps for patient {}", count, patientId);
```

### BOLA Ownership Check
**Source:** `PatientPathwayService.java` (lines 579–589, requireStep)
**Apply to:** `confirmProposedStep()`, `rejectProposedStep()` in `PatientPathwayService`
```java
// Every mutating operation starts with requireStep(patientId, stepId).
// This single call handles both 404 (step not found) and BOLA (wrong patient's step).
private PatientPathwayStep requireStep(UUID patientId, UUID stepId) {
    PatientPathway pathway = requirePathway(patientId);
    PatientPathwayStep step = stepRepository.findById(stepId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Step not found"));
    if (!step.getPathway().getId().equals(pathway.getId())) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Step not found");  // Opaque 404
    }
    return step;
}
```

### Temporal Signal on Every Mutation
**Source:** `PatientPathwayService.java` (lines 144, 194, 227, 259, 289)
**Apply to:** `confirmProposedStep()`, `rejectProposedStep()`, `createProposedSteps()` in `PatientPathwayService`
```java
pathwayService.signalPathwayStepsChanged(patientId);  // Last line before return, every mutation
```

### TanStack Query Invalidation on Mutation Success
**Source:** `frontend/src/features/patients/api.ts` (lines 115–127, useCreateStep)
**Apply to:** `useConfirmStep()`, `useRejectStep()` in `api.ts`
```typescript
onSuccess: () => {
  queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-steps'] });
  queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-status'] });
  // Add pathway-edges invalidation only for confirm (edges are created on confirm, not reject)
}
```

### PostgreSQL Flyway Migration Structure
**Source:** `src/main/resources/db/migration/V13__create_pathway_step_status_enum.sql` + `V14__create_per_patient_pathway_tables.sql`
**Apply to:** `V16__add_rejected_status_and_ai_source.sql`
```sql
-- Header comment block: file name, description, table list, design notes
-- IF NOT EXISTS guards on all DDL statements
-- GRANT ALL ON <table> TO onco_app at the bottom
-- Index naming: idx_<table>_<column>
```

---

## No Analog Found

All files to be created or modified have close analogs in the codebase. There are no files with no analog.

The only structural pattern without a direct codebase analog is the `@Async` trigger service class (`StepExtractionTriggerService`). The closest analog is `AuditService` (which uses `@Async` for async audit writes), but the full pattern is synthesized from `AsyncConfig.java` + `DocumentProcessingService.java` + the RESEARCH.md Section Pattern 1 reference.

---

## Metadata

**Analog search scope:** `src/main/java/com/onconavigator/`, `frontend/src/features/patients/`, `src/main/resources/db/migration/`
**Files read:** 19 source files
**Pattern extraction date:** 2026-05-04

**Key facts verified by reading source:**
- `@EnableAsync` IS present in `AsyncConfig.java` (line 18). RESEARCH.md Pitfall 1 is resolved — no code change needed to enable async.
- `PathwayStepStatus.PROPOSED` is already defined (line 13 of PathwayStepStatus.java); REJECTED is not yet present.
- `PatientPathwayStep` does not yet have `source`, `source_document_id`, or `proposed_edges_json` columns.
- `PathwayStepResponse` record does not yet have `sourceDocumentId`, `extractionSource`, or `sourceDocumentFilename` fields.
- `StepRow.tsx` already has a `PROPOSED` status branch with dashed icon and muted text, but has no Confirm/Reject buttons — only a Remove button.
- `frontend/src/features/patients/types.ts` `PathwayStepStatusEnum` does not include `'REJECTED'`.
- `frontend/src/features/patients/api.ts` has no `useConfirmStep` or `useRejectStep` hooks.
- `AiClientConfig.java` has `documentClassificationClient` and `alertGenerationClient` — the `stepExtractionClient` bean is absent.
