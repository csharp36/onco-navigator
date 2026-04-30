---
phase: 03-working-application
plan: 05
subsystem: ui
tags: [react, typescript, tanstack-query, tanstack-router, shadcn, zod, react-hook-form]

# Dependency graph
requires:
  - phase: 03-working-application plan 02
    provides: AlertResponse/DashboardStatsResponse types, useAlerts/useAlertCount/useResolveAlert/useDashboardStats hooks, route scaffolds for /alerts and /
  - phase: 03-working-application plan 03
    provides: nav-sidebar.tsx component structure for badge injection

provides:
  - AlertCard component with severity-colored left border stripe and clickable patient name link
  - ResolveAlertModal with non-dismissible dialog, Zod v4 min-10 validation, onSuccess close pattern
  - Alert queue page at /alerts with filter bar, severity groups, and resolution flow
  - Dashboard landing page with 3 stat cards and top-5 urgent alerts with 30-second polling
  - Sidebar alert count badge with 30-second polling and 99+ cap

affects: [nurse navigator workflow, alert resolution UX, dashboard at-a-glance monitoring, persistent alert awareness]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - Zod v4 min validation uses object syntax { error: 'message' } instead of string second arg
    - ResolveAlertModal closes via onOpenChange(false) in mutation onSuccess (Pitfall 8 — not in useEffect)
    - onInteractOutside={(e) => e.preventDefault()} for non-dismissible dialogs
    - Client-side filtering on already-fetched TanStack Query data (no new API calls for filters)
    - Severity groups rendered with empty-section hiding (no DOM element when count is 0)
    - Badge variant=destructive with ml-auto in nav Link for right-aligned alert count

key-files:
  created:
    - frontend/src/features/alerts/AlertCard.tsx
    - frontend/src/features/alerts/ResolveAlertModal.tsx
  modified:
    - frontend/src/app.css (severity CSS variables added to :root)
    - frontend/src/routes/alerts/index.tsx (full implementation replacing scaffold)
    - frontend/src/routes/index.tsx (full dashboard rewrite replacing placeholder)
    - frontend/src/components/layout/nav-sidebar.tsx (useAlertCount badge added)

key-decisions:
  - "AlertCard uses style prop for borderLeftColor rather than Tailwind arbitrary values to reference CSS variables — more readable and avoids Tailwind v4 compatibility edge cases with arbitrary CSS variable references"
  - "ResolveAlertModal uses both isValid from react-hook-form and a manual notesValue length check for the disabled state — belt-and-suspenders to ensure button is disabled until the form is genuinely valid"
  - "Dashboard stat card error state reuses the alert error copy to satisfy the UI-SPEC 'Unable to load alerts' contract even though this data comes from /dashboard/stats"

# Metrics
duration: 20min
completed: 2026-04-30
---

# Phase 3 Plan 05: Alert Management and Dashboard Frontend Summary

**AlertCard, ResolveAlertModal, alert queue page at /alerts, dashboard rewrite with stat cards and urgent alerts, and persistent sidebar badge — all wired to live TanStack Query hooks with 30-second polling**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-04-30T20:05:00Z
- **Completed:** 2026-04-30T20:28:42Z
- **Tasks:** 2 of 2
- **Files modified:** 6

## Accomplishments

- Added severity CSS variables (`--severity-overdue`, `--severity-missing`, `--severity-out-of-order`, `--severity-completed`) to `app.css` `:root` block using oklch values from UI-SPEC
- Created `AlertCard.tsx`: severity-colored `border-l-4` left stripe via `style` prop, `Badge` variants (destructive/secondary/outline) for severity, patient name as TanStack Router `Link` to `/patients/$patientId`, time elapsed and View/Resolve buttons
- Created `ResolveAlertModal.tsx`: non-dismissible dialog (`onInteractOutside` preventDefault), alert details summary (read-only), Zod v4 `.min(10, { error: ... })` validation, `onOpenChange(false)` called in `onSuccess` per Pitfall 8, "Keep Alert Open" dismiss button per UI-SPEC copywriting contract
- Rewrote `routes/alerts/index.tsx`: filter bar (type Select, patient name Input, date range From/To), alerts grouped by severity (Overdue/Missing/Out of Order), empty sections hidden, 3 loading skeletons, empty state "No open alerts. / All patient pathways are on track.", error state
- Rewrote `routes/index.tsx` (Dashboard): 3 stat cards in responsive grid, "Open Alerts" number uses `text-destructive` when >0, "Urgent Alerts" section with top 5 AlertCards, "View All Alerts" link (Button variant=outline), ResolveAlertModal available from dashboard
- Modified `nav-sidebar.tsx`: `useAlertCount` hook, `Badge variant="destructive"` with `ml-auto text-xs tabular-nums min-w-5 justify-center`, renders only when `alertCount > 0`, caps at "99+"

## Task Commits

1. **Task 1: Alert card component, resolve modal, and severity CSS variables** — `fbc8ae5`
2. **Task 2: Alert queue page, dashboard page, and nav sidebar alert badge** — `e1d39f7`

## Files Created/Modified

- `frontend/src/app.css` — 4 severity CSS variables added to `:root`
- `frontend/src/features/alerts/AlertCard.tsx` — New component: severity stripe, patient link, action buttons
- `frontend/src/features/alerts/ResolveAlertModal.tsx` — New component: non-dismissible resolve dialog with Zod v4 validation
- `frontend/src/routes/alerts/index.tsx` — Full alert queue page (was scaffold placeholder)
- `frontend/src/routes/index.tsx` — Full dashboard page (was static placeholder)
- `frontend/src/components/layout/nav-sidebar.tsx` — Alert count badge added

## Decisions Made

- AlertCard applies `border-l-4` class on the Card and sets `borderLeftColor` via `style` prop referencing CSS custom properties — avoids Tailwind arbitrary value syntax for CSS variables which can be less reliable in v4.
- ResolveAlertModal calls `onOpenChange(false)` in mutation `onSuccess` callback (not in a `useEffect` watching `isSuccess`) — this is the correct pattern to avoid double-close race conditions per Pitfall 8.
- Dashboard error state shows "Unable to load alerts" per UI-SPEC even though the data source is `/dashboard/stats` — the copy contract applies to any API load failure shown to the user.

## Deviations from Plan

None — plan executed exactly as written.

## Threat Surface Scan

No new network endpoints, auth paths, or trust boundaries introduced. All new code is frontend-only UI components consuming existing hooks. The threat register in the plan (T-03-16, T-03-17, T-03-18) covers all relevant surfaces — PHI displayed to authenticated users over TLS, 30-second polling at negligible pilot-scale load, and client-side filters with no security impact.

## Known Stubs

None. All components are wired to live TanStack Query hooks (`useAlerts`, `useAlertCount`, `useResolveAlert`, `useDashboardStats`). Data rendering is conditional on real API responses. No hardcoded placeholder values in the rendered output.

## Self-Check: PASSED

Files exist:
- frontend/src/features/alerts/AlertCard.tsx: FOUND
- frontend/src/features/alerts/ResolveAlertModal.tsx: FOUND
- frontend/src/routes/alerts/index.tsx: FOUND (full implementation)
- frontend/src/routes/index.tsx: FOUND (full dashboard)
- frontend/src/components/layout/nav-sidebar.tsx: FOUND (badge added)
- frontend/src/app.css: FOUND (severity vars added)

Commits exist:
- fbc8ae5: FOUND (Task 1)
- e1d39f7: FOUND (Task 2)

Build: `npx vite build` succeeded (2077 modules, 0 TypeScript errors)
