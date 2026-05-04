---
phase: 05-per-patient-pathway-dag
reviewed: 2026-05-04T00:00:00Z
depth: standard
files_reviewed: 42
files_reviewed_list:
  - src/main/java/com/onconavigator/domain/enums/PathwayStepStatus.java
  - src/main/java/com/onconavigator/domain/PatientPathway.java
  - src/main/java/com/onconavigator/domain/PatientPathwayEdge.java
  - src/main/java/com/onconavigator/domain/PatientPathwayStep.java
  - src/main/java/com/onconavigator/repository/AlertRepository.java
  - src/main/java/com/onconavigator/repository/PatientPathwayEdgeRepository.java
  - src/main/java/com/onconavigator/repository/PatientPathwayRepository.java
  - src/main/java/com/onconavigator/repository/PatientPathwayStepRepository.java
  - src/main/java/com/onconavigator/service/PathwayForkService.java
  - src/main/java/com/onconavigator/service/PathwayService.java
  - src/main/java/com/onconavigator/service/PathwayStatusService.java
  - src/main/java/com/onconavigator/service/PatientPathwayService.java
  - src/main/java/com/onconavigator/service/PatientService.java
  - src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java
  - src/main/java/com/onconavigator/web/PatientPathwayController.java
  - src/main/java/com/onconavigator/web/dto/CreatePatientRequest.java
  - src/main/java/com/onconavigator/web/dto/PathwayEdgeRequest.java
  - src/main/java/com/onconavigator/web/dto/PathwayEdgeResponse.java
  - src/main/java/com/onconavigator/web/dto/PathwayStepRequest.java
  - src/main/java/com/onconavigator/web/dto/PathwayStepResponse.java
  - src/main/java/com/onconavigator/web/dto/PathwayStepStatus.java
  - src/main/java/com/onconavigator/web/dto/SkipStepRequest.java
  - src/main/java/com/onconavigator/workflow/PatientPathwayWorkflow.java
  - src/main/java/com/onconavigator/workflow/PatientPathwayWorkflowImpl.java
  - src/main/resources/db/migration/V12__update_pathway_time_windows.sql
  - src/main/resources/db/migration/V13__create_pathway_step_status_enum.sql
  - src/main/resources/db/migration/V14__create_per_patient_pathway_tables.sql
  - src/main/resources/db/migration/V15__migrate_patients_to_per_patient_pathways.sql
  - frontend/src/app.css
  - frontend/src/components/ui/collapsible.tsx
  - frontend/src/components/ui/radio-group.tsx
  - frontend/src/features/patients/AddStepForm.tsx
  - frontend/src/features/patients/api.ts
  - frontend/src/features/patients/EdgeEditor.tsx
  - frontend/src/features/patients/PathwayDAGView.tsx
  - frontend/src/features/patients/PathwayEditor.tsx
  - frontend/src/features/patients/PatientWizard.tsx
  - frontend/src/features/patients/SkipStepDialog.tsx
  - frontend/src/features/patients/StepRow.tsx
  - frontend/src/features/patients/TemplatePicker.tsx
  - frontend/src/features/patients/types.ts
  - frontend/src/routes/patients/$patientId.tsx
findings:
  critical: 7
  warning: 8
  info: 3
  total: 18
status: issues_found
---

# Phase 05: Code Review Report

**Reviewed:** 2026-05-04T00:00:00Z
**Depth:** standard
**Files Reviewed:** 42
**Status:** issues_found

## Summary

This phase introduces the per-patient pathway DAG: database schema (V13–V15), domain entities, service layer (PathwayForkService, PatientPathwayService, PathwayStatusService), a Temporal evaluation activity rewrite, a new REST controller, and a full frontend DAG visualization and editor. The implementation is architecturally sound — Kahn's algorithm for topological sort, DFS cycle detection, BOLA ownership verification, and `@Audited` on all new entities are all present and correct.

However, seven blockers were found. The most severe are:

1. **Logic error in `allStepsComplete` evaluation** — the evaluation engine returns `true` when PROPOSED steps are still outstanding, causing the Temporal workflow to terminate monitoring prematurely on pathways that have unconfirmed AI-proposed steps.
2. **Missing `@Transactional` on `PatientService.createPatient`** — patient save, pathway fork, and Temporal start are not atomic; a fork failure leaves an orphan patient row with no pathway and no workflow.
3. **`deleteBySourceStepIdOrTargetStepId` lacks `@Transactional` and `@Modifying`** — Spring Data JPA derived-delete methods require an explicit transaction annotation; without it this executes outside a transaction and the deletion may silently fail in some contexts.
4. **V15 migration: `jsonb_array_length(tmpl->'prerequisites')` crashes when `prerequisites` key is absent or is JSON `null`** — `jsonb_array_length` returns `null` (not 0) for SQL `NULL`, and throws an error when the key is missing. This breaks the migration for any template step that omits the `prerequisites` field entirely.
5. **PHI in Temporal alert summary strings** — `PathwayEvaluationActivityImpl.createAlertIfNotDuplicate` builds the return string with `step.getName()` inline, and that string enters the `PathwayEvaluationResult` that is serialized into Temporal's event history.
6. **Log argument transposition in `ALERT_CREATED`** — `alertType` and `patient.getId()` are swapped in the SLF4J argument list, causing the wrong values to be logged.
7. **`pathwayMode` accepts arbitrary string values** — `CreatePatientRequest` accepts any string for `pathwayMode`, and `effectivePathwayMode()` only defaults on blank/null; passing `"manual"` or any non-"empty" string silently triggers the template fork path, which may 404 for unrecognized cancer types.

---

## Critical Issues

### CR-01: `allStepsComplete` incorrectly returns `true` when PROPOSED steps are still outstanding

**File:** `src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java:244`

**Issue:** At line 244, `allStepsComplete` is set to `activeSteps.isEmpty()`. But `activeSteps` was populated at line 126 by querying only `ACTIVE`-status steps. A pathway that consists entirely of `PROPOSED` steps (Phase 6 AI extraction flow) has `activeSteps.isEmpty() == true` and therefore returns `allStepsComplete = true` to the Temporal workflow. The workflow then sets `pathwayComplete = true` and terminates monitoring permanently, abandoning the patient before any step has been confirmed or evaluated.

The secondary path (lines 130–136) that runs when `activeSteps.isEmpty()` is correctly guarded to check that all steps are `COMPLETED` or `SKIPPED`, but that branch returns early before the evaluation loop. Line 244 executes only after the evaluation loop runs, where no such guard exists.

**Fix:**
```java
// Line 244 — replace:
boolean allStepsComplete = activeSteps.isEmpty();

// With (mirrors the correct logic in lines 133-135):
boolean allStepsComplete = !activeSteps.isEmpty() && readySteps.stream()
        .allMatch(s -> s.getStatus() == PathwayStepStatus.COMPLETED
                    || s.getStatus() == PathwayStepStatus.SKIPPED);
// Or more precisely, re-query:
List<PatientPathwayStep> remainingAllSteps = stepRepository.findByPathway_Id(pathway.getId());
boolean allStepsComplete = !remainingAllSteps.isEmpty() && remainingAllSteps.stream()
        .allMatch(s -> s.getStatus() == PathwayStepStatus.COMPLETED
                    || s.getStatus() == PathwayStepStatus.SKIPPED);
```

---

### CR-02: `PatientService.createPatient` is not `@Transactional` — partial failure leaves orphan patient row

**File:** `src/main/java/com/onconavigator/service/PatientService.java:80`

**Issue:** `createPatient` (line 80) calls `patientRepository.save(patient)` (line 95), then `pathwayForkService.forkFromTemplate` (line 101), then `pathwayService.startPathwayMonitoring` (line 105). There is no `@Transactional` on the method. If `forkFromTemplate` throws (e.g., 404 — no template for this cancer type), the patient row is already committed to the database but has no pathway and no Temporal workflow. The patient record is permanently orphaned — it appears in nurse dashboards with no pathway and cannot be recovered programmatically.

`PathwayForkService.forkFromTemplate` is `@Transactional` but this only covers the fork transaction, not the outer patient creation. The Temporal workflow start on line 105 is intentionally outside any transaction (correct), but the `patientRepository.save` must be inside the same transaction as the fork.

**Fix:**
```java
@Transactional
public PatientResponse createPatient(CreatePatientRequest req, UUID actorId) {
    // ... existing code ...
    // Temporal start MUST remain outside the @Transactional scope.
    // Extract it to a separate non-transactional method or use
    // TransactionSynchronizationManager to schedule after commit.
}
```
The cleanest approach is to annotate `createPatient` with `@Transactional`, and call `pathwayService.startPathwayMonitoring` in a `TransactionSynchronizationManager.registerSynchronization` callback so the Temporal start only fires after the DB transaction commits successfully.

---

### CR-03: `deleteBySourceStepIdOrTargetStepId` missing `@Transactional` and `@Modifying` — deletion may silently fail

**File:** `src/main/java/com/onconavigator/repository/PatientPathwayEdgeRepository.java:39`

**Issue:** Spring Data JPA derived `delete*` methods require `@Transactional` and `@Modifying` to operate correctly. Without `@Transactional`, Spring Data wraps the method in a read-only transaction (the default), causing the deletion to be rolled back silently in some provider configurations. Without `@Modifying`, Hibernate may issue a `SELECT` followed by individual `DELETE` statements per entity rather than a single bulk `DELETE` — but more critically, outside a write transaction in certain Spring configurations the deletion is a no-op. When `deleteStep` calls this on line 221 of `PatientPathwayService`, the surrounding `@Transactional` on `deleteStep` provides a transaction, so under normal conditions this works. However, any caller that invokes this repository method without a surrounding `@Transactional` will silently leave stale edges in the database, leading to phantom prerequisites that corrupt DAG evaluation.

**Fix:**
```java
@Modifying
@Transactional
void deleteBySourceStepIdOrTargetStepId(UUID sourceStepId, UUID targetStepId);
```

---

### CR-04: V15 migration crashes when template steps have no `prerequisites` key (SQL NULL from `jsonb_array_length`)

**File:** `src/main/resources/db/migration/V15__migrate_patients_to_per_patient_pathways.sql:159-165`

**Issue:** In PHASE 2 of V15, line 159 uses `JOIN LATERAL jsonb_array_elements_text(tmpl->'prerequisites') AS prereq_id ON true`. If a template step's JSONB object has no `prerequisites` key at all, `tmpl->'prerequisites'` returns SQL `NULL`. Passing SQL `NULL` to `jsonb_array_elements_text` throws a PostgreSQL error: `ERROR: cannot call jsonb_array_elements_text on a non-array`. The `WHERE` clause guard on line 165 (`jsonb_array_length(tmpl->'prerequisites') > 0`) runs after the `JOIN LATERAL`, not before it, so it does not prevent the crash.

Additionally, `jsonb_array_length(tmpl->'prerequisites')` returns `NULL` (not `0`) when the key is absent — so the `> 0` predicate evaluates to `NULL` rather than `false`, and would not filter out such rows even if the join order allowed it.

This will crash the entire V15 migration for any practice that has template steps without an explicit `"prerequisites": []` entry.

**Fix:**
```sql
-- Replace line 159 with a null-safe lateral join:
JOIN LATERAL jsonb_array_elements_text(
    COALESCE(tmpl->'prerequisites', '[]'::jsonb)
) AS prereq_id ON true
-- Replace line 165 with a null-safe guard:
WHERE jsonb_array_length(COALESCE(tmpl->'prerequisites', '[]'::jsonb)) > 0
```

---

### CR-05: PHI (step names) in Temporal event history via `alertsGenerated` list

**File:** `src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java:211-237, 305`

**Issue:** `createAlertIfNotDuplicate` builds a return string containing the step name: `"MISSING_EVENT: step 'Surgery' for patient <UUID>"` (line 305). This string is added to the `alertsGenerated` list (lines 212, 224, 237), which is then included in `PathwayEvaluationResult` (line 248). The `PathwayEvaluationResult` is the return value of the Temporal activity `evaluate(UUID patientId)`. Temporal serializes activity return values into its event history and stores them in its PostgreSQL backend — which, while encrypted at rest, is a different encryption domain than the ePHI PostgreSQL database.

While step names (e.g., "Surgery", "Pathology Report") are clinical process data rather than patient identifiers, they are stored alongside the patient UUID in Temporal's history. Under HIPAA's minimum necessary standard, combining a patient UUID with detailed clinical procedure names in a secondary storage system (Temporal history) without explicit data classification is a HIPAA technical safeguard concern.

The `defaultDescription` strings on lines 211, 223, 237 also embed `step.getName()` directly.

**Fix:**
Return only step UUIDs in the `alertsGenerated` list, not human-readable strings containing step names:
```java
// Line 305 — replace:
return alertType.name() + ": step '" + step.getName() + "' for patient " + patient.getId();

// With (UUID only, zero clinical detail in Temporal history):
return alertType.name() + ":" + step.getId();
```
Update callers of `alertsGenerated` to use the UUID-based format, or make `PathwayEvaluationResult.alertsGenerated` a list of step UUIDs.

---

### CR-06: SLF4J argument transposition in `ALERT_CREATED` log statement

**File:** `src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java:304`

**Issue:** The log statement `log.info("ALERT_CREATED: patient={} step={} type={}", alertType, patient.getId(), step.getId())` has its arguments in the wrong order. The format string declares `patient={}` first, but `alertType` (an enum) is the first argument. The logged output will be `ALERT_CREATED: patient=MISSING_EVENT step=<patientUUID> type=<stepUUID>` — all three values are transposed. This corrupts audit log entries, making it impossible to trace alert creation accurately for HIPAA audit purposes.

**Fix:**
```java
log.info("ALERT_CREATED: patient={} step={} type={}", patient.getId(), step.getId(), alertType);
```

---

### CR-07: `pathwayMode` accepts arbitrary string values with no validation — silent misrouting

**File:** `src/main/java/com/onconavigator/web/dto/CreatePatientRequest.java:36-47`

**Issue:** The `pathwayMode` field accepts any `String` with no `@Pattern` or enum constraint. `effectivePathwayMode()` returns the raw value unchanged for any non-blank string. In `PatientService.createPatient` (line 98), the condition is `if ("empty".equals(...))`. Any value other than exactly `"empty"` — including `"Empty"`, `"EMPTY"`, `"manual"`, or a typo — silently triggers the template fork path. For cancer types with no template (e.g., a future expansion type), this causes a 500 from `PathwayForkService.forkFromTemplate` rather than a meaningful 400 validation error.

**Fix:**
```java
// In CreatePatientRequest, replace:
String pathwayMode  // "template" (default) or "empty" per D-07

// With a pattern constraint:
@Pattern(regexp = "^(template|empty)$", message = "pathwayMode must be 'template' or 'empty'")
String pathwayMode
```
Alternatively, use an enum type for `pathwayMode` so Jackson rejects unknown values before any service logic runs.

---

## Warnings

### WR-01: `PatientPathwayService.createStep` uses a second DB query to compute `sortOrder` — race condition possible

**File:** `src/main/java/com/onconavigator/service/PatientPathwayService.java:149`

**Issue:** After saving the new step, line 149 re-queries `stepRepository.findByPathway_Id(pathway.getId()).size() - 1` to determine its `sortOrder`. Since the save and the re-query are in the same transaction, the count includes the newly saved step, so `size() - 1` is the correct index in single-user scenarios. However, if two concurrent requests add steps to the same pathway simultaneously (both see N steps, both compute `N`, both return `sortOrder = N`), two steps are assigned the same `sortOrder`. The `sortOrder` field in the response DTO is informational only (computed at query time by Kahn's traversal), so this does not corrupt persistent state. But it means the response DTO returned from `createStep` has a stale `sortOrder` that may not match a subsequent `getSteps()` call.

**Fix:** Remove the `sortOrder` computation from `createStep`. It is not persisted — returning `sortOrder = -1` or computing it with the same `computeTopology` helper used by `getSteps` is more accurate. The current approach does more work than necessary and is misleading:
```java
// Replace lines 149-150 with a full topology computation,
// or simply return a placeholder sortOrder=-1 since the
// canonical order comes from getSteps():
return toStepResponse(step, 0, -1, List.of());
```

---

### WR-02: `updateStep` returns incorrect `depth=0, sortOrder=0` for non-root steps

**File:** `src/main/java/com/onconavigator/service/PatientPathwayService.java:199`

**Issue:** `updateStep` (and likewise `skipStep` at line 263, `unskipStep` at line 294) return `toStepResponse(step, 0, 0, prereqIds)` — hardcoding `depth=0` and `sortOrder=0` regardless of the step's actual topological position. If a nurse is watching the UI update optimistically after editing a step, the returned DTO will incorrectly show the step at depth 0 (root node) until the next full `getSteps()` refresh. In the DAG visualization, this could momentarily render the step at the wrong indentation level.

**Fix:** Call `computeTopology` (already available as a private helper in the same class) and extract the correct `depth` and `sortOrder` for the updated step before building the response, or accept a brief client-side stale display and document the limitation. The simplest correct fix:
```java
// In updateStep/skipStep/unskipStep, replace:
return toStepResponse(step, 0, 0, prereqIds);

// With:
List<StepWithDepth> topology = computeTopology(
    stepRepository.findByPathway_Id(step.getPathway().getId()),
    edgeRepository.findByPathway_Id(step.getPathway().getId()));
StepWithDepth swd = topology.stream()
    .filter(s -> s.step().getId().equals(stepId))
    .findFirst().orElse(new StepWithDepth(step, 0, 0, prereqIds));
return toStepResponse(swd.step(), swd.depth(), swd.sortOrder(), swd.prerequisiteIds());
```

---

### WR-03: `PathwayStatusService` and `PatientPathwayService` duplicate Kahn's algorithm with subtle differences

**File:** `src/main/java/com/onconavigator/service/PathwayStatusService.java:102-154`, `src/main/java/com/onconavigator/service/PatientPathwayService.java:402-489`

**Issue:** The Kahn's BFS algorithm for topological ordering is implemented in two separate service classes. The two implementations differ in their data structures (one uses `Set<UUID>` for adjacency, the other `List<UUID>`), their handling of orphaned nodes, and their depth calculation mechanics. This duplication is a maintenance hazard: clinical correctness of the DAG evaluation depends on this algorithm, and divergence between the two implementations will produce inconsistent sort orders and depths between the pathway status endpoint and the step CRUD endpoint.

**Fix:** Extract the common `computeTopology` logic (the full version in `PatientPathwayService`) into a shared `PathwayTopologyService` or utility class, and have both `PathwayStatusService` and `PatientPathwayService` delegate to it.

---

### WR-04: `EdgeEditor.handleAddSuccess` is defined but never called — add-dependency form does not reset after success

**File:** `frontend/src/features/patients/EdgeEditor.tsx:55-59`

**Issue:** `handleAddSuccess` (lines 55–59) clears `sourceStepId`, `targetStepId`, and closes `showAddForm`. It is also exported (line 221). However, it is never called from `PathwayEditor` or anywhere in the component. After a successful `createEdge` mutation, `addEdgeError` is cleared (via `onSuccess` in `PathwayEditor.handleAddEdge`), but the add-dependency selects and the open form state are not reset. The user must manually close the form after adding a dependency, and the previously selected source/target steps remain selected, which could confuse users into double-submitting the same edge.

**Fix:**
```typescript
// In PathwayEditor.tsx handleAddEdge onSuccess callback:
onSuccess: () => {
  setAddEdgeError(null);
  // Call a reset prop on EdgeEditor, or restructure so EdgeEditor
  // detects success from addEdgePending transitioning false without error:
},
```
The cleanest fix is to pass an `onAddSuccess` callback prop to `EdgeEditor` and call `handleAddSuccess` from there. The exported-but-dead `handleAddSuccess` should be removed.

---

### WR-05: `PathwayEditor.handleUpdateStep` always sets `required: true` regardless of step's actual value

**File:** `frontend/src/features/patients/PathwayEditor.tsx:196-204`

**Issue:** Lines 196–204 in `handleUpdateStep` compute `required` as:
```typescript
required: original?.prerequisiteStepIds !== undefined ? true : true,
```
Both branches of the ternary return `true`. The comment says "Look up the original step to get required field" but the lookup result is discarded. Every step edit via the inline edit form will force `required = true`, overwriting any step that was originally created as non-required (e.g., optional follow-up steps).

**Fix:**
```typescript
required: original?.required ?? true,
```

---

### WR-06: `PatientWizard` uses raw `fetch` for care event creation — bypasses `apiClient` error handling and query cache

**File:** `frontend/src/features/patients/PatientWizard.tsx:178-192`

**Issue:** Lines 178–192 use a raw `fetch` call (not TanStack Query `useMutation` / `apiClient`) to create a care event from a classified document. This violates the project's CLAUDE.md convention ("Do not use `useEffect` + `fetch` for API calls" — the same principle applies to ad-hoc `fetch` calls in mutation callbacks). The raw `fetch` bypasses: (1) the centralized `apiClient` base URL and authentication header injection, (2) error handling / retry logic, (3) query cache invalidation for the care events list. If the token fetch fails silently, the `Authorization` header is omitted and the request returns a 401 that is silently caught by the empty `catch` block (line 193).

**Fix:** Extract the care event creation into a reusable `useCreateCareEvent` call (the hook already exists in `api.ts`). The document link + care event creation should be a sequence of `useMutation.mutateAsync` calls using the existing hooks.

---

### WR-07: `SkipStepDialog` does not reset `reason` state when closed without confirming — stale reason reappears on reopen

**File:** `frontend/src/features/patients/SkipStepDialog.tsx:41-43`

**Issue:** `handleOpenChange(false)` calls `setReason('')` (line 42) correctly when the dialog closes via the `onOpenChange` prop. However, `handleConfirm` (line 37) calls `onConfirm(reason.trim())` without clearing `reason`. If the skip mutation fails and the user retries (closes and reopens the dialog), `reason` is cleared by `handleOpenChange`. But if the user clicks "Skip Step" while the mutation is pending and then the mutation succeeds, the dialog closes via the `onOpenChange` callback in the parent (`setSkipDialogStep(null)` in `PathwayEditor`). That path calls `handleOpenChange(false)` which does clear `reason`. So this path is actually safe. This is a minor quality concern rather than a bug — the code path is correct, but the intent is only clear on close inspection.

No code change is strictly required, but adding a `setReason('')` call after `onConfirm(reason.trim())` in `handleConfirm` would make the cleanup intent explicit.

---

### WR-08: `V14` migration grants `ALL` privileges to `onco_app` — violates least privilege for a write-append table

**File:** `src/main/resources/db/migration/V14__create_per_patient_pathway_tables.sql:80-82`

**Issue:** The migration grants `ALL ON patient_pathway_edges TO onco_app`. The design documentation (and the entity's Javadoc) states that edges are write-once — no update operations are permitted. However, `GRANT ALL` includes `UPDATE` and `TRUNCATE` privileges. A compromised application credential could update edge records that are supposed to be immutable, undermining the audit trail. The same concern applies to `patient_pathways` and `patient_pathway_steps` to a lesser extent (steps do need updates, but not all columns).

**Fix:**
```sql
-- For patient_pathway_edges (write-once per design):
GRANT SELECT, INSERT, DELETE ON patient_pathway_edges TO onco_app;
-- For patient_pathways and patient_pathway_steps:
GRANT SELECT, INSERT, UPDATE, DELETE ON patient_pathways TO onco_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON patient_pathway_steps TO onco_app;
```

---

## Info

### IN-01: `console.error` in `PatientWizard` silently swallows document-linking failures

**File:** `frontend/src/features/patients/PatientWizard.tsx:193-195`

**Issue:** The `catch` block on lines 193–195 calls `console.error('Failed to link document or create care event')` and then continues navigation to the patient page. The user receives no feedback that their uploaded document was not linked or that the care event was not created. In a clinical context where the document linkage is the user's primary intent, silently discarding the error could cause staff to believe the document was recorded when it was not.

**Fix:** Surface a non-blocking error notification (e.g., a toast or banner) to inform the user that the document association failed and they should manually add the care event. The navigation can still proceed, but the failure must be visible.

---

### IN-02: Topological sort does not preserve stable ordering within the same depth level

**File:** `src/main/java/com/onconavigator/service/PatientPathwayService.java:444-473`

**Issue:** Kahn's algorithm, as implemented, processes nodes at the same depth level in `HashSet` iteration order — which is non-deterministic across JVM restarts. Two steps at depth 1 (both depending only on a depth-0 root) may appear in different orders on different requests. This is unlikely to confuse nurses in practice (the DAG visualization shows depth position), but it means the `sortOrder` field in the response is non-deterministic for parallel steps.

**Fix:** Sort same-depth nodes by insertion order (use step `createdAt` as a tiebreaker) when adding to the BFS queue. Replace `new HashSet<>()` for the adjacency map values with a collection that preserves insertion order, or use a stable comparator when initializing the queue.

---

### IN-03: `CreatePatientRequest.dateOfBirth` typed as `String` with only `@NotBlank` — no date format validation

**File:** `src/main/java/com/onconavigator/web/dto/CreatePatientRequest.java:29`

**Issue:** `dateOfBirth` is a `String` with only `@NotBlank`. The field accepts any non-blank string including `"not-a-date"`, `"yesterday"`, or malformed ISO dates. The backend will attempt to store the raw string via the JPA entity's converter (which performs AES-GCM encryption on the raw string) — the invalid date won't be caught until something tries to parse it. The frontend sends a valid HTML `<input type="date">` value, but other API callers (integrations, tests) are unvalidated.

**Fix:**
```java
@NotBlank
@Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "dateOfBirth must be in YYYY-MM-DD format")
String dateOfBirth,
```
Or change the type to `LocalDate` and let Jackson's date deserializer enforce the format.

---

_Reviewed: 2026-05-04T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
