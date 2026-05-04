---
phase: 05-per-patient-pathway-dag
plan: 06
subsystem: ui
tags: [react, typescript, tanstack-query, shadcn-ui, lucide-react, zod, react-hook-form, pathway-dag]

requires:
  - phase: 05-05
    provides: "TypeScript types (PathwayStepStatus, PatientPathwayEdge), TanStack Query hooks (usePathwaySteps, useCreateStep, useUpdateStep, useDeleteStep, useSkipStep, useUnskipStep, useCreateEdge, useDeleteEdge, usePathwayEdges), TemplatePicker component"
  - phase: 05-04
    provides: "REST API endpoints for per-patient pathway step/edge CRUD and skip/unskip"

provides:
  - "StepRow: shared step row component rendering status icons, depth indentation (24px/level), Unicode branching indicators, edit-mode CRUD action buttons per status"
  - "PathwayDAGView: enhanced vertical list with topological depth ordering, isLastAtDepth computation, aria-live region, loading skeletons, empty state"
  - "SkipStepDialog: focus-trapped dialog with required reason input before confirming skip"
  - "AddStepForm: inline (non-dialog) form with Zod v4 validation for step name, event type select, window days"
  - "EdgeEditor: collapsible dependency section showing source/target edge list with inline add-dependency dropdowns and cycle detection error display"
  - "PathwayEditor: orchestrates step list in edit mode, inline step edit fields, AddStepForm toggle, EdgeEditor, SkipStepDialog, remove confirmation dialog"
  - "Patient detail page: replaced inline <ol> with PathwayDAGView/PathwayEditor toggle; Edit Pathway / Done Editing button in CardHeader"

affects:
  - "05-07 and beyond: per-patient pathway evaluation and AI confirmation workflows — editor UI is complete, Phase 6 adds PROPOSED step confirmation/reject actions"

tech-stack:
  added: []
  patterns:
    - "isLastAtDepth computed client-side by scanning forward in sorted step array for next step at same depth — pure function, no extra API call"
    - "Inline step edit replaces full StepRow with InlineStepEdit sub-component inside PathwayEditor — avoids prop drilling edit state into StepRow"
    - "EdgeEditor uses local showAddForm state for add-dependency form — no parent state needed, mutations handled internally with onError cycle detection"
    - "addEdgeError is string|null in PathwayEditor state — set to 'cycle' on mutation error, passed as non-null to EdgeEditor for display"

key-files:
  created:
    - frontend/src/features/patients/StepRow.tsx
    - frontend/src/features/patients/PathwayDAGView.tsx
    - frontend/src/features/patients/SkipStepDialog.tsx
    - frontend/src/features/patients/AddStepForm.tsx
    - frontend/src/features/patients/EdgeEditor.tsx
    - frontend/src/features/patients/PathwayEditor.tsx
  modified:
    - frontend/src/routes/patients/$patientId.tsx
    - frontend/src/app.css

key-decisions:
  - "StepRow receives isLastAtDepth boolean — computation lives in PathwayDAGView.computeIsLastAtDepth(), not inside StepRow, so the component stays pure and testable"
  - "paddingLeft uses calc() combining depth*24px with base 0.75rem — allows Tailwind p-3 to apply to the li while depth indentation is additive"
  - "PathwayEditor does NOT use usePathwayStatus — it receives steps prop from $patientId.tsx which owns the data-fetching layer; editor only calls mutations"
  - "InlineStepEdit rendered inside PathwayEditor instead of StepRow — avoids needing edit state in StepRow props, keeps StepRow a pure display component"
  - "addEdgeError stored as string|null in PathwayEditor — set on mutation onError, cleared on next attempt; EdgeEditor checks non-null to display cycle error copy"
  - "Zod v4 .or(z.literal('')).transform() pattern for optional number fields — prevents coerce from rejecting empty string on initial render"

requirements-completed: [PW-ALL-002, PW-BR-001, PW-BR-003]

duration: 25min
completed: 2026-05-04
---

# Phase 5 Plan 06: Frontend DAG Visualization and Inline Pathway Editor Summary

**Six React components delivering depth-indented pathway DAG view and full inline editor with step/edge CRUD, skip/unskip, and cycle detection — wired into the patient detail page behind an Edit Pathway toggle**

## Performance

- **Duration:** ~25 min
- **Started:** 2026-05-04T20:00:00Z
- **Completed:** 2026-05-04T20:25:00Z
- **Tasks:** 2 (Task 3 is checkpoint:human-verify — awaiting human approval)
- **Files modified:** 8

## Accomplishments

- Patient detail page now renders pathway steps with depth-based indentation (24px per level) and Unicode box-drawing branching indicators (`├──` for middle, `└──` for last at depth)
- Status icons match UI-SPEC exactly: green CheckCircle2 for COMPLETED, blue Circle for ACTIVE, red AlertTriangle for ACTIVE+alert, dashed gray Circle for PROPOSED, gray MinusCircle for SKIPPED
- Edit Pathway toggle in CardHeader switches between PathwayDAGView (view mode) and PathwayEditor (edit mode) without unmounting the card layout
- PathwayEditor provides full step CRUD (add/edit/remove), skip with required reason dialog, unskip/restore, and dependency edge management with server-side cycle detection error display
- Zod v4 form validation on AddStepForm with correct `.or(z.literal('')).transform()` pattern for optional number coercion

## Task Commits

Each task was committed atomically:

1. **Task 1: StepRow, PathwayDAGView, SkipStepDialog** - `b9ef35e` (feat)
2. **Task 2: PathwayEditor, AddStepForm, EdgeEditor, $patientId.tsx integration** - `9a8652d` (feat)

## Files Created/Modified

- `frontend/src/features/patients/StepRow.tsx` — Shared step row: status icons, depth indentation, branching indicators, edit-mode action buttons per status (Active: edit+skip+remove, Completed: remove, Proposed: remove, Skipped: restore+remove)
- `frontend/src/features/patients/PathwayDAGView.tsx` — Enhanced vertical list with isLastAtDepth computation, aria-live region, 5-skeleton loading state, empty state with UI-SPEC copy
- `frontend/src/features/patients/SkipStepDialog.tsx` — Focus-trapped dialog with required reason Input, disabled submit until reason non-empty
- `frontend/src/features/patients/AddStepForm.tsx` — Inline add-step form (not dialog) with Zod v4 validation, 8 event type options, optional window days
- `frontend/src/features/patients/EdgeEditor.tsx` — Collapsible dependencies section: edge list as "A must complete before B", inline add-dependency with two Select dropdowns, cycle detection error message
- `frontend/src/features/patients/PathwayEditor.tsx` — Edit mode orchestrator: StepRow list, InlineStepEdit sub-component, AddStepForm toggle, EdgeEditor, SkipStepDialog, remove confirmation dialog
- `frontend/src/routes/patients/$patientId.tsx` — Replaced inline `<ol>` with PathwayDAGView/PathwayEditor toggle, added isEditingPathway state, "Edit Pathway"/"Done Editing" button, removed old PathwayStepIcon function, aria-live="polite" wrapper
- `frontend/src/app.css` — Added `.icon-dashed { stroke-dasharray: 4 2; }` for PROPOSED step dashed circle icon

## Decisions Made

- `paddingLeft: calc(${depth * 24}px + 0.75rem)` on StepRow `<li>` — Tailwind `p-3` (0.75rem) applies as base padding; depth indentation is additive. This avoids removing Tailwind padding in favor of pure inline style.
- `InlineStepEdit` defined inside `PathwayEditor.tsx` rather than extracted to its own file — It's only used in PathwayEditor, is tightly coupled to its mutation handlers, and would add file complexity without benefit.
- `usePathwayEdges` called inside `PathwayEditor` (not passed as prop from $patientId.tsx) — edges are only needed in edit mode, so fetching them inside the editor avoids unnecessary network requests in view mode.
- Zod v4 optional number pattern: `z.coerce.number().int().positive().optional().or(z.literal('')).transform(...)` — Zod v4's coerce rejects empty string; the `.or(z.literal(''))` union accepts it then transforms to `undefined`.

## Deviations from Plan

None — plan executed exactly as written.

## Issues Encountered

- `node_modules` not installed in the worktree directory; used main repo's `node_modules/.bin/tsc` for TypeScript verification — this is expected for git worktrees sharing the same package.json.

## User Setup Required

None — no external service configuration required. Component changes are purely frontend; existing backend API endpoints from Plans 04-05 power the mutations.

## Next Phase Readiness

- All Phase 5 auto-tasks are complete
- Task 3 is `checkpoint:human-verify` — human must start the stack and verify visual rendering and interaction per the 5 test scenarios
- Phase 6 (PROPOSED step confirmation/reject) has a clear integration point: PathwayEditor renders PROPOSED steps as view-only; the `onEdit` handler for PROPOSED steps can be replaced with a confirm/reject action set in Phase 6

---
*Phase: 05-per-patient-pathway-dag*
*Completed: 2026-05-04*
