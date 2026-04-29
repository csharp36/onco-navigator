# Pitfalls Research

**Domain:** HIPAA-compliant oncology care pathway monitoring with Temporal.io workflow orchestration
**Researched:** 2026-04-29
**Confidence:** HIGH (Temporal.io official docs + community forum), HIGH (HIPAA regulatory sources), MEDIUM (clinical adoption research)

---

## Critical Pitfalls

### Pitfall 1: Temporal Workflow Non-Determinism from Code Changes

**What goes wrong:**
You ship a pathway logic change — adding a new step, reordering activities, changing a timer duration — to workers that are still executing long-running patient workflows started under the old code. When those old executions replay their event history (e.g., after a worker restart or crash), the new code produces a different sequence of commands than what was originally recorded. Temporal detects this mismatch and terminates the workflow execution with a non-determinism error. For a breast cancer patient in week 6 of a 12-week pathway, this means silent workflow death.

**Why it happens:**
Developers treat Temporal workers like stateless microservices — deploy new code, old instances pick it up, no problem. This works for regular services but is catastrophically wrong for durable workflows. Temporal's correctness guarantee depends on replay fidelity: the code must produce the exact same sequence of commands given the same history, forever.

**How to avoid:**
- Use `Workflow.getVersion()` (patching API) for every breaking change to running workflows. Wrap changed sections in version branches.
- Write replay tests before every deployment: capture real workflow history snapshots and run them against the new code. The Temporal Java SDK includes `WorkflowReplayer` for this.
- When changes are too large to patch cleanly, use task queue versioning: route new workflow starts to a new task queue/worker version, let old executions drain on the old worker, then decommission.
- Never remove `Workflow.getVersion()` patch blocks until you have confirmed zero running executions use the old version.

**Warning signs:**
- Any deployment where pathway step order changes, new activities are added/removed, or timer durations change
- `NonDeterministicException` appearing in worker logs after deployment
- Workflows suddenly transitioning to FAILED state after a code push

**Phase to address:** Foundation phase (Temporal setup). Must be designed in before any workflow code is written — retrofitting versioning discipline is much harder than establishing it from the start.

---

### Pitfall 2: Event History Explosion in Long-Running Patient Pathways

**What goes wrong:**
An oncology patient pathway spans 8-16 weeks. Each signal received, each timer fired, each activity executed generates events in Temporal's event history. At the 10K event mark, Temporal emits warnings. At 51,200 events or 50MB, Temporal terminates the workflow with an error. A pathway with daily check signals, multiple timer arms, and active coordination generates events faster than expected.

**Why it happens:**
Developers prototype a pathway that works fine in testing (a handful of executions, a few signals) and never model the event rate for a full patient journey. Each `Workflow.sleep()` call generates 2 events (TimerStarted + TimerFired). Each signal generates 2-3 events. Each activity generates 3-4 events (scheduled, started, completed). A daily check-in signal alone over 90 days is 270 events from signals plus timer events.

**How to avoid:**
- Implement `Continue-As-New` as a first-class citizen in every patient pathway workflow, not an afterthought. Design the workflow state object to be fully serializable from the start so it can be passed cleanly to the next run.
- Drain all pending signals before calling `continueAsNew()`. Unawaited activities are cancelled when Continue-As-New fires.
- Trigger Continue-As-New proactively at a safe threshold (e.g., check `Workflow.getInfo().getHistoryLength() > 8000`) rather than waiting for Temporal's 10K warning.
- Model your expected event rate in a spreadsheet before building: signals per week x event multiplier x pathway duration in weeks = projected event count.

**Warning signs:**
- Temporal server logs warn "workflow history size exceeds threshold"
- `WorkflowExecutionAlreadyStarted` errors in unusual contexts
- Long workflow replay times during worker startup

**Phase to address:** Workflow engine phase (pathway implementation). Must be built into the initial workflow skeleton before pathways are implemented.

---

### Pitfall 3: Alert Fatigue — Nurses Stop Trusting and Stop Looking

**What goes wrong:**
The system generates alerts that are technically correct but clinically trivial, redundant, or poorly timed. Nurses receive 10+ alerts per shift, a significant fraction of which turn out to be irrelevant to their actual workload. Within 2-4 weeks they begin treating the alert dashboard as noise. They develop workarounds (checking the old spreadsheet instead) and stop resolving alerts. The system has data, the nurses have experience, but the two are no longer connected. The proof of concept fails not because the technology broke but because the UX was antagonistic.

**Why it happens:**
Developers default to "alert on everything and let the user decide." This is reasonable in software monitoring (PagerDuty) but catastrophic in clinical settings. Clinical research is explicit: alert fatigue causes documented patient harm. The issue is structural — it is easier to add a detection rule than to define the clinical threshold at which an alert is actually worth interrupting a nurse's workflow.

**How to avoid:**
- Before writing any alert generation code, sit with the nurse navigators and map which deviations actually require action vs. which are informational. This is domain design, not software design.
- Prioritize alert types: URGENT (patient missed surgery, requires same-day action), ROUTINE (result overdue, needs follow-up within 24h), INFORMATIONAL (pathway note, no action required). Only URGENT should visually interrupt.
- Build suppression rules: if an alert was already resolved for this patient/step pattern recently, suppress the repeat.
- Track the alert-to-action ratio as a first-class metric. If nurses are resolving fewer than 70% of alerts with a real action, the threshold is too low.
- Start with fewer alert types and add more only when nurses ask for them — never ship untested alert types to the pilot.

**Warning signs:**
- Alert resolution rate drops below 50%
- Nurses mark alerts as resolved without opening the patient record
- Staff verbally say "I just close those, they're always nothing"
- Alert backlog grows faster than it is resolved

**Phase to address:** Alert system phase. Alert thresholds must be co-designed with the pilot practice's nurse navigator before implementation, not tuned after deployment.

---

### Pitfall 4: HIPAA Non-Compliance from Incomplete Audit Trail

**What goes wrong:**
The audit log captures login/logout and patient record opens but misses: viewing a patient alert, exporting a patient list, accessing the dashboard with a broad cohort query, an admin changing a pathway configuration, or a system-generated activity touching PHI. During an OCR audit or breach investigation, the incomplete log cannot demonstrate who accessed what PHI and when. This is a HIPAA Security Rule violation (45 C.F.R. § 164.312(b)) that can result in civil monetary penalties even if no breach occurred.

**Why it happens:**
Developers implement audit logging incrementally, adding it feature by feature. Early features get logged, later features get skipped under time pressure. Temporal workflow activity executions often touch PHI but are not considered "user access" and go unlogged. The audit log schema was designed for web request logging, not the full range of PHI access patterns.

**How to avoid:**
- Design the audit event schema before writing any code that touches PHI. Define the canonical audit event structure: actor (user or system), action, resource type, resource ID, timestamp, IP, outcome.
- Use a Spring AOP interceptor or Hibernate listener to automatically capture all PHI-touching repository operations, not just controller-level actions.
- Temporal activity executions that read or write patient data must emit an audit event. This means the audit service must be callable from activity implementations.
- Make the audit log immutable from the application layer: append-only table with no UPDATE or DELETE permissions granted to the application service account.
- Audit log retention must be defensible: HIPAA requires 6 years for security documentation. Plan the storage/archival strategy before launch.

**Warning signs:**
- A developer asks "do we need to log that?" — the answer is always yes if PHI is involved
- Audit logs are stored in the same table space with application data (not isolated)
- System-triggered actions (Temporal activity completions, scheduled jobs) appear in application logs but not audit logs

**Phase to address:** Foundation phase. Audit infrastructure must be operational before the first feature that touches patient data is built. It is nearly impossible to retrofit comprehensively.

---

### Pitfall 5: HIPAA Audit Log Mutability — Logs the Application Can Delete

**What goes wrong:**
The application database user has UPDATE and DELETE privileges on the audit log table. Or, the audit table is in the same schema as operational data, accessible to the same service account. A malicious insider, a compromised application account, or an accidental bulk delete removes audit records. The organization cannot prove compliance for the affected period, and the missing records themselves constitute a HIPAA violation.

**Why it happens:**
Developers model audit logs like application tables and apply the same CRUD access patterns. The concept of write-once, read-never-modify audit storage runs counter to how most ORM-centric development works.

**How to avoid:**
- Create a separate PostgreSQL role for audit log writes with INSERT-only on the audit table — no UPDATE, no DELETE, no SELECT on the writer role.
- Create a separate read-only role for audit log querying (used by admin dashboard and compliance reports only).
- The application service account that handles patient workflows must NOT have SELECT on the audit log — it can write but cannot read its own audit trail (reduces insider threat surface).
- For long-term retention, archive audit logs to AWS S3 with Object Lock (WORM mode) on a rolling basis.
- Add a hash chain or Merkle tree signature to audit records so any modification to historical records is detectable.

**Warning signs:**
- Application service account has the same permissions on audit tables as on patient tables
- Audit logs can be filtered or truncated from within the admin dashboard
- No separation between "write audit record" and "read audit records" roles

**Phase to address:** Foundation phase. Database permission model must be established before the first PHI-touching feature.

---

### Pitfall 6: PHI Leaking into Temporal Event History

**What goes wrong:**
Patient identifiers, medical record numbers, diagnosis codes, or other PHI are passed as direct workflow/activity parameters. Temporal persists all input and output payloads in its event history in the persistence store. If Temporal's persistence database (PostgreSQL) is not separately HIPAA-scoped, or if PHI appears in Temporal's workflow search attributes (which are indexed and visible in the UI), PHI has escaped into a system that may not have been reviewed for HIPAA controls.

**Why it happens:**
The natural workflow API design passes identifiers and context as parameters. Developers don't realize Temporal stores all inputs/outputs in the history database, and the Temporal Web UI can display them. The Temporal persistence store needs its own HIPAA controls just like the application database.

**How to avoid:**
- Pass only opaque internal identifiers (UUID patient IDs, pathway instance IDs) through Temporal workflow and activity parameters. Never pass names, DOB, MRN, diagnosis, or other PHI.
- All PHI resolution happens inside activity implementations against the application database, never in workflow parameters.
- Treat the Temporal persistence database as a HIPAA-covered system: same encryption, access control, and audit requirements as the main application database.
- Restrict access to the Temporal Web UI to a HIPAA-aware network segment; the UI displays workflow input/output payloads.
- Document the data classification of everything that passes through Temporal in architecture documentation.

**Warning signs:**
- Workflow start parameters include patient name, MRN, or clinical data
- Developers describe Temporal event history as "just metadata"
- The Temporal Web UI is accessible without authentication on the development network

**Phase to address:** Foundation phase and workflow engine phase. Must be in the design before workflow implementations are written.

---

### Pitfall 7: BAA Gap with Third-Party Services That Touch PHI

**What goes wrong:**
The Claude API is used to generate deviation alert text. The prompt includes patient pathway context. No BAA has been signed with Anthropic. This is a HIPAA violation regardless of how small the PHI footprint is. Similarly, if any monitoring, logging, error tracking, or LLM service receives PHI without a signed BAA, the covered entity (the pilot practice) is in violation and so is the software company acting as their business associate.

**Why it happens:**
Developers focus on technical integration and overlook the legal prerequisite. Public/consumer AI APIs explicitly do not offer BAAs. The Anthropic API has enterprise/HIPAA-compliant offerings but they are not the default product tier.

**How to avoid:**
- Before any integration with a third-party service that will receive PHI: confirm BAA availability, obtain a signed BAA, and document it.
- For the Claude API specifically: either (a) obtain an enterprise BAA from Anthropic before using PHI in prompts, or (b) strip all PHI from prompts and use only anonymized pathway structure (step names, timing gaps, deviation type) — no patient identifiers in API calls.
- The safest V1 approach: Claude API receives zero PHI. The prompt describes the deviation type and clinical context in generic terms only. The nurse applies the generic guidance to the specific patient.
- Audit every third-party service integration for BAA status before the pilot practice onboards.
- Keep a BAA register: service name, BAA status, date signed, expiration.

**Warning signs:**
- Integration with any AI API without first asking "does this service have a signed BAA with us?"
- Patient context (name, MRN, diagnosis) appearing in API request logs for external services
- No written BAA register or compliance checklist exists

**Phase to address:** Foundation phase (legal/compliance setup) and AI integration phase. BAA must precede any PHI flowing to third parties, including development/test data.

---

### Pitfall 8: Pathway Model Assumes Linear Happy Path — Patients Don't

**What goes wrong:**
The pathway engine is designed as a sequential state machine: step 1 completes, triggers step 2, triggers step 3. But real oncology patients: have surgery delayed due to a cardiology consult finding, receive chemotherapy before final pathology results arrive due to clinical judgment, have a second biopsy that runs concurrent with the originally expected pathology, or re-enter an earlier stage after recurrence. The engine flags all of these as deviations when some are deliberate clinical decisions. Nurses learn that the system doesn't understand their practice and stop trusting it.

**Why it happens:**
Engineers model the documented "canonical pathway" as the workflow, which is written as a linear ideal. Clinical domain experts describe reality — "sometimes we do X before Y" — but this doesn't make it into the workflow model because it complicates the design.

**How to avoid:**
- Treat pathways as partially ordered sets with optional steps and time windows, not strict sequences. A step can be marked "allowed early" or "order-independent from sibling step" in the pathway template.
- Build a concept of "physician override" into the data model from day one: a nurse can annotate a deviation as "intentional, per physician order" which suppresses the alert and logs the justification.
- Implement concurrent step execution in the workflow model. The Temporal workflow can track multiple parallel branches (e.g., concurrent radiation planning + chemotherapy initiation) rather than requiring one to complete before the other starts.
- During pathway definition (with the oncologist co-author), explicitly model every known valid non-linear scenario and encode them as valid pathway variations, not as suppressible deviations.

**Warning signs:**
- Pathway definition only has one "success path"
- No mechanism for a nurse to mark a deviation as intentional
- The oncologist co-author uses phrases like "well, sometimes we do it this way instead" during pathway review
- Alert suppression is the only tool available for handling clinical variations

**Phase to address:** Pathway modeling phase. The data model must support non-linear execution before pathways are configured.

---

### Pitfall 9: Manual Data Entry Abandonment — If It's Painful, There Is No Data

**What goes wrong:**
The system depends entirely on manual data entry for V1. If entering a care event takes more than 60-90 seconds, nurses skip it or batch-enter at end of shift from memory, introducing errors and delays. If the form requires navigating multiple screens, selecting from poorly organized dropdowns, or re-entering information the system should know, adoption fails silently. The system shows incomplete pathway status not because patients are deviating but because data wasn't entered. Alerts fire incorrectly. Trust evaporates.

**Why it happens:**
Developers build data entry forms that satisfy the data model, not the clinical workflow. The form asks for everything the system needs rather than the minimum the nurse needs to type. Fields are generic database fields, not clinically labeled terminology. The form isn't tested with actual nurses performing their real tasks under time pressure.

**How to avoid:**
- Design data entry around the nurse's existing mental model: "I just got the call that Mrs. Johnson's surgery report came back. I need to record that." The form should be: select patient, select event type (surgery complete), enter result/date, save. Three interactions, one screen.
- Implement smart defaults: if a step completion is recorded, pre-populate the next expected step's due date automatically.
- Use clinical terminology in labels, not database field names. "Pathology report received" not "event_type = PATH_RPT, status = COMPLETE."
- Conduct at least one hallway usability test with the pilot practice's nurses before the first patient enters the system.
- Measure task completion time for common data entry scenarios during testing. Target: primary entry flows under 60 seconds.

**Warning signs:**
- Forms require more than 5 clicks to complete a common task
- The term "event type" appears anywhere in the nurse-facing UI
- No nurse has tested the data entry flow before the pilot starts
- Nurses describe the system as "another thing to fill out"

**Phase to address:** Dashboard/UX phase. Forms must be validated with nurses before the pilot, not after.

---

### Pitfall 10: Temporal Self-Hosted Operational Complexity Underestimated

**What goes wrong:**
Self-hosting Temporal requires operating: a Temporal server cluster (history, matching, frontend, worker services), a persistence database (PostgreSQL for history), and a visibility store (Elasticsearch/OpenSearch for advanced search, or basic PostgreSQL visibility). In a V1 proof of concept, the team deploys a single-node Docker Compose setup for local development and assumes it translates directly to an AWS deployment. The AWS deployment breaks on configuration differences, certificate rotation, database schema migrations, and the requirement to run Temporal services separately at production scale.

**Why it happens:**
The Temporal Docker Compose quickstart is excellent for development and gives a false sense of simplicity. Production Temporal requires distinct service topology. Additionally, Cassandra — which some Temporal documentation references — has limited cloud compatibility (e.g., does not work with AWS Keyspaces) and is harder to operate than PostgreSQL.

**How to avoid:**
- Use PostgreSQL exclusively for Temporal persistence (history + visibility). Avoid Cassandra for a new deployment — PostgreSQL is the correct choice for this scale, has better cloud support, and you already know it.
- Separate local Docker Compose (development) from the AWS deployment configuration from the beginning. Do not treat Docker Compose as a path to production.
- Use the official Temporal Helm charts for the AWS deployment target. Plan Kubernetes from the start even if local dev uses Docker Compose.
- Test the AWS deployment path (even a single-node AWS instance) before the pilot starts. Do not discover AWS configuration gaps under pilot pressure.
- Document the Temporal upgrade procedure: server upgrades require database schema migrations and careful worker rolling updates.

**Warning signs:**
- The team has only ever run Temporal via Docker Compose
- The AWS deployment plan is "run the same Docker Compose on an EC2 instance"
- No runbook exists for Temporal server restart, upgrade, or database migration

**Phase to address:** Infrastructure phase. The AWS deployment must be validated (not just planned) before the pilot practice onboards.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Skip `Workflow.getVersion()` for small changes | Faster deployment | Non-determinism crash on running workflows; forces rewrite | Never in workflows with running executions |
| PHI in Temporal workflow parameters | Simpler code | HIPAA violation; PHI in Temporal persistence store | Never |
| Single audit log table with application write privileges | Simpler schema | Mutable audit trail; HIPAA violation risk | Never |
| Alert on every pathway deviation without threshold review | Complete detection | Alert fatigue within weeks; system abandoned | Never as a launch configuration |
| Shared database user for Temporal and application | Simpler DB setup | No isolation; HIPAA access control failure | Never in any environment with real PHI |
| Linear pathway model only | Faster initial build | Cannot represent real clinical practice; cascading false alerts | Only acceptable if override annotation exists |
| Docker Compose for AWS deployment | No Kubernetes learning curve | Operational fragility; no HA; upgrade path broken | Only for local development |
| Hardcode BAA-status check to "assumed signed" | Unblocks development | Legal exposure; pilot practice bears HIPAA liability | Never with real patient data |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| Temporal Java SDK + Spring Boot | Calling Workflow APIs during Spring bean initialization causes `IllegalStateException` | Only invoke Workflow APIs within workflow execution threads, not in `@PostConstruct` or bean wiring |
| Temporal signals | Sending signals to a completed workflow; not draining signals before `continueAsNew()` | Always check workflow state before signaling; drain signal queue explicitly before Continue-As-New |
| Temporal timers | Assuming a timer fires immediately when the worker restarts after being down | Timers are durable and fire on schedule regardless of downtime; test this explicitly |
| Temporal + PostgreSQL | Using the application DB user for Temporal persistence (mixing PHI and Temporal data in same schema) | Separate database users and schemas for Temporal persistence vs. application data |
| Claude API | Passing patient identifiers or clinical values in prompts without a BAA | Strip all PHI before constructing prompts; use generic pathway terminology only |
| pgAudit | Installing pgAudit but not configuring which statement classes to log (defaults miss SELECT) | Explicitly configure `pgaudit.log = 'read, write, ddl'` to capture PHI access |
| PostgreSQL TLS | Leaving `ssl = off` in PostgreSQL config (default in many Docker images) | Set `ssl = on`, configure certificates, set `ssl_min_protocol_version = TLSv1.2` |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Large objects in Temporal activity payloads | Slow workflow task scheduling; high Temporal DB storage growth | Pass only IDs through Temporal; fetch full objects inside activities from the application DB | At ~50+ active patients with frequent signals |
| Polling-based deviation detection (cron queries) | Database load spikes every N minutes; detection lag | Use Temporal timers for per-patient deadline tracking; avoid global polling loops | At ~200+ active patients |
| Missing database indexes on audit log table | Slow compliance queries (date range, user, patient) | Index on (actor_id, timestamp), (resource_id, timestamp), (action, timestamp) from the start | At ~6 months of audit log accumulation |
| N+1 queries in dashboard patient list | Dashboard load time grows linearly with patient count | Eager-load pathway status and latest alert in a single query with joins | At ~100+ active patients on the dashboard |
| Temporal event history not pruned | Temporal persistence DB grows unboundedly | Configure workflow retention (e.g., 30 days after completion for HIPAA minimum) and test archival | At ~6 months of pilot operation |

---

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Encryption keys stored in application environment variables alongside the application | Key compromise = PHI compromise; no separation between key custody and data | Store keys in AWS KMS or HashiCorp Vault; application retrieves keys at runtime via IAM role, not env vars |
| PostgreSQL backups not encrypted | Backup theft exposes PHI even if the live database is encrypted | Use `pg_dump` with AES-256 encryption; store backups in S3 with SSE-KMS; restrict backup bucket access |
| TLS terminated at load balancer, plaintext to database | Traffic sniffing on internal network exposes PHI | Enforce TLS at the database level (`ssl = on`); use certificate-based authentication for application-to-DB connections |
| Broad RBAC roles (e.g., all nurses can see all patients) | Unauthorized access to PHI; HIPAA minimum-necessary violation | Implement patient-panel scoping: nurses see only patients assigned to their care team |
| No session timeout on dashboard | Unattended workstation = unauthorized PHI access | Enforce 15-minute session timeout; require re-authentication to resume |
| PHI in application logs (Spring Boot default logging) | Log aggregation systems receive PHI without HIPAA controls | Implement a log scrubbing filter that masks patient identifiers before they reach log output |
| Temporal Web UI exposed without authentication | Anyone who can reach the UI sees workflow inputs/outputs (which may contain PHI if design is wrong) | Restrict Temporal Web UI to VPN or internal network only; add authentication layer |

---

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Alert list sorted by creation time, not urgency | Nurses process in arrival order; critical alerts buried | Sort by severity (URGENT first), then by time within severity; allow nurse to filter by type |
| Pathway visualization shows all steps including future/tentative ones | Cognitive overload; confusion about what is actionable now | Show completed steps, current step, and next 1-2 expected steps; hide distant future steps |
| "Resolve alert" requires selecting a resolution reason from a long dropdown | Friction causes nurses to resolve without documenting | Provide 3-4 common reasons as buttons, with a free-text option for other; single click to resolve |
| Data entry form uses system terminology | Nurses do not recognize field names | Co-design all form labels with the oncologist co-author; use the exact language nurses use verbally |
| No confirmation when alert is resolved | Accidental resolution with no recovery path | Show a brief "Undo" toast for 5 seconds after resolution; log all resolutions in audit trail |
| Dashboard requires login every time | Nurses work fast in short bursts; repeated login = abandonment | Implement session persistence with timeout, not login on every page load; re-auth for sensitive actions only |

---

## "Looks Done But Isn't" Checklist

- [ ] **Audit trail:** Verify system-generated events (Temporal activity completions, scheduled jobs) appear in the audit log — not just user-initiated actions
- [ ] **Audit trail:** Verify the application service account cannot SELECT, UPDATE, or DELETE from the audit log table
- [ ] **Temporal versioning:** Verify that at least one workflow has been modified using `Workflow.getVersion()` and replay tests pass against the old history
- [ ] **Encryption:** Verify WAL archives and pg_dump backups are encrypted — not just the live database files
- [ ] **BAA:** Verify a signed BAA exists for every third-party service that receives any data in a production or pilot environment
- [ ] **Alert thresholds:** Verify at least one nurse has reviewed each alert type and confirmed it represents an actionable deviation at the configured threshold
- [ ] **Continue-As-New:** Verify that long-running pathway workflows check event history length and call `continueAsNew()` before reaching the 10K warning threshold
- [ ] **PHI in Temporal:** Verify that Temporal workflow and activity inputs/outputs contain no PHI by inspecting the Temporal Web UI on a test execution
- [ ] **RBAC:** Verify that a nurse account cannot access patients outside their assigned panel
- [ ] **Pathway overrides:** Verify there is a mechanism for a nurse to mark a deviation as intentional without the system re-alerting on it

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Non-determinism crash on running workflows | HIGH | Identify all affected running workflows; manually export their state; terminate and restart with patched code; potentially lose workflow timer state |
| Event history overflow (workflow terminated) | MEDIUM | Restore workflow state from application DB; start a new workflow execution as a continuation; no Temporal history carries over |
| Incomplete audit trail discovered during audit | HIGH | Reconstruct partial timeline from application logs and database change history; demonstrate compensating controls; document gap in writing; cannot fully remediate retroactively |
| Alert fatigue (nurses ignoring system) | MEDIUM | Survey nurses to identify which alert types are noise; suppress or raise thresholds for low-value alerts; schedule co-design session to rebuild alert definitions; requires trust-rebuilding over weeks |
| PHI discovered in Temporal event history | HIGH | Assess scope; notify legal/compliance; if BAA gap: notify covered entity immediately; retroactively scope Temporal DB under same HIPAA controls; purge affected histories if possible under Temporal's history retention |
| Pilot practice abandons system (poor UX) | HIGH | Cannot recover if pilot ends; prevention is the only strategy |

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| Workflow non-determinism | Temporal foundation + every deployment | Replay tests pass in CI against captured workflow histories |
| Event history overflow | Pathway implementation phase | Load test simulating 90-day patient journey confirms Continue-As-New fires correctly |
| Alert fatigue | Alert system design (before any alert code) | Nurse co-design session sign-off on each alert type and threshold |
| Incomplete audit trail | Foundation phase | Security review confirms all PHI-touching code paths emit audit events |
| Mutable audit log | Foundation phase | Database permission audit: application account has INSERT-only on audit table |
| PHI in Temporal history | Workflow engine design | Manual inspection of Temporal Web UI on test workflows confirms no PHI in payloads |
| BAA gap | Pre-pilot legal phase | BAA register lists all third-party services with signed BAA status before any real PHI is used |
| Linear pathway model | Domain modeling phase | Pathway spec review with oncologist confirms at least 3 non-linear scenarios are encoded as valid variations |
| Data entry abandonment | Dashboard UX phase | Usability test with 2+ nurses; primary entry flows complete in under 60 seconds |
| Temporal self-hosted complexity | Infrastructure phase | AWS deployment validated (not just planned) with actual Temporal services running before pilot |

---

## Sources

- Temporal.io Workflow Versioning (Java SDK): https://docs.temporal.io/develop/java/versioning
- Temporal.io Long-Running Workflows — Continue-As-New: https://temporal.io/blog/very-long-running-workflows
- Temporal.io Failures Reference: https://docs.temporal.io/references/failures
- Temporal.io Signal/Query Handling: https://docs.temporal.io/handling-messages
- Temporal.io Self-Hosted Deployment Guide: https://docs.temporal.io/self-hosted-guide/deployment
- Effective Temporal (Java) — community patterns: https://medium.com/@yongskong/effective-temporal-java-9b6f0fa7cd82
- Understanding Non-Determinism in Temporal.io: https://medium.com/@sanhdoan/understanding-non-determinism-in-temporal-io-why-it-matters-how-to-avoid-it-3d397d8a5793
- HIPAA Audit Log Requirements (Kiteworks): https://www.kiteworks.com/hipaa-compliance/hipaa-audit-log-requirements/
- HIPAA Security Rule — HHS.gov: https://www.hhs.gov/hipaa/for-professionals/security/laws-regulations/index.html
- HIPAA Business Associate Agreements — HIPAA Journal: https://www.hipaajournal.com/hipaa-business-associate-agreement/
- PostgreSQL HIPAA Security Configuration Guide: https://www.accountablehq.com/post/postgresql-healthcare-security-configuration-guide-hipaa-compliant-encryption-access-controls-and-auditing
- Alert Fatigue — AHRQ PSNet Primer: https://psnet.ahrq.gov/primer/alert-fatigue
- Alert Fatigue in Primary Care — JMIR Systematic Review: https://www.jmir.org/2025/1/e62763
- Medication Safety Alert Fatigue — JAMIA: https://academic.oup.com/jamia/article/26/10/1141/5519579
- EHR Adoption and Healthcare Staff Well-Being — PMC: https://pmc.ncbi.nlm.nih.gov/articles/PMC11594038/
- Clinical CDSS in Oncology — Evaluation Systematic Review: https://www.sciencedirect.com/science/article/abs/pii/S1040842823002317
- Patient Journey Non-Linearity (graph theory approach): https://pmc.ncbi.nlm.nih.gov/articles/PMC12510292/

---
*Pitfalls research for: HIPAA-compliant oncology care pathway monitoring / Temporal.io + Java Spring Boot + PostgreSQL*
*Researched: 2026-04-29*
