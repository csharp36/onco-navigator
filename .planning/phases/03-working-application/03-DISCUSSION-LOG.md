# Phase 3: Working Application - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-30
**Phase:** 03-working-application
**Areas discussed:** Data entry experience, Alert queue & resolution, Pathway status view, Dashboard landing page

---

## Data Entry Experience

### Patient form structure

| Option | Description | Selected |
|--------|-------------|----------|
| Single-page form | All fields on one page — fast to fill, no navigation | |
| Two-step wizard | Step 1: demographics. Step 2: clinical info. Visually simpler per step. | Yes |
| Dialog/modal form | Opens as modal from patient list. Quick add without navigating. | |

**User's choice:** Two-step wizard
**Notes:** Separates "who" (demographics) from "what" (clinical info) cleanly.

### Care event recording location

| Option | Description | Selected |
|--------|-------------|----------|
| From patient detail page | Add Event button on patient detail. Navigate to patient first. | |
| Quick-add from patient list | Quick-action button on list row. Compact dialog, no navigation. | |
| Both approaches | Quick-add dialog from list + full history on patient detail page. | Yes |

**User's choice:** Both approaches
**Notes:** Two entry points for the highest-frequency action in the system.

### Post-save navigation

| Option | Description | Selected |
|--------|-------------|----------|
| Patient detail page | Redirect to newly created patient's detail view. | Yes |
| Patient list with toast | Return to list, show success notification. | |
| Stay on form (add another) | Clear form, show success banner with link. | |

**User's choice:** Patient detail page
**Notes:** Immediately ready to record care events after patient creation.

### Patient search

| Option | Description | Selected |
|--------|-------------|----------|
| MRN search with HMAC | HMAC index token for exact MRN lookup + in-memory name search. | Yes |
| Name-only search (defer MRN) | Load all, filter client-side by name. Defer HMAC. | |
| You decide | Let Claude pick. | |

**User's choice:** MRN search with HMAC
**Notes:** Closes the deferred item from Phase 1 (PatientRepository.findByMrn stub).

---

## Alert Queue & Resolution

### Alert queue layout

| Option | Description | Selected |
|--------|-------------|----------|
| Alert cards | Each alert is a card with severity badge, patient info, actions. | |
| Compact table | Dense table view, click row to expand. | |
| Hybrid (cards + filter) | Cards layout with filter bar, grouped by severity. | Yes |

**User's choice:** Hybrid (cards + filter)
**Notes:** Visual hierarchy for scanning plus filtering for focused work.

### Resolution workflow

| Option | Description | Selected |
|--------|-------------|----------|
| Inline slide-out panel | Side panel slides out showing details + resolution form. | |
| Modal dialog | Centered dialog over alert queue. Focused resolution. | Yes |
| Separate page | Navigate to full alert detail page. | |

**User's choice:** Modal dialog
**Notes:** Clean focus, resolution notes required, then back to queue. shadcn/ui dialog component already available.

### Alert card navigation

| Option | Description | Selected |
|--------|-------------|----------|
| Patient name is a link | Clicking patient name navigates to patient detail. Alert card shows enough to decide. | Yes |
| Expandable card | Card expands in-place to show mini pathway status. | |

**User's choice:** Patient name is a link
**Notes:** [View] and [Resolve] buttons on each card. Patient name as clickable link to detail page.

---

## Pathway Status View

### Pathway visualization style

| Option | Description | Selected |
|--------|-------------|----------|
| Vertical stepped list | Steps top-to-bottom with status icons, dates, timing. | Yes |
| Horizontal timeline | Steps left-to-right as connected nodes. | |
| Card grid per step | Each step is its own card in 2-column grid. | |

**User's choice:** Vertical stepped list
**Notes:** Clear linear progression, scannable, tablet-friendly. Shows status, timing, and alert relationship per step.

### Patient detail page layout

| Option | Description | Selected |
|--------|-------------|----------|
| Pathway-first layout | Pathway is main content, care events in tab. | |
| Side-by-side split | Pathway on left, care events on right. Stacks on tablet. | Yes |
| You decide | Let Claude pick. | |

**User's choice:** Side-by-side split
**Notes:** Everything visible at once without tabs. Stacks vertically on tablet breakpoint.

---

## Dashboard Landing Page

### Dashboard focus

| Option | Description | Selected |
|--------|-------------|----------|
| Alert-focused dashboard | Open alert count, top urgent alerts, patient stats. | Yes |
| Patient-focused dashboard | Patient list front and center with alert badges. | |
| Role-adaptive dashboard | Different content per role. | |

**User's choice:** Alert-focused dashboard
**Notes:** Three stat cards (open alerts, active patients, on-track patients) + top 5 urgent alerts. "View All Alerts" link.

### Persistent alert count location

| Option | Description | Selected |
|--------|-------------|----------|
| Sidebar nav badge | Red badge next to Alerts nav item. Standard pattern. | Yes |
| Top header bar badge | Bell icon with count in top header. | |
| Both sidebar + header | Belt and suspenders. | |

**User's choice:** Sidebar nav badge
**Notes:** Clean, standard pattern. Visible on every page via existing nav-sidebar component.

### Data refresh strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Polling refresh (30s) | TanStack Query refetchInterval of 30 seconds. | Yes |
| Stale-on-focus | Refresh on tab focus or navigation only. | |
| You decide | Let Claude pick. | |

**User's choice:** Polling refresh (30s)
**Notes:** Nurses see new alerts without manual refresh. 30s interval is lightweight for pilot scale.

---

## Claude's Discretion

- REST API controller structure and endpoint naming
- DTO design (records vs classes)
- Bean validation and error response format
- TanStack Query key structure and cache invalidation
- Patient list pagination vs load-all at pilot scale
- Zod schema design for form validation
- HMAC key management approach
- Pathway auto-enrollment trigger from patient creation endpoint

## Deferred Ideas

- Pathway template admin UI — V2 requirement ADV-01
- Manual re-scan trigger — not in current requirements
- Bulk patient import — not in current scope
