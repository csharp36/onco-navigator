---
status: partial
phase: 06-ai-step-extraction-from-clinical-documents
source: [06-VERIFICATION.md]
started: 2026-05-05T03:00:00.000Z
updated: 2026-05-05T03:00:00.000Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. End-to-End Upload to Proposed Steps
Upload a synthetic clinical PDF with `ONCO_AI_STEP_EXTRACTION_ENABLED=true`, wait for async extraction, navigate to patient pathway editor, verify PROPOSED steps appear with AI Proposed badge, dashed border, source filename, and Confirm/Edit/Reject buttons (no auto-ACTIVE steps).
expected: PROPOSED steps render with correct visual treatment and source attribution
result: [pending]

### 2. Confirm Step triggers Temporal Re-evaluation
Click Confirm on a PROPOSED step, verify step transitions to ACTIVE in UI, inspect Temporal UI to confirm a `pathwayStepsChanged` signal event appears in workflow history.
expected: Step becomes ACTIVE, Temporal signal fires, pathway re-evaluates
result: [pending]

### 3. Reject Step hides and prevents re-proposal
(a) Reject a step via dialog, verify it moves to collapsible toggle; (b) upload second document with same event type, verify no new PROPOSED step is created (dedup against REJECTED).
expected: Rejected steps hidden, dedup prevents re-creation
result: [pending]

### 4. Already in Pathway Section (D-10)
Upload a document mentioning an event type already tracked as ACTIVE. Verify the "Already in pathway" Card renders in PathwayEditor listing those event types.
expected: Card shows event types Claude found but skipped
result: [pending]

## Summary

total: 4
passed: 0
issues: 0
pending: 4
skipped: 0
blocked: 0

## Gaps
