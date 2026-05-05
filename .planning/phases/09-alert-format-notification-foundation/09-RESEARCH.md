# Phase 9: Alert Format + Notification Foundation — Research

**Researched:** 2026-05-05
**Domain:** Alert entity extension, notification infrastructure, Temporal digest scheduling, HIPAA-safe PHI handling in notifications
**Confidence:** HIGH (primary sources: verified codebase + Temporal SDK docs)

---

## Summary

Phase 9 extends the Alert entity with a `missing_summary` field to implement the oncologist-specified two-part notification format (PW-ALL-007), and builds the notification dispatch infrastructure so alerts can be routed to external channels (Teams, email) with per-user preferences. The implementation is entirely log-only in this phase — real channel connectors arrive later — but the full routing, filtering, quiet-hours, and digest-batching logic is built now.

The codebase is well-prepared for this phase. The `Alert` entity is a clean JPA entity with `@Audited` and an established `EncryptionConverter` pattern ready to reuse. The alert creation paths are in two places (`PathwayEvaluationActivityImpl.createAlertIfNotDuplicate()` and `AlertGenerationActivityImpl.generateAlert()`) — both require notification dispatch hooks. The AI pipeline already produces a structured `AlertText` record; extending it to a third field (`missingSummary`) is a straightforward record update.

The main design work in this phase is in: (1) the `notification_preferences` schema and merge logic for admin defaults vs. user overrides, (2) the quiet-hours hold-and-release mechanism selection (a pending-queue table approach wins over in-memory for durability), and (3) the Temporal digest workflow structure. All three involve discrete schema and code decisions documented in the Claude's Discretion section of CONTEXT.md and addressed here.

**Primary recommendation:** Use a `notification_pending_queue` table (not in-memory) for quiet-hours holds. Use one shared Temporal Schedule (not per-user workflows) for digest batching — it runs periodically and queries the pending queue table for users whose digest interval has elapsed. This is simpler to operate and survives restarts without per-user workflow registration overhead.

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Two-Part Alert Model**
- D-01: Three-field model — Alert entity keeps `deviation_description` (detailed, internal) and adds `missing_summary` (≤150 chars, notification-friendly). `suggested_action` capped at 150 characters. `missing_summary` is the notification's primary content.
- D-02: Both `missing_summary` and `suggested_action` capped at 150 characters at the service level (not DB level).
- D-03: Flyway migration truncates existing `suggested_action` values exceeding 150 chars and generates `missing_summary` from first 150 chars of `deviation_description`. New alerts always conform.

**Alert Text Generation**
- D-04: Claude's discretion on how `missing_summary` is populated in the `buildAlertDescription` pipeline.

**Notification Channels**
- D-05: `NotificationService` interface with `LoggingNotificationService` implementation only. No real Teams or email connectors.
- D-06: Immediate dispatch on alert creation — direct call from alert creation path, no async event bus.
- D-07: Dashboard always-on for all users with dashboard access. `notification_preferences` controls external channels only.

**Notification Content**
- D-08: Rich notification payload — patient name, MRN, pathway step name, alert severity/type label, two-part alert (missing_summary + suggested_action), deep link to patient pathway view.
- D-09: `notification_log` table stores the rendered message content (what, to whom, via which channel, at what time).
- D-10: `notification_log.rendered_content` encrypted via `EncryptionConverter`. `@Audited` for Envers revision trail.

**User Preferences**
- D-11: All four preference dimensions implemented: channel selection, severity filter, quiet hours, digest batching.
- D-12: Admin defaults + user override model. Admin sets org-wide defaults; users can override their own preferences.
- D-13: Temporal scheduled workflow for digest batching. Durable, survives restarts.

### Claude's Discretion

- How `missing_summary` is populated in the alert generation pipeline (dual-output vs auto-derivation from `deviation_description`)
- `notification_preferences` table schema design (column types, defaults, constraints)
- `notification_log` table schema and which columns are PHI-encrypted vs plaintext
- `NotificationService` interface method signatures and the dispatch routing logic
- Severity filter implementation (array column vs join table for allowed alert types)
- Quiet hours hold-and-release mechanism (queue table, Temporal timer, or in-memory hold)
- Digest workflow design: per-user workflow instance vs shared scheduled sweep
- How admin defaults are stored and how user overrides merge with them
- Flyway migration structure for new tables and Alert entity column changes
- Notification deep link URL format for the dashboard

### Deferred Ideas (OUT OF SCOPE)

None — discussion stayed within phase scope.
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PW-ALL-007 | Two-part alerts ≤150 chars: "1) What is missing and 2) a suggested action in no more than 150 characters" | Three-field model (D-01/D-02), `AlertText` record extension, service-layer validation before persist |
| PW-ALL-004 | End state: Teams/email for users, dashboard for admin only; Phase 9 builds the infrastructure | `NotificationService` interface + `LoggingNotificationService`, `notification_preferences` table with channel selection |
</phase_requirements>

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| 150-char constraint enforcement | API / Backend (service layer) | — | Constraint is at the service layer per D-02, not DB CHECK constraint. Validated before `alertRepository.save()`. |
| `missing_summary` generation | API / Backend (activity layer) | — | Lives in `PathwayEvaluationActivityImpl.buildAlertDescription()` and `AlertGenerationAiService`. Both are activity/service tier. |
| Notification dispatch (immediate) | API / Backend (service layer) | — | Direct call from alert creation path after `alertRepository.save()` (D-06). No async bus. |
| Notification preferences storage | Database / Storage | API / Backend | `notification_preferences` table; accessed by `NotificationPreferenceService`. |
| Quiet-hours hold-and-release | Database / Storage + API / Backend | Temporal | Pending queue table for durability; Temporal digest workflow drains the queue. |
| Digest batching | Temporal (scheduled workflow) | Database / Storage | Temporal Schedule triggers a sweep workflow on each user's digest interval. Queue table is the durable buffer. |
| PHI-encrypted notification log | Database / Storage | — | `notification_log.rendered_content` encrypted via `EncryptionConverter`. `@Audited` via Envers. |
| `missingSummary` in REST response | API / Backend (DTO) | Frontend | `AlertResponse` record gets `missingSummary` field; frontend TypeScript type updated. |
| Deep link URL format | Frontend | API / Backend | Frontend base URL + `/patients/{patientId}` path. Backend stores the full URL string in notification content. |

---

## Standard Stack

### Core (all already in project, no new dependencies required for Phase 9)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot 3.5.x | via BOM | Application framework, JPA, validation | Already in pom.xml |
| Hibernate Envers | via Boot BOM | `@Audited` on `notification_log` | Already used on all ePHI entities |
| `spring-boot-starter-data-jpa` | via BOM | New entity persistence (`notification_preferences`, `notification_log`) | Already in pom.xml |
| `spring-boot-starter-validation` | via BOM | `@Size(max=150)` on service DTOs | Already in pom.xml |
| `io.temporal:temporal-spring-boot-starter` | 1.32.0 | Digest batching Temporal Schedule | Already in pom.xml |
| Flyway 11.x | via Boot BOM | V21–V23 migrations | Already in pom.xml |
| PostgreSQL 16.x | via Boot BOM | Persistence for all new tables | Already in use |

**No new Maven dependencies are required for Phase 9.** All needed libraries are already declared. [VERIFIED: codebase pom.xml and application-local.yml]

### Supporting (discretion items resolved here)

| Choice | Decision | Rationale |
|--------|----------|-----------|
| `missing_summary` generation | Dual-output from AI pipeline + template derivation | `AlertText` record gains a third field `missingSummary`. `AlertGenerationAiService` adds a `MISSING_SUMMARY:` section to its prompt. Template fallback auto-derives from first 150 chars of `deviationDescription`. |
| Severity filter storage | PostgreSQL `TEXT[]` array column on `notification_preferences` | Simple, no join table needed for V1. `AlertType[]` preferred severity values stored as `text[]`. Spring Data query uses `ARRAY[]` containment operators or Java-side filtering on loaded preferences. |
| Quiet-hours hold | `notification_pending_queue` table with `hold_until TIMESTAMP` column | Survives restarts. Temporal digest workflow drains it. Avoids in-memory state loss on pod restart. Better than a Temporal timer per notification (would create thousands of timers). |
| Digest workflow | Single shared `DigestDispatchWorkflow` using Temporal Schedules API | One `ScheduleClient.createSchedule()` call at startup (or via a Spring ApplicationRunner). The workflow queries `notification_pending_queue` for users whose `next_digest_at` has elapsed. Per-user workflow instances would require registering O(users) workflows — unnecessary complexity. |
| Admin defaults storage | Separate `notification_defaults` table with `is_admin_default = TRUE` and `user_id = NULL` | Clean separation. `NotificationPreferenceService` loads defaults first, then overlays user-specific rows keyed by `user_id`. |
| PHI in `notification_log` | `rendered_content BYTEA` via `EncryptionConverter` (AES-GCM). `notification_channel`, `status`, `sent_at`, `alert_id`, `user_id` all plaintext. | Only the rendered text contains PHI (patient name, MRN, step). All metadata is non-PHI. Consistent with existing project encryption posture. |
| Deep link URL format | Configurable base URL property + `/patients/{patientId}` path. Example: `https://app.onconavigator.example/patients/uuid-here` | Base URL injected from `onconavigator.notification.base-url` config property, defaulting to `http://localhost:5173` for local dev. |

---

## Architecture Patterns

### System Architecture Diagram

```
Alert Creation Path (PathwayEvaluationActivityImpl / AlertGenerationActivityImpl)
        │
        ▼
    alertRepository.save(alert)        ← existing
        │
        ▼
    NotificationService.dispatch(alert)  ← NEW: called after every alert save
        │
        ├─── load notification_preferences for all users with matching patient/channel
        │
        ├─── [immediate channel]: severity filter passes?
        │         │ YES
        │         ├─── quiet hours active?
        │         │         │ YES → insert into notification_pending_queue (hold_until)
        │         │         │ NO  → LoggingNotificationService.send(payload)
        │         │                 + insert into notification_log (SENT status)
        │         │
        │         └─── [digest channel]: insert into notification_pending_queue (type=DIGEST)
        │
        └─── notification_log.save() for all dispatched or queued entries
                │
                ▼ (encrypted rendered_content via EncryptionConverter)
           notification_log table


Temporal Digest Sweep (DigestDispatchWorkflow — Temporal Schedule)
        │ Runs per configured interval (e.g., hourly or per user digest preference)
        ▼
    DigestDispatchActivity.runDigestSweep()
        │
        ├─── query notification_pending_queue WHERE type=DIGEST AND hold_until <= NOW()
        │         grouped by user_id
        │
        ├─── for each user batch:
        │         format digest summary
        │         LoggingNotificationService.sendDigest(user, batch)
        │         update notification_pending_queue rows to DISPATCHED
        │         insert notification_log entry (SENT status, digest=true)
        │
        └─── also drain quiet-hours held items WHERE type=IMMEDIATE AND hold_until <= NOW()
```

### Recommended Project Structure (new files only)

```
src/main/java/com/onconavigator/
├── domain/
│   ├── NotificationPreference.java    # JPA entity for notification_preferences table
│   └── NotificationLog.java           # JPA entity for notification_log table (PHI-encrypted)
├── domain/enums/
│   └── NotificationChannel.java       # Enum: TEAMS, EMAIL (dashboard always-on per D-07)
├── repository/
│   ├── NotificationPreferenceRepository.java
│   └── NotificationLogRepository.java
├── notification/
│   ├── NotificationService.java            # Interface: dispatch(Alert, notificationContext)
│   ├── LoggingNotificationService.java     # Implementation: logs what WOULD be sent
│   ├── NotificationPayload.java            # Value object: the rendered message content
│   └── NotificationPreferenceService.java  # Loads+merges admin defaults + user overrides
├── workflow/
│   ├── DigestDispatchWorkflow.java          # Interface
│   └── DigestDispatchWorkflowImpl.java     # @WorkflowImpl — Temporal cron via Schedule
├── activity/
│   ├── DigestDispatchActivity.java          # Interface
│   └── DigestDispatchActivityImpl.java     # Drains pending queue, dispatches digests
src/main/resources/db/migration/
├── V21__add_alert_missing_summary.sql     # ALTER TABLE alerts ADD COLUMN missing_summary
├── V22__notification_preferences.sql      # CREATE TABLE notification_preferences + defaults
└── V23__notification_log.sql              # CREATE TABLE notification_log
```

### Pattern 1: Extending AlertText Record

**What:** Add `missingSummary` as a third component to the existing `AlertText` record.
**When to use:** All alert text generation paths.

```java
// Source: codebase — src/main/java/com/onconavigator/ai/model/AlertText.java (current)
// Current:
public record AlertText(String deviationDescription, String suggestedAction) {}

// Phase 9 extension:
public record AlertText(String deviationDescription, String suggestedAction, String missingSummary) {}
```

**Template fallback derivation** (when Claude is not called or returns no `MISSING_SUMMARY:` section):
```java
// Auto-derive from deviationDescription — first 150 chars, trimmed
String missingSummary = deviationDescription.length() > 150
    ? deviationDescription.substring(0, 150).trim()
    : deviationDescription;
```

**Claude prompt extension** — add a third section to `AlertPrompts.USER_TEMPLATE`:
```
3. MISSING_SUMMARY: A single sentence (≤150 characters) stating specifically what is missing \
   or overdue. This is the primary content of nurse notifications.

Format your response exactly as:
DESCRIPTION: [your description]
SUGGESTED_ACTION: [your suggested actions]
MISSING_SUMMARY: [your one-sentence summary]
```

**Parser extension** in `AlertGenerationAiService.parseAlertResponse()`:
```java
// After existing DESCRIPTION/SUGGESTED_ACTION parsing, add:
int missingSummaryIdx = response.indexOf("MISSING_SUMMARY:");
String missingSummary = null;
if (missingSummaryIdx >= 0) {
    missingSummary = response.substring(
        missingSummaryIdx + "MISSING_SUMMARY:".length()).strip();
    // Cap at 150 chars defensively
    if (missingSummary.length() > 150) {
        missingSummary = missingSummary.substring(0, 150).trim();
    }
}
```
[VERIFIED: codebase `AlertGenerationAiService.parseAlertResponse()` at lines 97-127]

### Pattern 2: Service-Layer 150-Char Enforcement

**What:** Validate both `missing_summary` and `suggested_action` in the alert creation service path before `alertRepository.save()`. Use explicit length check + truncation with a warning log (not `@Size` annotation — the constraint is a service concern, not a bean validation concern, since the field is TEXT in the DB).

```java
// In createAlertIfNotDuplicate() / generateAlert() before alertRepository.save():
private String cap150(String value, String fieldName, UUID patientId) {
    if (value == null) return null;
    if (value.length() > 150) {
        log.warn("ALERT_FIELD_TRUNCATED: field={} patient={}", fieldName, patientId);
        return value.substring(0, 150);
    }
    return value;
}
```
[ASSUMED: truncation-with-log is preferred over throwing a validation exception for alert text — alerts must be created even if text generation misbehaves]

### Pattern 3: NotificationService Interface

```java
package com.onconavigator.notification;

import com.onconavigator.domain.Alert;
import java.util.UUID;

/**
 * Dispatches alert notifications to eligible users based on their channel preferences.
 * Immediate dispatch only; digest collection is handled via the DigestDispatchWorkflow.
 *
 * <p>Implementations must be PHI-safe: only log alert UUIDs and user UUIDs,
 * never patient names or MRNs.
 */
public interface NotificationService {
    /**
     * Dispatch notifications for a newly created alert.
     * Called immediately after alertRepository.save().
     *
     * @param alert the newly persisted Alert entity
     * @param patientName decrypted patient name (for payload rendering only — do not log)
     * @param patientMrn  decrypted patient MRN (for payload rendering only — do not log)
     */
    void dispatchForAlert(Alert alert, String patientName, String patientMrn);
}
```

**Why `patientName` and `patientMrn` are passed as parameters:** The `Alert` entity itself contains no PHI (it holds `patientId` UUID only). The notification payload (D-08) requires patient name and MRN. The `NotificationService` receives them from the caller, who has already loaded the `Patient` entity. This avoids the notification service needing a `PatientRepository` dependency.

[VERIFIED: Alert entity has no PHI — only `patientId UUID` — consistent with zero-PHI boundary in existing code]

### Pattern 4: NotificationPreference Schema Design

**notification_preferences table:**
```sql
CREATE TABLE notification_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID,                                -- NULL = admin default row
    is_admin_default BOOLEAN NOT NULL DEFAULT FALSE,
    channel notification_channel NOT NULL,        -- TEAMS, EMAIL
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    -- Severity filter: array of alert types this user/channel wants to receive
    -- Empty array = receive all types
    alert_type_filter TEXT[] NOT NULL DEFAULT '{}',
    -- Quiet hours (24-hour clock, e.g., 22 = 10pm, 7 = 7am)
    quiet_hours_start INTEGER,                   -- NULL = no quiet hours
    quiet_hours_end INTEGER,                     -- NULL = no quiet hours
    -- Digest settings
    digest_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    digest_interval_hours INTEGER NOT NULL DEFAULT 4,
    next_digest_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
-- One row per (user_id, channel) — UNIQUE prevents duplicate entries
CREATE UNIQUE INDEX idx_notification_preferences_user_channel
    ON notification_preferences(COALESCE(user_id, '00000000-0000-0000-0000-000000000000'::UUID), channel);
```

**notification_pending_queue table:**
```sql
CREATE TABLE notification_pending_queue (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_id UUID NOT NULL REFERENCES alerts(id),
    user_id UUID NOT NULL,
    channel notification_channel NOT NULL,
    hold_type VARCHAR(20) NOT NULL, -- 'QUIET_HOURS' or 'DIGEST'
    hold_until TIMESTAMP WITH TIME ZONE NOT NULL,
    rendered_content_encrypted BYTEA NOT NULL, -- AES-GCM encrypted
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING', -- PENDING, DISPATCHED
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_notification_pending_dispatch
    ON notification_pending_queue(status, hold_until)
    WHERE status = 'PENDING';
```

**notification_log table:**
```sql
CREATE TABLE notification_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_id UUID NOT NULL REFERENCES alerts(id),
    user_id UUID NOT NULL,
    channel notification_channel NOT NULL,
    rendered_content BYTEA NOT NULL,   -- encrypted via EncryptionConverter (PHI: patient name + MRN)
    is_digest BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'SENT',  -- SENT, FAILED
    sent_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
```

[ASSUMED: `notification_pending_queue` is a separate table from `notification_log` — queue is operational, log is the immutable audit trail]

### Pattern 5: Temporal Digest Dispatch Workflow

**Decision: single shared `DigestDispatchSchedule`, not per-user workflows.**

```java
// DigestDispatchWorkflow.java
@WorkflowInterface
public interface DigestDispatchWorkflow {
    @WorkflowMethod
    void runDigestPass();
}

// DigestDispatchWorkflowImpl.java
@WorkflowImpl(taskQueues = TemporalConfig.TASK_QUEUE)
public class DigestDispatchWorkflowImpl implements DigestDispatchWorkflow {
    private final DigestDispatchActivity digestActivity = Workflow.newActivityStub(
        DigestDispatchActivity.class,
        ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofMinutes(5))
            .build());

    @Override
    public void runDigestPass() {
        digestActivity.drainPendingQueue();
    }
}
```

**Schedule registration (Spring ApplicationRunner or startup bean):**
```java
// Source: Context7 /temporalio/sdk-java Temporal Schedules API
Schedule schedule = Schedule.newBuilder()
    .setAction(ScheduleActionStartWorkflow.newBuilder()
        .setWorkflowType(DigestDispatchWorkflow.class)
        .setOptions(WorkflowOptions.newBuilder()
            .setWorkflowId("digest-dispatch-run-" + Workflow.currentTimeMillis())
            .setTaskQueue(TemporalConfig.TASK_QUEUE)
            .build())
        .build())
    .setSpec(ScheduleSpec.newBuilder()
        .setIntervals(Collections.singletonList(
            ScheduleIntervalSpec.newBuilder()
                .setEvery(Duration.ofMinutes(30)) // Check every 30 min
                .build()))
        .build())
    .setPolicy(SchedulePolicy.newBuilder()
        .setOverlap(ScheduleOverlapPolicy.SKIP)
        .build())
    .build();
scheduleClient.createSchedule("digest-dispatch-schedule", schedule,
    ScheduleOptions.newBuilder().build());
```

[VERIFIED: Temporal Schedules API pattern from Context7 /temporalio/sdk-java]

**Worker registration:** `DigestDispatchActivityImpl` bean name must be added to `activity-beans` in `application-local.yml`. DigestDispatchWorkflowImpl is auto-discovered via `@WorkflowImpl` and `workers-auto-discovery.packages`. [VERIFIED: application-local.yml pattern — explicit activity-beans list required, Phase 02-fix decision]

### Anti-Patterns to Avoid

- **Per-user Temporal workflow for digest:** Registering one workflow instance per user creates O(users) durable workflows. A single shared Schedule sweeping the `notification_pending_queue` table is correct. [ASSUMED: per-user is viable but overengineered for V1 pilot scale]
- **In-memory quiet-hours hold:** Application restart loses all held notifications. Use the `notification_pending_queue` table. [VERIFIED: project uses PostgreSQL for all durable state]
- **PHI in Temporal workflow history:** `dispatchForAlert()` must NOT pass patient name/MRN as workflow parameters or activity parameters that enter Temporal's history. The `notification_pending_queue` stores the encrypted rendered payload out-of-band. [VERIFIED: SEC-06 — no PHI in workflow history; consistent with existing workflow pattern where Patient entity is loaded in activities]
- **DB-level CHECK constraint for 150-char cap:** The CONTEXT.md and success criteria specify service-level constraint. Keep the DB column as TEXT; enforce in Java. This allows migrations without schema changes if the limit is ever revised.
- **Calling `@Audited` entities from DigestDispatchActivity inside Temporal:** The `@Audited` annotation works normally via Spring JPA — no special handling needed in Temporal activities, since activities run in Spring context. Envers `_AUD` tables get revision records on all save/update operations. [VERIFIED: existing Temporal activities use Spring JPA with @Audited entities without issues]
- **Keycloak `preferred_username` as user identifier in `notification_preferences`.** Use `jwt.getSubject()` (Keycloak user UUID) as `user_id` FK — consistent with how all existing controllers extract actor identity. [VERIFIED: AlertController line 95 — `UUID.fromString(jwt.getSubject())`]

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| PHI field encryption | Custom AES implementation | `EncryptionConverter` (already in codebase) | Reuse existing AES-GCM implementation — HIPAA-safe, tested |
| Durable scheduled execution | Spring `@Scheduled` | Temporal Schedules API | Spring `@Scheduled` is in-memory and lost on restart; Temporal provides durable scheduling with crash recovery |
| Audit trail for `notification_log` | Custom audit table + triggers | Hibernate Envers `@Audited` | Envers creates `_AUD` tables automatically; consistent with all other ePHI entities in the project |
| String length enforcement | DB CHECK constraints | Service-layer Java check before `save()` | Keeps constraint logic in code, not DB schema; easier to adjust |

**Key insight:** The project already has the infrastructure for every hard problem in this phase (encryption, auditing, Temporal, role-based dispatch). Phase 9 is wiring patterns that already exist, not inventing new ones.

---

## Common Pitfalls

### Pitfall 1: AlertText Record is a Java `record` — adding a field breaks all constructors

**What goes wrong:** `AlertText` is `record AlertText(String deviationDescription, String suggestedAction)`. Every call site that constructs it as `new AlertText(desc, action)` breaks at compile time when a third field is added.

**Why it happens:** Java records have a canonical constructor with all components. Adding `missingSummary` changes the constructor signature.

**How to avoid:** Search all `new AlertText(` usages before the change and update them atomically. Sites affected: `AlertGenerationAiService.parseAlertResponse()` and the two fallback/null-return paths. The `generateAlertFallback()` returns `null` — no change needed there.

**Warning signs:** Compile errors on `new AlertText(` call sites.

[VERIFIED: AlertText.java — record with 2 components; AlertGenerationAiService constructs it at line 126]

### Pitfall 2: `createAlertIfNotDuplicate()` passes `suggestedAction` from `step.getSuggestedAction()` without the 150-char cap

**What goes wrong:** The existing code at PathwayEvaluationActivityImpl line 427:
```java
alert.setSuggestedAction(step.getSuggestedAction() != null
    ? step.getSuggestedAction() : "Review patient pathway and take corrective action.");
```
This sets `suggestedAction` directly from the pathway step's template value, bypassing the cap. Phase 9 requires the cap applied here too, not just on Claude-generated text.

**How to avoid:** Apply `cap150()` to ALL set calls for both `suggestedAction` and `missingSummary` before `alertRepository.save()`.

[VERIFIED: PathwayEvaluationActivityImpl lines 426-428]

### Pitfall 3: Temporal activity auto-discovery does NOT work for new activities

**What goes wrong:** New `DigestDispatchActivityImpl` bean is not registered on the worker automatically even with `workers-auto-discovery.packages` configured.

**How to avoid:** Add `digestDispatchActivityImpl` to the explicit `activity-beans` list in `application-local.yml` under the `onco-pathway-worker`. [VERIFIED: Phase 02-fix decision in STATE.md — "temporal-spring-boot-starter does NOT auto-register @Component activity beans on workers — explicit activity-beans list required"]

### Pitfall 4: `notification_preferences` UNIQUE constraint on `user_id` is nullable

**What goes wrong:** PostgreSQL treats NULL as distinct from every value, including other NULLs. A standard `UNIQUE(user_id, channel)` constraint would allow multiple admin-default rows per channel (all with `user_id = NULL`).

**How to avoid:** Use a partial unique index with `COALESCE` or a separate `is_admin_default BOOLEAN` column with a separate unique partial index:
```sql
-- Admin default: at most one per channel where user_id IS NULL
CREATE UNIQUE INDEX idx_notification_defaults_channel
    ON notification_preferences(channel) WHERE user_id IS NULL;
-- User overrides: one per (user_id, channel)
CREATE UNIQUE INDEX idx_notification_user_channel
    ON notification_preferences(user_id, channel) WHERE user_id IS NOT NULL;
```
[ASSUMED: standard PostgreSQL NULL handling; verified pattern used in existing V7 migration with partial unique index]

### Pitfall 5: Envers `_AUD` tables must be updated in the same migration that adds columns

**What goes wrong:** Adding a column to `notification_log` in a later migration without updating `notification_log_aud` causes Hibernate Envers to throw on startup because the audit table schema doesn't match.

**How to avoid:** In V23 (notification_log creation), also create `notification_log_aud` with the same columns plus Envers `REV`, `REVTYPE` columns. Consistent with V19 (`pathway_templates_aud` updated in same migration).

[VERIFIED: V19__template_inheritance.sql lines 27-30 — explicit _AUD table updates]

### Pitfall 6: PHI passing from Temporal activity to `NotificationService`

**What goes wrong:** If `DigestDispatchActivityImpl` tries to build notification payloads by passing patient PHI through Temporal activity parameters, it violates SEC-06 (no PHI in workflow history).

**How to avoid:** The `notification_pending_queue` stores the encrypted rendered payload. The digest activity decrypts and dispatches from the encrypted queue entry — it never passes PHI through the Temporal activity parameter boundary. [VERIFIED: SEC-06 constraint and existing PHI-safe activity pattern]

### Pitfall 7: `notification_pending_queue.hold_until` timezone handling

**What goes wrong:** Quiet hours are defined as integers (e.g., `quiet_hours_start = 22`, `quiet_hours_end = 7`). Computing `hold_until` requires knowing the user's timezone. Without an explicit timezone, UTC is assumed, which may not match the clinical practice's operating hours.

**How to avoid:** For V1 pilot (single practice), compute `hold_until` server-side using the application's JVM timezone (configured via `TZ` environment variable). Add a `timezone` column to `notification_preferences` as VARCHAR for future use, defaulting to `UTC` if null.

[ASSUMED: single-practice V1 makes server timezone acceptable; future multi-practice would need per-user timezone]

---

## Code Examples

### Verified Pattern: Existing `createAlertIfNotDuplicate()` — Hook Point

```java
// Source: PathwayEvaluationActivityImpl.java lines 412-433 (verified)
// Phase 9 adds notification dispatch AFTER alertRepository.save():

private String createAlertIfNotDuplicate(Patient patient, PatientPathwayStep step,
        AlertType alertType, String defaultDescription) {
    boolean isDuplicate = alertRepository.existsByPatientIdAndPathwayStepNameAndStatus(
            patient.getId(), step.getName(), AlertStatus.OPEN);
    if (isDuplicate) return null;

    String description = buildAlertDescription(step, alertType, defaultDescription, patient);

    Alert alert = new Alert();
    // ... set fields including cap150(missingSummary) and cap150(suggestedAction) ...
    alertRepository.save(alert);   // ← Phase 9: add notification dispatch after this line

    // NEW Phase 9:
    // notificationService.dispatchForAlert(alert, patient.getFirstName() + " " + patient.getLastName(), patient.getMrn());

    return alertType.name() + ": step '" + step.getName() + "' for patient " + patient.getId();
}
```

### Verified Pattern: Temporal Schedules API

```java
// Source: Context7 /temporalio/sdk-java — verified
import io.temporal.client.schedules.*;
import java.time.Duration;
import java.util.Collections;

ScheduleClient scheduleClient = ScheduleClient.newInstance(serviceStubs);

Schedule schedule = Schedule.newBuilder()
    .setAction(ScheduleActionStartWorkflow.newBuilder()
        .setWorkflowType(DigestDispatchWorkflow.class)
        .setOptions(WorkflowOptions.newBuilder()
            .setWorkflowId("digest-dispatch")
            .setTaskQueue(TemporalConfig.TASK_QUEUE)
            .build())
        .build())
    .setSpec(ScheduleSpec.newBuilder()
        .setIntervals(Collections.singletonList(
            ScheduleIntervalSpec.newBuilder()
                .setEvery(Duration.ofMinutes(30))
                .build()))
        .build())
    .setPolicy(SchedulePolicy.newBuilder()
        .setOverlap(ScheduleOverlapPolicy.SKIP)
        .build())
    .build();

// Register on application startup — idempotent via try/catch on AlreadyExistsException
try {
    scheduleClient.createSchedule("digest-dispatch-schedule", schedule,
        ScheduleOptions.newBuilder().build());
} catch (ScheduleAlreadyRunningException e) {
    log.info("Digest dispatch schedule already registered");
}
```
[VERIFIED: Context7 /temporalio/sdk-java Schedules API]

### Verified Pattern: `EncryptionConverter` Reuse on `notification_log.rendered_content`

```java
// Source: EncryptionConverter.java (verified) — same converter, new entity
@Entity
@Table(name = "notification_log")
@Audited
public class NotificationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "alert_id", nullable = false)
    private UUID alertId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", columnDefinition = "notification_channel", nullable = false)
    private NotificationChannel channel;

    // PHI: patient name + MRN embedded in rendered content — must be encrypted
    @Convert(converter = EncryptionConverter.class)
    @Column(name = "rendered_content", columnDefinition = "bytea", nullable = false)
    private String renderedContent;

    @Column(name = "is_digest", nullable = false)
    private boolean digest = false;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "SENT";

    @Column(name = "sent_at", nullable = false, updatable = false)
    private OffsetDateTime sentAt;

    @PrePersist
    void prePersist() {
        this.sentAt = OffsetDateTime.now();
    }
}
```
[VERIFIED: EncryptionConverter.java pattern; Patient entity uses exact same `@Convert(converter = EncryptionConverter.class)` pattern on `@Column(columnDefinition = "bytea")`]

### Flyway Migration Sequence (Next versions: V21, V22, V23)

```sql
-- V21__add_alert_missing_summary.sql
-- Phase 9: Add missing_summary column to alerts table.
-- Column is TEXT (no DB-level length constraint — service layer enforces ≤150 chars).
-- Migration backfills existing rows from first 150 chars of deviation_description.
-- Also truncates existing suggested_action values exceeding 150 chars (D-03).

ALTER TABLE alerts ADD COLUMN IF NOT EXISTS missing_summary TEXT;

-- Backfill: derive missing_summary from first 150 chars of deviation_description
UPDATE alerts SET missing_summary = LEFT(deviation_description, 150)
WHERE missing_summary IS NULL;

-- Truncate existing suggested_action values exceeding 150 chars (D-03)
UPDATE alerts SET suggested_action = LEFT(suggested_action, 150)
WHERE suggested_action IS NOT NULL AND LENGTH(suggested_action) > 150;

-- Mirror change to Envers audit table (Pitfall 5: _AUD must match entity)
ALTER TABLE alerts_aud ADD COLUMN IF NOT EXISTS missing_summary TEXT;

GRANT ALL ON alerts TO onco_app;
```

```sql
-- V22__notification_preferences.sql
-- Phase 9: Notification preferences table + admin defaults seed.
-- notification_channel enum for channel discrimination.

CREATE TYPE notification_channel AS ENUM ('TEAMS', 'EMAIL');

CREATE TABLE notification_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID,
    is_admin_default BOOLEAN NOT NULL DEFAULT FALSE,
    channel notification_channel NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    alert_type_filter TEXT[] NOT NULL DEFAULT '{}',
    quiet_hours_start INTEGER,
    quiet_hours_end INTEGER,
    timezone VARCHAR(100) NOT NULL DEFAULT 'UTC',
    digest_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    digest_interval_hours INTEGER NOT NULL DEFAULT 4,
    next_digest_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

-- Partial unique indexes to handle NULL user_id for admin defaults
CREATE UNIQUE INDEX idx_notification_defaults_channel
    ON notification_preferences(channel) WHERE user_id IS NULL AND is_admin_default = TRUE;
CREATE UNIQUE INDEX idx_notification_user_channel
    ON notification_preferences(user_id, channel) WHERE user_id IS NOT NULL;

-- Seed admin defaults: both channels enabled, no quiet hours, no digest
INSERT INTO notification_preferences (is_admin_default, channel, enabled)
VALUES (TRUE, 'TEAMS', FALSE),   -- disabled until real connector exists
       (TRUE, 'EMAIL', FALSE);   -- disabled until real connector exists

-- Notification pending queue (quiet hours + digest hold)
CREATE TABLE notification_pending_queue (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_id UUID NOT NULL REFERENCES alerts(id),
    user_id UUID NOT NULL,
    channel notification_channel NOT NULL,
    hold_type VARCHAR(20) NOT NULL CHECK (hold_type IN ('QUIET_HOURS', 'DIGEST')),
    hold_until TIMESTAMP WITH TIME ZONE NOT NULL,
    rendered_content_encrypted BYTEA NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'DISPATCHED')),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notification_pending_dispatch
    ON notification_pending_queue(status, hold_until) WHERE status = 'PENDING';

GRANT ALL ON notification_preferences TO onco_app;
GRANT ALL ON notification_pending_queue TO onco_app;
```

```sql
-- V23__notification_log.sql
-- Phase 9: Immutable notification dispatch audit trail.

CREATE TABLE notification_log (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    alert_id UUID NOT NULL REFERENCES alerts(id),
    user_id UUID NOT NULL,
    channel notification_channel NOT NULL,
    rendered_content BYTEA NOT NULL,   -- AES-GCM encrypted (contains PHI: patient name + MRN)
    is_digest BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(20) NOT NULL DEFAULT 'SENT',
    sent_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_notification_log_alert ON notification_log(alert_id);
CREATE INDEX idx_notification_log_user ON notification_log(user_id, sent_at DESC);

-- Envers audit table for notification_log
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

GRANT ALL ON notification_log TO onco_app;
GRANT ALL ON notification_log_aud TO onco_app;
```

[VERIFIED: Migration numbering — last migration is V20 (verified via `ls db/migration/`); next three are V21, V22, V23]

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Spring `@Scheduled` for recurring jobs | Temporal Schedules API | Temporal 1.4+ | Durable scheduling that survives restarts; correct for Phase 9 digest |
| Single alert text field | Two-part format (missing_summary + suggested_action) | Phase 9 | PW-ALL-007 compliance |
| Temporal cron via `WorkflowOptions.setCronSchedule()` | `ScheduleClient.createSchedule()` | Temporal 1.15+ | New Schedules API is the recommended approach; cron string on WorkflowOptions still works but is older pattern |

**Deprecated/Outdated:**
- `WorkflowOptions.setCronSchedule(cronString)` — works but is the older pattern; `ScheduleClient` is now the recommended Temporal approach. [CITED: Context7 /temporalio/sdk-java]

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Truncation with warning log is preferred over throwing a validation exception when alert text exceeds 150 chars | Pattern 2 (150-char enforcement) | If exception is preferred, the caller needs to handle it — alert creation must not fail silently |
| A2 | A single shared DigestDispatchSchedule (not per-user workflows) is the correct design for V1 pilot scale | Pattern 5, Architecture Diagram | If user counts grow significantly, a per-user approach might be needed (more targeted intervals) |
| A3 | `notification_pending_queue` is separate from `notification_log` | Schema section | If they're combined, the schema simplifies but the queue management logic changes |
| A4 | JVM/server timezone is acceptable for quiet-hours computation in V1 single-practice pilot | Pitfall 7 | If the practice operates across timezones, UTC assumption breaks quiet hours |
| A5 | `ScheduleAlreadyRunningException` is the correct exception to catch on duplicate schedule creation | Pattern 5 code example | If a different exception is thrown, the idempotency guard will not catch it — schedule creation fails on restart |

---

## Open Questions (RESOLVED)

1. **Who can manage notification_preferences via the REST API?**
   - What we know: D-12 says admin sets defaults, users override their own. D-07 says dashboard is always-on.
   - What's unclear: Is there a REST API endpoint for users to update their own preferences in Phase 9, or is that deferred (since channels are log-only anyway)?
   - Recommendation: Include `GET /api/notification-preferences` and `PUT /api/notification-preferences` in Phase 9 for completeness, restricted to `ROLE_ADMIN` for defaults and any authenticated user for their own settings. Without an API, there's no way to set preferences even for testing.
   - **RESOLVED:** Include GET/PUT endpoints in Plan 09-02 (NotificationPreferenceController), restricted by role. Admin sets defaults, authenticated users manage their own. Additionally seed one enabled preference row so the log-only pipeline is testable without the API.

2. **Deep link URL base — how is it configured in local dev?**
   - What we know: D-08 requires a deep link to `patients/{patientId}` in notifications.
   - What's unclear: The frontend runs on `http://localhost:5173` in dev. The URL needs to be configurable.
   - Recommendation: Add `onconavigator.notification.base-url` config property, defaulting to `http://localhost:5173` for local dev. Override per Spring profile.
   - **RESOLVED:** Use `onconavigator.notification.base-url` config property defaulting to `http://localhost:5173`. Implemented in Plan 09-02 (LoggingNotificationService `@Value` annotation).

3. **`notification_log.rendered_content` — what exactly is rendered for the log-only implementation?**
   - What we know: D-09 requires storing what was sent. D-05 is log-only.
   - What's unclear: Should the rendered content match what WOULD be sent via Teams (JSON card format) or a simpler human-readable string?
   - Recommendation: Use a simple human-readable format for the log-only implementation. JSON card format can be added when the real Teams connector is built. Changing the `rendered_content` format at that point is fine since the field is opaque encrypted BYTEA.

   - **RESOLVED:** Use simple human-readable string format for the log-only implementation. NotificationPayload.render() produces this format. Can be changed to JSON card when real connectors are built (field is opaque encrypted BYTEA).
---

## Environment Availability

Step 2.6: SKIPPED — Phase 9 is purely backend code and schema changes. All required runtimes (Java 21, PostgreSQL, Temporal) are already validated as running in Phases 1-8. No new external tools or services are required.

---

## Security Domain

`security_enforcement: true` in config.json.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | Notification preferences require JWT auth but no new auth mechanisms |
| V3 Session Management | No | Stateless JWT — no session state involved |
| V4 Access Control | Yes | `notification_preferences` endpoints must enforce role-based access; only admin can set org defaults |
| V5 Input Validation | Yes | `missing_summary` and `suggested_action` length validated at service layer before persist |
| V6 Cryptography | Yes | `notification_log.rendered_content` and `notification_pending_queue.rendered_content_encrypted` use existing `EncryptionConverter` (AES-256-GCM) — never hand-roll |

### Known Threat Patterns for This Stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| PHI in notification queue (pending items readable if DB compromised) | Information Disclosure | `notification_pending_queue.rendered_content_encrypted` encrypted via `EncryptionConverter` before insert |
| PHI in Temporal workflow history (patient name/MRN as activity params) | Information Disclosure | Store rendered payload in encrypted DB table; never pass PHI through Temporal parameter boundary (SEC-06) |
| User A setting notification preferences for User B | Elevation of Privilege | `@PreAuthorize` on preference endpoints: admin can set any, users can only read/update their own (by extracting `user_id` from JWT subject, not from request body) |
| Unauthorized access to notification_log (contains PHI) | Information Disclosure | `notification_log` query endpoints restricted to `ROLE_ADMIN`; `rendered_content` is encrypted even at DB query level |
| Notification content injection (attacker-controlled alert text in notification) | Tampering | Alert text is generated from pathway templates (admin-controlled) or Claude with zero-PHI prompts — not user-controlled input |

---

## Sources

### Primary (HIGH confidence)
- Codebase: `Alert.java` — verified entity structure, existing fields, `@Audited` pattern
- Codebase: `AlertText.java` — verified 2-component record structure; Phase 9 adds third component
- Codebase: `AlertGenerationAiService.java` — verified parsing pipeline; extension point for `MISSING_SUMMARY:` section
- Codebase: `PathwayEvaluationActivityImpl.java` — verified `createAlertIfNotDuplicate()` and `buildAlertDescription()` hook points
- Codebase: `AlertGenerationActivityImpl.java` — verified second alert creation path requiring dispatch hook
- Codebase: `EncryptionConverter.java` — verified AES-GCM pattern for reuse on `notification_log`
- Codebase: `DailySweepWorkflowImpl.java` — verified Temporal workflow pattern for digest workflow
- Codebase: `application-local.yml` — verified explicit `activity-beans` registration requirement
- Codebase: `V17__add_alert_type_values.sql` — verified `flyway:nonTransactional` comment for PostgreSQL enum ALTER TYPE
- Codebase: `V19__template_inheritance.sql` — verified pattern for updating `_AUD` tables in same migration
- Codebase: `AlertController.java` line 95 — verified `jwt.getSubject()` as user identity pattern
- Codebase: `STATE.md` — Phase 02-fix decision: Temporal activity auto-discovery does not work, explicit bean list required
- Context7: `/temporalio/sdk-java` — Temporal Schedules API (`ScheduleClient.createSchedule`) and `Workflow.sleep` patterns

### Secondary (MEDIUM confidence)
- `.planning/phases/09-alert-format-notification-foundation/09-CONTEXT.md` — all D-* decisions from discussion session

### Tertiary (LOW confidence)
- None — all claims are directly verifiable from codebase inspection or Context7 documentation

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all libraries already in pom.xml; verified against codebase
- Architecture: HIGH — integration points verified from actual source code reads
- Schema patterns: HIGH — consistent with existing Flyway migrations (V17, V18, V19 read and verified)
- Temporal Schedules API: HIGH — verified via Context7 /temporalio/sdk-java
- PHI handling: HIGH — follows verified existing `EncryptionConverter` + `@Audited` pattern
- Discretion decisions (digest design, queue table choice): MEDIUM — recommended from analysis, not mandated by a locked decision

**Research date:** 2026-05-05
**Valid until:** 2026-06-05 (stable stack; Temporal SDK and Spring Boot are slow-moving)
