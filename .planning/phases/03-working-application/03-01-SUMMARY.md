---
phase: 03-working-application
plan: 01
subsystem: api
tags: [java, spring-boot, jpa, hmac, dto, validation, exception-handling, postgresql, flyway]

# Dependency graph
requires:
  - phase: 02-infrastructure
    provides: "Patient entity with AES-GCM encrypted PHI fields, EncryptionConverter, AlertRepository, PatientRepository stubs"
  - phase: 02-infrastructure
    provides: "V1–V7 Flyway migrations establishing schema (patients, care_events, alerts, pathway_templates tables)"
provides:
  - "V8 Flyway migration adding mrn_hmac_token VARCHAR(64) column with B-tree index on patients"
  - "HmacTokenService computing 64-char hex HMAC-SHA256 tokens for deterministic MRN search"
  - "Patient entity mrnHmacToken field (non-encrypted, deterministic index token)"
  - "10 web DTO records (request + response) defining the complete Phase 3 API contract"
  - "GlobalExceptionHandler with PHI-safe error handling (no ex.getMessage() in generic handler)"
  - "AlertRepository severity-ordered JPQL query + countByStatus"
  - "PatientRepository findByMrnHmacToken + countByStatus"
affects:
  - "03-02 — service layer uses all DTOs and HmacTokenService for patient/care event/alert operations"
  - "03-03 — controllers depend on GlobalExceptionHandler for exception translation"
  - "03-04 — pathway status service uses PathwayStatusResponse and PathwayStepStatus DTOs"

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "HMAC-SHA256 index token alongside AES-GCM encrypted columns for deterministic equality search (D-04)"
    - "Java records as immutable DTOs with bean validation annotations on request types"
    - "GlobalExceptionHandler logs ex.getClass().getSimpleName() only — never ex.getMessage() (T-03-01)"
    - "Separate HMAC key from AES encryption key — key separation for blast-radius reduction"

key-files:
  created:
    - src/main/resources/db/migration/V8__add_mrn_hmac_token.sql
    - src/main/java/com/onconavigator/security/HmacTokenService.java
    - src/main/java/com/onconavigator/web/GlobalExceptionHandler.java
    - src/main/java/com/onconavigator/web/dto/CreatePatientRequest.java
    - src/main/java/com/onconavigator/web/dto/PatientResponse.java
    - src/main/java/com/onconavigator/web/dto/CreateCareEventRequest.java
    - src/main/java/com/onconavigator/web/dto/CareEventResponse.java
    - src/main/java/com/onconavigator/web/dto/UpdateCareEventStatusRequest.java
    - src/main/java/com/onconavigator/web/dto/DeactivatePatientRequest.java
    - src/main/java/com/onconavigator/web/dto/ResolveAlertRequest.java
    - src/main/java/com/onconavigator/web/dto/AlertResponse.java
    - src/main/java/com/onconavigator/web/dto/DashboardStatsResponse.java
    - src/main/java/com/onconavigator/web/dto/PathwayStatusResponse.java
    - src/main/java/com/onconavigator/web/dto/PathwayStepStatus.java
    - src/test/java/com/onconavigator/security/HmacTokenServiceTest.java
  modified:
    - src/main/java/com/onconavigator/domain/Patient.java
    - src/main/java/com/onconavigator/repository/AlertRepository.java
    - src/main/java/com/onconavigator/repository/PatientRepository.java
    - src/main/resources/application-local.yml

key-decisions:
  - "AlertResponse carries both alertType (enum name) and severityLabel (display text) so the frontend doesn't need to map enum values for display"
  - "GlobalExceptionHandler generic handler logs ex.getClass().getSimpleName() only — never ex.getMessage() which can contain PHI from entity validation"
  - "AlertRepository JPQL CASE uses string literals ('DELAYED_EVENT') not full enum paths — more reliable with Hibernate 6 and PostgreSQL custom enum types"
  - "findByMrn marked @Deprecated with explanation rather than removed — preserves design decision documentation for future readers"
  - "HMAC key generated fresh and different from AES key — key separation per D-04 and HIPAA best practice"

patterns-established:
  - "Pattern: HMAC index token — store HMAC-SHA256(plaintext, separate_key) alongside AES-GCM encrypted column for deterministic equality search without exposing plaintext"
  - "Pattern: PHI-safe error handler — catch all exceptions, log class name only, return generic client message"
  - "Pattern: DTO validation at boundary — @NotBlank/@NotNull on record components; validated before any business logic via @Valid on controller parameters"

requirements-completed: [DATA-01, DATA-04, DATA-05, ALRT-02, ALRT-05]

# Metrics
duration: 25min
completed: 2026-04-30
---

# Phase 3 Plan 01: Backend Data Layer Contracts Summary

**HMAC-SHA256 deterministic MRN index, 10 Java record DTOs with bean validation, and PHI-safe GlobalExceptionHandler establishing the complete Phase 3 API contract**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-04-30T20:00:00Z
- **Completed:** 2026-04-30T20:25:00Z
- **Tasks:** 3
- **Files modified:** 18 (15 created, 3 modified + application-local.yml)

## Accomplishments

- V8 Flyway migration adds `mrn_hmac_token VARCHAR(64)` column with B-tree index to the patients table
- `HmacTokenService` computes deterministic 64-char hex HMAC-SHA256 tokens using a key separate from the AES encryption key (D-04 key separation)
- `Patient` entity adds `mrnHmacToken` field (non-encrypted — it's a hash, non-reversible)
- 10 Java record DTOs define the complete REST API contract: 4 request types with `@NotBlank`/`@NotNull` bean validation, 6 response types
- `AlertResponse` carries both `alertType` (enum name) and `severityLabel` (display text, e.g., "OVERDUE" for DELAYED_EVENT)
- `GlobalExceptionHandler` (@RestControllerAdvice) handles validation errors, ResponseStatusException, and generic exceptions — generic handler logs `ex.getClass().getSimpleName()` only (T-03-01 PHI safety)
- `AlertRepository.findByStatusOrderedBySeverity` JPQL query orders by clinical urgency: DELAYED (1) → MISSING (2) → OUT_OF_ORDER (3), then by age
- `AlertRepository.countByStatus` and `PatientRepository.countByStatus` for dashboard statistics (ALRT-05)
- `PatientRepository.findByMrnHmacToken` replaces the broken `findByMrn` (random-IV encrypted columns are never equal)
- `HmacTokenServiceTest` verifies determinism, discrimination, 64-char hex output, and key validation (6 tests, all pass)
- `application-local.yml` adds `onconavigator.hmac.key` with a real generated key (distinct from the AES key)

## Files Created/Modified

- `src/main/resources/db/migration/V8__add_mrn_hmac_token.sql` — HMAC index column + B-tree index
- `src/main/java/com/onconavigator/security/HmacTokenService.java` — HMAC-SHA256 token computation
- `src/main/java/com/onconavigator/web/GlobalExceptionHandler.java` — PHI-safe centralized error handling
- `src/main/java/com/onconavigator/web/dto/` — 10 DTO records (request + response types)
- `src/test/java/com/onconavigator/security/HmacTokenServiceTest.java` — 6 unit tests for HmacTokenService
- `src/main/java/com/onconavigator/domain/Patient.java` — added mrnHmacToken field + getter/setter
- `src/main/java/com/onconavigator/repository/AlertRepository.java` — severity query + count method
- `src/main/java/com/onconavigator/repository/PatientRepository.java` — HMAC lookup + count + deprecated findByMrn
- `src/main/resources/application-local.yml` — added hmac.key section

## Decisions Made

- **HMAC string literals in JPQL** — Used `'DELAYED_EVENT'` string literals instead of full enum paths (`com.onconavigator.domain.enums.AlertType.DELAYED_EVENT`) in the CASE expression. Hibernate 6 with PostgreSQL custom enum types is more reliable with string values in CASE expressions.
- **AlertResponse dual fields** — `alertType` (enum name) and `severityLabel` (display text) both included so the frontend can use either without mapping logic.
- **findByMrn marked @Deprecated** — Not removed, to preserve the design decision documentation explaining why direct MRN search fails with AES-GCM encryption.
- **HmacTokenServiceTest added** — Not in plan tasks but specified in task notes. Added 6 unit tests verifying determinism, discrimination, length, and key validation.

## Deviations from Plan

None — plan executed exactly as written. The unit test for HmacTokenService was specified in the task notes and created as part of Task 1.

## Threat Surface Scan

No new network endpoints, auth paths, or trust boundaries introduced in this plan. All files are internal domain/infrastructure layer (migration, DTOs, service, repositories). The `GlobalExceptionHandler` is a net security improvement — it reduces information disclosure surface compared to Spring's default error handling.

## Self-Check: PASSED

All committed files confirmed present:
- `64c8874` — V8 migration, HmacTokenService, Patient entity, HmacTokenServiceTest, application-local.yml
- `281e185` — 10 DTOs + GlobalExceptionHandler
- `b179b4b` — AlertRepository + PatientRepository additions

`./mvnw compile` exits 0. HmacTokenServiceTest: 6/6 tests pass.
