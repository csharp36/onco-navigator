---
status: partial
phase: 05-per-patient-pathway-dag
source: [05-VERIFICATION.md]
started: 2026-05-04T18:00:00Z
updated: 2026-05-04T18:00:00Z
---

## Current Test

[awaiting human testing]

## Tests

### 1. Template Picker renders in patient wizard
expected: Add Patient → Step 2 → select cancer type → "Pathway Setup" radio group appears with "Start from template" and "Build from documents" options
result: [pending]

### 2. DAG View renders depth-based tiered layout
expected: Open patient detail → pathway steps display with depth indentation (24px per level), Unicode branching indicators, correct status icons (green CheckCircle2, blue Circle, red AlertTriangle, dashed gray Circle, gray MinusCircle)
result: [pending]

### 3. Inline editor toggle and step CRUD
expected: Click "Edit Pathway" → button changes to "Done Editing" → add step form appears → can add/skip/unskip steps with live mutations
result: [pending]

### 4. Edge management with cycle detection
expected: In editor → expand Dependencies → add dependency between steps → try circular dependency → inline error message "Adding this dependency would create a circular reference"
result: [pending]

### 5. Responsive tablet layout
expected: Resize to 768px → pathway view stacks vertically → branching indicators and depth indentation still render correctly
result: [pending]

## Summary

total: 5
passed: 0
issues: 0
pending: 5
skipped: 0
blocked: 0

## Gaps
