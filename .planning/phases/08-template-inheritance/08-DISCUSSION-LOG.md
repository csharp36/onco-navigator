# Phase 8: Template Inheritance - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-05
**Phase:** 08-template-inheritance
**Areas discussed:** Cancer type taxonomy, Inheritance power, Template selection UX

---

## Cancer type taxonomy

### Q1: How should the system model the colon/rectal distinction?

| Option | Description | Selected |
|--------|-------------|----------|
| Keep COLORECTAL, sub-templates | COLORECTAL stays as one cancer type. The inheritance system handles variants. | |
| Split into COLON + RECTAL | Add COLON and RECTAL as separate CancerType values. | |
| You decide | Let Claude pick the approach that best fits existing architecture. | ✓ |

**User's choice:** You decide
**Notes:** Claude's discretion — "Keep COLORECTAL, sub-templates" is the natural fit since it exercises the inheritance system and avoids touching the CancerType enum.

### Q2: Should inheritance be general-purpose or rectal-only?

| Option | Description | Selected |
|--------|-------------|----------|
| General-purpose (Recommended) | Build a parent_template_id mechanism that works for any cancer type. | ✓ |
| Rectal-only shortcut | Solve only the PW-CR-004 requirement. | |
| You decide | Let Claude pick based on implementation complexity tradeoff. | |

**User's choice:** General-purpose (Recommended)
**Notes:** None

### Q3: Should there be a limit on inheritance depth?

| Option | Description | Selected |
|--------|-------------|----------|
| Single level only (Recommended) | A template can have a parent, but not a grandparent. | ✓ |
| Unlimited depth | Any template can inherit from any other. | |
| You decide | Let Claude pick based on implementation complexity. | |

**User's choice:** Single level only (Recommended)
**Notes:** None

---

## Inheritance power

### Q4: What operations should a child template support on parent steps?

| Option | Description | Selected |
|--------|-------------|----------|
| Override step properties | Change a parent step's time window, alert text, suggested action, or description. | ✓ |
| Add new steps + edges | Insert steps that don't exist in the parent, with their own prerequisite edges. | ✓ |
| Remove parent steps | Mark a parent step as excluded in the child template. | ✓ |
| Rearrange edges | Change prerequisite relationships between parent steps. | ✓ |

**User's choice:** All four operations (multi-select)
**Notes:** Full power needed for the rectal case (insert neoadjuvant steps, reverse surgery ordering, adjust time windows).

### Q5: How should child template overrides be stored?

| Option | Description | Selected |
|--------|-------------|----------|
| Diff-based (Recommended) | Child stores only the delta: overridden fields, additions, removals, edge changes. | ✓ |
| Full copy with parent ref | Child stores a complete step list (all parent steps + modifications). | |
| You decide | Let Claude choose based on merge complexity. | |

**User's choice:** Diff-based (Recommended)
**Notes:** User reviewed the preview showing the diff JSONB structure.

### Q6: Should child templates automatically pick up parent updates?

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, live inheritance (Recommended) | Non-overridden parent steps reflect latest parent version at fork time. | ✓ |
| No, version-locked children | Child records parent version; requires manual re-sync. | |
| You decide | Let Claude pick based on complexity and clinical safety. | |

**User's choice:** Yes, live inheritance (Recommended)
**Notes:** None

---

## Template selection UX

### Q7: How should the wizard present template choices?

| Option | Description | Selected |
|--------|-------------|----------|
| Two-step: type then variant | Cancer type first, then template variant picker (only when 2+ templates exist). | ✓ |
| Flat list of all templates | Single list of all templates replacing cancer type selection. | |
| You decide | Let Claude pick the UX that integrates cleanest. | |

**User's choice:** Two-step: type then variant
**Notes:** User reviewed the preview showing the variant radio group under cancer type selection.

### Q8: Should root template auto-select when only one exists?

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, auto-select when only one (Recommended) | Zero UX change for Breast and Lung patients. Variant picker only appears when needed. | ✓ |
| Always show the picker | Even if there's only one template. | |
| You decide | Let Claude pick based on UX minimalism. | |

**User's choice:** Yes, auto-select when only one (Recommended)
**Notes:** None

### Q9: Should child templates have descriptions in the picker?

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, brief description | 1-line description explaining clinical difference from parent. | ✓ |
| Name only is sufficient | Template names are descriptive enough. | |
| You decide | Let Claude pick based on clinical clarity. | |

**User's choice:** Yes, brief description
**Notes:** None

---

## Claude's Discretion

- Cancer type taxonomy model details (keep COLORECTAL with sub-templates vs split)
- Schema design for parent_template_id, name/description columns, diff JSONB structure
- Merge algorithm implementation (order of operations)
- Flyway migration structure
- PathwayForkService merge logic
- Repository query changes
- TemplatePicker component redesign
- Backend REST endpoint changes
- Rectal child template clinical content (specific steps/edges)

## Deferred Ideas

None — discussion stayed within phase scope
