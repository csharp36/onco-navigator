---
phase: 02-pathway-engine
reviewed: 2026-04-30T00:00:00Z
depth: standard
files_reviewed: 25
files_reviewed_list:
  - pom.xml
  - src/main/java/com/onconavigator/activity/AlertGenerationActivity.java
  - src/main/java/com/onconavigator/activity/AlertGenerationActivityImpl.java
  - src/main/java/com/onconavigator/activity/PathwayEvaluationActivity.java
  - src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java
  - src/main/java/com/onconavigator/activity/SweepActivity.java
  - src/main/java/com/onconavigator/activity/SweepActivityImpl.java
  - src/main/java/com/onconavigator/config/TemporalConfig.java
  - src/main/java/com/onconavigator/domain/dto/AnchorType.java
  - src/main/java/com/onconavigator/domain/dto/CareEventSignal.java
  - src/main/java/com/onconavigator/domain/dto/PathwayEvaluationResult.java
  - src/main/java/com/onconavigator/domain/dto/PathwayStep.java
  - src/main/java/com/onconavigator/domain/PhysicianOverride.java
  - src/main/java/com/onconavigator/repository/PathwayTemplateRepository.java
  - src/main/java/com/onconavigator/repository/PhysicianOverrideRepository.java
  - src/main/java/com/onconavigator/service/PathwayService.java
  - src/main/java/com/onconavigator/workflow/DailySweepWorkflow.java
  - src/main/java/com/onconavigator/workflow/DailySweepWorkflowImpl.java
  - src/main/java/com/onconavigator/workflow/PatientPathwayWorkflow.java
  - src/main/java/com/onconavigator/workflow/PatientPathwayWorkflowImpl.java
  - src/main/resources/application-local.yml
  - src/main/resources/db/migration/V5__create_physician_overrides.sql
  - src/main/resources/db/migration/V6__seed_pathway_templates.sql
  - src/test/java/com/onconavigator/activity/AlertGenerationActivityTest.java
  - src/test/java/com/onconavigator/activity/PathwayEvaluationActivityTest.java
  - src/test/java/com/onconavigator/workflow/PatientPathwayWorkflowTest.java
findings:
  critical: 7
  warning: 7
  info: 3
  total: 17
status: issues_found
---

# Phase 02: Code Review Report

**Reviewed:** 2026-04-30
**Depth:** standard
**Files Reviewed:** 25
**Status:** issues_found

## Summary

This phase implements the Temporal workflow and activity layer for pathway deviation detection in a HIPAA-regulated oncology system. The overall architecture is sound — PHI is kept out of Temporal's event history, the dual-approach (signal + 24h timer) is correctly structured, and deduplication intent is present. However, there are several blockers that must be fixed before this code ships: a race condition in alert deduplication that allows duplicate PHI-adjacent records under concurrent retries, an alarm-silencing logic flaw that can cause OUT_OF_ORDER and MISSING/DELAYED alerts to permanently shadow each other, a hardcoded encryption key placeholder that will ship if not caught, missing `@Transactional` on multi-write operations, and `IllegalStateException` propagating from activity code with the cancer type embedded in the message (PHI-adjacent). There are also meaningful warnings around the `WORKFLOW_ID_REUSE_POLICY` inconsistency between `PathwayService` and `SweepActivityImpl`, missing no-retry declaration for `IllegalStateException`, and un-exercised code paths in the test suite.

---

## Critical Issues

### CR-01: Alert deduplication is a TOCTOU race — duplicate alerts can be created on Temporal retries

**File:** `src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java:169-209`

**Issue:** The deduplication pattern is a check-then-act sequence with no transaction boundary spanning both the existence check and the insert. `PathwayEvaluationActivityImpl.evaluate()` performs three separate pairs of `existsByPatientIdAndPathwayStepNameAndStatus` + `alertRepository.save()` calls (lines 169-175, 200-209, 217-227), and `AlertGenerationActivityImpl.generateAlert()` repeats the same pattern. Temporal retries the entire activity on transient failures. If two concurrent retries reach the existence check before either has committed, both will see `false` and both will insert, creating duplicate `OPEN` alerts for the same `(patient, step)` pair — defeating PATH-06. The `UNIQUE` index that would catch this does not exist on the `alerts` table (none is declared in V5 or elsewhere in the reviewed files).

**Fix:** Add a `UNIQUE` index on `alerts(patient_id, pathway_step_name, status)` (or better, on `alerts(patient_id, pathway_step_name)` filtered to `status = 'OPEN'` using a partial index) so the database enforces uniqueness as the last line of defense. Additionally, wrap each check+insert pair in a single `@Transactional` method with `SERIALIZABLE` isolation or use an `INSERT ... ON CONFLICT DO NOTHING` native query to make the operation atomic:

```sql
-- In a new migration:
CREATE UNIQUE INDEX idx_alerts_open_dedup
    ON alerts(patient_id, pathway_step_name)
    WHERE status = 'OPEN';
```

```java
// In PathwayEvaluationActivityImpl or a helper service:
@Transactional
protected void saveAlertIfNotDuplicate(UUID patientId, PathwayStep step, AlertType type) {
    if (!alertRepository.existsByPatientIdAndPathwayStepNameAndStatus(
            patientId, step.name(), AlertStatus.OPEN)) {
        alertRepository.save(buildAlert(patientId, step, type));
    }
}
```

---

### CR-02: `evaluate()` has no `@Transactional` annotation — partial alert writes are possible on failure mid-loop

**File:** `src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java:95`

**Issue:** `evaluate()` iterates over all pathway steps and calls `alertRepository.save()` multiple times (one per deviation detected). There is no enclosing transaction. If the JVM crashes or an exception is thrown after the first `save()` but before the second, the database will contain a partial set of alerts for this evaluation run. On the next Temporal retry the deduplication check will suppress the already-created alert but create the missed ones — meaning the evaluation is not truly atomic. For clinical correctness, either all alerts for an evaluation run should be saved or none (especially if `closeOpenAlerts` is involved in the same activity).

**Fix:** Annotate `evaluate()` with `@Transactional`. Spring's JPA integration will roll back all saves if any downstream exception is thrown before the method returns:

```java
@Override
@Transactional
public PathwayEvaluationResult evaluate(UUID patientId) { ... }
```

Similarly annotate `closeOpenAlerts()`:

```java
@Override
@Transactional
public void closeOpenAlerts(UUID patientId) { ... }
```

---

### CR-03: OUT_OF_ORDER logic silences the MISSING/DELAYED check for the same step — deviations go undetected

**File:** `src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java:164-228`

**Issue:** When a step has an event that is OUT_OF_ORDER, the code at line 179 comments "Even if out-of-order, still check for timing deviations below" but the `continue` at line 186 (`if (!step.required()) { continue; }`) only guards optional steps. However, the more subtle problem is at lines 164–180: the block detects OUT_OF_ORDER when `eventExists && !prerequisites.isEmpty() && prerequisitesMissing`. After creating the OUT_OF_ORDER alert, execution falls through to the MISSING/DELAYED block (lines 183+). But the MISSING/DELAYED block at line 197 tests `if (!eventExists)` — when we are in the OUT_OF_ORDER branch, `eventExists` is `true`, so the MISSING_EVENT branch is skipped (correct), but the DELAYED_EVENT branch (line 213) requires `hasScheduledOrPending`. If the out-of-order event is `SCHEDULED` or `PENDING`, **both** an OUT_OF_ORDER alert and a DELAYED_EVENT alert will be generated for the same step in the same evaluation run. Each dedup check uses a different `AlertType` but the same `(patient, step, OPEN)` key — however, the dedup key is only `(patient_id, pathway_step_name, status)` with no `alert_type` column, so the second write for a different type on the same `(patient, step)` will be suppressed by the dedup check only if the key matches. Since both alerts share the same step name, the second is correctly suppressed. BUT: the logic is inverted in the non-overlap case — when `eventExists=false`, the code can produce **no** OUT_OF_ORDER alert at all (because `eventExists` is `false`, the condition at line 165 is `false`), yet also correctly generates MISSING_EVENT. The true bug is that a step with `eventExists=true` and `prerequisites` met can never trigger the MISSING_EVENT branch — correct in isolation — but a step with `eventExists=false` and non-empty `prerequisites` skips the OUT_OF_ORDER check entirely. A patient who has *no event at all* for a step whose prerequisite is also incomplete will never get an OUT_OF_ORDER alert even if they somehow received downstream care, since there is no event to detect. This is not the dangerous direction; the dangerous direction is that when a prerequisite is not complete but **an event exists**, the code creates both OUT_OF_ORDER and (potentially) DELAYED alerts in the same run, which may confuse nurse navigators with two alerts for one step.

**Fix:** After an OUT_OF_ORDER alert is created, `continue` to the next step to avoid double-alerting on the same step within a single evaluation:

```java
if (prerequisitesMissing) {
    boolean isDuplicate = alertRepository.existsByPatientIdAndPathwayStepNameAndStatus(
            patientId, step.name(), AlertStatus.OPEN);
    if (!isDuplicate) {
        Alert alert = buildAlert(patientId, step, AlertType.OUT_OF_ORDER);
        alertRepository.save(alert);
        alertsGenerated.add("OUT_OF_ORDER: step '" + step.name() + "' for patient " + patientId);
        log.info("ALERT_CREATED: patient={} step={} type=OUT_OF_ORDER", patientId, step.stepId());
    }
    continue; // Do not double-alert with MISSING/DELAYED for an out-of-order step
}
```

---

### CR-04: Hardcoded AES key placeholder in committed config — will silently encrypt with all-zero key if unset

**File:** `src/main/resources/application-local.yml:42`

**Issue:** The encryption key is set to `AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=` (44 Base64 characters, decoding to 32 bytes of `0x00`). The comment correctly marks it as a placeholder, but there is no runtime validation that the key has been replaced before the application starts encrypting PHI. If a developer runs the application without setting a real key — which is the default since the placeholder is a valid Base64 string — all PHI fields will be encrypted with the all-zeroes key. This is effectively no encryption. In a HIPAA system this is a direct compliance failure: the system will silently claim to encrypt PHI while using a well-known, trivially reversible key.

**Fix:** Add a startup validator (implement `ApplicationRunner` or use `@PostConstruct`) that rejects the known-placeholder key at boot time:

```java
@Component
public class EncryptionKeyValidator implements ApplicationRunner {
    @Value("${onconavigator.encryption.key}")
    private String encryptionKey;

    private static final String PLACEHOLDER = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";

    @Override
    public void run(ApplicationArguments args) {
        if (PLACEHOLDER.equals(encryptionKey)) {
            throw new IllegalStateException(
                "FATAL: onconavigator.encryption.key is the placeholder value. " +
                "Generate a real key with: openssl rand -base64 32");
        }
    }
}
```

---

### CR-05: `IllegalStateException` thrown with cancer type in the message — potential PHI-adjacent data leaks into Temporal history and logs

**File:** `src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java:113-115`

**Issue:** When JSONB deserialization fails, the exception message at line 113–115 includes the patient's cancer type:

```java
throw new IllegalStateException(
    "Failed to deserialize pathway template for cancer type: " + patient.getCancerType(), e);
```

Cancer type (`CancerType`) is a diagnosis attribute. While it is an enum (BREAST, LUNG, COLORECTAL) and not a named identifier, in a HIPAA context a cancer type linked to a workflow ID (which contains the patient UUID) constitutes PHI context. Temporal records exception messages in its workflow event history, which is viewable in the Temporal Web UI and queryable via the Temporal API. Any staff member with Temporal UI access can see this linkage.

**Fix:** Remove the cancer type from the exception message. Use the patient UUID (already in scope) or the template ID if needed:

```java
throw new IllegalStateException(
    "Failed to deserialize pathway template (templateId=" + template.getId() + ")", e);
```

Similarly, review line 106 — `patient.getCancerType()` in the `IllegalArgumentException` message for missing template:

```java
throw new IllegalArgumentException("No pathway template for cancer type: " + patient.getCancerType());
```

This also embeds cancer type in the exception which Temporal will record. Replace with the template ID lookup key or a generic message.

---

### CR-06: `WorkflowIdReusePolicy` inconsistency between `PathwayService` and `SweepActivityImpl` — sweep can silently fail to start workflows for re-enrolled patients

**File:** `src/main/java/com/onconavigator/activity/SweepActivityImpl.java:79-81`

**Issue:** `SweepActivityImpl` uses `WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE` (line 80) to detect already-running workflows. The comment on `TemporalConfig.PATHWAY_WORKFLOW_ID_PREFIX` (line 33) states: "WorkflowIdReusePolicy.ALLOW_DUPLICATE allows re-enrollment after deactivation (D-08)." `PathwayService.startPathwayMonitoring()` correctly uses `ALLOW_DUPLICATE` (line 68) to support re-enrollment. However, `SweepActivityImpl` uses `REJECT_DUPLICATE`, which rejects *any* workflow that shares an ID — including **completed** workflows. A patient who completed their pathway (workflow terminated with `pathwayComplete=true`) and is then re-enrolled will be silently skipped by the sweep because `REJECT_DUPLICATE` catches both running and recently-completed executions. The sweep's `WorkflowExecutionAlreadyStarted` catch treats all such rejections as "already monitored" (line 93-95), so the re-enrolled patient will never get a new workflow from the sweep path.

**Fix:** The sweep's intent is to skip *running* workflows, not completed ones. Use `ALLOW_DUPLICATE` in the sweep (matching `PathwayService`) and rely on the `WorkflowExecutionAlreadyStarted` exception only for truly running workflows:

```java
WorkflowOptions options = WorkflowOptions.newBuilder()
        .setWorkflowId(workflowId)
        .setTaskQueue(TemporalConfig.TASK_QUEUE)
        .setWorkflowIdReusePolicy(
                WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE)
        .build();
```

Alternatively, before starting, query the workflow state via `WorkflowClient` to confirm whether a running execution exists, rather than using exception-based flow control for this distinction.

---

### CR-07: `PatientPathwayWorkflowImpl` does not have `@WorkflowImpl` annotation — worker auto-discovery will not register it

**File:** `src/main/java/com/onconavigator/workflow/PatientPathwayWorkflowImpl.java:32`

**Issue:** The `temporal-spring-boot-starter` auto-discovery (configured at `spring.temporal.workers-auto-discovery.packages`) relies on `@WorkflowImpl` annotation to identify and register workflow implementations with the worker. `PatientPathwayWorkflowImpl` has no `@WorkflowImpl` annotation. `DailySweepWorkflowImpl` likewise has no `@WorkflowImpl`. Without these annotations the starter will not register the workflow implementations, and the worker will silently have no workflow handlers. Workflow executions will be queued but never executed, causing all pathway workflows to be stuck.

**Fix:** Add `@WorkflowImpl` to both workflow implementation classes:

```java
import io.temporal.spring.boot.WorkflowImpl;

@WorkflowImpl(taskQueues = TemporalConfig.TASK_QUEUE)
public class PatientPathwayWorkflowImpl implements PatientPathwayWorkflow { ... }
```

```java
@WorkflowImpl(taskQueues = TemporalConfig.TASK_QUEUE)
public class DailySweepWorkflowImpl implements DailySweepWorkflow { ... }
```

Note: `ActivityImpl` beans annotated with `@Component` are discovered differently (as Spring beans) and may work without a specific annotation depending on starter version, but workflow implementations are not Spring-managed beans and require `@WorkflowImpl` for auto-registration.

---

## Warnings

### WR-01: `IllegalStateException` is not in the `doNotRetry` list — corrupt template data causes 3x activity retries before failing

**File:** `src/main/java/com/onconavigator/workflow/PatientPathwayWorkflowImpl.java:59-65`

**Issue:** The `ACTIVITY_RETRY_OPTIONS` at line 59–65 only lists `IllegalArgumentException` in `setDoNotRetry`. `PathwayEvaluationActivityImpl.evaluate()` throws `IllegalStateException` when JSONB deserialization fails (line 113). This is a programming-time error (corrupt template data) that will not recover with retries, yet Temporal will retry it 3 times with exponential backoff before the activity fails the workflow task. This wastes up to 2+ minutes and generates unnecessary noise in Temporal's event history.

**Fix:** Add `IllegalStateException` to the do-not-retry list:

```java
private static final RetryOptions ACTIVITY_RETRY_OPTIONS = RetryOptions.newBuilder()
        .setMaximumAttempts(3)
        .setInitialInterval(Duration.ofSeconds(5))
        .setBackoffCoefficient(2.0)
        .setMaximumInterval(Duration.ofMinutes(1))
        .setDoNotRetry(
            IllegalArgumentException.class.getName(),
            IllegalStateException.class.getName())
        .build();
```

---

### WR-02: `buildAlert()` does not set `workflowRunId` — alert traceability field is silently null for evaluation-path alerts

**File:** `src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java:314-321`

**Issue:** The `buildAlert()` helper at lines 314–321 constructs an `Alert` entity but does not set `workflowRunId`. `AlertGenerationActivityImpl.generateAlert()` correctly sets this field (line 65). Alerts created through `PathwayEvaluationActivityImpl` (the primary code path for all three alert types) will have a `null` `workflowRunId`. If `workflowRunId` is non-nullable in the database schema or meaningful for audit/debugging, this is both a data integrity issue and a HIPAA audit-trail gap — investigators cannot trace which workflow run created an alert.

**Fix:** Pass the Temporal workflow run ID from the activity context into `buildAlert()`:

```java
// In evaluate(), obtain the run ID:
String runId = Activity.getExecutionContext().getInfo().getWorkflowId(); // or getRunId()

// Pass to buildAlert:
private Alert buildAlert(UUID patientId, PathwayStep step, AlertType alertType, String workflowRunId) {
    Alert alert = new Alert();
    alert.setPatientId(patientId);
    alert.setAlertType(alertType);
    alert.setPathwayStepName(step.name());
    alert.setDeviationDescription(step.alertText());
    alert.setSuggestedAction(step.suggestedAction());
    alert.setWorkflowRunId(workflowRunId);
    return alert;
}
```

---

### WR-03: `LocalDate.now()` used in activity — non-deterministic in activities is fine, but introduces timezone ambiguity

**File:** `src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java:195`

**Issue:** `LocalDate.now()` at line 195 uses the JVM's default timezone. In a Docker/ECS environment the JVM timezone may not match the practice's operational timezone (e.g., JVM in UTC, practice in US/Eastern). For a patient diagnosed on 2026-04-30 at 11 PM Eastern, `LocalDate.now()` in UTC returns 2026-05-01 — adding a spurious day to `elapsedDays`. For care pathways measured in 14- or 21-day windows, a timezone error of one day can cause false-positive alerts or mask a real 24-hour violation. This is not a Temporal determinism issue (activities may use system time), but it is a clinical correctness issue.

**Fix:** Use an explicit timezone consistent with the practice's locale:

```java
ZoneId practiceZone = ZoneId.of("America/New_York"); // or inject from config
LocalDate today = LocalDate.now(practiceZone);
long elapsedDays = ChronoUnit.DAYS.between(anchorDate, today);
```

Inject the timezone string from `application.yml` under `onconavigator.timezone` so it can be configured per deployment.

---

### WR-04: `PathwayService.deactivatePatient()` passes free-text `reason` string to Temporal signal — no validation; PHI can enter Temporal history

**File:** `src/main/java/com/onconavigator/service/PathwayService.java:115`

**Issue:** The Javadoc at line 115 says "reason — coded reason for deactivation (e.g., 'PATIENT_DECEASED', 'CARE_COMPLETE')". However, the method accepts an arbitrary `String reason` with no validation. A caller from the REST API (Phase 3) could pass free-text clinical notes (which may contain the patient's name, MRN, or diagnosis detail). The `reason` is transmitted as a Temporal signal payload, which is stored in Temporal's event history and viewable in the Temporal Web UI. This directly violates the PHI-in-Temporal constraint (SEC-06, T-02-05).

**Fix:** Define a `DeactivationReason` enum and accept only that:

```java
public enum DeactivationReason {
    PATIENT_DECEASED, CARE_COMPLETE, TRANSFERRED, PATIENT_WITHDREW
}

public void deactivatePatient(UUID patientId, DeactivationReason reason) {
    ...
    workflow.deactivatePatient(reason.name());
    ...
}
```

Also update `PatientPathwayWorkflow.deactivatePatient(String reason)` to accept the enum name only, and document that callers must use enum values.

---

### WR-05: `closeOpenAlerts()` uses in-memory mutation then `saveAll()` with no transaction — concurrent reads can see intermediate state

**File:** `src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java:249-258`

**Issue:** `closeOpenAlerts()` fetches all OPEN alerts, mutates their status to `RESOLVED` in-memory, then calls `saveAll()`. Between the `findByPatientIdAndStatus()` call and the `saveAll()` commit, another thread (or a concurrent Temporal retry) could fetch the same OPEN alerts and generate duplicate resolution records or fail to see the alert as already resolved. Without `@Transactional`, each `save()` call inside `saveAll()` is its own transaction, so partial resolution is observable.

**Fix:** Annotate `closeOpenAlerts()` with `@Transactional` (also noted in CR-02). Additionally, consider using a bulk UPDATE query to atomically close alerts:

```java
@Modifying
@Query("UPDATE Alert a SET a.status = 'RESOLVED', a.resolvedAt = :now, " +
       "a.resolutionNotes = 'Patient deactivated -- workflow cancelled' " +
       "WHERE a.patientId = :patientId AND a.status = 'OPEN'")
int closeOpenAlertsBulk(@Param("patientId") UUID patientId, @Param("now") OffsetDateTime now);
```

---

### WR-06: `spring.jpa.hibernate.ddl-auto: update` in `application-local.yml` — Hibernate DDL can silently drop columns on entity changes

**File:** `src/main/resources/application-local.yml:4`

**Issue:** `ddl-auto: update` causes Hibernate to auto-alter the schema on startup. In a system with PHI-containing tables, Hibernate's schema update may silently drop encrypted columns or remove constraints if an entity field is renamed or removed during development. This can result in undetected data loss or constraint removal before Flyway migrations catch up. The CLAUDE.md tech stack notes explicitly recommend Flyway for all schema changes.

**Fix:** Set `ddl-auto: validate` in local profile (matching the production setting) and rely exclusively on Flyway migrations for schema changes. If Envers `_AUD` tables need to be created locally, add them as a Flyway migration rather than relying on Hibernate DDL:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

---

### WR-07: `TemporalConfig` declares a private constructor but is annotated `@Configuration` — Spring cannot proxy it if `proxyBeanMethods=true` were used; private constructor blocks subclassing

**File:** `src/main/java/com/onconavigator/config/TemporalConfig.java:50-53`

**Issue:** `TemporalConfig` is annotated `@Configuration(proxyBeanMethods = false)` and has a `private` no-arg constructor. The class is used purely as a constants holder (`static final` fields) and is never instantiated as a bean — all references use `TemporalConfig.TASK_QUEUE` directly. Having `@Configuration` on a constants-only class misleads readers into expecting bean definitions. If `proxyBeanMethods` were changed to `true` (the default), Spring's CGLIB subclassing would fail at startup because the constructor is private. This is a latent fragility.

**Fix:** Remove `@Configuration` from `TemporalConfig` since it defines no beans. A plain class or `final class` with `private` constructor correctly communicates its intent:

```java
public final class TemporalConfig {
    public static final String TASK_QUEUE = "onco-pathway-queue";
    // ...
    private TemporalConfig() {}
}
```

---

## Info

### IN-01: `CareEventSignal` record is defined but never used — dead code

**File:** `src/main/java/com/onconavigator/domain/dto/CareEventSignal.java:17`

**Issue:** The Javadoc for `CareEventSignal` acknowledges that the actual signal method takes a plain `UUID` parameter and that this record "exists for documentation and potential future use." The record is not referenced by any code in the reviewed files. Dead types in a HIPAA codebase add confusion about what data crosses Temporal boundaries.

**Fix:** If the record is truly forward-looking documentation, move it to a `// TODO` comment in the interface Javadoc. If it has no near-term use, delete it to avoid confusion. Do not leave unused types adjacent to PHI-boundary code where auditors may question its role.

---

### IN-02: `alertTypeStr` parameter in `AlertGenerationActivity` will throw unchecked `IllegalArgumentException` on invalid input — no validation before `valueOf()`

**File:** `src/main/java/com/onconavigator/activity/AlertGenerationActivityImpl.java:61`

**Issue:** `AlertType.valueOf(alertTypeStr)` at line 61 throws `IllegalArgumentException` if `alertTypeStr` is not a valid enum name. All current callers pass string literals (`"MISSING_EVENT"`, `"DELAYED_EVENT"`, `"OUT_OF_ORDER"`), so this is not an immediate bug. However, the `AlertGenerationActivity` interface is a Temporal activity contract — future callers (workflow code or external triggers) could pass an invalid string, causing an opaque exception with the invalid type value in the message. Since this exception is not in `doNotRetry`, Temporal will retry 3 times before failing.

**Fix:** Validate the string before conversion and throw with a clear, non-PHI message:

```java
AlertType alertType;
try {
    alertType = AlertType.valueOf(alertTypeStr);
} catch (IllegalArgumentException e) {
    throw new IllegalArgumentException("Unknown alert type: " + alertTypeStr);
}
```

Alternatively, accept `AlertType` directly in the interface (Temporal serializes enums by name via Jackson) and remove the String indirection. The interface Javadoc mentions String is used "for schema evolution robustness" but this argument weakens when the enum is already serialized by Jackson in other DTOs.

---

### IN-03: `V5__create_physician_overrides.sql` grants `ALL` privileges to `onco_app` — violates least-privilege for a PHI-adjacent table

**File:** `src/main/resources/db/migration/V5__create_physician_overrides.sql:24`

**Issue:** `GRANT ALL ON physician_overrides TO onco_app` grants `SELECT, INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER`. The `physician_overrides` table is write-once by design (the entity has `updatable = false` on all mutable columns). Granting `UPDATE`, `DELETE`, and `TRUNCATE` to the application user means a SQL injection vulnerability or a compromised application user could silently delete override records, re-enabling alerts for patients whose physicians intentionally suppressed them — a direct patient-safety risk.

**Fix:** Grant only the permissions the application actually needs:

```sql
GRANT SELECT, INSERT ON physician_overrides TO onco_app;
-- No UPDATE, DELETE, or TRUNCATE — table is write-once by design
```

Apply the same audit to V3 and other migration grants.

---

_Reviewed: 2026-04-30_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
