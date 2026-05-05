# Roadmap: Onco-Navigator AI

## Overview

Nine phases deliver a HIPAA-compliant oncology care pathway monitoring system. Phases 1-4 established the foundation: security infrastructure, Temporal workflow engine, working dashboard, and AI document ingestion. Phases 5-9 evolve the pathway architecture from static per-cancer-type templates to per-patient DAG pathways driven by AI extraction from clinical documents — a paradigm shift based on oncologist clinical review (2026-05-04) confirming that "there is no standard sequence for a type of cancer; each patient will need a unique sequence of events."

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [ ] **Phase 1: HIPAA Foundation** - Secure infrastructure, data model, encryption, and RBAC that every subsequent phase depends on
- [x] **Phase 2: Pathway Engine** - Temporal.io durable workflows, deviation detection, and all three cancer pathway templates (completed 2026-04-30)
- [x] **Phase 3: Working Application** - Patient data entry, alert management, and the nurse navigator dashboard (completed 2026-04-30)
- [x] **Phase 4: AI Document Ingestion & Alert Enhancement** - PDF drag-and-drop classification, patient matching, event pre-fill, Claude alert generation, circuit breaker (completed 2026-05-01)
- [x] **Phase 5: Per-Patient Pathway Instances + DAG Foundation** - Mutable per-patient pathways forked from templates, DAG evaluation engine replacing linear iteration (completed 2026-05-04)
- [x] **Phase 6: AI Step Extraction from Clinical Documents** - Claude extracts pathway steps from MD notes, orders, and nurse notes; proposed steps require nurse confirmation (completed 2026-05-05)
- [x] **Phase 7: Referral Trigger + Enhanced Timing** - Referral PDF as pathway clock trigger, event status awareness (Scheduled/Pending/Cancelled), results-before-visit and 48-hour escalation alerts (completed 2026-05-05)
- [ ] **Phase 8: Template Inheritance** - Extensible pathway templates with parent/child inheritance (e.g., rectal inherits from colorectal)
- [ ] **Phase 9: Alert Format + Notification Foundation** - Two-part alerts (what's missing + action ≤150 chars), Teams/email notification infrastructure

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
- [x] 03-04-PLAN.md — Patient pages: two-step wizard, patient list with search, patient detail with pathway visualization
- [x] 03-05-PLAN.md — Alert and dashboard pages: alert queue with severity grouping, resolve modal, dashboard stats, nav sidebar badge
**Wave 4** *(blocked on Wave 3)*
- [x] 03-06-PLAN.md — Human verification checkpoint: end-to-end flow testing through dashboard
**UI hint**: yes

Cross-cutting constraints:
- No PHI in log statements — controllers and services log UUID only
- @PreAuthorize uses hasRole('NURSE_NAVIGATOR') without ROLE_ prefix
- Zod v4 API: use { error: '...' } not { message: '...' } for validation messages
- Severity display: DELAYED_EVENT -> "OVERDUE", MISSING_EVENT -> "MISSING", OUT_OF_ORDER -> "OUT OF ORDER"

### Phase 4: AI Document Ingestion & Alert Enhancement
**Goal**: Clinical documents (PDFs) can be dragged into the dashboard, classified by Claude AI, matched to patients, and used to pre-fill care event recording — reducing manual data entry. Additionally, non-standard deviation alerts get Claude-generated descriptions with circuit breaker fallback.
**Depends on**: Phase 3
**Requirements**: AI-01, AI-02, AI-03, AI-04, DOC-01, DOC-02, DOC-03, DOC-04, DOC-05
**Success Criteria** (what must be TRUE):
  1. A test corpus of de-identified/synthetic clinical PDFs exists in the repository covering pathology reports, radiology reports, referral letters, operative notes, and lab results for breast, lung, and colorectal cancer
  2. A user can drag a PDF onto the dashboard and the system classifies it into a document type (pathology report, radiology report, referral letter, operative note, lab result) using Claude AI
  3. After classification, the system attempts to match the document to an existing patient (by extracting MRN or patient name from the document) or offers to create a new patient
  4. On successful patient match, a pre-filled care event recording wizard opens with event type, date, and extracted details — the user confirms or corrects before saving
  5. The source PDF is stored as a blob in PostgreSQL and linked to the resulting care event record
  6. For low-quality faxed PDFs where extraction fails, the wizard opens with blank fields for manual entry while still attaching the PDF
  7. Non-standard deviation alerts show Claude-generated plain-language descriptions (zero PHI in prompt) with circuit breaker fallback to template text
  8. When Claude API is unavailable, both document classification and alert generation fall back gracefully (manual classification dropdown, template alert text)
**Plans:** 7 plans
Plans:
**Wave 1** *(no dependencies -- parallel)*
- [x] 04-01-PLAN.md — Maven dependencies (Spring AI, Resilience4j, PDFBox, Tess4J), Flyway V9 clinical_documents table, ClinicalDocument entity, AI config/types/prompts, Dockerfile Tesseract
- [x] 04-02-PLAN.md — Synthetic clinical document test corpus (16 documents, 5 types, 3 cancer types) with reference dataset JSON
**Wave 2** *(blocked on Wave 1 Plan 01)*
- [x] 04-03-PLAN.md — Backend extraction pipeline (PDFBox, Tess4J OCR, Claude vision), classification service, alert generation service, patient matching service, document processing orchestrator
- [x] 04-04-PLAN.md — Document upload controller, content streaming endpoint, CareEvent-to-document linkage, PathwayEvaluationActivityImpl Claude alert text integration
**Wave 3** *(blocked on Wave 1 Plan 01 + Wave 2)*
- [x] 04-05-PLAN.md — Frontend document infrastructure: api-client multipart, TypeScript types, TanStack Query hooks, DocumentDropZone, DocumentProcessingModal, PatientMatchSelector
- [x] 04-06-PLAN.md — Frontend integration: PrefilledCareEventDialog, DocumentPreviewPanel, dashboard/patient detail page drop zone integration
**Wave 4** *(blocked on Wave 3)*
- [x] 04-07-PLAN.md — Unit tests: classification circuit breaker, alert generation zero-PHI verification, PDF extraction, patient matching, pathway evaluation Claude branching
**UI hint**: yes

Cross-cutting constraints:
- Two PHI boundaries: document classification (full PHI, BAA-covered) vs alert generation (zero-PHI, no BAA needed)
- All ePHI entities use `@Audited` (Hibernate Envers) -- ClinicalDocument entity included
- ClinicalDocument.extractedText encrypted via EncryptionConverter; content bytea relies on storage-level encryption
- Resilience4j @CircuitBreaker on all Claude API calls with graceful fallback
- Zod v4 API: use { error: '...' } syntax for new frontend schemas
- PDFBox 3.x API: use Loader.loadPDF() not PDDocument.load()
- Tesseract instances created per-call (not Spring beans) for virtual thread safety

### Phase 5: Per-Patient Pathway Instances + DAG Foundation
**Goal**: Each patient gets their own mutable pathway that starts from a template (or empty) and can diverge. The evaluation engine traverses a directed acyclic graph instead of a linear list.
**Depends on**: Phase 4
**Requirements**: PW-ALL-002 (AI extraction model), PW-BR-001 (per-patient steps), PW-BR-003 (no fixed linear sequence)
**Success Criteria** (what must be TRUE):
  1. A new patient can be created with either "Start from template" (forks template into per-patient steps) or "Build from documents" (empty pathway)
  2. Per-patient pathway steps are stored relationally (not JSONB) with individual audit trails via Hibernate Envers
  3. DAG edges (prerequisites) between steps are stored in a separate edges table and support parallel paths
  4. The evaluation engine performs topological sort and evaluates all "ready" steps (prerequisites satisfied) rather than iterating linearly
  5. Existing patients are migrated via Flyway data migration to per-patient rows (D-08 clean cutover, no legacy JSONB fallback)
  6. The frontend renders pathway steps in a tiered-by-depth layout showing parallel steps at the same level

**Schema**: 3 new tables (patient_pathways, patient_pathway_steps, patient_pathway_edges) — all additive, no changes to existing tables
**Plans:** 6 plans
Plans:
**Wave 1** *(no dependencies -- parallel)*
- [x] 05-01-PLAN.md — Flyway migrations (enum, 3 tables, data migration JSONB->relational), JPA entities, repositories
- [x] 05-02-PLAN.md — Temporal pathwayStepsChanged signal (workflow interface + impl + PathwayService method)
**Wave 2** *(blocked on Wave 1)*
- [x] 05-03-PLAN.md — PathwayForkService (template deep copy), PatientPathwayService (step/edge CRUD + cycle detection), PatientService modification
- [x] 05-04-PLAN.md — DAG evaluation engine rewrite (PathwayEvaluationActivityImpl), PathwayStatusService rewrite, DTO updates
**Wave 3** *(blocked on Wave 2 Plan 03)*
- [x] 05-05-PLAN.md — PatientPathwayController (9 REST endpoints), backend DTOs, frontend types + API hooks, TemplatePicker, wizard modification
**Wave 4** *(blocked on Wave 2 + Wave 3)*
- [x] 05-06-PLAN.md — Frontend DAG visualization (PathwayDAGView, StepRow), inline editor (PathwayEditor, AddStepForm, EdgeEditor, SkipStepDialog), patient detail page integration
**UI hint**: yes

Cross-cutting constraints:
- All new entities use `@Audited` (Hibernate Envers)
- No PHI in Temporal workflow payloads — UUID-only approach maintained
- Cycle detection runs at step modification time, not every evaluation cycle
- PROPOSED steps (from future AI extraction) are skipped during evaluation until confirmed
- SKIPPED replaces physician_overrides (D-04) — existing overrides migrated during data migration
- All clinical roles can edit per-patient pathways (D-03) — no role restriction on step/edge CRUD

### Phase 6: AI Step Extraction from Clinical Documents
**Goal**: When a clinical document is uploaded for a patient, Claude AI extracts ordered/planned care events and proposes them as new pathway steps. A nurse must confirm before steps become active.
**Depends on**: Phase 5
**Requirements**: PW-ALL-002 (events extracted from documents), PW-BR-001 (steps from MD notes/orders/nurse notes)
**Success Criteria** (what must be TRUE):
  1. After document classification and patient matching, the system calls Claude to extract pathway-relevant events from the document text
  2. Extracted steps appear in the patient's pathway as PROPOSED with source=AI_EXTRACTED and a link to the source document
  3. A nurse can confirm or reject each proposed step from the patient detail page
  4. Confirmed steps become active in the DAG evaluation; rejected steps are excluded
  5. The system never auto-confirms AI-extracted steps — human-in-the-loop is non-negotiable
  6. A new `pathwayStepsChanged` Temporal signal triggers re-evaluation when steps are confirmed

**Plans:** 5 plans
Plans:
**Wave 1** *(no dependencies)*
- [x] 06-01-PLAN.md — Flyway V16 migration (REJECTED enum, source columns), PathwayStepStatus REJECTED, PatientPathwayStep entity fields, PathwayStepResponse DTO, ExtractionResult model, ExtractionPrompts, stepExtractionClient bean, feature flag
**Wave 2** *(blocked on Wave 1)*
- [x] 06-02-PLAN.md — StepExtractionService (Claude call, circuit breaker, feature flag, enum validation), StepExtractionTriggerService (async orchestrator), PatientPathwayService new methods (buildExistingStepsContext, createProposedSteps), DocumentProcessingService hook
- [x] 06-03-PLAN.md — confirmProposedStep and rejectProposedStep service methods (status transitions, edge activation, cycle detection), confirm/reject REST endpoints on PatientPathwayController
**Wave 3** *(blocked on Wave 2)*
- [x] 06-04-PLAN.md — Frontend: TypeScript types (REJECTED, source fields), confirm/reject API hooks, StepRow confirm/reject/REJECTED rendering, PathwayEditor integration (reject dialog, show rejected toggle)
- [x] 06-05-PLAN.md — Unit tests: StepExtractionService (feature flag, blank text, enum validation, fallback), PatientPathwayService confirm/reject (status guards, dedup, edge activation)
**UI hint**: yes

Cross-cutting constraints:
- Step extraction sends full document text to Claude (PHI — same BAA scope as document classification)
- Patient pathway step names/statuses sent as context are non-PHI
- Resilience4j @CircuitBreaker on extraction calls with fallback to manual step entry
- Feature flag gates extraction (same pattern as document classification)
- REJECTED status blocks re-proposal of previously rejected steps (D-09)
- Confirm/reject restricted to NURSE_NAVIGATOR and ADMIN (not CARE_COORDINATOR)
- Proposed edges stored as JSONB on step row, activated with cycle detection on confirm (D-12)

### Phase 7: Referral Trigger + Enhanced Timing + Status-Aware Evaluation
**Goal**: The pathway clock starts from referral PDF receipt. The evaluation engine understands event statuses (Scheduled, Pending, Cancelled) and generates new alert types for results-before-visit, scheduling confirmation, and deadline escalation.
**Depends on**: Phase 6
**Requirements**: PW-ALL-001 (results-before-visit, scheduling confirmations, referral tracking, escalation), PW-ALL-003 (event status tracking), PW-CR-001 (clock from referral)
**Success Criteria** (what must be TRUE):
  1. A patient record tracks `referral_received_at` timestamp, set when a referral document is uploaded
  2. Pathway steps can use `REFERRAL_DATE` as an anchor type for time window calculation
  3. Care events track `expected_completion_date`, `scheduling_confirmed`, and `external_facility_name`
  4. The system generates a RESULTS_NOT_READY alert when results won't be available before a scheduled visit
  5. The system generates a SCHEDULING_UNCONFIRMED alert when an outside facility hasn't confirmed within 7 days
  6. The system generates a DEADLINE_APPROACHING alert 48 hours before a deadline
  7. A CANCELLED event triggers an immediate corrective action alert
  8. A SCHEDULED/PENDING event with expected_completion_date in the past triggers a DELAYED alert
**Plans:** 4 plans
Plans:
**Wave 1** *(no dependencies -- parallel)*
- [x] 07-01-PLAN.md — Flyway migrations (V17 enum, V18 columns), entities, DTOs, alert severity, ClassificationPrompts
- [x] 07-02-PLAN.md — Frontend TypeScript types, care event form scheduling fields, alert display for new types
**Wave 2** *(blocked on Wave 1 Plan 01)*
- [x] 07-03-PLAN.md — PathwayEvaluationActivityImpl status-aware rewrite, DocumentProcessingService referral hook
**Wave 3** *(blocked on Wave 2)*
- [x] 07-04-PLAN.md — Unit tests for status-aware evaluation (10 test methods, all alert types)

Cross-cutting constraints:
- No PHI in log statements — UUID-only logging preserved
- @Audited (Hibernate Envers) on Patient and CareEvent — new fields auto-captured
- Alert dedup via existing partial unique index (V7) + existsByPatientIdAndPathwayStepNameAndStatus
- RESULTS_NOT_READY uses sentinel step name "__RESULTS_NOT_READY__" for patient-level alert dedup
- CANCELLED/DELAYED mutual exclusion enforced in evaluation branching (Pitfall 7)

### Phase 8: Template Inheritance
**Goal**: Pathway templates become extensible with parent/child relationships. A child template inherits all parent steps and can override, add, or remove specific steps.
**Depends on**: Phase 5
**Requirements**: PW-CR-004 (separate colon vs rectal pathways)
**Success Criteria** (what must be TRUE):
  1. A pathway template can declare a `parent_template_id` to inherit from another template
  2. Child templates contain only overridden/added steps; parent steps provide the baseline
  3. At instantiation time, parent and child steps are merged correctly (child overrides by stepId match)
  4. Multiple templates can exist per cancer type (the active root template is used by default)
  5. A "Rectal Cancer" child template exists inheriting from "Colorectal Cancer" with neoadjuvant-specific modifications
  6. The patient creation wizard shows available templates including child templates for the selected cancer type
**Plans:** 3 plans
Plans:
**Wave 1** *(no dependencies)*
- [ ] 08-01-PLAN.md — Flyway V19/V20 migrations (template inheritance schema, rectal seed), PathwayTemplate entity, TemplateDiff DTOs, repository changes, TemplateMergeService with unit tests
**Wave 2** *(blocked on Wave 1)*
- [ ] 08-02-PLAN.md — PathwayForkService merge integration, PathwayTemplateController REST endpoint, CreatePatientRequest templateId, PatientService changes, fork service tests
**Wave 3** *(blocked on Wave 2)*
- [ ] 08-03-PLAN.md — Frontend TemplatePicker rewrite with variant selection, PatientWizard templateId integration, usePathwayTemplates hook, PathwayTemplateResponse type

Cross-cutting constraints:
- No CancerType enum changes (D-01) — colon/rectal distinction at template level only
- Single-level inheritance only (D-03) — no grandparent traversal
- Diff-based storage (D-05) — child template_data is TemplateDiff JSON, not step array
- Live inheritance at fork time (D-06) — non-overridden parent steps reflect latest parent version
- @Audited on PathwayTemplate — Envers _AUD table must be updated in migration
- Templates are non-PHI — no encryption needed, all authenticated roles can read

### Phase 9: Alert Format + Notification Foundation
**Goal**: Alerts use the oncologist-specified two-part format (what's missing + suggested action ≤150 chars). Infrastructure for Teams/email notifications is established.
**Depends on**: Phase 5
**Requirements**: PW-ALL-007 (two-part alerts ≤150 chars), PW-ALL-004 (end state: Teams/email, dashboard for admin only)
**Success Criteria** (what must be TRUE):
  1. Each alert has a separate `missing_summary` field describing what is missing
  2. The `suggested_action` field is constrained to 150 characters at the service level
  3. A `notification_preferences` table stores per-user notification channel preferences
  4. A `NotificationService` interface exists with channel-specific implementations
  5. Initial implementation is log-only; Teams/email connectors are deferred to a future milestone

## Progress

**Execution Order:**
Phases 1-4 execute sequentially. Phase 5 follows Phase 4. After Phase 5, phases 6/8/9 can run in parallel. Phase 7 depends on Phase 6.

```
Phase 1 -> 2 -> 3 -> 4 -> 5 --+-- 6 -> 7
                            +-- 8
                            +-- 9
```

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. HIPAA Foundation | 4/5 (01-04 awaiting checkpoint) | In Progress | - |
| 2. Pathway Engine | 4/4 | Complete | 2026-04-30 |
| 3. Working Application | 6/6 | Complete | 2026-04-30 |
| 4. AI Document Ingestion & Alert Enhancement | 7/7 | Complete | 2026-05-01 |
| 5. Per-Patient Pathway + DAG Foundation | 6/6 | Complete | 2026-05-04 |
| 6. AI Step Extraction | 5/5 | Complete | 2026-05-05 |
| 7. Referral Trigger + Enhanced Timing | 4/4 | Complete | 2026-05-05 |
| 8. Template Inheritance | 0/3 | Planned | - |
| 9. Alert Format + Notifications | 0/0 | Not Started | - |
