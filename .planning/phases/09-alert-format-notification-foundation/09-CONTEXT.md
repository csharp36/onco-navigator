# Phase 9: Alert Format + Notification Foundation - Context

**Gathered:** 2026-05-05
**Status:** Ready for planning

<domain>
## Phase Boundary

Alerts adopt the oncologist-specified two-part format: a concise "what's missing" summary (≤150 chars) paired with a suggested corrective action (≤150 chars). A notification infrastructure is established so alerts can be dispatched to external channels (Teams, email) beyond the dashboard, with per-user preferences for channel selection, severity filtering, quiet hours, and digest batching.

This phase delivers: a `missing_summary` column on the Alert entity (short notification-friendly "what is missing"), a 150-character service-level constraint on both `missing_summary` and `suggested_action`, a `notification_preferences` table with admin defaults + user overrides, a `NotificationService` interface with a `LoggingNotificationService` implementation, a `notification_log` table with PHI-encrypted rendered payloads, notification dispatch on alert creation, severity filtering logic, quiet hours hold-and-release, digest batching via Temporal scheduled workflow, and Flyway migration for existing alert data truncation/backfill.

This phase does NOT build real Teams webhook or email SMTP connectors (log-only for now), template admin UI (ADV-01), or changes to the evaluation engine's deviation detection logic.

</domain>

<decisions>
## Implementation Decisions

### Two-Part Alert Model
- **D-01:** **Three-field model** — Alert entity keeps `deviation_description` (detailed, internal) and adds `missing_summary` (short ≤150 chars, notification-friendly "what is missing"). `suggested_action` is capped at 150 characters. `missing_summary` is the notification's primary content; `deviation_description` provides full detail on the dashboard.
- **D-02:** **Both fields capped at 150 chars** — `missing_summary` and `suggested_action` are both constrained to 150 characters at the service level, matching the oncologist's PW-ALL-007 specification for concise two-part alerts.
- **D-03:** **Truncate on migration** — Flyway migration truncates existing `suggested_action` values exceeding 150 chars and generates `missing_summary` from existing `deviation_description` (first 150 chars). New alerts always conform to the constraint.

### Alert Text Generation
- **D-04:** **Claude's discretion on missing_summary population** — Claude decides how `missing_summary` is generated in the `buildAlertDescription` pipeline. Options include dual-output from template/Claude generation or auto-derivation from `deviation_description`. The approach should integrate cleanly with the existing template-first + Claude fallback pattern in `PathwayEvaluationActivityImpl.buildAlertDescription()`.

### Notification Channels
- **D-05:** **Interface + log-only implementation** — `NotificationService` interface with `sendAlert(Alert, User, Channel)` method. `LoggingNotificationService` implementation logs what WOULD be sent. Real `TeamsNotificationService` and `EmailNotificationService` implementations added in a future phase.
- **D-06:** **Immediate dispatch on alert creation** — Each new alert triggers immediate notification dispatch to all users with matching preferences for that patient/channel. No async event bus for now — direct call from alert creation path.
- **D-07:** **Dashboard always-on** — All users with dashboard access always see alerts there. `notification_preferences` only controls external channels (Teams, email). No risk of a user accidentally disabling their only alert source.

### Notification Content
- **D-08:** **Rich notification payload** — Notifications include: patient name, MRN, pathway step name, alert severity/type label, the two-part alert (missing_summary + suggested_action), and a deep link to the patient's pathway view in the dashboard.
- **D-09:** **Stored rendered payload** — `notification_log` table stores the rendered message content (what was sent, to whom, via which channel, at what time). Useful for audit trail and debugging delivery issues.
- **D-10:** **PHI columns encrypted** — `notification_log.rendered_content` encrypted via `EncryptionConverter` (same AES-GCM pattern as Patient fields). `@Audited` for Envers revision trail. Consistent with project HIPAA posture.

### User Preferences
- **D-11:** **Four preference dimensions, all implemented** — Channel selection, severity filter, quiet hours, and digest batching are all built in Phase 9 with full logic, not just schema stubs.
- **D-12:** **Admin defaults + user override** — Admin sets organization-wide default preferences. Individual users can override their own channel preferences. Balances central control with user flexibility.
- **D-13:** **Temporal scheduled workflow for digest batching** — A Temporal workflow runs on each user's digest interval, collects pending notifications, formats a digest summary, and dispatches. Durable, survives restarts, consistent with the project's all-in-on-Temporal architecture.

### Claude's Discretion
- How `missing_summary` is populated in the alert generation pipeline (dual-output vs auto-derivation from deviation_description)
- `notification_preferences` table schema design (column types, defaults, constraints)
- `notification_log` table schema and which columns are PHI-encrypted vs plaintext
- `NotificationService` interface method signatures and the dispatch routing logic
- Severity filter implementation (array column vs join table for allowed alert types)
- Quiet hours hold-and-release mechanism (queue table, Temporal timer, or in-memory hold)
- Digest workflow design: per-user workflow instance vs shared scheduled sweep
- How admin defaults are stored and how user overrides merge with them
- Flyway migration structure for new tables and Alert entity column changes
- Notification deep link URL format for the dashboard

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Clinical Context
- `docs/Pathway-Template-Review-Worksheet.md` — Oncologist clinical review. PW-ALL-007: "The communication that the system will give to the nurse should have 2 parts. 1) What is missing and 2) a suggested action in no more than 150 characters." PW-ALL-004: "At the end product only admin will have access to the dashboard in order to monitor and troubleshoot the system. Users will receive teams or email notifications only."
- `docs/Onco-Navigator AI - V1 Feature Specification v2.md` — Original alert scenarios and suggested action text examples.

### Requirements
- `.planning/REQUIREMENTS.md` — PW-ALL-007 (two-part alerts ≤150 chars), PW-ALL-004 (end state: Teams/email, dashboard for admin only). Also ALRT-01 through ALRT-06 (alert dashboard requirements that coexist with notification channels).

### Prior Phase Context
- `.planning/phases/07-referral-trigger-enhanced-timing-status-aware-evaluation/07-CONTEXT.md` — Phase 7 added 4 new alert types (RESULTS_NOT_READY, SCHEDULING_UNCONFIRMED, DEADLINE_APPROACHING, CANCELLED_EVENT). Phase 9 notification severity filtering must handle all 7 alert types.
- `.planning/phases/06-ai-step-extraction-from-clinical-documents/06-CONTEXT.md` — Phase 6 D-02 (separate Claude API calls with circuit breaker per service). Alert text generation follows this same pattern.

### Existing Backend Code (Phase 9 integration points)
- `src/main/java/com/onconavigator/domain/Alert.java` — Entity needs `missing_summary` column. Currently has `deviationDescription` and `suggestedAction` (both TEXT, unconstrained).
- `src/main/java/com/onconavigator/domain/enums/AlertType.java` — 7 alert types (3 original + 4 from Phase 7). Severity filtering preferences reference these.
- `src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java` — `createAlertIfNotDuplicate()` at line 412 creates alerts. Notification dispatch hooks in after `alertRepository.save()`. `buildAlertDescription()` at line 457 is the text generation pipeline — needs to also produce `missing_summary`.
- `src/main/java/com/onconavigator/activity/AlertGenerationActivityImpl.java` — Standalone alert creation activity. Also needs `missing_summary` + notification dispatch.
- `src/main/java/com/onconavigator/ai/service/AlertGenerationAiService.java` — Claude-powered alert text generation. `AlertText` record returns `deviationDescription` and `suggestedAction` — may need a third field for `missingSummary`.
- `src/main/java/com/onconavigator/ai/model/AlertText.java` — Record with `deviationDescription` and `suggestedAction`. Phase 9 may add `missingSummary`.
- `src/main/java/com/onconavigator/web/dto/AlertResponse.java` — Response DTO needs `missingSummary` field for frontend.
- `src/main/java/com/onconavigator/security/EncryptionConverter.java` — AES-GCM JPA attribute converter for PHI fields. Used on `notification_log.rendered_content`.

### Existing Frontend Code
- `frontend/src/features/patients/types.ts` — TypeScript types need `missingSummary` on AlertResponse.
- `frontend/src/routes/patients/$patientId.tsx` — Patient detail page; alert display may show both `missingSummary` (compact) and `deviationDescription` (expanded).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `PathwayEvaluationActivityImpl.createAlertIfNotDuplicate()` — alert creation with dedup. Phase 9 hooks notification dispatch after `alertRepository.save()`.
- `PathwayEvaluationActivityImpl.buildAlertDescription()` — template-first + Claude fallback pipeline. Phase 9 extends to also produce `missing_summary`.
- `AlertGenerationAiService.generateAlertDescription()` — Claude API call with structured response parsing (DESCRIPTION: / SUGGESTED_ACTION: format). May need a third section for MISSING_SUMMARY.
- `EncryptionConverter` — JPA `@Convert` for AES-GCM column encryption. Reuse on `notification_log` PHI columns.
- Temporal workflow patterns — existing `PatientPathwayWorkflow` demonstrates long-running durable workflows. Digest batching workflow follows the same registration and worker patterns.

### Established Patterns
- Flyway versioned SQL migrations for schema changes (ALTER TABLE for new columns, new tables)
- Hibernate Envers `@Audited` on all ePHI entities — `notification_log` inherits this
- `@CircuitBreaker` on Claude calls — existing pattern in `AlertGenerationAiService`
- `AlertText` record as structured return type from AI service
- `AlertResponse` DTO maps entity fields for frontend consumption
- Temporal `temporal-spring-boot-starter` auto-discovers `@WorkflowImpl`/`@ActivityImpl` beans

### Integration Points
- `Alert` entity → add `missingSummary` (String, TEXT, ≤150 chars enforced at service level)
- `Alert.suggestedAction` → 150-char service-level constraint
- `alerts` table → ALTER TABLE ADD COLUMN `missing_summary`, UPDATE existing rows
- New `notification_preferences` table (user_id FK, channel enum, severity filter, quiet hours, digest interval, enabled flag)
- New `notification_log` table (alert_id FK, user_id FK, channel, rendered_content encrypted, sent_at, status)
- New `NotificationService` interface + `LoggingNotificationService` implementation
- New `NotificationChannel` enum (TEAMS, EMAIL)
- New Temporal workflow for digest batching
- `PathwayEvaluationActivityImpl.createAlertIfNotDuplicate()` → call `NotificationService.dispatch()` after save
- `AlertGenerationActivityImpl.generateAlert()` → call `NotificationService.dispatch()` after save
- `AlertResponse` DTO → add `missingSummary` field
- Frontend types → add `missingSummary` to alert types

</code_context>

<specifics>
## Specific Ideas

- The oncologist's PW-ALL-007 response explicitly defines the two-part format: "1) What is missing and 2) a suggested action in no more than 150 characters." The `missing_summary` IS part 1, `suggested_action` IS part 2.
- The oncologist's PW-ALL-004 end-state vision is clear: admin = dashboard only, nurses = Teams/email only. Phase 9 builds the infrastructure; future phases activate real channels. The log-only implementation lets the team verify notification content and routing logic without external dependencies.
- Rich notification payload (patient name, MRN, step, severity, two-part alert, dashboard link) means the nurse gets everything they need in the notification itself. The dashboard link is for when they need to take action (resolve, add notes).
- Stored rendered payloads in `notification_log` serve dual purpose: HIPAA audit trail (who was notified about what PHI, when) and debugging (verify content before activating real channels).
- Temporal digest workflow is the natural fit — the project is all-in on Temporal for anything that needs durable scheduled execution. Per-user digest intervals mean the workflow handles varying schedules gracefully.
- The 150-char cap on both parts ensures notifications are scannable on mobile Teams/email without scrolling. This constraint should be enforced at the service layer (validation before persist), not at the DB level, per the success criteria.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 09-alert-format-notification-foundation*
*Context gathered: 2026-05-05*
