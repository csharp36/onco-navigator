# Phase 3: Working Application - Context

**Gathered:** 2026-04-30
**Status:** Ready for planning

<domain>
## Phase Boundary

Wire the full end-to-end user experience for Onco-Navigator. Care coordinators enter patient data and record care events through the dashboard. Nurse navigators manage alerts, view patient pathway status, and resolve deviations. All interactions happen through the React dashboard — no direct API usage required.

This phase delivers: REST API controllers for all CRUD operations, patient creation wizard, care event recording (two entry points), alert queue with filtering and resolution workflow, patient detail page with pathway visualization, dashboard landing page with alert-focused stats, HMAC index token for MRN search, and persistent alert count badge.

This phase does NOT integrate Claude AI for alert text (Phase 4), does not add SMS/notifications (V2), and does not implement pathway template admin UI (V2 ADV-01).

</domain>

<decisions>
## Implementation Decisions

### Data Entry Experience
- **D-01:** Patient creation uses a **two-step wizard** — Step 1: demographics (first name, last name, DOB, MRN). Step 2: clinical info (cancer type, cancer stage, diagnosis date, assigned navigator, treating physician). Validates per-step before advancing.
- **D-02:** After saving a new patient, **redirect to the patient detail page**. The care coordinator can immediately start recording care events against the newly enrolled pathway.
- **D-03:** Care event recording has **two entry points** — a quick-add dialog from the patient list (compact form: event type, date, status, notes) AND a full "Add Care Event" button on the patient detail page. Same backend endpoint, two frontend paths.
- **D-04:** Patient search uses **HMAC index token for MRN lookup** — a deterministic HMAC-SHA256 of MRN stored as `mrn_hmac_token` column alongside encrypted MRN. Exact MRN search hits the HMAC column. Name search uses in-memory decryption and client-side filtering (acceptable at pilot scale <500 patients).

### Alert Queue & Resolution
- **D-05:** Alert queue uses **card layout with filter bar**, grouped by severity level (OVERDUE first, MISSING second, OUT_OF_ORDER third). Filter bar supports filtering by alert type, patient, and date range. Cards display: severity badge, patient name (clickable link to patient detail), MRN, pathway step name, deviation description, suggested action, time elapsed, and [View] [Resolve] buttons.
- **D-06:** Alert resolution uses a **modal dialog** — shows alert details (patient, step, severity, description, suggested action) and a required resolution notes textarea. Cancel or Resolve buttons. On resolve, alert disappears from queue (optimistic update via TanStack Query invalidation).
- **D-07:** Patient name on alert cards is a **clickable link** to the patient detail page. Separate [View] button also navigates to patient detail. [Resolve] button opens the resolution modal.

### Pathway Status View
- **D-08:** Patient pathway visualization uses a **vertical stepped list** — steps listed top-to-bottom in pathway order. Each step shows: step number, step name, status icon (completed checkmark, warning for overdue/alert, circle for upcoming), completion date if applicable, timing info (on time, X days overdue, waiting on step N), and time window reference.
- **D-09:** Patient detail page uses a **side-by-side split layout** — pathway visualization on the left, care events list on the right. Stacks vertically on tablet/mobile. Patient demographics in a compact header bar above both columns.

### Dashboard Landing Page
- **D-10:** Dashboard is **alert-focused** — three summary stat cards at top (open alert count, active patients, on-track patients), followed by a list of the top ~5 most urgent alerts. "View All Alerts" link at the bottom navigates to the full alert queue.
- **D-11:** Persistent open alert count displayed as a **sidebar nav badge** on the "Alerts" nav item. Visible from every page. Uses the existing `nav-sidebar.tsx` component.
- **D-12:** Dashboard stats and alert count use **30-second polling** via TanStack Query `refetchInterval: 30_000`. Nurses see new alerts appear without manual refresh.

### Claude's Discretion
- REST API controller structure and endpoint naming conventions
- DTO design for request/response objects (records vs classes)
- Bean validation annotations and error response format
- TanStack Query key structure and cache invalidation strategy
- Whether patient list uses pagination or loads all (pilot scale decision)
- Zod schema design for frontend form validation
- HMAC key management (same AES key or separate HMAC key)
- How pathway auto-enrollment (D-06 from Phase 2) is triggered from the patient creation endpoint

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Clinical Pathway Definitions
- `docs/Onco-Navigator AI - V1 Feature Specification v2.docx` — Contains pathway definitions, alert text templates, and example scenarios. Convert to text with `textutil -convert txt` before reading.

### Requirements
- `.planning/REQUIREMENTS.md` — DATA-01 through DATA-05 (data management), ALRT-01 through ALRT-06 (alert management)

### Prior Phase Context
- `.planning/phases/02-pathway-engine/02-CONTEXT.md` — Decisions D-05 through D-11 that define workflow behavior (auto-enrollment, event signals, deactivation, monitoring cadence). Phase 3 REST controllers must trigger these Temporal operations correctly.

### Existing Backend Code
- `src/main/java/com/onconavigator/domain/Patient.java` — Patient entity with AES-GCM encrypted PHI fields. Phase 3 adds HMAC token column.
- `src/main/java/com/onconavigator/domain/Alert.java` — Alert entity with status lifecycle (OPEN/ACKNOWLEDGED/RESOLVED), resolution fields
- `src/main/java/com/onconavigator/domain/CareEvent.java` — CareEvent entity linked to Patient
- `src/main/java/com/onconavigator/service/PathwayService.java` — Temporal workflow lifecycle: `startPathwayMonitoring()`, `signalCareEventChanged()`, `deactivatePatient()`. Phase 3 controllers call these.
- `src/main/java/com/onconavigator/security/SecurityConfig.java` — RBAC config. `/api/**` requires valid JWT. Method-level `@PreAuthorize` for role enforcement.
- `src/main/java/com/onconavigator/repository/AlertRepository.java` — Has `existsByPatientIdAndPathwayStepNameAndStatus()` for dedup
- `src/main/java/com/onconavigator/repository/PatientRepository.java` — `findByMrn` is a documented stub (needs HMAC token)

### Existing Frontend Code
- `frontend/src/lib/api-client.ts` — Generic API client with JWT auth, supports GET/POST/PUT/PATCH/DELETE
- `frontend/src/components/layout/app-shell.tsx` — App shell with sidebar, mobile responsive
- `frontend/src/components/layout/nav-sidebar.tsx` — Role-based nav items (Dashboard, Patients, Alerts, Pathways, Audit, Settings). Phase 3 adds alert count badge here.
- `frontend/src/lib/auth.ts` — Auth utilities: `getAccessToken()`, `hasRole()`, `getUserName()`, `isAuthenticated()`
- `frontend/src/components/ui/` — shadcn/ui primitives: button, card, dialog, input, badge, alert, skeleton, tooltip, separator, sheet

### Technology References
- `application-local.yml` — Temporal connection config, worker packages, Keycloak issuer URI
- TanStack Query v5 for server state, TanStack Router for type-safe routing, react-hook-form + Zod for forms, shadcn/ui for components

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `apiClient` (api-client.ts) — generic fetch wrapper with JWT injection, error handling via ApiError class
- `Card`, `Dialog`, `Input`, `Button`, `Badge` components — shadcn/ui primitives for alert cards, resolution modal, forms
- `hasRole()` from auth.ts — client-side role checks for conditional rendering
- `NavSidebar` — role-filtered nav items, needs badge addition for alert count
- `PathwayService` — all Temporal operations exposed, controllers just need to call through
- `EncryptionConverter` — existing JPA converter for PHI fields, reusable for HMAC token pattern
- `AuditService.logAccess()` — async audit logging for HIPAA compliance on all data access

### Established Patterns
- AES-GCM encryption via `@Convert(converter = EncryptionConverter.class)` on PHI fields
- Hibernate Envers `@Audited` on all ePHI entities — new entities and modifications follow this
- PostgreSQL ENUM types mapped to Java enums via `@Enumerated(EnumType.STRING)` with `columnDefinition`
- `@PrePersist`/`@PreUpdate` for timestamp management
- `@Column(updatable = false)` for immutable audit fields
- TanStack Router file-based routing in `frontend/src/routes/`
- Tailwind v4 with `@theme` in app.css (no tailwind.config.ts)

### Integration Points
- Patient creation endpoint must call `PathwayService.startPathwayMonitoring()` after persist
- Care event endpoints must call `PathwayService.signalCareEventChanged()` after persist/update
- Patient deactivation endpoint must call `PathwayService.deactivatePatient()` after status change
- SecurityConfig already allows `/api/**` for authenticated users — method-level `@PreAuthorize` for role granularity
- CORS configured for `localhost:5173` (Vite dev server)
- TanStack Router routes need to be added for /patients, /patients/$id, /alerts paths
- NavSidebar alert count badge requires a lightweight API endpoint for open alert count

</code_context>

<specifics>
## Specific Ideas

- Alert cards should show severity with colored indicators (red for overdue, orange/amber for missing, yellow for out-of-order) matching the clinical urgency hierarchy from REQUIREMENTS.md ALRT-01
- Patient detail side-by-side layout should stack vertically on tablet breakpoints (the app must be "usable on a tablet browser without horizontal scrolling" per SEC-07 / success criterion 5)
- The "quick-add care event" dialog from the patient list should pre-populate the event type dropdown with the next expected pathway step to reduce clicks
- Dashboard urgent alerts should show the same card format as the full alert queue for visual consistency
- Resolution notes field in the modal should be required (cannot resolve with empty notes) — this is the nurse's documentation of what action was taken

</specifics>

<deferred>
## Deferred Ideas

- Pathway template admin UI (editing pathways through the dashboard) — V2 requirement ADV-01
- Manual re-scan trigger (F3-07 from feature spec) — could be a "Rescan Now" button on patient detail, but not in current requirements
- Bulk patient import — could be useful during pilot onboarding but not in current scope

None — discussion stayed within phase scope

</deferred>

---

*Phase: 03-working-application*
*Context gathered: 2026-04-30*
