---
phase: 02-pathway-engine
status: partial
iteration: 1
findings_in_scope: 14
fixed: 8
skipped: 6
---

# Phase 02: Code Review Fix Report

**Fix scope:** critical + warning (14 findings)
**Iteration:** 1
**Build status:** BUILD SUCCESS — 40 tests, 0 failures

## Fixes Applied

| # | Finding | Severity | Commit | What Changed |
|---|---------|----------|--------|--------------|
| 1 | CR-01 — Alert dedup TOCTOU race | critical | `23333c6` | Added partial UNIQUE index `idx_alerts_patient_step_open` on `alerts(patient_id, pathway_step_name)` WHERE `status = 'OPEN'` via new Flyway migration V7 |
| 2 | CR-02 — `evaluate()` missing `@Transactional` | critical | `1dce12f` | Added `@Transactional` to `evaluate()` and `closeOpenAlerts()` in PathwayEvaluationActivityImpl |
| 3 | CR-03 — OUT_OF_ORDER fall-through double-alerting | critical | `a377ddb` | Added `continue` after OUT_OF_ORDER alert creation to skip MISSING/DELAYED check for same step |
| 4 | CR-04 — Hardcoded zero-key placeholder | critical | `6d8a0e9` | Added `EncryptionKeyValidator` startup check that rejects known-weak placeholder keys |
| 5 | CR-05 — Cancer type in exception messages (PHI in Temporal) | critical | `05ab5b7` | Removed cancer type from exception messages in PathwayEvaluationActivityImpl |
| 6 | CR-06 — Sweep uses REJECT_DUPLICATE vs PathwayService ALLOW_DUPLICATE | critical | `cb502de` | Changed SweepActivityImpl to use `ALLOW_DUPLICATE_FAILED_ONLY` to correctly handle re-enrolled patients |
| 7 | CR-07 — Missing @WorkflowImpl annotations | critical | `9762d30` | Added `@WorkflowImpl(taskQueues = TemporalConfig.TASK_QUEUE)` to both workflow implementations |
| 8 | WR-01 — IllegalStateException not in doNotRetry | warning | `ee438a6` | Added `IllegalStateException.class.getName()` to activity retry options doNotRetry list |

## Skipped (Not Auto-Fixed)

| # | Finding | Severity | Reason |
|---|---------|----------|--------|
| 1 | WR-02 — buildAlert() produces null workflowRunId | warning | Design decision — workflowRunId populated when called from workflow context; standalone calls legitimately have null |
| 2 | WR-03 — LocalDate.now() uses JVM default timezone | warning | Requires architecture decision on timezone strategy (UTC vs practice-local); deferring to Phase 3 when REST API establishes timezone handling |
| 3 | WR-04 — deactivatePatient reason is unvalidated free text | warning | Signal parameter validation requires Temporal interceptor pattern; low risk at V1 pilot scale |
| 4 | WR-05 — closeOpenAlerts() lacks @Transactional | warning | Fixed as part of CR-02 (both methods annotated) |
| 5 | WR-06 — ddl-auto: update in local profile | warning | Intentional for local dev — Flyway handles production; documented in CLAUDE.md |
| 6 | WR-07 — TemporalConfig CGLIB proxy issue | warning | Already fixed in Plan 02-04 with `proxyBeanMethods=false` |
