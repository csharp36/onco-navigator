# Phase 8: Template Inheritance - Context

**Gathered:** 2026-05-05
**Status:** Ready for planning

<domain>
## Phase Boundary

Pathway templates become extensible with parent/child relationships. A child template inherits all parent steps and can override, add, remove, or rearrange specific steps. At fork time (when a patient is enrolled), the system merges parent + child into a flat per-patient pathway.

This phase delivers: a `parent_template_id` column on `pathway_templates`, a diff-based child template JSONB schema (overrides, additions, removals, edge changes), a merge engine that resolves parent + child diff at fork time, removal of the UNIQUE constraint on `cancer_type` (multiple templates per cancer type), a "Rectal Cancer" child template inheriting from the "Colorectal Cancer" root with neoadjuvant-specific modifications, a template name/description column on `pathway_templates` for display, and an updated TemplatePicker that shows variant selection when multiple templates exist for a cancer type.

This phase does NOT build alert format changes (Phase 9), new cancer type enum values, or template admin UI (deferred to ADV-01).

</domain>

<decisions>
## Implementation Decisions

### Cancer Type Taxonomy
- **D-01:** **COLORECTAL stays as one cancer type** (Claude's discretion) — the colon/rectal distinction is handled at the template level via inheritance, not by adding new CancerType enum values. The "Colorectal Cancer" root template is the colon pathway; the "Rectal Cancer" child template adds neoadjuvant modifications. This exercises the inheritance system (the point of Phase 8) and avoids touching the CancerType enum or patient form.
- **D-02:** **General-purpose inheritance** — the parent/child mechanism works for any cancer type, not just colorectal. Rectal is the first child template, but the same system supports future subtypes (e.g., small cell vs non-small cell lung) without schema changes.
- **D-03:** **Single-level inheritance only** — a template can have a parent, but not a grandparent. The merge engine only resolves one level of parent + child diff. If a deeper variant is needed, it overrides from the root directly.

### Inheritance Power
- **D-04:** **Full override power** — a child template can: (a) override parent step properties (time windows, alert text, suggested actions, descriptions), (b) add new steps with their own prerequisite edges, (c) remove parent steps entirely (mark as excluded), and (d) rearrange prerequisite edges between parent steps. All four operations are needed for the rectal case (insert neoadjuvant steps, reverse surgery ordering, adjust time windows).
- **D-05:** **Diff-based storage** — child template JSONB stores only the delta from the parent: overridden step fields, added steps, removed step IDs, and edge changes. The parent's full step list is NOT duplicated in the child. At fork time, the merge engine reads the parent's templateData, applies the child's diff, and produces the flat step list that gets deep-copied to per-patient rows (per Phase 5 D-05).
- **D-06:** **Live inheritance at fork time** — non-overridden parent steps always reflect the latest parent template version when a new patient is enrolled. If the oncologist corrects a parent step's time window, all future forks of child templates get the updated value automatically. Only explicitly overridden fields in the child are immune to parent updates. (Note: per-patient pathways remain version-locked after fork — Phase 5 D-05 unchanged.)

### Template Selection UX
- **D-07:** **Two-step: cancer type then variant** — the patient creation wizard flow is: Step 1 selects cancer type (unchanged). Step 2 shows template variants only when 2+ templates exist for that cancer type. If only one template exists (Breast, Lung), the wizard auto-selects it and shows the current simple radio (template vs empty) with zero UX change.
- **D-08:** **Root template is default** — when multiple templates exist for a cancer type, the root (parent) template is pre-selected as the default. Child templates are alternatives the nurse can opt into.
- **D-09:** **Brief description on child templates** — each child template shows a 1-line description in the picker explaining the clinical difference from the parent (e.g., "Includes neoadjuvant chemoradiation before surgery"). Helps nurses pick correctly.

### Claude's Discretion
- Cancer type taxonomy model details: whether to keep COLORECTAL as-is with sub-templates or split — Claude should pick whichever integrates cleanest with the existing architecture
- Schema design for `parent_template_id`, template `name`/`description` columns, and the diff JSONB structure
- Merge algorithm implementation (order of operations: apply removals, apply overrides, apply additions, apply edge changes)
- Flyway migration structure: ALTER TABLE for new columns, removing UNIQUE constraint, seed data for the rectal child template
- `PathwayForkService` modification to handle the merge before deep-copying to per-patient rows
- `PathwayTemplateRepository` query changes (findByCancerType returns List, findById for specific template selection)
- TemplatePicker component redesign for the variant radio group
- Backend REST endpoint changes for template listing (by cancer type with children)
- What specific steps/edges the rectal child template contains (clinical content: neoadjuvant chemoradiation before surgery, adjusted time windows)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Clinical Context
- `docs/Pathway-Template-Review-Worksheet.md` — Oncologist clinical review. PW-CR-004: "B" — separate colon vs rectal pathways needed. Rectal cancer requires neoadjuvant chemoradiation BEFORE surgery, reversing the treatment sequence. Also PW-CR-005 (split pathology from MSI testing), PW-CR-006 (no missing steps), PW-CR-007 (time window corrections).
- `docs/Onco-Navigator AI - V1 Feature Specification v2.md` — Original colorectal pathway definition (6 steps, surgery-first sequence). The rectal child template modifies this sequence.

### Requirements
- `.planning/REQUIREMENTS.md` — PW-CR-004 (separate colon vs rectal pathways), PATH-01 (configurable pathway templates).

### Prior Phase Context
- `.planning/phases/05-per-patient-pathway-dag/05-CONTEXT.md` — Phase 5 D-05 (deep copy, version-locked fork — merge must happen before fork), D-07 (template picker in wizard — Phase 8 extends it), D-08 (Flyway data migration — existing patients already on per-patient rows).
- `.planning/phases/02-pathway-engine/02-CONTEXT.md` — Phase 2 D-10 (three cancer pathway templates: breast, lung, colorectal).

### Existing Backend Code (Phase 8 integration points)
- `src/main/java/com/onconavigator/domain/PathwayTemplate.java` — Entity needs `parentTemplateId`, `name`, `description` columns. Currently has UNIQUE constraint on `cancer_type` (must be removed). No `name` field — cancer type serves as display name today.
- `src/main/java/com/onconavigator/repository/PathwayTemplateRepository.java` — `findByCancerType()` returns single Optional. Phase 8 changes to return List and adds `findByParentTemplateId()`.
- `src/main/java/com/onconavigator/service/PathwayForkService.java` — `forkFromTemplate()` at line 78 currently finds template by cancer type. Phase 8 modifies to accept a specific template ID and merge parent + child diff before deep-copying steps/edges.
- `src/main/resources/db/migration/V6__seed_pathway_templates.sql` — Current seed data for the three root templates. Phase 8 adds the rectal child template as new seed data.
- `src/main/java/com/onconavigator/domain/dto/PathwayStep.java` — JSONB step DTO used in template parsing. The merge engine uses this to read parent steps and apply child overrides.
- `src/main/java/com/onconavigator/domain/enums/CancerType.java` — Currently BREAST, LUNG, COLORECTAL. Phase 8 does NOT add new values (D-01).
- `src/main/resources/db/migration/V14__create_per_patient_pathway_tables.sql` — Per-patient pathway tables. The `source_template_id` column already references `pathway_templates(id)` — child template ID will be stored here for traceability.

### Existing Frontend Code
- `frontend/src/features/patients/TemplatePicker.tsx` — Simple radio group (template vs empty). Phase 8 adds variant selection when multiple templates exist per cancer type.
- `frontend/src/features/patients/PatientWizard.tsx` — Two-step wizard. Step 2 template selection needs to pass specific template ID instead of just cancer type.
- `frontend/src/features/patients/types.ts` — TypeScript types need template list response with name, description, parent info.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `PathwayForkService.forkFromTemplate()` — existing deep-copy logic for steps + edges. Phase 8 adds a merge step before the copy: resolve parent templateData + child diff into a flat step list, then pass to the existing copy logic.
- `PathwayStep` record DTO — Jackson JSONB deserialization. The merge engine reads parent steps as `List<PathwayStep>` and applies child overrides field by field.
- `TemplatePicker` component — existing radio group structure. Extend with conditional variant list when multiple templates exist.
- `PatientPathway.sourceTemplateId` — already stores which template was forked. Child template ID goes here naturally.

### Established Patterns
- Flyway versioned SQL migrations for schema changes (ALTER TABLE for new columns, DROP CONSTRAINT for UNIQUE removal, INSERT for seed data)
- Hibernate Envers `@Audited` on PathwayTemplate — new fields auto-captured in audit trail
- JSONB template_data parsed via Jackson `ObjectMapper.readValue()` with `TypeReference<List<PathwayStep>>`
- `@PrePersist` / `@PreUpdate` for timestamp management on entities
- shadcn RadioGroup for selection UI (existing TemplatePicker pattern)
- TanStack Query for server state in frontend

### Integration Points
- `PathwayTemplate` entity → add `parentTemplateId` (UUID, nullable, self-referential FK), `name` (String), `description` (String)
- `pathway_templates` table → DROP UNIQUE on `cancer_type`, ADD `parent_template_id` / `name` / `description` columns
- `PathwayTemplateRepository` → `findByCancerType()` returns `List<PathwayTemplate>`, add `findByParentTemplateId()`
- `PathwayForkService.forkFromTemplate()` → accept template ID, load template + parent if child, merge diff, then deep-copy
- `PatientService` → pass specific template ID to fork service (not just cancer type)
- Patient creation endpoint → accept `templateId` parameter instead of deriving from cancer type alone
- TemplatePicker → fetch templates by cancer type, show variant picker when 2+ exist
- New backend endpoint: `GET /api/pathway-templates?cancerType=COLORECTAL` returning template list with hierarchy info

</code_context>

<specifics>
## Specific Ideas

- The rectal child template's core change: insert "Neoadjuvant Chemoradiation" step(s) before surgery, rearrange edges so surgery depends on neoadjuvant completion instead of directly following consultation. The oncologist's PW-CR-004 answer is the clinical justification.
- The diff-based storage means child templates are compact and self-documenting — reading the diff immediately shows what's clinically different about the variant.
- Live inheritance (D-06) means when the oncologist corrects a time window on the colorectal root template, the rectal child template automatically picks it up for non-overridden steps at next fork. This reduces maintenance burden.
- The UNIQUE constraint removal on `cancer_type` is a breaking change — `findByCancerType()` must change from `Optional` to `List`. All callers need updating.
- PW-CR-005 (split pathology from MSI testing) was answered "B" by the oncologist — this is a template content change that may apply to the colorectal root template, not just the rectal child. The researcher should check whether this step split should be done in this phase or is already handled.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 08-template-inheritance*
*Context gathered: 2026-05-05*
