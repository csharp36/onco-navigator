# Phase 9: Alert Format + Notification Foundation - Pattern Map

**Mapped:** 2026-05-05
**Files analyzed:** 19 new/modified files
**Analogs found:** 19 / 19

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `src/main/java/com/onconavigator/domain/Alert.java` | model (modify) | CRUD | itself | exact |
| `src/main/java/com/onconavigator/ai/model/AlertText.java` | model (modify) | transform | itself | exact |
| `src/main/java/com/onconavigator/ai/model/AlertText.java` | model (modify) | transform | `AlertText.java` (current) | exact |
| `src/main/java/com/onconavigator/domain/NotificationPreference.java` | model | CRUD | `Patient.java` | role-match |
| `src/main/java/com/onconavigator/domain/NotificationLog.java` | model | CRUD | `Patient.java` + `AuditLogEntry.java` | role-match |
| `src/main/java/com/onconavigator/domain/enums/NotificationChannel.java` | enum | — | `AlertType.java` | exact |
| `src/main/java/com/onconavigator/repository/NotificationPreferenceRepository.java` | repository | CRUD | `AlertRepository.java` | role-match |
| `src/main/java/com/onconavigator/repository/NotificationLogRepository.java` | repository | CRUD | `AlertRepository.java` | role-match |
| `src/main/java/com/onconavigator/notification/NotificationService.java` | service (interface) | request-response | `AlertService.java` | role-match |
| `src/main/java/com/onconavigator/notification/LoggingNotificationService.java` | service | request-response | `AuditService.java` | role-match |
| `src/main/java/com/onconavigator/notification/NotificationPayload.java` | value object | transform | `AlertText.java` | role-match |
| `src/main/java/com/onconavigator/notification/NotificationPreferenceService.java` | service | CRUD | `AlertService.java` | role-match |
| `src/main/java/com/onconavigator/workflow/DigestDispatchWorkflow.java` | workflow interface | event-driven | `DailySweepWorkflow.java` | exact |
| `src/main/java/com/onconavigator/workflow/DigestDispatchWorkflowImpl.java` | workflow impl | event-driven | `DailySweepWorkflowImpl.java` | exact |
| `src/main/java/com/onconavigator/activity/DigestDispatchActivity.java` | activity interface | batch | `SweepActivity.java` | exact |
| `src/main/java/com/onconavigator/activity/DigestDispatchActivityImpl.java` | activity impl | batch | `SweepActivityImpl.java` | exact |
| `src/main/resources/db/migration/V21__add_alert_missing_summary.sql` | migration | batch | `V19__template_inheritance.sql` | exact |
| `src/main/resources/db/migration/V22__notification_preferences.sql` | migration | batch | `V19__template_inheritance.sql` | exact |
| `src/main/resources/db/migration/V23__notification_log.sql` | migration | batch | `V19__template_inheritance.sql` | exact |
| `src/main/java/com/onconavigator/ai/service/AlertGenerationAiService.java` | service (modify) | request-response | itself | exact |
| `src/main/java/com/onconavigator/ai/prompt/AlertPrompts.java` | config (modify) | transform | itself | exact |
| `src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java` | activity (modify) | request-response | itself | exact |
| `src/main/java/com/onconavigator/activity/AlertGenerationActivityImpl.java` | activity (modify) | request-response | itself | exact |
| `src/main/java/com/onconavigator/web/dto/AlertResponse.java` | dto (modify) | request-response | itself | exact |
| `frontend/src/features/alerts/types.ts` | types (modify) | request-response | itself | exact |
| `src/main/resources/application-local.yml` | config (modify) | — | itself | exact |

---

## Pattern Assignments

### `src/main/java/com/onconavigator/domain/Alert.java` (model, CRUD — modify)

**Analog:** itself (current file)

**What changes:** Add `missingSummary` field only. All structural patterns stay identical.

**New field pattern** — copy from `suggestedAction` field structure (lines 55-56):
```java
// Existing pattern to copy for new field:
@Column(name = "suggested_action", columnDefinition = "TEXT")
private String suggestedAction;

// New field to add using same pattern:
@Column(name = "missing_summary", columnDefinition = "TEXT")
private String missingSummary;
```

**Getter/setter pattern** — copy from `getSuggestedAction()` / `setSuggestedAction()` (lines 128-134):
```java
public String getSuggestedAction() {
    return suggestedAction;
}

public void setSuggestedAction(String suggestedAction) {
    this.suggestedAction = suggestedAction;
}
```

**No @Audited change needed** — `@Audited` at class level (line 31) already covers all fields. The V21 migration must manually add `missing_summary` to `alerts_aud` (see V19 pitfall pattern).

---

### `src/main/java/com/onconavigator/ai/model/AlertText.java` (model, transform — modify)

**Analog:** itself (current file at line 12)

**Current state (line 12):**
```java
public record AlertText(String deviationDescription, String suggestedAction) {}
```

**Phase 9 change:** Add third record component. Every `new AlertText(desc, action)` call site breaks at compile time — find all usages before changing.

**PITFALL:** `AlertGenerationAiService.java` line 126 constructs `new AlertText(description, suggestedAction)`. `generateAlertFallback()` returns `null` (no change needed). Update both the record and all constructor call sites atomically.

**New record:**
```java
public record AlertText(String deviationDescription, String suggestedAction, String missingSummary) {}
```

**Updated construction at `AlertGenerationAiService` line 126:**
```java
// Before:
return new AlertText(description, suggestedAction);
// After:
return new AlertText(description, suggestedAction, missingSummary);
```

---

### `src/main/java/com/onconavigator/domain/NotificationPreference.java` (model, CRUD — new)

**Analog:** `src/main/java/com/onconavigator/domain/Patient.java`

**Imports pattern** (Patient.java lines 1-17):
```java
package com.onconavigator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.envers.Audited;

import java.time.OffsetDateTime;
import java.util.UUID;
```

**Core entity pattern** (Patient.java lines 37-101) — `@Entity`, `@Table`, `@Audited`, UUID PK with `GenerationType.UUID`, `@PrePersist`/`@PreUpdate` for timestamps:
```java
@Entity
@Table(name = "notification_preferences")
@Audited
public class NotificationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id")           // NULL = admin default row
    private UUID userId;

    @Column(name = "is_admin_default", nullable = false)
    private boolean adminDefault = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", columnDefinition = "notification_channel", nullable = false)
    private NotificationChannel channel;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    // alert_type_filter stored as TEXT[] — use String[] with @Type or custom converter
    // For V1: store as comma-separated string or use @Column with array type
    @Column(name = "alert_type_filter", columnDefinition = "text[]")
    private String[] alertTypeFilter = new String[0];

    @Column(name = "quiet_hours_start")
    private Integer quietHoursStart;

    @Column(name = "quiet_hours_end")
    private Integer quietHoursEnd;

    @Column(name = "timezone", length = 100, nullable = false)
    private String timezone = "UTC";

    @Column(name = "digest_enabled", nullable = false)
    private boolean digestEnabled = false;

    @Column(name = "digest_interval_hours", nullable = false)
    private int digestIntervalHours = 4;

    @Column(name = "next_digest_at")
    private OffsetDateTime nextDigestAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }
    // ... getters and setters follow Patient.java pattern
}
```

**PHI note:** This entity is NOT `@Audited` since it contains no ePHI — it is preference metadata only. Copy the plain entity pattern from `AuditLogEntry.java` (which also lacks `@Audited`) if `@Audited` is not desired. If `@Audited` is included, a `notification_preferences_aud` table must be created in V22.

---

### `src/main/java/com/onconavigator/domain/NotificationLog.java` (model, CRUD — new)

**Analog:** `src/main/java/com/onconavigator/domain/Patient.java` (for `@Audited` + `@Convert` pattern)

**Imports pattern** — merge of Patient.java and AuditLogEntry.java imports:
```java
package com.onconavigator.domain;

import com.onconavigator.domain.enums.NotificationChannel;
import com.onconavigator.security.EncryptionConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.envers.Audited;

import java.time.OffsetDateTime;
import java.util.UUID;
```

**PHI encryption pattern** — copy from Patient.java lines 44-58 (`@Convert` on `@Column(columnDefinition = "bytea")`):
```java
// PHI: rendered_content contains patient name + MRN embedded in notification text
@Convert(converter = EncryptionConverter.class)
@Column(name = "rendered_content", columnDefinition = "bytea", nullable = false)
private String renderedContent;
```

**Core entity pattern** — use `@Audited` for HIPAA audit trail on the notification log:
```java
@Entity
@Table(name = "notification_log")
@Audited
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "alert_id", nullable = false, updatable = false)
    private UUID alertId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", columnDefinition = "notification_channel", nullable = false,
            updatable = false)
    private NotificationChannel channel;

    @Convert(converter = EncryptionConverter.class)
    @Column(name = "rendered_content", columnDefinition = "bytea", nullable = false,
            updatable = false)
    private String renderedContent;

    @Column(name = "is_digest", nullable = false, updatable = false)
    private boolean digest = false;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "SENT";

    @Column(name = "sent_at", nullable = false, updatable = false)
    private OffsetDateTime sentAt;

    @PrePersist
    void prePersist() {
        this.sentAt = OffsetDateTime.now();
    }
    // ... getters only (no setters on updatable=false columns, per AuditLogEntry pattern)
}
```

**V23 migration must include `notification_log_aud`** — see Shared Patterns / Envers AUD mirror.

---

### `src/main/java/com/onconavigator/domain/enums/NotificationChannel.java` (enum — new)

**Analog:** `src/main/java/com/onconavigator/domain/enums/AlertType.java`

**Copy full structure** (AlertType.java, all 19 lines):
```java
package com.onconavigator.domain.enums;

/**
 * Notification delivery channels for external alert dispatch.
 * Maps to the notification_channel PostgreSQL enum.
 *
 * <p>Dashboard is always-on per D-07 and is not a configurable channel.
 * notification_preferences controls TEAMS and EMAIL only.
 */
public enum NotificationChannel {
    TEAMS,
    EMAIL
}
```

---

### `src/main/java/com/onconavigator/repository/NotificationPreferenceRepository.java` (repository, CRUD — new)

**Analog:** `src/main/java/com/onconavigator/repository/AlertRepository.java`

**Imports + interface pattern** (AlertRepository.java lines 1-20):
```java
package com.onconavigator.repository;

import com.onconavigator.domain.NotificationPreference;
import com.onconavigator.domain.enums.NotificationChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {

    // Load user-specific preferences for a given channel
    Optional<NotificationPreference> findByUserIdAndChannel(UUID userId, NotificationChannel channel);

    // Load all preferences for a user (all channels)
    List<NotificationPreference> findByUserId(UUID userId);

    // Load admin default rows (user_id IS NULL and is_admin_default = TRUE)
    List<NotificationPreference> findByUserIdIsNullAndAdminDefaultTrue();

    // Load all preferences for a channel (to find all users enabled for a channel)
    List<NotificationPreference> findByChannelAndEnabledTrue(NotificationChannel channel);
}
```

---

### `src/main/java/com/onconavigator/repository/NotificationLogRepository.java` (repository, CRUD — new)

**Analog:** `src/main/java/com/onconavigator/repository/AlertRepository.java`

**Pattern** — simple JpaRepository with alert-scoped query, mirroring `AlertRepository.findByPatientIdAndStatus()` (line 37-40):
```java
package com.onconavigator.repository;

import com.onconavigator.domain.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    List<NotificationLog> findByAlertId(UUID alertId);

    List<NotificationLog> findByUserIdOrderBySentAtDesc(UUID userId);
}
```

---

### `src/main/java/com/onconavigator/notification/NotificationService.java` (service interface, request-response — new)

**Analog:** no existing service interface in the project (services are concrete `@Service` classes). Nearest structural model is `SweepActivity.java` (activity interface pattern).

**Activity interface pattern** (SweepActivity.java lines 1-32) — `@ActivityInterface` maps to a plain Java interface for services. For a Spring service interface, no annotation is needed:
```java
package com.onconavigator.notification;

import com.onconavigator.domain.Alert;

/**
 * Dispatches alert notifications to eligible users based on their channel preferences.
 *
 * <p>Called immediately after alertRepository.save() on every alert creation path.
 * Immediate channel dispatch (non-digest) happens synchronously in the calling thread.
 * Digest-bound notifications are queued to notification_pending_queue for the
 * DigestDispatchWorkflow to drain.
 *
 * <p>Implementations MUST be PHI-safe: log only alert UUIDs and user UUIDs.
 * Never log patientName or patientMrn parameters.
 */
public interface NotificationService {

    /**
     * Dispatch notifications for a newly created alert to all eligible users.
     *
     * @param alert       the newly persisted Alert entity
     * @param patientName decrypted patient name (render in payload only — do not log)
     * @param patientMrn  decrypted patient MRN (render in payload only — do not log)
     */
    void dispatchForAlert(Alert alert, String patientName, String patientMrn);
}
```

---

### `src/main/java/com/onconavigator/notification/LoggingNotificationService.java` (service, request-response — new)

**Analog:** `src/main/java/com/onconavigator/service/AuditService.java`

**Imports + @Service + constructor injection + PHI-safe logging** (AuditService.java lines 1-44):
```java
package com.onconavigator.notification;

import com.onconavigator.domain.Alert;
import com.onconavigator.domain.NotificationLog;
import com.onconavigator.repository.NotificationLogRepository;
import com.onconavigator.repository.NotificationPreferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
```

**PHI-safe logging pattern** (AuditService.java lines 84-85):
```java
// CORRECT: log only UUIDs
log.info("NOTIFICATION_DISPATCHED: alert={} user={} channel={}", alertId, userId, channel);

// INCORRECT (never do this):
// log.info("Sending to {} MRN {}", patientName, patientMrn);
```

**Core dispatch pattern** — mirrors `AuditService.logAccess()` structure (lines 62-88): load context, build entry, persist, catch-and-log failures:
```java
@Service
public class LoggingNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationService.class);

    private final NotificationPreferenceRepository preferenceRepository;
    private final NotificationLogRepository notificationLogRepository;

    public LoggingNotificationService(NotificationPreferenceRepository preferenceRepository,
                                      NotificationLogRepository notificationLogRepository) {
        this.preferenceRepository = preferenceRepository;
        this.notificationLogRepository = notificationLogRepository;
    }

    @Override
    public void dispatchForAlert(Alert alert, String patientName, String patientMrn) {
        // Load enabled preferences for each channel
        // Apply severity filter (alert.getAlertType() in preference.getAlertTypeFilter())
        // Apply quiet-hours check — if in quiet hours, insert into notification_pending_queue
        // Otherwise: build NotificationPayload, log what WOULD be sent, persist NotificationLog
        log.info("NOTIFICATION_LOGGED: alert={} [log-only, no real channel active]",
                alert.getId());
    }
}
```

---

### `src/main/java/com/onconavigator/notification/NotificationPayload.java` (value object, transform — new)

**Analog:** `src/main/java/com/onconavigator/ai/model/AlertText.java`

**Record value object pattern** (AlertText.java line 12):
```java
package com.onconavigator.notification;

/**
 * Immutable value object representing the rendered notification payload.
 *
 * <p>HIPAA note: Contains PHI (patientName, patientMrn) for rendering purposes only.
 * Must not be logged. Rendered content is encrypted before storage in notification_log.
 *
 * @param patientName     decrypted patient name (PHI — do not log)
 * @param patientMrn      decrypted MRN (PHI — do not log)
 * @param pathwayStepName the step where the deviation occurred (non-PHI)
 * @param severityLabel   display label (e.g., "OVERDUE") (non-PHI)
 * @param missingSummary  the "what is missing" part of the two-part alert (non-PHI)
 * @param suggestedAction the corrective action part (non-PHI)
 * @param deepLink        URL to the patient pathway view in the dashboard
 */
public record NotificationPayload(
        String patientName,
        String patientMrn,
        String pathwayStepName,
        String severityLabel,
        String missingSummary,
        String suggestedAction,
        String deepLink
) {}
```

---

### `src/main/java/com/onconavigator/notification/NotificationPreferenceService.java` (service, CRUD — new)

**Analog:** `src/main/java/com/onconavigator/service/AlertService.java`

**Imports + @Service + constructor injection** (AlertService.java lines 1-43):
```java
package com.onconavigator.notification;

import com.onconavigator.domain.NotificationPreference;
import com.onconavigator.domain.enums.NotificationChannel;
import com.onconavigator.repository.NotificationPreferenceRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepository;

    public NotificationPreferenceService(NotificationPreferenceRepository preferenceRepository) {
        this.preferenceRepository = preferenceRepository;
    }

    /**
     * Loads effective preference for a user on a channel.
     * User override wins over admin default. Returns admin default if no user override exists.
     */
    public Optional<NotificationPreference> getEffectivePreference(UUID userId,
                                                                    NotificationChannel channel) {
        // 1. Try user-specific row
        Optional<NotificationPreference> userPref =
            preferenceRepository.findByUserIdAndChannel(userId, channel);
        if (userPref.isPresent()) return userPref;

        // 2. Fall back to admin default
        return preferenceRepository.findByUserIdIsNullAndAdminDefaultTrue()
            .stream()
            .filter(p -> p.getChannel() == channel)
            .findFirst();
    }
    // ... additional preference management methods
}
```

---

### `src/main/java/com/onconavigator/workflow/DigestDispatchWorkflow.java` (workflow interface, event-driven — new)

**Analog:** `src/main/java/com/onconavigator/workflow/DailySweepWorkflow.java`

**Copy full structure** (DailySweepWorkflow.java all 28 lines):
```java
package com.onconavigator.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Digest dispatch workflow: drains the notification_pending_queue for users
 * whose digest interval has elapsed, and dispatches quiet-hours held items
 * that are past their hold_until time.
 *
 * <p>Scheduled via Temporal Schedules API (ScheduleClient.createSchedule) — runs
 * every 30 minutes. A single shared schedule, not per-user workflows.
 *
 * <p>CRITICAL — Determinism constraints:
 * <ul>
 *   <li>NO database access — delegated to DigestDispatchActivity</li>
 *   <li>NO Spring/JPA imports — workflow classes run in Temporal's context</li>
 * </ul>
 */
@WorkflowInterface
public interface DigestDispatchWorkflow {

    @WorkflowMethod
    void runDigestPass();
}
```

---

### `src/main/java/com/onconavigator/workflow/DigestDispatchWorkflowImpl.java` (workflow impl, event-driven — new)

**Analog:** `src/main/java/com/onconavigator/workflow/DailySweepWorkflowImpl.java`

**Copy full structure** (DailySweepWorkflowImpl.java all 53 lines) with these substitutions:
- Replace `SweepActivity` with `DigestDispatchActivity`
- Replace `sweepActivity.findAndStartMissingWorkflows()` with `digestActivity.drainPendingQueue()`
- Replace `@WorkflowImpl(taskQueues = TemporalConfig.TASK_QUEUE)` — keep identical

**Pattern to copy** (DailySweepWorkflowImpl.java lines 28-53):
```java
@WorkflowImpl(taskQueues = TemporalConfig.TASK_QUEUE)
public class DigestDispatchWorkflowImpl implements DigestDispatchWorkflow {

    private final DigestDispatchActivity digestActivity = Workflow.newActivityStub(
            DigestDispatchActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(5))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(3)
                            .setInitialInterval(Duration.ofSeconds(10))
                            .setBackoffCoefficient(2.0)
                            .build())
                    .build());

    @Override
    public void runDigestPass() {
        digestActivity.drainPendingQueue();
    }
}
```

**Schedule registration** — add a Spring `ApplicationRunner` bean (or startup `@PostConstruct` in a new `DigestScheduleRegistrar` config class). Pattern from RESEARCH.md Pattern 5:
```java
// Idempotent: catch ScheduleAlreadyRunningException on restart
try {
    scheduleClient.createSchedule("digest-dispatch-schedule", schedule,
        ScheduleOptions.newBuilder().build());
} catch (ScheduleAlreadyRunningException e) {
    log.info("Digest dispatch schedule already registered");
}
```

---

### `src/main/java/com/onconavigator/activity/DigestDispatchActivity.java` (activity interface, batch — new)

**Analog:** `src/main/java/com/onconavigator/activity/SweepActivity.java`

**Copy full structure** (SweepActivity.java all 32 lines):
```java
package com.onconavigator.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Digest dispatch activity: drains the notification_pending_queue table.
 *
 * <p>Dispatches items whose hold_until <= NOW() — both DIGEST-type and
 * QUIET_HOURS-type pending notifications.
 *
 * <p>Called by DigestDispatchWorkflowImpl on the Temporal Schedule (every 30 minutes).
 */
@ActivityInterface
public interface DigestDispatchActivity {

    @ActivityMethod
    void drainPendingQueue();
}
```

---

### `src/main/java/com/onconavigator/activity/DigestDispatchActivityImpl.java` (activity impl, batch — new)

**Analog:** `src/main/java/com/onconavigator/activity/SweepActivityImpl.java`

**Imports + @Component + constructor injection + PHI-safe logging** (SweepActivityImpl.java lines 1-55):
```java
package com.onconavigator.activity;

import com.onconavigator.notification.LoggingNotificationService;
import com.onconavigator.repository.NotificationPendingQueueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
```

**Core activity pattern** (SweepActivityImpl.java lines 38-107) — `@Component`, constructor injection, PHI-safe structured logging:
```java
@Component
public class DigestDispatchActivityImpl implements DigestDispatchActivity {

    private static final Logger log = LoggerFactory.getLogger(DigestDispatchActivityImpl.class);

    private final NotificationPendingQueueRepository pendingQueueRepository;
    private final LoggingNotificationService notificationService;

    public DigestDispatchActivityImpl(NotificationPendingQueueRepository pendingQueueRepository,
                                      LoggingNotificationService notificationService) {
        this.pendingQueueRepository = pendingQueueRepository;
        this.notificationService = notificationService;
    }

    @Override
    public void drainPendingQueue() {
        // Query WHERE status = 'PENDING' AND hold_until <= NOW()
        // Group by user_id for DIGEST type, single dispatch for QUIET_HOURS type
        // Mark rows DISPATCHED after successful send
        // Insert NotificationLog entry (SENT status)
        // PHI: encrypted rendered_content stays encrypted throughout — never log
        log.info("DIGEST_SWEEP: dispatched={} queued={}", dispatched, remaining);
    }
}
```

**CRITICAL:** Add `digestDispatchActivityImpl` to `activity-beans` list in `application-local.yml` (Pitfall 3 from RESEARCH.md — auto-discovery does NOT work for activity beans):
```yaml
# src/main/resources/application-local.yml — under workers[0].activity-beans:
activity-beans:
  - pathwayEvaluationActivityImpl
  - alertGenerationActivityImpl
  - sweepActivityImpl
  - digestDispatchActivityImpl    # ADD THIS
```

---

### `src/main/java/com/onconavigator/ai/service/AlertGenerationAiService.java` (service, request-response — modify)

**Analog:** itself (current file)

**Parser extension** — add after existing `SUGGESTED_ACTION:` parsing block (lines 108-115). Follow the same indexed-substring pattern:

**Existing pattern to extend** (lines 97-126):
```java
private AlertText parseAlertResponse(String response) {
    // ... existing null/blank check ...

    String description = null;
    String suggestedAction = null;

    int descIdx = response.indexOf("DESCRIPTION:");
    int actionIdx = response.indexOf("SUGGESTED_ACTION:");

    if (descIdx >= 0 && actionIdx > descIdx) {
        description = response.substring(descIdx + "DESCRIPTION:".length(), actionIdx).strip();
        suggestedAction = response.substring(actionIdx + "SUGGESTED_ACTION:".length()).strip();
    } else if (descIdx >= 0) {
        description = response.substring(descIdx + "DESCRIPTION:".length()).strip();
    }
    // ...
    return new AlertText(description, suggestedAction);
}
```

**Phase 9 extension — add MISSING_SUMMARY parsing:**
```java
// After parsing suggestedAction, before the null checks:
int summaryIdx = response.indexOf("MISSING_SUMMARY:");
String missingSummary = null;
if (summaryIdx >= 0) {
    // If MISSING_SUMMARY: comes after SUGGESTED_ACTION:, trim suggestedAction to that boundary
    if (actionIdx >= 0 && summaryIdx > actionIdx) {
        suggestedAction = response.substring(
            actionIdx + "SUGGESTED_ACTION:".length(), summaryIdx).strip();
    }
    missingSummary = response.substring(summaryIdx + "MISSING_SUMMARY:".length()).strip();
    if (missingSummary.length() > 150) {
        missingSummary = missingSummary.substring(0, 150).trim();
    }
}
// Fallback: derive from description if Claude didn't provide MISSING_SUMMARY section
if (missingSummary == null && description != null) {
    missingSummary = description.length() > 150
        ? description.substring(0, 150).trim()
        : description;
}

// Update return to three-arg record:
return new AlertText(description, suggestedAction, missingSummary);
```

---

### `src/main/java/com/onconavigator/ai/prompt/AlertPrompts.java` (config, transform — modify)

**Analog:** itself (current file)

**Existing USER_TEMPLATE pattern** (lines 30-49) — append third section to the numbered list:

```java
// Extend the USER_TEMPLATE string constant.
// Current format string ends with:
//   Format your response exactly as:
//   DESCRIPTION: [your description]
//   SUGGESTED_ACTION: [your suggested actions]
//
// Phase 9 extension adds:
public static final String USER_TEMPLATE = """
    ...existing content...
    Generate:
    1. DESCRIPTION: A 2-4 sentence plain-language description...
    2. SUGGESTED_ACTION: 1-3 specific coordination actions...
    3. MISSING_SUMMARY: A single sentence (≤150 characters) stating specifically \
       what is missing or overdue. This is the primary content of nurse notifications.

    Format your response exactly as:
    DESCRIPTION: [your description]
    SUGGESTED_ACTION: [your suggested actions]
    MISSING_SUMMARY: [your one-sentence summary ≤150 chars]
    """;
```

---

### `src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java` (activity, request-response — modify)

**Analog:** itself (current file)

**Hook point** (lines 412-433) — add `cap150()` enforcement and notification dispatch after `alertRepository.save()`:

**Existing createAlertIfNotDuplicate pattern** (lines 420-432):
```java
// EXISTING (lines 421-429):
Alert alert = new Alert();
alert.setPatientId(patient.getId());
alert.setAlertType(alertType);
alert.setPathwayStepName(step.getName());
alert.setDeviationDescription(description);
alert.setSuggestedAction(step.getSuggestedAction() != null
        ? step.getSuggestedAction() : "Review patient pathway and take corrective action.");
alert.setStatus(AlertStatus.OPEN);
alertRepository.save(alert);
```

**Phase 9 modifications:**
```java
// PHASE 9 CHANGES — apply cap150() and set missingSummary before save:
alert.setDeviationDescription(description);
alert.setSuggestedAction(cap150(
    step.getSuggestedAction() != null
        ? step.getSuggestedAction() : "Review patient pathway and take corrective action.",
    "suggestedAction", patient.getId()));
alert.setMissingSummary(cap150(deriveMissingSummary(step, claudeText), "missingSummary",
    patient.getId()));
alertRepository.save(alert);

// NEW: notification dispatch after save (D-06)
notificationService.dispatchForAlert(alert,
    patient.getFirstName() + " " + patient.getLastName(), patient.getMrn());
```

**cap150() helper to add** (copy `AuditService.truncate()` pattern at lines 117-122, with added warn log):
```java
private String cap150(String value, String fieldName, UUID patientId) {
    if (value == null) return null;
    if (value.length() > 150) {
        log.warn("ALERT_FIELD_TRUNCATED: field={} patient={}", fieldName, patientId);
        return value.substring(0, 150);
    }
    return value;
}
```

**buildAlertDescription() extension** (lines 457-483) — return `missingSummary` alongside description. Since the method currently returns `String`, consider refactoring to return `AlertText` (the record already holds both fields) or use a local field. The simplest approach: after Claude returns `claudeText`, also capture `missingSummary` and set it on the alert directly inside `createAlertIfNotDuplicate()` before calling `buildAlertDescription()`.

---

### `src/main/java/com/onconavigator/activity/AlertGenerationActivityImpl.java` (activity, request-response — modify)

**Analog:** itself (current file)

**Add `missingSummary` parameter** to `generateAlert()` signature (line 47). Follow the same pattern as `deviationDescription` and `suggestedAction`:

**Existing core pattern** (lines 59-68):
```java
Alert alert = new Alert();
alert.setPatientId(patientId);
alert.setAlertType(AlertType.valueOf(alertTypeStr));
alert.setPathwayStepName(pathwayStepName);
alert.setDeviationDescription(deviationDescription);
alert.setSuggestedAction(suggestedAction);
alert.setWorkflowRunId(workflowRunId);
alertRepository.save(alert);

log.info("ALERT_GENERATED: patient={} step={} type={}", patientId, pathwayStepName, alertTypeStr);
```

**Phase 9 additions:**
```java
// Before save: cap both fields and set missingSummary
alert.setDeviationDescription(deviationDescription);
alert.setSuggestedAction(cap150(suggestedAction, "suggestedAction", patientId));
alert.setMissingSummary(cap150(missingSummary, "missingSummary", patientId));
alert.setWorkflowRunId(workflowRunId);
alertRepository.save(alert);

// After save: dispatch notification (D-06)
// Note: ActivityGenerationActivityImpl does NOT have Patient entity in scope.
// Load patient from PatientRepository to get name/MRN for notification dispatch.
```

---

### `src/main/java/com/onconavigator/web/dto/AlertResponse.java` (dto, request-response — modify)

**Analog:** itself (current file)

**Existing record pattern** (all 38 lines) — add `missingSummary` as a new record component:

**Current record** (lines 25-38):
```java
public record AlertResponse(
        UUID id,
        UUID patientId,
        String patientName,
        String patientMrn,
        String alertType,
        String severityLabel,
        String status,
        String pathwayStepName,
        String deviationDescription,
        String suggestedAction,
        OffsetDateTime createdAt,
        String timeElapsed
) {}
```

**Phase 9 addition** — add `missingSummary` after `suggestedAction`:
```java
public record AlertResponse(
        UUID id,
        UUID patientId,
        String patientName,
        String patientMrn,
        String alertType,
        String severityLabel,
        String status,
        String pathwayStepName,
        String deviationDescription,
        String suggestedAction,
        String missingSummary,          // NEW — Phase 9 two-part alert format
        OffsetDateTime createdAt,
        String timeElapsed
) {}
```

**Update `AlertService.toAlertResponse()`** (lines 106-131) — add `a.getMissingSummary()` in the constructor call after `a.getSuggestedAction()`.

---

### Flyway Migrations V21, V22, V23

**Analog:** `src/main/resources/db/migration/V19__template_inheritance.sql`

**Header comment pattern** (V19 lines 1-4):
```sql
-- V21__add_alert_missing_summary.sql
-- Phase 9: Add missing_summary column to alerts table.
-- Column is TEXT (no DB-level constraint — service layer enforces ≤150 chars per D-02).
-- Backfills existing rows and mirrors change on alerts_aud (Pitfall 5 from RESEARCH.md).
```

**ALTER TABLE + backfill + AUD mirror pattern** (V19 lines 8-30):
```sql
-- Add column (idempotent with IF NOT EXISTS)
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS missing_summary TEXT;

-- Backfill: derive missing_summary from first 150 chars of deviation_description (D-03)
UPDATE alerts SET missing_summary = LEFT(deviation_description, 150)
WHERE missing_summary IS NULL;

-- Truncate existing suggested_action values exceeding 150 chars (D-03)
UPDATE alerts SET suggested_action = LEFT(suggested_action, 150)
WHERE suggested_action IS NOT NULL AND LENGTH(suggested_action) > 150;

-- CRITICAL: Mirror on Envers AUD table (Pitfall 5)
ALTER TABLE alerts_aud ADD COLUMN IF NOT EXISTS missing_summary TEXT;

GRANT ALL ON alerts TO onco_app;
```

**New enum type pattern** (V17 provides the `-- flyway:nonTransactional` pattern for PostgreSQL enum):
```sql
-- V22 creates the notification_channel PostgreSQL enum:
-- NOTE: CREATE TYPE cannot be inside a regular transaction;
-- for a brand new type (not ALTER TYPE ADD VALUE), no nonTransactional needed.
CREATE TYPE notification_channel AS ENUM ('TEAMS', 'EMAIL');
```

**Partial unique index pattern** (V7 uses a partial unique index; V22 needs two of them):
```sql
-- Prevent duplicate admin defaults per channel (user_id IS NULL rows)
CREATE UNIQUE INDEX idx_notification_defaults_channel
    ON notification_preferences(channel) WHERE user_id IS NULL AND is_admin_default = TRUE;

-- Prevent duplicate user overrides per (user_id, channel)
CREATE UNIQUE INDEX idx_notification_user_channel
    ON notification_preferences(user_id, channel) WHERE user_id IS NOT NULL;
```

---

### `frontend/src/features/alerts/types.ts` (types, request-response — modify)

**Analog:** itself (current file)

**Existing `AlertResponse` interface** (lines 1-14) — add `missingSummary` field:

```typescript
export interface AlertResponse {
  id: string;
  patientId: string;
  patientName: string;
  patientMrn: string;
  alertType: 'DELAYED_EVENT' | 'MISSING_EVENT' | 'OUT_OF_ORDER' | 'RESULTS_NOT_READY' |
             'SCHEDULING_UNCONFIRMED' | 'DEADLINE_APPROACHING' | 'CANCELLED_EVENT';
  severityLabel: 'OVERDUE' | 'MISSING' | 'OUT OF ORDER' | 'CANCELLED' |
                 'RESULTS PENDING' | 'DEADLINE' | 'UNCONFIRMED';
  status: 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED';
  pathwayStepName: string;
  deviationDescription: string;
  suggestedAction: string;
  missingSummary: string | null;     // NEW — Phase 9 two-part alert format
  createdAt: string;
  timeElapsed: string;
}
```

**Note:** `missingSummary` is `string | null` because existing alerts backfilled from V21 migration will have it set, but null safety is good defensive practice in TypeScript.

---

## Shared Patterns

### PHI Encryption on Bytea Columns
**Source:** `src/main/java/com/onconavigator/domain/Patient.java` lines 44-58
**Source:** `src/main/java/com/onconavigator/security/EncryptionConverter.java` lines 36-160
**Apply to:** `NotificationLog.renderedContent` and `notification_pending_queue.rendered_content_encrypted`

```java
// JPA entity field — copy this exact pattern:
@Convert(converter = EncryptionConverter.class)
@Column(name = "rendered_content", columnDefinition = "bytea", nullable = false)
private String renderedContent;
```

The converter is a `@Converter` class (not a Spring `@Bean`) — it is instantiated by Hibernate and retrieves the `SecretKey` from the Spring context via `ApplicationContextProvider`. No additional wiring is needed beyond the `@Convert` annotation.

---

### Hibernate Envers Audit Trail
**Source:** `src/main/java/com/onconavigator/domain/Alert.java` line 31, `Patient.java` line 37
**Apply to:** `NotificationLog` (contains PHI in encrypted form — requires audit trail)

```java
@Entity
@Table(name = "notification_log")
@Audited                    // Creates notification_log_aud table automatically
public class NotificationLog { ... }
```

**Migration consequence (Pitfall 5):** V23 must explicitly create `notification_log_aud` with all the same columns plus `rev INTEGER NOT NULL` and `revtype SMALLINT`. Copy the `_aud` table pattern from V19 lines 27-30:
```sql
-- In V23__notification_log.sql:
CREATE TABLE notification_log_aud (
    id UUID NOT NULL,
    rev INTEGER NOT NULL,
    revtype SMALLINT,
    alert_id UUID,
    user_id UUID,
    channel notification_channel,
    rendered_content BYTEA,
    is_digest BOOLEAN,
    status VARCHAR(20),
    sent_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id, rev),
    FOREIGN KEY (rev) REFERENCES revinfo(rev)
);
```

---

### JWT Actor Identity Extraction
**Source:** `src/main/java/com/onconavigator/web/AlertController.java` line 95
**Apply to:** `NotificationPreferenceController` (any endpoint that reads/writes a user's own preferences)

```java
// Exact pattern — copy from AlertController:
@AuthenticationPrincipal Jwt jwt
// ...
UUID actorId = UUID.fromString(jwt.getSubject());
```

Always extract `user_id` from `jwt.getSubject()`, never from the request body. Users cannot set notification preferences for other users by supplying a foreign UUID in the body.

---

### @PreAuthorize Role Guards
**Source:** `src/main/java/com/onconavigator/web/AlertController.java` lines 59, 74, 90
**Source:** `src/main/java/com/onconavigator/web/PatientController.java` lines 64, 74
**Apply to:** `NotificationPreferenceController`

```java
// Admin-only (setting org defaults):
@PreAuthorize("hasRole('ADMIN')")

// Any authenticated user can read/update their own preferences:
@PreAuthorize("isAuthenticated()")
```

---

### PHI-Safe Logging
**Source:** `src/main/java/com/onconavigator/activity/SweepActivityImpl.java` lines 96-98
**Source:** `src/main/java/com/onconavigator/service/AuditService.java` lines 84-85
**Apply to:** `LoggingNotificationService`, `DigestDispatchActivityImpl`, `NotificationPreferenceService`

```java
// CORRECT — log UUIDs only:
log.info("NOTIFICATION_DISPATCHED: alert={} user={} channel={}", alert.getId(), userId, channel);
log.info("DIGEST_SWEEP: activeUsers={} dispatched={} queued={}", userCount, dispatched, remaining);

// INCORRECT — never do this:
// log.info("Sending notification to {} MRN {}", patientName, patientMrn);
```

---

### Temporal Activity Registration
**Source:** `src/main/resources/application-local.yml` lines 56-59
**Apply to:** `DigestDispatchActivityImpl`

```yaml
# Under spring.temporal.workers[0]:
activity-beans:
  - pathwayEvaluationActivityImpl
  - alertGenerationActivityImpl
  - sweepActivityImpl
  - digestDispatchActivityImpl      # Phase 9: add this line
```

Auto-discovery via `workers-auto-discovery.packages` does NOT register `@Component` activity beans on the worker. The explicit `activity-beans` list is required (Phase 02-fix decision, verified in STATE.md).

---

### Service-Layer Field Truncation with Warning Log
**Source:** `src/main/java/com/onconavigator/service/AuditService.java` lines 117-122
**Apply to:** `PathwayEvaluationActivityImpl.cap150()`, `AlertGenerationActivityImpl.cap150()`

```java
// AuditService.truncate() is the project's established truncation pattern.
// Phase 9 variant adds a warning log for compliance visibility:
private String cap150(String value, String fieldName, UUID patientId) {
    if (value == null) return null;
    if (value.length() > 150) {
        log.warn("ALERT_FIELD_TRUNCATED: field={} patient={}", fieldName, patientId);
        return value.substring(0, 150);
    }
    return value;
}
```

---

## No Analog Found

All files have strong analogs in the codebase. No new patterns are needed from RESEARCH.md alone.

---

## Metadata

**Analog search scope:** `src/main/java/com/onconavigator/` (all subdirectories), `frontend/src/`, `src/main/resources/db/migration/`
**Files scanned:** 22 Java source files, 5 frontend TypeScript files, 5 Flyway migration files
**Pattern extraction date:** 2026-05-05
