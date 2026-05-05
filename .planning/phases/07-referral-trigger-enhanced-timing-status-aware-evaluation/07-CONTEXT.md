# Phase 7: Referral Trigger + Enhanced Timing + Status-Aware Evaluation - Context

**Gathered:** 2026-05-05
**Status:** Ready for planning

<domain>
## Phase Boundary

The pathway clock starts from referral PDF receipt. The evaluation engine understands event statuses (Scheduled, Pending, Cancelled) and generates new alert types for results-before-visit, scheduling confirmation, and deadline escalation.

This phase delivers: `referral_received_at` timestamp on the Patient entity (auto-set from referral PDF upload + manual fallback), referral date as default root anchor replacing diagnosis date, `expected_completion_date` / `scheduling_confirmed` / `external_facility_name` fields on CareEvent, status-aware evaluation logic (SCHEDULED/PENDING suppress MISSING_EVENT, CANCELLED triggers immediate alert, past-due SCHEDULED triggers DELAYED), four new alert types (RESULTS_NOT_READY, SCHEDULING_UNCONFIRMED, DEADLINE_APPROACHING, CANCELLED_EVENT), 48-hour deadline proximity warnings for all steps, and 14-day lookahead for results-before-visit detection.

This phase does NOT build template inheritance (Phase 8), alert format changes (Phase 9), or batch notification infrastructure.

</domain>

<decisions>
## Implementation Decisions

### Referral Clock Mechanism
- **D-01:** **Clock starts from referral PDF receipt** — the oncologist's PW-CR-001 answer is the governing decision. `referral_received_at` is the primary time anchor for patient pathway evaluation.
- **D-02:** **Auto-set from PDF upload + manual fallback** — when a document classified as REFERRAL_LETTER is linked to a patient, the system auto-sets `referral_received_at` to the upload timestamp. The nurse can also manually set/override this date (for referrals received before the system existed or via unscanned fax).
- **D-03:** **Referral date is default root anchor, diagnosis date is fallback** — root steps (no prerequisite edges) anchor to `referral_received_at` when it exists. If no referral date is set (patient created without a referral PDF), the system falls back to `diagnosisDate`. Existing patients without `referral_received_at` continue using diagnosis date unchanged.

### Status-Aware Evaluation
- **D-04:** **SCHEDULED/PENDING suppresses MISSING_EVENT + tracks deadline** — a SCHEDULED or PENDING care event means the step isn't "missing." The system suppresses MISSING_EVENT and instead tracks `expected_completion_date`. If the expected date passes without COMPLETED, the system fires DELAYED_EVENT instead.
- **D-05:** **CANCELLED triggers immediate corrective alert** — a CANCELLED event fires a new alert type immediately (not waiting for window expiry). The alert communicates "X was cancelled — reschedule or update pathway." Nurse must act.
- **D-06:** **DEADLINE_APPROACHING fires for ALL steps** — 48-hour warning before the step's window expires, regardless of whether a SCHEDULED/PENDING event exists. Proactive heads-up to the nurse before MISSING_EVENT would fire.

### Expected Dates & Results-Before-Visit
- **D-07:** **Manual entry + AI suggestion for expected_completion_date** — when recording a SCHEDULED or PENDING event, the nurse enters the expected completion date. When AI extracts steps from documents, it can suggest expected dates from clinical note context (e.g., "results expected in 2 weeks").
- **D-08:** **RESULTS_NOT_READY uses broad patient-level matching** — if ANY pending/scheduled result event for a patient has `expected_completion_date` after ANY upcoming visit's scheduled date within a 14-day lookahead window, fire RESULTS_NOT_READY. Broader catch over DAG-edge-specific matching.
- **D-09:** **14-day lookahead for results-before-visit** — the system checks upcoming visits within the next 14 days against pending results. Beyond 14 days, results timing is not yet actionable.

### Scheduling Confirmation
- **D-10:** **Boolean flip by nurse** — `scheduling_confirmed` is a boolean checkbox on the care event. The nurse checks it when they get verbal/written confirmation from the outside facility that the procedure is scheduled. Simple, matches current manual workflow.
- **D-11:** **Initial referral: 7-day clock from referral_received_at** — for the initial scheduling after referral receipt, the SCHEDULING_UNCONFIRMED alert fires if not confirmed within 7 days of `referral_received_at`.
- **D-12:** **Subsequent procedures: 7-day clock from eventDate** — for subsequent outside procedures, the 7-day confirmation clock starts from the care event's `eventDate` (when the order was placed). Each event has its own confirmation deadline.
- **D-13:** **External facility name tracked** — `external_facility_name` is an optional text field on care events. Makes alert actions more specific (e.g., "Contact Memorial Hospital radiology about scheduling confirmation").

### Claude's Discretion
- How `referral_received_at` integrates with the DAG anchor resolution code (implementation of the fallback logic in PathwayEvaluationActivityImpl)
- New AlertType enum values: naming convention and PostgreSQL migration (ALTER TYPE)
- Alert description templates for the four new alert types
- How the "any pending result vs any upcoming visit" matching works in the evaluation loop (query strategy, performance)
- CareEvent form UI changes to accommodate new fields (expected_completion_date, scheduling_confirmed, external_facility_name)
- How the 48-hour DEADLINE_APPROACHING window interacts with the evaluation cycle frequency (Temporal timer interval)
- Whether SCHEDULING_UNCONFIRMED checks only PENDING events or also SCHEDULED events without the confirmed flag
- Flyway migration structure for new columns and enum values

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Clinical Context
- `docs/Pathway-Template-Review-Worksheet.md` — Oncologist clinical review. Key decisions: PW-ALL-001 (results-before-visit=PILOT, scheduling confirmations=PILOT, referral tracking=PILOT, 48hr escalation=PILOT), PW-ALL-003 (Scheduled/Pending/Cancelled statuses needed), PW-CR-001 (clock starts from referral PDF receipt).
- `docs/Onco-Navigator AI - V1 Feature Specification v2.md` — Original four event statuses (Scheduled, Completed, Cancelled, Pending), alert scenarios.

### Requirements
- `.planning/REQUIREMENTS.md` — PW-ALL-001, PW-ALL-003, PW-CR-001 requirements mapped to Phase 7.

### Prior Phase Context
- `.planning/phases/05-per-patient-pathway-dag/05-CONTEXT.md` — Phase 5 D-11 (time windows anchored to prerequisites, root steps use diagnosis date — Phase 7 changes root anchor to referral date with diagnosis fallback), D-06 (pathwayStepsChanged signal), D-12 (newly added steps default to root).
- `.planning/phases/06-ai-step-extraction-from-clinical-documents/06-CONTEXT.md` — Phase 6 D-01 (extraction triggered during document upload — same pipeline where referral detection hooks in), D-02 (separate Claude call per service).

### Existing Backend Code (Phase 7 integration points)
- `src/main/java/com/onconavigator/domain/Patient.java` — Needs `referral_received_at` field. Currently only has `diagnosisDate` as a date anchor.
- `src/main/java/com/onconavigator/domain/CareEvent.java` — Needs `expected_completion_date`, `scheduling_confirmed`, `external_facility_name` fields. Currently has `eventDate`, `status`, `notes`.
- `src/main/java/com/onconavigator/domain/enums/AlertType.java` — Currently has MISSING_EVENT, DELAYED_EVENT, OUT_OF_ORDER. Phase 7 adds RESULTS_NOT_READY, SCHEDULING_UNCONFIRMED, DEADLINE_APPROACHING, CANCELLED_EVENT.
- `src/main/java/com/onconavigator/domain/enums/CareEventStatus.java` — Already has SCHEDULED, COMPLETED, CANCELLED, PENDING. No changes needed.
- `src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java` — Core evaluation engine. Phase 7 rewrites the evaluation branches to be status-aware: suppress MISSING when SCHEDULED/PENDING, add CANCELLED immediate alert, add 48hr DEADLINE_APPROACHING, add RESULTS_NOT_READY cross-event check.
- `src/main/java/com/onconavigator/service/DocumentProcessingService.java` — Pipeline at ~line 97. Phase 7 hooks in after classification: when documentType is REFERRAL_LETTER and patient is linked, auto-set `referral_received_at`.

### Existing Frontend Code
- `frontend/src/features/patients/types.ts` — TypeScript types need new CareEvent fields.
- `frontend/src/routes/patients/$patientId.tsx` — Patient detail page; care event forms need new fields.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `PathwayEvaluationActivityImpl.evaluate()` — existing DAG evaluation with topological sort, anchor date resolution, and dedup. Phase 7 extends the per-step evaluation branches, not replaces the DAG traversal.
- `DocumentProcessingService.processUpload()` — existing pipeline with document classification. Referral detection hooks in at the classification result check.
- `CareEventStatus` enum — SCHEDULED, PENDING, CANCELLED already defined. No new enum values needed for event statuses.
- Alert dedup via `existsByPatientIdAndPathwayStepNameAndStatus(OPEN)` — carries forward for new alert types.
- `buildAlertDescription()` — existing template-first + Claude fallback pattern. New alert types follow the same pattern.

### Established Patterns
- Flyway versioned migrations for schema changes (ALTER TYPE for new AlertType values, ALTER TABLE for new columns)
- Hibernate Envers `@Audited` on all ePHI entities — new Patient/CareEvent fields inherit this
- `@CircuitBreaker` on Claude calls — not directly needed for Phase 7 (no new Claude services), but alert description generation reuses existing pattern
- CareEvent form in frontend uses react-hook-form + Zod — new fields added to existing form schema

### Integration Points
- `Patient` entity → add `referralReceivedAt` (OffsetDateTime, nullable)
- `CareEvent` entity → add `expectedCompletionDate` (LocalDate, nullable), `schedulingConfirmed` (Boolean, default false), `externalFacilityName` (String, nullable)
- `AlertType` PostgreSQL enum → ALTER TYPE ADD VALUE for 4 new types
- `PathwayEvaluationActivityImpl.evaluate()` → rewrite per-step evaluation to check event status and fire status-appropriate alerts
- `DocumentProcessingService` → after classification, if REFERRAL_LETTER + patient linked, set `referral_received_at` on patient
- Frontend care event form → add expected completion date picker, scheduling confirmed checkbox, external facility name input

</code_context>

<specifics>
## Specific Ideas

- The oncologist's PW-ALL-003 additional notes specify distinct corrective actions per status: "If system detects scheduled and pending, it should request an estimated date of when it should be completed and then start monitoring at that time. If an event is cancelled it needs to trigger corrective action." This maps directly to D-04 (track deadline) and D-05 (immediate CANCELLED alert).
- The oncologist's PW-CR-001 answer: "The clock should start once the referral PDF is received. From there, the first visit should happen in less than 7 days." This 7-day target for first visit from referral is the SCHEDULING_UNCONFIRMED window (D-11).
- The broad RESULTS_NOT_READY matching (D-08) is intentionally noisy — it's a safety net. The nurse can dismiss irrelevant alerts. Missing a "wasted visit" where the doctor can't make a decision is worse than an extra alert.
- Existing patients migrated in Phase 5 will have no `referral_received_at` and will naturally fall back to `diagnosisDate` anchoring (D-03). No data migration needed for this fallback.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope

</deferred>

---

*Phase: 07-referral-trigger-enhanced-timing-status-aware-evaluation*
*Context gathered: 2026-05-05*
