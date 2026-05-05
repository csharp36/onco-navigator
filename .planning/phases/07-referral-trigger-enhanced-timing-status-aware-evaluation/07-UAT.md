---
status: complete
phase: 07-referral-trigger-enhanced-timing-status-aware-evaluation
source: [07-01-SUMMARY.md, 07-02-SUMMARY.md, 07-03-SUMMARY.md, 07-04-SUMMARY.md]
started: 2026-05-05T18:35:00Z
updated: 2026-05-05T18:45:00Z
---

## Current Test

[testing complete]

## Tests

### 1. Cold Start Smoke Test
expected: Kill any running services. Run `docker compose up -d` then `./mvnw spring-boot:run`. Flyway V17+V18 migrations run successfully. Application starts without exceptions.
result: pass

### 2. Care Event Form — Scheduling Fields (SCHEDULED/PENDING)
expected: Open QuickAddCareEventDialog for a patient. Select status "SCHEDULED" or "PENDING". An "Expected Completion Date" date picker and "Scheduling confirmed with external facility" checkbox appear below the status field.
result: pass

### 3. Care Event Form — Scheduling Fields Hidden (COMPLETED)
expected: In the same dialog, switch status to "COMPLETED". The expected completion date and scheduling confirmed checkbox disappear. Only the "External Facility" text field remains visible.
result: pass

### 4. Care Event Form — External Facility Always Visible
expected: Regardless of status selection, the "External Facility" text input with placeholder "e.g., Memorial Hospital Radiology" is always visible at the bottom of the form.
result: pass

### 5. Alert Severity Badges — New Types
expected: If any of the new alert types exist (CANCELLED, RESULTS PENDING, DEADLINE, UNCONFIRMED), their badges display with correct colors: CANCELLED in red/destructive (same as OVERDUE), RESULTS PENDING and DEADLINE in default variant, UNCONFIRMED in secondary variant.
result: pass

### 6. Referral PDF Upload Sets referralReceivedAt
expected: Upload one of the test referral PDFs (test-corpus/referral/referral-radiation-oncology-01.pdf) via the document drop zone on a patient detail page. After classification completes, the patient's record should show a referralReceivedAt timestamp (check via API or database).
result: pass

### 7. Referral Upload — Second Upload Does Not Overwrite
expected: Upload a second referral PDF for the same patient. The original referralReceivedAt timestamp should NOT change (first-referral-only semantics).
result: pass

## Summary

total: 7
passed: 7
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

[none]
