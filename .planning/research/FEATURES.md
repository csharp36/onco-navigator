# Feature Research

**Domain:** Oncology care pathway monitoring — nurse navigator tooling
**Researched:** 2026-04-29
**Confidence:** HIGH (core feature set), MEDIUM (competitive positioning), LOW (LLM-for-alerts novelty)

---

## Feature Landscape

### Table Stakes (Users Expect These)

Features nurse navigators expect in any oncology navigation system. Missing these makes the product feel broken before they've given it a chance.

| Feature | Why Expected | Complexity | Notes |
|---------|--------------|------------|-------|
| Prioritized patient worklist | Navigators manage 30–80 patients simultaneously. Without a ranked "who needs attention now" list, they revert to mental models and spreadsheets — the exact problem being solved. | MEDIUM | Sort by: alert severity, days since last contact, time-to-next-milestone. This is the landing page users see every day. |
| Per-patient pathway status view | Users need to see where a specific patient is in their care sequence — what happened, what's next, what's overdue. This is the single-patient drill-down from the worklist. | MEDIUM | Show completed steps (with dates), current step, upcoming steps, any flags. Timeline visualization beats tables. |
| Deviation detection: missing events | If a step hasn't been recorded within its expected time window, the system must surface this. The most common failure mode in manual tracking. | HIGH | Core engine work. Requires time window definitions per pathway step, timer-based evaluation (Temporal.io handles this). |
| Deviation detection: delayed events | Similar to missing but with soft vs. hard deadlines. A biopsy result expected in 5 days that arrives on day 8 is a delay, not a miss. | HIGH | Needs two-tier alerting: "approaching due" vs. "overdue". Time windows vary by step type. |
| Deviation detection: out-of-order events | If surgery is recorded before the pre-op workup is complete, the system must detect the sequence violation. | HIGH | Prerequisite checking. Each pathway step carries a list of required prior steps. |
| Alert queue / active alerts dashboard | A consolidated view of all open alerts across all patients, sortable and filterable. The "inbox" equivalent for deviations. | MEDIUM | Separate from the worklist. The worklist shows all patients; the alert queue shows only patients with open alerts. |
| Alert resolution workflow | Nurse must be able to acknowledge, document action taken, and close an alert. Without this, the system generates noise with no memory of what was done. | MEDIUM | Resolution requires: action taken (free text), who resolved it, timestamp. Feeds audit trail. |
| Patient record CRUD | Add/edit patients (name, MRN, diagnosis date, cancer type, assigned navigator, stage). Manual entry is V1 — quality of this form determines whether staff uses the system. | LOW | Cancer type determines which pathway template activates. MRN is critical for identity. |
| Care event recording | Staff must be able to record that a clinical event occurred (e.g., "biopsy received," "surgery completed") with date, facility, and notes. This is the data the engine runs on. | MEDIUM | Event types are defined per pathway template. Free-form notes field is essential for nuance. |
| Role-based access control | Nurse navigators see their assigned patients. Care coordinators see broader lists. Administrators manage templates and user accounts. Without RBAC, HIPAA compliance is impossible. | MEDIUM | Three roles minimum: nurse_navigator, care_coordinator, admin. Role defines what patient data is visible and what actions are permitted. |
| HIPAA-compliant data handling | ePHI encryption at rest and in transit, no logging of PHI in application logs, BAA-ready architecture. Expected by any healthcare buyer — non-negotiable for pilot sign-off. | HIGH | Encryption at rest (database-level + application-level field encryption for identifiers), TLS everywhere, no PHI in log lines. |
| Immutable audit trail | Every read and write of patient data must be logged with user identity, timestamp, and action. Required for HIPAA Security Rule 45 CFR §164.312(b). Minimum 6-year retention. | HIGH | Append-only log table. Log entries must include: user_id, patient_id, action_type, before/after values, IP address, timestamp. Never delete or update audit rows. |
| Pathway template definitions | System must ship with clinically validated step sequences for breast, lung, and colorectal cancer. Without this, the engine has nothing to evaluate patients against. | HIGH | Templates define: steps, prerequisite steps, expected time window per step, deviation thresholds. These are the "rules" for the state machine. |

---

### Differentiators (Competitive Advantage)

Features that meaningfully separate Onco-Navigator from general care coordination software and from spreadsheets. These are where the product can win.

| Feature | Value Proposition | Complexity | Notes |
|---------|-------------------|------------|-------|
| AI-generated deviation alert descriptions | When a deviation is non-standard or ambiguous (e.g., step completed at a facility not in the expected referral network), canned alert text fails. Claude API generates plain-language descriptions that accurately describe the clinical situation. Competitors use template strings exclusively. | MEDIUM | Template strings for the ~80% of known deviation patterns (predictable, zero cost, fast). Claude API for edge cases where template doesn't fit. Gate Claude calls behind a "non-standard deviation" classifier. |
| AI-suggested corrective actions | Each alert includes a suggested next action for the navigator ("Call radiology at Mercy Hospital to confirm biopsy slides were received"). Reduces cognitive load. | MEDIUM | Actions are seeded from a curated lookup per deviation type. Claude API generates narrative for edge cases. Nurse decides and acts — AI is advisory. |
| Configurable pathway templates (admin UI) | Admin can define new pathway types, add steps, edit time windows, and publish without a code deploy. Competitors hardcode their pathway logic. This enables the engine to generalize to new cancer types and institution-specific variations. | HIGH | Template editor must validate: no circular prerequisites, no orphaned steps, time windows are positive integers. Version pathway templates so historical evaluations remain valid. |
| Prerequisite-aware deviation detection | Not just "step X is late" but "step X is missing because step Y (its prerequisite) was never completed." The alert describes the root cause, not the symptom. | HIGH | Requires DAG (directed acyclic graph) modeling of step dependencies. Most navigation software checks milestone due dates but does not model prerequisite chains. |
| Multi-facility event origin tracking | Events are recorded with the external facility where they occurred (e.g., "biopsy — St. Mary's Pathology"). Enables reports like "which facilities are causing the most delays." | LOW | Add facility field to care event record. Seed facility list per practice. No integration needed in V1 — just a dropdown + free text fallback. |
| Navigator-to-patient assignment with caseload visibility | Admin can see how many active patients each navigator has, balanced across staff. Prevents individual navigators from being overwhelmed invisibly. | LOW | Dashboard widget: navigator name, active patient count, open alert count. Assignment changes logged to audit trail. |
| Pathway progression timeline visualization | Per-patient view showing completed steps on a horizontal timeline, current position, and upcoming steps with their expected windows. Communicates patient state at a glance without reading tables. | MEDIUM | Not a Gantt chart (too complex). A stepped progress bar with event markers and alert flags is sufficient and buildable with React. |
| Plain-language alert descriptions | All alerts use complete sentences that describe the clinical situation in terms a nurse navigator uses, not engineering terminology. ("The surgical pathology report for this patient was due 7 days after surgery. No result has been recorded.") | LOW | This is a content/UX discipline, not a feature per se, but it is a real differentiator. Template strings must be written by a clinician or clinically reviewed. |
| Alert age visibility | Alerts display how long they have been open. Navigators can see which issues are stale vs. new. Enables escalation judgment without needing a separate escalation engine in V1. | LOW | "Open for 3 days" next to each alert card. Sort by alert age is valuable. |

---

### Anti-Features (Commonly Requested, Often Problematic)

Things that seem useful but introduce disproportionate complexity, clinical risk, or scope drift for a V1 proof of concept.

| Feature | Why Requested | Why Problematic | Alternative |
|---------|---------------|-----------------|-------------|
| SMS / push notification alerts | Navigators are not always at their desk; they want alerts to follow them. | V1 is a PoC at one practice. SMS introduces Twilio dependency, phone number management, HIPAA-compliant message content rules (no PHI in SMS bodies), opt-in/opt-out flows, and carrier failures. Each navigator needs to know to check the dashboard as their job. | Dashboard-only in V1. Design the dashboard so checking it is fast (< 30 seconds for a status check). Add SMS in Phase 2 once the alert quality is validated. |
| EMR integration / HL7 FHIR ingestion | Eliminating manual entry is the obvious next step. | Requires API access agreements with each EMR vendor (Epic, Cerner, athena), negotiation timelines of 3–12 months, HL7 FHIR mapping per event type, credential management, and failure handling when the feed goes stale. Premature in V1. | Manual entry with a well-designed form. Validate the pathway engine and alert quality on manually entered data first. |
| Patient-facing portal | Patients want to see their care status. | Adds a new user type, consent/authorization workflows, patient identity verification, and a completely different UX design constraint. Out of scope for a staff-only coordination tool. | Staff-only in V1. Nurse navigators communicate with patients through their existing channels. |
| Autonomous alert escalation (no human approval) | Automatically calling facilities or escalating to physicians without nurse review. | Clinical safety. The nurse navigator is the clinical judgment layer. Autonomous escalation could trigger unnecessary interventions, violate patient trust, and expose the practice to liability. Non-negotiable human-in-the-loop constraint. | AI suggests the corrective action. Nurse executes it. Document the action in the resolution workflow. |
| Real-time streaming / WebSocket-pushed alerts | Dashboard updates without refresh. | Adds infrastructure complexity (WebSocket server, connection management, reconnection logic) for marginal value in V1. Navigators check the dashboard periodically — they don't need sub-second latency. | Polling every 60 seconds is undetectable to users and trivially implementable. Add streaming if user research shows it matters post-launch. |
| Billing and revenue cycle tracking | Practices want to track navigation billing codes (PIN, CPT G-codes). | Completely different domain from care coordination. Billing software has its own compliance requirements, payer contract dependencies, and error recovery models. | Out of scope permanently unless the product pivots. If billing matters, integrate with existing billing software via a report export. |
| Symptom monitoring / PRO collection | Collecting patient-reported outcomes between visits. | Patient-facing feature. Requires patient authentication, reminder scheduling, clinical interpretation of responses, and integration with clinical workflows. The practice's coordination problem is about care pathway tracking, not symptom surveillance. | Not the right problem for this tool. Symptom monitoring tools (Navigating Care, Carevive) exist for this specifically. |
| Native mobile app | Navigators want to check alerts from their phones. | Doubles the frontend maintenance surface (React + iOS/Android) for no functional difference in V1. A responsive web dashboard on a tablet or phone is equivalent for the use case. | Responsive React dashboard renders acceptably on mobile. Test on iPad specifically — common in clinical settings. |
| Complex role hierarchy with custom permissions | "Can we have sub-roles and per-patient permission grants?" | Premature generalization. V1 has three well-understood roles: navigator, coordinator, admin. Custom permission engines take weeks to build and test correctly, and RBAC failures are a HIPAA violation. | Ship three hardcoded roles. Make role assignment an admin action with audit logging. Revisit in Phase 2 if the pilot practice requests custom roles. |
| Predictive analytics / ML-based at-risk scoring | "Which patients are most likely to fall behind?" | Requires enough historical data to train or validate a model — data that doesn't exist until after V1 is deployed and used. Premature ML leads to unreliable predictions that erode trust. | Rule-based deviation detection is deterministic and explainable. Collect historical data in V1. Evaluate predictive features in Phase 3+ when there's a meaningful dataset. |

---

## Feature Dependencies

```
Pathway Template Definitions
    └──required by──> Deviation Detection Engine
                           └──required by──> Alert Generation
                                                 └──required by──> Alert Queue Dashboard
                                                                       └──required by──> Alert Resolution Workflow

Patient Record CRUD
    └──required by──> Care Event Recording
                           └──required by──> Deviation Detection Engine

Role-Based Access Control
    └──required by──> All authenticated views (worklist, patient record, alert queue)
    └──required by──> Audit Trail (must log who did what)

Immutable Audit Trail
    └──required by──> HIPAA compliance
    └──feeds──> Alert Resolution Workflow (resolution actions are audit events)

Care Event Recording
    └──enhances──> Pathway Progression Timeline Visualization

AI Alert Descriptions (Claude API)
    └──enhances──> Alert Queue Dashboard (replaces canned text for edge-case alerts)
    └──enhances──> Alert Resolution Workflow (suggested corrective action)

Multi-Facility Event Origin Tracking
    └──enhances──> Care Event Recording (adds facility field)
    └──enables──> Facility delay reporting (Phase 2)

Configurable Pathway Templates (Admin UI)
    └──enhances──> Pathway Template Definitions (makes them editable without code)
    └──requires──> Template versioning (so live patients aren't broken by template changes)
```

### Dependency Notes

- **Pathway Template Definitions require clinical authorship:** The nurse/physician writes the step sequences and time windows. The admin UI makes them editable, but the initial content must be clinically validated before the engine runs against real patients.
- **Deviation Detection requires Temporal.io timers:** Missing event detection works by scheduling a timer at event-expected-time; if no event recorded before timer fires, alert triggers. This is not checkable at query time alone — requires durable scheduling.
- **RBAC must be in place before any patient data is recorded:** Building RBAC after data entry begins means retroactively deciding what each user should see. Start with RBAC enforced from day one.
- **Alert Resolution documents feed the audit trail:** Resolution actions are not a separate log — they are audit events. The resolution workflow is a UI wrapper around an audit trail write.
- **AI alert descriptions conflict with fully-offline operation:** Claude API calls require network access. If the deployment environment has restricted outbound internet, AI descriptions fail silently or fall back to templates. Design the fallback path before deployment.

---

## MVP Definition

### Launch With (V1)

Minimum viable product for a single-practice proof of concept.

- [x] Pathway template definitions — breast, lung, colorectal cancer (clinical content, not just the engine)
- [x] Patient record creation and management — MRN, name, diagnosis date, cancer type, stage, assigned navigator
- [x] Care event recording — event type, date, facility, notes, recorded-by
- [x] Deviation detection engine — missing events, delayed events, out-of-order events, evaluated against pathway templates
- [x] Alert generation — one alert per deviation, canned text for known patterns, Claude API for edge cases
- [x] Alert queue dashboard — all open alerts, sortable by severity and age, filterable by navigator and cancer type
- [x] Alert resolution workflow — acknowledge, document action, close, with timestamp and user capture
- [x] Prioritized patient worklist — ranked by alert severity and time sensitivity
- [x] Per-patient pathway status view — steps completed, current step, upcoming steps, open alerts
- [x] Role-based access control — nurse_navigator, care_coordinator, admin
- [x] Immutable audit trail — every ePHI access and modification logged, append-only
- [x] HIPAA-compliant infrastructure — encryption at rest and in transit, no PHI in logs, BAA-ready

### Add After Validation (V1.x)

Once the core is deployed and the pilot practice is using it daily.

- [ ] Configurable pathway template admin UI — triggered when a fourth cancer type is needed or an existing pathway needs updating
- [ ] Pathway progression timeline visualization — triggered when navigators report difficulty understanding patient position in worklist/list view
- [ ] Multi-facility event origin tracking — triggered when practice wants to understand which external facilities cause most delays
- [ ] Navigator caseload dashboard — triggered when there are multiple navigators and load balancing becomes a concern
- [ ] Alert age sorting and escalation visibility — triggered when navigators report difficulty distinguishing new vs. stale alerts

### Future Consideration (V2+)

Defer until pilot data validates the product and Phase 2 scope is defined.

- [ ] SMS/push notifications — defer until dashboard habit is established and notification content policy is defined
- [ ] EMR integration (HL7 FHIR) — defer until API access agreements are negotiated with the practice's EMR vendor
- [ ] Predictive at-risk scoring — defer until sufficient historical deviation data exists to train or validate a model
- [ ] Multi-practice support (SaaS) — defer until single-practice PoC is validated and a second customer is acquired
- [ ] Reporting and analytics suite — defer until enough data exists to make reports meaningful (minimum 3–6 months of patient data)

---

## Feature Prioritization Matrix

| Feature | User Value | Implementation Cost | Priority |
|---------|------------|---------------------|----------|
| Prioritized patient worklist | HIGH | MEDIUM | P1 |
| Care event recording | HIGH | MEDIUM | P1 |
| Deviation detection engine | HIGH | HIGH | P1 |
| Alert queue dashboard | HIGH | MEDIUM | P1 |
| Alert resolution workflow | HIGH | MEDIUM | P1 |
| Patient record CRUD | HIGH | LOW | P1 |
| Role-based access control | HIGH | MEDIUM | P1 |
| Immutable audit trail | HIGH | MEDIUM | P1 |
| HIPAA-compliant infrastructure | HIGH | HIGH | P1 |
| Pathway template definitions (content) | HIGH | MEDIUM | P1 |
| Per-patient pathway status view | HIGH | MEDIUM | P1 |
| AI-generated alert descriptions (Claude API) | MEDIUM | MEDIUM | P2 |
| AI-suggested corrective actions | MEDIUM | LOW | P2 |
| Pathway progression timeline visualization | MEDIUM | MEDIUM | P2 |
| Alert age visibility | MEDIUM | LOW | P2 |
| Multi-facility event origin tracking | MEDIUM | LOW | P2 |
| Configurable pathway template admin UI | HIGH | HIGH | P2 |
| Navigator caseload visibility | LOW | LOW | P3 |
| Reporting and analytics suite | MEDIUM | HIGH | P3 |
| SMS notifications | LOW | HIGH | P3 |
| EMR integration | HIGH | HIGH | P3 |

**Priority key:**
- P1: Must have for launch — without these, V1 cannot operate
- P2: Should have — add when core is stable, before pilot expands
- P3: Nice to have — future consideration, requires separate scoping

---

## Competitor Feature Analysis

| Feature | ONCONav (ONCO Inc.) | CONNECT (NurseNav) | CancerNAV (HealthcareNav) | Onco-Navigator approach |
|---------|---------------------|--------------------|---------------------------|------------------------|
| Pathway monitoring | Standardized touchpoints by diagnosis/phase | Timeliness-of-care milestones | Real-time milestone and task tracking | Durable workflow engine (Temporal.io) with explicit state machine per patient — more rigorous than milestone lists |
| Deviation detection | Overdue follow-up flags | Follow-up care alert calendar | Proactive alerts and reminders | Three deviation types: missing, delayed, out-of-order — with prerequisite chain analysis |
| Alert descriptions | Template strings only | Template strings only | Template strings only | Template strings + Claude API for edge cases — first known system with AI-generated alert text |
| Alert resolution | Basic encounter documentation | Task completion tracking | Encounter note to EHR | Explicit resolution workflow with action documentation feeding immutable audit trail |
| Pathway configurability | Predefined workflow checklists | Disease-specific workflows | Disease-specific checklists | Admin-configurable pathway templates: steps, prerequisites, time windows — no code deploy required |
| EMR integration | ADT feeds, EHR note push, registry | Limited | SMART/HL7 FHIR | V1: none by design. Phase 2: HL7 FHIR. |
| HIPAA/compliance | Assumed (enterprise product) | Assumed | SOC 2 certified | HIPAA from day one: field encryption, immutable audit trail, RBAC, BAA-ready |
| AI / LLM features | None | None | None | Claude API for non-standard deviation alert generation and corrective action suggestions |
| Manual data entry | Partially (ADT integration reduces it) | Navigation-focused entry | Automated metrics where possible | V1 fully manual, optimized form UX is critical success factor |
| Accreditation reporting | CoC standards reports | 50+ filterable reports | Metrics for program assessment | Not a V1 priority — collect clean data now, build reports in Phase 2+ |

---

## Sources

- ONCONav product page: https://www.oncoinc.com/oncology-products/onconav-navigation-software/
- CONNECT (NurseNav) product page: https://nursenav.com/
- CancerNAV product page: https://www.healthcarenav.com/cancernav
- careMESH oncology navigation overview: https://www.caremesh.com/oncology-navigation-platforms-improving-patient-outcomes
- careMESH patient navigation software overview: https://www.caremesh.com/patient-navigation-software-streamlining-patient-journeys-in-healthcare
- MIT Sloan Action Learning: nurse navigator patient tracking: https://mitsloan.mit.edu/action-learning/streamlining-patient-tracking-systems-nurse-navigator
- HIPAA audit trail requirements: https://www.scrut.io/hub/hipaa/hipaa-audit-trail-requirements
- HIPAA access control requirements: https://censinet.com/perspectives/hipaa-access-control-requirements-explained
- Oncology data security requirements (HIPAA, GDPR, 21 CFR Part 11): https://www.accountablehq.com/post/oncology-data-security-requirements-how-to-comply-with-hipaa-gdpr-and-21-cfr-part-11
- Care pathway deviation detection (Hindawi): https://www.hindawi.com/journals/sp/2022/6993449/
- Mining deviations from patient care pathways via EMR audits: https://www.researchgate.net/publication/262369145_Mining_Deviations_from_Patient_Care_Pathways_via_Electronic_Medical_Record_System_Audits
- JMIR Human Factors — user-centered design for oncology navigation tools: https://humanfactors.jmir.org/2026/1/e87686
- Azra AI oncology pathway management: https://www.azra-ai.com/
- Tempus oncology care pathway solutions: https://www.tempus.com/oncology/care-pathway-solutions/
- Clinical pathway deviation detection: https://pmc.ncbi.nlm.nih.gov/articles/PMC3197986/

---
*Feature research for: Oncology care pathway monitoring (Onco-Navigator)*
*Researched: 2026-04-29*
