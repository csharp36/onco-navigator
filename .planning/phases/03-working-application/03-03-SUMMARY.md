---
phase: 03-working-application
plan: 03
subsystem: api
tags: [java, spring-boot, rest-api, temporal, rbac, hipaa, service-layer, controllers]

# Dependency graph
requires:
  - phase: 03-working-application
    plan: 01
    provides: "HmacTokenService, 10 DTOs, GlobalExceptionHandler, AlertRepository severity query, PatientRepository HMAC lookup"
  - phase: 02-infrastructure
    provides: "Patient entity with AES-GCM encryption, PathwayService with Temporal client, CareEvent/Alert/PathwayTemplate entities and repositories"

provides:
  - "PatientService: patient CRUD with HMAC token computation and Temporal workflow lifecycle (start/signal/deactivate)"
  - "AlertService: severity-ordered alert queries (OVERDUE/MISSING/OUT OF ORDER) and alert resolution with actor UUID"
  - "PathwayStatusService: per-step COMPLETED/OVERDUE/MISSING/UPCOMING status derived from template JSONB and care events"
  - "PatientController: POST/GET/PATCH /api/patients with RBAC and pathway-status sub-endpoint"
  - "CareEventController: POST/PATCH/GET /api/patients/{patientId}/care-events"
  - "AlertController: GET/POST /api/alerts with severity ordering and badge count endpoint"
  - "DashboardController: GET /api/dashboard/stats with aggregated counts and top 5 urgent alerts"

affects:
  - "03-04 — frontend dashboard connects to all 7 new endpoint paths"
  - "03-05 — AI alert generation uses PatientService/AlertService for context retrieval"

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Service layer separates business logic from controllers — controllers never inject repositories directly"
    - "RBAC via hasRole('NURSE_NAVIGATOR') without ROLE_ prefix — Spring Security prepends ROLE_ automatically (T-03-07)"
    - "PatientService.updateCareEventStatus verifies care event belongs to patientId path variable (T-03-10 BOLA mitigation)"
    - "WorkflowExecutionAlreadyStarted caught and logged as warning in createPatient — idempotent Temporal start"
    - "PHI-safe logging: all service log statements reference UUIDs only, no patient name/DOB/MRN in any log.info call"
    - "computeSummaryStatus checks alertRepository.findByPatientIdAndStatus to derive On Track / Alert Active / Inactive"
    - "PathwayStatusService uses same JSONB deserialization pattern as PathwayEvaluationActivityImpl (TypeReference<List<PathwayStep>>)"

key-files:
  created:
    - src/main/java/com/onconavigator/service/PatientService.java
    - src/main/java/com/onconavigator/service/AlertService.java
    - src/main/java/com/onconavigator/service/PathwayStatusService.java
    - src/main/java/com/onconavigator/web/PatientController.java
    - src/main/java/com/onconavigator/web/CareEventController.java
    - src/main/java/com/onconavigator/web/AlertController.java
    - src/main/java/com/onconavigator/web/DashboardController.java
  modified: []

key-decisions:
  - "DashboardController injects PatientService (not PatientRepository) — service layer encapsulation for countActivePatients"
  - "PathwayStatusService.resolveAnchorDate falls back to diagnosisDate when anchorType=PREVIOUS_STEP and no previous step found (first step in pathway)"
  - "AlertService.computeTimeElapsed produces human-readable elapsed time (minutes → hours → days → weeks → months) for nurse dashboard readability"
  - "PatientService.findByMrn returns List<PatientResponse> wrapping 0 or 1 results for API consistency with listPatients"
  - "WorkflowExecutionAlreadyStarted caught in createPatient and logged as warning (not error) — idempotent re-enrollment per D-08"

# Metrics
duration: 30min
completed: 2026-04-30
---

# Phase 3 Plan 03: Backend Services and Controllers Summary

**Complete REST API surface — 3 service classes and 4 controllers wiring the Phase 2 Temporal pathway engine to the frontend dashboard with RBAC, PHI-safe logging, and Temporal integration**

## Performance

- **Duration:** ~30 min
- **Started:** 2026-04-30T20:30:00Z
- **Completed:** 2026-04-30T21:00:00Z
- **Tasks:** 2
- **Files created:** 7

## Accomplishments

### Task 1: Service Layer

**PatientService** — full patient CRUD with Temporal integration:
- `createPatient`: sets all fields, computes HMAC token via `hmacTokenService.computeMrnToken(req.mrn())`, saves patient, starts Temporal workflow via `pathwayService.startPathwayMonitoring`, catches `WorkflowExecutionAlreadyStarted` for idempotency
- `addCareEvent` / `updateCareEventStatus`: create/update care events and call `pathwayService.signalCareEventChanged` after save
- `deactivatePatient`: marks INACTIVE, calls `pathwayService.deactivatePatient`
- `findByMrn`: computes HMAC token then calls `patientRepository.findByMrnHmacToken`
- `countActivePatients`: delegates to `patientRepository.countByStatus(ACTIVE)` — used by DashboardController
- `computeSummaryStatus`: checks open alerts via `alertRepository.findByPatientIdAndStatus` to derive "On Track" / "Alert Active" / "Inactive"
- All log statements reference UUIDs only — no PHI in any log.info call

**AlertService** — severity-ordered alert management:
- `getOpenAlerts`: calls `alertRepository.findByStatusOrderedBySeverity(OPEN)` for clinical urgency ordering
- `resolveAlert`: sets RESOLVED, records timestamp, actor UUID, and resolution notes
- `toSeverityLabel`: DELAYED_EVENT → "OVERDUE", MISSING_EVENT → "MISSING", OUT_OF_ORDER → "OUT OF ORDER"
- `computeTimeElapsed`: human-readable duration (just now / N minutes/hours/days/weeks/months ago)
- Alert resolution logged as `alertId + actorId` — UUID only, PHI-safe

**PathwayStatusService** — pathway step status derivation:
- `getPathwayStatus`: loads patient, template (JSONB deserialized via TypeReference), care events, open alerts; derives one PathwayStepStatus per template step
- `deriveStepStatus`: COMPLETED (matching COMPLETED care event) / OVERDUE (required + window expired) / MISSING (non-required + window expired) / UPCOMING (window not expired)
- `resolveAnchorDate`: handles DIAGNOSIS_DATE, PREVIOUS_STEP, SPECIFIC_STEP anchor types (same logic as PathwayEvaluationActivityImpl)
- `buildCompletedTimingInfo`: "Completed Day X of Y-day window" or "Completed N days late"
- `hasActiveAlertForStep`: matches by pathwayStepName to show alert indicator on each step

### Task 2: Controller Layer

**PatientController** (`/api/patients`):
- POST — `hasRole('CARE_COORDINATOR') or hasRole('ADMIN')` — 201 CREATED
- GET — `hasRole('CARE_COORDINATOR') or hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')` — supports `?mrn=` search
- GET /{id} — same as list
- PATCH /{id}/deactivate — CARE_COORDINATOR|ADMIN — 204 NO_CONTENT
- GET /{id}/pathway-status — CARE_COORDINATOR|NURSE_NAVIGATOR|ADMIN

**CareEventController** (`/api/patients/{patientId}/care-events`):
- GET — `isAuthenticated()` — all clinical staff need event history
- POST — CARE_COORDINATOR|ADMIN — 201 CREATED; signals Temporal workflow
- PATCH /{careEventId} — CARE_COORDINATOR|ADMIN; ownership verified in service (T-03-10)

**AlertController** (`/api/alerts`):
- GET — NURSE_NAVIGATOR|ADMIN — severity-ordered open alerts
- GET /count — `isAuthenticated()` — badge polling for all roles, returns `{"count": N}`
- POST /{id}/resolve — NURSE_NAVIGATOR|ADMIN — 204 NO_CONTENT

**DashboardController** (`/api/dashboard`):
- GET /stats — `isAuthenticated()` — returns openAlertCount, activePatients, onTrackPatients, topUrgentAlerts (top 5)
- Injects PatientService (not PatientRepository) — service layer encapsulation

## RBAC Compliance Verification

grep for `hasRole('ROLE_` inside `@PreAuthorize` annotations returns zero matches in all 4 controllers. All role names use the correct form: `hasRole('CARE_COORDINATOR')`, `hasRole('NURSE_NAVIGATOR')`, `hasRole('ADMIN')`. Spring Security prepends `ROLE_` automatically.

## PHI Safety Verification

grep for `log.info|warn|error` with name/mrn/dob patterns returns zero matches in all service files. Log statements reference only:
- Patient UUIDs (`saved.getId()`, `patientId`, `id`)
- Care event UUIDs (`saved.getId()`, `ceId`, `careEventId`)
- Alert UUIDs (`alertId`)
- Actor UUIDs (`actorId`)

## Deviations from Plan

None — plan executed exactly as written. All 7 files created with the specified method signatures, RBAC annotations, and Temporal integration patterns.

## Threat Surface Scan

No new trust boundaries introduced beyond those documented in the plan's threat model. The 4 new REST controllers operate within the existing `/api/**` security chain (JWT-required per SecurityConfig). AuditLoggingFilter covers all new endpoints without additional configuration.

## Self-Check: PASSED

All created files confirmed present:
- `c067493` — PatientService, AlertService, PathwayStatusService
- `e70ed9d` — PatientController, CareEventController, AlertController, DashboardController

`./mvnw compile` exits 0 (BUILD SUCCESS, all classes up to date).

No ROLE_ prefix inside any hasRole() annotation (Javadoc-only occurrences in comments).
No PHI fields in any log statement across all 3 service files.
DashboardController injects PatientService (not PatientRepository).
