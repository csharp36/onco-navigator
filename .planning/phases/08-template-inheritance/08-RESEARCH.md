# Phase 8: Template Inheritance - Research

**Researched:** 2026-05-05
**Domain:** JPA entity inheritance, JSONB diff-merge, Flyway schema migration, React conditional UI
**Confidence:** HIGH

## Summary

Phase 8 extends the existing `PathwayTemplate` entity to support parent/child relationships via a self-referential `parent_template_id` foreign key. A child template stores only its diff from the parent (overrides, additions, removals, edge changes) in its `template_data` JSONB column. At fork time, a merge engine resolves parent steps + child diff into a flat step list, which then feeds into the existing `PathwayForkService` deep-copy logic unchanged.

The scope is well-bounded: one new column on `pathway_templates` (`parent_template_id`), two metadata columns (`name`, `description`), removal of the UNIQUE constraint on `cancer_type`, a merge engine service class, modification of `PathwayForkService.forkFromTemplate()` to accept a template ID and perform merging, a new REST endpoint for listing templates by cancer type, a rectal cancer child template seed migration, and an updated `TemplatePicker` frontend component with conditional variant selection. All decisions are locked in CONTEXT.md with high specificity -- no ambiguous design choices remain.

**Primary recommendation:** Implement the merge engine as a pure-function service (`TemplateMergeService`) that takes a parent `List<PathwayStep>` and a child diff JSONB structure, returns a merged `List<PathwayStep>`. Keep it stateless and testable. Wire it into `PathwayForkService` which handles the existing deep-copy and persistence logic.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** COLORECTAL stays as one cancer type. Colon/rectal distinction is handled at the template level via inheritance, not by adding new CancerType enum values.
- **D-02:** General-purpose inheritance. The parent/child mechanism works for any cancer type, not just colorectal.
- **D-03:** Single-level inheritance only. A template can have a parent, but not a grandparent. Merge engine resolves one level only.
- **D-04:** Full override power. Child can: (a) override parent step properties, (b) add new steps with edges, (c) remove parent steps, (d) rearrange prerequisite edges.
- **D-05:** Diff-based storage. Child template JSONB stores only the delta. Parent steps NOT duplicated.
- **D-06:** Live inheritance at fork time. Non-overridden parent steps always reflect the latest parent version at fork time.
- **D-07:** Two-step UX: cancer type then variant. Variant picker only appears when 2+ templates exist for a cancer type.
- **D-08:** Root template is default. Pre-selected when multiple templates exist.
- **D-09:** Brief description on child templates. One-line clinical description in the picker.

### Claude's Discretion
- Schema design for `parent_template_id`, `name`/`description` columns, and the diff JSONB structure
- Merge algorithm implementation (order of operations)
- Flyway migration structure
- `PathwayForkService` modification
- `PathwayTemplateRepository` query changes
- TemplatePicker component redesign
- Backend REST endpoint for template listing
- Rectal child template clinical content (neoadjuvant steps)

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PW-CR-004 | Separate colon vs rectal pathways | Template inheritance enables this: colorectal root template = colon pathway; rectal child template adds neoadjuvant chemoradiation before surgery. No CancerType enum change needed (D-01). |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Template inheritance schema (parent_template_id, name, description) | Database / Storage | -- | Schema change via Flyway migration; JPA entity reflects it |
| Diff-based child template JSONB | Database / Storage | API / Backend | Stored as JSONB; parsed and merged in backend service |
| Merge engine (parent + child diff -> flat steps) | API / Backend | -- | Pure business logic; no persistence, no client involvement |
| Template fork with merge | API / Backend | -- | Extension of existing PathwayForkService; merge happens before deep-copy |
| Template listing endpoint | API / Backend | -- | New GET endpoint returning templates by cancer type with hierarchy info |
| Template variant picker | Browser / Client | -- | Conditional UI: shows variant radio group only when 2+ templates exist |
| Rectal cancer seed data | Database / Storage | -- | Flyway INSERT for the child template with neoadjuvant diff content |

## Standard Stack

### Core
No new libraries required. This phase uses only existing project dependencies.

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 3.5.x | Application framework | Already in project [VERIFIED: pom.xml] |
| Spring Data JPA | via Boot BOM | Repository queries | Already in project [VERIFIED: pom.xml] |
| Jackson (ObjectMapper) | via Boot BOM | JSONB serialization/deserialization | Already used for template_data parsing [VERIFIED: PathwayForkService.java] |
| Flyway | 11.x | Schema migration | Already in project [VERIFIED: 18 existing migrations] |
| React + TypeScript | 19.x / 5.x | Frontend | Already in project [VERIFIED: frontend/package.json] |
| TanStack Query | v5.x | Server state management | Already used for all API calls [VERIFIED: api.ts] |
| shadcn/ui RadioGroup | current | Radio button components | Already used in TemplatePicker [VERIFIED: TemplatePicker.tsx] |

### Supporting
No new supporting libraries needed.

### Alternatives Considered
None -- this phase extends existing patterns without introducing new dependencies.

## Architecture Patterns

### System Architecture Diagram

```
Patient Creation Wizard (React)
    |
    v
[1] Select Cancer Type (existing)
    |
    v
[2] GET /api/pathway-templates?cancerType=COLORECTAL
    |                               |
    v                               v
  1 template found           2+ templates found
  (auto-select root)         (show variant picker)
    |                               |
    v                               v
[3] POST /api/patients  { templateId: UUID, pathwayMode: "template" }
    |
    v
PatientService.createPatient()
    |
    v
PathwayForkService.forkFromTemplate(patient, templateId, actorId)
    |
    +--> Is this a child template? (parentTemplateId != null)
    |       |
    |       YES --> Load parent template steps
    |       |       Load child diff JSONB
    |       |       TemplateMergeService.merge(parentSteps, childDiff)
    |       |       --> Merged flat List<PathwayStep>
    |       |
    |       NO --> Use template steps directly
    |
    v
Deep-copy merged steps + edges into per-patient tables (existing logic)
```

### Recommended Project Structure

No new directories needed. New files integrate into existing package structure:

```
src/main/java/com/onconavigator/
  domain/
    PathwayTemplate.java          # ADD: parentTemplateId, name, description fields
  domain/dto/
    PathwayStep.java              # UNCHANGED -- merge engine produces these
    TemplateDiff.java             # NEW: diff JSONB structure (record)
    StepOverride.java             # NEW: single step override within diff (record)
    EdgeChange.java               # NEW: edge addition/removal within diff (record)
  repository/
    PathwayTemplateRepository.java # MODIFY: findByCancerType -> List, add findByParentTemplateId
  service/
    TemplateMergeService.java     # NEW: pure-function merge engine
    PathwayForkService.java       # MODIFY: accept templateId, call merge engine
    PatientService.java           # MODIFY: pass templateId to fork service
  web/
    PathwayTemplateController.java # NEW: GET /api/pathway-templates?cancerType=X
    dto/
      PathwayTemplateResponse.java # NEW: response DTO with id, name, description, isChild
      CreatePatientRequest.java    # MODIFY: add optional templateId field
src/main/resources/db/migration/
  V19__template_inheritance.sql    # NEW: ALTER TABLE, DROP UNIQUE, ADD columns
  V20__seed_rectal_template.sql    # NEW: INSERT child template

frontend/src/
  features/patients/
    TemplatePicker.tsx             # REWRITE: fetch templates, conditional variant picker
    PatientWizard.tsx              # MODIFY: pass templateId to create request
    api.ts                         # ADD: usePathwayTemplates hook
    types.ts                       # ADD: PathwayTemplateResponse type
```

### Pattern 1: Diff-Based Child Template JSONB Structure

**What:** The child template's `template_data` JSONB column stores a diff object (not a step array) describing how the child differs from the parent.

**When to use:** When a child template is created (`parent_template_id IS NOT NULL`).

**Structure:**
```json
{
  "overrides": [
    {
      "stepId": "CRC_03",
      "fields": {
        "windowDays": 60,
        "alertText": "Surgery not yet performed within 60 days (neoadjuvant chemo completes first).",
        "suggestedAction": "Confirm neoadjuvant therapy completion before scheduling surgery."
      }
    }
  ],
  "additions": [
    {
      "stepId": "RECTAL_01",
      "stepNumber": 3,
      "name": "Neoadjuvant Chemoradiation",
      "description": "Combined chemotherapy and radiation therapy before surgery.",
      "eventType": "RADIATION",
      "windowDays": 30,
      "anchorType": "PREVIOUS_STEP",
      "anchorStepId": null,
      "required": true,
      "alertText": "Neoadjuvant chemoradiation not started within 30 days of staging.",
      "suggestedAction": "Coordinate with radiation oncology for treatment planning.",
      "prerequisites": ["CRC_02"]
    }
  ],
  "removals": [],
  "edgeChanges": {
    "remove": [
      { "from": "CRC_01", "to": "CRC_03" }
    ],
    "add": [
      { "from": "RECTAL_01", "to": "CRC_03" },
      { "from": "CRC_01", "to": "RECTAL_01" }
    ]
  }
}
```

**Key design decisions:**
- `overrides[].fields` is a partial object -- only fields that differ from the parent are included. Non-overridden fields inherit from the parent at merge time (D-06: live inheritance). [VERIFIED: matches CONTEXT.md D-05, D-06]
- `additions[]` uses the same `PathwayStep` schema as parent template steps. Added steps have stepIds prefixed with the child template's domain (e.g., `RECTAL_01`). [ASSUMED]
- `removals` is an array of parent stepIds to exclude entirely. Empty array for the rectal case (no parent steps removed). [ASSUMED]
- `edgeChanges` contains `remove` (parent edges to drop) and `add` (new edges) as `{from, to}` pairs referencing stepIds. This enables the rectal template to reroute surgery to depend on neoadjuvant completion instead of directly following consultation. [VERIFIED: matches CONTEXT.md D-04]

### Pattern 2: Merge Engine Algorithm

**What:** A stateless service that resolves parent steps + child diff into a flat `List<PathwayStep>`.

**When to use:** At fork time, when a child template is selected.

**Algorithm (order of operations):**
```java
// Source: designed per CONTEXT.md D-04 merge requirements
public List<PathwayStep> merge(List<PathwayStep> parentSteps, TemplateDiff diff) {
    // 1. REMOVALS: filter out parent steps whose stepId is in diff.removals
    List<PathwayStep> base = parentSteps.stream()
        .filter(s -> !diff.removals().contains(s.stepId()))
        .toList();

    // 2. OVERRIDES: for each step in base, apply field-level overrides from diff.overrides
    List<PathwayStep> merged = base.stream()
        .map(s -> applyOverride(s, diff.overrides()))
        .collect(Collectors.toCollection(ArrayList::new));

    // 3. ADDITIONS: append new steps from diff.additions
    merged.addAll(diff.additions());

    // 4. EDGE CHANGES: apply to prerequisite lists
    //    - Remove edges listed in diff.edgeChanges.remove
    //    - Add edges listed in diff.edgeChanges.add
    merged = applyEdgeChanges(merged, diff.edgeChanges());

    // 5. RENUMBER: reassign stepNumbers based on final order
    return renumber(merged);
}
```

**Why this order matters:**
- Removals first prevents overriding a step that is being removed (wasted work).
- Overrides before additions ensures parent steps are modified before new steps reference them.
- Edge changes last because additions may introduce new stepIds that edges reference.

[ASSUMED -- algorithm designed from CONTEXT.md requirements, not from an external source]

### Pattern 3: Repository Query Changes

**What:** `PathwayTemplateRepository.findByCancerType()` must return `List<PathwayTemplate>` instead of `Optional<PathwayTemplate>`.

**Breaking change impact:**
```java
// BEFORE (current code):
Optional<PathwayTemplate> findByCancerType(CancerType cancerType);

// AFTER:
List<PathwayTemplate> findByCancerType(CancerType cancerType);
Optional<PathwayTemplate> findByCancerTypeAndParentTemplateIdIsNull(CancerType cancerType);
List<PathwayTemplate> findByParentTemplateId(UUID parentTemplateId);
```

**Callers that must be updated:**
1. `PathwayForkService.forkFromTemplate()` -- currently uses `findByCancerType()`. Will change to `findById(templateId)` since the specific template ID is now passed in. [VERIFIED: PathwayForkService.java line 80]
2. No other callers exist in the codebase -- confirmed by grep. [VERIFIED: grep of findByCancerType found only 2 references: repository definition and PathwayForkService]

### Pattern 4: Self-Referential JPA Entity

**What:** `PathwayTemplate` gets a nullable `parentTemplateId` column with a self-referential foreign key.

```java
// Source: standard JPA pattern for self-referential relationships
@Column(name = "parent_template_id")
private UUID parentTemplateId;  // null for root templates

@Column(name = "name", nullable = false)
private String name;  // display name (e.g., "Colorectal Cancer" or "Rectal Cancer")

@Column(name = "description")
private String description;  // 1-line clinical description for child templates
```

**Design choice -- UUID column vs @ManyToOne:**
Use a plain UUID column (not `@ManyToOne`) for `parentTemplateId`, consistent with the project pattern established in Phase 4 (ClinicalDocument.careEventId uses plain UUID -- see decision [Phase 04-01]). This avoids bidirectional relationship complexity and lazy-loading concerns. The parent template is loaded explicitly when needed via `findById()`. [VERIFIED: ClinicalDocument.careEventId pattern in Phase 04-01 decision]

### Anti-Patterns to Avoid

- **Duplicating parent steps in child template_data:** The child stores ONLY the diff. If parent steps were duplicated, D-06 (live inheritance) would break -- parent updates would not propagate to child forks.
- **Using @ManyToOne with cascading for parent/child relationship:** Self-referential @ManyToOne with cascade creates subtle lazy-loading and N+1 query issues. Use plain UUID + explicit load. [VERIFIED: project convention from Phase 04-01]
- **Modifying the CancerType enum for colon/rectal split:** D-01 explicitly locks this -- the distinction is at the template level, not the enum level. Adding COLON and RECTAL values would require patient form changes, migration of existing data, and Temporal workflow ID changes.
- **Multi-level inheritance:** D-03 locks single-level only. The merge engine MUST NOT recursively load grandparent templates.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JSONB diff deserialization | Custom string parsing | Jackson `ObjectMapper.readValue()` with `TemplateDiff` record | Jackson handles nested objects, null safety, type coercion automatically. Already used for `List<PathwayStep>` parsing. |
| Step field-level merge | Reflection-based generic merger | Explicit field-by-field copy in `applyOverride()` method | PathwayStep is a Java record with 12 fields. Reflection is fragile and obscures which fields are overridable. Explicit code is readable, testable, and catches compile errors on schema changes. |
| Template tree traversal | Recursive parent loading | Single parent lookup (D-03 limits to one level) | Single-level inheritance means one `findById(parentId)` call. No tree traversal needed. |

**Key insight:** The merge engine is conceptually simple -- it is a JSON patch operation on a step array. Do not over-engineer it. A 50-line service method with explicit field copying is correct for 12 fields and single-level inheritance.

## Common Pitfalls

### Pitfall 1: UNIQUE Constraint Removal Breaks findByCancerType Callers
**What goes wrong:** Dropping the UNIQUE constraint on `cancer_type` without updating `findByCancerType()` return type causes Spring Data to throw `IncorrectResultSizeDataAccessException` when 2+ templates exist for a cancer type.
**Why it happens:** Spring Data's `Optional<T>` query derivation assumes at most one result. With the UNIQUE constraint removed, multiple results are valid.
**How to avoid:** Update the return type to `List<PathwayTemplate>` AND update all callers in the same plan. The only caller is `PathwayForkService.forkFromTemplate()` which changes to use `findById()` anyway.
**Warning signs:** `IncorrectResultSizeDataAccessException` at runtime when a child template is seeded.

### Pitfall 2: Flyway Migration Ordering
**What goes wrong:** The rectal child template seed INSERT references a `parent_template_id` that does not exist because the colorectal root template was inserted in V6 with `gen_random_uuid()` -- its UUID is not deterministic.
**Why it happens:** V6 used `gen_random_uuid()` for template IDs. The child template's `parent_template_id` must reference the parent's actual ID.
**How to avoid:** The child template seed migration must use a subquery to look up the parent: `(SELECT id FROM pathway_templates WHERE cancer_type = 'COLORECTAL' AND parent_template_id IS NULL)`. After Phase 8, the `parent_template_id IS NULL` condition distinguishes the root from children.
**Warning signs:** FK violation on INSERT.

### Pitfall 3: Edge Changes Reference Non-Existent StepIds
**What goes wrong:** Edge changes in the diff reference stepIds from both parent and child. If a removal removes a step that an edge change references, the merged result has dangling edges.
**How to avoid:** The merge engine must validate edge integrity after merge: every edge's `from` and `to` must exist in the final merged step list. Log a warning and skip invalid edges rather than failing the fork.
**Warning signs:** `NullPointerException` in `PathwayForkService` when `stepIdMap.get(prereqStepId)` returns null for a dangling edge.

### Pitfall 4: Hibernate Envers on New Columns
**What goes wrong:** Adding columns to an `@Audited` entity requires the corresponding `_AUD` table to also get those columns. Flyway migration must ALTER both `pathway_templates` AND `pathway_templates_aud`.
**Why it happens:** Envers creates audit columns automatically on first startup, but if the `_AUD` table already exists (it does -- created when Phase 2 ran), new columns are NOT auto-added.
**How to avoid:** The Flyway migration must ALTER both tables: `pathway_templates` and `pathway_templates_aud`. Add `parent_template_id`, `name`, `description` to both.
**Warning signs:** Hibernate startup error: "could not execute statement" on `pathway_templates_aud` INSERT.

### Pitfall 5: Frontend Template Fetch Before Cancer Type Selection
**What goes wrong:** Calling `GET /api/pathway-templates?cancerType=X` before the user selects a cancer type results in a null/empty request.
**How to avoid:** The TanStack Query hook must use `enabled: !!cancerType` to prevent fetching until a cancer type is selected. This matches the existing pattern in `useDocumentAlreadyCovered`.
**Warning signs:** 400 Bad Request on page load.

### Pitfall 6: Existing Patients Not Affected
**What goes wrong:** Developers worry about migrating existing per-patient pathways when a parent template changes.
**Why it does NOT happen:** Per-patient pathways are version-locked after fork (Phase 5 D-05). Template inheritance only affects FUTURE forks. Existing patient pathways are independent copies. No data migration of patient data is needed for Phase 8.
**Warning signs:** None -- but this must be clearly documented to prevent unnecessary migration work.

## Code Examples

### Merge Engine Service

```java
// Source: designed from CONTEXT.md D-04, D-05, D-06 requirements
@Service
public class TemplateMergeService {

    private final ObjectMapper objectMapper;

    public TemplateMergeService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Merges parent steps with a child diff to produce a flat step list.
     * Order: removals -> overrides -> additions -> edge changes -> renumber.
     */
    public List<PathwayStep> merge(List<PathwayStep> parentSteps, TemplateDiff diff) {
        // 1. Remove excluded steps
        Set<String> removedIds = new HashSet<>(diff.removals());
        List<PathwayStep> working = parentSteps.stream()
                .filter(s -> !removedIds.contains(s.stepId()))
                .collect(Collectors.toCollection(ArrayList::new));

        // 2. Apply field-level overrides
        Map<String, StepOverride> overrideMap = diff.overrides().stream()
                .collect(Collectors.toMap(StepOverride::stepId, Function.identity()));
        working = working.stream()
                .map(s -> overrideMap.containsKey(s.stepId())
                        ? applyOverride(s, overrideMap.get(s.stepId()))
                        : s)
                .collect(Collectors.toCollection(ArrayList::new));

        // 3. Add new steps
        working.addAll(diff.additions());

        // 4. Apply edge changes to prerequisites
        working = applyEdgeChanges(working, diff.edgeChanges());

        // 5. Renumber step numbers
        for (int i = 0; i < working.size(); i++) {
            working.set(i, withStepNumber(working.get(i), i + 1));
        }

        return List.copyOf(working);
    }

    private PathwayStep applyOverride(PathwayStep step, StepOverride override) {
        Map<String, Object> fields = override.fields();
        return new PathwayStep(
                step.stepId(),
                step.stepNumber(),
                fields.containsKey("name") ? (String) fields.get("name") : step.name(),
                fields.containsKey("description") ? (String) fields.get("description") : step.description(),
                fields.containsKey("eventType") ? CareEventType.valueOf((String) fields.get("eventType")) : step.eventType(),
                fields.containsKey("windowDays") ? ((Number) fields.get("windowDays")).intValue() : step.windowDays(),
                fields.containsKey("anchorType") ? AnchorType.valueOf((String) fields.get("anchorType")) : step.anchorType(),
                fields.containsKey("anchorStepId") ? (String) fields.get("anchorStepId") : step.anchorStepId(),
                fields.containsKey("required") ? (Boolean) fields.get("required") : step.required(),
                fields.containsKey("alertText") ? (String) fields.get("alertText") : step.alertText(),
                fields.containsKey("suggestedAction") ? (String) fields.get("suggestedAction") : step.suggestedAction(),
                fields.containsKey("prerequisites") ? castPrerequisites(fields.get("prerequisites")) : step.prerequisites()
        );
    }
}
```

### TemplateDiff Record DTOs

```java
// Source: designed for CONTEXT.md D-05 diff-based storage
public record TemplateDiff(
        List<StepOverride> overrides,
        List<PathwayStep> additions,
        List<String> removals,
        EdgeChanges edgeChanges
) {
    public TemplateDiff {
        overrides = overrides != null ? overrides : List.of();
        additions = additions != null ? additions : List.of();
        removals = removals != null ? removals : List.of();
        edgeChanges = edgeChanges != null ? edgeChanges : new EdgeChanges(List.of(), List.of());
    }
}

public record StepOverride(
        String stepId,
        Map<String, Object> fields
) {}

public record EdgeChanges(
        List<EdgeRef> remove,
        List<EdgeRef> add
) {
    public EdgeChanges {
        remove = remove != null ? remove : List.of();
        add = add != null ? add : List.of();
    }
}

public record EdgeRef(String from, String to) {}
```

### Modified PathwayForkService

```java
// Source: extension of existing PathwayForkService.java pattern
@Transactional
public PatientPathway forkFromTemplate(Patient patient, UUID templateId, UUID actorId) {
    // 1. Load the selected template
    PathwayTemplate template = templateRepository.findById(templateId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No pathway template found with ID " + templateId));

    // 2. Resolve steps: merge if child, direct parse if root
    List<PathwayStep> templateSteps;
    try {
        if (template.getParentTemplateId() != null) {
            // Child template -- load parent and merge
            PathwayTemplate parent = templateRepository.findById(template.getParentTemplateId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Parent template not found: " + template.getParentTemplateId()));
            List<PathwayStep> parentSteps = objectMapper.readValue(
                    parent.getTemplateData(), new TypeReference<List<PathwayStep>>() {});
            TemplateDiff diff = objectMapper.readValue(
                    template.getTemplateData(), TemplateDiff.class);
            templateSteps = templateMergeService.merge(parentSteps, diff);
        } else {
            // Root template -- parse directly (existing behavior)
            templateSteps = objectMapper.readValue(
                    template.getTemplateData(), new TypeReference<List<PathwayStep>>() {});
        }
    } catch (Exception e) {
        throw new IllegalStateException(
                "Failed to parse template data for template " + template.getId(), e);
    }

    // 3-5: Existing deep-copy logic (unchanged)
    // ... create PatientPathway, deep-copy steps, deep-copy edges ...
}
```

### Frontend Template Listing Hook

```typescript
// Source: follows existing TanStack Query pattern in api.ts
export interface PathwayTemplateResponse {
  id: string;
  cancerType: 'BREAST' | 'LUNG' | 'COLORECTAL';
  name: string;
  description: string | null;
  parentTemplateId: string | null;
  version: number;
}

export function usePathwayTemplates(cancerType: string | null) {
  return useQuery({
    queryKey: ['pathway-templates', cancerType],
    queryFn: () => apiClient.get<PathwayTemplateResponse[]>(
      `/pathway-templates?cancerType=${encodeURIComponent(cancerType!)}`
    ),
    enabled: !!cancerType,
    staleTime: 5 * 60 * 1000, // Templates change rarely
  });
}
```

### Flyway Migration: Schema Changes

```sql
-- V19__template_inheritance.sql
-- Phase 8: Add template inheritance support

-- 1. Drop UNIQUE constraint on cancer_type (allows multiple templates per cancer type)
ALTER TABLE pathway_templates DROP CONSTRAINT IF EXISTS pathway_templates_cancer_type_key;

-- 2. Add inheritance columns
ALTER TABLE pathway_templates ADD COLUMN parent_template_id UUID REFERENCES pathway_templates(id);
ALTER TABLE pathway_templates ADD COLUMN name VARCHAR(255);
ALTER TABLE pathway_templates ADD COLUMN description TEXT;

-- 3. Backfill name for existing root templates
UPDATE pathway_templates SET name = 'Breast Cancer Pathway' WHERE cancer_type = 'BREAST';
UPDATE pathway_templates SET name = 'Lung Cancer Pathway' WHERE cancer_type = 'LUNG';
UPDATE pathway_templates SET name = 'Colorectal Cancer Pathway' WHERE cancer_type = 'COLORECTAL';

-- 4. Make name NOT NULL after backfill
ALTER TABLE pathway_templates ALTER COLUMN name SET NOT NULL;

-- 5. Index for parent lookups
CREATE INDEX idx_pathway_templates_parent ON pathway_templates(parent_template_id);
CREATE INDEX idx_pathway_templates_cancer_type ON pathway_templates(cancer_type);

-- 6. Mirror changes on Envers audit table
ALTER TABLE pathway_templates_aud ADD COLUMN parent_template_id UUID;
ALTER TABLE pathway_templates_aud ADD COLUMN name VARCHAR(255);
ALTER TABLE pathway_templates_aud ADD COLUMN description TEXT;

-- 7. Grants
GRANT ALL ON pathway_templates TO onco_app;
```

### Flyway Migration: Rectal Child Template

```sql
-- V20__seed_rectal_template.sql
-- Phase 8: Seed rectal cancer child template

INSERT INTO pathway_templates (id, cancer_type, version, parent_template_id, name, description, template_data, created_at, updated_at, created_by)
VALUES (
    gen_random_uuid(),
    'COLORECTAL',
    1,
    (SELECT id FROM pathway_templates WHERE cancer_type = 'COLORECTAL' AND parent_template_id IS NULL),
    'Rectal Cancer Pathway',
    'Includes neoadjuvant chemoradiation before surgery',
    '{
        "overrides": [
            {
                "stepId": "CRC_03",
                "fields": {
                    "windowDays": 60,
                    "description": "Surgical resection performed after neoadjuvant chemoradiation.",
                    "alertText": "Surgery not yet performed within 60 days. Confirm neoadjuvant therapy completion status.",
                    "suggestedAction": "Verify chemoradiation completion and coordinate surgery scheduling with surgical team."
                }
            }
        ],
        "additions": [
            {
                "stepId": "RECTAL_01",
                "stepNumber": 3,
                "name": "Neoadjuvant Chemoradiation",
                "description": "Combined chemotherapy and radiation therapy before surgical resection. Standard for locally advanced rectal cancer.",
                "eventType": "RADIATION",
                "windowDays": 30,
                "anchorType": "PREVIOUS_STEP",
                "anchorStepId": null,
                "required": true,
                "alertText": "Neoadjuvant chemoradiation not started within 30 days of staging workup.",
                "suggestedAction": "Coordinate with radiation oncology for treatment planning and scheduling.",
                "prerequisites": ["CRC_02"]
            }
        ],
        "removals": [],
        "edgeChanges": {
            "remove": [
                { "from": "CRC_01", "to": "CRC_03" }
            ],
            "add": [
                { "from": "CRC_01", "to": "RECTAL_01" },
                { "from": "RECTAL_01", "to": "CRC_03" }
            ]
        }
    }'::jsonb,
    NOW(),
    NOW(),
    '00000000-0000-0000-0000-000000000000'
);
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Single template per cancer type (UNIQUE constraint) | Multiple templates per cancer type with parent/child | Phase 8 | Enables clinical pathway variants without enum proliferation |
| Template lookup by cancer type only | Template lookup by ID (selected from variant list) | Phase 8 | Fork service accepts explicit template ID instead of deriving from cancer type |
| Static template names from CancerType enum display | Explicit `name` and `description` columns on template | Phase 8 | Templates are self-describing; display names independent of enum values |

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Added steps use stepIds prefixed with child domain (e.g., RECTAL_01) | Pattern 1: Diff Structure | Low -- stepId naming is a convention, any unique string works. If collision occurs, merge produces duplicate stepIds which would cause edge remapping errors in fork. |
| A2 | Merge algorithm order (removals -> overrides -> additions -> edge changes) is correct | Pattern 2: Merge Engine | Medium -- if edge changes must be processed before additions (e.g., edges reference parent steps that are being rearranged), the order would need adjustment. The proposed order handles the rectal use case correctly. |
| A3 | RADIATION CareEventType is appropriate for neoadjuvant chemoradiation | Rectal Template Seed | Low -- chemoradiation combines chemo + radiation, but RADIATION is the closest existing event type. The oncologist has not specified a preference. Creating a CHEMORADIATION event type would require a PostgreSQL enum ALTER and is likely overengineering for V1. |
| A4 | The rectal child template needs only one added step (RECTAL_01 neoadjuvant chemoradiation) and one override (CRC_03 surgery timing) | Rectal Template Seed | Medium -- the oncologist's PW-CR-004 answer was brief ("B" = separate pathways). The actual clinical content of the rectal pathway may require additional modifications. However, the inheritance system is general-purpose, so additional diff entries can be added. |
| A5 | Flyway migration version numbers V19 and V20 are correct (next after V18) | Code Examples | Low -- if other branches add V19 first, renumbering is needed. Verify at implementation time. |

## Open Questions

1. **PW-CR-005: Split pathology from MSI testing**
   - What we know: The oncologist answered "B" (split them into separate steps). This affects the colorectal ROOT template, not just the rectal child.
   - What's unclear: Should this split happen in Phase 8 or was it already handled? The current CRC_04 step is still "Pathology and MSI/MMR Testing" as a single combined step. The CONTEXT.md specifics section mentions it but does not include it in the locked decisions.
   - Recommendation: Include the CRC_04 split in the Phase 8 Flyway migration since we are already modifying the colorectal template. This is a root template content change (split CRC_04 into CRC_04a "Surgical Pathology" and CRC_04b "MSI/MMR Testing"), not an inheritance feature. If the planner deems it out of scope, it can be deferred, but the migration is the right vehicle.

2. **Rectal pathway clinical accuracy**
   - What we know: Rectal cancer requires neoadjuvant chemoradiation BEFORE surgery (PW-CR-004). The rectal template reverses the surgery ordering.
   - What's unclear: Exactly how many neoadjuvant steps are needed (one combined "Chemoradiation" step vs. separate "Chemo" and "Radiation" steps), and what the correct time windows are.
   - Recommendation: Start with one combined step (RECTAL_01 "Neoadjuvant Chemoradiation") using RADIATION event type. The oncologist co-author can refine the content after the system is working. The inheritance mechanism is what Phase 8 delivers -- the clinical content is secondary.

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | Existing Keycloak JWT -- no changes |
| V3 Session Management | No | Existing session management -- no changes |
| V4 Access Control | Yes | New `PathwayTemplateController` must enforce role-based access. Templates are non-PHI configuration data, but write access (future ADV-01) should be ADMIN-only. Read access for all clinical roles. |
| V5 Input Validation | Yes | `cancerType` query param must be validated as a valid CancerType enum value. `templateId` in CreatePatientRequest must be validated as existing UUID. |
| V6 Cryptography | No | Templates contain no PHI -- no encryption needed |

### Known Threat Patterns for This Phase

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Invalid templateId in patient creation request | Tampering | Validate templateId exists via `findById()` before fork. Return 404 if not found. |
| BOLA: accessing templates from other cancer types | Information Disclosure | Templates are non-PHI, shared configuration. No per-user isolation needed. All authenticated clinical roles can read all templates. |
| Mass assignment on template fields via diff | Tampering | N/A for V1 -- child templates are seed data only (no admin UI). When ADV-01 adds template admin UI, validate diff structure server-side. |

## Project Constraints (from CLAUDE.md)

- **HIPAA Compliance:** Templates are non-PHI (clinical protocol data, not patient-specific). No encryption needed on template fields. `@Audited` annotation already on `PathwayTemplate` -- new columns are automatically captured. [VERIFIED: PathwayTemplate.java line 37]
- **Human-in-the-Loop:** Template selection is a nurse decision in the enrollment wizard. The system does not auto-select child templates based on clinical data. [VERIFIED: CONTEXT.md D-07, D-08]
- **No PHI in logs:** Template operations log template UUIDs and cancer type only -- no patient data. [VERIFIED: consistent with existing log patterns]
- **Tech Stack:** Java 21 + Spring Boot 3, PostgreSQL, React + TypeScript. No deviations. [VERIFIED: CLAUDE.md]
- **Flyway for migrations:** All schema changes via versioned SQL. [VERIFIED: 18 existing migrations]
- **Hibernate Envers `@Audited`:** PathwayTemplate already annotated. New columns auto-captured in `_AUD` table (after Flyway adds them). [VERIFIED: PathwayTemplate.java]
- **TanStack Query for frontend data fetching:** New template listing uses `useQuery`. No `useEffect` + `fetch`. [VERIFIED: CLAUDE.md "What NOT to Use" section]
- **shadcn RadioGroup for selection UI:** TemplatePicker already uses this. Variant picker extends the same pattern. [VERIFIED: TemplatePicker.tsx]

## Sources

### Primary (HIGH confidence)
- `PathwayTemplate.java` -- current entity with `@Audited`, UNIQUE on cancer_type, JSONB templateData
- `PathwayForkService.java` -- existing fork logic, deep-copy pattern, only caller of `findByCancerType()`
- `PathwayTemplateRepository.java` -- current `Optional<PathwayTemplate> findByCancerType()` signature
- `V1__create_base_schema.sql` -- `cancer_type cancer_type NOT NULL UNIQUE` constraint definition
- `V6__seed_pathway_templates.sql` -- current template seed data with `gen_random_uuid()` IDs
- `V14__create_per_patient_pathway_tables.sql` -- per-patient DAG schema, `source_template_id` FK
- `PatientService.java` -- patient creation flow, fork service integration
- `TemplatePicker.tsx` -- current simple radio group implementation
- `PatientWizard.tsx` -- current 2-step wizard with cancer type selection and pathway mode
- `CreatePatientRequest.java` -- current DTO with `pathwayMode` field
- `api.ts` -- existing TanStack Query hooks pattern
- `types.ts` -- existing TypeScript type definitions
- `08-CONTEXT.md` -- all locked decisions D-01 through D-09

### Secondary (MEDIUM confidence)
- `Pathway-Template-Review-Worksheet.md` -- PW-CR-004 oncologist answer ("B" = separate colon/rectal), PW-CR-005 ("B" = split pathology/MSI)
- `05-CONTEXT.md` -- Phase 5 D-05 (deep copy, version-locked fork), D-07 (template picker)

### Tertiary (LOW confidence)
- Rectal cancer neoadjuvant clinical content (step names, time windows, event types) -- based on general oncology knowledge, not verified with the oncologist co-author [ASSUMED]

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- no new dependencies, all existing libraries
- Architecture: HIGH -- extends well-understood existing patterns (JPA entity, JSONB, Flyway, TanStack Query)
- Merge engine design: HIGH -- algorithm is straightforward for single-level inheritance with 4 operations
- Rectal template clinical content: MEDIUM -- clinical accuracy should be reviewed by oncologist
- Pitfalls: HIGH -- based on verified codebase analysis (Envers _AUD table, UNIQUE constraint, Flyway ID generation)

**Research date:** 2026-05-05
**Valid until:** 2026-06-05 (stable -- no rapidly evolving dependencies)
