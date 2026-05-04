---
phase: 05-per-patient-pathway-dag
verified: 2026-05-04T21:00:00Z
status: human_needed
score: 6/6 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Visual rendering of depth-indented DAG layout with branching indicators"
    expected: "Steps at different depths show Unicode box-drawing characters (├── for middle, └── for last), 24px indentation per depth level, COMPLETED=green checkmark, ACTIVE=blue circle, ACTIVE+alert=red triangle, PROPOSED=dashed gray circle, SKIPPED=gray MinusCircle"
    why_human: "StepRow.tsx renders icons and indentation based on props; visual correctness requires running the application and viewing a patient with a multi-depth pathway"
  - test: "Template picker renders in patient creation wizard and payload reaches backend"
    expected: "Step 2 of wizard shows 'Pathway Setup' RadioGroup; selecting 'Build from documents' shows helper text; both modes complete enrollment and produce correct pathway (template mode forks steps, empty mode creates 0-step pathway)"
    why_human: "End-to-end flow requires running frontend+backend+DB; TanStack Query hook wiring cannot be verified without a live session"
  - test: "Edit Pathway toggle behavior and inline editor"
    expected: "Button renders 'Edit Pathway' (outline) in view mode and 'Done Editing' (default) in edit mode; Add Step form appears below step list; step CRUD operations reflect immediately in the step list without page reload"
    why_human: "Stateful React toggle and mutation response requires browser testing; PathwayEditor renders dynamically based on isEditingPathway state"
  - test: "Cycle detection error display in EdgeEditor"
    expected: "Adding a circular dependency (A depends on B when B already depends on A) shows the error 'Cannot add this dependency — it would create a circular path' inline in the EdgeEditor, without page crash"
    why_human: "Requires live backend to return 409 Conflict and frontend onError handler to set addEdgeError state; cannot trace through 2-tier mutation without running the stack"
  - test: "Tablet layout (768px width)"
    expected: "Pathway card stacks above care events card; branching indicators still render; Edit Pathway button remains accessible"
    why_human: "CSS responsive behavior requires browser resize; Tailwind grid-cols-5 stack cannot be verified from source alone"
---

# Phase 05: Per-Patient Pathway DAG Verification Report

**Phase Goal:** Replace JSONB template pathways with per-patient relational DAG pathways — each patient gets their own mutable set of steps and edges, evaluated via topological sort instead of linear iteration. Frontend renders depth-based tiered layout with inline editing.
**Verified:** 2026-05-04T21:00:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | A new patient can be created with either "Start from template" (forks template into per-patient steps) or "Build from documents" (empty pathway) | VERIFIED | `CreatePatientRequest.effectivePathwayMode()` present; `PatientService.createPatient` routes through `forkService.forkFromTemplate` or `forkService.createEmptyPathway`; `TemplatePicker.tsx` renders RadioGroup with both options; `PatientWizard.tsx` includes `pathwayMode` in submit payload |
| 2 | Per-patient pathway steps are stored relationally (not JSONB) with individual audit trails via Hibernate Envers | VERIFIED | `PatientPathwayStep.java` has `@Audited` + `@Table(name="patient_pathway_steps")`; V14 migration creates the table; `@Version` provides optimistic locking; `PathwayEvaluationActivityImpl` no longer imports PathwayTemplateRepository or JSONB types |
| 3 | DAG edges (prerequisites) between steps are stored in a separate edges table and support parallel paths | VERIFIED | V14 creates `patient_pathway_edges` with `CONSTRAINT uq_pathway_edge UNIQUE (source_step_id, target_step_id)` and `CONSTRAINT chk_no_self_edge`; `PatientPathwayEdge.java` with `@Audited`; `PatientPathwayEdgeRepository` with `findByPathway_Id` and `deleteBySourceStepIdOrTargetStepId` |
| 4 | The evaluation engine performs topological sort and evaluates all "ready" steps (prerequisites satisfied) rather than iterating linearly | VERIFIED | `PathwayEvaluationActivityImpl.evaluate()` calls `stepRepository.findByPathway_IdAndStatus(pathway.getId(), PathwayStepStatus.ACTIVE)`, builds prerequisite map from edges, filters `readySteps` as ACTIVE steps where all prerequisites are in `satisfiedStepIds`; `PathwayStatusService` runs Kahn's BFS with `depthMap`; no JSONB/template references remain |
| 5 | Existing patients are migrated via Flyway data migration to per-patient rows (D-08 clean cutover, no legacy JSONB fallback) | VERIFIED | V15 migration uses `jsonb_array_elements` for JSONB expansion, physician_overrides converted to SKIPPED (UPDATE with `skip_reason = po.override_reason`), completed care events matched via window functions; handles edge cases (no template, empty template, INACTIVE patients) |
| 6 | The frontend renders pathway steps in a tiered-by-depth layout showing parallel steps at the same level | VERIFIED (automated) / PENDING (visual) | `StepRow.tsx` applies `style={{ paddingLeft: calc(${step.depth * 24}px + 0.75rem) }}` and `aria-level={step.depth + 1}`; Unicode branching indicators rendered with `aria-hidden="true"`; `PathwayDAGView.tsx` renders steps with `aria-live="polite"` region; visual correctness requires human testing |

**Score:** 6/6 truths verified (SC#6 automated portion verified; visual rendering requires human)

### Deferred Items

None identified — all 6 ROADMAP success criteria are addressed by this phase.

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `src/main/resources/db/migration/V13__create_pathway_step_status_enum.sql` | PostgreSQL enum type for step status | VERIFIED | Contains `CREATE TYPE pathway_step_status AS ENUM ('ACTIVE', 'PROPOSED', 'COMPLETED', 'SKIPPED')` |
| `src/main/resources/db/migration/V14__create_per_patient_pathway_tables.sql` | Three new tables with FK constraints and indexes | VERIFIED | Contains `CREATE TABLE patient_pathway_steps`, all constraints (`uq_pathway_edge`, `chk_no_self_edge`, `ON DELETE CASCADE`), 5 indexes, grants to `onco_app` |
| `src/main/resources/db/migration/V15__migrate_patients_to_per_patient_pathways.sql` | Data migration from JSONB templates to per-patient rows | VERIFIED | Contains `jsonb_array_elements`, `jsonb_array_elements_text`, physician override migration to SKIPPED, completed care event matching with ROW_NUMBER window functions |
| `src/main/java/com/onconavigator/domain/PatientPathwayStep.java` | JPA entity for per-patient steps | VERIFIED | `@Audited`, `@Table(name="patient_pathway_steps")`, `@Version`, `columnDefinition="pathway_step_status"`, `FetchType.LAZY`, `@PrePersist`/`@PreUpdate` |
| `src/main/java/com/onconavigator/domain/PatientPathway.java` | JPA entity for pathway header | VERIFIED | `@Audited`, `@Table(name="patient_pathways")`, `@PrePersist`/`@PreUpdate` |
| `src/main/java/com/onconavigator/domain/PatientPathwayEdge.java` | JPA entity for DAG edges | VERIFIED | `@Audited`, UUID columns for step references (not @ManyToOne), write-once pattern |
| `src/main/java/com/onconavigator/domain/enums/PathwayStepStatus.java` | Java enum mirroring PostgreSQL enum | VERIFIED | `ACTIVE, PROPOSED, COMPLETED, SKIPPED` |
| `src/main/java/com/onconavigator/repository/PatientPathwayRepository.java` | Spring Data repository | VERIFIED | `findByPatient_Id(UUID)`, `existsByPatient_Id(UUID)` |
| `src/main/java/com/onconavigator/repository/PatientPathwayStepRepository.java` | Spring Data repository | VERIFIED | `findByPathway_Id`, `findByPathway_IdAndStatus`, `findByPathway_IdAndStatusIn` |
| `src/main/java/com/onconavigator/repository/PatientPathwayEdgeRepository.java` | Spring Data repository | VERIFIED | `findByPathway_Id`, `deleteBySourceStepIdOrTargetStepId` |
| `src/main/java/com/onconavigator/workflow/PatientPathwayWorkflow.java` | Workflow interface with signal | VERIFIED | Contains `@SignalMethod void pathwayStepsChanged()` as third signal method |
| `src/main/java/com/onconavigator/workflow/PatientPathwayWorkflowImpl.java` | Signal handler | VERIFIED | `pathwayStepsChanged()` sets `signalReceived = true`; main loop (`Workflow.await(Duration.ofHours(24), () -> signalReceived || deactivated)`) unchanged |
| `src/main/java/com/onconavigator/service/PathwayService.java` | Signal dispatch method | VERIFIED | `signalPathwayStepsChanged(UUID patientId)` calls `workflow.pathwayStepsChanged()` with UUID-only logging |
| `src/main/java/com/onconavigator/service/PathwayForkService.java` | Template fork logic | VERIFIED | `forkFromTemplate` deep-copies steps with `stepIdMap` UUID remapping; `createEmptyPathway` for empty mode; both `@Transactional` |
| `src/main/java/com/onconavigator/service/PatientPathwayService.java` | Step/edge CRUD with cycle detection | VERIFIED | `wouldCreateCycle` (DFS), `computeTopology` (Kahn's BFS), all 8 mutation methods call `signalPathwayStepsChanged`; `deleteStep` calls `resolveAlertsForStep` |
| `src/main/java/com/onconavigator/service/PatientService.java` | Modified createPatient | VERIFIED | `pathwayForkService.forkFromTemplate` or `createEmptyPathway` call present; `effectivePathwayMode()` conditional branching |
| `src/main/java/com/onconavigator/web/dto/CreatePatientRequest.java` | DTO with pathwayMode | VERIFIED | `String pathwayMode` field + `effectivePathwayMode()` compact method |
| `src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java` | DAG-based evaluation engine | VERIFIED | Imports `PatientPathwayRepository`, `PatientPathwayStepRepository`, `PatientPathwayEdgeRepository`; no JSONB template/PhysicianOverride imports; `findByPathway_IdAndStatus(ACTIVE)` query; ready step filter; anchor date resolution |
| `src/main/java/com/onconavigator/service/PathwayStatusService.java` | Per-patient status with depth | VERIFIED | Imports per-patient repositories; Kahn's BFS with `depthMap`; no `PathwayTemplateRepository` or `ObjectMapper` |
| `src/main/java/com/onconavigator/web/dto/PathwayStepStatus.java` | DTO with depth and DAG fields | VERIFIED | `depth`, `sortOrder`, `skipReason`, `prerequisiteStepIds` present; `stepNumber` absent |
| `src/main/java/com/onconavigator/web/PatientPathwayController.java` | REST controller for step/edge CRUD | VERIFIED | `@RequestMapping("/api/patients/{patientId}/pathway")`; 9 endpoints; `@PreAuthorize` count = 9 |
| `src/main/java/com/onconavigator/web/dto/PathwayStepRequest.java` | Request DTO | VERIFIED | Created in Plan 03 |
| `src/main/java/com/onconavigator/web/dto/PathwayEdgeRequest.java` | Request DTO | VERIFIED | Created in Plan 03 |
| `src/main/java/com/onconavigator/web/dto/PathwayStepResponse.java` | Response DTO with depth | VERIFIED | `depth`, `sortOrder`, `prerequisiteStepIds` fields present |
| `src/main/java/com/onconavigator/web/dto/PathwayEdgeResponse.java` | Response DTO | VERIFIED | Created in Plan 03 |
| `src/main/java/com/onconavigator/web/dto/SkipStepRequest.java` | Skip DTO | VERIFIED | `@NotBlank` on reason field |
| `frontend/src/features/patients/types.ts` | TypeScript types for DAG | VERIFIED | `PatientPathwayStep` with `depth`, `sortOrder`, `prerequisiteStepIds`; `PathwayStepStatus` updated (no `stepNumber`); `CreatePatientRequest` has `pathwayMode` |
| `frontend/src/features/patients/api.ts` | TanStack Query hooks | VERIFIED | 9 hooks: `usePathwaySteps`, `usePathwayEdges`, `useCreateStep`, `useUpdateStep`, `useDeleteStep`, `useSkipStep`, `useUnskipStep`, `useCreateEdge`, `useDeleteEdge` targeting `/pathway/steps` and `/pathway/edges` |
| `frontend/src/features/patients/TemplatePicker.tsx` | Radio group for pathway mode | VERIFIED | `RadioGroup` with two options; renders only when cancer type is selected |
| `frontend/src/features/patients/PatientWizard.tsx` | Wizard with template picker | VERIFIED | `pathwayMode` state, `TemplatePicker` imported and rendered, `pathwayMode` included in submit payload |
| `frontend/src/components/ui/radio-group.tsx` | shadcn RadioGroup | VERIFIED | File exists at correct path |
| `frontend/src/components/ui/collapsible.tsx` | shadcn Collapsible | VERIFIED | File exists at correct path |
| `frontend/src/features/patients/StepRow.tsx` | Shared step row component | VERIFIED | `CheckCircle2`, `AlertTriangle`, `Circle`, `MinusCircle` imports; `paddingLeft: calc(${step.depth * 24}px + 0.75rem)`; branching indicators with `aria-hidden="true"`; edit mode actions per status |
| `frontend/src/features/patients/PathwayDAGView.tsx` | Tiered DAG view | VERIFIED | `isLastAtDepth` computation; `aria-live="polite"` region; 5-skeleton loading; empty state with UI-SPEC copy |
| `frontend/src/features/patients/SkipStepDialog.tsx` | Skip reason dialog | VERIFIED | Focus-trapped Dialog with required reason Input |
| `frontend/src/features/patients/AddStepForm.tsx` | Inline add-step form | VERIFIED | Zod v4 schema; 8 event type options in Select |
| `frontend/src/features/patients/EdgeEditor.tsx` | Dependency management | VERIFIED | `Collapsible` component; "Dependencies" header; cycle detection error text: "Cannot add this dependency — it would create a circular" |
| `frontend/src/features/patients/PathwayEditor.tsx` | Inline editor orchestrator | VERIFIED | Imports `StepRow`, `AddStepForm`, `EdgeEditor`, `SkipStepDialog`; state: `showAddForm`, `editingStepId`, `skipDialogStep`, `removeDialogStep`; remove dialog contains "Downstream steps that depended on this step will become" |
| `frontend/src/routes/patients/$patientId.tsx` | Patient detail page | VERIFIED | Imports `PathwayDAGView` and `PathwayEditor`; `isEditingPathway` state; "Edit Pathway" / "Done Editing" toggle button; conditional render of editor vs. view |
| `frontend/src/app.css` | Dashed icon CSS | VERIFIED | `.icon-dashed { stroke-dasharray: 4 2; }` at line 115 |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `V14__create_per_patient_pathway_tables.sql` | `patients` table | `REFERENCES patients(id)` | VERIFIED | Line 21: `patient_id UUID NOT NULL REFERENCES patients(id)` |
| `PatientPathwayStep.java` | `PatientPathway.java` | `@ManyToOne(fetch = FetchType.LAZY)` | VERIFIED | Line 54 in PatientPathwayStep.java |
| `PathwayService.java` | `PatientPathwayWorkflow.java` | `signalPathwayStepsChanged` → `workflow.pathwayStepsChanged()` | VERIFIED | Lines 119-127 in PathwayService.java |
| `PatientPathwayService.java` | `PathwayService.java` | `signalPathwayStepsChanged` after every mutation | VERIFIED | 7 call sites at lines 144, 194, 226, 258, 289, 352, 382 |
| `PatientService.java` | `PathwayForkService.java` | `forkService.forkFromTemplate` in createPatient | VERIFIED | Line 101 in PatientService.java |
| `PathwayEvaluationActivityImpl.java` | `PatientPathwayStepRepository` | `findByPathway_IdAndStatus(ACTIVE)` | VERIFIED | Lines 126-127 |
| `PathwayStatusService.java` | `PatientPathwayStepRepository` | `findByPathway_Id` for all steps | VERIFIED | Line 95 |
| `PatientPathwayController.java` | `PatientPathwayService.java` | Delegates all operations | VERIFIED | `patientPathwayService` field; all 9 endpoints delegate to service |
| `frontend/src/features/patients/api.ts` | `/api/patients/{patientId}/pathway/steps` | `apiClient.get/post/put/delete/patch` | VERIFIED | Lines 103, 120, 134, 146, 162, 177 |
| `TemplatePicker.tsx` | `PatientWizard.tsx` | `pathwayMode` state | VERIFIED | `pathwayMode` state at line 92; TemplatePicker rendered at line 326 |
| `$patientId.tsx` | `PathwayDAGView.tsx` | Component in view mode | VERIFIED | Import at line 39; used at line 316 |
| `$patientId.tsx` | `PathwayEditor.tsx` | Conditional render when `isEditingPathway=true` | VERIFIED | Import at line 40; used at line 311 |
| `PathwayEditor.tsx` | `api.ts hooks` | `useCreateStep` and other hooks | VERIFIED | `useCreateStep` imported at line 29; used at line 164 |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `PathwayDAGView.tsx` | `steps` prop | `usePathwayStatus` hook in `$patientId.tsx` → `PathwayStatusService.getPathwayStatus()` → `stepRepository.findByPathway_Id()` | Yes — DB query against `patient_pathway_steps` table | FLOWING |
| `PathwayEditor.tsx` | `steps` prop from `$patientId.tsx`; edges from `usePathwayEdges` → `GET /pathway/edges` → `PatientPathwayService.getEdges()` → `edgeRepository.findByPathway_Id()` | Yes — DB query against `patient_pathway_edges` | FLOWING |
| `PathwayEvaluationActivityImpl` | `activeSteps` | `stepRepository.findByPathway_IdAndStatus(pathway.getId(), PathwayStepStatus.ACTIVE)` | Yes — filtered query on `patient_pathway_steps` with status=ACTIVE | FLOWING |

### Behavioral Spot-Checks

Step 7b skipped — no runnable entry points available without starting the full Docker Compose + Spring Boot + React dev server stack. Human verification covers these behaviors.

### Requirements Coverage

The PLAN frontmatter across all 6 plans cites three requirement IDs: `PW-ALL-002`, `PW-BR-001`, `PW-BR-003`. These IDs are not defined in `/Users/csharpl/Desktop/Source_Code/Java/Onco-Navigator/.planning/REQUIREMENTS.md` (which uses a different ID scheme: DATA-*, PATH-*, ALRT-*, DOC-*, AI-*, SEC-*, INFR-*). These PW-* IDs are defined in the phase's RESEARCH.md and CONTEXT.md as domain-level requirements from the oncologist clinical review, not v1 product requirements.

| Requirement | Defined In | Description | Status | Evidence |
|-------------|-----------|-------------|--------|----------|
| PW-ALL-002 | 05-RESEARCH.md | Events extracted from documents — each patient needs unique sequence | SATISFIED | Per-patient relational model implemented; PROPOSED step status prepared for AI extraction; full manual editing supported |
| PW-BR-001 | 05-RESEARCH.md | Steps from MD notes/orders/nurse notes — steps not from pre-defined list | SATISFIED | Full step CRUD (create/update/delete) via `PatientPathwayService`; `AddStepForm` supports freeform step names; empty pathway mode for document-based workflows |
| PW-BR-003 | 05-RESEARCH.md | No fixed linear sequence | SATISFIED | DAG edge model replaces `stepNumber` ordering; Kahn's topological sort in both evaluation engine and status service; cycle detection prevents circular DAGs |

Cross-reference against REQUIREMENTS.md ROADMAP traceability: Phase 5 is not yet listed in REQUIREMENTS.md traceability table (which ends at Phase 4). This is not a gap — the traceability section maps standard v1 requirements (PATH-*, DATA-*), not the PW-* clinical domain requirements that drove the Phase 5 architecture.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `PathwayEditor.tsx` | 104, 128 | `placeholder="..."` in Select and Input | Info | Form UX placeholder text — not a stub; these are legitimate UI affordances |
| `PathwayEvaluationActivityImpl.java` | 75 | `ObjectMapper` retained but not used | Warning | Kept per Plan 04 instruction to avoid changing bean wiring; does not affect correctness |

No blockers found. The `ObjectMapper` retention was an explicit documented decision in 05-04-SUMMARY.md to avoid recompiling the Temporal workflow. Placeholder text in form inputs is standard UX, not a stub.

### Human Verification Required

The automated checks confirm all code paths exist, are wired, and produce real data. The following items require running the application stack to verify:

**1. Depth-Indented DAG Visual Rendering**

**Test:** Start full stack, open a patient with a template-forked pathway. Verify steps render with indentation showing parallel paths at the same depth level and Unicode branching indicators.
**Expected:** Steps at depth 0 show no indentation. Steps at depth 1 show 24px indent + "└──" or "├──" connector. Steps at the same depth level are visually grouped.
**Why human:** StepRow renders icons and CSS indentation dynamically; visual correctness of the tiered layout cannot be asserted from source alone.

**2. Template Picker End-to-End Patient Creation**

**Test:** Add Patient → fill Step 1 → Step 2 with cancer type BREAST → verify RadioGroup appears → create patient in template mode → verify patient detail shows forked pathway steps → create second patient in "Build from documents" mode → verify empty pathway with "No pathway steps" empty state.
**Expected:** Both modes complete without errors; template mode produces N steps matching the BREAST template; empty mode produces 0 steps.
**Why human:** Full TanStack Query → fetch → Spring Boot → DB → response cycle; DB must be running with seeded templates.

**3. Inline Pathway Editor Toggle**

**Test:** Open a patient detail → click "Edit Pathway" → verify button changes to "Done Editing" (primary style) → click "Add Step" → fill name field → submit → verify step appears in list → click skip on a step → fill reason → confirm → verify step shows line-through with "Skipped" badge.
**Expected:** All state transitions happen without page reload; mutations reflect immediately via TanStack Query cache invalidation.
**Why human:** Stateful React toggle and mutation response behavior requires browser testing.

**4. Cycle Detection Error Display**

**Test:** In editor mode, expand "Dependencies" section → add an edge A→B → then try to add edge B→A → verify inline error appears.
**Expected:** Backend returns 409 Conflict; EdgeEditor displays "Cannot add this dependency — it would create a circular path."
**Why human:** Requires live backend DFS cycle detection response and frontend onError handler triggering.

**5. Tablet Responsive Layout**

**Test:** Resize browser to 768px → verify pathway card and care events card stack vertically (not side-by-side) → verify branching indicators still render at 768px width.
**Expected:** Grid collapses to single column; DAG view remains readable.
**Why human:** CSS responsive behavior requires browser resize testing.

---

_Verified: 2026-05-04T21:00:00Z_
_Verifier: Claude (gsd-verifier)_
