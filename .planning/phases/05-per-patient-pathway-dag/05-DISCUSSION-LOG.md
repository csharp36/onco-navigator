# Phase 5: Per-Patient Pathway Instances + DAG Foundation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-04
**Phase:** 05-per-patient-pathway-dag
**Areas discussed:** Step lifecycle & manual editing, Template fork mechanics, DAG edge behavior, Pathway visualization

---

## Step Lifecycle & Manual Editing

### Q1: Can nurses manually add, remove, or edit steps?

| Option | Description | Selected |
|--------|-------------|----------|
| Full manual editing | Nurses can add, remove, edit step details, and reorder dependencies. Fills gap before Phase 6. | ✓ |
| Add only, no remove/edit | Nurses add steps but cannot remove or modify template-forked steps. Physician override handles suppression. | |
| No manual editing in Phase 5 | Per-patient pathways only via template fork or empty. Customization waits for Phase 6. | |

**User's choice:** Full manual editing (Recommended)
**Notes:** None

### Q2: What statuses should per-patient pathway steps have?

| Option | Description | Selected |
|--------|-------------|----------|
| ACTIVE / PROPOSED / COMPLETED / SKIPPED | Four statuses with clear evaluation semantics. PROPOSED is Phase 6 prep. | ✓ |
| ACTIVE / PROPOSED / SKIPPED only | No COMPLETED on step itself — derived from care event existence. | |
| You decide | Let Claude choose based on existing architecture. | |

**User's choice:** ACTIVE / PROPOSED / COMPLETED / SKIPPED (Recommended)
**Notes:** None

### Q3: Who should be able to edit per-patient pathway steps?

| Option | Description | Selected |
|--------|-------------|----------|
| Nurse navigator + Admin | Nurse navigators and admins can edit. Care coordinators cannot. | |
| All clinical roles | Nurse navigator, care coordinator, and admin can all edit. | ✓ |
| Admin only | Only administrators can modify pathway steps. | |

**User's choice:** All clinical roles
**Notes:** None

### Q4: Does SKIPPED replace physician_overrides?

| Option | Description | Selected |
|--------|-------------|----------|
| SKIPPED replaces overrides | One concept instead of two. Existing overrides migrated to SKIPPED. | ✓ |
| Keep both mechanisms | Physician overrides for legacy, SKIPPED for per-patient steps. | |
| You decide | Let Claude determine cleanest migration path. | |

**User's choice:** SKIPPED replaces overrides (Recommended)
**Notes:** None

---

## Template Fork Mechanics

### Q5: What gets copied when starting from template?

| Option | Description | Selected |
|--------|-------------|----------|
| Steps + edges, version-locked | Deep copy all steps and edges. Fork records template version. Independent after fork. | ✓ |
| Steps only, edges derived | Copy steps, derive edges from prerequisites list at fork time. | |
| Steps + edges, template-linked | Per-patient steps reference template. Template updates propagate to unmodified steps. | |

**User's choice:** Steps + edges, version-locked (Recommended)
**Notes:** None

### Q6: What happens with "Build from documents" (empty pathway)?

| Option | Description | Selected |
|--------|-------------|----------|
| Workflow starts, waits for steps | Temporal workflow starts immediately. Evaluation is no-op until steps added. pathwayStepsChanged signal triggers re-evaluation. | ✓ |
| Workflow deferred until first step | No workflow until at least one ACTIVE step exists. | |
| You decide | Let Claude determine based on Temporal architecture. | |

**User's choice:** Workflow starts, waits for steps (Recommended)
**Notes:** None

### Q7: How should patient creation change?

| Option | Description | Selected |
|--------|-------------|----------|
| Template picker in wizard | Step 2 adds pathway selection: start from template (default) or build from documents. | ✓ |
| Separate pathway setup after creation | Patient creation stays as-is. Separate "Set Up Pathway" on patient detail. | |
| Always start from template | Remove "Build from documents" from Phase 5. | |

**User's choice:** Template picker added to wizard (Recommended)
**Notes:** None

### Q8: What happens to existing patients?

| Option | Description | Selected |
|--------|-------------|----------|
| Auto-migrate on next evaluation | Evaluation engine auto-forks template on first encounter with legacy patient. | |
| Manual migration via admin action | Admin must explicitly "Upgrade Pathway" for each patient. | |
| Flyway data migration | Flyway script converts all existing patients to per-patient rows at deploy time. | ✓ |

**User's choice:** Flyway data migration
**Notes:** Supersedes ROADMAP SC5 (legacy JSONB template fallback). Clean cutover, no runtime fallback.

---

## DAG Edge Behavior

### Q9: Can nurses add/remove edges?

| Option | Description | Selected |
|--------|-------------|----------|
| Full edge editing | Nurses add/remove edges. Cycle detection on each edit. | ✓ |
| Edges from fork only | Edges set at template fork. Nurses can add/remove steps but not change dependencies. | |
| You decide | Let Claude determine edge editing model. | |

**User's choice:** Full edge editing (Recommended)
**Notes:** None

### Q10: What happens to edges when a step is removed?

| Option | Description | Selected |
|--------|-------------|----------|
| Cascade delete all edges | Removing step deletes all its edges. Downstream steps become "ready." | ✓ |
| Reconnect edges through gap | A→B→C, remove B → auto-create A→C. | |
| Block removal if step has dependents | Cannot remove step with dependents until edges are manually removed. | |

**User's choice:** Cascade delete all edges (Recommended)
**Notes:** None

### Q11: How do time windows work in parallel paths?

| Option | Description | Selected |
|--------|-------------|----------|
| Each step's own anchor | Time window from immediate prerequisites. Multiple prerequisites: clock starts from LATEST completion. | ✓ |
| All anchored to diagnosis date | Time windows from diagnosis date regardless of DAG structure. | |
| You decide | Let Claude determine anchor strategy. | |

**User's choice:** Each step's own anchor (Recommended)
**Notes:** None

### Q12: Should newly added steps require a prerequisite edge?

| Option | Description | Selected |
|--------|-------------|----------|
| Default to root | No-edge step is immediately "ready." Lowest friction. | ✓ |
| Require at least one edge | System prompts nurse to set a prerequisite. | |
| You decide | Let Claude determine default. | |

**User's choice:** Default to root (Recommended)
**Notes:** None

---

## Pathway Visualization

### Q13: How should the DAG render?

| Option | Description | Selected |
|--------|-------------|----------|
| Horizontal tiered columns | Left-to-right by depth, connecting lines. Pipeline view. | |
| Vertical tiered rows | Top-to-bottom by depth, parallel steps side-by-side. Org chart style. | |
| Enhanced vertical list | Keep existing vertical list, add indentation and branching indicators. | ✓ |

**User's choice:** Enhanced vertical list
**Notes:** Minimal visual change, tablet-friendly, consistent with existing UI.

### Q14: How should nurses edit the pathway?

| Option | Description | Selected |
|--------|-------------|----------|
| Inline editing on pathway view | "Edit Pathway" toggle transforms view into editor. | ✓ |
| Separate pathway editor page | Dedicated route with full DAG editor. | |
| Modal-based editing | Full-screen modal with pathway editor. | |

**User's choice:** Inline editing on pathway view (Recommended)
**Notes:** None

### Q15: How should step statuses look?

| Option | Description | Selected |
|--------|-------------|----------|
| Icons + color coding | Green check (complete), blue circle (active), dashed gray (proposed), strikethrough (skipped). | ✓ |
| Badge labels | Small text badges on each step. | |
| You decide | Let Claude design based on existing components. | |

**User's choice:** Icons + color coding (Recommended)
**Notes:** None

### Q16: Does the layout need to change for the editor?

| Option | Description | Selected |
|--------|-------------|----------|
| Keep split layout, editor in left column | Editor replaces view in left column. Care events stay in right column. | ✓ |
| Full-width editor mode | Editor expands to full width, care events collapse. | |

**User's choice:** Keep split layout, editor in left column (Recommended)
**Notes:** None

---

## Claude's Discretion

- Relational schema design (column names, indexes, constraints)
- Topological sort algorithm choice
- Flyway migration script structure
- Step completion detection criteria
- `pathwayStepsChanged` signal design
- Cycle detection algorithm
- REST API endpoint design
- Frontend component structure for inline editor
- Add Step form UX
- Edge creation visual interaction

## Deferred Ideas

None — discussion stayed within phase scope
