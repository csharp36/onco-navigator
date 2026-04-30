---
phase: 03-working-application
verified: 2026-04-30T23:00:00Z
status: passed
score: 5/5 must-haves verified
overrides_applied: 0
re_verification: null
gaps: []
deferred: []
human_verification: []
---

# Phase 3: Working Application Verification Report

**Phase Goal:** A nurse navigator and care coordinator can use the system end-to-end — entering patient data, viewing pathway status, and resolving alerts — entirely through the dashboard
**Verified:** 2026-04-30T23:00:00Z
**Status:** PASSED
**Re-verification:** No — initial verification

> Note: Human verification was completed as part of plan 03-06. All 6 end-to-end flows were exercised through the live dashboard. Two bugs were found and fixed during that session (cancer stage constraint fix, assignedNavigatorId type removal). This report performs goal-backward codebase verification against the ROADMAP success criteria.

---

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Care coordinator can add a new patient, assign a cancer pathway, and record care events through the dashboard without touching any API directly | VERIFIED | `PatientWizard.tsx` two-step wizard calls `useCreatePatient()` → `POST /api/patients`. `QuickAddCareEventDialog.tsx` calls `useCreateCareEvent()` → `POST /api/patients/{id}/care-events`. Both wired to real backend service methods. |
| 2 | Nurse navigator sees all open alerts sorted by severity and can click through to view a patient's full pathway status with each step's current state | VERIFIED | `AlertController.listOpenAlerts()` calls `alertService.getOpenAlerts()` which uses `alertRepository.findByStatusOrderedBySeverity(OPEN)` with JPQL CASE ordering (DELAYED_EVENT=1, MISSING_EVENT=2, OUT_OF_ORDER=3). `AlertCard.tsx` links to `/patients/$patientId`. `$patientId.tsx` calls `usePathwayStatus()` → `GET /api/patients/{id}/pathway-status` which cross-references template JSONB against care events via `PathwayStatusService`. |
| 3 | Nurse navigator can mark an alert as resolved, enter a documentation note, and see the alert disappear from the open queue | VERIFIED | `ResolveAlertModal.tsx` with Zod min(10) validation calls `useResolveAlert()` → `POST /api/alerts/{id}/resolve`. `AlertController.resolveAlert()` requires `NURSE_NAVIGATOR` or `ADMIN` role. `onSuccess` calls `onOpenChange(false)` and invalidates `['alerts']`, `['alerts','count']`, `['dashboard','stats']` query keys. |
| 4 | Dashboard displays a persistent count of open alerts visible from every page | VERIFIED | `nav-sidebar.tsx` imports `useAlertCount` (refetchInterval: 30_000, staleTime: 0), renders `<Badge variant="destructive">` when `alertCount > 0`, capped at "99+". Sidebar is rendered in root layout — visible from every page. |
| 5 | Dashboard is usable on a tablet browser without horizontal scrolling or broken layouts | VERIFIED | `$patientId.tsx` uses `grid grid-cols-1 lg:grid-cols-5` with `lg:col-span-3` / `lg:col-span-2`. `routes/patients/index.tsx` uses responsive Table. All pages confirmed working at tablet width in human verification session (03-06-SUMMARY.md). |

**Score:** 5/5 truths verified

---

### Deferred Items

None.

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/resources/db/migration/V8__add_mrn_hmac_token.sql` | HMAC MRN token column and index | VERIFIED | Contains `ALTER TABLE patients ADD COLUMN mrn_hmac_token VARCHAR(64)` and `CREATE INDEX idx_patients_mrn_hmac_token` |
| `src/main/java/com/onconavigator/security/HmacTokenService.java` | HMAC-SHA256 token computation | VERIFIED | `@Service`, `computeMrnToken()` uses `HexFormat.of().formatHex(hash)`, reads from `onconavigator.hmac.key` (distinct from AES key) |
| `src/main/java/com/onconavigator/web/GlobalExceptionHandler.java` | Centralized error handling without PHI | VERIFIED | `@RestControllerAdvice`, handles `ResponseStatusException`, `MethodArgumentNotValidException`, `HttpMessageNotReadableException`, and `Exception`. Generic handler logs `ex.getClass().getSimpleName()` — NOT `ex.getMessage()` |
| `src/main/java/com/onconavigator/web/dto/` (11 DTOs) | All request/response DTOs | VERIFIED | All 11 records exist: `CreatePatientRequest`, `PatientResponse`, `CreateCareEventRequest`, `CareEventResponse`, `UpdateCareEventStatusRequest`, `DeactivatePatientRequest`, `ResolveAlertRequest`, `AlertResponse`, `DashboardStatsResponse`, `PathwayStatusResponse`, `PathwayStepStatus`. Bean validation on request types. |
| `src/main/java/com/onconavigator/service/PatientService.java` | Patient CRUD with HMAC + Temporal | VERIFIED | All 7 public methods present. `createPatient()` calls `hmacTokenService.computeMrnToken()` and `pathwayService.startPathwayMonitoring()`. `addCareEvent()` calls `pathwayService.signalCareEventChanged()`. `deactivatePatient()` calls `pathwayService.deactivatePatient()`. Log statements use UUID only. |
| `src/main/java/com/onconavigator/service/AlertService.java` | Alert queries with severity ordering and resolution | VERIFIED | `getOpenAlerts()` calls `findByStatusOrderedBySeverity(OPEN)`. `toSeverityLabel()` maps DELAYED_EVENT→OVERDUE, MISSING_EVENT→MISSING, OUT_OF_ORDER→OUT OF ORDER. `resolveAlert()` sets RESOLVED status with actor UUID. |
| `src/main/java/com/onconavigator/service/PathwayStatusService.java` | Pathway step status derivation | VERIFIED | `objectMapper.readValue(template.getTemplateData(), new TypeReference<>(){})` deserializes JSONB. Derives "COMPLETED", "OVERDUE", "MISSING", "UPCOMING" with timing info. |
| `src/main/java/com/onconavigator/web/PatientController.java` | REST endpoints for patient CRUD | VERIFIED | `@RestController`, `@RequestMapping("/api/patients")`. `@PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('ADMIN')")` on write endpoints (no ROLE_ prefix). |
| `src/main/java/com/onconavigator/web/CareEventController.java` | REST endpoints for care events | VERIFIED | `@RequestMapping("/api/patients/{patientId}/care-events")`. POST and PATCH require CARE_COORDINATOR/ADMIN. `addCareEvent()` calls `patientService.addCareEvent()`. |
| `src/main/java/com/onconavigator/web/AlertController.java` | REST endpoints for alert management | VERIFIED | `@RequestMapping("/api/alerts")`. `getOpenAlertCount()` returns `Map<String, Long>` with key "count". `resolveAlert()` requires NURSE_NAVIGATOR/ADMIN. |
| `src/main/java/com/onconavigator/web/DashboardController.java` | Dashboard stats endpoint | VERIFIED | `@RequestMapping("/api/dashboard")`. Injects `PatientService` (not `PatientRepository` directly). `getStats()` limits `topUrgent` to 5 via `.stream().limit(5)`. |
| `frontend/src/features/patients/types.ts` | TypeScript types matching backend DTOs | VERIFIED | `PatientResponse` with `summaryStatus`, `CreatePatientRequest`, `PathwayStepStatus` with `hasActiveAlert`. All fields match backend DTOs. |
| `frontend/src/features/alerts/api.ts` | TanStack Query hooks with polling | VERIFIED | `useAlerts()` with `refetchInterval: 30_000`. `useAlertCount()` with `refetchInterval: 30_000, staleTime: 0`. `useResolveAlert()` invalidates `['alerts']`, `['alerts','count']`, `['dashboard','stats']`. |
| `frontend/src/features/patients/PatientWizard.tsx` | Two-step wizard with Zod v4 validation | VERIFIED | Two `useForm` instances each with `zodResolver`. Zod v4 `{ error: '...' }` syntax throughout. Step indicator with `bg-primary` for active step. "Next: Clinical Details" and "Enroll Patient" button text. Post-create `navigate({ to: '/patients/$patientId'... })`. |
| `frontend/src/routes/patients/index.tsx` | Patient list with search, table, status badges | VERIFIED | `usePatients(activeMrn)` hook. `Link to="/patients/$patientId"` for patient names. `StatusBadge` with "On Track"/variant=secondary, "Alert Active"/variant=destructive, "Inactive"/variant=outline. "Add Patient" links to `/patients/new`. "Record Event" per row opens `QuickAddCareEventDialog`. |
| `frontend/src/routes/patients/$patientId.tsx` | Patient detail with split layout, pathway viz, care events | VERIFIED | `usePatient()`, `usePathwayStatus()`, `useCareEvents()` all called. `grid grid-cols-1 lg:grid-cols-5`. `CheckCircle2`, `Clock`, `AlertTriangle`, `Circle` icons for step states. `timingInfo` rendered. Deactivate dialog with `useDeactivatePatient()`. |
| `frontend/src/features/alerts/AlertCard.tsx` | Alert card with severity indicators | VERIFIED | `border-l-4` with `borderLeftColor: var(--severity-*)`. `<Link to="/patients/$patientId"` for patient name. `severityLabel` in Badge. [View] and [Resolve] buttons. |
| `frontend/src/features/alerts/ResolveAlertModal.tsx` | Resolution modal with required notes | VERIFIED | `useResolveAlert()` mutation. Zod `.min(10, { error: '...' })` on notes. `onOpenChange(false)` called in `onSuccess`. `onInteractOutside={(e) => e.preventDefault()}`. "Keep Alert Open" button. |
| `frontend/src/routes/alerts/index.tsx` | Alert queue page with filter bar and severity groups | VERIFIED | `useAlerts()`. Filter bar: severity Select, patient name Input, From/To date Inputs. Groups by severity: "Overdue", "Missing", "Out of Order" sections. Empty state: "No open alerts." `AlertCard` + `ResolveAlertModal` used. |
| `frontend/src/routes/index.tsx` | Dashboard with stat cards and urgent alerts | VERIFIED | `useDashboardStats()`. Three Card components for Open Alerts (red when >0), Active Patients, On-Track Patients. "Urgent Alerts" heading. Top 5 AlertCards. "View All Alerts" link. `ResolveAlertModal`. |
| `frontend/src/components/layout/nav-sidebar.tsx` | Alert count badge on Alerts nav item | VERIFIED | `useAlertCount` imported from `@/features/alerts/api`. `alertCount = alertCountData?.count ?? 0`. `Badge variant="destructive"` with `ml-auto`, `tabular-nums`. Caps at "99+". Only renders when `alertCount > 0`. |
| `frontend/src/app.css` | Severity CSS custom properties | VERIFIED | `--severity-overdue`, `--severity-missing`, `--severity-out-of-order`, `--severity-completed` all defined in `:root` with oklch values. |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `HmacTokenService` | `application-local.yml` | `@Value("${onconavigator.hmac.key}")` | WIRED | Constructor reads `onconavigator.hmac.key`, validates 32 bytes |
| `PatientRepository` | `Patient.mrnHmacToken` | `findByMrnHmacToken(String)` | WIRED | Method exists; `PatientService.findByMrn()` calls `hmacTokenService.computeMrnToken(mrn)` then `patientRepository.findByMrnHmacToken(hmacToken)` |
| `PatientController` | `PatientService` | `patientService.createPatient()` | WIRED | Controller injects PatientService via constructor; `createPatient()` delegates fully |
| `PatientService` | `PathwayService` | `pathwayService.startPathwayMonitoring()` | WIRED | Called after `patientRepository.save()` in `createPatient()`; `WorkflowExecutionAlreadyStarted` caught for idempotency |
| `PatientService` | `HmacTokenService` | `hmacTokenService.computeMrnToken()` | WIRED | Called in `createPatient()` before `save()`, and in `findByMrn()` for search |
| `AlertService` | `AlertRepository.findByStatusOrderedBySeverity` | severity-ordered query | WIRED | `getOpenAlerts()` calls `alertRepository.findByStatusOrderedBySeverity(AlertStatus.OPEN)` |
| `ResolveAlertModal.tsx` | `features/alerts/api.ts` | `useResolveAlert` mutation | WIRED | Import confirmed; `resolveAlert.mutate({alertId, notes})` in form submit handler |
| `nav-sidebar.tsx` | `features/alerts/api.ts` | `useAlertCount` polling hook | WIRED | Import confirmed; `alertCount` used in badge render |
| `routes/index.tsx` | `features/dashboard/api.ts` | `useDashboardStats()` hook | WIRED | Import confirmed; `stats.topUrgentAlerts`, `stats.openAlertCount`, etc. rendered |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|--------------|--------|--------------------|--------|
| `routes/alerts/index.tsx` | `alerts` | `GET /api/alerts` → `alertService.getOpenAlerts()` → `alertRepository.findByStatusOrderedBySeverity(OPEN)` | DB query on `alerts` table, maps to `AlertResponse` with decrypted patient PHI | FLOWING |
| `routes/patients/index.tsx` | `patients` | `GET /api/patients` → `patientService.findAll()` → `patientRepository.findAll()` | DB query on `patients` table, maps to `PatientResponse` with computed `summaryStatus` | FLOWING |
| `routes/patients/$patientId.tsx` | `pathwayStatus` | `GET /api/patients/{id}/pathway-status` → `pathwayStatusService.getPathwayStatus()` → template JSONB + care events | DB queries on `pathway_templates` + `care_events` + `alerts`; derives per-step status | FLOWING |
| `routes/index.tsx` | `stats` | `GET /api/dashboard/stats` → `patientService.countActivePatients()` + `alertService.getOpenAlerts()` | DB COUNT queries, not static returns | FLOWING |
| `nav-sidebar.tsx` | `alertCount` | `GET /api/alerts/count` → `alertRepository.countByStatus(OPEN)` | DB COUNT query | FLOWING |

---

### Behavioral Spot-Checks

Behavioral spot-checks skipped: no server running during verification. Human verification in plan 03-06 confirmed all 6 flows against a live stack. Two bugs found and fixed during that session are documented in `03-06-SUMMARY.md`.

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| DATA-01 | 03-01, 03-03, 03-04 | Care coordinator can add patient with all required fields | SATISFIED | `CreatePatientRequest` DTO with `@NotBlank`/`@NotNull` validation; two-step wizard with Zod v4 per-step validation; `PatientService.createPatient()` persists all fields |
| DATA-02 | 03-01, 03-03, 03-04 | Care coordinator can add care event with type, date, status, optional notes | SATISFIED | `CreateCareEventRequest` DTO; `CareEventController.addCareEvent()`; both wizard detail page and patient list "Record Event" entry points functional |
| DATA-03 | 03-01, 03-03, 03-04 | Care coordinator can update care event status | SATISFIED | `UpdateCareEventStatusRequest` DTO; `PATCH /api/patients/{id}/care-events/{ceId}` in `CareEventController`; inline status Select on patient detail page via `useUpdateCareEventStatus` |
| DATA-04 | 03-01, 03-03, 03-04 | Care coordinator can deactivate patient to stop alert generation | SATISFIED | `DeactivatePatientRequest` DTO; `PATCH /api/patients/{id}/deactivate`; `PatientService.deactivatePatient()` calls `pathwayService.deactivatePatient()`; confirmation dialog with required reason Select in `$patientId.tsx` |
| DATA-05 | 03-03 | All data entry actions logged with staff member identity and timestamp | SATISFIED | `AuditLoggingFilter` covers all `/api/**` endpoints; logs actor UUID, role, action, resource type/ID, IP, success; registered after `BearerTokenAuthenticationFilter` so JWT principal is populated |
| ALRT-01 | 03-01, 03-03, 03-05 | Dashboard displays open alerts sorted by severity | SATISFIED | `AlertRepository.findByStatusOrderedBySeverity()` with JPQL CASE ordering; `AlertQueuePage` groups by OVERDUE/MISSING/OUT OF ORDER sections |
| ALRT-02 | 03-01, 03-03, 03-05 | Each alert shows patient name, MRN, alert type, step, description, action, time elapsed | SATISFIED | `AlertResponse` DTO includes all fields; `AlertCard.tsx` renders all required fields; `AlertService.toAlertResponse()` decrypts patient name/MRN from JPA converter, computes `timeElapsed` |
| ALRT-03 | 03-01, 03-03, 03-04 | Nurse can view patient's full pathway status with all steps | SATISFIED | `GET /api/patients/{id}/pathway-status` via `PathwayStatusService.getPathwayStatus()`; `$patientId.tsx` renders vertical step list with icons, timing info, active alert highlight |
| ALRT-04 | 03-01, 03-03, 03-05 | Nurse can mark alert as Resolved with free-text note | SATISFIED | `ResolveAlertRequest` with `@NotBlank`; `POST /api/alerts/{id}/resolve`; `ResolveAlertModal` with Zod min(10) validation; requires `NURSE_NAVIGATOR` or `ADMIN` role |
| ALRT-05 | 03-01, 03-03, 03-05 | Dashboard shows count of open alerts, always visible | SATISFIED | `GET /api/alerts/count` returns `{"count": N}`; `useAlertCount()` polls every 30s; `nav-sidebar.tsx` renders `Badge` with destructive variant on Alerts nav item from every page |
| ALRT-06 | 03-02, 03-04 | Dashboard shows list of all patients with pathway and summary status | SATISFIED | `GET /api/patients` via `patientService.findAll()`; `routes/patients/index.tsx` shows table with Name, MRN, Cancer Type, Stage, Status badge (On Track/Alert Active/Inactive), Enrolled date |

All 11 requirements covered and satisfied.

---

### Anti-Patterns Found

None blocking. Observations:

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `PatientRepository.java` | 47 | `@Deprecated findByMrn(String mrn)` retained with TODO comment | Info | Intentional — documents design decision; method kept for documentation, `findByMrnHmacToken` is the operative method |
| `GlobalExceptionHandler.java` | 75-79 | `HttpMessageNotReadableException` handler uses `ex.getMostSpecificCause().getMessage()` | Info | Jackson parse errors reference field names and type expectations, not PHI values. Acceptable per code comment. |
| `AuditLoggingFilter.java` | 61 | `filterChain.doFilter()` called before extracting audit data | Info | Required design — security context is populated after filter chain execution. Correct pattern for post-execution audit logging. |

---

### Human Verification Required

None required. Human verification was completed as part of plan 03-06 checkpoint (2026-04-30). All 6 end-to-end flows confirmed working through the live dashboard. The `03-06-SUMMARY.md` documents: Flow 1 (patient creation wizard), Flow 2 (care event recording from both entry points), Flow 3 (alert queue, severity grouping, resolve with notes, 403 on coordinator confirmed correct), Flow 4 (dashboard stat cards, sidebar badge with 30s refresh), Flow 5 (patient deactivation with confirmation dialog), Flow 6 (tablet responsive layout).

---

## Gaps Summary

No gaps. All 5 ROADMAP success criteria are verified against the codebase. All 11 phase requirements (DATA-01 through DATA-05, ALRT-01 through ALRT-06) have confirmed, substantive, wired implementations with real data flowing from PostgreSQL through the Spring services to the React frontend. Human verification was completed prior to this automated verification pass, and two bugs found during that session were fixed. The phase goal is achieved.

---

_Verified: 2026-04-30T23:00:00Z_
_Verifier: Claude (gsd-verifier)_
