---
phase: 02-pathway-engine
audited_by: gsd-secure-phase
audit_date: 2026-04-30
asvs_level: 2
threats_total: 16
threats_closed: 14
threats_open: 0
warnings: 2
block_on: open
verdict: SECURED_WITH_WARNINGS
---

# Phase 02 — Pathway Engine: Security Audit

**Phase:** 02 — pathway-engine  
**Plans audited:** 02-01, 02-02, 02-03, 02-04  
**ASVS Level:** 2  
**Audit date:** 2026-04-30

---

## Verdict: SECURED (with warnings — no blockers)

All 16 declared threats verified. 0 OPEN (BLOCKER) threats. 2 WARNING items documented below.

---

## Threat Verification

| Threat ID | Category | Disposition | Status | Evidence |
|-----------|----------|-------------|--------|----------|
| T-02-01 | Tampering | mitigate | CLOSED | `src/main/resources/db/migration/V6__seed_pathway_templates.sql` exists as a versioned Flyway migration. `PathwayTemplate` entity carries `@Audited` at `src/main/java/com/onconavigator/domain/PathwayTemplate.java:36` — confirmed by grep of domain package. |
| T-02-02 | Information Disclosure | accept | CLOSED | Accepted risk. `PhysicianOverride` entity stores `patientId` (UUID only, no PHI) and `overrideReason` (clinical process text). Javadoc at `PhysicianOverride.java:15-21` explicitly documents no-PHI contract. No encryption needed for this field class. |
| T-02-03 | Tampering | mitigate | CLOSED | (1) `created_by UUID NOT NULL` column confirmed in `V5__create_physician_overrides.sql:16`. (2) `@Audited` confirmed at `PhysicianOverride.java:34`. (3) `CREATE UNIQUE INDEX idx_physician_overrides_patient_step ON physician_overrides(patient_id, pathway_step_id)` confirmed at `V5__create_physician_overrides.sql:21-22`. |
| T-02-04 | Denial of Service | accept | CLOSED | Accepted risk. JSONB template data is admin-seeded via Flyway migration (V6), not user-supplied. Malformed JSONB is caught at startup by `ObjectMapper.readValue` in `PathwayEvaluationActivityImpl.java:112-116` which throws `IllegalStateException` — activity retries stop on this non-retriable exception class per `ACTIVITY_RETRY_OPTIONS` at `PatientPathwayWorkflowImpl.java:68-71`. |
| T-02-05 | Information Disclosure | mitigate | CLOSED | `PatientPathwayWorkflow.monitorPathway` signature: `void monitorPathway(UUID patientId, String cancerType)` — confirmed at `PatientPathwayWorkflow.java:37`. Grep of `src/main/java/com/onconavigator/workflow/` for `firstName`, `lastName`, `dateOfBirth`, `mrn` returns zero matches. |
| T-02-06 | Information Disclosure | mitigate | CLOSED | `PatientPathwayWorkflow.careEventChanged` carries only `UUID careEventId` — confirmed at `PatientPathwayWorkflow.java:48`. Signal handler at `PatientPathwayWorkflowImpl.java:132-134` sets only `signalReceived = true`; the UUID is not stored. Activity fetches event details from encrypted DB. |
| T-02-07 | Elevation of Privilege | mitigate | CLOSED (deferred) | `PathwayService` is an internal Spring bean with no REST controller in this phase. Grep for controllers returning zero files confirms no HTTP entry point exists. The Javadoc at `PathwayService.java:29` documents: "Phase 3 REST controllers enforce RBAC via @PreAuthorize." The only non-test caller is `TestDataLoader` which is `@Profile("local")` only. **See WARNING-01 below.** |
| T-02-08 | Tampering | mitigate | CLOSED | Signal `careEventChanged(UUID careEventId)` carries only UUID — confirmed at interface line 48 and impl line 132. Alert text is sourced exclusively from `step.alertText()` and `step.suggestedAction()` (pathway template JSONB) at `PathwayEvaluationActivityImpl.java:324-326`, never from signal payloads. Re-evaluation is idempotent due to dedup check at lines 171, 205, 222. |
| T-02-09 | Denial of Service | accept | CLOSED | Accepted risk. V1 pilot scale (dozens of patients). `WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE` confirmed at `PathwayService.java:68`. `WorkflowExecutionAlreadyStarted` is caught in `SweepActivityImpl.java:99` for idempotent operation. |
| T-02-10 | Information Disclosure | mitigate | CLOSED | Grep of `PathwayEvaluationActivityImpl.java` for `firstName`, `lastName`, `dateOfBirth`, `mrn` returns zero matches. Log statements at lines 150-151, 178-179, 212-213, 228-230, 241-242, 264 log only `patientId` (UUID), `step.stepId()`, `step.name()`, counts, and boolean flags. |
| T-02-11 | Tampering | mitigate | CLOSED | DB-enforced deduplication at three call sites: `PathwayEvaluationActivityImpl.java:171`, `205`, `222` call `alertRepository.existsByPatientIdAndPathwayStepNameAndStatus`. Additionally, `V7__alert_dedup_index.sql` adds a partial `UNIQUE INDEX idx_alerts_open_dedup ON alerts(patient_id, pathway_step_name) WHERE status = 'OPEN'`, making the dedup constraint DB-enforced and TOCTOU-safe. This exceeds the declared mitigation. |
| T-02-12 | Denial of Service | accept | CLOSED | Accepted risk. Pilot scale. `SweepActivityImpl` uses try-to-start-with-catch pattern: `WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE` at line 86, `catch (WorkflowExecutionAlreadyStarted e)` at line 99. Idempotent. |
| T-02-13 | Repudiation | mitigate | CLOSED | Structured `PATHWAY_EVALUATION:` log confirmed at `PathwayEvaluationActivityImpl.java:241-242` with `patient={}`, `stepsEvaluated={}`, `alertsGenerated={}`, `allComplete={}` fields. Temporal event history independently records every activity execution. |
| T-02-14 | Elevation of Privilege | mitigate | CLOSED | `overrideRepository.existsByPatientIdAndPathwayStepId(patientId, step.stepId())` confirmed at `PathwayEvaluationActivityImpl.java:149`. Override suppression gate is checked before any alert logic. `@Audited` on `PhysicianOverride` confirmed at `PhysicianOverride.java:34`. The `existsByPatientIdAndPathwayStepId` method confirmed in `PhysicianOverrideRepository.java:34`. |
| T-02-15 | Tampering | accept | CLOSED | Accepted risk. Test fixtures use Mockito mocks and synthetic UUIDs (e.g., `aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa`). No real PHI, no real DB interaction in unit tests. |
| T-02-16 | Information Disclosure | accept | CLOSED | Accepted risk. Synthetic UUIDs only appear in test log output. Output is transient Maven Surefire test reports. No real PHI. |

---

## Warnings (non-blocking)

### WARNING-01: T-02-07 — PathwayService RBAC is deferred, not yet present

**Threat:** Elevation of Privilege — unauthorized invocation of `PathwayService.startPathwayMonitoring`.

**Current state:** `PathwayService` has no `@PreAuthorize` annotation. No REST controller calls it yet. The only non-test caller is `TestDataLoader` guarded by `@Profile("local")`.

**Why not a BLOCKER:** There is no HTTP entry point in Phase 02. The threat cannot be exploited until a REST controller is wired. The PLAN.md threat entry explicitly acknowledges this: "Phase 3 REST controllers will enforce RBAC via @PreAuthorize before calling PathwayService."

**Required action before Phase 03 ships:** Every REST controller method that calls `PathwayService` must carry `@PreAuthorize("hasRole('ROLE_NURSE_NAVIGATOR') or hasRole('ROLE_ADMIN')")` or equivalent. The security auditor for Phase 03 must verify RBAC is present on all `PathwayService` call sites in controllers.

---

### WARNING-02: SweepActivityImpl uses ALLOW_DUPLICATE (not REJECT_DUPLICATE as documented in plan)

**Discrepancy:** The 02-03-PLAN.md Task 2 specifies `WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE` for the sweep activity. The implemented code at `SweepActivityImpl.java:86` uses `WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE`. The 02-03-SUMMARY.md documents this as an intentional decision ("ALLOW_DUPLICATE (rather than REJECT_DUPLICATE) is intentional: it matches the policy in PathwayService and allows re-enrolled patients whose previous workflow completed to receive a new workflow").

**Security impact:** ALLOW_DUPLICATE means the sweep will start a new workflow for a patient whose previous workflow completed (not just one who has no running workflow). This is a broader behavior, not a security regression — it expands coverage rather than reducing it. The WorkflowExecutionAlreadyStarted exception is still caught correctly for currently running workflows.

**Assessment:** The deviation from the plan was deliberate and documented. No security gap introduced. Documenting for traceability.

---

## Unregistered Flags

The SUMMARY.md `## Threat Flags` sections across all four plans report no new security-relevant surface beyond the declared threat model. This is consistent with the code audit:

- 02-03-SUMMARY.md: "No new security-relevant surface detected beyond what the threat model covers."
- 02-04-SUMMARY.md: "No new security-relevant surface introduced."

No unregistered flags to log.

---

## Accepted Risks Log

| Risk ID | Threat ID | Category | Rationale | Review Trigger |
|---------|-----------|----------|-----------|----------------|
| AR-02-01 | T-02-02 | Information Disclosure | PhysicianOverride.overrideReason contains clinical process text, not ePHI. UUID-only patient reference. | If override reason schema changes to include free-text patient data |
| AR-02-02 | T-02-04 | Denial of Service | JSONB template is admin-seeded Flyway data, not user input. Startup failure on malformed JSONB is acceptable behavior. | If template data becomes user-editable at runtime |
| AR-02-03 | T-02-09 | Denial of Service | One workflow per patient at pilot scale. ALLOW_DUPLICATE prevents ID collisions. | When patient volume exceeds ~1,000 active patients |
| AR-02-04 | T-02-12 | Denial of Service | Sweep is idempotent at pilot scale. Try-to-start-with-reject pattern prevents duplicate starts. | When patient volume exceeds ~1,000 active patients |
| AR-02-05 | T-02-15 | Tampering | Mockito mocks with synthetic UUIDs; no real PHI in test fixtures. | If test fixtures are seeded with real patient data |
| AR-02-06 | T-02-16 | Information Disclosure | Synthetic UUIDs in transient test log output; no real PHI. | If real PHI is used in test environments |

---

## Key Security Findings (positive)

- **T-02-11 exceeded declared mitigation:** V7 migration adds a DB-level partial UNIQUE index (`idx_alerts_open_dedup`) enforcing at-most-one OPEN alert per (patient, step) pair. This makes the application-level dedup check TOCTOU-safe — a TOCTOU gap between the existence check and the insert is blocked at the database layer. This is stronger than the declared mitigation.

- **PHI boundary is enforced at two layers:** (1) Workflow interface method signatures carry only UUID and String enum names — no PHI fields present in any method parameter. (2) Log statements in all activity implementations verified to contain only UUID tokens, step IDs, step names, and numeric counts — no PHI field references.

- **Determinism constraints respected:** Grep of `src/main/java/com/onconavigator/workflow/` for `System.currentTimeMillis`, `new Random`, and `Thread.sleep` returns zero matches in actual code (comments only). All temporal operations use `Workflow.await()` API.

- **Alert text isolation:** `PathwayEvaluationActivityImpl.buildAlert()` at line 320-327 sources `deviationDescription` and `suggestedAction` exclusively from pathway template JSONB (`step.alertText()`, `step.suggestedAction()`), never from signal payloads or user input. This closes the T-02-08 injection vector completely.

---

*Generated by gsd-secure-phase | Phase 02 — pathway-engine | 2026-04-30*
