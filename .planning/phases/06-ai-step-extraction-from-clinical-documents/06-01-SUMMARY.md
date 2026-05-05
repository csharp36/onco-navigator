---
phase: 06-ai-step-extraction-from-clinical-documents
plan: "01"
subsystem: ai-data-contracts
tags: [flyway-migration, jpa-entity, spring-ai, chat-client, feature-flag, hipaa]
dependency_graph:
  requires: []
  provides:
    - pathway_step_status.REJECTED (PostgreSQL enum value + Java enum)
    - patient_pathway_steps.source column (VARCHAR 50)
    - patient_pathway_steps.source_document_id column (UUID FK)
    - patient_pathway_steps.proposed_edges_json column (TEXT)
    - clinical_documents.already_covered_event_types column (TEXT)
    - PatientPathwayStep entity source tracking fields
    - ClinicalDocument.alreadyCoveredEventTypes for D-10 transparency
    - PathwayStepResponse.sourceDocumentId/extractionSource/sourceDocumentFilename fields
    - ExtractionResult record (ProposedStep + ProposedEdge inner records)
    - ExtractionPrompts.SYSTEM_PROMPT with all 12 CareEventType values
    - ExtractionPrompts.USER_TEMPLATE with {documentText}/{existingSteps} placeholders
    - stepExtractionClient ChatClient bean (temperature=0.1, maxTokens=2000)
    - onconavigator.ai.step-extraction.enabled feature flag (default: false)
  affects:
    - PatientPathwayService.toStepResponse (updated constructor call)
    - Downstream Phase 6 plans (02-05) which build services against these contracts
tech_stack:
  added:
    - ExtractionResult.java (new Spring AI output record with nested inner records)
    - ExtractionPrompts.java (new prompt constants class)
    - V16__add_rejected_status_and_ai_source.sql (Flyway migration)
  patterns:
    - Spring AI ChatClient.builder(chatModel) static factory per bean (CR-05 pattern)
    - String eventType in ExtractionResult (not CareEventType enum) per AI-SPEC Pitfall 5
    - Feature flag via @Value with false default until Anthropic BAA is signed (T-06-01)
    - @JsonProperty(required = true) on all mandatory Claude response fields
key_files:
  created:
    - src/main/resources/db/migration/V16__add_rejected_status_and_ai_source.sql
    - src/main/java/com/onconavigator/ai/model/ExtractionResult.java
    - src/main/java/com/onconavigator/ai/prompt/ExtractionPrompts.java
  modified:
    - src/main/java/com/onconavigator/domain/enums/PathwayStepStatus.java
    - src/main/java/com/onconavigator/domain/PatientPathwayStep.java
    - src/main/java/com/onconavigator/domain/ClinicalDocument.java
    - src/main/java/com/onconavigator/web/dto/PathwayStepResponse.java
    - src/main/java/com/onconavigator/ai/config/AiClientConfig.java
    - src/main/resources/application-local.yml
    - src/main/java/com/onconavigator/service/PatientPathwayService.java
decisions:
  - "[06-01]: ExtractionResult.eventType is String not CareEventType -- hard Jackson deserialization failure on unknown values is replaced with service-layer filter per AI-SPEC Pitfall 5"
  - "[06-01]: stepExtractionClient uses ChatClient.builder(chatModel) static factory (not injected singleton builder) -- avoids CR-05 shared builder state mutation"
  - "[06-01]: REJECTED enum value added via ALTER TYPE ADD VALUE IF NOT EXISTS placed FIRST in V16 migration -- safe in PostgreSQL 16 when first statement in Flyway transaction"
  - "[06-01]: PathwayStepResponse.sourceDocumentFilename passes null from toStepResponse -- resolved to actual filename in Plan 03 when document lookup service is implemented"
metrics:
  duration: "12 minutes"
  completed: "2026-05-04"
  tasks_completed: 2
  files_created: 3
  files_modified: 7
---

# Phase 6 Plan 01: Data Contracts and AI Configuration Summary

**One-liner:** V16 Flyway migration (REJECTED enum, AI source columns, D-10 field) plus Spring AI ExtractionResult/ExtractionPrompts/stepExtractionClient bean contracts for Phase 6 step extraction.

## What Was Built

This plan established all foundational data and AI contracts that downstream Phase 6 plans depend on. No business logic was implemented — purely types, schemas, and configuration.

### Task 1: Schema and Entity Changes

**V16 Flyway Migration** (`V16__add_rejected_status_and_ai_source.sql`):
- Adds `REJECTED` to the `pathway_step_status` PostgreSQL enum (placed FIRST to satisfy PostgreSQL 16 `ALTER TYPE` transaction constraint)
- Adds `source VARCHAR(50)`, `source_document_id UUID FK`, `proposed_edges_json TEXT` columns to `patient_pathway_steps`
- Adds `already_covered_event_types TEXT` to `clinical_documents` for D-10 transparency display
- Creates `idx_pathway_steps_source_doc` partial index on `source_document_id`
- GRANTs to `onco_app`

**PathwayStepStatus enum** — added `REJECTED` as fifth value with D-07/D-09 audit trail Javadoc.

**PatientPathwayStep entity** — added `source`, `sourceDocumentId`, `proposedEdgesJson` fields with standard getters/setters following existing plain Java (no Lombok) pattern.

**ClinicalDocument entity** — added `alreadyCoveredEventTypes` field with getter/setter. Non-PHI metadata (comma-separated CareEventType values only).

**PathwayStepResponse record** — appended `sourceDocumentId`, `extractionSource`, `sourceDocumentFilename` parameters. Updated Javadoc `@param` list. Updated `PatientPathwayService.toStepResponse()` constructor call to pass new fields.

### Task 2: AI Contracts

**ExtractionResult.java** — top-level wrapper record with:
- `List<ProposedStep> proposedSteps` (required)
- `List<String> alreadyCoveredEventTypes` (required, always present)
- `ProposedStep` inner record: `stepName`, `eventType` (String not enum), `estimatedTimeWindowDays`, `proposedEdges`, `extractionRationale`
- `ProposedEdge` inner record: `predecessorStepName`, `predecessorIsExistingStep`
- All `@JsonProperty(required = true)` on mandatory fields; `@JsonPropertyOrder` on all records

**ExtractionPrompts.java** — prompt constants:
- `SYSTEM_PROMPT`: 7 output rules, all 12 `CareEventType` values explicitly listed, negation rule (Rule 4), administrative noise guidance
- `USER_TEMPLATE`: `{documentText}` and `{existingSteps}` parameterized per-call

**AiClientConfig.java** — added `stepExtractionClient @Bean`:
- Uses `ChatClient.builder(chatModel)` static factory (CR-05 pattern)
- `temperature=0.1` (deterministic extraction)
- `maxTokens=2000` (hard cap per AI-SPEC Pitfall 4)
- Class Javadoc updated from 2 to 3 beans

**application-local.yml** — added `onconavigator.ai.step-extraction.enabled: ${ONCO_AI_STEP_EXTRACTION_ENABLED:false}` sibling to `document-classification` (HIPAA T-06-01: feature flag prevents PHI transmission until BAA is signed).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] PatientPathwayService.toStepResponse constructor call updated**
- **Found during:** Task 1 — Java record canonical constructor has fixed arity
- **Issue:** PathwayStepResponse record gained 3 new parameters; the existing `new PathwayStepResponse(...)` call in `PatientPathwayService.toStepResponse()` would fail compilation with 17 args instead of 20
- **Fix:** Updated the constructor call to pass `step.getSourceDocumentId()`, `step.getSource()`, and `null` (sourceDocumentFilename resolved in Plan 03)
- **Files modified:** `src/main/java/com/onconavigator/service/PatientPathwayService.java`
- **Commit:** 41bbf7c

**2. Note on Flyway migration verification:** The plan called for `./mvnw flyway:migrate` to succeed. The Flyway Maven plugin is not configured in `pom.xml` (Flyway runs at Spring Boot startup, not as a Maven goal). The migration was verified by applying all SQL statements directly via `psql` against the running Docker PostgreSQL instance, which confirmed all `ADD COLUMN`, `ALTER TYPE`, `CREATE INDEX`, and `GRANT` statements execute without error. The V16 migration file will be applied by Spring Boot's Flyway autoconfiguration at next application startup.

## Known Stubs

- `PathwayStepResponse.sourceDocumentFilename` is `null` from `PatientPathwayService.toStepResponse()`. Plan 03 will implement the document lookup service that resolves the original filename for the "Source: {filename}" UI link. This is documented in the constructor call comment.

## Threat Flags

None — no new network endpoints or auth paths introduced. The `stepExtractionClient` bean sends data to Anthropic API only when `step-extraction.enabled=true` (default: false per T-06-01). The `alreadyCoveredEventTypes` field is non-PHI metadata.

## Self-Check: PASSED

- FOUND: V16 migration `src/main/resources/db/migration/V16__add_rejected_status_and_ai_source.sql`
- FOUND: `src/main/java/com/onconavigator/ai/model/ExtractionResult.java`
- FOUND: `src/main/java/com/onconavigator/ai/prompt/ExtractionPrompts.java`
- FOUND: `06-01-SUMMARY.md`
- FOUND: Task 1 commit `41bbf7c`
- FOUND: Task 2 commit `db7f7f8`
