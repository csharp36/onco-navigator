# Pathway Template Clinical Review

## How to Use This Document

This document contains questions about the clinical pathway templates built into Onco-Navigator AI. Your answers will directly determine what the system monitors and what alerts it sends to nurses.

**How to fill this out:**

1. Open this file in any text editor (TextEdit, Notepad, Word, Google Docs — whatever you prefer)
2. Scroll to each question — they are numbered with IDs like PW-BR-001
3. Each question has a line that says **YOUR ANSWER:** followed by an empty space
4. Type your answer directly after **YOUR ANSWER:** on that same line or the lines below it
5. If you are asked to pick an option (A, B, C, etc.), type the letter of your choice
6. If you want to write more detail, use the **ADDITIONAL NOTES:** area below the answer line
7. When you are done, save the file and send it back

**You do not need to answer every question in one sitting.** Save your progress and come back to it. If a question does not make sense, write "UNSURE" and we will discuss it.

**Here is an example of a completed answer:**

---

> **PW-EXAMPLE-001: Is chocolate ice cream better than vanilla?**
>
> CONTEXT: We need to pick a default ice cream flavor for the office freezer.
>
> OPTIONS:
> - (A) Chocolate
> - (B) Vanilla
> - (C) Both — stock both flavors
>
> YOUR ANSWER: C
>
> ADDITIONAL NOTES: Most people like one or the other so stock both. Also add mint chip.

---

**Important:** Wherever you see tables showing "What Was Built," those describe what the system does RIGHT NOW. Your job is to tell us whether that is clinically correct, and if not, what should change.

**Companion documents** (converted from your original Word files — open them for reference if needed):
- Onco-Navigator AI - Concept Brief v3.md
- Onco-Navigator AI - V1 Feature Specification v2.md

---
---

# DECISIONS

---

## Cross-Cutting Decisions (Apply to All Pathways)

These decisions affect the entire system, not just one cancer type.

---

### PW-ALL-001: Which concept brief capabilities should be in the pilot?

CONTEXT: Your concept brief described several monitoring capabilities that are NOT in the current build. We simplified for V1. Below is a list of what was cut. For each one, tell us whether it should be added for the pilot or is OK to defer to a future version.

For each capability, write PILOT (add it now) or DEFER (save it for later):

(a) Track whether outside results (pathology, imaging, genomic tests) are in the chart BEFORE a scheduled visit — to prevent "wasted visits" where the doctor cannot make a decision:

YOUR ANSWER: PILOT

(b) ECHO and port placement tracking as prerequisites before chemotherapy can start — to prevent last-minute chemo cancellations:

YOUR ANSWER: DEFER

(c) Scheduling confirmations from outside facilities — confirming that an outside facility has actually scheduled a procedure within 5-7 days of referral:

YOUR ANSWER: PILOT

(d) Referral tracking — monitoring that a referral was received and prerequisite records (like mammogram reports) arrived with it:

YOUR ANSWER: PILOT

(e) Escalation timing — urgent alerts when a prerequisite is not completed within 48 hours of a scheduled event (like chemo start):

YOUR ANSWER: PILOT

ADDITIONAL NOTES:


---

### PW-ALL-002: Should we add new event types?

CONTEXT: The system currently tracks 12 types of care events (listed in the Reference section at the bottom of this document). Some things mentioned in your concept brief do not have their own type and are grouped under generic categories. For each one below, tell us ADD (create a new type) or KEEP (the current grouping is fine):

(a) IMMUNOTHERAPY — currently there is no way to distinguish immunotherapy from chemotherapy. Your lung pathway mentions immunotherapy as a treatment option.

YOUR ANSWER: KEEP

(b) TUMOR_BOARD — currently tracked as a generic CONSULTATION, same as a regular doctor visit. Your concept brief and lung pathway treat tumor board review as a distinct event.

YOUR ANSWER: KEEP

(c) PORT_PLACEMENT — currently not trackable at all. Your concept brief described port placement as a chemo prerequisite.

YOUR ANSWER: KEEP

(d) CARDIAC_ECHO — currently not trackable at all. Your concept brief described cardiac ECHO as a chemo prerequisite.

YOUR ANSWER: KEEP

(e) Any other event types you think are missing?

YOUR ANSWER: YES.  EVENTS SHOULD BE EXTRACTED FROM PDF FILES IN THE EMR AND FOMR THE ACTIVE ORDER FROM THE EMR, REGARDLESS OD WHAT THE EVENT IS.

ADDITIONAL NOTES:
There is significant variability in the seuquence of events that need to happen.  There is no standard sequence for a type of cancer. Each patient will need a unique sequence of events.  The souce of what the events are will be a specific data found in different documents in the EMR.  This will be the MD notes, orders and nurse notes.  AI will extract and interpret these data to understabd what needs to be done in the sequence of events.  It may also have the medical literature and the steps that every patient should have and may help monitor for those as well, but this is not the primary goal of this initial system

---

### PW-ALL-003: Should we track event status beyond "Completed"?

CONTEXT: Right now, the system only knows whether a care event has been COMPLETED. Your feature spec defined four statuses: Scheduled, Completed, Cancelled, and Pending. Without these additional statuses, the system CANNOT do the following:

- Detect that a visit is SCHEDULED but a prerequisite is still PENDING (your Scenario B from the feature spec — a med onc visit is on the calendar but the Oncotype DX result has not come back yet)
- Detect that a procedure was CANCELLED and needs to be rescheduled
- Distinguish between "this hasn't happened yet" and "this is scheduled for next week"

Should we add Scheduled, Pending, and Cancelled statuses for the pilot?

OPTIONS:
- (A) Yes — these statuses are needed for the pilot to be clinically useful
- (B) No — knowing whether something is Completed or not is sufficient for the pilot
- (C) Add Scheduled only — that is the most important one

YOUR ANSWER: A

ADDITIONAL NOTES:
The system should create a classification with the above terms. This is how it will determine the best corrective action
If system detects schedule and pending. it should request a estimated date of when it shoud be completed and then start monitoring at that time.
If a event is canceled it needs to trigger corrective action
- Distinguish between "this hasn't happened yet" and "this is scheduled for next week". This is important because the system will have different corrective actions for these 2 events



---

### PW-ALL-004: Are SMS alerts needed for the pilot?

CONTEXT: Your feature spec listed SMS text message alerts as "Must Have." The current build only shows alerts on the dashboard — nurses must log in to see them. No text messages are sent.

OPTIONS:
- (A) SMS is required for the pilot — nurses will not check the dashboard often enough
- (B) Dashboard-only is acceptable for the pilot — the practice can test with dashboard alerts first
- (C) SMS is preferred but not a blocker — pilot can start without it

YOUR ANSWER: B

ADDITIONAL NOTES:
At the end product only admin will have access to the dashboard in order to monitor and troubleshoot the system.  Users will receive teams or email notifications only.


---

### PW-ALL-005: Should pathways filter by cancer stage?

CONTEXT: Your feature spec says pathways apply to "Stage I-III" for all three cancer types. The current build assigns a pathway based on cancer type only — there is no stage field. A Stage IV patient would get the same pathway as a Stage I patient.

OPTIONS:
- (A) Stage filtering is needed — Stage IV patients have different pathways and should not get Stage I-III alerts
- (B) Stage filtering is not needed for the pilot — the pilot practice will only enroll appropriate patients manually
- (C) Add a stage field to the patient record but do not filter pathways yet — just capture the data

YOUR ANSWER: B

ADDITIONAL NOTES: Stage is not needed.  All patients require a unique sequence of events which will be extracted from MD notes, nurse notes and orders.


---

### PW-ALL-006: Should certain steps always be high severity?

CONTEXT: The system currently assigns alert severity based on the TYPE of deviation:
- MISSING (highest) — a required step has no completed event and the time window expired
- OVERDUE — the step exists but took longer than expected
- OUT OF ORDER — a step was completed before its prerequisites

This means a missing pathology report and a missing follow-up visit get the same severity. Should certain steps always be treated as higher priority regardless of deviation type?

YOUR ANSWER (list any steps that should always be high priority, or write "current system is fine"):
current system is fine

ADDITIONAL NOTES:


---

### PW-ALL-007: Are the suggested actions specific enough?

CONTEXT: When the system alerts a nurse about a deviation, it includes a "suggested action" — a short instruction for what the nurse should do. Here are some examples of what the system currently says:

- "Contact pathology department."
- "Follow up with surgical team on scheduling."
- "Review with treating physician."
- "Schedule consultation."

Are these specific enough for a nurse to act on? Or should they be more detailed, such as "Call the pathology lab at the facility where surgery was performed and request the report be faxed to our office"?

OPTIONS:
- (A) Current suggested actions are specific enough — nurses will know what to do
- (B) They need to be more specific — please rewrite them (use the space below)
- (C) They are fine for the pilot but should be improved before full deployment

YOUR ANSWER: B

If you answered B, please write improved suggested actions for any steps you think need them (reference the step by pathway and number, e.g., "Breast Step 3"):


ADDITIONAL NOTES:
The communication that the system will give to the nurse should have 2 parts. 1) What is missing and 2)a suggeted action in no more then 150 characters

---

### PW-ALL-008: Should optional steps generate warnings instead of alerts?

CONTEXT: Your feature spec (requirement P-05) says optional steps should generate warnings, not full alerts. Currently, every step in every pathway is marked as REQUIRED, and there is no distinction between warnings and alerts. Are there any steps in any pathway that should be optional?

YOUR ANSWER (list any steps that should be optional, or write "all steps should be required"):
all steps should be required

ADDITIONAL NOTES:


---
---

## Breast Cancer Pathway Decisions

---

### PW-BR-001: Is the 6-step pathway adequate, or should we restore cut steps?

CONTEXT: Your concept brief described an 8-step breast cancer pathway. For V1, we simplified it to 6 steps by cutting:

- Steps 1-4 from the concept brief (referral from PCP, first med onc visit, biopsy at outside radiology, second visit with diagnosis confirmation) — the V1 pathway starts at surgeon consultation, AFTER diagnosis is confirmed
- Step 8 from the concept brief (chemotherapy planning visit with ECHO, port placement, and chemo start prerequisites)

The 6 steps currently in the system are: Surgeon Consultation, Surgery, Pathology Report, Genomic Testing (Oncotype DX), Medical Oncology Visit, Treatment Plan Established.

OPTIONS:
- (A) The 6-step version is adequate for the pilot
- (B) We need to add back the pre-diagnosis steps (referral, first visit, biopsy, diagnosis confirmation)
- (C) We need to add the chemo prep steps (ECHO, port placement, chemo start)
- (D) We need both — restore closer to the original 8-step pathway
- (E) The 6 steps are wrong in a different way (explain below)

YOUR ANSWER: E

ADDITIONAL NOTES: The steps should be extracted from the MD notes, nurse notes and orders and not from a pre-defined general list of events.


---

### PW-BR-002: Should "Oncotype DX" be renamed to something more generic?

CONTEXT: Step 4 is called "Genomic Testing (Oncotype DX)." Oncotype DX is one specific genomic test. Other tests that serve a similar purpose include MammaPrint, EndoPredict, and Prosigna. Is it correct to name this step after Oncotype DX specifically, or should it be more generic?

OPTIONS:
- (A) Keep "Oncotype DX" — it is the standard test at the pilot practice
- (B) Rename to "Genomic Assay" or similar — the specific test should not be hard-coded
- (C) Something else (write below)

YOUR ANSWER:A

ADDITIONAL NOTES:


---

### PW-BR-003: Can we use a single linear pathway, or does neoadjuvant chemo require a branch?

CONTEXT: The current pathway assumes surgery happens BEFORE chemotherapy (the standard adjuvant sequence). But some breast cancer patients receive neoadjuvant chemotherapy BEFORE surgery, which reverses the step order. The system currently supports only linear sequences — no branching based on treatment approach.

OPTIONS:
- (A) A single linear pathway (surgery first) is acceptable for the pilot — neoadjuvant cases are uncommon enough to handle manually
- (B) We need a separate pathway for neoadjuvant cases — it is clinically wrong to alert on surgery timing when chemo is planned first
- (C) One pathway is fine, but some steps should be marked OPTIONAL so they do not generate false alerts for neoadjuvant patients

YOUR ANSWER: A, B and C are incorrect

ADDITIONAL NOTES: The steps should be extracted from the MD notes, nurse notes and orders and not from a pre-defined general list of events.


---

### PW-BR-004: Validate time windows

CONTEXT: Below are the time windows currently built into the breast pathway. For each one, write OK if correct, or write the number of days it SHOULD be.

- Diagnosis to surgeon consultation: currently 14 days.  YOUR ANSWER: OK
- Surgeon consultation to surgery: currently 30 days.  YOUR ANSWER: N/A
- Surgery to pathology report: currently 21 days.  YOUR ANSWER: 7 DAYS
- Pathology report to Oncotype DX order: currently 14 days.  YOUR ANSWER: N/A
- Oncotype DX result to medical oncology visit: currently 14 days.  YOUR ANSWER: 7 DAYS
- Medical oncology visit to treatment plan: currently 14 days.  YOUR ANSWER: N/A

ADDITIONAL NOTES:


---

### PW-BR-005: Validate alert text and suggested actions

CONTEXT: Below is the alert text and suggested action for each step. For each one, write OK if correct, or write what it SHOULD say.

Step 1 — Alert: "No surgeon visit found within 14 days of diagnosis date."
Step 1 — Action: "Schedule surgical oncology consultation."
YOUR ANSWER:
Action: " Contact the surgeons office to request the date of surgery then contact the patient to inform"

Step 2 — Alert: "Surgery not yet completed within 30 days of surgeon consult."
Step 2 — Action: "Follow up with surgical team on scheduling."
YOUR ANSWER: OK

Step 3 — Alert: "No pathology report found within 21 days of surgery date."
Step 3 — Action: "Contact pathology department."
YOUR ANSWER: OK

Step 4 — Alert: "Oncotype DX not ordered within 14 days of pathology report."
Step 4 — Action: "Review with treating physician to confirm order."
YOUR ANSWER:

Step 5 — Alert: "Oncotype result not available before medical oncology visit."
Step 5 — Action: "Confirm Oncotype DX result timing. If result will not be available before the visit, consider rescheduling the medical oncology visit to avoid a wasted appointment."
YOUR ANSWER: Oncotype Dx not resulted will trigger a message to the doctor 2 days after the MD visit.

Step 6 — Alert: "No treatment plan documented within 14 days of medical oncology visit."
Step 6 — Action: "Follow up with medical oncologist."
YOUR ANSWER: OK

ADDITIONAL NOTES:


---
---

## Lung Cancer Pathway Decisions

---

### PW-LU-001: What is the correct anchor for biomarker testing?

CONTEXT: This is a bug we found. Step 3 (Molecular / Biomarker Testing) has conflicting information:

- The ALERT TEXT says: "Biomarker testing not ordered within 14 days of **biopsy confirmation**"
- But the system actually measures the 14-day window from **staging imaging** (Step 2)
- There is NO biopsy step in the lung pathway at all
- The prerequisite is Step 1 (thoracic consultation), not biopsy or imaging

We need you to tell us: what should the 14-day clock start from?

OPTIONS:
- (A) 14 days from thoracic consultation (Step 1)
- (B) 14 days from staging imaging (Step 2)
- (C) 14 days from biopsy — and we need to ADD a biopsy step to the pathway
- (D) 14 days from diagnosis date
- (E) Something else (explain below)

YOUR ANSWER: E

ADDITIONAL NOTES: From the creating of the first note of MD


---

### PW-LU-002: Should biopsy be an explicit step in the lung pathway?

CONTEXT: The feature spec's trigger condition for biomarker testing says "14 days of biopsy confirmation," which implies a biopsy happens somewhere in the lung cancer workup. But there is no biopsy step in the pathway. Is biopsy assumed to have already happened before "diagnosis" (meaning the patient was already biopsied before entering the system)? Or should we add a biopsy step?

OPTIONS:
- (A) Biopsy happens before diagnosis — do NOT add it as a step
- (B) Biopsy should be an explicit step — add it to the pathway (tell us where in the sequence)
- (C) It depends on the case — sometimes biopsy is pre-diagnosis, sometimes post

YOUR ANSWER:

If you answered B, where should biopsy go in the sequence (before which step)?

ADDITIONAL NOTES:


---

### PW-LU-003: Are steps 1 and 2 correctly parallel?

CONTEXT: Steps 1 (thoracic consultation) and 2 (staging imaging) both start their clock from DIAGNOSIS DATE with no dependency on each other. This means they are expected to happen at the same time, in parallel. Is this clinically correct?

OPTIONS:
- (A) Yes — thoracic consultation and staging imaging happen in parallel after diagnosis
- (B) No — staging imaging should happen AFTER the thoracic consultation (make Step 1 a prerequisite for Step 2)
- (C) No — the order should be reversed (imaging first, then consultation)
- (D) Something else (explain below)

YOUR ANSWER:

ADDITIONAL NOTES:


---

### PW-LU-004: Is 30 days from diagnosis to tumor board realistic?

CONTEXT: Step 4 (Multidisciplinary Tumor Board Review) has a 30-day window from diagnosis date. Is this the right amount of time?

YOUR ANSWER (OK, or write the correct number of days):


ADDITIONAL NOTES:


---

### PW-LU-005: Does the lung pathway need to distinguish NSCLC from SCLC?

CONTEXT: Small cell lung cancer (SCLC) and non-small cell lung cancer (NSCLC) have significantly different treatment approaches. The current system has one lung cancer pathway that covers both. Is this clinically acceptable for the pilot?

OPTIONS:
- (A) One pathway is acceptable for the pilot — the key steps overlap enough
- (B) We need separate pathways — SCLC and NSCLC are too different to share a sequence
- (C) One pathway is OK but some steps should be marked optional for SCLC cases

YOUR ANSWER:

ADDITIONAL NOTES:


---

### PW-LU-006: Are there missing steps?

CONTEXT: The current lung pathway has 6 steps: thoracic consultation, staging imaging, biomarker testing, tumor board review, medical oncology visit, and treatment plan. Are any important steps missing?

Possible additions to consider:
- Pulmonary function testing (PFT)
- Brain MRI for staging
- Mediastinoscopy
- Biopsy (see PW-LU-002)
- Radiation planning
- Other?

YOUR ANSWER (list any steps that should be added, or write "no changes needed"):


ADDITIONAL NOTES:


---

### PW-LU-007: Validate time windows

CONTEXT: Below are the time windows currently built into the lung pathway. For each one, write OK if correct, or write the number of days it SHOULD be.

- Diagnosis to thoracic consultation: currently 14 days.  YOUR ANSWER:
- Diagnosis to staging imaging: currently 21 days.  YOUR ANSWER:
- [See PW-LU-001] to biomarker testing: currently 14 days.  YOUR ANSWER:
- Diagnosis to tumor board: currently 30 days.  YOUR ANSWER:
- Tumor board to med onc visit: currently 14 days.  YOUR ANSWER:
- Med onc visit to treatment plan: currently 14 days.  YOUR ANSWER:

ADDITIONAL NOTES:


---

### PW-LU-008: Validate alert text and suggested actions

CONTEXT: Below is the alert text and suggested action for each step. For each one, write OK if correct, or write what it SHOULD say.

Step 1 — Alert: "No thoracic consultation found within 14 days of diagnosis."
Step 1 — Action: "Schedule thoracic surgery or pulmonology consult."
YOUR ANSWER:

Step 2 — Alert: "Staging imaging not completed within 21 days of diagnosis."
Step 2 — Action: "Confirm imaging order with treating team."
YOUR ANSWER:

Step 3 — Alert: "Biomarker testing not ordered within 14 days of biopsy confirmation."
Step 3 — Action: "Review with treating physician."
YOUR ANSWER (note: the alert text references "biopsy confirmation" which may need to change based on your answer to PW-LU-001):

Step 4 — Alert: "Tumor board review not documented within 30 days of diagnosis."
Step 4 — Action: "Schedule case presentation."
YOUR ANSWER:

Step 5 — Alert: "Medical oncology visit not scheduled within 14 days of tumor board review."
Step 5 — Action: "Schedule consultation."
YOUR ANSWER:

Step 6 — Alert: "No treatment plan documented within 14 days of medical oncology visit."
Step 6 — Action: "Follow up with medical oncologist."
YOUR ANSWER:

ADDITIONAL NOTES:


---
---

## Colorectal Cancer Pathway Decisions

---

### PW-CR-001: Should the medical oncology visit wait for pathology results?

CONTEXT: We found a discrepancy between the feature spec and what was built.

- The FEATURE SPEC says: medical oncology visit should happen within 21 days of SURGERY
- The SYSTEM actually starts the 21-day clock from PATHOLOGY RESULTS (Step 4), not surgery (Step 3)

This matters because if pathology takes 18 of its 21 days, the med onc visit clock does not start until day 18 post-surgery, giving the patient potentially 39 days between surgery and med onc visit instead of 21.

However, if the oncologist NEEDS pathology results before the visit, the system's behavior may actually be more correct — there is no point scheduling the visit before results are available.

OPTIONS:
- (A) The 21-day clock should start from SURGERY (match the feature spec)
- (B) The 21-day clock should start from PATHOLOGY RESULTS (keep the current build — the oncologist needs these results before the visit)
- (C) Something else (explain below)

YOUR ANSWER:

ADDITIONAL NOTES:


---

### PW-CR-002: Should CEA be a separate step from imaging?

CONTEXT: Step 2 is called "Complete Staging Workup (CT abdomen/pelvis, CEA)." It groups a blood test (CEA tumor marker) with imaging (CT scan) under the event type IMAGING. CEA is actually a lab draw, not an imaging procedure. Should CEA be tracked separately?

OPTIONS:
- (A) Keep them together — they are both part of the staging workup and are ordered at the same time
- (B) Split into two steps — one IMAGING step for CT, one LAB_WORK step for CEA
- (C) CEA is not important enough to track separately for the pilot

YOUR ANSWER:

ADDITIONAL NOTES:


---

### PW-CR-003: Is 45 days from diagnosis to surgery correct?

CONTEXT: Step 3 (Surgery) has a 45-day window measured from DIAGNOSIS DATE. This means the system expects surgery to happen within 45 days of the original diagnosis, regardless of how long staging or surgical consultation takes.

Is 45 days the right number? And should the clock start from diagnosis, or from a specific earlier step (like staging completion or surgical consultation)?

YOUR ANSWER (write the correct number of days AND what the clock should start from):


ADDITIONAL NOTES:


---

### PW-CR-004: Does colorectal need to distinguish colon from rectal cancer?

CONTEXT: Rectal cancer often requires neoadjuvant chemoradiation BEFORE surgery, which reverses the treatment sequence compared to colon cancer. The current system has one colorectal pathway that assumes surgery first. Is this acceptable for the pilot?

OPTIONS:
- (A) One pathway is acceptable — the pilot practice can manage rectal cases manually
- (B) We need separate pathways for colon and rectal cancer
- (C) One pathway is OK but we need to add a note or optional step for neoadjuvant therapy

YOUR ANSWER:

ADDITIONAL NOTES:


---

### PW-CR-005: Should MSI/MMR testing be split from surgical pathology?

CONTEXT: Step 4 is "Pathology and MSI/MMR Testing" — it groups the surgical pathology report with microsatellite instability testing as a single step. In practice, MSI/MMR testing may be ordered separately and may come back on a different timeline than the surgical pathology report. Should these be two steps?

OPTIONS:
- (A) Keep them together — they are closely related and usually tracked together
- (B) Split into two steps — surgical pathology and MSI/MMR are different processes with different timelines
- (C) MSI/MMR is not relevant enough to track separately for the pilot

YOUR ANSWER:

ADDITIONAL NOTES:


---

### PW-CR-006: Are there missing steps?

CONTEXT: The current colorectal pathway has 6 steps: surgical consultation, staging workup, surgery, pathology/MSI, medical oncology visit, and treatment plan. Are any important steps missing?

Possible additions to consider:
- Colonoscopy
- Radiation therapy (especially for rectal cancer)
- Adjuvant chemotherapy
- Genetic counseling (for Lynch syndrome if MSI-high)
- Other?

YOUR ANSWER (list any steps that should be added, or write "no changes needed"):


ADDITIONAL NOTES:


---

### PW-CR-007: Validate time windows

CONTEXT: Below are the time windows currently built into the colorectal pathway. For each one, write OK if correct, or write the number of days it SHOULD be.

- Diagnosis to surgical consultation: currently 14 days.  YOUR ANSWER:
- Diagnosis to staging workup: currently 21 days.  YOUR ANSWER:
- Diagnosis to surgery: currently 45 days.  YOUR ANSWER:
- Surgery to pathology/MSI: currently 21 days.  YOUR ANSWER:
- [See PW-CR-001] to med onc visit: currently 21 days.  YOUR ANSWER:
- Med onc visit to treatment plan: currently 14 days.  YOUR ANSWER:

ADDITIONAL NOTES:


---

### PW-CR-008: Validate alert text and suggested actions

CONTEXT: Below is the alert text and suggested action for each step. For each one, write OK if correct, or write what it SHOULD say.

Step 1 — Alert: "No surgical consultation found within 14 days of diagnosis."
Step 1 — Action: "Schedule surgical oncology consult."
YOUR ANSWER:

Step 2 — Alert: "Staging workup not completed within 21 days of diagnosis."
Step 2 — Action: "Confirm orders with treating team."
YOUR ANSWER:

Step 3 — Alert: "Surgery not yet performed within 45 days of diagnosis."
Step 3 — Action: "Follow up with surgical team on scheduling."
YOUR ANSWER:

Step 4 — Alert: "Pathology or MSI not documented within 21 days of surgery."
Step 4 — Action: "Contact pathology."
YOUR ANSWER:

Step 5 — Alert: "Medical oncology visit not scheduled within 21 days of surgery."
Step 5 — Action: "Schedule post-surgical oncology consultation."
YOUR ANSWER:

Step 6 — Alert: "No treatment plan documented within 14 days of medical oncology visit."
Step 6 — Action: "Follow up with medical oncologist."
YOUR ANSWER:

ADDITIONAL NOTES:


---
---

# REFERENCE MATERIAL

Everything below this line is background information to help you answer the decisions above. You do not need to edit anything in this section.

---

## What the System Currently Tracks (Event Types)

| Event Type | Description |
|------------|-------------|
| REFERRAL | Referral to external provider |
| CONSULTATION | Office visit / consult (surgeon, oncologist, tumor board, etc.) |
| BIOPSY | Tissue sampling procedure |
| PATHOLOGY_REPORT | Pathology results received |
| IMAGING | CT, PET, MRI, X-ray, ultrasound |
| SURGERY | Surgical procedure |
| CHEMOTHERAPY | Chemotherapy administration |
| RADIATION | Radiation therapy |
| FOLLOW_UP | Follow-up visit or treatment plan documentation |
| LAB_WORK | Blood work, tumor markers, etc. |
| GENETIC_TESTING | Genomic / biomarker / molecular testing |
| OTHER | Anything not covered above |

---

## Breast Pathway — Concept Brief vs Feature Spec vs What Was Built

Your concept brief described an 8-step pathway. The feature spec simplified it to 6 steps. The system was built to match the feature spec. Here is what was cut:

| Concept Brief Step | In Feature Spec? | In Build? | What It Tracked |
|--------------------|-----------------|-----------|-----------------|
| 1. Referral Received from PCP | No | No | Mammogram report arrives within 5 days of referral |
| 2. First Visit with Medical Oncologist | No | No | Visit occurred, biopsy order generated |
| 3. US-Guided Biopsy at Outside Radiology | No | No | Biopsy scheduling confirmation within 5 days, pathology from biopsy within 14 days |
| 4. Second Visit (Diagnosis Confirmed) | No | No | Pathology report present in chart before visit date |
| 5. Referral to Breast Surgeon | Yes (as Step 1: Surgeon Consultation) | Yes | Surgeon visit within 14 days of diagnosis |
| 6. Post-Surgery Return Visit | Yes (as Steps 2-3: Surgery + Pathology) | Yes | Surgery within 30 days, pathology within 21 days |
| 7. Oncotype DX Genomic Test | Yes (as Steps 4-5: Genomic Testing + Med Onc Visit) | Yes | Oncotype within 14 days, med onc visit after result |
| 8. Chemotherapy Planning Visit (ECHO, port, chemo) | No | No | ECHO within 7 days, port within 5 days, urgent alert 48 hours before chemo |

---

## Feature Spec Requirements — Implementation Status

| Spec ID | Requirement | Status |
|---------|-------------|--------|
| P-01 | Cancer type and stage | Partial — type tracked, stage is NOT |
| P-05 | Optional steps generate warnings, not alerts | Not implemented — all steps are required |
| F1-02 | Configurable scan interval (default 4 hours) | Different approach — uses per-patient timers instead of batch scan |
| F2-01 | SMS alert delivery | Not implemented — dashboard only |
| F2-05 | 4-hour follow-up reminder | Not implemented |
| F3-07 | Manual re-scan button | Not implemented |
| E-03 | Event status: Scheduled, Completed, Cancelled, Pending | Partial — only Completed is tracked |

---

## Feature Spec Alert Scenarios

Your feature spec included three test scenarios:

**Scenario A (Missing Genomic Test):** Maria, 58, Stage II breast cancer. Pathology completed 21 days ago. No Oncotype DX event found. System should alert: missing step, Oncotype not ordered.
- CAN the system handle this? YES.

**Scenario B (Visit Scheduled Before Test Complete):** Robert, 62. Oncotype DX ordered but not resulted. Medical oncology visit scheduled for next week.
- CAN the system handle this? NO — the system does not track "Scheduled" or "Pending" status. It only knows whether a COMPLETED event exists.

**Scenario C (Delayed Treatment Plan):** James, 70, Stage III lung cancer. Medical oncology visit completed 18 days ago. No treatment plan entered.
- CAN the system handle this? YES.
