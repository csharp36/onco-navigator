---
phase: 08-template-inheritance
plan: 03
subsystem: frontend
tags: [template-inheritance, template-picker, tanstack-query, react, typescript]
dependency_graph:
  requires:
    - phase: 08-template-inheritance/02
      provides: GET /api/pathway-templates endpoint, CreatePatientRequest templateId field
  provides:
    - PathwayTemplateResponse TypeScript interface
    - usePathwayTemplates TanStack Query hook (conditional fetch, 5min staleTime)
    - TemplatePicker with conditional variant selection (D-07, D-08, D-09)
    - PatientWizard templateId in creation payload
  affects: [patient-creation-flow, frontend-template-selection]
tech_stack:
  added: []
  patterns: [conditional-query-enabled, variant-radio-group, auto-select-root]
key_files:
  created: []
  modified:
    - frontend/src/features/patients/types.ts
    - frontend/src/features/patients/api.ts
    - frontend/src/features/patients/TemplatePicker.tsx
    - frontend/src/features/patients/PatientWizard.tsx
key_decisions:
  - "TemplatePicker uses two useEffect hooks: one for auto-selecting root template on load, one for clearing templateId when switching to empty mode"
  - "Variant picker renders as nested RadioGroup indented under the pathway mode selection for visual hierarchy"
patterns-established:
  - "Conditional variant picker: only renders when pathwayMode is template AND hasVariants (templates.length > 1)"
  - "Root auto-select: useEffect finds isRoot template and sets it as default when templates load"
  - "Cancer type change resets: setSelectedTemplateId(null) in Select onValueChange forces re-evaluation"
requirements-completed: [PW-CR-004]
metrics:
  duration: 2min
  completed: "2026-05-05T20:28:00Z"
  tasks: 2
  files: 4
---

# Phase 08 Plan 03: Frontend Template Variant Picker Summary

**TemplatePicker rewritten to fetch templates via usePathwayTemplates hook and conditionally show variant radio group when multiple templates exist for a cancer type, with root template auto-selected as default.**

## Performance

- **Duration:** 2 min
- **Started:** 2026-05-05T20:25:37Z
- **Completed:** 2026-05-05T20:28:11Z
- **Tasks:** 2
- **Files modified:** 4

## Accomplishments
- PathwayTemplateResponse TypeScript interface with id, cancerType, name, description, parentTemplateId, version, isRoot
- CreatePatientRequest extended with optional templateId field for variant selection
- usePathwayTemplates hook fetches templates conditionally (enabled: !!cancerType) with 5-minute staleTime
- TemplatePicker shows "Template Variant" radio group when 2+ templates exist for selected cancer type (D-07)
- Root template auto-selected as default via useEffect on template load (D-08)
- Child template descriptions displayed in variant picker (D-09)
- PatientWizard passes templateId in patient creation payload
- Cancer type change resets template selection to null

## Task Commits

Each task was committed atomically:

1. **Task 1: TypeScript types and usePathwayTemplates hook** - `53e5ce2` (feat)
2. **Task 2: TemplatePicker rewrite and PatientWizard integration** - `f51f6c5` (feat)

## Files Modified
- `frontend/src/features/patients/types.ts` - Added PathwayTemplateResponse interface and templateId to CreatePatientRequest
- `frontend/src/features/patients/api.ts` - Added usePathwayTemplates TanStack Query hook
- `frontend/src/features/patients/TemplatePicker.tsx` - Rewritten with variant picker, usePathwayTemplates integration, auto-select root
- `frontend/src/features/patients/PatientWizard.tsx` - Added selectedTemplateId state, reset on cancer type change, templateId in payload

## Decisions Made
- TemplatePicker uses two separate useEffect hooks (auto-select root, clear on empty mode) rather than combining into one effect for clarity and separation of concerns
- Variant picker rendered as nested RadioGroup with ml-6 indent to visually indicate it is subordinate to the pathway mode selection

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered
None

## User Setup Required
None - frontend-only changes, no external service configuration required.

## Next Phase Readiness
- End-to-end template inheritance flow is complete:
  - Backend: schema + merge engine (Plan 01), fork service + REST endpoint (Plan 02)
  - Frontend: type definitions + query hook + variant picker + wizard integration (Plan 03)
- Phase 8 is fully delivered: parent/child templates with live inheritance at fork time
- For BREAST/LUNG (1 template): wizard behaves identically to before (no variant picker, auto-selects root)
- For COLORECTAL (2 templates): variant picker shows root + rectal child with description

---
*Phase: 08-template-inheritance*
*Completed: 2026-05-05*
