# Phase 6: AI Step Extraction from Clinical Documents - Research

**Researched:** 2026-05-04
**Domain:** Spring AI structured extraction, async pipeline integration, PostgreSQL enum migration, React confirm/reject UX
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**D-01** Automatic during document upload — step extraction runs as the next pipeline step in `DocumentProcessingService.processUpload()` after classification + patient matching succeeds. No separate user action required. Mirrors how classification already works automatically.

**D-02** Separate Claude API call from document classification — extraction has its own prompt, output schema, feature flag (`onconavigator.ai.step-extraction.enabled`), and circuit breaker. A classification failure doesn't prevent extraction or vice versa.

**D-03** Claude receives existing pathway steps as context — send step names, statuses, and event types (non-PHI per cross-cutting constraint) so Claude can avoid re-proposing existing steps and can suggest where new steps fit relative to existing ones.

**D-04** Inline with visual distinction — PROPOSED steps appear in the pathway list at their inferred position with dashed border, gray/muted styling, and an "AI Proposed" badge. Consistent with Phase 5 D-15 (dashed outline/gray for PROPOSED).

**D-05** Per-step inline confirm/reject buttons — each PROPOSED step row shows [Confirm] and [Reject] icons. Confirming transitions to ACTIVE; rejecting transitions to REJECTED. No modal overhead, works naturally with the existing pathway editor.

**D-06** Edit before confirm — the nurse can edit a proposed step's name, time window, or event type before confirming it. Uses the same Phase 5 inline edit UI that already exists for ACTIVE steps. Claude's extraction is a starting point, not final.

**D-07** REJECTED = soft delete with audit trail — rejected steps stay in the database with status=REJECTED (new enum value). Hidden from the pathway view by default but visible via a "show rejected" toggle. Preserves full history of what AI proposed. Prevents re-proposing the same step from a different document (combined with D-09 dedup).

**D-08** Claude filters + backend validates (belt and suspenders) — the prompt instructs Claude to NOT propose steps matching existing ACTIVE/COMPLETED steps. The backend also compares extracted step event types against existing steps before creating PROPOSED rows.

**D-09** Event type match for dedup — if an extracted step has the same CareEventType as an existing ACTIVE/COMPLETED step, it's a duplicate. Deterministic, uses the structured enum. Also checks against REJECTED steps to avoid re-proposing previously rejected extractions.

**D-10** Show duplicates as "already covered" — when the backend detects duplicates, display them in a separate informational section (e.g., "Steps already in pathway: CT Scan, Blood Work"). Gives transparency into what Claude found vs. what's genuinely new.

**D-11** Claude proposes full graph edges — Claude infers ordering from the document and proposes edges both between new steps AND connecting new steps to existing active steps in the pathway.

**D-12** Edges bundled with step confirmation — when a nurse confirms a PROPOSED step, its proposed edges are confirmed automatically. Rejecting a step removes its edges too. One action per step, edges come along for the ride.

**D-13** Cross-edges to existing steps — Claude can propose edges from existing active steps to new proposed steps. Edges to existing steps auto-activate when the proposed step is confirmed. Cycle detection runs per Phase 5 cross-cutting constraint.

### Claude's Discretion

- Extraction failure behavior (silent skip vs toast notification vs other UX pattern — follow existing fallback patterns)
- Claude prompt template structure and structured output schema for step + edge extraction
- `stepExtractionClient` ChatClient bean configuration (temperature, max tokens)
- How "inferred position" is determined for rendering PROPOSED steps in the DAG view
- How proposed edges are visually rendered (dashed lines, lighter color, etc.)
- How the "already covered" informational section is displayed in the UI
- Whether to add a "show rejected" toggle or a separate view for rejected steps
- Exact circuit breaker parameters for the extraction call (reuse existing `claude-api` breaker name or separate)

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PW-ALL-002 | Events extracted from clinical documents (MD notes, orders, nurse notes) become pathway steps | StepExtractionService pattern (Section 4.3 of AI-SPEC) — Claude extraction + backend dedup + PROPOSED persistence; all implementation patterns are verified in existing codebase |
| PW-BR-001 | Steps extracted from MD notes, orders, and nurse notes — per-patient unique sequence not inferred from standard-of-care templates | Document-fidelity guardrail in ExtractionPrompts.SYSTEM_PROMPT rule 3; reinforces unique per-patient pathway per oncologist review in docs/Pathway-Template-Review-Worksheet.md |

</phase_requirements>

---

## Summary

Phase 6 adds a second Claude API call inside the document upload pipeline. After the existing classification step succeeds and the document is persisted, a new `StepExtractionTriggerService` fires asynchronously (`@Async`) to call Claude with the document text and existing pathway step context, receives a structured JSON response containing proposed steps and DAG edges, runs backend dedup and cycle-detection, and persists new `PatientPathwayStep` rows in PROPOSED status with source=AI_EXTRACTED and a document link. A nurse then confirms or rejects each step inline from the patient detail pathway view.

The backend work is additive and well-bounded: one new ChatClient bean, one new extraction service following the DocumentClassificationService template exactly, one new trigger service for async decoupling, three new service methods on PatientPathwayService, two new controller endpoints, a Flyway migration to add REJECTED to the enum, a new column for source document link, and a new column for AI extraction source tracking. The frontend work is equally bounded: PROPOSED step rows already render (Phase 5 built the visual distinction); Phase 6 adds Confirm/Reject buttons and the "already covered" informational section.

The AI-SPEC (06-AI-SPEC.md) is the primary technical reference for this phase. It contains verified Spring AI 1.1.0 patterns, the complete ExtractionResult record hierarchy, the full StepExtractionService implementation, prompt constants, async trigger pattern, and all pitfall mitigations. The UI-SPEC (06-UI-SPEC.md) is the primary reference for all frontend component decisions. Research here focuses on filling gaps not covered by those specs: the precise database schema changes needed, the complete list of new backend artifacts, the frontend type extensions, and the integration handoff points.

**Primary recommendation:** Follow the AI-SPEC implementation guidance verbatim for all Claude integration code. The spec was written with direct reference to the existing codebase patterns. The planner should structure plans to build the async async pipeline backbone first (database, service methods), then add the Claude extraction call, then add the UI — each independently testable.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Claude extraction call (step proposals) | API / Backend | — | PHI-bearing document text cannot leave the Spring Boot trust boundary to the frontend; extraction is a server-side pipeline step |
| Async extraction trigger (decouple from upload transaction) | API / Backend | — | The @Async/@Transactional pattern is a JVM-level concern; decoupling holds the DB connection for only the upload, not the Claude wait |
| PROPOSED step persistence (source, document link) | Database / Storage | — | All ePHI-adjacent data (PatientPathwayStep rows) lives in PostgreSQL with Envers @Audited |
| Backend dedup (event type comparison) | API / Backend | Database / Storage | Deterministic enum comparison is fast, but requires reading existing steps from DB |
| DAG cycle detection on proposed edges | API / Backend | — | Existing DFS cycle detection in PatientPathwayService is reused; no new algorithm |
| Confirm/reject status transitions | API / Backend | — | Status transitions (PROPOSED→ACTIVE, PROPOSED→REJECTED) are backend mutations with @PreAuthorize enforcement |
| pathwayStepsChanged Temporal signal | API / Backend | — | Already wired in PatientPathwayService on every step mutation; confirm/reject just call it too |
| PROPOSED step visual rendering | Browser / Client | Frontend Server (SSR) | PathwayDAGView already handles PROPOSED status in Phase 5; Phase 6 adds confirm/reject buttons |
| "Already covered" informational display | Browser / Client | — | Read-only client-side rendering of alreadyCoveredEventTypes from ClinicalDocument metadata |
| Reject confirmation dialog | Browser / Client | — | Radix Dialog, already installed; no server involvement until user confirms the rejection |
| "Show rejected" toggle | Browser / Client | — | Collapsible, already installed; filtered client-side from step list that includes REJECTED steps |
| Extraction in-progress indicator | Browser / Client | — | Progress component rendered while TanStack Query polling detects new PROPOSED steps |

---

## Standard Stack

### Core

All libraries are already installed. Phase 6 adds zero new npm packages and zero new Maven dependencies.

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring AI (spring-ai-starter-model-anthropic) | 1.1.0 (via Boot BOM) | Claude API ChatClient for structured extraction | Already present from Phase 4; ExtractionResult.entity() pattern verified in DocumentClassificationService |
| Resilience4j | via Boot BOM | @CircuitBreaker on extraction call | Same `claude-api` breaker name used by DocumentClassificationService; shared CB is correct for a Claude API outage |
| Spring @Async | via Spring Framework (Boot BOM) | Decouple Claude call from upload transaction | Required to avoid holding HikariCP connection during 2–8 second Claude wait |
| Hibernate Envers | via Boot BOM | @Audited on PatientPathwayStep | Already annotated; REJECTED status transitions are automatically revision-tracked |
| Flyway | 11.x (via Boot BOM) | ALTER TYPE pathway_step_status ADD VALUE 'REJECTED' | Existing migration pattern; V16 migration |
| TanStack Query v5 | Already installed | useMutation hooks for confirm/reject, invalidation on settlement | Existing pattern in api.ts |
| shadcn/ui | Already installed | All UI components | Badge, Button, Dialog, Collapsible, Progress — all confirmed installed per UI-SPEC |
| lucide-react | Already installed | Check, X, Pencil icons for confirm/reject/edit buttons | Already used in PathwayDAGView and StepRow |

### No New Dependencies

```
# Backend: no new Maven dependencies
# Frontend: no new npm packages
# All needed capabilities are already in the project
```

[VERIFIED: codebase grep — pom.xml already has spring-ai-starter-model-anthropic, resilience4j, spring-boot-starter-data-jpa; frontend/components.json confirms all shadcn components installed]

---

## Architecture Patterns

### System Architecture Diagram

```
Document Upload (POST /api/documents/upload)
        |
        v
DocumentProcessingService.processUpload() [@Transactional]
        |
        +-- Text extraction (PDFBox / OCR / Vision)
        +-- DocumentClassificationService.classify() [Claude call 1 -- existing]
        +-- DocumentPatientMatchService.matchPatient()
        +-- ClinicalDocumentRepository.save(doc)  <-- transaction commits HERE
        |
        +-- if (doc.patient != null && extractedText != null)
                 |
                 +-- StepExtractionTriggerService.triggerAsync()  [@Async -- runs AFTER transaction]
                          |
                          +-- PatientPathwayService.buildExistingStepsContext(patientId) [DB query]
                          +-- StepExtractionService.extractSteps() [@CircuitBreaker("claude-api")]
                          |         |
                          |         +-- Claude API call (claude-sonnet-4-20250514)
                          |               stepExtractionClient.prompt().call().entity(ExtractionResult.class)
                          |
                          +-- ExtractionResult.proposedSteps: List<ProposedStep>
                          |         +-- CareEventType enum validation (filter invalids)
                          |         +-- Backend dedup against ACTIVE/COMPLETED/REJECTED steps
                          |         +-- DAG cycle detection on proposed edges
                          |
                          +-- PatientPathwayService.createProposedSteps()
                          |         +-- PatientPathwayStep (status=PROPOSED, source=AI_EXTRACTED,
                          |                                 sourceDocumentId=doc.getId())
                          |         +-- Persist alreadyCoveredEventTypes on ClinicalDocument
                          |
                          +-- PatientPathwayService.signalPathwayStepsChanged(patientId)
                                    +-- Temporal signal -> workflow re-evaluates
                                         (PROPOSED steps excluded from evaluation)


Nurse Confirm Flow (POST /api/patients/{patientId}/pathway/steps/{stepId}/confirm)
        |
        +-- PatientPathwayService.confirmProposedStep()
                 +-- step.status = ACTIVE
                 +-- Confirm proposed edges (create PatientPathwayEdge rows)
                 |         +-- DAG cycle detection per existing createEdge() pattern
                 +-- signalPathwayStepsChanged() -> evaluation engine picks up new ACTIVE step


Nurse Reject Flow (PATCH /api/patients/{patientId}/pathway/steps/{stepId}/reject)
        |
        +-- PatientPathwayService.rejectProposedStep()
                 +-- step.status = REJECTED
                 +-- signalPathwayStepsChanged()
                          (REJECTED step excluded from evaluation; blocks re-proposal via dedup)
```

### Recommended Project Structure

No new top-level directories. New files slot into existing packages:

```
src/main/java/com/onconavigator/
+-- ai/
|   +-- config/
|   |   +-- AiClientConfig.java              <-- ADD stepExtractionClient @Bean
|   +-- model/
|   |   +-- ExtractionResult.java            <-- NEW (top-level wrapper record)
|   +-- prompt/
|   |   +-- ExtractionPrompts.java           <-- NEW (SYSTEM_PROMPT + USER_TEMPLATE)
|   +-- service/
|       +-- StepExtractionService.java       <-- NEW (@CircuitBreaker, feature flag, null fallback)
+-- domain/
|   +-- enums/
|       +-- PathwayStepStatus.java           <-- ADD REJECTED value
+-- service/
|   +-- DocumentProcessingService.java       <-- MODIFY: hook async trigger after doc.save()
|   +-- PatientPathwayService.java           <-- ADD: createProposedSteps(), buildExistingStepsContext(),
|   |                                               confirmProposedStep(), rejectProposedStep()
|   +-- StepExtractionTriggerService.java    <-- NEW (@Async wrapper, owns full extraction pipeline)
+-- web/
    +-- PatientPathwayController.java        <-- ADD confirm endpoint, reject endpoint
    +-- dto/
        +-- PathwayStepResponse.java         <-- ADD sourceDocumentId, extractionSource fields

src/main/resources/db/migration/
    +-- V16__add_rejected_status_and_ai_source.sql  <-- NEW

frontend/src/features/patients/
+-- types.ts     <-- ADD 'REJECTED' to PathwayStepStatusEnum, add sourceDocumentId + extractionSource
+-- api.ts       <-- ADD useConfirmStep(), useRejectStep() mutation hooks
+-- StepRow.tsx  <-- ADD confirm/reject buttons for PROPOSED steps (or handled in PathwayEditor)
+-- PathwayEditor.tsx  <-- ADD "already covered" section, "show rejected" Collapsible
```

### Pattern 1: @Async Trigger After @Transactional Upload (Critical Pattern)

**What:** The Claude extraction call must run AFTER the upload transaction commits, not inside it. Running inside @Transactional holds the HikariCP connection during the 2-8 second Claude wait, exhausting the connection pool under concurrent load.

**When to use:** Any time a long-running I/O operation (Claude, HTTP call) would be triggered from within a @Transactional method.

```java
// Source: AI-SPEC Section 4b.2 -- verified against Spring @Async documentation
// StepExtractionTriggerService.java
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

**Critical prerequisite:** `@EnableAsync` must be on a `@Configuration` class. Spring Boot does NOT enable async automatically. Without it, `@Async` methods run synchronously on the caller's thread with no error. Add to `AiClientConfig` or a new `AsyncConfig` class.

[VERIFIED: existing codebase -- @EnableAsync not yet present; must be added in Phase 6]

### Pattern 2: EventType as String, Validated in Service Layer

**What:** Declare `eventType` as `String` in the `ExtractionResult.ProposedStep` record, not as `CareEventType`. Validate and map in service code after deserialization.

**When to use:** Any Spring AI `.entity()` call where Claude could return unknown enum values.

```java
// Source: AI-SPEC Section 4.2 and Section 3.5 (Pitfall 5)
// If declared as CareEventType, Claude's unknown value causes Jackson to throw
// InvalidDefinitionException during .entity() -- the whole extraction fails.
// Declaring as String lets service code filter invalids gracefully.
private boolean isValidCareEventType(String eventType, UUID documentId) {
    try {
        CareEventType.valueOf(eventType);
        return true;
    } catch (IllegalArgumentException ex) {
        log.warn("Step extraction returned unknown CareEventType '{}' for document {}",
                eventType, documentId);
        return false;
    }
}
```

[VERIFIED: existing CareEventType enum has 12 values; AI-SPEC Section 4.3 verified this pattern]

### Pattern 3: PostgreSQL Enum ALTER TYPE for REJECTED

**What:** PostgreSQL enums cannot be modified in a transaction. `ALTER TYPE ... ADD VALUE` must run outside a transaction block, which Flyway handles with `SET search_path` but the value itself is non-transactional.

**When to use:** Any time a new value is added to an existing PostgreSQL enum type.

```sql
-- V16__add_rejected_status_and_ai_source.sql
-- ALTER TYPE ... ADD VALUE cannot run inside a transaction.
-- Flyway executes migrations in transactions by default; this statement
-- must be the only DML in this migration, or use -- Flyway annotation.
ALTER TYPE pathway_step_status ADD VALUE IF NOT EXISTS 'REJECTED';

-- Add source tracking columns to patient_pathway_steps
ALTER TABLE patient_pathway_steps
    ADD COLUMN IF NOT EXISTS source VARCHAR(50),          -- 'TEMPLATE', 'MANUAL', 'AI_EXTRACTED'
    ADD COLUMN IF NOT EXISTS source_document_id UUID REFERENCES clinical_documents(id);

CREATE INDEX IF NOT EXISTS idx_pathway_steps_source_doc
    ON patient_pathway_steps(source_document_id)
    WHERE source_document_id IS NOT NULL;
```

**Flyway note:** `ALTER TYPE ... ADD VALUE` is not transactional in PostgreSQL. Flyway wraps migrations in transactions by default. For PostgreSQL enum modifications, either (a) use `-- Flyway disable migration` annotation, or (b) confirm that `ADD VALUE IF NOT EXISTS` works correctly in Flyway's transaction context for PostgreSQL 16. Testing on the actual DB instance is required. [ASSUMED -- the exact Flyway annotation approach needs verification against the project's Flyway version]

### Pattern 4: Confirm Step -- Bundle Edges

**What:** When a nurse confirms a PROPOSED step, its proposed edges are activated atomically. The edge proposals are stored as part of the step creation (in a separate proposed_edges concept or as part of the step JSON) and converted to real `PatientPathwayEdge` rows on confirmation.

**Design decision for planner:** The AI-SPEC stores proposed edges embedded in the `ExtractionResult.ProposedStep.proposedEdges` list. When `createProposedSteps()` persists a PROPOSED step, it must also persist the proposed edge information somewhere retrievable at confirmation time. Two approaches are possible:

**Option A (recommended):** Store proposed edges as a `JSONB` column (`proposed_edges_json`) on `patient_pathway_steps`. On confirmation, read this JSON, resolve step name references to step UUIDs, run cycle detection, and create `PatientPathwayEdge` rows. Clean and requires no new table.

**Option B:** Create a new `proposed_pathway_edges` table parallel to `patient_pathway_edges`. More normalized but adds migration complexity for a transient structure.

Option A is recommended because proposed edges are transient -- they exist only between extraction and confirmation. Storing them as JSONB on the step eliminates a new table and a join. [ASSUMED -- final choice is Claude's discretion per CONTEXT.md]

```java
// PatientPathwayService.confirmProposedStep()
@Transactional
public PathwayStepResponse confirmProposedStep(UUID patientId, UUID stepId, UUID actorId) {
    PatientPathwayStep step = requireStep(patientId, stepId);
    if (step.getStatus() != PathwayStepStatus.PROPOSED) {
        throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Only PROPOSED steps can be confirmed; step is " + step.getStatus());
    }
    step.setStatus(PathwayStepStatus.ACTIVE);
    step = stepRepository.save(step);

    // Resolve and create proposed edges
    activateProposedEdges(patientId, step);

    signalPathwayStepsChanged(patientId);
    return toStepResponse(step, 0, 0, getPrerequisiteIds(step.getPathway().getId(), stepId));
}
```

### Anti-Patterns to Avoid

- **Calling Claude inside @Transactional without @Async:** Holds DB connection for the full Claude response time (2-8 seconds). Under 10 concurrent uploads, exhausts HikariCP pool. [VERIFIED: AI-SPEC Section 4b.2]
- **Declaring eventType as CareEventType enum in ExtractionResult record:** Hard Jackson deserialization failure on any unknown Claude value. [VERIFIED: AI-SPEC Section 3.5 Pitfall 5]
- **Calling .defaultSystem() on the injected ChatClient.Builder singleton:** Mutates shared builder state -- both beans end up with the same system prompt. Use ChatClient.builder(chatModel) static factory per bean. [VERIFIED: existing AiClientConfig.java has this pattern correct already]
- **Forgetting @EnableAsync:** @Async runs synchronously with no error. [VERIFIED: @EnableAsync not present in existing codebase]
- **Auto-confirming steps without nurse action:** PROPOSED status is enforced by structure -- createProposedSteps() always uses PROPOSED, confirmProposedStep() is the only path to ACTIVE, and it is @PreAuthorize-protected. [VERIFIED: AI-SPEC Section 6.1]

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Structured JSON output from Claude | Custom JSON parsing of LLM response string | `ChatClient.entity(ExtractionResult.class)` | Spring AI generates JSON schema, injects format instructions, handles Jackson deserialization; hand-rolling misses schema validation and retry hooks |
| Circuit breaker on Claude call | Try/catch with retry loop | `@CircuitBreaker(name = "claude-api", fallbackMethod = "extractFallback")` | Existing shared breaker already tracks Claude API health; adding a second breaker name would fragment the health signal |
| Cycle detection for proposed edges | New algorithm | Reuse existing `wouldCreateCycle()` / `dfsReaches()` in PatientPathwayService | Already tested, handles the same graph structure; new code is unnecessary duplication |
| Topological sort for PROPOSED step ordering | New sort | Reuse existing `computeTopology()` in PatientPathwayService | Already runs on all steps including PROPOSED; rendering order is computed automatically |
| Async execution | Thread pool management | Spring `@Async` + `@EnableAsync` | Spring Boot manages virtual thread executor; no custom ThreadPoolTaskExecutor needed given `spring.threads.virtual.enabled=true` |
| Enum validation | `switch` or `if/else` chain | `CareEventType.valueOf(eventType)` with try/catch | Single source of truth; auto-updates when enum changes |

---

## Runtime State Inventory

Not applicable -- this is a greenfield capability addition (new service, new DB columns, new enum value, new API endpoints, new UI components). No rename/refactor/migration of existing data.

One note: the `pathway_step_status` PostgreSQL enum is modified by adding `REJECTED`. Existing rows are unaffected -- no data migration needed.

---

## Common Pitfalls

### Pitfall 1: @Async Without @EnableAsync

**What goes wrong:** `StepExtractionTriggerService.triggerAsync()` is annotated `@Async`, but Spring Boot does not enable async processing by default. Without `@EnableAsync` on a `@Configuration` class, the method executes synchronously on the upload request thread inside the `@Transactional` boundary -- holding the HikariCP connection for the entire Claude call.

**Why it happens:** Spring docs say "add @EnableAsync to your configuration" but it is easy to miss. Spring Boot auto-configuration does not apply it automatically.

**How to avoid:** Add `@EnableAsync` to `AiClientConfig` or a dedicated `AsyncConfig` class. Verify by checking that the upload response returns in < 500ms in integration testing even when Claude is slow (mock with a 3-second delay).

**Warning signs:** Upload response takes 3-8 seconds instead of < 500ms; all HikariCP connections blocked under concurrent upload load.

[VERIFIED: @EnableAsync is not present in the codebase -- must be added in Phase 6]

### Pitfall 2: PostgreSQL Enum ALTER TYPE in Flyway Transaction

**What goes wrong:** Flyway wraps each migration in a transaction by default. PostgreSQL allows `ALTER TYPE ... ADD VALUE` to run in a transaction since PostgreSQL 12, but the new value is not visible to other statements in the same transaction until the transaction commits. If the V16 migration does both `ALTER TYPE` and `ALTER TABLE ADD COLUMN` in the same transaction, the column's type reference to the new enum value may fail.

**Why it happens:** PostgreSQL enum modification visibility rules differ from standard DDL.

**How to avoid:** Structure V16 to run `ALTER TYPE pathway_step_status ADD VALUE IF NOT EXISTS 'REJECTED'` as the first (or only) statement. The `source` and `source_document_id` column additions are straightforward DDL and can share the migration. Test by running `./mvnw flyway:migrate` against a fresh Docker Compose PostgreSQL before committing the migration.

**Warning signs:** Flyway migration fails with `ERROR: unsafe use of new value "REJECTED" of enum type pathway_step_status` on startup.

[ASSUMED -- specific Flyway + PostgreSQL 16 interaction; verify by running the migration locally before committing]

### Pitfall 3: Proposed Edge Name Resolution at Confirmation Time

**What goes wrong:** Claude proposes edges by step name (e.g., `predecessorStepName: "Surgical Consultation"`). At confirmation time, the backend must resolve these names to step UUIDs to create `PatientPathwayEdge` rows. If the nurse edited the step name before confirming, the stored name no longer matches.

**Why it happens:** The extraction stores names not UUIDs (Claude doesn't know UUIDs). Name resolution is a join operation that can fail if names change.

**How to avoid:** When `createProposedSteps()` persists proposed edges as JSONB, also store the UUID of the predecessor step (resolved at creation time from the existing steps context) alongside the name. Confirmation then uses the UUID, not the name.

**Warning signs:** Edge creation fails at confirmation time with "predecessor step not found" for steps that clearly exist in the pathway.

### Pitfall 4: Dedup Includes REJECTED Steps

**What goes wrong:** Dedup is specified to check ACTIVE, COMPLETED, and REJECTED steps (D-09). If dedup only checks ACTIVE and COMPLETED, a nurse who rejects a step will see it re-proposed the next time a document for the same patient is uploaded.

**Why it happens:** REJECTED is a new status added in Phase 6; developers may forget to include it in the dedup query.

**How to avoid:** The dedup query in `PatientPathwayService.createProposedSteps()` must explicitly include `status IN ('ACTIVE', 'COMPLETED', 'REJECTED')` when fetching existing event types for comparison.

**Warning signs:** Rejected proposed steps re-appear after a second document upload for the same patient.

### Pitfall 5: Missing `source_document_id` in PathwayStepResponse

**What goes wrong:** The frontend needs `sourceDocumentId` to render the "Source: {filename}" link under each PROPOSED step (per UI-SPEC). If `PathwayStepResponse` does not include this field, the link cannot be rendered without an additional API call.

**Why it happens:** The field must be added to both the entity, the DTO record, and the mapping in `toStepResponse()`.

**How to avoid:** Add `sourceDocumentId` (UUID, nullable) to `PathwayStepResponse`, the `PatientPathwayStep` entity (mapped to `source_document_id` column from V16 migration), and the frontend `PatientPathwayStep` TypeScript interface.

---

## Code Examples

### New ChatClient Bean (stepExtractionClient)

```java
// Source: AI-SPEC Section 4.1 -- verified against existing AiClientConfig.java pattern
// AiClientConfig.java -- add alongside existing documentClassificationClient bean
@Bean
ChatClient stepExtractionClient(ChatModel chatModel) {
    // ChatClient.builder(chatModel) -- static factory, fresh builder per CR-05.
    return ChatClient.builder(chatModel)
            .defaultSystem(ExtractionPrompts.SYSTEM_PROMPT)
            .defaultOptions(AnthropicChatOptions.builder()
                    .temperature(0.1)   // Deterministic extraction
                    .maxTokens(2000)    // Hard cap -- bounded JSON output
                    .build())
            .build();
}
```

### Pipeline Hook in DocumentProcessingService

```java
// Source: AI-SPEC Section 4.4 -- after doc = documentRepository.save(doc)
// Add AFTER line 166 in DocumentProcessingService.java

// 6b. Step extraction -- async, non-blocking. StepExtractionTriggerService
// owns the full pipeline: fetch context, call Claude, persist proposed steps, signal.
// Only fires when patient is linked and document has extractable text.
if (doc.getPatient() != null && extraction.text() != null && !extraction.text().isBlank()) {
    stepExtractionTrigger.triggerAsync(doc.getId(), doc.getPatient().getId(), extraction.text());
}
```

Note: `extraction` is the `ExtractionResult` private record in `DocumentProcessingService`. The field access is `extraction.text()` (record accessor). The `stepExtractionTrigger` field must be injected into `DocumentProcessingService` via constructor injection.

### New Service Methods Signatures (PatientPathwayService)

```java
// Source: AI-SPEC Section 4.4 + codebase analysis

// Build non-PHI context string for Claude's dedup prompt parameter
public String buildExistingStepsContext(UUID patientId) { ... }

// Persist ExtractionResult proposed steps as PROPOSED rows, run dedup, store proposed edges
@Transactional
public void createProposedSteps(UUID patientId, UUID documentId, ExtractionResult result) { ... }

// Transition PROPOSED -> ACTIVE; create proposed edges; signal
@Transactional
public PathwayStepResponse confirmProposedStep(UUID patientId, UUID stepId, UUID actorId) { ... }

// Transition PROPOSED -> REJECTED; signal
@Transactional
public PathwayStepResponse rejectProposedStep(UUID patientId, UUID stepId, UUID actorId) { ... }
```

### New Controller Endpoints

```java
// Source: existing PatientPathwayController.java pattern
// Add to PatientPathwayController.java

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

**Authorization note:** Confirm and reject are `NURSE_NAVIGATOR` and `ADMIN` only (not CARE_COORDINATOR). Clinical step activation is a nurse decision, not a data entry function. [ASSUMED -- CONTEXT.md does not specify; this is Claude's discretion consistent with the role model]

### Frontend Type Extensions

```typescript
// Source: frontend/src/features/patients/types.ts -- add to existing types

// Extend PathwayStepStatusEnum
export type PathwayStepStatusEnum = 'ACTIVE' | 'PROPOSED' | 'COMPLETED' | 'SKIPPED' | 'REJECTED';

// Extend PatientPathwayStep interface
export interface PatientPathwayStep {
  // ... existing fields ...
  sourceDocumentId: string | null;     // UUID of ClinicalDocument if AI_EXTRACTED
  extractionSource: 'TEMPLATE' | 'MANUAL' | 'AI_EXTRACTED' | null;
  extractionRationale: string | null;  // Quote from document motivating extraction (for tooltip)
}
```

### TanStack Query Mutation Hooks

```typescript
// Source: frontend/src/features/patients/api.ts -- follow existing useSkipStep pattern
export function useConfirmStep(patientId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (stepId: string) =>
      apiClient.post<PatientPathwayStep>(
        `/patients/${patientId}/pathway/steps/${stepId}/confirm`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-steps'] });
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-status'] });
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-edges'] });
    },
  });
}

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

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Direct Anthropic SDK | Spring AI ChatClient.entity() | Spring AI 1.1.0 GA (Nov 2025) | Schema generation, format injection, and deserialization handled by framework |
| Thread-per-request blocking | Java 21 virtual threads (Loom) | Spring Boot 3.2+ / Java 21 | @Async + virtual threads: extraction runs without platform thread exhaustion |
| Manual cycle detection | DFS + Kahn's algorithm in PatientPathwayService | Phase 5 | Already implemented; Phase 6 reuses without changes |

**Deprecated/outdated patterns in this context:**
- `ChatClient.Builder` singleton mutation via `.defaultSystem()`: Replaced by `ChatClient.builder(chatModel)` static factory per bean (enforced by CR-05 already in `AiClientConfig`)
- Streaming responses for extraction: Not applicable -- BeanOutputConverter requires complete response before deserialization

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `ALTER TYPE ... ADD VALUE IF NOT EXISTS 'REJECTED'` runs cleanly in Flyway's transaction context on PostgreSQL 16 | Common Pitfalls #2 | Flyway migration fails on startup; must restructure V16 with `-- Flyway disableChecksum` or similar annotation |
| A2 | Proposed edges stored as JSONB column on patient_pathway_steps (Option A) is the preferred approach over a new proposed_pathway_edges table | Pattern 4 | If Option B preferred, requires additional V16 migration table and join queries at confirmation time |
| A3 | Confirm endpoint is restricted to NURSE_NAVIGATOR and ADMIN (not CARE_COORDINATOR) | Code Examples -- controller | If CARE_COORDINATOR should also confirm, update @PreAuthorize; low-risk either way |
| A4 | `extractionRationale` field is surfaced in the frontend as a tooltip (not inline text) to keep the step row compact | Frontend type extensions | If shown inline, step row height increases; UI-SPEC does not explicitly specify the tooltip vs inline decision |
| A5 | Source document filename for "Source: {filename}" link comes from a separate query on the document store or is included in the step response | Frontend type extensions | If not included in PathwayStepResponse, the frontend needs a second API call per PROPOSED step or a denormalized filename field added to the response |

**Items A1 and A2 need a decision before the migration plan is written.** A1 can be verified by running the migration locally. A2 is Claude's discretion per CONTEXT.md.

---

## Open Questions (RESOLVED)

1. **Proposed edge storage mechanism (A2)** -- RESOLVED
   - Resolution: Use JSONB column (`proposed_edges_json TEXT`) on patient_pathway_steps. Implemented in V16 migration (Plan 01) and consumed in confirmProposedStep (Plan 03).

2. **Source document filename exposure in step response** -- RESOLVED
   - Resolution: `sourceDocumentFilename` field added to PathwayStepResponse DTO. Populated via ClinicalDocumentRepository lookup in toStepResponse() for PROPOSED steps with a sourceDocumentId (Plan 03). Non-PHI metadata, avoids second API call from frontend.

3. **Extraction progress indicator and polling** -- RESOLVED (deferred to post-Phase 6)
   - Resolution: Polling after async extraction is not implemented in Phase 6 plans. The existing TanStack Query behavior already refetches pathway-steps on window focus and after mutations. After a document upload, the nurse navigating to/from the patient detail page will see newly PROPOSED steps. Dedicated polling (refetchInterval) is a UX enhancement that can be added without backend changes once the core extraction pipeline ships. This is explicitly deferred -- not dropped -- because the core value (extraction + confirm/reject) is deliverable without it.

---

## Environment Availability

Step 2.6: SKIPPED for Phase 6 Claude integration specifics -- all external dependencies (Anthropic API, PostgreSQL, Temporal) were verified and operational in Phase 4. No new external tools are introduced.

Specific Phase 6 prerequisite: `@EnableAsync` is a code change, not an environment dependency.

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | Covered by existing Keycloak JWT infrastructure |
| V3 Session Management | no | Stateless JWT; no new session state |
| V4 Access Control | yes | Confirm/reject endpoints restricted to NURSE_NAVIGATOR + ADMIN via @PreAuthorize; PROPOSED->ACTIVE transition structurally impossible without explicit confirmation call |
| V5 Input Validation | yes | @Valid on controller request bodies; extraction result validated (enum check, null guard) in service layer |
| V6 Cryptography | no | Document text already handled by EncryptionConverter from Phase 4; no new PHI storage patterns |

### Known Threat Patterns for This Stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| BOLA on confirm/reject -- nurse A confirms nurse B's patient's step | Tampering | `requireStep(patientId, stepId)` ownership check in PatientPathwayService -- same pattern as all existing step mutations |
| PHI leakage via logs in extraction service | Information Disclosure | Log only document UUID, step counts, and invalid eventType strings -- never log extractedText, step names, or extractionRationale; pattern matches DocumentClassificationService |
| Hallucinated step auto-activation | Tampering | PROPOSED status enforced structurally: createProposedSteps() always uses PROPOSED; only confirmProposedStep() transitions to ACTIVE; no code path allows direct ACTIVE creation from extraction |
| Claude API response injection via extractionRationale | Tampering | extractionRationale is stored as text and displayed to the nurse; it is not executed; standard output encoding in React prevents XSS |
| Extraction call without BAA | Compliance | Feature flag `onconavigator.ai.step-extraction.enabled` defaults to false; PHI reaches Anthropic only when flag is explicitly enabled post-BAA |

### HIPAA-Specific PHI Controls for Phase 6

- Document text (PHI) flows: Upload -> DocumentProcessingService (memory) -> StepExtractionTriggerService (memory) -> Anthropic API (requires BAA). The memory path is acceptable; the Anthropic transmission requires BAA.
- `PatientPathwayStep.name` from AI extraction: names like "Radiation Therapy for Ms. Smith" would be PHI. The system prompt instructs Claude to name steps from clinical process vocabulary (event types), not patient-specific language. The `extractionRationale` field contains quotes from the document -- this may contain PHI and must be stored encrypted or treated as PHI-bearing content.
- `extractionRationale` storage: If this field is persisted in `patient_pathway_steps`, it may contain clinical document content re-stated verbatim. Assess whether it should go through `EncryptionConverter` or be excluded from persistence (display only, not stored). [ASSUMED -- the AI-SPEC mentions this field is shown to the nurse; it is not explicit about whether it is persisted vs. ephemeral]

---

## Sources

### Primary (HIGH confidence)
- `.planning/phases/06-ai-step-extraction-from-clinical-documents/06-AI-SPEC.md` -- Complete Spring AI 1.1.0 implementation guidance; ExtractionResult record; StepExtractionService; prompt constants; async pattern; pitfalls; eval strategy; guardrails. Generated with Context7 verification of Spring AI docs.
- `.planning/phases/06-ai-step-extraction-from-clinical-documents/06-UI-SPEC.md` -- Complete frontend component contracts; interaction specifications; copy; accessibility; component inventory.
- `.planning/phases/06-ai-step-extraction-from-clinical-documents/06-CONTEXT.md` -- All locked decisions (D-01 through D-13).
- `src/main/java/com/onconavigator/ai/service/DocumentClassificationService.java` -- Reference implementation pattern for StepExtractionService.
- `src/main/java/com/onconavigator/ai/config/AiClientConfig.java` -- Confirmed ChatClient bean pattern; confirmed @EnableAsync is not yet present.
- `src/main/java/com/onconavigator/service/PatientPathwayService.java` -- Existing step/edge CRUD, cycle detection, topology computation, and signalPathwayStepsChanged pattern.
- `src/main/java/com/onconavigator/web/PatientPathwayController.java` -- Endpoint patterns to follow for confirm/reject.
- `src/main/java/com/onconavigator/domain/enums/PathwayStepStatus.java` -- Confirmed REJECTED is not yet present.
- `src/main/resources/db/migration/V13__create_pathway_step_status_enum.sql` -- Confirms enum syntax to match for V16.
- `src/main/resources/db/migration/V14__create_per_patient_pathway_tables.sql` -- Confirms table structure for the columns to add.
- `frontend/src/features/patients/types.ts` -- Confirmed REJECTED not yet in PathwayStepStatusEnum; sourceDocumentId not yet present.
- `frontend/src/features/patients/api.ts` -- Confirmed confirm/reject hooks do not yet exist; existing mutation patterns to follow.

### Secondary (MEDIUM confidence)
- `.planning/STATE.md` accumulated decisions -- Phase 04-01: ChatClient beans use ChatClient.Builder with per-bean overrides; Phase 02-fix: @Async-equivalent pattern context.

### Tertiary (LOW confidence)
- A2 (proposed edge storage), A3 (auth scope), A4 (rationale display), A5 (filename in response) -- Design decisions under Claude's discretion; no single authoritative source; recommendations based on existing project patterns.

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- all libraries confirmed present in pom.xml and package.json; no new dependencies
- Architecture: HIGH -- all integration points verified by reading actual source files
- Pitfalls: HIGH (pitfalls 1, 3, 4, 5) / MEDIUM (pitfall 2) -- majority verified by reading code; Flyway/PG enum pitfall is cross-referenced against documented behavior
- Frontend types: HIGH -- types.ts and api.ts read directly; gaps are clear and specific

**Research date:** 2026-05-04
**Valid until:** 2026-06-04 (Spring AI 1.1.0 stable; no expected breaking changes in 30 days)
