# Phase 6: AI Step Extraction from Clinical Documents - Context

**Gathered:** 2026-05-04
**Status:** Ready for planning

<domain>
## Phase Boundary

When a clinical document is uploaded for a patient, Claude AI extracts ordered/planned care events and proposes them as new pathway steps with dependency edges. A nurse must confirm before steps become active in the DAG evaluation.

This phase delivers: a step extraction AI service (separate Claude call after classification), automatic extraction triggered inline during document upload, PROPOSED steps with source=AI_EXTRACTED and document linkage, proposed DAG edges (both between new steps and connecting to existing steps), per-step inline confirm/reject UX on the patient detail pathway view, backend duplicate detection by event type, "already covered" transparency display, REJECTED soft-delete status for audit trail, and a `pathwayStepsChanged` signal on confirmation.

This phase does NOT build referral triggers or enhanced timing (Phase 7), template inheritance (Phase 8), alert format changes (Phase 9), or batch step review UI.

</domain>

<decisions>
## Implementation Decisions

### Extraction Trigger & Pipeline Integration
- **D-01:** **Automatic during document upload** — step extraction runs as the next pipeline step in `DocumentProcessingService.processUpload()` after classification + patient matching succeeds. No separate user action required. Mirrors how classification already works automatically.
- **D-02:** **Separate Claude API call** from document classification — extraction has its own prompt, output schema, feature flag (`onconavigator.ai.step-extraction.enabled`), and circuit breaker. A classification failure doesn't prevent extraction or vice versa.
- **D-03:** **Claude receives existing pathway steps as context** — send step names, statuses, and event types (non-PHI per cross-cutting constraint) so Claude can avoid re-proposing existing steps and can suggest where new steps fit relative to existing ones.

### Nurse Review UX
- **D-04:** **Inline with visual distinction** — PROPOSED steps appear in the pathway list at their inferred position with dashed border, gray/muted styling, and an "AI Proposed" badge. Consistent with Phase 5 D-15 (dashed outline/gray for PROPOSED).
- **D-05:** **Per-step inline confirm/reject buttons** — each PROPOSED step row shows [Confirm] and [Reject] icons. Confirming transitions to ACTIVE; rejecting transitions to REJECTED. No modal overhead, works naturally with the existing pathway editor.
- **D-06:** **Edit before confirm** — the nurse can edit a proposed step's name, time window, or event type before confirming it. Uses the same Phase 5 inline edit UI that already exists for ACTIVE steps. Claude's extraction is a starting point, not final.
- **D-07:** **REJECTED = soft delete with audit trail** — rejected steps stay in the database with status=REJECTED (new enum value). Hidden from the pathway view by default but visible via a "show rejected" toggle. Preserves full history of what AI proposed. Prevents re-proposing the same step from a different document (combined with D-09 dedup).

### Duplicate Handling
- **D-08:** **Claude filters + backend validates** (belt and suspenders) — the prompt instructs Claude to NOT propose steps matching existing ACTIVE/COMPLETED steps. The backend also compares extracted step event types against existing steps before creating PROPOSED rows.
- **D-09:** **Event type match for dedup** — if an extracted step has the same CareEventType as an existing ACTIVE/COMPLETED step, it's a duplicate. Deterministic, uses the structured enum. Also checks against REJECTED steps to avoid re-proposing previously rejected extractions.
- **D-10:** **Show duplicates as "already covered"** — when the backend detects duplicates, display them in a separate informational section (e.g., "Steps already in pathway: CT Scan, Blood Work"). Gives transparency into what Claude found vs. what's genuinely new.

### Edge Inference
- **D-11:** **Claude proposes full graph edges** — Claude infers ordering from the document (e.g., "surgery before radiation", "biopsy results needed for treatment planning") and proposes edges both between new steps AND connecting new steps to existing active steps in the pathway.
- **D-12:** **Edges bundled with step confirmation** — when a nurse confirms a PROPOSED step, its proposed edges are confirmed automatically. Rejecting a step removes its edges too. One action per step, edges come along for the ride.
- **D-13:** **Cross-edges to existing steps** — Claude can propose edges from existing active steps to new proposed steps (e.g., "existing surgery → new radiation"). Edges to existing steps auto-activate when the proposed step is confirmed. Cycle detection runs per Phase 5 cross-cutting constraint.

### Claude's Discretion
- Extraction failure behavior (silent skip vs toast notification vs other UX pattern — follow existing fallback patterns)
- Claude prompt template structure and structured output schema for step + edge extraction
- `stepExtractionClient` ChatClient bean configuration (temperature, max tokens)
- How "inferred position" is determined for rendering PROPOSED steps in the DAG view
- How proposed edges are visually rendered (dashed lines, lighter color, etc.)
- How the "already covered" informational section is displayed in the UI
- Whether to add a "show rejected" toggle or a separate view for rejected steps
- Exact circuit breaker parameters for the extraction call (reuse existing `claude-api` breaker name or separate)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Clinical Context
- `docs/Pathway-Template-Review-Worksheet.md` — Oncologist clinical review (2026-05-04). Key decision PW-ALL-002: "each patient needs unique sequence, steps extracted from MD notes/orders/nurse notes." Primary motivation for AI step extraction.
- `docs/Onco-Navigator AI - V1 Feature Specification v2.md` — Original pathway definitions, document types, alert scenarios.

### Requirements
- `.planning/REQUIREMENTS.md` — PW-ALL-002 (events extracted from documents), PW-BR-001 (steps from MD notes/orders/nurse notes)

### Prior Phase Context
- `.planning/phases/04-ai-document-ingestion/04-CONTEXT.md` — Phase 4 decisions: D-01 through D-04 (PDF extraction pipeline), D-13 (full text to Claude, PHI/BAA), D-16 (Resilience4j circuit breaker). Phase 6 hooks into the same pipeline.
- `.planning/phases/05-per-patient-pathway-dag/05-CONTEXT.md` — Phase 5 decisions: D-02 (PROPOSED status), D-06 (pathwayStepsChanged signal), D-09 (edge editing), D-10 (cascade edge delete), D-14 (inline editing), D-15 (status icons/colors). Phase 6 extends all of these.

### Existing Backend Code (Phase 6 integration points)
- `src/main/java/com/onconavigator/service/DocumentProcessingService.java` — Document upload pipeline. Phase 6 hooks in at ~line 113 after classification succeeds (extracted text + patient ID available).
- `src/main/java/com/onconavigator/ai/service/DocumentClassificationService.java` — Pattern to follow: @CircuitBreaker, feature flag, public fallback method, null-return-on-failure.
- `src/main/java/com/onconavigator/ai/config/AiClientConfig.java` — ChatClient bean registration pattern. Phase 6 adds `stepExtractionClient` @Qualifier bean.
- `src/main/java/com/onconavigator/domain/PatientPathwayStep.java` — Entity already has PROPOSED status support, nullable sourceTemplateStepId (null for AI steps), nullable eventType.
- `src/main/java/com/onconavigator/domain/enums/PathwayStepStatus.java` — PROPOSED already defined. Phase 6 adds REJECTED enum value.
- `src/main/java/com/onconavigator/service/PatientPathwayService.java` — Step CRUD. Phase 6 adds `createProposedStep()` method and `confirmProposedStep()` / `rejectProposedStep()` methods. Each mutation signals pathwayStepsChanged.
- `src/main/java/com/onconavigator/workflow/PatientPathwayWorkflow.java` — `pathwayStepsChanged()` signal at line 74. Already wired, no new signal needed.

### Existing Frontend Code
- `frontend/src/routes/patients/$patientId.tsx` — Patient detail page with pathway DAG view. PROPOSED steps render inline here.
- `frontend/src/features/patients/types.ts` — TypeScript types. Need proposed step source + document link fields.
- `frontend/src/features/patients/api.ts` — API hooks. Need confirm/reject mutation hooks.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `DocumentClassificationService` — exact pattern for a new AI service: @CircuitBreaker, feature flag, ChatClient @Qualifier, public fallback
- `DocumentProcessingService.processUpload()` — pipeline orchestrator; extraction hooks in after classification
- `PatientPathwayService.createStep()` — step creation logic; Phase 6 adds parallel `createProposedStep()` with status=PROPOSED
- `pathwayStepsChanged` signal — already called on every step mutation; confirm/reject just need to call it too
- Phase 5 inline pathway editor — edit UI for ACTIVE steps; PROPOSED steps reuse the same edit form before confirming
- `ClinicalDocument.extractedText` — persisted extracted text; re-accessible for extraction without re-processing the PDF

### Established Patterns
- Resilience4j `@CircuitBreaker(name = "claude-api")` shared across all Claude calls
- Feature flag via `@Value("${onconavigator.ai.*.enabled:false}")` — gates AI features pending BAA
- ChatClient beans per use case via `@Qualifier` in `AiClientConfig`
- Hibernate Envers `@Audited` on all ePHI entities
- PostgreSQL ENUM types mapped to Java enums (PathwayStepStatus will need REJECTED added via Flyway ALTER TYPE)
- TanStack Query invalidation on mutations for optimistic UI updates
- Per-step inline buttons in pathway view (Phase 5 edit/remove pattern)

### Integration Points
- `DocumentProcessingService` line ~113 → call new `StepExtractionService.extractSteps()` after classification
- `PatientPathwayService` → new methods: `createProposedSteps()`, `confirmProposedStep()`, `rejectProposedStep()`
- `PatientPathwayController` → new endpoints: POST confirm, POST reject (or PATCH status transitions)
- Patient detail pathway view → PROPOSED step rendering with confirm/reject buttons
- Flyway migration → ALTER TYPE pathway_step_status ADD VALUE 'REJECTED'
- `application-local.yml` → new feature flag `onconavigator.ai.step-extraction.enabled`
- `AiClientConfig` → new `stepExtractionClient` bean

</code_context>

<specifics>
## Specific Ideas

- The extraction prompt should return structured JSON: array of steps, each with name, event type, estimated time window, and proposed edges (source step reference → target step reference)
- Claude receives existing steps as context to avoid duplicates AND to propose cross-edges — this dual purpose justifies sending the pathway state
- The "already covered" display prevents nurses from wondering "why didn't the AI find my CT scan?" — it shows Claude DID find it but recognized it's already tracked
- REJECTED status + dedup against REJECTED steps prevents the annoying pattern of re-uploading a document and getting the same rejected proposals again
- Edge confirmation bundled with step confirmation keeps the UX simple — nurses think in terms of "approve this step" not "approve this step AND then approve its connections"

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 06-ai-step-extraction-from-clinical-documents*
*Context gathered: 2026-05-04*
