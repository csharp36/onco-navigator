**ONCO-NAVIGATOR AI**

Version 1 Feature Specification

*Care Pathway Sequence Monitoring --- Proof of Concept*

For use by software engineers, technical partners, and clinical advisors

Version 1.0 \| Confidential

**Purpose of This Document**

This specification describes exactly what Onco-Navigator AI Version 1 must do. It is written in plain language so it can serve as a prompt for AI coding tools, be reviewed by a software engineer or architect, and shared with a clinical advisor or technical development partner.

Version 1 is intentionally narrow. It proves one core concept: that an AI system can monitor patient charts against defined oncology care pathways, identify deviations, and alert the nurse navigator with a suggested corrective action --- without requiring the nurse to manually review every chart.

This version does not communicate with patients, does not make clinical decisions, and does not take autonomous actions in the EMR. Those capabilities are planned for future versions.

**Version 1 Scope**

**What Version 1 Does**

- Maintains a library of defined care pathways, each representing the ideal sequence of clinical events for a given cancer type and stage.

- Reads patient care event data from a data source (manual entry in PoC; EMR API in Phase 2) and compares it against the defined pathway for each patient.

- Detects three types of deviations: a required event is missing entirely, a required event has not occurred within an expected time window, or an event has occurred before its prerequisite was completed.

- Sends an alert to the assigned nurse navigator describing the deviation in plain language and suggesting a specific corrective action.

- Provides a simple nurse-facing dashboard showing all active alerts, their status (open or resolved), and a log of past alerts.

- Allows the nurse to mark an alert as resolved and record what action was taken.

**What Version 1 Does NOT Do**

- Does not connect to any EMR system automatically. Patient event data is entered manually by staff for the proof of concept.

- Does not communicate with patients. No SMS, voice calls, or patient-facing messages.

- Does not make clinical decisions or recommendations beyond suggesting a next step based on the pathway definition.

- Does not place orders, reschedule appointments, or take any actions in the EMR.

- Does not handle billing, insurance, or clinical documentation.

- Does not conduct symptom monitoring or clinical assessments.

**Users of the System**

  -------- ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- --------------- -------------------------------------------------
  **ID**   **Requirement**                                                                                                                                                                                **Priority**    **Notes**

  U-01     Nurse Navigator: Receives alerts when a deviation is detected. Reviews the suggested corrective action, contacts the physician if needed, and executes the action. Marks alerts as resolved.   **Must Have**   Primary user of Version 1

  U-02     Care Coordinator / Administrator: Enters patient data and care event records manually into the system. Manages the patient list.                                                               **Must Have**   Data entry role in PoC

  U-03     Physician / APP: May be contacted by the nurse based on an alert. Does not interact with the system directly in Version 1.                                                                     Should Have     Indirect user; direct access in future versions
  -------- ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- --------------- -------------------------------------------------

**Data the System Needs**

**Patient Record (Entered Manually by Staff)**

  -------- ------------------------------------------- --------------- --------------------------------------------------
  **ID**   **Requirement**                             **Priority**    **Notes**

  D-01     Patient first and last name                 **Must Have**   

  D-02     Date of birth                               **Must Have**   Used to confirm patient identity

  D-03     Medical record number (MRN)                 **Must Have**   Links to EMR record in future phases

  D-04     Primary diagnosis (cancer type and stage)   **Must Have**   Used to assign the correct pathway template

  D-05     Diagnosis date                              **Must Have**   Used to calculate time windows for pathway steps

  D-06     Assigned nurse navigator                    **Must Have**   Determines who receives alerts

  D-07     Treating physician name                     **Must Have**   Referenced in alert messages

  D-08     Active pathway assigned                     **Must Have**   Links patient to a specific pathway template
  -------- ------------------------------------------- --------------- --------------------------------------------------

**Care Event Record (Entered Manually by Staff)**

  -------- -------------------------------------------------------------------------------------------------------------------------------------- --------------- -----------------------------------------------
  **ID**   **Requirement**                                                                                                                        **Priority**    **Notes**

  E-01     Event type (e.g., Surgeon Consultation, Surgery, Lab Result, Pathology Report, Genomic Test, Medical Oncology Visit, Treatment Plan)   **Must Have**   Must match defined pathway step types

  E-02     Event date (date the event occurred or is scheduled)                                                                                   **Must Have**   Used for sequencing and time-window logic

  E-03     Event status: Scheduled, Completed, Cancelled, Pending                                                                                 **Must Have**   Drives deviation detection logic

  E-04     Free-text note (optional)                                                                                                              Should Have     Staff can add context to an event record

  E-05     Linked patient ID                                                                                                                      **Must Have**   Associates the event with the correct patient
  -------- -------------------------------------------------------------------------------------------------------------------------------------- --------------- -----------------------------------------------

**Pathway Template (Configured by Administrator)**

  -------- ----------------------------------------------------------------------------------------------------------------- --------------- ----------------------------------------------
  **ID**   **Requirement**                                                                                                   **Priority**    **Notes**

  P-01     Cancer type and stage this pathway applies to (e.g., Breast Cancer, Stage I-III)                                  **Must Have**   

  P-02     Ordered list of required steps, each with a step name and step type                                               **Must Have**   Defines the correct sequence

  P-03     For each step: prerequisite step(s) that must be Completed before this step can occur                             **Must Have**   Core dependency chain logic

  P-04     For each step: maximum number of days after the previous step within which this step should occur (time window)   **Must Have**   Used to detect delayed steps

  P-05     For each step: whether the step is required or optional                                                           **Must Have**   Optional steps generate warnings, not alerts

  P-06     For each step: the suggested corrective action text that will appear in the alert to the nurse                    **Must Have**   Human-readable instruction for the nurse
  -------- ----------------------------------------------------------------------------------------------------------------- --------------- ----------------------------------------------

**Core Features --- Detailed Requirements**

**Feature 1: Care Pathway Monitoring Engine**

The core function of the system. A background process runs on a scheduled interval (e.g., every hour), scans all active patient records, compares their event history against their assigned pathway template, and identifies deviations.

  -------- ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- --------------- --------------------------------------------------------
  **ID**   **Requirement**                                                                                                                                                                             **Priority**    **Notes**

  F1-01    System maintains a library of pathway templates that define the correct sequence of events for each cancer type.                                                                            **Must Have**   Pathway templates are configurable by an administrator

  F1-02    System runs a monitoring scan at a configurable interval (default: every 4 hours during business hours).                                                                                    **Must Have**   Can be run manually at any time by staff

  F1-03    For each active patient, the system checks whether each required pathway step has an associated care event record in Completed status.                                                      **Must Have**   Core missing-event detection

  F1-04    For each pathway step, the system checks whether any prerequisite steps are in Completed status before the current step is marked as occurring.                                             **Must Have**   Core out-of-order detection

  F1-05    For each pathway step, the system checks whether the time elapsed since the previous step exceeds the configured time window for that step.                                                 **Must Have**   Core delayed-step detection

  F1-06    When a deviation is detected, the system creates an alert record with: patient name, deviation type, step name, description, and suggested corrective action.                               **Must Have**   See Feature 2 for alert delivery

  F1-07    The system does not create duplicate alerts for the same deviation. If an alert for a given patient and step is already open, no new alert is created until the existing one is resolved.   **Must Have**   Prevents alert fatigue

  F1-08    The system logs every monitoring scan, including timestamp, number of patients scanned, and number of new alerts generated.                                                                 **Must Have**   Audit trail
  -------- ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- --------------- --------------------------------------------------------

**Feature 2: Nurse Alert Delivery**

When a deviation is detected, the system sends an alert to the assigned nurse navigator. The alert is delivered in two ways: as an entry in the dashboard and as an SMS text message.

  -------- -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- --------------- -------------------------------------------------------------------------------------------------------------------------------------------------------------------
  **ID**   **Requirement**                                                                                                                                                            **Priority**    **Notes**

  F2-01    When a new alert is created, the system sends an SMS text message to the assigned nurse navigator.                                                                         **Must Have**   Nurse does not need to be logged in to receive urgent alerts

  F2-02    Alert SMS includes: patient name, deviation type (missing step, delayed step, or out-of-order step), the name of the affected pathway step, and a link to the dashboard.   **Must Have**   

  F2-03    Alert SMS includes the suggested corrective action as defined in the pathway template for that step.                                                                       **Must Have**   Plain-language instruction for the nurse

  F2-04    Each alert is also visible on the nurse dashboard as an open alert card.                                                                                                   **Must Have**   See Feature 3 for dashboard details

  F2-05    If an alert has not been acknowledged within 4 business hours, the system sends a follow-up SMS reminder to the nurse.                                                     Should Have     Prevents alerts from being missed

  F2-06    Alert text is written in plain language and avoids clinical abbreviations. The tone is informational, not alarming.                                                        **Must Have**   Example: \'Oncotype DX has not been ordered for \[Patient Name\] following surgery on \[Date\]. Suggested action: Contact surgical team to verify order status.\'
  -------- -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- --------------- -------------------------------------------------------------------------------------------------------------------------------------------------------------------

**Feature 3: Nurse Dashboard**

A simple web-based screen where nurses can view all active alerts, review patient pathway status, and record their actions.

  -------- ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------- --------------- ---------------------------------------------
  **ID**   **Requirement**                                                                                                                                                              **Priority**    **Notes**

  F3-01    Dashboard displays all open alerts, sorted by severity (overdue steps first, then missing steps, then sequence violations).                                                  **Must Have**   

  F3-02    Each alert card shows: patient name, MRN, alert type, affected pathway step, deviation description, suggested corrective action, and time elapsed since alert was created.   **Must Have**   

  F3-03    Nurse can click on any alert to view the patient\'s full pathway status, showing all steps and their current status.                                                         **Must Have**   Provides full context before the nurse acts

  F3-04    Nurse can mark an alert as Resolved and enter a free-text note describing what action was taken.                                                                             **Must Have**   Creates accountability and audit trail

  F3-05    Dashboard shows a count of open alerts at the top, always visible.                                                                                                           **Must Have**   

  F3-06    Dashboard shows a list of all patients, their assigned pathway, and a summary status (On Track, Alert Active, Resolved).                                                     **Must Have**   

  F3-07    Nurse can manually trigger a re-scan for a specific patient at any time.                                                                                                     Should Have     Useful after entering new event data

  F3-08    Dashboard requires login with username and password.                                                                                                                         **Must Have**   HIPAA access control requirement

  F3-09    Dashboard is accessible on desktop browser and mobile browser (tablet and phone).                                                                                            **Must Have**   Nurses may check from the floor on a tablet
  -------- ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------- --------------- ---------------------------------------------

**Feature 4: Patient and Event Data Entry**

In Version 1, all patient data and care event records are entered manually by the care coordinator. There is no automatic EMR connection.

  -------- ----------------------------------------------------------------------------------------------------------------------------------------- --------------- -------------------------------------------------
  **ID**   **Requirement**                                                                                                                           **Priority**    **Notes**

  F4-01    Staff can add a new patient through a simple form: name, DOB, MRN, diagnosis, diagnosis date, treating physician, and assigned pathway.   **Must Have**   

  F4-02    Staff can add a care event to a patient record: event type, date, and status.                                                             **Must Have**   This is how the system learns what has happened

  F4-03    Staff can update the status of an existing care event (e.g., from Scheduled to Completed).                                                **Must Have**   

  F4-04    Staff can deactivate a patient record (e.g., patient deceased or transferred).                                                            **Must Have**   Prevents alerts for inactive patients

  F4-05    All data entry actions are logged with the staff member\'s name and a timestamp.                                                          **Must Have**   Audit trail
  -------- ----------------------------------------------------------------------------------------------------------------------------------------- --------------- -------------------------------------------------

**Pathway Definitions for Version 1**

Version 1 will include the following pathway templates as a starting point. These definitions will be reviewed and refined with the pilot practice clinical team before deployment. Additional pathways will be added in subsequent versions.

+-------------------+-------------------------------+---------------------------------------------------------+----------------------------------------------------------------------------+------------------------------------------------------------------------------------------------------------------+
| **PATHWAY 1: NEWLY DIAGNOSED BREAST CANCER (Stage I--III)**                                                                                                                                                                                                                                                 |
+-------------------+-------------------------------+---------------------------------------------------------+----------------------------------------------------------------------------+------------------------------------------------------------------------------------------------------------------+
| **Step**          | **Event Name**                | **Description**                                         | **Trigger Condition**                                                      | **Alert to Nurse**                                                                                               |
+-------------------+-------------------------------+---------------------------------------------------------+----------------------------------------------------------------------------+------------------------------------------------------------------------------------------------------------------+
| 1                 | Surgeon Consultation          | Patient meets with surgical oncologist.                 | No surgeon visit found within 14 days of diagnosis date.                   | No surgeon visit found. Suggest: Schedule surgical oncology consultation.                                        |
+-------------------+-------------------------------+---------------------------------------------------------+----------------------------------------------------------------------------+------------------------------------------------------------------------------------------------------------------+
| 2                 | Surgery                       | Surgical procedure performed; specimen obtained.        | Surgery not scheduled or completed within 30 days of surgeon consult.      | Surgery not yet completed. Suggest: Follow up with surgical team on scheduling.                                  |
+-------------------+-------------------------------+---------------------------------------------------------+----------------------------------------------------------------------------+------------------------------------------------------------------------------------------------------------------+
| 3                 | Pathology Report              | Final pathology confirms diagnosis and receptor status. | No pathology report within 21 days of surgery date.                        | No pathology report found after surgery. Suggest: Contact pathology department.                                  |
+-------------------+-------------------------------+---------------------------------------------------------+----------------------------------------------------------------------------+------------------------------------------------------------------------------------------------------------------+
| 4                 | Genomic Testing (Oncotype DX) | Tumor specimen sent for genomic analysis.               | Oncotype DX not ordered within 14 days of pathology report.                | Oncotype DX not ordered. Suggest: Review with treating physician to confirm order.                               |
+-------------------+-------------------------------+---------------------------------------------------------+----------------------------------------------------------------------------+------------------------------------------------------------------------------------------------------------------+
| 5                 | Medical Oncology Visit        | Patient meets with medical oncologist.                  | Med onc visit is scheduled but Oncotype result is not in Completed status. | Oncotype result not available before medical oncology visit. Suggest: Confirm result timing or reschedule visit. |
+-------------------+-------------------------------+---------------------------------------------------------+----------------------------------------------------------------------------+------------------------------------------------------------------------------------------------------------------+
| 6                 | Treatment Plan Established    | Chemo, hormone therapy, or observation plan documented. | No treatment plan documented within 14 days of medical oncology visit.     | No treatment plan found. Suggest: Follow up with medical oncologist.                                             |
+-------------------+-------------------------------+---------------------------------------------------------+----------------------------------------------------------------------------+------------------------------------------------------------------------------------------------------------------+

+-------------------+--------------------------------------+---------------------------------------------------------------------------------+----------------------------------------------------------------------+--------------------------------------------------------------------------------------------+
| **PATHWAY 2: NEWLY DIAGNOSED LUNG CANCER (Stage I--III)**                                                                                                                                                                                                                                                      |
+-------------------+--------------------------------------+---------------------------------------------------------------------------------+----------------------------------------------------------------------+--------------------------------------------------------------------------------------------+
| **Step**          | **Event Name**                       | **Description**                                                                 | **Trigger Condition**                                                | **Alert to Nurse**                                                                         |
+-------------------+--------------------------------------+---------------------------------------------------------------------------------+----------------------------------------------------------------------+--------------------------------------------------------------------------------------------+
| 1                 | Pulmonology / Thoracic Consultation  | Initial evaluation by pulmonologist or thoracic surgeon.                        | No consultation found within 14 days of diagnosis.                   | No thoracic consultation found. Suggest: Schedule thoracic surgery or pulmonology consult. |
+-------------------+--------------------------------------+---------------------------------------------------------------------------------+----------------------------------------------------------------------+--------------------------------------------------------------------------------------------+
| 2                 | Staging Imaging (CT/PET)             | CT chest and/or PET scan to determine staging.                                  | No staging imaging within 21 days of diagnosis.                      | Staging imaging not completed. Suggest: Confirm imaging order with treating team.          |
+-------------------+--------------------------------------+---------------------------------------------------------------------------------+----------------------------------------------------------------------+--------------------------------------------------------------------------------------------+
| 3                 | Molecular / Biomarker Testing        | Tumor tissue sent for EGFR, ALK, PD-L1, and other biomarkers.                   | Biomarker testing not ordered within 14 days of biopsy confirmation. | Biomarker testing not ordered. Suggest: Review with treating physician.                    |
+-------------------+--------------------------------------+---------------------------------------------------------------------------------+----------------------------------------------------------------------+--------------------------------------------------------------------------------------------+
| 4                 | Multidisciplinary Tumor Board Review | Case presented to tumor board for treatment recommendation.                     | No tumor board entry within 30 days of diagnosis.                    | Tumor board review not documented. Suggest: Schedule case presentation.                    |
+-------------------+--------------------------------------+---------------------------------------------------------------------------------+----------------------------------------------------------------------+--------------------------------------------------------------------------------------------+
| 5                 | Medical Oncology Visit               | Patient meets with medical oncologist to discuss systemic therapy.              | Med onc visit not scheduled within 14 days of tumor board review.    | Medical oncology visit not scheduled. Suggest: Schedule consultation.                      |
+-------------------+--------------------------------------+---------------------------------------------------------------------------------+----------------------------------------------------------------------+--------------------------------------------------------------------------------------------+
| 6                 | Treatment Plan Established           | Treatment plan (chemo, targeted therapy, immunotherapy, or surgery) documented. | No treatment plan within 14 days of medical oncology visit.          | No treatment plan found. Suggest: Follow up with medical oncologist.                       |
+-------------------+--------------------------------------+---------------------------------------------------------------------------------+----------------------------------------------------------------------+--------------------------------------------------------------------------------------------+

+-------------------+--------------------------------------------------+---------------------------------------------------------------------------------+-------------------------------------------------------------+----------------------------------------------------------------------------------------------+
| **PATHWAY 3: NEWLY DIAGNOSED COLORECTAL CANCER (Stage I--III)**                                                                                                                                                                                                                                                     |
+-------------------+--------------------------------------------------+---------------------------------------------------------------------------------+-------------------------------------------------------------+----------------------------------------------------------------------------------------------+
| **Step**          | **Event Name**                                   | **Description**                                                                 | **Trigger Condition**                                       | **Alert to Nurse**                                                                           |
+-------------------+--------------------------------------------------+---------------------------------------------------------------------------------+-------------------------------------------------------------+----------------------------------------------------------------------------------------------+
| 1                 | Surgical Oncology Consultation                   | Patient meets with colorectal or surgical oncologist.                           | No surgical consult within 14 days of diagnosis.            | No surgical consultation found. Suggest: Schedule surgical oncology consult.                 |
+-------------------+--------------------------------------------------+---------------------------------------------------------------------------------+-------------------------------------------------------------+----------------------------------------------------------------------------------------------+
| 2                 | Complete Staging Workup (CT abdomen/pelvis, CEA) | Imaging and labs to confirm staging before surgery.                             | No staging imaging within 21 days of diagnosis.             | Staging workup not completed. Suggest: Confirm orders with treating team.                    |
+-------------------+--------------------------------------------------+---------------------------------------------------------------------------------+-------------------------------------------------------------+----------------------------------------------------------------------------------------------+
| 3                 | Surgery (Resection)                              | Surgical resection performed.                                                   | Surgery not completed within 45 days of diagnosis.          | Surgery not yet performed. Suggest: Follow up with surgical team on scheduling.              |
+-------------------+--------------------------------------------------+---------------------------------------------------------------------------------+-------------------------------------------------------------+----------------------------------------------------------------------------------------------+
| 4                 | Pathology and MSI/MMR Testing                    | Pathology confirms margins; microsatellite instability (MSI) testing performed. | No pathology or MSI result within 21 days of surgery.       | Pathology or MSI not documented. Suggest: Contact pathology.                                 |
+-------------------+--------------------------------------------------+---------------------------------------------------------------------------------+-------------------------------------------------------------+----------------------------------------------------------------------------------------------+
| 5                 | Medical Oncology Visit                           | Patient meets with medical oncologist to discuss adjuvant chemotherapy.         | Med onc visit not scheduled within 21 days of surgery.      | Medical oncology visit not scheduled. Suggest: Schedule post-surgical oncology consultation. |
+-------------------+--------------------------------------------------+---------------------------------------------------------------------------------+-------------------------------------------------------------+----------------------------------------------------------------------------------------------+
| 6                 | Treatment Plan Established                       | Adjuvant chemotherapy plan or observation plan documented.                      | No treatment plan within 14 days of medical oncology visit. | No treatment plan found. Suggest: Follow up with medical oncologist.                         |
+-------------------+--------------------------------------------------+---------------------------------------------------------------------------------+-------------------------------------------------------------+----------------------------------------------------------------------------------------------+

**Example Alert Scenarios**

These scenarios describe how the system behaves in real situations. They can be used as test cases by the engineering team.

  -----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
  **SCENARIO A: Missing Genomic Test**

  Patient: Maria, 58, Stage II breast cancer. Surgery completed 3 weeks ago. Pathology report is in the system marked Completed.

  Monitoring scan runs at 8:00 AM.

  System checks Pathway Step 4 (Oncotype DX): prerequisite (pathology) is Completed. Time since pathology: 21 days. Time window configured: 14 days. No Oncotype DX event found.

  System creates alert: Type = Missing Step. Description = \'Oncotype DX has not been ordered for Maria \[Last Name\]. Pathology report was completed 21 days ago, which exceeds the 14-day recommended window.\'

  Suggested action in alert: \'Confirm Oncotype DX order status with surgical team. If not ordered, request order from treating physician.\'

  Alert SMS sent to assigned nurse navigator. Alert visible on dashboard as OPEN.

  Nurse contacts surgeon's office. Confirms Oncotype was ordered but not yet logged. Nurse adds event record. Marks alert as Resolved with note: 'Confirmed ordered 3 days ago. Added to system.'
  -----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------

  --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
  **SCENARIO B: Medical Oncology Visit Scheduled Before Genomic Test Complete**

  Patient: Robert, 62, Stage II breast cancer. Oncotype DX ordered but not yet resulted. Medical oncology visit scheduled for next Tuesday.

  Monitoring scan detects: Step 5 (Medical Oncology Visit) is Scheduled, but Step 4 (Oncotype DX) is in Pending status, not Completed.

  System creates alert: Type = Out-of-Order Step. Description = \'Medical oncology visit for Robert \[Last Name\] is scheduled for \[Date\], but the Oncotype DX result is not yet marked as complete.\'

  Suggested action: \'Confirm Oncotype DX result timing. If result will not be available before the visit, consider rescheduling the medical oncology visit to avoid a wasted appointment.\'

  Alert SMS sent to nurse. Nurse calls the genomic testing lab. Result expected in 4 days. Nurse contacts medical oncology scheduler. Visit rescheduled by one week. Alert marked Resolved.
  --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------

  --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
  **SCENARIO C: Delayed Treatment Plan**

  Patient: James, 70, Stage III lung cancer. Medical oncology visit was completed 18 days ago. No treatment plan event has been entered.

  Monitoring scan detects: Step 6 (Treatment Plan Established) time window is 14 days from Step 5 (Medical Oncology Visit). Elapsed time: 18 days. No Treatment Plan event found.

  System creates alert: Type = Delayed Step. Description = \'No treatment plan has been documented for James \[Last Name\]. Medical oncology visit occurred 18 days ago, which exceeds the 14-day expected window.\'

  Suggested action: \'Contact medical oncologist to confirm treatment plan status and document outcome in the system.\'

  Nurse calls medical oncologist's office. Learns the plan was decided at the visit but not yet entered. Nurse enters the treatment plan event. Alert resolved.
  --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------

**Security and Compliance Requirements**

  -------- ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- --------------- ----------------------------------------------------------------
  **ID**   **Requirement**                                                                                                                                                                 **Priority**    **Notes**

  S-01     All patient data must be stored in a HIPAA-compliant cloud environment.                                                                                                         **Must Have**   Recommended: AWS with HIPAA BAA, or Azure Health Data Services

  S-02     All data transmitted between system components must use encrypted channels (TLS 1.2 or higher).                                                                                 **Must Have**   

  S-03     Staff dashboard must require authenticated login. No patient data visible without login.                                                                                        **Must Have**   

  S-04     All system actions (scans, alerts, data entry, resolutions) must be logged with timestamp and user identity and stored for a minimum of 6 years.                                **Must Have**   HIPAA audit trail requirement

  S-05     Patient data must never be used to train AI models without explicit patient consent.                                                                                            **Must Have**   

  S-06     Role-based access: Care coordinator can enter data. Nurse can view alerts and resolve them. Administrator can configure pathways. No role can access data beyond their scope.   **Must Have**   
  -------- ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- --------------- ----------------------------------------------------------------

**Recommended Technology Components**

This section is written for the software engineer or AI coding tool that will build Version 1.

  -------- --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- --------------- -------------------------------------------------------------------------------------------
  **ID**   **Requirement**                                                                                                                                                                   **Priority**    **Notes**

  T-01     Backend and database: A standard web framework with a relational database. Patient records, event records, pathway templates, alerts, and audit logs are stored here.             **Must Have**   Examples: Node.js + PostgreSQL, or Python (FastAPI or Django) + PostgreSQL

  T-02     Pathway monitoring engine: A background scheduler process that runs on a timer, loads all active patients, evaluates each against their pathway template, and generates alerts.   **Must Have**   This is the core 'brain' of the system. Examples: cron job, Celery (Python), or node-cron

  T-03     SMS alert delivery: Use Twilio or AWS SNS to send text message alerts to nurse navigators.                                                                                        **Must Have**   Twilio recommended for ease of setup

  T-04     Nurse dashboard frontend: A simple web application that displays alerts and patient pathway status.                                                                               **Must Have**   React or plain HTML/CSS/JS. Must be mobile-responsive.

  T-05     Authentication: Username/password login for all staff users. Session tokens stored securely.                                                                                      **Must Have**   

  T-06     Cloud hosting: Deploy on AWS, Google Cloud, or Azure with a signed HIPAA Business Associate Agreement (BAA).                                                                      **Must Have**   BAA is a legal requirement before storing any PHI in the cloud

  T-07     AI language model (optional for PoC): Used to generate natural-language alert descriptions and suggested actions dynamically, rather than using fixed template strings.           Nice to Have    Can use Claude API or GPT-4o. Adds flexibility for non-standard deviations.
  -------- --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- --------------- -------------------------------------------------------------------------------------------

**Version 1 Success Metrics**

These measurements determine whether the proof of concept is working and whether the system is providing value to the pilot practice. They should be tracked from day one of deployment.

  --------------------------------------- ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
  **Alert accuracy**                      Percentage of alerts generated by the system that are confirmed as valid deviations by the nurse navigator. Target: \>80%. A high false-positive rate indicates pathway template logic needs tuning.

  **Alert resolution time**               Average time between an alert being sent and a nurse marking it resolved. Target: under 4 hours during business hours.

  **Deviation detection rate**            Number of pathway deviations detected by the system per week. Baseline to be measured manually before deployment to confirm the system is catching real issues.

  **Nurse chart-review time displaced**   Estimated hours per week that nurses previously spent manually checking charts for pathway gaps, reduced by use of the system. Measured by staff self-report.

  **Pathway adherence rate**              Percentage of patients who complete each pathway step within the configured time window. Tracked before and after deployment to measure clinical improvement.

  **System uptime**                       The monitoring engine must be available and running during all business hours. Target: 99% uptime during business hours.
  --------------------------------------- ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------

*CONFIDENTIAL --- FOR DISCUSSION PURPOSES ONLY*
