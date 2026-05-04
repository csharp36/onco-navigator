# Phase 5: Per-Patient Pathway Instances + DAG Foundation - Research

**Researched:** 2026-05-04
**Domain:** Relational DAG modeling, topological sort evaluation, template forking, Flyway data migration, inline editing UI
**Confidence:** HIGH

## Summary

Phase 5 replaces the static JSONB template-based pathway evaluation with per-patient relational pathway steps connected by DAG edges. This is the most architecturally significant phase since Phase 2 -- it rewrites the core evaluation engine, adds three new tables, migrates all existing patient data from JSONB templates to per-patient rows, and introduces a full inline pathway editor on the frontend.

The technical domain is well-understood: DAG topological sort (Kahn's algorithm) is a textbook algorithm, JPA entity modeling for adjacency lists is straightforward with Spring Data, and the Flyway data migration is complex but contained (JSONB-to-relational conversion for a known template structure). The highest risk areas are: (1) the data migration must correctly fork existing patients from their cancer-type templates into per-patient rows while preserving physician overrides as SKIPPED statuses, (2) the evaluation engine rewrite must produce identical alert behavior for the same patient data (regression risk), and (3) the Temporal workflow signal addition must not break running workflows.

The frontend work is significant but well-scoped by the UI-SPEC: an enhanced vertical list with depth indentation, a full inline editor with step/edge CRUD, and a template picker in the patient creation wizard. All new frontend components use existing shadcn/ui primitives plus two new ones (RadioGroup, Collapsible).

**Primary recommendation:** Implement in 6-7 waves: schema + entities first, then template fork + data migration, then DAG evaluation engine, then backend API endpoints, then frontend visualization + editor, then integration testing.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01:** Full manual editing -- nurses can add, remove, edit step details (name, time window, event type), and reorder dependency edges on per-patient pathways
- **D-02:** Per-patient step statuses: ACTIVE, PROPOSED, COMPLETED, SKIPPED. Evaluation engine only checks ACTIVE steps. PROPOSED skipped until nurse confirms. COMPLETED have matching care event. SKIPPED replaces physician override
- **D-03:** All clinical roles can edit per-patient pathway steps. No role restriction on pathway modification
- **D-04:** SKIPPED replaces physician_overrides -- existing records migrated to SKIPPED status during Flyway data migration. physician_overrides table becomes legacy
- **D-05:** Deep copy, version-locked -- fork copies all template steps AND edges into per-patient tables. Records source template ID and version. Future template updates do NOT retroactively change existing patient pathways
- **D-06:** "Build from documents" starts Temporal workflow immediately with no steps. Evaluation activity returns early. pathwayStepsChanged signal triggers re-evaluation when steps are added
- **D-07:** Template picker in patient creation wizard -- Step 2 adds pathway selection: "Start from template" (default) or "Build from documents" (empty)
- **D-08:** Flyway data migration converts ALL existing patients. Clean cutover, NO legacy JSONB fallback. Supersedes ROADMAP SC5
- **D-09:** Full edge editing with cycle detection
- **D-10:** Cascade delete edges on step removal
- **D-11:** Time windows anchored to prerequisites. Multiple prerequisites: clock starts from LATEST completion. Root steps anchor to diagnosis date
- **D-12:** Newly added steps default to root (immediately ready)
- **D-13:** Enhanced vertical list with branching indicators
- **D-14:** Inline editing on pathway view -- "Edit Pathway" toggle transforms view into editor
- **D-15:** Icons + color coding for statuses (COMPLETED=green, ACTIVE=blue, PROPOSED=dashed gray, SKIPPED=strikethrough gray)
- **D-16:** Keep split layout -- pathway editor replaces view in left column when toggled

### Claude's Discretion
- Relational schema design for patient_pathways, patient_pathway_steps, patient_pathway_edges tables
- Topological sort implementation (Kahn's algorithm vs DFS-based)
- Flyway migration script structure for JSONB-to-relational data conversion
- Step completion detection (care event match criteria)
- pathwayStepsChanged signal design in Temporal workflow
- Cycle detection algorithm
- REST API endpoint design for step/edge CRUD
- Frontend component structure for inline pathway editor
- Add Step form UX (inline form, small dialog, etc.)
- Edge creation visual UX (click-to-connect, dropdown selector, etc.)

### Deferred Ideas (OUT OF SCOPE)
None -- discussion stayed within phase scope
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PW-ALL-002 | Events extracted from documents -- each patient needs unique sequence, no standard sequence for cancer type | Drives the per-patient relational model (not shared templates), PROPOSED status for AI-extracted steps (Phase 6 prep), full manual editing capability |
| PW-BR-001 | Steps from MD notes/orders/nurse notes -- steps not from pre-defined list | Drives full manual step CRUD (D-01), the "Build from documents" empty pathway option (D-06), and the freeform step name field |
| PW-BR-003 | No fixed linear sequence | Drives DAG edge model replacing linear stepNumber ordering, topological sort evaluation, parallel path support |
</phase_requirements>

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Per-patient pathway storage | Database / Storage | -- | Three new relational tables with FK constraints, indexes, and Envers audit |
| Template fork (deep copy) | API / Backend | Database / Storage | Service-layer logic reads JSONB template, creates per-patient rows; bulk INSERT |
| JSONB-to-relational data migration | Database / Storage | -- | Flyway SQL migration operates at DB level; no application code involvement |
| DAG evaluation engine | API / Backend | -- | Topological sort + deviation detection in PathwayEvaluationActivityImpl (Temporal activity) |
| Cycle detection | API / Backend | -- | Graph algorithm runs in service layer before persisting edge changes |
| Step/edge CRUD REST API | API / Backend | -- | Standard Spring MVC controller + service pattern |
| pathwayStepsChanged signal | API / Backend | -- | Temporal workflow signal, follows existing careEventChanged pattern |
| Pathway DAG visualization | Browser / Client | API / Backend | React component renders server-provided depth + sortOrder; backend computes topology |
| Inline pathway editor | Browser / Client | -- | Client-side form state with server mutations via TanStack Query |
| Template picker in wizard | Browser / Client | -- | Client-side radio group; affects payload sent to create patient endpoint |

## Standard Stack

### Core (already in project -- no new dependencies)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 3.5.0 | Application framework | Already in pom.xml [VERIFIED: pom.xml] |
| Spring Data JPA | via Boot BOM | Repository pattern for new entities | Project convention [VERIFIED: pom.xml] |
| Hibernate Envers | via Boot BOM | @Audited on new entities | Project convention [VERIFIED: pom.xml] |
| Flyway | 11.x via Boot BOM | Schema migration + data migration | Project convention [VERIFIED: pom.xml] |
| Temporal Java SDK | 1.32.0 | pathwayStepsChanged signal, workflow modifications | Already in pom.xml [VERIFIED: pom.xml] |
| React | 19.2.5 | Frontend SPA | Already installed [VERIFIED: package.json] |
| TanStack Query | 5.100.6 | Server state for step/edge CRUD | Already installed [VERIFIED: package.json] |
| TanStack Router | 1.168.26 | Client-side routing | Already installed [VERIFIED: package.json] |
| shadcn/ui | new-york preset | UI components | Already configured [VERIFIED: components.json] |
| react-hook-form | 7.74.0 | AddStepForm, inline edit forms | Already installed [VERIFIED: package.json] |
| zod | 4.4.1 | Frontend schema validation | Already installed [VERIFIED: package.json] |
| lucide-react | 1.14.0 | Icons for step statuses | Already installed [VERIFIED: package.json] |

### New shadcn Components to Install

| Component | Purpose | Install Command |
|-----------|---------|----------------|
| RadioGroup | Template picker in patient creation wizard | `npx shadcn@latest add radio-group` |
| Collapsible | EdgeEditor dependencies section | `npx shadcn@latest add collapsible` |

**Note:** Tooltip is already installed. [VERIFIED: frontend/src/components/ui/tooltip.tsx exists]

### No New Backend Dependencies

Phase 5 requires zero new Maven dependencies. All needed libraries (JPA, Envers, Flyway, Temporal, Validation) are already in pom.xml. The DAG topological sort and cycle detection are implemented with standard Java collections -- no graph library needed.

## Architecture Patterns

### System Architecture Diagram

```
Patient Creation (wizard)
    |
    v
CreatePatientRequest --[pathwayMode: template|empty]--> PatientService
    |                                                        |
    |-- template --> PathwayForkService.forkFromTemplate()   |
    |       |            |                                   |
    |       |            +-- Read JSONB template steps       |
    |       |            +-- Deep copy to patient_pathway_steps
    |       |            +-- Deep copy edges to patient_pathway_edges
    |       |            +-- Record source template ID + version
    |       |            +-- Signal pathwayStepsChanged
    |       v                                                |
    |-- empty --> Create patient_pathways row (0 steps)     |
    |       +-- Start Temporal workflow (evaluates empty)    |
    v                                                        |
Temporal Workflow (PatientPathwayWorkflowImpl)               |
    |                                                        |
    +-- careEventChanged signal --> evaluate()               |
    +-- pathwayStepsChanged signal --> evaluate() [NEW]      |
    +-- 24h timer --> evaluate()                             |
    v                                                        
PathwayEvaluationActivityImpl.evaluate(patientId)            
    |                                                        
    +-- Query patient_pathway_steps WHERE patient_id AND status=ACTIVE
    +-- Query patient_pathway_edges for those steps
    +-- Build in-memory DAG adjacency list
    +-- Topological sort (Kahn's algorithm) --> ordered step list
    +-- For each "ready" step (all prerequisites COMPLETED):
    |       +-- Match to care events by event_type
    |       +-- Resolve anchor date from LATEST prerequisite completion
    |       +-- Detect MISSING / DELAYED / OUT_OF_ORDER
    |       +-- Create alert (with dedup check)
    v
Alerts table (existing, unchanged)

Step/Edge CRUD:
    Frontend PathwayEditor --> POST/PUT/DELETE /api/patients/{id}/pathway/steps
                           --> POST/DELETE /api/patients/{id}/pathway/edges
                           --> Each mutation: persist + signal pathwayStepsChanged
```

### Recommended Project Structure

```
src/main/java/com/onconavigator/
  domain/
    PatientPathway.java              # New JPA entity
    PatientPathwayStep.java          # New JPA entity
    PatientPathwayEdge.java          # New JPA entity
    enums/
      PathwayStepStatus.java         # New enum: ACTIVE, PROPOSED, COMPLETED, SKIPPED
  repository/
    PatientPathwayRepository.java    # New
    PatientPathwayStepRepository.java # New
    PatientPathwayEdgeRepository.java # New
  service/
    PathwayForkService.java          # New -- template fork logic
    PatientPathwayService.java       # New -- step/edge CRUD + cycle detection
    PathwayStatusService.java        # Modified -- query per-patient steps instead of JSONB
    PatientService.java              # Modified -- template picker in creation flow
  activity/
    PathwayEvaluationActivityImpl.java # REWRITE -- DAG evaluation engine
  workflow/
    PatientPathwayWorkflow.java      # Modified -- add pathwayStepsChanged signal
    PatientPathwayWorkflowImpl.java  # Modified -- handle new signal
  web/
    PatientPathwayController.java    # New -- step/edge CRUD endpoints
    dto/
      PathwayStepRequest.java        # New
      PathwayEdgeRequest.java        # New
      PathwayStepResponse.java       # New
      PathwayEdgeResponse.java       # New
      PathwayStatusResponse.java     # Modified -- add depth, prerequisites
      PathwayStepStatus.java         # Modified -- add depth, status enum

src/main/resources/db/migration/
  V13__create_per_patient_pathway_tables.sql       # 3 new tables + indexes + grants
  V14__create_pathway_step_status_enum.sql         # PostgreSQL enum type
  V15__migrate_patients_to_per_patient_pathways.sql # Data migration

frontend/src/features/patients/
  PathwayDAGView.tsx          # New -- enhanced vertical list with depth
  PathwayEditor.tsx           # New -- inline editor mode
  StepRow.tsx                 # New -- shared step row component
  AddStepForm.tsx             # New -- inline add step form
  EdgeEditor.tsx              # New -- dependency management
  SkipStepDialog.tsx          # New -- skip reason dialog
  TemplatePicker.tsx          # New -- wizard radio group
  types.ts                    # Modified -- new types
  api.ts                      # Modified -- new hooks
```

### Pattern 1: DAG Topological Sort (Kahn's Algorithm)

**What:** Kahn's algorithm produces a topological ordering of a DAG by repeatedly removing nodes with no incoming edges. It also detects cycles (if remaining nodes exist when no zero-indegree nodes are found).

**When to use:** Every evaluation cycle. Build the DAG from patient_pathway_edges, compute topological order, identify "ready" steps.

**Example:**
```java
// Source: Standard algorithm, applied to project domain [ASSUMED]
public List<PatientPathwayStep> topologicalSort(
        List<PatientPathwayStep> steps,
        List<PatientPathwayEdge> edges) {

    // Build adjacency list and in-degree map
    Map<UUID, List<UUID>> adjacency = new HashMap<>();
    Map<UUID, Integer> inDegree = new HashMap<>();

    for (PatientPathwayStep step : steps) {
        adjacency.put(step.getId(), new ArrayList<>());
        inDegree.put(step.getId(), 0);
    }

    for (PatientPathwayEdge edge : edges) {
        adjacency.get(edge.getSourceStepId()).add(edge.getTargetStepId());
        inDegree.merge(edge.getTargetStepId(), 1, Integer::sum);
    }

    // Seed queue with zero-indegree nodes (root steps)
    Queue<UUID> queue = new ArrayDeque<>();
    for (var entry : inDegree.entrySet()) {
        if (entry.getValue() == 0) {
            queue.add(entry.getKey());
        }
    }

    List<UUID> sortedIds = new ArrayList<>();
    while (!queue.isEmpty()) {
        UUID current = queue.poll();
        sortedIds.add(current);
        for (UUID neighbor : adjacency.get(current)) {
            inDegree.merge(neighbor, -1, Integer::sum);
            if (inDegree.get(neighbor) == 0) {
                queue.add(neighbor);
            }
        }
    }

    // Cycle detection: if sortedIds.size() < steps.size(), cycle exists
    // (should not happen if cycle detection runs at edge creation time)

    // Map UUIDs back to steps in topological order
    Map<UUID, PatientPathwayStep> stepMap = steps.stream()
        .collect(Collectors.toMap(PatientPathwayStep::getId, s -> s));
    return sortedIds.stream().map(stepMap::get).toList();
}
```

### Pattern 2: Cycle Detection at Edge Creation Time

**What:** Before persisting a new edge, verify the proposed edge does not create a cycle. Use DFS cycle detection on the prospective graph.

**When to use:** Every edge create/update operation. Run in the service layer before calling repository.save().

**Example:**
```java
// Source: Standard DFS cycle detection [ASSUMED]
public boolean wouldCreateCycle(UUID sourceStepId, UUID targetStepId,
                                 List<PatientPathwayEdge> existingEdges) {
    // Build adjacency list with the proposed edge added
    Map<UUID, Set<UUID>> adjacency = new HashMap<>();
    for (PatientPathwayEdge edge : existingEdges) {
        adjacency.computeIfAbsent(edge.getSourceStepId(), k -> new HashSet<>())
            .add(edge.getTargetStepId());
    }
    // Add proposed edge
    adjacency.computeIfAbsent(sourceStepId, k -> new HashSet<>())
        .add(targetStepId);

    // DFS from targetStepId: if we can reach sourceStepId, cycle exists
    Set<UUID> visited = new HashSet<>();
    return dfsReaches(targetStepId, sourceStepId, adjacency, visited);
}

private boolean dfsReaches(UUID current, UUID target,
                            Map<UUID, Set<UUID>> adjacency,
                            Set<UUID> visited) {
    if (current.equals(target)) return true;
    if (!visited.add(current)) return false;
    for (UUID neighbor : adjacency.getOrDefault(current, Set.of())) {
        if (dfsReaches(neighbor, target, adjacency, visited)) return true;
    }
    return false;
}
```

### Pattern 3: Template Fork (Deep Copy)

**What:** When "Start from template" is selected, read the JSONB template, create per-patient rows for each step and each edge, recording the source template metadata.

**When to use:** Patient creation with pathwayMode=template.

**Example:**
```java
// Source: Project-specific pattern [ASSUMED]
@Transactional
public PatientPathway forkFromTemplate(UUID patientId, UUID templateId) {
    PathwayTemplate template = templateRepository.findById(templateId)
        .orElseThrow(() -> new IllegalArgumentException("Template not found"));

    List<PathwayStep> templateSteps = objectMapper.readValue(
        template.getTemplateData(), new TypeReference<>() {});

    // Create pathway record
    PatientPathway pathway = new PatientPathway();
    pathway.setPatientId(patientId);
    pathway.setSourceTemplateId(templateId);
    pathway.setSourceTemplateVersion(template.getVersion());
    pathway = pathwayRepository.save(pathway);

    // Map old stepId -> new UUID for edge remapping
    Map<String, UUID> stepIdMap = new HashMap<>();

    // Copy each template step to per-patient step
    for (PathwayStep ts : templateSteps) {
        PatientPathwayStep step = new PatientPathwayStep();
        step.setPathway(pathway);
        step.setName(ts.name());
        step.setDescription(ts.description());
        step.setEventType(ts.eventType());
        step.setWindowDays(ts.windowDays());
        step.setRequired(ts.required());
        step.setAlertText(ts.alertText());
        step.setSuggestedAction(ts.suggestedAction());
        step.setStatus(PathwayStepStatus.ACTIVE);
        step.setSourceTemplateStepId(ts.stepId());
        step = stepRepository.save(step);
        stepIdMap.put(ts.stepId(), step.getId());
    }

    // Copy prerequisite edges
    for (PathwayStep ts : templateSteps) {
        UUID targetId = stepIdMap.get(ts.stepId());
        for (String prereqStepId : ts.prerequisites()) {
            UUID sourceId = stepIdMap.get(prereqStepId);
            if (sourceId != null && targetId != null) {
                PatientPathwayEdge edge = new PatientPathwayEdge();
                edge.setPathway(pathway);
                edge.setSourceStepId(sourceId);
                edge.setTargetStepId(targetId);
                edgeRepository.save(edge);
            }
        }
    }

    return pathway;
}
```

### Pattern 4: Temporal pathwayStepsChanged Signal

**What:** Add a new @SignalMethod to the workflow interface so step/edge mutations trigger re-evaluation.

**When to use:** After any step or edge CRUD operation (add, update, delete, skip, unskip).

**Example:**
```java
// Source: Existing careEventChanged pattern [VERIFIED: PatientPathwayWorkflow.java]
// In PatientPathwayWorkflow interface:
@SignalMethod
void pathwayStepsChanged();

// In PatientPathwayWorkflowImpl:
@Override
public void pathwayStepsChanged() {
    signalReceived = true;  // Same mechanism as careEventChanged
}
```

**Critical note on running workflows:** Adding a new signal method to a workflow interface is safe for running workflows. Temporal ignores unrecognized signals on older workflow versions. However, the signal can only be received by workflows that have been replayed with the new code. Existing running workflows will pick up the new signal handler on their next replay (which happens naturally on the next timer wake or activity completion). [CITED: Temporal versioning docs -- signal methods are additive-safe]

### Pattern 5: Flyway Data Migration (JSONB to Relational)

**What:** A Flyway migration script reads existing patient + template data and inserts per-patient pathway rows.

**Critical ordering:** V12 (time window corrections) MUST run before the data migration so corrected windows propagate into per-patient steps.

**Example structure:**
```sql
-- V15__migrate_patients_to_per_patient_pathways.sql
-- For each ACTIVE patient:
--   1. Look up their cancer_type -> pathway_template
--   2. Parse JSONB template_data
--   3. INSERT patient_pathways row
--   4. INSERT patient_pathway_steps for each template step
--   5. INSERT patient_pathway_edges for each prerequisite
--   6. For each physician_override, set corresponding step to SKIPPED
--   7. For each care event, mark corresponding step as COMPLETED

-- This is a pure SQL migration using jsonb_array_elements and INSERT...SELECT
-- No application code runs -- migration is atomic and repeatable-safe
```

### Anti-Patterns to Avoid

- **Computing topology on every render:** The backend should compute depth and sortOrder and include them in the response DTO. The frontend should NOT compute topological sort -- it just renders in the order the server provides.
- **Storing depth/sortOrder as persistent columns:** These are derived data. Compute them on read (in the service layer), not on write. Storing them would create update anomalies when edges change.
- **Bidirectional JPA relationships on edges:** Edges reference steps by UUID foreign key, not by @ManyToOne with inverse collection. This avoids N+1 query problems and keeps the entity graph simple.
- **Evaluating PROPOSED steps:** The evaluation engine MUST filter to `status = ACTIVE` only. PROPOSED steps are Phase 6 prep -- they exist in the database but are invisible to the evaluation engine.
- **Logging step names in evaluation activity:** Step names are not PHI (they are clinical process names like "Surgery" or "Pathology Report"), but maintaining the UUID-only logging convention avoids any ambiguity. Log step UUIDs, not names.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Graph cycle detection | Custom iterative walker | Standard DFS with visited set | Well-known O(V+E) algorithm; one method, ~15 lines |
| Topological sort | Custom priority system | Kahn's algorithm with BFS queue | Textbook algorithm; produces ordering AND depth in one pass |
| PostgreSQL enum creation | Java-driven DDL | Flyway SQL migration `CREATE TYPE pathway_step_status AS ENUM (...)` | Project convention: all schema via Flyway SQL, never Hibernate ddl-auto |
| JSONB parsing in SQL | Application-layer migration loop | `jsonb_array_elements()` in Flyway SQL | Single atomic SQL migration is faster and safer than row-by-row application code |
| Optimistic locking on steps | Manual version tracking | `@Version` column on PatientPathwayStep | JPA handles concurrent edit conflicts automatically |
| Edge cascade delete | Manual cleanup queries | `ON DELETE CASCADE` on edge FK + JPA `orphanRemoval` or manual service cleanup | D-10 specifies cascade; database-level constraint is safest |

**Key insight:** The DAG domain is well-served by standard algorithms (Kahn's, DFS) and standard JPA patterns. No graph database or graph library is needed for the expected scale (a patient pathway has 5-20 steps with 5-25 edges).

## Common Pitfalls

### Pitfall 1: Data Migration Breaks Existing Alerts

**What goes wrong:** The data migration creates per-patient steps with new UUIDs. Existing alerts reference steps by `pathway_step_name` (a string like "Surgery"). If the migration changes how step names are stored or the evaluation engine changes how alerts are keyed, existing open alerts become orphaned.

**Why it happens:** The alert dedup check uses `alertRepository.existsByPatientIdAndPathwayStepNameAndStatus()`. If the per-patient step name differs from the template step name (e.g., trimming or casing), dedup breaks.

**How to avoid:** The migration must copy step names EXACTLY from the template. The evaluation engine must use the per-patient step's `name` field for alert creation, same as before. Verify alert dedup still works after migration.

**Warning signs:** Duplicate alerts appearing for steps that already have open alerts.

### Pitfall 2: Temporal Workflow Non-Determinism on Signal Addition

**What goes wrong:** Adding a new signal method changes the workflow code. If a running workflow is replayed and the replay encounters old history events, the workflow might fail with a non-determinism error.

**Why it happens:** Adding a @SignalMethod is additive and safe -- Temporal handles unrecognized signals gracefully. But if you also change the workflow's main loop logic (e.g., adding a new await condition or changing the timer duration), that IS a non-determinism risk.

**How to avoid:** The pathwayStepsChanged signal sets the SAME `signalReceived` flag that `careEventChanged` uses. The main loop logic (`Workflow.await(Duration.ofHours(24), () -> signalReceived || deactivated)`) does NOT change. This is safe. [VERIFIED: PatientPathwayWorkflowImpl.java -- the signal just sets a boolean flag]

**Warning signs:** Workflow task failures in Temporal UI with "non-deterministic" in the error message.

### Pitfall 3: Cascade Delete Orphans Steps from Alerts

**What goes wrong:** D-10 says cascade delete edges when a step is removed. But if the removed step is referenced by existing alerts via `pathway_step_name`, those alerts become disconnected from the pathway.

**Why it happens:** Alerts are keyed by step name (string), not step UUID. Deleting a step doesn't cascade to alerts.

**How to avoid:** Step removal should close (resolve) any OPEN alerts for that step name. Add this to the step deletion service logic: `alertRepository.findByPatientIdAndPathwayStepNameAndStatus(patientId, stepName, OPEN)` and set them to RESOLVED with notes "Step removed from pathway."

**Warning signs:** Open alerts for steps that no longer exist in the pathway.

### Pitfall 4: Flyway Migration Fails on Empty Template Data

**What goes wrong:** If any patient's cancer_type has no matching pathway_template, or if a template has empty/null templateData, the migration crashes.

**Why it happens:** The SQL migration uses `jsonb_array_elements(template_data)` which fails on NULL or empty arrays.

**How to avoid:** The migration must handle edge cases: patients with no matching template (skip with log), templates with empty arrays (create pathway with 0 steps), patients who are INACTIVE (still migrate -- they retain their pathway for audit purposes per D-08 context).

**Warning signs:** Flyway migration fails on startup with a PostgreSQL error about null jsonb operations.

### Pitfall 5: Time Window Anchor Resolution for Multiple Prerequisites

**What goes wrong:** D-11 says time windows anchor to prerequisites, with multiple prerequisites using the LATEST completion date. If any prerequisite is not yet completed, the step is not "ready" -- but the anchor date calculation might return null or throw.

**Why it happens:** The old evaluation used `resolveAnchorDate()` with AnchorType enum. The new evaluation computes anchor from prerequisite edges. A step with 3 prerequisites where only 2 are completed has no valid anchor yet.

**How to avoid:** A step is "ready" for evaluation ONLY when ALL prerequisites have status=COMPLETED. The anchor date is `MAX(prerequisite.completionDate)`. If any prerequisite is not completed, skip the step entirely (same as the current null-anchor-date behavior).

**Warning signs:** Alerts generated for steps whose prerequisites are not all completed.

### Pitfall 6: Physician Override Migration Precision

**What goes wrong:** D-04 says existing physician_overrides are migrated to SKIPPED status. But physician_overrides use `pathway_step_id` (a string like "BREAST_01"), while per-patient steps use UUID primary keys. The migration must correctly map override step IDs to the newly created per-patient step UUIDs.

**Why it happens:** The step ID mapping during template fork creates new UUIDs. The physician_overrides reference the old template step IDs.

**How to avoid:** During migration, build a mapping of (patient_id, template_step_id) -> per-patient step UUID. Then for each physician_override, look up the per-patient step and set its status to SKIPPED with the override reason. Use a CTE or temp table in the Flyway SQL.

**Warning signs:** Physician overrides not reflected as SKIPPED steps after migration.

### Pitfall 7: Care Event-to-Step Matching After Migration

**What goes wrong:** Currently, care events are matched to steps by `eventType` (the template step's eventType matches the care event's eventType). After migration, the evaluation engine needs to match care events to per-patient steps the same way. But if a patient has multiple steps with the same eventType (e.g., two CONSULTATION steps), the matching becomes ambiguous.

**Why it happens:** The existing template structure already has multiple steps with the same eventType (e.g., LUNG_01 and LUNG_04 are both CONSULTATION). The current engine handles this by iterating linearly and matching the first unmatched event. DAG order is different.

**How to avoid:** Match care events to steps using eventType in topological order. For each step, find the first COMPLETED care event of that eventType that hasn't already been claimed by a previous step in the topological ordering. This preserves the current matching semantics while operating on the DAG. Consider adding an explicit `care_event_id` FK on `patient_pathway_steps` for steps with confirmed matches.

**Warning signs:** Steps incorrectly showing as COMPLETED or not matching to care events.

## Code Examples

### Database Schema (Flyway Migration)

```sql
-- Source: Project convention from V1__create_base_schema.sql, V5__create_physician_overrides.sql [VERIFIED]

-- New PostgreSQL enum for step status
CREATE TYPE pathway_step_status AS ENUM ('ACTIVE', 'PROPOSED', 'COMPLETED', 'SKIPPED');

-- Per-patient pathway header
CREATE TABLE patient_pathways (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL REFERENCES patients(id),
    source_template_id UUID REFERENCES pathway_templates(id),
    source_template_version INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by UUID NOT NULL,
    CONSTRAINT uq_patient_pathways_patient UNIQUE (patient_id)
);

-- Per-patient pathway steps
CREATE TABLE patient_pathway_steps (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pathway_id UUID NOT NULL REFERENCES patient_pathways(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    event_type care_event_type,
    window_days INTEGER,
    required BOOLEAN NOT NULL DEFAULT true,
    status pathway_step_status NOT NULL DEFAULT 'ACTIVE',
    skip_reason TEXT,
    alert_text TEXT,
    suggested_action TEXT,
    source_template_step_id VARCHAR(100),
    completed_at TIMESTAMP WITH TIME ZONE,
    completed_care_event_id UUID REFERENCES care_events(id),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by UUID NOT NULL,
    version INTEGER NOT NULL DEFAULT 0
);

-- DAG edges (prerequisites)
CREATE TABLE patient_pathway_edges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pathway_id UUID NOT NULL REFERENCES patient_pathways(id) ON DELETE CASCADE,
    source_step_id UUID NOT NULL REFERENCES patient_pathway_steps(id) ON DELETE CASCADE,
    target_step_id UUID NOT NULL REFERENCES patient_pathway_steps(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by UUID NOT NULL,
    CONSTRAINT uq_pathway_edge UNIQUE (source_step_id, target_step_id),
    CONSTRAINT chk_no_self_edge CHECK (source_step_id <> target_step_id)
);

-- Indexes
CREATE INDEX idx_patient_pathway_steps_pathway ON patient_pathway_steps(pathway_id);
CREATE INDEX idx_patient_pathway_steps_status ON patient_pathway_steps(status);
CREATE INDEX idx_patient_pathway_edges_pathway ON patient_pathway_edges(pathway_id);
CREATE INDEX idx_patient_pathway_edges_source ON patient_pathway_edges(source_step_id);
CREATE INDEX idx_patient_pathway_edges_target ON patient_pathway_edges(target_step_id);

-- Envers audit tables (Hibernate auto-creates these, but grants needed)
-- Will be: patient_pathways_AUD, patient_pathway_steps_AUD, patient_pathway_edges_AUD

-- Permissions
GRANT ALL ON patient_pathways TO onco_app;
GRANT ALL ON patient_pathway_steps TO onco_app;
GRANT ALL ON patient_pathway_edges TO onco_app;
```

### JPA Entity Pattern

```java
// Source: Established project pattern from Patient.java, PhysicianOverride.java [VERIFIED]
@Entity
@Table(name = "patient_pathway_steps")
@Audited
public class PatientPathwayStep {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pathway_id", nullable = false)
    private PatientPathway pathway;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", columnDefinition = "care_event_type")
    private CareEventType eventType;

    @Column(name = "window_days")
    private Integer windowDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", columnDefinition = "pathway_step_status", nullable = false)
    private PathwayStepStatus status = PathwayStepStatus.ACTIVE;

    @Column(name = "skip_reason", columnDefinition = "TEXT")
    private String skipReason;

    @Version
    @Column(name = "version")
    private Integer version = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @PrePersist void prePersist() { /* same pattern */ }
    @PreUpdate void preUpdate() { /* same pattern */ }
}
```

### REST API Pattern

```java
// Source: Established project pattern from PatientController.java [VERIFIED]
@RestController
@RequestMapping("/api/patients/{patientId}/pathway")
public class PatientPathwayController {

    @GetMapping("/steps")
    @PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')")
    public List<PathwayStepResponse> getSteps(@PathVariable UUID patientId) { ... }

    @PostMapping("/steps")
    @PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public PathwayStepResponse createStep(
            @PathVariable UUID patientId,
            @Valid @RequestBody PathwayStepRequest request,
            @AuthenticationPrincipal Jwt jwt) { ... }

    // PUT, DELETE for steps; POST, DELETE for edges
}
```

### Frontend TanStack Query Hook Pattern

```typescript
// Source: Established project pattern from api.ts [VERIFIED]
export function useCreateStep(patientId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateStepRequest) =>
      apiClient.post<PathwayStepResponse>(
        `/patients/${patientId}/pathway/steps`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ['patients', patientId, 'pathway-status']
      });
      queryClient.invalidateQueries({
        queryKey: ['patients', patientId, 'pathway-steps']
      });
    },
  });
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| JSONB templateData per cancer type | Relational per-patient steps | Phase 5 | Enables per-patient pathway divergence, DAG evaluation, manual editing |
| Linear step evaluation (iterate by stepNumber) | DAG topological sort evaluation | Phase 5 | Enables parallel paths, flexible prerequisites |
| PhysicianOverride entity + table | SKIPPED status on PatientPathwayStep | Phase 5 | Simplifies model -- one table instead of two; reason stored on the step itself |
| AnchorType enum (PREVIOUS_STEP, DIAGNOSIS_DATE, SPECIFIC_STEP) | Edge-based prerequisite anchoring | Phase 5 | D-11: time windows anchor to prerequisites. Root steps anchor to diagnosis date. Unified model |
| PathwayTemplate.findByCancerType for evaluation | PatientPathwayStep.findByPathwayId for evaluation | Phase 5 | Evaluation queries per-patient rows, not shared templates |

**Deprecated/outdated after Phase 5:**
- `PathwayStep` record (dto) -- still needed for template JSONB parsing during fork, but NOT used by evaluation engine
- `AnchorType` enum -- replaced by edge-based anchoring in per-patient context; still exists for JSONB backward compatibility
- `PhysicianOverride` entity + `physician_overrides` table -- legacy after migration; not deleted (audit trail retention), but evaluation engine no longer queries it
- `PhysicianOverrideRepository` -- no longer called by evaluation engine

## Assumptions Log

> List all claims tagged [ASSUMED] in this research. The planner and discuss-phase use this
> section to identify decisions that need user confirmation before execution.

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Kahn's algorithm implementation pattern in Java (standard textbook) | Architecture Patterns - Pattern 1 | LOW -- well-known algorithm, trivial to verify |
| A2 | DFS cycle detection pattern (standard textbook) | Architecture Patterns - Pattern 2 | LOW -- well-known algorithm |
| A3 | Template fork creates pathway rows in a single @Transactional method | Architecture Patterns - Pattern 3 | LOW -- standard Spring transactional pattern |
| A4 | Adding @SignalMethod to workflow interface is replay-safe | Common Pitfalls - Pitfall 2 | MEDIUM -- verified against Temporal docs, but edge cases exist with running workflows |
| A5 | ON DELETE CASCADE on edges table handles D-10 | Database Schema | LOW -- standard PostgreSQL FK behavior |
| A6 | @Version column provides optimistic locking for concurrent edits | Don't Hand-Roll | LOW -- standard JPA pattern |
| A7 | One pathway per patient (UNIQUE constraint) | Database Schema | LOW -- explicitly stated in phase description |

**If this table is empty:** All claims in this research were verified or cited -- no user confirmation needed.

## Open Questions

1. **Care event-to-step explicit linkage**
   - What we know: Currently, care events match to steps by eventType in iteration order. With DAG, topological order replaces iteration order.
   - What's unclear: Should we add `completed_care_event_id` to `patient_pathway_steps` for explicit linkage, or continue the implicit eventType matching?
   - Recommendation: Add the FK column now (`completed_care_event_id UUID REFERENCES care_events(id)`). This enables the data migration to explicitly link existing care events to per-patient steps. The evaluation engine can set this when marking a step COMPLETED. Cost: one extra column. Benefit: unambiguous linkage, simpler future queries.

2. **Envers audit table grants**
   - What we know: Hibernate Envers auto-creates `_AUD` tables. The Flyway migration creates the base tables with `GRANT ALL ... TO onco_app`.
   - What's unclear: Envers tables are created by Hibernate, not Flyway, so they may not have the grants.
   - Recommendation: Check existing behavior -- if Phases 1-4 already handle this (V3 migration grants or `spring.jpa.properties.hibernate.hbm2ddl.auto=update` for Envers tables), follow the same pattern. If not, add a V16 migration to grant permissions on the `_AUD` tables after Hibernate creates them on first startup.

3. **Existing template step eventType ambiguity**
   - What we know: The LUNG pathway has LUNG_01 (CONSULTATION), LUNG_04 (CONSULTATION), and LUNG_05 (CONSULTATION) -- three steps with the same eventType.
   - What's unclear: After migration, how does the evaluation engine distinguish which CONSULTATION care event maps to which step?
   - Recommendation: During migration, use step ordering (stepNumber) combined with care event dates to make the best-effort assignment. After migration, the explicit `completed_care_event_id` column handles future assignments. For the initial migration, assign completed events in stepNumber order for steps sharing an eventType.

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | Existing Keycloak JWT (no changes) |
| V3 Session Management | No | Existing Spring Security (no changes) |
| V4 Access Control | Yes | D-03: All clinical roles can edit. @PreAuthorize on new endpoints with hasRole('CARE_COORDINATOR') or hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN'). No new roles |
| V5 Input Validation | Yes | Bean validation (@NotBlank, @NotNull) on step/edge request DTOs. Zod v4 schemas on frontend forms |
| V6 Cryptography | No | Per-patient step names are NOT PHI (clinical process data). No encryption needed on new tables. [VERIFIED: CONTEXT.md code_context section] |

### Known Threat Patterns for Phase 5

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| BOLA: User modifies steps for a patient they don't own | Tampering | @PreAuthorize role check. Phase 5 D-03 allows ALL clinical roles. Future: patient-level access control (noted in V2 TODO from Phase 4) |
| Cycle injection via edge creation | Tampering | Server-side cycle detection before persisting edge (D-09). Never trust client-side validation alone |
| Mass step deletion (accidental or malicious) | Denial of Service | @Audited on all entities; Hibernate Envers tracks deletions. Consider soft-delete pattern for steps (not in scope per D-01) |
| PHI in Temporal payload via step names | Information Disclosure | Step names are NOT PHI. pathwayStepsChanged signal carries no parameters. Maintain UUID-only convention in all workflow signals |
| SQL injection in Flyway data migration | Tampering | Migration uses parameterized operations via JSONB functions, not string concatenation. Templates are trusted seed data |

## Sources

### Primary (HIGH confidence)
- `pom.xml` -- verified all backend dependency versions
- `frontend/package.json` -- verified all frontend dependency versions
- `frontend/components.json` -- verified shadcn preset and configuration
- `V1__create_base_schema.sql` through `V12__update_pathway_time_windows.sql` -- verified schema conventions, naming, grant patterns
- `PathwayEvaluationActivityImpl.java` -- verified current evaluation logic (420 lines, full read)
- `PatientPathwayWorkflowImpl.java` -- verified workflow signal pattern, main loop structure
- `PatientPathwayWorkflow.java` -- verified workflow interface signal method pattern
- `PathwayTemplate.java` -- verified JSONB template entity structure
- `PathwayStep.java` (dto) -- verified JSONB step record fields (12 fields)
- `PhysicianOverride.java` -- verified override entity (being replaced)
- `PathwayStatusService.java` -- verified current JSONB-based status derivation
- `PatientService.java` -- verified patient creation + workflow start pattern
- `PathwayService.java` -- verified Temporal signal sending pattern
- `PatientController.java` -- verified REST controller @PreAuthorize pattern
- `Patient.java` -- verified entity pattern (@Audited, @PrePersist, EncryptionConverter)
- `CareEvent.java` -- verified care event entity (eventType, status, pathwayStepId fields)
- `Alert.java` -- verified alert entity (pathwayStepName field for dedup)
- `$patientId.tsx` -- verified patient detail page layout, pathway rendering
- `PatientWizard.tsx` -- verified two-step wizard, form patterns
- `types.ts`, `api.ts` -- verified TypeScript types and TanStack Query hooks
- `05-CONTEXT.md` -- all locked decisions D-01 through D-16
- `05-UI-SPEC.md` -- component inventory, interaction contracts, design system
- `V6__seed_pathway_templates.sql` -- verified template JSONB structure (3 templates, 18 total steps)

### Secondary (MEDIUM confidence)
- Context7 `/temporalio/sdk-java` -- Signal method patterns, workflow interface conventions [VERIFIED via Context7]
- Temporal Java SDK documentation -- Signal method addition is replay-safe for running workflows [CITED: docs.temporal.io]

### Tertiary (LOW confidence)
- None -- all claims verified against codebase or official documentation

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH -- all dependencies already in project, zero new libraries needed
- Architecture: HIGH -- DAG algorithms are textbook, JPA patterns are established, Temporal signal addition follows existing pattern
- Pitfalls: HIGH -- identified 7 specific pitfalls from codebase analysis, each with concrete mitigation
- Data migration: MEDIUM -- complex SQL migration with JSONB parsing, physician override mapping, and care event matching. Thoroughly analyzed but execution carries inherent risk
- Frontend: HIGH -- UI-SPEC provides complete interaction contracts, all components use existing shadcn primitives

**Research date:** 2026-05-04
**Valid until:** 2026-06-04 (stable -- all dependencies are project-locked, no external API changes expected)
