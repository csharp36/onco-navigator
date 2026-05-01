---
phase: 04-ai-document-ingestion
plan: 01
subsystem: ai, database, infra
tags: [spring-ai, anthropic, resilience4j, pdfbox, tess4j, tesseract, flyway, jpa, hipaa]

# Dependency graph
requires:
  - phase: 03-working-application
    provides: CareEvent entity, Patient entity, EncryptionConverter, application-local.yml, Dockerfile
provides:
  - Spring AI 1.1.5 BOM and spring-ai-starter-model-anthropic dependency
  - Resilience4j 2.3.0 circuit breaker dependency and config
  - PDFBox 3.0.7 and Tess4J 5.16.0 document extraction dependencies
  - clinical_documents table (V9 Flyway migration) with bytea blob storage
  - ClinicalDocument JPA entity with @Audited and EncryptionConverter on extractedText
  - ClinicalDocumentRepository with patient and care event lookup methods
  - DocumentClassification and AlertText AI model records for structured Claude output
  - DocumentUploadResponse DTO with PatientCandidate nested record
  - Two ChatClient beans (classification temp=0.1, alert generation temp=0.3)
  - ClassificationPrompts and AlertPrompts constant classes
  - Resilience4j claude-api circuit breaker instance config
  - Multipart upload config (20MB max)
  - Tesseract OCR native library in Docker runtime image
  - onconavigator.ai.document-classification.enabled feature flag (default false)
affects: [04-02, 04-03, 04-04, 04-05, 04-06, 04-07]

# Tech tracking
tech-stack:
  added: [spring-ai-bom 1.1.5, spring-ai-starter-model-anthropic, resilience4j-spring-boot3 2.3.0, spring-boot-starter-aop, pdfbox 3.0.7, pdfbox-io 3.0.7, pdfbox-tools 3.0.7, tess4j 5.16.0]
  patterns: [ChatClient bean per AI use case, Resilience4j circuit breaker YAML config, zero-PHI prompt boundary, bytea blob storage for clinical documents]

key-files:
  created:
    - src/main/resources/db/migration/V9__create_clinical_documents.sql
    - src/main/java/com/onconavigator/domain/ClinicalDocument.java
    - src/main/java/com/onconavigator/repository/ClinicalDocumentRepository.java
    - src/main/java/com/onconavigator/ai/config/AiClientConfig.java
    - src/main/java/com/onconavigator/ai/model/DocumentClassification.java
    - src/main/java/com/onconavigator/ai/model/AlertText.java
    - src/main/java/com/onconavigator/ai/prompt/ClassificationPrompts.java
    - src/main/java/com/onconavigator/ai/prompt/AlertPrompts.java
    - src/main/java/com/onconavigator/web/dto/DocumentUploadResponse.java
  modified:
    - pom.xml
    - src/main/resources/application-local.yml
    - Dockerfile

key-decisions:
  - "ChatClient beans use per-bean temperature/maxTokens overrides with shared model from application config"
  - "ClinicalDocument uses @ManyToOne Patient relationship but careEventId is a plain UUID column (nullable FK for optional care event linkage)"
  - "extractedText encrypted via EncryptionConverter; content byte[] relies on storage-level encryption (RDS KMS) since converter operates on String not byte[]"
  - "Document classification feature-flagged off by default (onconavigator.ai.document-classification.enabled=false) pending Anthropic BAA"

patterns-established:
  - "ChatClient bean pattern: one @Bean per AI use case with distinct system prompt and temperature"
  - "AI prompt constants: final class with private constructor, SYSTEM_PROMPT and USER_TEMPLATE static fields"
  - "Dual PHI boundary: classification (full PHI, BAA-required) vs alert generation (zero-PHI)"
  - "Circuit breaker config: YAML-based Resilience4j instance config under resilience4j.circuitbreaker.instances"

requirements-completed: [DOC-05, AI-04]

# Metrics
duration: 5min
completed: 2026-05-01
---

# Phase 4 Plan 01: AI Document Ingestion Foundation Summary

**Spring AI 1.1.5 + Resilience4j + PDFBox + Tess4J dependencies, clinical_documents schema with @Audited EncryptionConverter entity, two ChatClient beans, circuit breaker config, Dockerfile Tesseract**

## Performance

- **Duration:** 5 min
- **Started:** 2026-05-01T20:47:31Z
- **Completed:** 2026-05-01T20:52:39Z
- **Tasks:** 2
- **Files modified:** 12

## Accomplishments
- All Phase 4 Maven dependencies added and resolving (Spring AI BOM 1.1.5, Resilience4j 2.3.0, PDFBox 3.0.7, Tess4J 5.16.0) with correct artifact names
- Flyway V9 migration creates clinical_documents table with bytea blob storage, patient/care_event FKs, and indexes
- ClinicalDocument JPA entity follows established pattern: @Audited for HIPAA, EncryptionConverter on extractedText PHI field, no @Lob
- Two ChatClient beans configured for classification (low temp) and alert generation (moderate temp) with distinct system prompts
- Resilience4j circuit breaker configured for claude-api with 50% failure threshold and 30s open state
- Dockerfile runtime stage includes Tesseract OCR native library (tesseract-ocr, tesseract-ocr-eng, libtesseract-dev)

## Task Commits

Each task was committed atomically:

1. **Task 1: Add Maven dependencies, Flyway V9 migration, ClinicalDocument entity, repository, and DTOs** - `d190820` (feat)
2. **Task 2: Spring AI ChatClient config, Resilience4j config, prompt constants, application-local.yml updates, Dockerfile Tesseract** - `1822c1e` (feat)

## Files Created/Modified
- `pom.xml` - Added Spring AI BOM, spring-ai-starter-model-anthropic, Resilience4j, AOP, PDFBox, Tess4J dependencies
- `src/main/resources/db/migration/V9__create_clinical_documents.sql` - clinical_documents table with bytea blob, FKs to patients and care_events
- `src/main/java/com/onconavigator/domain/ClinicalDocument.java` - JPA entity with @Audited, EncryptionConverter on extractedText
- `src/main/java/com/onconavigator/repository/ClinicalDocumentRepository.java` - Patient and care event lookup methods
- `src/main/java/com/onconavigator/ai/config/AiClientConfig.java` - Two ChatClient beans for classification and alert generation
- `src/main/java/com/onconavigator/ai/model/DocumentClassification.java` - Structured output record for Claude classification
- `src/main/java/com/onconavigator/ai/model/AlertText.java` - Structured output record for alert text generation
- `src/main/java/com/onconavigator/ai/prompt/ClassificationPrompts.java` - System prompt and user template for document classification
- `src/main/java/com/onconavigator/ai/prompt/AlertPrompts.java` - Zero-PHI system prompt and user template for alert generation
- `src/main/java/com/onconavigator/web/dto/DocumentUploadResponse.java` - Upload response DTO with PatientCandidate nested record
- `src/main/resources/application-local.yml` - Spring AI, Resilience4j, multipart, tessdata, feature flag config
- `Dockerfile` - Tesseract OCR native library installation in runtime stage

## Decisions Made
- ChatClient beans use `ChatClient.Builder` (injected by Spring AI auto-config) rather than manually creating from `AnthropicChatModel` -- follows Spring AI 1.1.x recommended pattern
- ClinicalDocument.careEventId is a plain UUID column rather than a @ManyToOne relationship -- document may be created before care event linkage, and bidirectional relationship would complicate the care event entity
- extractedText uses EncryptionConverter (String->byte[]) but content does not -- EncryptionConverter operates on String, and raw file bytes cannot be meaningfully string-converted
- API key uses `${ANTHROPIC_API_KEY:sk-ant-placeholder}` env var pattern consistent with existing `APP_DB_PASSWORD` pattern

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None - all dependencies resolved on first compile, no conflicts with existing stack.

## User Setup Required

None - no external service configuration required for compilation. ANTHROPIC_API_KEY environment variable needed at runtime for actual Claude API calls (placeholder used for development).

## Next Phase Readiness
- All Phase 4 dependencies are in place for Plans 02-07
- Plan 02 (test corpus) and Plan 03 (extraction pipeline services) can proceed
- ChatClient beans and prompt constants are ready for service layer integration in Plan 03
- ClinicalDocument entity and repository are ready for Plan 04 (controller/endpoint)
- Resilience4j circuit breaker config is ready for @CircuitBreaker annotation usage in Plan 03

## Self-Check: PASSED

All 12 created/modified files verified present on disk. Both task commit hashes (d190820, 1822c1e) verified in git log.

---
*Phase: 04-ai-document-ingestion*
*Completed: 2026-05-01*
