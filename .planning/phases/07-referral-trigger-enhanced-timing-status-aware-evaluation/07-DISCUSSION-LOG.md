# Phase 7: Referral Trigger + Enhanced Timing + Status-Aware Evaluation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-05
**Phase:** 07-referral-trigger-enhanced-timing-status-aware-evaluation
**Areas discussed:** Referral clock mechanism, Status-aware evaluation, Expected dates & results-before-visit, Scheduling confirmation

---

## Referral Clock Mechanism

### Q1: How should referral_received_at relate to the existing anchor model?

| Option | Description | Selected |
|--------|-------------|----------|
| Replace diagnosis date | referral_received_at becomes the new root anchor for ALL root steps | |
| Per-step anchor choice | Each step declares its anchor type (DIAGNOSIS_DATE or REFERRAL_DATE) | |
| Referral = first prerequisite | Model referral as a pathway step auto-completed on PDF upload | |

**User's choice:** User rejected the options — said to go with what the oncologist said: "clock starts from when the referral PDF is received."
**Notes:** User deferred to oncologist's PW-CR-001 clinical decision rather than choosing an implementation framing.

### Q2: How does referral_received_at get set?

| Option | Description | Selected |
|--------|-------------|----------|
| Auto from PDF upload | Auto-set when REFERRAL_LETTER document linked to patient | |
| Auto + manual fallback | Same auto-set, plus nurse can manually set date for edge cases | ✓ |
| Manual only | Nurse enters referral received date during data entry | |

**User's choice:** Auto + manual fallback

### Q3: Does referral date replace diagnosis date as root anchor?

| Option | Description | Selected |
|--------|-------------|----------|
| Referral replaces diagnosis | Root steps always anchor to referral_received_at | |
| Referral is default, diagnosis is fallback | Use referral when set, fall back to diagnosisDate when not | ✓ |
| Per-step choice | Each step declares which anchor to use | |

**User's choice:** Referral is default, diagnosis is fallback

---

## Status-Aware Evaluation

### Q1: Should SCHEDULED/PENDING suppress MISSING_EVENT?

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, suppress MISSING | SCHEDULED/PENDING means step isn't missing | |
| Suppress + track deadline | Same suppression, but track expected_completion_date and fire DELAYED if it passes | ✓ |
| No suppression | Keep firing MISSING_EVENT regardless of status | |

**User's choice:** Suppress + track deadline

### Q2: What happens when a care event is CANCELLED?

| Option | Description | Selected |
|--------|-------------|----------|
| Immediate corrective alert | CANCELLED triggers new alert type immediately, nurse must act | ✓ |
| Revert to MISSING after window | Treated as if event never existed, MISSING fires at window expiry | |
| Immediate + auto-reopen step | Same immediate alert plus auto-revert step state | |

**User's choice:** Immediate corrective alert

### Q3: DEADLINE_APPROACHING: all steps or only those with SCHEDULED/PENDING?

| Option | Description | Selected |
|--------|-------------|----------|
| Only with SCHEDULED/PENDING | 48hr warning only when something is scheduled | |
| All steps approaching deadline | Fire for any step where window is about to expire | ✓ |
| Configurable per step | Boolean on step to enable/disable deadline alerts | |

**User's choice:** All steps approaching deadline

---

## Expected Dates & Results-Before-Visit

### Q1: Where does expected_completion_date come from?

| Option | Description | Selected |
|--------|-------------|----------|
| Manual entry by nurse | Nurse enters expected-by date from facility info | |
| Manual + AI suggestion | Nurse enters manually; AI can suggest from document context | ✓ |
| Computed from step window | Auto-calculated as anchor_date + windowDays, nurse can override | |

**User's choice:** Manual + AI suggestion

### Q2: How should RESULTS_NOT_READY detection work?

| Option | Description | Selected |
|--------|-------------|----------|
| DAG edges define it | Only check DAG prerequisite pairs | |
| Any pending result + any upcoming visit | Broad patient-level matching of any pending result vs any visit | ✓ |
| Nurse links them manually | Explicit result-to-visit linkage by nurse | |

**User's choice:** Any pending result + any upcoming visit

### Q3: How far ahead should results-before-visit look?

| Option | Description | Selected |
|--------|-------------|----------|
| 7 days ahead | Focused, actionable window | |
| 14 days ahead | Two-week lookahead for more lead time | ✓ |
| Match the step window | Adapt per step's windowDays | |

**User's choice:** 14 days ahead

---

## Scheduling Confirmation

### Q1: What constitutes scheduling confirmed?

| Option | Description | Selected |
|--------|-------------|----------|
| Boolean flip by nurse | Nurse checks a confirmed checkbox when they get confirmation from facility | ✓ |
| Status transition | PENDING to SCHEDULED transition is the confirmation | |
| Separate confirmation field | Dedicated boolean + timestamp, distinct from event status | |

**User's choice:** Boolean flip by nurse

### Q2: When does the 7-day SCHEDULING_UNCONFIRMED clock start?

| Option | Description | Selected |
|--------|-------------|----------|
| From care event creation | 7 days from when the event was logged in the system | |
| From referral_received_at | 7 days from referral receipt (matches oncologist guidance) | ✓ |
| From event date entry | 7 days from the eventDate recorded on the care event | |

**User's choice:** From referral_received_at

### Q3: For subsequent procedures, when does their confirmation clock start?

| Option | Description | Selected |
|--------|-------------|----------|
| From care event creation | Each event's 7-day clock from when it was logged | |
| Only initial referral | SCHEDULING_UNCONFIRMED only for initial referral | |
| From event date | 7-day clock from eventDate (when order was placed) | ✓ |

**User's choice:** From event date

### Q4: Should care events track external facility name?

| Option | Description | Selected |
|--------|-------------|----------|
| Yes, optional text field | external_facility_name on care events for specific alert actions | ✓ |
| No, not needed for V1 | Keep care events simple, reduce data entry burden | |

**User's choice:** Yes, optional text field

---

## Claude's Discretion

- DAG anchor resolution implementation (referral_received_at fallback logic in PathwayEvaluationActivityImpl)
- New AlertType enum naming and PostgreSQL migration strategy
- Alert description templates for four new alert types
- Query strategy for broad RESULTS_NOT_READY matching (performance considerations)
- CareEvent form UI changes for new fields
- 48-hour DEADLINE_APPROACHING interaction with Temporal timer interval
- SCHEDULING_UNCONFIRMED scope (PENDING only vs PENDING+SCHEDULED without confirmed flag)
- Flyway migration structure for new columns and enum values

## Deferred Ideas

None — discussion stayed within phase scope
