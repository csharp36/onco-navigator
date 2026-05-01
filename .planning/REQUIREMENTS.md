# Requirements: Onco-Navigator AI

**Defined:** 2026-04-29
**Core Value:** Prevent patients from falling through the cracks by systematically watching every patient's care pathway and surfacing deviations before they become wasted visits, delayed treatments, or invisible gaps.

## v1 Requirements

Requirements for initial release (pilot-ready proof of concept at single practice). Each maps to roadmap phases.

### Data Management

- [ ] **DATA-01**: Care coordinator can add a new patient with name, DOB, MRN, primary diagnosis (cancer type and stage), diagnosis date, assigned nurse navigator, and treating physician
- [ ] **DATA-02**: Care coordinator can add a care event to a patient record with event type, date, status (Scheduled/Completed/Cancelled/Pending), and optional notes
- [ ] **DATA-03**: Care coordinator can update the status of an existing care event
- [ ] **DATA-04**: Care coordinator can deactivate a patient record (deceased, transferred) to stop alert generation
- [ ] **DATA-05**: All data entry actions are logged with staff member identity and timestamp

### Pathway Engine

- [x] **PATH-01**: System maintains configurable pathway templates defining the correct sequence of events for each cancer type, with step names, types, prerequisites, time windows, and suggested corrective actions
- [x] **PATH-02**: System includes pathway templates for breast cancer (Stage I-III), lung cancer (Stage I-III), and colorectal cancer (Stage I-III) as defined in the clinical specification
- [x] **PATH-03**: System detects when a required pathway step has no associated care event in Completed status (missing event)
- [x] **PATH-04**: System detects when the time elapsed since the previous step exceeds the configured time window (delayed event)
- [x] **PATH-05**: System detects when a care event is recorded or scheduled before its prerequisite steps are completed (out-of-order event)
- [x] **PATH-06**: System does not create duplicate alerts for the same deviation — if an alert for a given patient and step is already open, no new alert is created
- [x] **PATH-07**: System logs every monitoring evaluation including timestamp, patients evaluated, and alerts generated
- [x] **PATH-08**: Physician can annotate a deliberate step reordering to suppress false-positive alerts for that patient's pathway

### Alert Management

- [ ] **ALRT-01**: Dashboard displays all open alerts sorted by severity (overdue first, then missing, then out-of-order)
- [ ] **ALRT-02**: Each alert shows patient name, MRN, alert type, affected pathway step, deviation description, suggested corrective action, and time elapsed since creation
- [ ] **ALRT-03**: Nurse can view a patient's full pathway status showing all steps and their current status
- [ ] **ALRT-04**: Nurse can mark an alert as Resolved and enter a free-text note describing the action taken
- [ ] **ALRT-05**: Dashboard shows count of open alerts, always visible
- [ ] **ALRT-06**: Dashboard shows a list of all patients with assigned pathway and summary status (On Track, Alert Active, Resolved)

### Document Ingestion

- [x] **DOC-01**: A test corpus of de-identified/synthetic clinical PDFs exists in the repository covering pathology reports, radiology reports, referral letters, operative notes, and lab results for breast, lung, and colorectal cancer
- [x] **DOC-02**: User can drag-and-drop a PDF onto the dashboard and the system classifies it into a document type using Claude AI (pathology report, radiology report, referral letter, operative note, lab result)
- [x] **DOC-03**: After classification, system extracts patient identifiers (MRN, name) from the document and matches to an existing patient or offers new patient creation
- [x] **DOC-04**: On successful patient match, a pre-filled care event wizard opens with extracted data (event type, date, relevant details) for user confirmation before saving
- [x] **DOC-05**: Source PDF is stored as bytea in PostgreSQL linked to the care event, with original filename, content type, and classification metadata

### AI Integration

- [x] **AI-01**: System uses template-based alert text for known deviation types as defined in pathway templates
- [x] **AI-02**: System uses Claude API to generate plain-language alert descriptions for non-standard deviations where template text does not apply (zero PHI in prompts)
- [x] **AI-03**: System uses Claude API to suggest corrective actions for edge cases not covered by pathway template suggestions
- [x] **AI-04**: System falls back to template text when Claude API is unavailable (circuit breaker pattern)

### Security & Compliance

- [x] **SEC-01**: All patient data is stored with encryption at rest (column-level for PHI fields, storage-level for database)
- [x] **SEC-02**: All data transmitted between system components uses TLS 1.2 or higher
- [x] **SEC-03**: Dashboard requires authenticated login with username and password
- [x] **SEC-04**: System enforces role-based access: care coordinator (data entry), nurse navigator (alerts + resolution), administrator (pathway configuration)
- [x] **SEC-05**: All system actions (data access, alerts, resolutions, logins, failed attempts) are logged in an immutable audit trail with timestamp and user identity, retained for minimum 6 years
- [x] **SEC-06**: No PHI appears in application logs or Temporal workflow history — only opaque identifiers
- [ ] **SEC-07**: Dashboard is accessible on desktop and tablet browsers (responsive design)

### Infrastructure

- [x] **INFR-01**: System runs locally via Docker Compose (Temporal Server, PostgreSQL, Keycloak, Spring Boot app, React frontend)
- [x] **INFR-02**: System supports Spring profile switching between local and AWS deployment configurations
- [x] **INFR-03**: Patient pathway workflows are durable — they survive system restarts without losing state
- [x] **INFR-04**: Workflow engine handles patient journeys spanning weeks to months without event history overflow

## v2 Requirements

Deferred to future release. Tracked but not in current roadmap.

### Notifications

- **NOTF-01**: System sends SMS alerts to assigned nurse navigator when a deviation is detected
- **NOTF-02**: System sends follow-up SMS reminder if alert is not acknowledged within 4 business hours
- **NOTF-03**: Alert SMS includes patient name, deviation type, affected step, suggested action, and dashboard link

### EMR Integration

- **EMR-01**: System ingests patient event data automatically from practice EMR via API
- **EMR-02**: System logs alert resolutions back to EMR
- **EMR-03**: System supports HL7 FHIR data formats for interoperability

### Advanced Features

- **ADV-01**: Configurable pathway template admin UI (add/edit pathways without code deployment)
- **ADV-02**: Pathway progression timeline visualization (horizontal stepped progress bar)
- **ADV-03**: Predictive at-risk scoring based on historical deviation patterns
- **ADV-04**: Multi-practice SaaS deployment with tenant isolation
- **ADV-05**: Quality reporting dashboard with deviation trends and resolution metrics

## Out of Scope

| Feature | Reason |
|---------|--------|
| Direct patient communication (SMS, email, voice) | V1 is staff-only; no patient-facing interaction |
| Autonomous clinical actions | Non-negotiable human-in-the-loop design for clinical safety and regulatory simplicity |
| Billing, insurance, or clinical documentation | Outside the coordination problem this system solves |
| Symptom monitoring or clinical assessments | Not the practice's coordination role |
| Native mobile apps | Responsive web dashboard covers tablet/phone use cases |
| Real-time chat or messaging between staff | Out of scope for pathway monitoring tool |
| OAuth/SSO integration | Spring Security + Keycloak sufficient for V1; SSO deferred |
| Video or multimedia in patient records | Text-based event tracking sufficient for pathway monitoring |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| DATA-01 | Phase 3 | Pending |
| DATA-02 | Phase 3 | Pending |
| DATA-03 | Phase 3 | Pending |
| DATA-04 | Phase 3 | Pending |
| DATA-05 | Phase 3 | Pending |
| PATH-01 | Phase 2 | Complete |
| PATH-02 | Phase 2 | Complete |
| PATH-03 | Phase 2 | Complete |
| PATH-04 | Phase 2 | Complete |
| PATH-05 | Phase 2 | Complete |
| PATH-06 | Phase 2 | Complete |
| PATH-07 | Phase 2 | Complete |
| PATH-08 | Phase 2 | Complete |
| ALRT-01 | Phase 3 | Pending |
| ALRT-02 | Phase 3 | Pending |
| ALRT-03 | Phase 3 | Pending |
| ALRT-04 | Phase 3 | Pending |
| ALRT-05 | Phase 3 | Pending |
| ALRT-06 | Phase 3 | Pending |
| DOC-01 | Phase 4 | Complete (04-02) |
| DOC-02 | Phase 4 | Complete (04-05, 04-06) |
| DOC-03 | Phase 4 | Complete (04-03, 04-05) |
| DOC-04 | Phase 4 | Complete (04-04, 04-06) |
| DOC-05 | Phase 4 | Complete (04-04) |
| AI-01 | Phase 4 | Complete (04-04) |
| AI-02 | Phase 4 | Complete (04-03) |
| AI-03 | Phase 4 | Complete (04-03) |
| AI-04 | Phase 4 | Complete (04-03) |
| SEC-01 | Phase 1 | Pending |
| SEC-02 | Phase 1 | Complete (01-01) |
| SEC-03 | Phase 1 | Complete (01-03) |
| SEC-04 | Phase 1 | Complete (01-01) |
| SEC-05 | Phase 1 | Complete (01-03) |
| SEC-06 | Phase 1 | Complete (01-01) |
| SEC-07 | Phase 1 | Pending |
| INFR-01 | Phase 1 | Complete (01-01) |
| INFR-02 | Phase 1 | Complete (01-01) |
| INFR-03 | Phase 2 | Complete (02-02) |
| INFR-04 | Phase 2 | Complete (02-02) |

**Coverage:**
- v1 requirements: 34 total
- Mapped to phases: 34
- Unmapped: 0

---
*Requirements defined: 2026-04-29*
*Last updated: 2026-04-29 after roadmap creation — all requirements mapped*
