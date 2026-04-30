---
phase: 01-hipaa-foundation
plan: "03"
subsystem: security
tags: [spring-security, keycloak, jwt, rbac, audit, hipaa]

dependency_graph:
  requires:
    - 01-01 (application scaffold, Keycloak realm, Docker Compose)
    - 01-02 (AuditLogEntry entity, AuditLogRepository, database schema)
  provides:
    - SecurityConfig with JWT validation and RBAC
    - KeycloakJwtRoleConverter for realm_access.roles mapping
    - AuditLoggingFilter for per-request HIPAA audit logging
    - AuditService for async immutable audit writes
  affects:
    - 01-04 (frontend will use the CORS config defined here)
    - 01-05 (integration tests will validate the security filter chain)
    - Phase 3 (all REST controllers will use @PreAuthorize with roles defined here)

tech_stack:
  added:
    - Spring Security OAuth2 Resource Server (JWT validation against Keycloak JWKS)
    - Spring @EnableAsync for non-blocking audit writes
    - spring-security-test jwt() post-processor for unit testing without Keycloak
  patterns:
    - OncePerRequestFilter for audit capture after security processing completes
    - REQUIRES_NEW transaction isolation for audit entries independent of request transactions
    - Default-deny authorization (anyRequest().denyAll()) with explicit permit list

key_files:
  created:
    - src/main/java/com/onconavigator/security/SecurityConfig.java
    - src/main/java/com/onconavigator/security/KeycloakJwtRoleConverter.java
    - src/main/java/com/onconavigator/security/AuditLoggingFilter.java
    - src/main/java/com/onconavigator/service/AuditService.java
    - src/main/java/com/onconavigator/config/AsyncConfig.java
    - src/test/java/com/onconavigator/security/SecurityConfigTest.java
    - src/test/java/com/onconavigator/security/AuditLoggingFilterTest.java
  modified: []

decisions:
  - "[01-03]: @Order(HIGHEST_PRECEDENCE + 10) on AuditLoggingFilter — runs early enough to capture failed auth; filterChain.doFilter() called first so security context is populated when audit data is extracted"
  - "[01-03]: REQUIRES_NEW transaction on AuditService.logAccess — audit entry commits independently of business transaction; a rolled-back business operation still generates an audit record"
  - "[01-03]: Nil UUID (00000000-...) for anonymous actors — satisfies NOT NULL constraint on audit_log.actor_id without schema change; clearly distinguishable from real user UUIDs"
  - "[01-03]: Tests use status().isNotFound() for authenticated /api/patients — no controller registered in WebMvcTest context; 404 proves security passed (401 would mean security blocked it)"
  - "[01-03]: X-Forwarded-For header preferred over remoteAddr for IP capture — load balancers replace the real client IP; split on comma to get first hop"

metrics:
  duration_seconds: 231
  completed_date: "2026-04-30"
  tasks_completed: 2
  tasks_total: 2
  files_created: 7
  files_modified: 0
---

# Phase 01 Plan 03: Spring Security + Audit Filter Summary

**One-liner:** Stateless Keycloak JWT auth with realm_access.roles RBAC and async OncePerRequestFilter audit logging satisfying HIPAA access control and repudiation requirements.

## What Was Built

Spring Security is now the gatekeeper for every API request:

1. **SecurityConfig** — stateless OAuth2 resource server validates JWTs against Keycloak's JWKS endpoint. CSRF disabled (Bearer token, not cookie). CORS locked to localhost:5173 for local dev. Default-deny authorization with explicit permit list: `/actuator/health` and `/actuator/info` are public; all `/api/**` requires authentication; `/actuator/auditevents` requires `ROLE_ADMIN`.

2. **KeycloakJwtRoleConverter** — reads `realm_access.roles` from the JWT payload and maps only `ROLE_*` prefixed values to Spring `GrantedAuthority`. Keycloak internal roles (`offline_access`, `uma_authorization`) are silently ignored. The three application roles are `ROLE_NURSE_NAVIGATOR`, `ROLE_CARE_COORDINATOR`, `ROLE_ADMIN`.

3. **AuditLoggingFilter** — `OncePerRequestFilter` that calls `filterChain.doFilter()` first, then writes an audit entry with the actor UUID (from JWT `sub` claim), role, HTTP method+path, resource type, resource ID (UUID from path if present), client IP (X-Forwarded-For aware), and success/failure. Health/info endpoints excluded via `shouldNotFilter`.

4. **AuditService** — `@Async` + `@Transactional(REQUIRES_NEW)` service that writes to `AuditLogEntry` via the insert-only repository. Failures are caught, logged at ERROR level (AUDIT_FAILURE), and never propagated — audit logging must not block patient care workflows. Also provides `logAuthenticationFailure()` for future use by authentication event handlers.

5. **AsyncConfig** — `@EnableAsync` configuration class enabling Spring's async task execution infrastructure.

## Tests

- **SecurityConfigTest**: verifies 401 on unauthenticated `/api/**`, 200 on `/actuator/health` (no auth), and authenticated requests pass through security filter
- **AuditLoggingFilterTest**: verifies `auditService.logAccess()` is called for authenticated and unauthenticated API requests with correct actor/role/success parameters; verifies health endpoint is NOT audited

## Deviations from Plan

### Auto-adjusted: Test assertion for authenticated endpoint

**Found during:** Task 2
**Issue:** The plan's `SecurityConfigTest` expected `status().isOk()` for authenticated `/api/patients`, but no PatientController exists yet — it will be created in Phase 3. An authenticated request with no controller returns 404, not 200.
**Fix:** Changed test to `status().isNotFound()` with a comment explaining why 404 proves security passed. The intent is verified: a 401 would mean the security filter rejected the request, a 404 means it passed through.
**Files modified:** SecurityConfigTest.java, AuditLoggingFilterTest.java

### Auto-adjusted: @MockitoBean instead of @MockBean

**Found during:** Task 2
**Issue:** Spring Boot 3.4+ introduced `@MockitoBean` as the replacement for `@MockBean` (which is deprecated in Boot 3.4). The plan referenced `@MockBean`.
**Fix:** Used `@MockitoBean` (from `org.springframework.test.context.bean.override.mockito`) which is the current annotation for Spring Boot 3.5.

## Threat Surface Coverage

All STRIDE mitigations from the plan's threat model were implemented:

| Threat ID | Mitigation |
|-----------|-----------|
| T-03-01 (Spoofing — JWT) | oauth2ResourceServer validates against Keycloak JWKS; stateless sessions; 15-min token expiry configured in Keycloak realm |
| T-03-02 (Repudiation — API access) | AuditLoggingFilter captures every /api/** request |
| T-03-03 (Repudiation — failed logins) | AuditService.logAuthenticationFailure() + filter captures anonymous 401 responses |
| T-03-04 (Elevation — role claims) | KeycloakJwtRoleConverter only accepts ROLE_* prefixed claims |
| T-03-05 (DoS — audit write volume) | @Async writes; audit failures logged but never propagated |
| T-03-06 (Info Disclosure — CORS) | CORS locked to localhost:5173 in local profile |

## Self-Check: PASSED

Files exist:
- FOUND: src/main/java/com/onconavigator/security/SecurityConfig.java
- FOUND: src/main/java/com/onconavigator/security/KeycloakJwtRoleConverter.java
- FOUND: src/main/java/com/onconavigator/security/AuditLoggingFilter.java
- FOUND: src/main/java/com/onconavigator/service/AuditService.java
- FOUND: src/main/java/com/onconavigator/config/AsyncConfig.java
- FOUND: src/test/java/com/onconavigator/security/SecurityConfigTest.java
- FOUND: src/test/java/com/onconavigator/security/AuditLoggingFilterTest.java

Commits:
- FOUND: 5a7ade8 (feat(01-03): Spring Security config with Keycloak JWT validation and RBAC)
- FOUND: 36c75b0 (feat(01-03): AuditLoggingFilter + AuditService for HIPAA-compliant access logging)
