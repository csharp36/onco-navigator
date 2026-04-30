---
phase: 01-hipaa-foundation
plan: 02
subsystem: data-layer
tags: [hipaa, encryption, jpa, flyway, audit, postgresql]
dependency_graph:
  requires: [01-01]
  provides: [database-schema, jpa-entities, phi-encryption, audit-log]
  affects: [01-03, 01-04, 01-05]
tech_stack:
  added:
    - Hibernate Envers (@Audited on ePHI entities, _AUD revision tables)
    - AES-256-GCM column encryption via JPA AttributeConverter
    - Flyway V1/V2/V3 SQL migrations (base schema, audit log, permissions)
  patterns:
    - JPA AttributeConverter for transparent column encryption
    - ApplicationContextProvider static accessor for converter Spring bean access
    - INSERT-only audit_log enforced at DB layer (REVOKE UPDATE/DELETE/TRUNCATE)
    - PostgreSQL ENUM types for domain status fields
key_files:
  created:
    - src/main/resources/db/migration/V1__create_base_schema.sql
    - src/main/resources/db/migration/V2__create_audit_log.sql
    - src/main/resources/db/migration/V3__audit_permissions.sql
    - src/main/java/com/onconavigator/domain/Patient.java
    - src/main/java/com/onconavigator/domain/CareEvent.java
    - src/main/java/com/onconavigator/domain/Alert.java
    - src/main/java/com/onconavigator/domain/AuditLogEntry.java
    - src/main/java/com/onconavigator/domain/PathwayTemplate.java
    - src/main/java/com/onconavigator/domain/enums/CancerType.java
    - src/main/java/com/onconavigator/domain/enums/CareEventType.java
    - src/main/java/com/onconavigator/domain/enums/CareEventStatus.java
    - src/main/java/com/onconavigator/domain/enums/AlertType.java
    - src/main/java/com/onconavigator/domain/enums/AlertStatus.java
    - src/main/java/com/onconavigator/domain/enums/PatientStatus.java
    - src/main/java/com/onconavigator/repository/PatientRepository.java
    - src/main/java/com/onconavigator/repository/CareEventRepository.java
    - src/main/java/com/onconavigator/repository/AlertRepository.java
    - src/main/java/com/onconavigator/repository/AuditLogRepository.java
    - src/main/java/com/onconavigator/security/EncryptionConverter.java
    - src/main/java/com/onconavigator/config/EncryptionConfig.java
    - src/main/java/com/onconavigator/config/ApplicationContextProvider.java
  modified:
    - src/main/resources/application-local.yml
decisions:
  - "[01-02]: CareEventRepository uses findByPatient_IdOrderByEventDateDesc (underscore traversal) — CareEvent maps patient as @ManyToOne relationship, not a plain UUID field, so Spring Data requires the property path notation"
  - "[01-02]: AuditLogEntry uses IDENTITY generation strategy (maps to PostgreSQL BIGSERIAL) rather than UUID — audit_log uses BIGSERIAL primary key per V2 migration for sequential ordering and index efficiency"
  - "[01-02]: PatientRepository.findByMrn marked as TODO — AES-GCM with random IV means encrypted ciphertexts are never equal for the same plaintext; Phase 3 will add a deterministic HMAC index token for MRN search"
  - "[01-02]: EncryptionConverter uses ApplicationContextProvider (static accessor) for SecretKey bean — JPA converters are instantiated by Hibernate outside Spring lifecycle; constructor injection is not available"
metrics:
  duration: "~12 minutes"
  completed: "2026-04-30"
  tasks_completed: 2
  files_created: 21
  files_modified: 1
---

# Phase 01 Plan 02: Database Schema, PHI Encryption, and Audit Layer Summary

Flyway migrations create the complete application schema with encrypted PHI columns (BYTEA), immutable audit log table with DB-level INSERT-only enforcement, and full JPA domain layer with Hibernate Envers audit annotations and AES-256-GCM column encryption for all ePHI fields.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Flyway migrations: base schema, audit log, permissions | 388d83d | V1/V2/V3 SQL migrations |
| 2 | JPA entities, AES-GCM converter, repositories | c783751 | 20 Java files + application-local.yml |

## What Was Built

### Flyway Migrations (3 files)

**V1__create_base_schema.sql** — Core schema:
- 6 PostgreSQL ENUM types matching Java enums
- `patients` table with `first_name_encrypted`, `last_name_encrypted`, `date_of_birth_encrypted`, `mrn_encrypted` as `BYTEA NOT NULL` — no readable PHI in the database
- `care_events` table with `notes_encrypted BYTEA` for PHI notes
- `alerts` table (non-PHI: references patients by UUID, stores deviation descriptions)
- `pathway_templates` table with `template_data JSONB` for config-as-data pathway definitions
- 7 performance indexes on common query patterns

**V2__create_audit_log.sql** — Immutable audit log:
- `audit_log` table with BIGSERIAL primary key (sequential ordering for compliance queries)
- Fields: actor_id, actor_role, action, resource_type, resource_id, timestamp, ip_address, success, detail_hash, request_path, http_method
- 4 compliance query indexes (actor+time, resource+time, action+time, time-only)

**V3__audit_permissions.sql** — Database-level tamper resistance:
- `REVOKE UPDATE, DELETE, TRUNCATE ON audit_log FROM onco_app` — core HIPAA control
- `GRANT INSERT ON audit_log TO audit_writer` (role from init-db.sh)
- `GRANT audit_writer TO onco_app` — app user inherits INSERT-only access
- `GRANT ALL ON patients, care_events, alerts, pathway_templates TO onco_app`

### JPA Entities

| Entity | @Audited | PHI Encrypted | Notes |
|--------|----------|---------------|-------|
| Patient | Yes | firstName, lastName, dateOfBirth, mrn | 4 BYTEA columns via EncryptionConverter |
| CareEvent | Yes | notes | Optional BYTEA, ManyToOne to Patient |
| Alert | Yes | No | Contains deviation descriptions, not PHI |
| AuditLogEntry | No | No | INSERT-only, all columns updatable=false |
| PathwayTemplate | Yes | No | JSONB template_data via @JdbcTypeCode |

### AES-256-GCM EncryptionConverter

- Implements `AttributeConverter<String, byte[]>`
- `convertToDatabaseColumn`: generates 12-byte random IV via SecureRandom, encrypts with AES/GCM/NoPadding, stores `[12-byte IV][ciphertext]`
- `convertToEntityAttribute`: extracts IV from first 12 bytes, decrypts remainder; GCM 128-bit authentication tag provides tamper detection (AEADBadTagException on corruption)
- SecretKey retrieved via `ApplicationContextProvider.getBean(SecretKey.class)` — necessary because Hibernate instantiates converters outside Spring lifecycle

### EncryptionConfig

Provides the `SecretKey` bean from a Base64-encoded 256-bit key in `onconavigator.encryption.key`. Validates key length (must be 32 bytes) on startup. Placeholder value in `application-local.yml` with instructions to generate: `openssl rand -base64 32`.

### Repositories

| Repository | Key Methods |
|-----------|-------------|
| PatientRepository | `findByStatus`, `findByMrn` (TODO: Phase 3 HMAC index) |
| CareEventRepository | `findByPatient_IdOrderByEventDateDesc` |
| AlertRepository | `findByStatusOrderByCreatedAtDesc`, `findByPatientIdAndStatus`, `existsByPatientIdAndPathwayStepNameAndStatus` |
| AuditLogRepository | `findByActorIdAndTimestampBetween` |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] CareEventRepository derived query uses underscore traversal**
- **Found during:** Task 2 implementation
- **Issue:** `CareEvent` maps patient as `@ManyToOne Patient patient`, not a plain UUID field. Spring Data derived queries on relationship fields require the property path notation with underscore (`patient_id` traversal maps to `patient.id`).
- **Fix:** Changed `findByPatientIdOrderByEventDateDesc` to `findByPatient_IdOrderByEventDateDesc`
- **Files modified:** `src/main/java/com/onconavigator/repository/CareEventRepository.java`
- **Commit:** c783751

## HIPAA Compliance Controls Implemented

| Threat ID | Control | Implementation |
|-----------|---------|----------------|
| T-02-01 | Audit log tamper resistance | V3 REVOKE UPDATE/DELETE/TRUNCATE; AuditLogEntry all columns updatable=false |
| T-02-02 | PHI at-rest encryption | AES-256-GCM EncryptionConverter on all PHI fields; BYTEA columns in DB |
| T-02-04 | Repudiation prevention | @Audited on Patient, CareEvent, Alert, PathwayTemplate creates _AUD tables |
| T-02-05 | Key protection | Key from config (not hardcoded); placeholder in local yml; prod via Secrets Manager |

## Known Stubs

- `onconavigator.encryption.key` in `application-local.yml` contains a placeholder value (`AAAA...`). This is intentional — the developer must generate a real key before running the application. Instructions are in the config comment.
- `PatientRepository.findByMrn` is a stub that will not work with encrypted columns. Documented as TODO for Phase 3.

## Self-Check: PASSED

Files exist:
- src/main/resources/db/migration/V1__create_base_schema.sql: FOUND
- src/main/resources/db/migration/V2__create_audit_log.sql: FOUND
- src/main/resources/db/migration/V3__audit_permissions.sql: FOUND
- src/main/java/com/onconavigator/domain/Patient.java: FOUND
- src/main/java/com/onconavigator/security/EncryptionConverter.java: FOUND

Commits exist:
- 388d83d: feat(01-02): add Flyway migrations
- c783751: feat(01-02): add JPA entities, AES-GCM encryption converter, and repositories
