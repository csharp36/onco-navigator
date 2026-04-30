# Roadmap: Onco-Navigator AI

## Overview

Four phases deliver a HIPAA-compliant oncology care pathway monitoring system. Phase 1 locks in the security and infrastructure foundation that cannot be retrofitted. Phase 2 builds the durable Temporal workflow engine and deviation detection logic that gives the system its core value. Phase 3 wires everything together into a working application — data entry, alert management, and the nurse dashboard. Phase 4 layers in Claude AI alert generation and validates production deployment.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: HIPAA Foundation** - Secure infrastructure, data model, encryption, and RBAC that every subsequent phase depends on
- [x] **Phase 2: Pathway Engine** - Temporal.io durable workflows, deviation detection, and all three cancer pathway templates (completed 2026-04-30)
- [ ] **Phase 3: Working Application** - Patient data entry, alert management, and the nurse navigator dashboard
- [ ] **Phase 4: AI Enhancement & Production** - Claude alert generation with circuit breaker and AWS deployment hardening

## Phase Details

### Phase 1: HIPAA Foundation
**Goal**: A secure, auditable, role-enforced environment exists and is verifiable before any patient data is written
**Depends on**: Nothing (first phase)
**Requirements**: SEC-01, SEC-02, SEC-03, SEC-04, SEC-05, SEC-06, SEC-07, INFR-01, INFR-02
**Success Criteria** (what must be TRUE):
  1. A user can log in through the dashboard and is denied access without valid credentials
  2. Each of the three roles (care coordinator, nurse navigator, administrator) sees only the actions their role permits
  3. Every login, failed login, and data access attempt appears in an append-only audit log with timestamp and user identity
  4. PHI fields in the database are column-encrypted and no PHI value appears in application log files
  5. The full system stack (Temporal, PostgreSQL, Keycloak, app, frontend) starts with a single `docker compose up` command
**Plans:** 5 plans
Plans:
- [x] 01-01-PLAN.md — Project scaffold, Docker Compose, Spring profiles, Keycloak realm, PHI log redaction
- [x] 01-02-PLAN.md — Database schema (Flyway), JPA entities with Envers audit, AES-GCM encryption, audit permissions
- [x] 01-03-PLAN.md — Spring Security with Keycloak JWT, RBAC, AuditLoggingFilter
- [ ] 01-04-PLAN.md — React frontend scaffold, Keycloak OIDC login, responsive dashboard shell (Tasks 1+2 complete, awaiting checkpoint)
- [x] 01-05-PLAN.md — Integration tests (encryption, audit immutability, schema), Dockerfile

### Phase 2: Pathway Engine
**Goal**: The system can enroll a patient in a cancer pathway and automatically detect missing, delayed, or out-of-order care events using durable Temporal workflows
**Depends on**: Phase 1
**Requirements**: INFR-03, INFR-04, PATH-01, PATH-02, PATH-03, PATH-04, PATH-05, PATH-06, PATH-07, PATH-08
**Success Criteria** (what must be TRUE):
  1. A patient pathway workflow survives a full system restart without losing state or resetting timers
  2. The system raises a missing-event alert when a required pathway step has no completed care event within the configured time window
  3. The system raises a delayed-event alert when elapsed time since the previous step exceeds the pathway's configured threshold
  4. The system raises an out-of-order alert when a care event is recorded before its prerequisite steps are completed
  5. No duplicate alert is created when an existing open alert already covers the same patient and step
**Plans:** 4/4 plans complete
Plans:
**Wave 1** *(no dependencies — parallel)*
- [x] 02-01-PLAN.md — Flyway migrations (physician overrides, pathway template seed data), JPA entities, DTOs, repositories
- [x] 02-02-PLAN.md — Temporal workflow/activity interfaces, workflow implementations (signal+timer), PathwayService
**Wave 2** *(blocked on Wave 1 completion)*
- [x] 02-03-PLAN.md — Activity implementations (deviation detection, alert generation, daily sweep), YAML config
**Wave 3** *(blocked on Wave 2 completion)*
- [x] 02-04-PLAN.md — Workflow unit tests (TestWorkflowExtension), activity unit tests (all deviation types, overrides, dedup)

Cross-cutting constraints:
- No PHI in Temporal workflow inputs/payloads — UUID-only approach (enforced across 02-02, 02-03, 02-04)
- All ePHI entities use `@Audited` (Hibernate Envers) — enforced across 02-01, 02-03

### Phase 3: Working Application
**Goal**: A nurse navigator and care coordinator can use the system end-to-end — entering patient data, viewing pathway status, and resolving alerts — entirely through the dashboard
**Depends on**: Phase 2
**Requirements**: DATA-01, DATA-02, DATA-03, DATA-04, DATA-05, ALRT-01, ALRT-02, ALRT-03, ALRT-04, ALRT-05, ALRT-06
**Success Criteria** (what must be TRUE):
  1. A care coordinator can add a new patient, assign a cancer pathway, and record care events through the dashboard without touching any API directly
  2. A nurse navigator sees all open alerts sorted by severity and can click through to view a patient's full pathway status with each step's current state
  3. A nurse navigator can mark an alert as resolved, enter a documentation note, and see the alert disappear from the open queue
  4. The dashboard displays a persistent count of open alerts visible from every page
  5. The dashboard is usable on a tablet browser without horizontal scrolling or broken layouts
**Plans:** 6 plans
Plans:
**Wave 1** *(no dependencies — parallel)*
- [x] 03-01-PLAN.md — Backend contracts: Flyway V8 (HMAC MRN token), HmacTokenService, all DTOs, GlobalExceptionHandler, repository additions
- [x] 03-02-PLAN.md — Frontend scaffold: shadcn components, TypeScript types, TanStack Query hooks, route scaffolds
**Wave 2** *(blocked on Wave 1 Plan 01)*
- [x] 03-03-PLAN.md — Backend services and controllers: PatientService, AlertService, PathwayStatusService, all 4 REST controllers
**Wave 3** *(blocked on Wave 2 + Wave 1 Plan 02)*
- [ ] 03-04-PLAN.md — Patient pages: two-step wizard, patient list with search, patient detail with pathway visualization
- [ ] 03-05-PLAN.md — Alert and dashboard pages: alert queue with severity grouping, resolve modal, dashboard stats, nav sidebar badge
**Wave 4** *(blocked on Wave 3)*
- [ ] 03-06-PLAN.md — Human verification checkpoint: end-to-end flow testing through dashboard
**UI hint**: yes

Cross-cutting constraints:
- No PHI in log statements — controllers and services log UUID only
- @PreAuthorize uses hasRole('NURSE_NAVIGATOR') without ROLE_ prefix
- Zod v4 API: use { error: '...' } not { message: '...' } for validation messages
- Severity display: DELAYED_EVENT -> "OVERDUE", MISSING_EVENT -> "MISSING", OUT_OF_ORDER -> "OUT OF ORDER"

### Phase 4: AI Enhancement & Production
**Goal**: Alert descriptions for non-standard deviations are generated by Claude AI with a circuit breaker fallback, and the system is validated for AWS production deployment
**Depends on**: Phase 3
**Requirements**: AI-01, AI-02, AI-03, AI-04
**Success Criteria** (what must be TRUE):
  1. A non-standard deviation alert shows a plain-language description and corrective action generated by Claude — with zero PHI in the prompt
  2. When the Claude API is unavailable, the system falls back to template-based alert text without throwing errors or showing blank fields
  3. Known deviation types (missing, delayed, out-of-order) display template-based alert text without invoking the Claude API
**Plans**: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. HIPAA Foundation | 4/5 (01-04 awaiting checkpoint) | In Progress | - |
| 2. Pathway Engine | 4/4 | Complete | 2026-04-30 |
| 3. Working Application | 3/6 | In Progress | - |
| 4. AI Enhancement & Production | 0/? | Not started | - |
