# Phase 9: Alert Format + Notification Foundation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-05
**Phase:** 09-alert-format-notification-foundation
**Areas discussed:** Two-part alert model, Notification channels, Notification content, User preferences

---

## Two-Part Alert Model

### Q1: How should missing_summary relate to existing deviationDescription?

| Option | Description | Selected |
|--------|-------------|----------|
| Replace deviationDescription | Rename deviationDescription to missing_summary. Same concept, clearer name. | |
| Add alongside it | Keep deviationDescription as detailed internal field. Add missing_summary as short notification-friendly summary. | ✓ |
| You decide | Claude picks whichever maps cleanest. | |

**User's choice:** Add alongside it
**Notes:** Three-field model: deviation_description (detailed, internal), missing_summary (short, for notifications), suggested_action (150 char enforced).

### Q2: Should missing_summary also have a character limit?

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, cap at 150 chars | Same 150-char limit as suggested_action. Both parts concise and fit in a single notification line. | ✓ |
| Yes, but longer (250) | Allow more room for clinical context in the "what's missing" part. | |
| No limit | Free-text; truncation at rendering layer. | |

**User's choice:** Yes, cap at 150 chars
**Notes:** Symmetrical constraint — both parts of the two-part format are capped at 150 characters.

### Q3: How to handle existing template alertText/suggestedAction exceeding 150 chars?

| Option | Description | Selected |
|--------|-------------|----------|
| Truncate on migration | Flyway migration truncates existing data and generates missing_summary from deviationDescription. | ✓ |
| Rewrite templates | Update V6 seed template JSONB to have proper two-part text within limits. | |
| Enforce on new only | Don't touch existing data; constraint applies to new alerts only. | |

**User's choice:** Truncate on migration
**Notes:** None.

### Q4: Who populates missing_summary for new alerts?

| Option | Description | Selected |
|--------|-------------|----------|
| Same pipeline, two outputs | Alert generation pipeline produces both missing_summary and deviationDescription. | |
| Auto-derive from description | Only deviationDescription authored; missing_summary auto-derived. | |
| You decide | Claude picks approach that integrates cleanest with existing pipeline. | ✓ |

**User's choice:** You decide
**Notes:** Claude has discretion on how missing_summary integrates with buildAlertDescription pipeline.

---

## Notification Channels

### Q1: How far should notification infrastructure go in Phase 9?

| Option | Description | Selected |
|--------|-------------|----------|
| Interface + log-only | NotificationService interface, LoggingNotificationService impl. Real connectors deferred. | ✓ |
| Interface + Teams webhook stub | Log-only plus a real Teams incoming webhook implementation. | |
| Full Teams + email | Build both real connectors from day one. | |

**User's choice:** Interface + log-only (Recommended)
**Notes:** Matches success criteria exactly. Real connectors added in future phase.

### Q2: When should notifications fire?

| Option | Description | Selected |
|--------|-------------|----------|
| Immediate on creation | Each new alert triggers immediate notification dispatch. | ✓ |
| Event-driven, async | Alert creation publishes domain event; listener dispatches asynchronously. | |
| You decide | Claude picks dispatch approach. | |

**User's choice:** Immediate on creation
**Notes:** Direct call from alert creation path. Simple for log-only since no rate-limiting concern.

### Q3: Should IN_APP (dashboard) be a notification channel in preferences?

| Option | Description | Selected |
|--------|-------------|----------|
| Dashboard always-on | All users always see alerts on dashboard. Preferences control external channels only. | ✓ |
| IN_APP as a channel | Dashboard alerts are a preference like any other. Users can disable them. | |
| You decide | Claude picks based on oncologist's end-state vision. | |

**User's choice:** Dashboard always-on
**Notes:** No risk of user accidentally disabling their only alert source.

---

## Notification Content

### Q1: What information should a notification contain beyond the two-part alert?

| Option | Description | Selected |
|--------|-------------|----------|
| Patient name + MRN | Primary identifiers for the nurse. | ✓ |
| Pathway step name | Which specific step is in deviation. | ✓ |
| Dashboard link | Deep link to patient's pathway view. | ✓ |
| Alert severity/type | Severity label and/or alert type for prioritization. | ✓ |

**User's choice:** All four (multiSelect)
**Notes:** Rich notification payload — nurse gets everything needed without opening the dashboard.

### Q2: Should notification content be stored or assembled on-the-fly?

| Option | Description | Selected |
|--------|-------------|----------|
| Assemble on dispatch | Format from Alert + Patient data at dispatch time. | |
| Store rendered payload | notification_log table stores rendered message for audit and debugging. | ✓ |
| Both — assemble + log | Assemble dynamically, store copy after dispatch. | |

**User's choice:** Store rendered payload
**Notes:** Audit trail and debugging for delivery issues.

### Q3: Should notification_log follow same encryption pattern as other PHI tables?

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, encrypt PHI columns | EncryptionConverter (AES-GCM) on rendered_content. @Audited for revision trail. | ✓ |
| Encrypt entire payload | Single encrypted JSONB column for whole notification payload. | |
| You decide | Claude picks encryption approach consistent with existing patterns. | |

**User's choice:** Yes, encrypt PHI columns
**Notes:** Consistent with project HIPAA posture.

---

## User Preferences

### Q1: What should users be able to configure in notification preferences?

| Option | Description | Selected |
|--------|-------------|----------|
| Channel selection | Which channels to receive notifications on (Teams, email, both). | ✓ |
| Severity filter | Only notify for certain severities/alert types. | ✓ |
| Quiet hours | Hold notifications during configured window. | ✓ |
| Digest batching | Batch alerts into periodic summary instead of per-alert. | ✓ |

**User's choice:** All four (multiSelect)
**Notes:** Full preference model.

### Q2: Should all four preferences be built or just schema-prepped?

| Option | Description | Selected |
|--------|-------------|----------|
| Schema all, logic channel-only | Table has all columns; only channel logic implemented now. | |
| Build all four | Full implementation of all four preference types. | ✓ |
| Channel only, add rest later | Only channel columns in Phase 9. | |

**User's choice:** Build all four
**Notes:** Full implementation of severity filtering, quiet hours hold-and-release, and digest batching.

### Q3: How should digest batching be delivered?

| Option | Description | Selected |
|--------|-------------|----------|
| Temporal scheduled workflow | Durable workflow per user's digest interval. Consistent with all-in-on-Temporal pattern. | ✓ |
| Spring @Scheduled cron | Spring scheduled task at fixed interval. Simpler but not durable. | |
| You decide | Claude picks approach. | |

**User's choice:** Temporal scheduled workflow
**Notes:** Consistent with project architecture. Handles per-user varying schedules.

### Q4: Who manages notification preferences?

| Option | Description | Selected |
|--------|-------------|----------|
| Admin sets for users | Admin configures preferences per user via dashboard. | |
| Users self-service | Each user configures own preferences via settings page. | |
| Admin defaults + user override | Admin sets org defaults; users can override their own. | ✓ |

**User's choice:** Admin defaults + user override
**Notes:** Balances central control with user flexibility.

---

## Claude's Discretion

- How `missing_summary` is populated in the alert generation pipeline (D-04)
- `notification_preferences` table schema design
- `notification_log` table schema and PHI encryption column choices
- `NotificationService` interface method signatures and dispatch routing
- Severity filter implementation (array column vs join table)
- Quiet hours hold-and-release mechanism
- Digest workflow design (per-user instance vs shared sweep)
- Admin defaults storage and user override merge logic
- Flyway migration structure
- Dashboard deep link URL format

## Deferred Ideas

None — discussion stayed within phase scope
