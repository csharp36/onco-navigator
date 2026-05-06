---
phase: 09-alert-format-notification-foundation
reviewed: 2026-05-05T18:30:00Z
depth: standard
files_reviewed: 38
files_reviewed_list:
  - frontend/src/features/alerts/types.ts
  - src/main/java/com/onconavigator/activity/AlertGenerationActivity.java
  - src/main/java/com/onconavigator/activity/AlertGenerationActivityImpl.java
  - src/main/java/com/onconavigator/activity/DigestDispatchActivity.java
  - src/main/java/com/onconavigator/activity/DigestDispatchActivityImpl.java
  - src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java
  - src/main/java/com/onconavigator/ai/model/AlertText.java
  - src/main/java/com/onconavigator/ai/prompt/AlertPrompts.java
  - src/main/java/com/onconavigator/ai/service/AlertGenerationAiService.java
  - src/main/java/com/onconavigator/config/DigestScheduleRegistrar.java
  - src/main/java/com/onconavigator/config/TemporalConfig.java
  - src/main/java/com/onconavigator/domain/Alert.java
  - src/main/java/com/onconavigator/domain/enums/NotificationChannel.java
  - src/main/java/com/onconavigator/domain/NotificationLog.java
  - src/main/java/com/onconavigator/domain/NotificationPendingQueue.java
  - src/main/java/com/onconavigator/domain/NotificationPreference.java
  - src/main/java/com/onconavigator/notification/LoggingNotificationService.java
  - src/main/java/com/onconavigator/notification/NotificationPayload.java
  - src/main/java/com/onconavigator/notification/NotificationPreferenceService.java
  - src/main/java/com/onconavigator/notification/NotificationService.java
  - src/main/java/com/onconavigator/repository/NotificationLogRepository.java
  - src/main/java/com/onconavigator/repository/NotificationPendingQueueRepository.java
  - src/main/java/com/onconavigator/repository/NotificationPreferenceRepository.java
  - src/main/java/com/onconavigator/service/AlertService.java
  - src/main/java/com/onconavigator/web/dto/AlertResponse.java
  - src/main/java/com/onconavigator/web/NotificationPreferenceController.java
  - src/main/java/com/onconavigator/workflow/DigestDispatchWorkflow.java
  - src/main/java/com/onconavigator/workflow/DigestDispatchWorkflowImpl.java
  - src/main/resources/application-local.yml
  - src/main/resources/db/migration/V21__add_alert_missing_summary.sql
  - src/main/resources/db/migration/V22__notification_preferences.sql
  - src/main/resources/db/migration/V23__notification_log.sql
  - src/test/java/com/onconavigator/activity/AlertGenerationActivityTest.java
  - src/test/java/com/onconavigator/activity/DigestDispatchActivityImplTest.java
  - src/test/java/com/onconavigator/activity/PathwayEvaluationActivityImplTest.java
  - src/test/java/com/onconavigator/activity/PathwayEvaluationActivityTest.java
  - src/test/java/com/onconavigator/activity/PathwayEvaluationStatusAwareTest.java
  - src/test/java/com/onconavigator/ai/service/AlertGenerationAiServiceTest.java
  - src/test/java/com/onconavigator/notification/LoggingNotificationServiceTest.java
  - src/test/java/com/onconavigator/notification/NotificationPreferenceServiceTest.java
findings:
  critical: 4
  warning: 8
  info: 3
  total: 15
status: issues_found
---

# Phase 9: Code Review Report

**Reviewed:** 2026-05-05T18:30:00Z
**Depth:** standard
**Files Reviewed:** 38
**Status:** issues_found

## Summary

Phase 9 adds two-part alert format (missing_summary + suggested_action capped at 150 chars) and notification infrastructure (preferences, pending queue, digest dispatch, log-only implementation). The implementation is generally well-structured with good HIPAA controls (EncryptionConverter on PHI fields, @Audited on NotificationLog, zero-PHI boundary for Claude). However, several issues were found:

- **Security**: Missing input validation on the `NotificationPreferenceController` PUT endpoints allows arbitrary entity field injection (including `id`), and quiet hours values are never validated for range.
- **Bugs**: The `allStepsComplete` flag is always `false` after the main evaluation loop because it checks the pre-loop variable; a race condition exists in deduplication checks; the `@Transactional` annotation on `DigestDispatchActivityImpl` may not work correctly through Temporal's activity proxy; and `LoggingNotificationService.dispatchForAlert` is not transactional, risking partial dispatch state.
- **Quality**: `NotificationPayload.severityLabel` receives the raw enum name instead of a human-readable label, a `TemporalConfig` constructor conflict exists, and `computeQuietHoursEnd` uses `OffsetDateTime.now(ZoneId)` which is semantically fragile.

## Critical Issues

### CR-01: Missing Input Validation on NotificationPreferenceController Allows Field Injection

**File:** `src/main/java/com/onconavigator/web/NotificationPreferenceController.java:58-66`
**Issue:** The `updateMyPreference` endpoint accepts a raw `NotificationPreference` JPA entity as `@RequestBody` without `@Valid` annotation or any input validation. An attacker can set the `id` field to overwrite another user's preference row (the code sets `userId` from JWT, but the `id` field from the request body is preserved -- JPA `save()` will perform an UPDATE if `id` is non-null and matches an existing row). Similarly, `quietHoursStart`/`quietHoursEnd` can be set to arbitrary integers (e.g., -1 or 99), and `timezone` can be set to an invalid zone ID which would cause `ZoneId.of()` to throw `DateTimeException` at notification dispatch time. The `digestIntervalHours` can be set to 0 or negative. The `alertTypeFilter` array is also unbounded.

The same issue exists on the admin PUT endpoint at line 83-88.

**Fix:** Add a dedicated DTO (not a JPA entity) for the request body, add `@Valid`, and validate all fields:
```java
public record NotificationPreferenceRequest(
    @NotNull NotificationChannel channel,
    boolean enabled,
    @Size(max = 10) String[] alertTypeFilter,
    @Min(0) @Max(23) Integer quietHoursStart,
    @Min(0) @Max(23) Integer quietHoursEnd,
    @NotBlank @Size(max = 100) String timezone,
    boolean digestEnabled,
    @Min(1) @Max(24) int digestIntervalHours
) {}
```
Never accept a JPA entity directly as a request body -- this is the mass-assignment vulnerability pattern. Also validate the timezone by attempting `ZoneId.of(timezone)` before persisting.

### CR-02: Deduplication Race Condition in Alert Creation

**File:** `src/main/java/com/onconavigator/activity/AlertGenerationActivityImpl.java:61-77`
**File:** `src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java:433-484`
**Issue:** The check-then-act pattern (`existsByPatientIdAndPathwayStepNameAndStatus` followed by `alertRepository.save`) is not atomic. Two concurrent Temporal activity executions for the same patient can both pass the dedup check and create duplicate alerts. While Temporal retry semantics reduce this risk for a single workflow, the daily sweep evaluates all patients and could have overlapping evaluations. Additionally, `AlertGenerationActivityImpl.generateAlert` has no `@Transactional` annotation, so the dedup check and save occur in separate transactions.

**Fix:** Add a unique constraint on `(patient_id, pathway_step_name)` with a `WHERE status = 'OPEN'` partial index in the database:
```sql
CREATE UNIQUE INDEX idx_alerts_patient_step_open
    ON alerts(patient_id, pathway_step_name) WHERE status = 'OPEN';
```
Then wrap the check-and-save in a single `@Transactional` method and catch `DataIntegrityViolationException` as a dedup signal. For `AlertGenerationActivityImpl`, add `@Transactional` annotation.

### CR-03: allStepsComplete Always Returns False After Main Evaluation

**File:** `src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java:390`
**Issue:** After the main evaluation loop (lines 191-388), `allStepsComplete` is computed as `activeSteps.isEmpty()`. However, `activeSteps` was populated at line 134 and is guaranteed non-empty at this point (the empty case returns early at line 137-145). Therefore `allStepsComplete` will always be `false` at line 390 when the code reaches it through the main path. This means the pathway workflow will never receive a `true` signal indicating all steps are done when there are still ACTIVE steps being evaluated -- which is correct behavior -- but the comment and log message imply this could be `true`, which is misleading. The actual logical defect is more subtle: if all ACTIVE steps get processed and generate alerts (meaning the patient still has work to do), `allStepsComplete = false` is correct. But if the evaluation resolves some steps to COMPLETED during the loop (it does not currently do this), this value would be stale.

This is correctly handled by the early return at line 137-145 for the truly-all-complete case. The flag at line 390 is technically always `false` and therefore dead logic -- it can never be `true`.

**Fix:** The value at line 390 is always `false` for any code path that reaches it. Either remove it and hardcode `false`, or re-query the step count. As-is, the `allStepsComplete` value in the returned `PathwayEvaluationResult` is misleading to callers who might think it reflects a dynamic evaluation:
```java
// Line 390: This is always false because activeSteps is non-empty here
return new PathwayEvaluationResult(false, alertsGenerated);
```

### CR-04: @Transactional on DigestDispatchActivityImpl May Not Apply Through Temporal Proxy

**File:** `src/main/java/com/onconavigator/activity/DigestDispatchActivityImpl.java:43-44`
**Issue:** The `@Transactional` annotation on `drainPendingQueue()` requires Spring's AOP proxy to be in effect. However, Temporal creates its own activity stub proxy via `Workflow.newActivityStub()`, which serializes parameters and invokes the activity through Temporal's worker infrastructure. Whether Spring's `@Transactional` proxy wraps the Temporal activity invocation depends on the order of proxy creation during bean initialization. If Temporal's worker directly calls the bean method (which is the common pattern with `temporal-spring-boot-starter`), `@Transactional` will work. But if the activity is invoked through a non-Spring proxy path, the entire `drainPendingQueue()` method runs without a transaction, meaning individual `save()` calls each auto-commit. A failure midway would leave some items DISPATCHED and others PENDING, with notifications partially dispatched.

**Fix:** Either verify through integration testing that `@Transactional` is honored on Temporal activity beans with the `temporal-spring-boot-starter`, or explicitly use `TransactionTemplate` programmatically:
```java
@Override
public void drainPendingQueue() {
    transactionTemplate.executeWithoutResult(status -> {
        // ... existing logic ...
    });
}
```

## Warnings

### WR-01: LoggingNotificationService.dispatchForAlert Not Transactional -- Partial Dispatch Risk

**File:** `src/main/java/com/onconavigator/notification/LoggingNotificationService.java:54-58`
**Issue:** `dispatchForAlert` iterates over `NotificationChannel.values()` and for each channel iterates over all preferences, performing individual `save()` calls to `notificationLogRepository` and `pendingQueueRepository`. No `@Transactional` annotation is present. If the method fails midway (e.g., after saving a NotificationLog for TEAMS but before processing EMAIL), the system will have partially dispatched notifications with no way to know which were completed. On retry (via Temporal), duplicate notifications would be created for the channels already processed.

**Fix:** Add `@Transactional` to `dispatchForAlert`, or track which notifications have been dispatched via the dedup check against `notification_log` before creating new entries.

### WR-02: NotificationPayload Receives Raw AlertType Enum Name Instead of Severity Label

**File:** `src/main/java/com/onconavigator/notification/LoggingNotificationService.java:101`
**Issue:** The `NotificationPayload` constructor receives `alert.getAlertType().name()` as the `severityLabel` parameter (e.g., `"MISSING_EVENT"`). The `NotificationPayload` record's `@param severityLabel` Javadoc says it should be a display label like `"OVERDUE"`. The rendered notification will show `[MISSING_EVENT] Jane Doe (MRN: 12345)` instead of `[MISSING] Jane Doe (MRN: 12345)`. The `AlertService.toSeverityLabel()` method already maps AlertType to display labels, but that mapping is not used here.

**Fix:** Extract the `toSeverityLabel()` mapping from `AlertService` to a shared utility (or to the `AlertType` enum itself) and use it when constructing the payload:
```java
NotificationPayload payload = new NotificationPayload(
    patientName,
    patientMrn,
    alert.getPathwayStepName(),
    AlertType.toSeverityLabel(alert.getAlertType()), // human-readable
    alert.getMissingSummary(),
    alert.getSuggestedAction(),
    deepLink
);
```

### WR-03: TemporalConfig Has Both @Configuration and Private Constructor -- Spring Conflict

**File:** `src/main/java/com/onconavigator/config/TemporalConfig.java:12-44`
**Issue:** `TemporalConfig` is annotated `@Configuration(proxyBeanMethods = false)` but has a private constructor (line 42). Spring will attempt to instantiate this class to process the configuration. While `proxyBeanMethods = false` avoids CGLIB subclassing, Spring still needs to create an instance via the constructor. A `private` constructor will cause a `BeanInstantiationException` at startup. This class has only `static final` constants and no `@Bean` methods, so `@Configuration` is unnecessary.

**Fix:** Remove `@Configuration` entirely. This is a constants class, not a configuration class:
```java
public final class TemporalConfig {
    public static final String TASK_QUEUE = "onco-pathway-queue";
    // ... other constants ...
    private TemporalConfig() {}
}
```

### WR-04: computeQuietHoursEnd Uses OffsetDateTime.now(ZoneId) -- Zone Offset Fragility

**File:** `src/main/java/com/onconavigator/notification/NotificationPreferenceService.java:87`
**Issue:** `OffsetDateTime.now(zone)` captures the current offset at the instant of the call. But `OffsetDateTime.with(LocalTime)` preserves the original offset even if the target time crosses a DST boundary. For example, if quiet hours end at 7:00 AM and the current time is 1:00 AM EST (UTC-5), but DST kicks in at 2:00 AM (switching to UTC-4), `candidate.with(endTime)` will produce 7:00 AM at UTC-5 offset, which is actually 8:00 AM wall clock time in the new offset. The notification would be held an extra hour.

**Fix:** Use `ZonedDateTime` instead, which correctly handles DST transitions:
```java
public OffsetDateTime computeQuietHoursEnd(NotificationPreference pref) {
    ZoneId zone = ZoneId.of(pref.getTimezone() != null ? pref.getTimezone() : "UTC");
    LocalTime endTime = LocalTime.of(pref.getQuietHoursEnd(), 0);
    ZonedDateTime now = ZonedDateTime.now(zone);
    ZonedDateTime candidate = now.with(endTime);
    if (!candidate.isAfter(now)) {
        candidate = candidate.plusDays(1);
    }
    return candidate.toOffsetDateTime();
}
```

### WR-05: Quiet Hours Values Not Validated -- Out-of-Range Values Cause Runtime Exceptions

**File:** `src/main/java/com/onconavigator/domain/NotificationPreference.java:52-56`
**File:** `src/main/java/com/onconavigator/notification/NotificationPreferenceService.java:86`
**Issue:** `quietHoursStart` and `quietHoursEnd` are `Integer` with no range constraint (JSR-380 or DB CHECK). `computeQuietHoursEnd` calls `LocalTime.of(pref.getQuietHoursEnd(), 0)` which throws `DateTimeException` if the value is outside 0-23. Combined with CR-01 (no input validation on the controller), a user can set `quietHoursEnd = 25` and cause a runtime exception during notification dispatch for every subsequent alert.

Similarly, `timezone` has no validation -- an invalid timezone string causes `ZoneId.of()` to throw.

**Fix:** Add `CHECK (quiet_hours_start BETWEEN 0 AND 23)` and `CHECK (quiet_hours_end BETWEEN 0 AND 23)` constraints in the migration, and add `@Min(0) @Max(23)` on the entity fields. Validate timezone on save.

### WR-06: DigestScheduleRegistrar Catches Only ScheduleAlreadyRunningException -- Other Failures Crash Startup

**File:** `src/main/java/com/onconavigator/config/DigestScheduleRegistrar.java:69-77`
**Issue:** `DigestScheduleRegistrar` implements `ApplicationRunner` and catches only `ScheduleAlreadyRunningException`. If the Temporal server is unreachable at startup (transient network issue, Temporal not yet ready in Docker Compose), the `ScheduleClient.newInstance()` or `createSchedule()` call throws a gRPC `StatusRuntimeException`, which propagates uncaught and crashes the entire Spring Boot application. The rest of the application (dashboard, REST API, manual data entry) would be perfectly functional without the digest schedule.

**Fix:** Catch a broader exception and log a warning instead of crashing:
```java
try {
    scheduleClient.createSchedule(...);
    log.info("DIGEST_SCHEDULE_REGISTERED: ...");
} catch (ScheduleAlreadyRunningException e) {
    log.info("DIGEST_SCHEDULE_EXISTS: ...");
} catch (Exception e) {
    log.error("DIGEST_SCHEDULE_FAILED: Could not register digest schedule. " +
              "Digest dispatch will not run until application restart. Error: {}", e.getMessage());
}
```

### WR-07: NotificationPreference Entity Directly Used as REST Response -- Exposes Internal State

**File:** `src/main/java/com/onconavigator/web/NotificationPreferenceController.java:43-52`
**Issue:** The GET endpoint returns `List<NotificationPreference>` (JPA entity) directly as the response body. This exposes internal JPA state including all fields (even `nextDigestAt` which is an operational timestamp not useful to the user), and couples the API contract to the database schema. If the entity schema changes (new field, renamed column), the API contract silently breaks. This is a general anti-pattern but especially concerning in a HIPAA context where API contracts should be explicitly controlled.

**Fix:** Create a `NotificationPreferenceResponse` DTO and map from entity to DTO in the controller or service layer.

### WR-08: Frontend AlertResponse.severityLabel Type Does Not Match All Backend Values

**File:** `frontend/src/features/alerts/types.ts:7`
**Issue:** The frontend `severityLabel` type union includes `'OVERDUE' | 'MISSING' | 'OUT OF ORDER' | 'CANCELLED' | 'RESULTS PENDING' | 'DEADLINE' | 'UNCONFIRMED'`. However, the backend `AlertService.toSeverityLabel()` can also return `"UNKNOWN"` when `alertType` is null (line 150 of AlertService.java). If the frontend receives `"UNKNOWN"`, TypeScript will not catch this at runtime (type narrowing only applies at compile time), but any switch/match on `severityLabel` that does not handle `"UNKNOWN"` will silently fall through.

**Fix:** Add `'UNKNOWN'` to the frontend type union, or ensure the backend never sends `"UNKNOWN"` (by filtering out null-type alerts from the query):
```typescript
severityLabel: 'OVERDUE' | 'MISSING' | 'OUT OF ORDER' | 'CANCELLED' | 'RESULTS PENDING' | 'DEADLINE' | 'UNCONFIRMED' | 'UNKNOWN';
```

## Info

### IN-01: Application-local.yml Contains Committed Encryption Keys

**File:** `src/main/resources/application-local.yml:69-75`
**Issue:** Both the AES encryption key and HMAC key are committed in `application-local.yml`. While comments note these are "non-secret placeholders" and should be replaced, they are real Base64-encoded 32-byte keys that could be accidentally used in a non-local environment. The `application-local.yml` profile is activated by Spring profile, and if someone runs with `local` profile in a staging environment, these keys would encrypt real PHI.

**Fix:** Use environment variable references with no fallback defaults for the keys:
```yaml
onconavigator:
  encryption:
    key: ${ONCO_ENCRYPTION_KEY}
  hmac:
    key: ${ONCO_HMAC_KEY}
```
This forces explicit key provisioning in all environments.

### IN-02: Log Statement Has Swapped Parameters

**File:** `src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java:491`
**Issue:** The log statement `log.info("ALERT_CREATED: patient={} step={} type={}", alertType, patient.getId(), step.getId())` has the parameters in the wrong order. The format string expects `patient, step, type` but receives `alertType, patientId, stepId`. The log output will show the alert type in the patient field and the patient UUID in the step field.

**Fix:**
```java
log.info("ALERT_CREATED: patient={} step={} type={}", patient.getId(), step.getId(), alertType);
```

### IN-03: NotificationPayload deepLink Includes Patient UUID in URL -- Not PHI But Worth Noting

**File:** `src/main/java/com/onconavigator/notification/LoggingNotificationService.java:96`
**Issue:** The deepLink includes the patient UUID: `baseUrl + "/patients/" + alert.getPatientId()`. Patient UUIDs are explicitly designated as non-PHI in the codebase, so this is not a HIPAA violation. However, the deepLink is embedded in the rendered notification content which is sent to external channels (Teams/email in future phases). If the Teams/email channel logs URLs or shows them in previews, the patient UUID would be visible outside the application. This is acceptable per the current PHI classification but worth documenting for future review when real connectors are built.

**Fix:** No immediate fix required. Document in the notification connector design that deepLinks containing patient UUIDs are acceptable per the current PHI classification, and ensure external channels use TLS.

---

_Reviewed: 2026-05-05T18:30:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
