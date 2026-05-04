# Phase 6: AI Step Extraction from Clinical Documents - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-04
**Phase:** 06-ai-step-extraction-from-clinical-documents
**Areas discussed:** Extraction trigger, Nurse review UX, Duplicate handling, Edge inference

---

## Extraction Trigger

### When should step extraction happen?

| Option | Description | Selected |
|--------|-------------|----------|
| Automatic on upload | After document classification + patient matching succeeds, extraction runs as the next pipeline step in DocumentProcessingService. No extra user action. | ✓ |
| Separate button | After document is uploaded and linked, a nurse clicks "Extract Steps" on the patient detail page. | |
| Both paths | Auto-extract during upload AND allow re-extraction from the patient detail page. | |

**User's choice:** Automatic on upload (Recommended)
**Notes:** Natural extension of existing pipeline.

### When Claude extraction fails, what should happen?

| Option | Description | Selected |
|--------|-------------|----------|
| Silent skip + log | Document upload succeeds normally. Step extraction just doesn't happen. Log entry records failure. | |
| Toast notification | Same as silent skip but show a brief non-blocking toast to the nurse. | |
| You decide | Let Claude pick the right fallback behavior based on existing patterns. | ✓ |

**User's choice:** You decide
**Notes:** Deferred to Claude's discretion based on existing fallback patterns.

### Should extraction use the same Claude call as classification, or separate?

| Option | Description | Selected |
|--------|-------------|----------|
| Separate call | Distinct prompts, independent circuit breakers, independent feature flags. | ✓ |
| Combined single call | One call does both. Fewer API calls but failure coupling. | |
| You decide | Let Claude pick based on existing architecture. | |

**User's choice:** Separate call (Recommended)
**Notes:** Clean separation of concerns.

### Should Claude receive the patient's existing pathway steps as context?

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, send existing steps | Send step names and statuses (non-PHI) so Claude avoids re-proposing existing steps. | ✓ |
| No, extract blind | Send only document text. Simpler prompt. | |
| You decide | Let Claude pick. | |

**User's choice:** Yes, send existing steps (Recommended)
**Notes:** Enables Claude to avoid duplicates and suggest relative positioning.

---

## Nurse Review UX

### How should PROPOSED steps appear in the pathway view?

| Option | Description | Selected |
|--------|-------------|----------|
| Inline with visual distinction | Dashed border, gray/muted styling, "AI Proposed" badge. In the pathway list at inferred position. | ✓ |
| Separate "Proposed Steps" section | Collapsible section below the active pathway. | |
| Notification panel | Alert-card style review items. | |

**User's choice:** Inline with visual distinction (Recommended)
**Notes:** Consistent with Phase 5 D-15 visual treatment for PROPOSED status.

### How should the nurse confirm or reject proposed steps?

| Option | Description | Selected |
|--------|-------------|----------|
| Per-step inline buttons | Each PROPOSED step row shows Confirm/Reject icons. Quick, no modal. | ✓ |
| Batch review dialog | "Review Proposed Steps" button opens dialog with checkboxes. | |
| Both options | Inline buttons + batch "Review All" button for multiple steps. | |

**User's choice:** Per-step inline buttons (Recommended)
**Notes:** Works naturally with existing pathway editor pattern.

### Should the nurse be able to edit a proposed step before confirming?

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, edit then confirm | Nurse can modify name/time window/event type before confirming. Uses existing edit UI. | ✓ |
| Confirm as-is only | Confirm or reject only. Edit requires reject + manual re-add. | |
| You decide | Let Claude pick based on existing patterns. | |

**User's choice:** Yes, edit then confirm (Recommended)
**Notes:** Claude's extraction is a starting point, not final.

### What happens to rejected steps?

| Option | Description | Selected |
|--------|-------------|----------|
| Soft delete with REJECTED status | Stays in DB for audit trail. Hidden by default, visible via toggle. | ✓ |
| Hard delete | Removed entirely. No record of what was proposed. | |
| You decide | Let Claude pick based on HIPAA audit requirements. | |

**User's choice:** Soft delete with REJECTED status
**Notes:** Preserves full history, prevents re-proposing same steps.

---

## Duplicate Handling

### How should duplicates be handled?

| Option | Description | Selected |
|--------|-------------|----------|
| Claude filters + backend validates | Prompt instructs no duplicates; backend also checks. Belt and suspenders. | ✓ |
| Claude filters only | Trust prompt to exclude duplicates. | |
| Backend validates only | Let Claude extract everything, backend filters matches. | |

**User's choice:** Claude filters + backend validates (Recommended)
**Notes:** Safety net for hallucination or prompt drift.

### How fuzzy should the backend duplicate match be?

| Option | Description | Selected |
|--------|-------------|----------|
| Event type match | Same CareEventType = duplicate. Simple, deterministic, structured enum. | ✓ |
| Name similarity + event type | Fuzzy string + enum check. More edge cases caught but complexity. | |
| You decide | Let Claude pick. | |

**User's choice:** Event type match (Recommended)
**Notes:** Event types are constrained; step name wording varies.

### Should duplicates be silently skipped or shown?

| Option | Description | Selected |
|--------|-------------|----------|
| Silently skip | Don't create PROPOSED. Nurse never sees it. | |
| Show as "already covered" | Separate informational section showing what was found but already exists. | ✓ |
| You decide | Let Claude pick. | |

**User's choice:** Show as "already covered"
**Notes:** Transparency into what Claude found vs. what's genuinely new.

---

## Edge Inference

### Should Claude propose ordering/dependency edges?

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, propose edges | Claude infers ordering from document. Proposed edges as dashed connectors. | ✓ |
| No, flat steps only | Steps without ordering. Nurse wires edges manually. | |
| Edges to existing steps only | Edges connecting new to existing only, not new-to-new. | |

**User's choice:** Yes, propose edges (Recommended)
**Notes:** Gives nurse richest starting point to approve.

### How should proposed edges be confirmed?

| Option | Description | Selected |
|--------|-------------|----------|
| Bundled with step | Confirming step confirms its edges. Rejecting removes edges too. | ✓ |
| Separate edge review | Steps and edges confirmed independently in two passes. | |
| You decide | Let Claude pick. | |

**User's choice:** Bundled with step (Recommended)
**Notes:** One action per step, edges come along for the ride.

### Should Claude propose edges connecting new steps to existing active steps?

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, full graph | Edges from existing to new and new to new. Richest starting point. | ✓ |
| New-to-new only | Only edges between extracted steps. Connection to existing left to nurse. | |
| You decide | Let Claude pick. | |

**User's choice:** Yes, full graph (Recommended)
**Notes:** Justified by pathway step context already being sent to Claude.

---

## Claude's Discretion

- Extraction failure UX (silent skip vs toast vs other — follow existing fallback patterns)
- Prompt template structure and structured output schema
- stepExtractionClient ChatClient bean configuration (temperature, max tokens)
- PROPOSED step positioning logic in DAG view
- Proposed edge visual rendering
- "Already covered" section UI treatment
- "Show rejected" toggle design
- Circuit breaker naming (shared vs separate)

## Deferred Ideas

None — discussion stayed within phase scope.
