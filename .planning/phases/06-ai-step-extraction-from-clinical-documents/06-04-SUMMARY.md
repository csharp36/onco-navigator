---
phase: 06-ai-step-extraction-from-clinical-documents
plan: "04"
subsystem: frontend
tags: [react, tanstack-query, typescript, ui, pathway-editor, ai-proposals]
dependency_graph:
  requires: [06-02, 06-03]
  provides: [confirm-reject-ux, ai-proposed-step-rendering, already-covered-display]
  affects: [frontend/src/features/patients/types.ts, frontend/src/features/patients/api.ts, frontend/src/features/patients/StepRow.tsx, frontend/src/features/patients/PathwayEditor.tsx]
tech_stack:
  added: []
  patterns: [tanstack-query-mutation, tanstack-query-infinite-stale, shadcn-collapsible, shadcn-dialog, react-confirmation-dialog]
key_files:
  created: []
  modified:
    - frontend/src/features/patients/types.ts
    - frontend/src/features/patients/api.ts
    - frontend/src/features/patients/StepRow.tsx
    - frontend/src/features/patients/PathwayEditor.tsx
decisions:
  - "PathwayStepStatus interface (used by StepRow/PathwayEditor) also received AI extraction fields — not just PatientPathwayStep — since components use PathwayStepStatus from usePathwayStatus, not PatientPathwayStep from usePathwaySteps"
  - "useDocumentAlreadyCovered uses staleTime: Infinity — extraction results are immutable for a given document upload"
  - "PROPOSED block action area fully replaces Trash2/Remove with Confirm+Edit+Reject — no coexistence"
  - "InlineStepEdit guard updated to step.status === 'ACTIVE' || step.status === 'PROPOSED' for D-06 edit-before-confirm"
  - "Reject dialog uses rejectingStepId state pattern following existing skipDialogStep pattern"
metrics:
  duration: 5min
  completed: "2026-05-05T02:30:19Z"
  tasks_completed: 2
  files_modified: 4
---

# Phase 06 Plan 04: Frontend Confirm/Reject UX for AI-Proposed Steps Summary

Frontend confirm/reject UX for AI-proposed pathway steps: TypeScript types with REJECTED status and AI source fields, TanStack Query mutation hooks for confirm/reject, StepRow with Confirm/Edit/Reject buttons replacing Trash2, PathwayEditor with already-covered section, rejected step toggle, and InlineStepEdit for PROPOSED steps.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | TypeScript types and API hooks | d1894b0 | types.ts, api.ts |
| 2 | StepRow and PathwayEditor integration | 175e4f0 | StepRow.tsx, PathwayEditor.tsx |

## What Was Built

### Task 1: Types and API Hooks

**types.ts** changes:
- `PathwayStepStatusEnum` updated: added `'REJECTED'` as fifth value
- `PathwayStepStatus` interface (used by StepRow/PathwayEditor): added `sourceDocumentId: string | null`, `extractionSource: 'TEMPLATE' | 'MANUAL' | 'AI_EXTRACTED' | null`, `sourceDocumentFilename: string | null`
- `PatientPathwayStep` interface: same three AI source tracking fields added

**api.ts** additions:
- `useConfirmStep(patientId)`: POST mutation to `/patients/{id}/pathway/steps/{stepId}/confirm`; invalidates pathway-steps, pathway-status, pathway-edges
- `useRejectStep(patientId)`: PATCH mutation to `/patients/{id}/pathway/steps/{stepId}/reject`; invalidates pathway-steps, pathway-status
- `useDocumentAlreadyCovered(documentId | null)`: query with `enabled: !!documentId`, `staleTime: Infinity`, selects `alreadyCoveredEventTypes` string as comma-split array

### Task 2: StepRow and PathwayEditor

**StepRow.tsx** changes:
- Added `Check`, `X` imports from lucide-react
- Props: added `onConfirm?: () => void`, `onReject?: () => void`
- `PathwayStepIcon`: added REJECTED case returning `MinusCircle`
- `stepNameClass`: added REJECTED case returning `'text-sm text-muted-foreground line-through'`
- PROPOSED step row `<li>`: added `border-dashed` via conditional className
- PROPOSED name area: added "AI Proposed" `<Badge>` after step name
- PROPOSED content area: added source document filename line for D-10 provenance
- PROPOSED action block: Trash2/Remove fully replaced with Confirm (Check icon, variant="default") + Edit (Pencil) + Reject (X, destructive) buttons
- REJECTED badge rendered in step content area

**PathwayEditor.tsx** changes:
- Imports: added `useConfirmStep`, `useRejectStep`, `useDocumentAlreadyCovered`, `Collapsible`/`CollapsibleTrigger`/`CollapsibleContent`, `Card`/`CardContent`/`CardHeader`/`CardTitle`
- State: added `rejectingStepId`, `rejectingStepName`, `showRejected`
- Mutations: initialized `confirmStep` and `rejectStep`
- D-10: derived `sourceDocumentId` from first PROPOSED step; called `useDocumentAlreadyCovered`; renders "Already in pathway" Card when data exists
- D-07: filtered `visibleSteps` (non-REJECTED) and `rejectedSteps`; main list renders `visibleSteps`
- D-06: `InlineStepEdit` render guard updated to `step.status === 'ACTIVE' || step.status === 'PROPOSED'`
- StepRow: passes `onConfirm` / `onReject` callbacks for PROPOSED steps
- Collapsible toggle: "Show N rejected step(s)" / "Hide rejected steps"
- Reject dialog: "Remove proposal for X?" with Keep Proposal / Reject Proposal actions, error display

## Deviations from Plan

### Auto-applied: Rule 2 — Missing fields on PathwayStepStatus

**Found during:** Task 1 planning

The plan specified adding `sourceDocumentId`, `extractionSource`, `sourceDocumentFilename` only to `PatientPathwayStep`. However, `StepRow.tsx` and `PathwayEditor.tsx` actually receive `PathwayStepStatus[]` (from `usePathwayStatus` / `pathwayStatus?.steps`), not `PatientPathwayStep[]`. Without adding these fields to `PathwayStepStatus`, the components could not access `sourceDocumentFilename` for the source link or `sourceDocumentId` for `useDocumentAlreadyCovered`.

**Fix:** Added the three AI extraction fields to `PathwayStepStatus` interface as well.

**Files modified:** `frontend/src/features/patients/types.ts`

## Known Stubs

None — all data flows are wired to real API calls. The "Already in pathway" section correctly renders only when the API returns `alreadyCoveredEventTypes` data.

## Threat Flags

No new security-relevant surface introduced. All new endpoints called are gated by existing Keycloak JWT authentication. `sourceDocumentFilename` is non-PHI metadata displayed to authenticated users only (per T-06-15 in plan threat model).

## Self-Check: PASSED

- `frontend/src/features/patients/types.ts` — modified, exists
- `frontend/src/features/patients/api.ts` — modified, exists
- `frontend/src/features/patients/StepRow.tsx` — modified, exists
- `frontend/src/features/patients/PathwayEditor.tsx` — modified, exists
- Commit d1894b0 exists: `git log --oneline | grep d1894b0`
- Commit 175e4f0 exists: `git log --oneline | grep 175e4f0`
- TypeScript compilation: PASSED (no errors)
- No unexpected file deletions
