---
status: partial
phase: 04-ai-document-ingestion
source: [04-VERIFICATION.md]
started: "2026-05-01T18:35:00Z"
updated: "2026-05-01T18:35:00Z"
---

## Current Test

[awaiting human testing]

## Tests

### 1. End-to-end document upload flow (dashboard)
expected: Drag PDF onto dashboard drop zone, 5-step processing modal completes, classification result shown, patient matching with ranked candidates, pre-filled care event wizard opens with extracted data
result: [pending]

### 2. Patient detail upload flow
expected: Upload PDF from patient detail page, matching is bypassed for pre-selected patient, pre-filled wizard opens directly
result: [pending]

### 3. Circuit breaker fallback UI
expected: Upload document with invalid/unavailable Claude API, amber banner with manual classification dropdown appears instead of error
result: [pending]

### 4. Inline PDF viewer
expected: Click "Preview full document" after saving a linked care event, Sheet panel opens with iframe PDF rendering and download button
result: [pending]

### 5. Responsive layout
expected: Drop zone and modal render correctly at tablet viewport width, no horizontal scrolling or broken layouts
result: [pending]

### 6. Code review findings
expected: Developer reviews 5 CRITICAL and 7 WARNING findings from 04-REVIEW.md, decides on fix priority (security hardening items, not functional blockers)
result: [pending]

## Summary

total: 6
passed: 0
issues: 0
pending: 6
skipped: 0
blocked: 0

## Gaps
