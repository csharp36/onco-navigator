# Phase 2: Pathway Engine - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-04-30
**Phase:** 02-pathway-engine
**Areas discussed:** Pathway template structure, Monitoring cadence & triggers, Workflow lifecycle, Clinical pathway content

---

## Pathway Template Structure

### Step Ordering

| Option | Description | Selected |
|--------|-------------|----------|
| Linear only | Steps are a simple ordered list. Matches all three spec pathways. Simpler Temporal workflow logic. | ✓ |
| Linear with optional steps | Linear sequence with optional step flag. Optional steps generate warnings, not blocking alerts. | |
| DAG (directed graph) | Steps can have multiple prerequisites and branches. No current pathway requires this. | |

**User's choice:** Linear only
**Notes:** All three clinical pathways are linear (6 steps each). DAG complexity not needed for V1.

### Time Window Anchoring

| Option | Description | Selected |
|--------|-------------|----------|
| Previous step completion | Default: windowDays from previous step completion. Step 1 from diagnosis date. | |
| Configurable anchor per step | Each step specifies anchor: previous_step, diagnosis_date, or specific stepId. | ✓ |

**User's choice:** Configurable anchor per step
**Notes:** Needed for breast cancer step 5 (Med Onc Visit checks Oncotype DX completion status, not timing from previous step).

### Optional Steps

| Option | Description | Selected |
|--------|-------------|----------|
| Required field per step | Each step has required boolean. Required = OPEN alert, optional = softer warning. | ✓ |
| All steps required in V1 | Simplifies logic but doesn't match spec P-05. | |

**User's choice:** Required field per step

---

## Monitoring Cadence & Triggers

### Detection Approach

| Option | Description | Selected |
|--------|-------------|----------|
| Dual: event-driven + daily sweep | Re-evaluate on care event changes + daily timer sweep for time expirations. | ✓ |
| Timer-only (spec default) | Scheduled scan every 4 hours. Matches spec exactly but slower feedback. | |
| Event-driven only | Only evaluate on care event changes. Won't catch time-based expirations without timer. | |

**User's choice:** Dual: event-driven + daily sweep
**Notes:** Fast feedback on data entry plus safety net for calendar-based deadlines.

### Enrollment Trigger

| Option | Description | Selected |
|--------|-------------|----------|
| On patient creation | Auto-start pathway workflow when patient is created with cancer type. No separate step. | ✓ |
| Explicit enrollment action | Two-step: create patient, then assign pathway separately. More control, more friction. | |

**User's choice:** On patient creation

---

## Workflow Lifecycle

### Workflow Scope

| Option | Description | Selected |
|--------|-------------|----------|
| One per patient | Single long-running workflow per patient. Holds pathway state, receives signals. | ✓ |
| One per patient-pathway | Multiple workflows per patient possible. More complex for V1. | |

**User's choice:** One per patient

### Patient Deactivation

| Option | Description | Selected |
|--------|-------------|----------|
| Cancel workflow | Graceful termination: stop timers, close open alerts, record final state. | ✓ |
| Pause workflow | Pause without termination. Can resume if patient reactivated. More complex. | |

**User's choice:** Cancel workflow

### Pathway Completion

| Option | Description | Selected |
|--------|-------------|----------|
| Workflow completes naturally | Finishes when all steps confirmed Complete. Clean lifecycle. | ✓ |
| Workflow stays alive | Remains running after completion for late changes. Higher resource usage. | |

**User's choice:** Workflow completes naturally

---

## Clinical Pathway Content

### Pathway Seed Data

| Option | Description | Selected |
|--------|-------------|----------|
| Use spec as-is | Load three pathways exactly as defined in V1 Feature Spec via Flyway seed migration. | ✓ |
| Adapt with simplifications | Simplify pathways for initial testing. | |
| Placeholder only | Structure only, wait for oncologist review. | |

**User's choice:** Use spec as-is
**Notes:** Oncologist co-author wrote clinically accurate pathway definitions. Use them directly.

### Physician Override (PATH-08)

| Option | Description | Selected |
|--------|-------------|----------|
| Simple override flag per step | Mark step as intentionally reordered/skipped with reason. Workflow checks before alerting. | ✓ |
| Defer to Phase 3 | Build workflow support but no UI until Phase 3. | |

**User's choice:** Simple override flag per step

---

## Claude's Discretion

- Temporal namespace and task queue naming conventions
- Retry policies for activities (idempotent activities, backoff strategy)
- Daily sweep workflow structure (single parent vs individual child workflows)
- PHI handling in Temporal — UUIDs only in workflow inputs/payloads
- Temporal search attributes for patient/alert querying

## Deferred Ideas

- SMS alert delivery — V2
- Manual re-scan trigger — Phase 3 dashboard
- REST API endpoints — Phase 3
- Multi-pathway per patient — V2
