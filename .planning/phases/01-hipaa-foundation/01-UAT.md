---
status: testing
phase: 01-hipaa-foundation
source: [01-01-SUMMARY.md, 01-02-SUMMARY.md, 01-03-SUMMARY.md, 01-04-SUMMARY.md, 01-05-SUMMARY.md]
started: 2026-05-07T12:00:00Z
updated: 2026-05-07T12:00:00Z
---

## Current Test

number: 1
name: Cold Start Smoke Test
expected: |
  Kill any running containers and servers. Run `docker compose up -d` from the project root.
  All services start: PostgreSQL (5432), Temporal (7233), Temporal UI (8080), Keycloak (9090).
  Run `./mvnw clean verify` — project compiles, Flyway migrations apply against Testcontainers,
  and all 23 tests pass (BUILD SUCCESS).
awaiting: user response

## Tests

### 1. Cold Start Smoke Test
expected: Kill any running containers. Run `docker compose up -d` — all 4 services start healthy. Run `./mvnw clean verify` — compiles and all 23 tests pass.
result: [pending]

### 2. Keycloak Realm and Test Users
expected: Open Keycloak admin at http://localhost:9090. Log in with admin/admin. Navigate to the `onco-navigator` realm. Three roles visible: ROLE_NURSE_NAVIGATOR, ROLE_CARE_COORDINATOR, ROLE_ADMIN. Test users (nurse_nav, care_coord, admin_user) exist with correct role assignments.
result: [pending]

### 3. Frontend Build
expected: From `frontend/` directory, run `npm run build`. TypeScript compiles and Vite bundles with zero errors (exit 0).
result: [pending]

### 4. Keycloak OIDC Login Flow
expected: Start the frontend dev server (`npm run dev` in frontend/). Open http://localhost:5173. Browser redirects to Keycloak login page. Log in as `nurse_nav` (password from Keycloak realm config). After login, browser redirects back to the dashboard.
result: [pending]

### 5. Dashboard Shell and Role-Based Navigation
expected: After logging in as nurse_nav, you see a responsive dashboard shell with a sidebar. Sidebar shows: Dashboard, Patients, Alerts. It does NOT show: Pathways, Audit Log, Settings (those are admin-only). Dashboard cards show placeholder values ("--" with "Awaiting Phase 3"). Sidebar collapses on mobile viewport.
result: [pending]

### 6. API Security Gate (401 Without Token)
expected: With Docker Compose running and the Spring Boot app started (`./mvnw spring-boot:run`), send an unauthenticated request: `curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/api/patients`. Response is `401`. Then hit the health endpoint: `curl http://localhost:8081/health` — returns `{"status":"UP"}` without auth.
result: [pending]

### 7. PHI Log Redaction
expected: Check `src/main/resources/logback-spring.xml` and `PhiRedactingConverter.java`. The converter strips 8 PHI field patterns (patient_name, first_name, last_name, ssn, date_of_birth, dob, mrn, medical_record). Any log message containing these patterns outputs `PHI_REDACTED` instead of the actual value.
result: [pending]

### 8. Database Schema and PHI Encryption
expected: Connect to PostgreSQL (`psql -h localhost -p 5432 -U onco_app -d onconavigator`). Tables exist: patients, care_events, alerts, audit_log, pathway_templates. PHI columns (first_name_encrypted, last_name_encrypted, date_of_birth_encrypted, mrn_encrypted) are BYTEA type, not VARCHAR — no readable PHI in the database. Envers audit tables exist: patients_aud, care_events_aud, alerts_aud.
result: [pending]

### 9. Audit Log Tamper Resistance
expected: The audit_log table is INSERT-only for the app user. Attempting `UPDATE audit_log SET action='hacked' WHERE id=1` or `DELETE FROM audit_log WHERE id=1` as the onco_app user fails with a permission denied error. V3 migration revokes UPDATE/DELETE/TRUNCATE.
result: [pending]

### 10. Docker Image Build
expected: Run `docker build -t onco-navigator:test .` from project root. Multi-stage build succeeds: JDK builder compiles the JAR, JRE runtime stage produces a slim image. Image uses non-root user (uid 1001). HEALTHCHECK is configured.
result: [pending]

## Summary

total: 10
passed: 0
issues: 0
pending: 10
skipped: 0
blocked: 0

## Gaps

[none yet]
