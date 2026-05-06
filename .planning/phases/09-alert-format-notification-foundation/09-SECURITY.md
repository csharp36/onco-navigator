---
phase: 09-alert-format-notification-foundation
plans: [09-01, 09-02, 09-03, 09-04]
asvs_level: 1
auditor: gsd-security-auditor
completed: 2026-05-05
threats_open: 0
---

# Phase 9 Security Audit Report

**Phase:** 09 — Alert Format and Notification Foundation
**Plans Audited:** 09-01, 09-02, 09-03, 09-04
**Threats Closed:** 14/14
**Threats Open:** 0/14
**ASVS Level:** 1
**Verdict:** SECURED

---

## Threat Verification

| Threat ID | Category | Disposition | Status | Evidence |
|-----------|----------|-------------|--------|----------|
| T-09-01 | Information Disclosure | mitigate | CLOSED | NotificationLog.java:56-58 — `@Convert(converter = EncryptionConverter.class)` on `renderedContent` field; column definition is `bytea`. EncryptionConverter.java implements AES-256-GCM with per-encryption random IV (line 94-96). |
| T-09-02 | Information Disclosure | mitigate | CLOSED | NotificationPendingQueue.java:55-57 — `@Convert(converter = EncryptionConverter.class)` on `renderedContentEncrypted` field; column definition is `bytea`. Same AES-GCM converter as T-09-01. V22__notification_preferences.sql:43 — column declared `BYTEA NOT NULL`. |
| T-09-03 | Tampering | accept | CLOSED | Accepted risk documented below. V21 backfill is one-time and idempotent; original `deviation_description` column preserved; `missing_summary` populated as first 150 chars. |
| T-09-04 | Information Disclosure | accept | CLOSED | Accepted risk documented below. AlertResponse.java:36 — `missingSummary` field present; AlertResponse Javadoc:16-17 — "Transmitted over TLS to authenticated JWT holders only." SecurityConfig.java:76 — `/api/**` requires authentication. |
| T-09-05 | Information Disclosure | mitigate | CLOSED | LoggingNotificationService.java lines 65-66, 90-91, 115-116, 124-125, 130-131: all log statements contain only `alertId` (UUID), `userId` (UUID), `channel`, and `holdUntil`. `patientName` and `patientMrn` parameters are passed to `dispatchForChannel` and used only inside `NotificationPayload` construction (line 98-99) — they appear in no log call. |
| T-09-06 | Information Disclosure | accept | CLOSED | Accepted risk documented below. `NotificationPayload` is a Java record — not serializable by default, not written to any stream. `NotificationService.dispatchForAlert` receives PHI as method params; `renderedContent` (the output of `payload.render()`) is passed directly to `notificationLogRepository.save()` via EncryptionConverter. No heap serialization path exists. |
| T-09-07 | Tampering | mitigate | CLOSED | Dual-layer enforcement verified: (1) AlertGenerationAiService.java:133-139 — cap at AI parsing layer for both `missingSummary` and `suggestedAction` before AlertText is returned. (2) PathwayEvaluationActivityImpl.java:481-482 and AlertGenerationActivityImpl.java:74-75 — `cap150()` called before `alert.setSuggestedAction()` and `alert.setMissingSummary()` at the activity layer. AlertGenerationAiServiceTest.java:275-295 and 297-317 — explicit tests for 150-char truncation on both fields. |
| T-09-08 | Elevation of Privilege | mitigate | CLOSED | NotificationPreferenceController.java:45 and 61 — `UUID userId = UUID.fromString(jwt.getToken().getSubject())` extracts userId from JWT subject in both GET and PUT handlers. Line 63 — `preference.setUserId(userId)` overwrites any userId in the request body. Line 64 — `preference.setAdminDefault(false)` prevents escalation to admin defaults. Admin endpoints (lines 73-74, 83-84) guarded by `@PreAuthorize("hasRole('ADMIN')")`. |
| T-09-09 | Information Disclosure | mitigate | CLOSED | PHI does not enter Temporal event history. Temporal activity input for `PathwayEvaluationActivity.evaluate()` is only `UUID patientId` (PatientPathwayWorkflowImpl.java:118). PHI (`patient.getFirstName()`, `patient.getMrn()`) is loaded inside the activity body (PathwayEvaluationActivityImpl.java:380-381, 488-489) and passed only to `notificationService.dispatchForAlert()` — a Spring bean method call, not a Temporal activity parameter. `DigestDispatchActivity.drainPendingQueue()` takes no arguments (DigestDispatchActivity.java). |
| T-09-10 | Information Disclosure | mitigate | CLOSED | DigestDispatchActivityImpl.java:88-94 — decrypted `renderedContentEncrypted` passed to `notificationService.dispatchFromQueue()` which re-encrypts on save via EncryptionConverter when `notificationLogRepository.save()` is called. Activity log statements at lines 73, 81-82, 100-101 log only `userId`, `userBatch.size()`, item counts, `alertId`, `channel`, and `holdType` — no PHI content. |
| T-09-11 | Denial of Service | mitigate | CLOSED | DigestScheduleRegistrar.java:74 — `catch (ScheduleAlreadyRunningException e)` handles restart case. Line 65 — `ScheduleOverlapPolicy.SCHEDULE_OVERLAP_POLICY_SKIP` prevents concurrent execution overlap. |
| T-09-12 | Information Disclosure | accept | CLOSED | Accepted risk documented below. DigestDispatchWorkflowImpl.java:36 — `digestActivity.drainPendingQueue()` is called with no arguments. DigestDispatchActivity.java — `void drainPendingQueue()` method signature has no parameters. No PHI enters Temporal workflow or activity event history for this workflow. |
| T-09-13 | Tampering | mitigate | CLOSED | AlertGenerationAiServiceTest.java:275-317 — two explicit tests (`generateAlertDescription_missingSummaryExceeds150_truncated` and `generateAlertDescription_suggestedActionExceeds150_truncated`) construct 200-char inputs and assert `result.missingSummary().length()` and `result.suggestedAction().length()` equal exactly 150. |
| T-09-14 | Information Disclosure | accept | CLOSED | Accepted risk documented below. LoggingNotificationServiceTest.java:106 — uses literal `"Test Patient"` and `"MRN001"`. DigestDispatchActivityImplTest.java:64 — uses `"Rendered content for " + userId` (no real patient data). AlertGenerationAiServiceTest.java — uses clinical pathway terms (cancer types, step names) with no patient identifiers. |

---

## Accepted Risks Log

| Threat ID | Category | Rationale |
|-----------|----------|-----------|
| T-09-03 | Tampering | V21 migration backfill truncates `suggested_action` values to 150 chars. This is intentional data transformation per design decision D-03. The original `deviation_description` column is preserved and the migration is a one-time, idempotent Flyway versioned migration. Risk accepted by design. |
| T-09-04 | Information Disclosure | `AlertResponse.missingSummary` is clinical process text (pathway step names, deviation types) — not patient-identifying information. No patient name, MRN, DOB, or SSN is included. The field is transmitted over TLS to authenticated JWT holders only (`/api/**` requires authentication per SecurityConfig). |
| T-09-06 | Information Disclosure | `NotificationPayload` is a transient in-memory value object. It is not serialized to disk, written to any stream, passed to Temporal (which would record it in event history), or logged. Its lifetime is limited to the stack frame of `dispatchForChannel()`. The rendered String it produces is encrypted before persistence via EncryptionConverter. |
| T-09-12 | Information Disclosure | `DigestDispatchWorkflow.runDigestPass()` is invoked by Temporal schedule with no parameters. `DigestDispatchActivity.drainPendingQueue()` takes no arguments. All PHI access occurs inside the activity body via JPA (which decrypts via EncryptionConverter at load time). No PHI is present in any Temporal workflow or activity event history for this workflow path. SEC-06 satisfied. |

---

## Threat Flags from SUMMARY.md

| Plan | Flag | Mapping | Classification |
|------|------|---------|----------------|
| 09-02 | RESULTS_NOT_READY alert path — a second inline alert creation path identified beyond the plan spec | Maps to T-09-09 (PHI in notification dispatch) and T-09-07 (cap150 enforcement) — executor added `cap150()` and `notificationService.dispatchForAlert()` to this path (PathwayEvaluationActivityImpl.java:373-374, 379-381) | Informational — covered by existing threat mitigations |
| 09-03 | ScheduleOverlapPolicy imported from `io.temporal.api.enums.v1` (protobuf), not `io.temporal.client.schedules` | Maps to T-09-11 (duplicate schedule DoS) — policy is present and functional regardless of package origin | Informational |
| 09-04 | Quiet hours tests use deterministic 0-24 ranges to avoid time-dependent flakiness | Maps to T-09-13 (test coverage for cap enforcement) — test strategy detail, not a new attack surface | Informational |

No unregistered flags found. All SUMMARY.md threat flags map to existing threat IDs.

---

## Implementation Notes (Audit Findings)

**T-09-05 — PHI logging verification scope:** The non-PHI guarantee for `patientName`/`patientMrn` was verified in `LoggingNotificationService` and `DigestDispatchActivityImpl`. The `AlertGenerationActivityImpl.java:87` log statement (`ALERT_GENERATED: patient={} step={} type={}`) uses `patientId` (UUID), `pathwayStepName`, and `alertTypeStr` — no PHI. The `PathwayEvaluationActivityImpl` log statement at line 384 uses `patient.getId()` (UUID) only. PHI-safe logging is confirmed across all Phase 9 components.

**T-09-08 — GET endpoint authentication:** The non-admin GET endpoint (`getMyPreferences`) does not carry `@PreAuthorize` but is protected by (1) `JwtAuthenticationToken jwt` method parameter — Spring MVC will fail the request with 401 if no valid JWT is present — and (2) `SecurityConfig` applying `.requestMatchers("/api/**").authenticated()` globally. User A cannot read user B's preferences because the userId is extracted from the JWT subject and passed to `preferenceService.getUserPreferences(userId)`.

**T-09-01/T-09-02 — Encryption key source:** `EncryptionConverter` retrieves the `SecretKey` from the Spring `ApplicationContext` via `ApplicationContextProvider.getBean(SecretKey.class)` at conversion time. The key management itself (Jasypt/Secrets Manager) is outside Phase 9 scope but was established in prior phases and inherited here.
