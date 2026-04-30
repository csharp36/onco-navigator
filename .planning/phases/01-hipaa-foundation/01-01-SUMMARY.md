---
phase: 01-hipaa-foundation
plan: "01"
subsystem: foundation
tags: [spring-boot, docker-compose, keycloak, temporal, hipaa, logging, maven]
dependency_graph:
  requires: []
  provides:
    - maven-project-spring-boot-3.5
    - docker-compose-local-stack
    - keycloak-realm-rbac
    - spring-profiles-local-aws
    - phi-log-redaction
  affects:
    - all subsequent plans (every plan builds on this foundation)
tech_stack:
  added:
    - Spring Boot 3.5.0 (parent BOM)
    - Java 21 with virtual threads enabled
    - temporal-spring-boot-starter 1.32.0
    - jasypt-spring-boot-starter 3.0.5
    - hibernate-envers (via Boot BOM) for HIPAA audit trail
    - flyway-core + flyway-database-postgresql (both required for Flyway 11+)
    - spring-boot-starter-oauth2-resource-server for Keycloak JWT validation
    - PostgreSQL 16 (Docker), Keycloak 26 (Docker), Temporal v1.28.3 (Docker)
  patterns:
    - Spring profiles (local / aws) for environment-specific config
    - Jasypt ENC(...) pattern for encrypted config values in local dev
    - Logback PHI redaction via custom ClassicConverter
    - Docker Compose service-name networking (all services by name, no hardcoded IPs)
    - Two-database init pattern (onconavigator app DB + temporal DB from single init script)
key_files:
  created:
    - pom.xml
    - .mvn/wrapper/maven-wrapper.properties
    - mvnw
    - mvnw.cmd
    - .gitignore
    - src/main/java/com/onconavigator/OncoNavigatorApplication.java
    - src/main/java/com/onconavigator/logging/PhiRedactingConverter.java
    - src/main/resources/application.yml
    - src/main/resources/application-local.yml
    - src/main/resources/application-aws.yml
    - src/main/resources/logback-spring.xml
    - docker-compose.yml
    - docker/init-db.sh
    - .env.example
    - keycloak/realm-export.json
  modified: []
decisions:
  - "Jasypt ENC(placeholder_encrypted_password) in application-local.yml — real password encrypted at developer setup time with JASYPT_ENCRYPTOR_PASSWORD; prevents plaintext credentials in committed config"
  - "docker-compose.yml app service commented out — during active dev, run Spring Boot directly via mvnw spring-boot:run to avoid Docker rebuild on each change"
  - "Two separate PostgreSQL users created in init-db.sh (onco_app + temporal) for minimal-privilege separation"
  - "audit_writer and audit_reader roles created at DB init — assigned to specific users in later plans when audit tables exist"
  - "KC_DB: dev-mem for Keycloak in local dev — avoids needing a separate Keycloak PostgreSQL schema"
  - "Removed deprecated version: attribute from docker-compose.yml (Docker Compose v2 ignores it with a warning)"
metrics:
  duration_minutes: 5
  tasks_completed: 2
  files_created: 15
  files_modified: 0
  completed_date: "2026-04-30"
---

# Phase 01 Plan 01: Project Foundation Summary

Spring Boot 3.5 Maven project scaffold with full Phase 1 dependency set, Docker Compose local dev stack (PostgreSQL 16 + Temporal v1.28.3 + Keycloak 26), Keycloak realm with three RBAC roles and test users, local/AWS Spring profile split, and HIPAA-compliant PHI log redaction via Logback.

## Tasks Completed

| Task | Description | Commit | Files |
|------|-------------|--------|-------|
| 1 | Maven project with Spring Boot 3.5 and all Phase 1 dependencies | 638bb02 | pom.xml, mvnw, mvnw.cmd, .mvn/wrapper/maven-wrapper.properties, OncoNavigatorApplication.java, .gitignore |
| 2 | Docker Compose, Spring profiles, Keycloak realm, Logback PHI redaction | 7c44583 | docker-compose.yml, docker/init-db.sh, .env.example, application*.yml, logback-spring.xml, PhiRedactingConverter.java, keycloak/realm-export.json |

## Verification Results

| Check | Result |
|-------|--------|
| `./mvnw validate` | PASS (exit 0) |
| `docker compose config` | PASS (exit 0) |
| `ROLE_NURSE_NAVIGATOR` in realm-export.json | PASS (count: 2) |
| `PHI_REDACTED` in PhiRedactingConverter.java | PASS (count: 3) |
| `pgcrypto` in docker/init-db.sh | PASS (count: 2) |
| `flyway-database-postgresql` in pom.xml | PASS |
| `temporal-spring-boot-starter:1.32.0` in pom.xml | PASS |
| `issuer-uri: http://localhost:9090/realms/onco-navigator` in application-local.yml | PASS |
| `aws-secretsmanager` in application-aws.yml | PASS |

## HIPAA Controls Implemented

| Control | Implementation |
|---------|----------------|
| PHI in logs | PhiRedactingConverter strips 8 PHI field patterns; MarkerFilter denies PHI-marked log events |
| Secrets management | Jasypt ENC(...) for local config; AWS profile uses Secrets Manager placeholder |
| Column-level encryption | pgcrypto extension enabled in onconavigator database at init |
| Audit trail infrastructure | audit_writer + audit_reader DB roles created; hibernate-envers declared for @Audited entities |
| Access control | Keycloak realm with ROLE_NURSE_NAVIGATOR, ROLE_CARE_COORDINATOR, ROLE_ADMIN; Spring OAuth2 resource server configured |
| Least privilege | Separate DB users (onco_app for app, temporal for Temporal); no superuser access in app |

## Deviations from Plan

None — plan executed exactly as written.

Minor cleanup applied: Removed deprecated `version: "3.9"` attribute from docker-compose.yml (Docker Compose v2 treats it as obsolete and warns; removing it is the correct action per current Docker Compose specification).

## Known Stubs

1. **application-local.yml** `spring.datasource.password: ENC(placeholder_encrypted_password)` — intentional placeholder. Developer must run `./mvnw jasypt:encrypt-value` to generate the real encrypted value using their JASYPT_MASTER_KEY from `.env`. This is documented in the file comment. The app cannot connect to the local DB until this is replaced with a real ENC(...) value.

## Threat Surface Scan

No new threat surface beyond what is modeled in the plan's threat model. All STRIDE mitigations are in place:
- T-01-01 (Spoofing): Keycloak JWT with 15-min expiry configured in realm-export.json
- T-01-02 (Tampering): `.env` in .gitignore, Jasypt ENC(...) in committed config
- T-01-03 (Information Disclosure): PhiRedactingConverter + MarkerFilter in logback-spring.xml
- T-01-04 (Docker env): Accepted — local dev only; noted in .env.example
- T-01-05 (Elevation of Privilege): No self-registration in Keycloak realm; roles managed by admin only

## Self-Check: PASSED
