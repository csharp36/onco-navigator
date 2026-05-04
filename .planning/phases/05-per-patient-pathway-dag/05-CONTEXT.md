# Phase 5: Per-Patient Pathway Instances + DAG Foundation - Context

**Gathered:** 2026-05-04
**Status:** Ready for planning

<domain>
## Phase Boundary

Each patient gets their own mutable pathway that starts from a template (or empty) and can diverge. The evaluation engine traverses a directed acyclic graph instead of a linear list. Nurses can manually add, remove, edit, and reorder steps and dependency edges on per-patient pathways.

This phase delivers: 3 new relational tables (patient_pathways, patient_pathway_steps, patient_pathway_edges), a template fork mechanism that deep-copies steps + edges into per-patient rows, a DAG evaluation engine using topological sort, a Flyway migration converting all existing patients from JSONB templates to per-patient rows, an inline pathway editor on the patient detail page, an enhanced vertical list visualization showing parallel paths via indentation and branching, and a template picker in the patient creation wizard.

This phase does NOT build AI step extraction from documents (Phase 6), referral triggers or enhanced timing (Phase 7), template inheritance (Phase 8), or alert format changes (Phase 9).

</domain>

<decisions>
## Implementation Decisions

### Step Lifecycle & Manual Editing
- **D-01:** **Full manual editing** — nurses can add, remove, edit step details (name, time window, event type), and reorder dependency edges on per-patient pathways. This fills the gap before Phase 6 AI extraction.
- **D-02:** Per-patient step statuses: **ACTIVE, PROPOSED, COMPLETED, SKIPPED**. The evaluation engine only checks ACTIVE steps. PROPOSED steps (Phase 6 prep) are skipped during evaluation until a nurse confirms them. COMPLETED steps have a matching care event. SKIPPED steps replace the physician override mechanism.
- **D-03:** **All clinical roles** (nurse navigator, care coordinator, admin) can edit per-patient pathway steps. No role restriction on pathway modification.
- **D-04:** **SKIPPED replaces physician_overrides** — setting a step to SKIPPED (with a reason field) replaces the physician override table. Existing physician_overrides records are migrated to SKIPPED status on the corresponding per-patient steps during the Flyway data migration. The physician_overrides table becomes legacy.

### Template Fork Mechanics
- **D-05:** **Deep copy, version-locked** — "Start from template" copies all template steps AND their prerequisite edges into per-patient relational tables. The fork records the source template ID and version. Future template updates do NOT retroactively change existing patient pathways — each patient's pathway is independent after fork.
- **D-06:** **"Build from documents" starts workflow immediately** — the Temporal workflow starts with no steps. The evaluation activity returns early (no steps = no deviations). When steps are added manually or via AI extraction (Phase 6), a `pathwayStepsChanged` Temporal signal triggers re-evaluation. The workflow is alive and ready from patient creation.
- **D-07:** **Template picker in patient creation wizard** — Step 2 of the existing two-step wizard adds a pathway selection: "Start from [cancer type] template" (default, pre-selected) or "Build from documents" (empty pathway). Shows available templates for the selected cancer type.
- **D-08:** **Flyway data migration** converts all existing patients from JSONB template evaluation to per-patient pathway rows at deploy time. Clean cutover — no legacy JSONB fallback needed. The evaluation engine only handles per-patient steps after migration. ROADMAP SC5 (legacy JSONB template fallback) is superseded by this decision.

### DAG Edge Behavior
- **D-09:** **Full edge editing** — nurses can add and remove prerequisite edges between per-patient steps via the pathway editor. Adding an edge means "Step B cannot start until Step A is done." Removing an edge means "These steps can happen in parallel." Cycle detection runs on each edit to prevent invalid DAGs (per ROADMAP cross-cutting constraint).
- **D-10:** **Cascade delete edges on step removal** — removing a step deletes all edges where that step is source or target. Downstream steps that depended on the removed step become "ready" (no blocker). The nurse can re-wire edges if needed.
- **D-11:** **Time windows anchored to prerequisites** — each step's time window is measured from its immediate prerequisite(s). If a step has multiple prerequisites, the clock starts from the LATEST prerequisite completion date. This replaces the AnchorType enum (PREVIOUS_STEP, DIAGNOSIS_DATE, SPECIFIC_STEP) with a single prerequisite-based model. Root steps (no prerequisites) anchor to diagnosis date.
- **D-12:** **Newly added steps default to root** — a step added with no edges is immediately "ready" for evaluation. Its clock starts from when it was added (or diagnosis date). The nurse can optionally add prerequisite edges afterward.

### Pathway Visualization
- **D-13:** **Enhanced vertical list** — keeps the existing vertical stepped list from Phase 3 but adds indentation and branching indicators (├──, └──) for parallel steps at the same depth. Minimal visual change, tablet-friendly, consistent with the existing UI.
- **D-14:** **Inline editing on pathway view** — an "Edit Pathway" toggle on the patient detail page transforms the pathway view into an editor. Each step gets edit/remove icons, an "Add Step" button appears, and clicking between steps allows adding dependency edges. The pathway visualization itself becomes the editor.
- **D-15:** **Icons + color coding for statuses** — COMPLETED = green checkmark. ACTIVE = blue circle (warning icon if overdue/alert). PROPOSED = dashed outline/gray (visually distinct as "pending confirmation"). SKIPPED = strikethrough text with gray icon. Consistent with existing Phase 3 status indicators.
- **D-16:** **Keep split layout** — the pathway editor replaces the pathway view in the left column when "Edit Pathway" is toggled. Care events list stays in the right column for reference while editing. Same Phase 3 D-09 layout, toggling between view and edit mode.

### Claude's Discretion
- Relational schema design for patient_pathways, patient_pathway_steps, patient_pathway_edges tables (column names, indexes, constraints)
- Topological sort implementation (Kahn's algorithm vs DFS-based)
- Flyway migration script structure for JSONB-to-relational data conversion
- How step completion is detected (care event match criteria — by event type, or by explicit linkage)
- `pathwayStepsChanged` signal design in Temporal workflow
- Cycle detection algorithm (DFS cycle detection on edit vs. topological sort failure)
- REST API endpoint design for step/edge CRUD operations
- Frontend component structure for the inline pathway editor
- How "Add Step" form captures step details (inline form, small dialog, etc.)
- How edge creation works visually (click-to-connect, dropdown selector, etc.)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Clinical Context
- `docs/Pathway-Template-Review-Worksheet.md` — Oncologist clinical review (2026-05-04). Key decisions: PW-ALL-002 (each patient needs unique sequence, steps extracted from MD notes/orders/nurse notes), PW-BR-001 (steps not from pre-defined list), PW-BR-003 (no fixed linear sequence), PW-CR-001 (clock from referral receipt), PW-CR-004 (separate colon vs rectal pathways). **This document is the primary motivation for Phase 5's per-patient DAG architecture.**
- `docs/Onco-Navigator AI - V1 Feature Specification v2.md` — Original pathway definitions, alert text, example scenarios. Still relevant for template seed data.

### Requirements
- `.planning/REQUIREMENTS.md` — PATH-01 through PATH-08 (pathway engine requirements, most already complete)

### Prior Phase Context
- `.planning/phases/02-pathway-engine/02-CONTEXT.md` — Phase 2 decisions being superseded: D-01 (linear ordering → DAG), D-04 (JSONB templateData → relational), D-11 (physician override → SKIPPED status). Decisions carrying forward: D-05 (dual monitoring), D-07 (one workflow per patient), D-10 (three cancer pathway templates).
- `.planning/phases/03-working-application/03-CONTEXT.md` — Phase 3 decisions affected: D-01 (patient wizard gets template picker), D-08 (vertical list → enhanced vertical list with branching), D-09 (split layout preserved for editor).
- `.planning/phases/04-ai-document-ingestion/04-CONTEXT.md` — Phase 4 context for document-to-event pipeline. Phase 5 does not change document ingestion, but the care event linkage now connects to per-patient steps.

### Existing Backend Code
- `src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java` — Current linear evaluation engine. Phase 5 replaces with DAG topological sort evaluation. Core file being rewritten.
- `src/main/java/com/onconavigator/workflow/PatientPathwayWorkflowImpl.java` — Temporal workflow. Phase 5 adds `pathwayStepsChanged` signal. Minimal changes.
- `src/main/java/com/onconavigator/domain/PathwayTemplate.java` — JSONB template entity. Stays as template source for forking. Not deleted.
- `src/main/java/com/onconavigator/domain/dto/PathwayStep.java` — Current JSONB step DTO. Per-patient steps will be a new JPA entity, not this record.
- `src/main/java/com/onconavigator/domain/dto/AnchorType.java` — AnchorType enum (PREVIOUS_STEP, DIAGNOSIS_DATE, SPECIFIC_STEP). Superseded by edge-based anchoring (D-11), but may still be useful for template-to-patient fork logic.
- `src/main/java/com/onconavigator/domain/PhysicianOverride.java` — Being replaced by SKIPPED status (D-04). Migration needed.
- `src/main/java/com/onconavigator/service/PathwayStatusService.java` — Builds PathwayStepStatus DTOs for frontend. Must be updated for per-patient steps and DAG depth.
- `src/main/java/com/onconavigator/web/dto/PathwayStepStatus.java` — Frontend DTO. Needs depth/parallel information added.
- `src/main/java/com/onconavigator/service/PatientService.java` — Patient creation triggers workflow. Must be updated for template picker (D-07).
- `src/main/resources/db/migration/V6__seed_pathway_templates.sql` — Template seed data. Templates stay; per-patient rows forked from them.
- `src/main/resources/db/migration/V12__update_pathway_time_windows.sql` — Pending migration with oncologist-validated time window corrections. Must be applied before the data migration that converts patients to per-patient rows.

### Existing Frontend Code
- `frontend/src/routes/patients/$patientId.tsx` — Patient detail page with split layout (D-09). Pathway view in left column gets replaced by enhanced list + inline editor.
- `frontend/src/features/patients/types.ts` — TypeScript types. Need per-patient step and edge types.
- `frontend/src/features/patients/api.ts` — API hooks. Need step/edge CRUD hooks.
- `frontend/src/routes/patients/index.tsx` — Patient creation uses wizard. Template picker added to Step 2.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `PathwayTemplate` entity — stays as template source for forking. JSONB templateData parsed into typed PathwayStep records.
- `PatientPathwayWorkflowImpl` — existing Temporal workflow with signal infrastructure. Adding `pathwayStepsChanged` signal follows the same pattern as `careEventChanged`.
- `PathwayStatusService` — builds step status DTOs. Refactor to query per-patient steps instead of JSONB.
- `AlertRepository.existsByPatientIdAndPathwayStepNameAndStatus()` — alert dedup check. Carries forward unchanged.
- `EncryptionConverter` — JPA converter for PHI fields. Per-patient step names are NOT PHI (clinical process data), so no encryption needed on new tables.
- `Card`, `Button`, `Badge`, `Input` shadcn components — for pathway editor UI.
- Two-step patient wizard — Phase 3 component. Add template picker to Step 2.

### Established Patterns
- Hibernate Envers `@Audited` on all ePHI entities — new per-patient step and edge entities follow this
- Flyway versioned SQL migrations — new tables + data migration
- TanStack Query for server state, optimistic updates for inline editing
- TanStack Router file-based routing
- Tailwind v4 with `@theme` in app.css
- PostgreSQL ENUM types mapped to Java enums
- `@PrePersist` / `@PreUpdate` for timestamp management
- `@Column(updatable = false)` on immutable audit fields

### Integration Points
- Patient creation endpoint → template fork + workflow start with `pathwayStepsChanged` signal if template forked
- Care event add/update → existing `careEventChanged` signal still works, but evaluation engine checks per-patient steps
- Step/edge CRUD → new REST endpoints, each mutation sends `pathwayStepsChanged` signal to Temporal
- `PathwayEvaluationActivityImpl.evaluate()` → rewrite to query per-patient steps, build DAG, topological sort, evaluate ready steps
- Patient detail page left column → enhanced vertical list component + inline editor toggle

</code_context>

<specifics>
## Specific Ideas

- The Flyway data migration should apply V12 time window corrections BEFORE converting patients to per-patient rows, so the corrected windows propagate into per-patient steps
- Root steps (no prerequisites) anchor to diagnosis date, maintaining continuity with the current DIAGNOSIS_DATE anchor type behavior
- The `pathwayStepsChanged` signal is critical for the "Build from documents" path — without it, the workflow would never know steps were added
- PROPOSED status in Phase 5 is prep for Phase 6 (AI extraction). The UI shows them as dashed/gray but they don't affect evaluation. When Phase 6 lands, confirming a PROPOSED step changes it to ACTIVE.
- The oncologist's key insight (PW-ALL-002): "There is no standard sequence for a type of cancer. Each patient will need a unique sequence of events." This is why full manual editing and DAG support are non-negotiable for Phase 5.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 05-per-patient-pathway-dag*
*Context gathered: 2026-05-04*
