================================================================================
                    ONCO-NAVIGATOR AI TEST CORPUS
                    Synthetic Clinical Documents
================================================================================

PURPOSE
-------
This directory contains synthetic clinical documents used for testing and
evaluating the Onco-Navigator AI document classification and extraction
pipeline. These documents serve as the ground-truth corpus for validating:

  - Document type classification (pathology, radiology, operative note, lab
    result, referral letter)
  - Patient field extraction (MRN, patient name, date of birth, event date)
  - Cancer type identification (breast, lung, colorectal)
  - Edge case handling (format variants, date ambiguity, missing fields)

ALL PATIENT DATA IN THIS CORPUS IS SYNTHETIC AND FICTIONAL.
No real patient data was used in creating these documents. Patient names,
medical record numbers (MRNs), dates of birth, clinical findings, and all
other content are entirely fabricated for testing purposes. Any resemblance
to real patients is purely coincidental.

DOCUMENT NAMING CONVENTION
---------------------------
Files follow the pattern: {type}-{description}-{sequence}.txt

  - type:        Document category (pathology, radiology, operative, lab,
                 referral, or descriptor for variants/edge cases)
  - description: Brief content description
  - sequence:    Two-digit sequence number (01, 02, etc.)

DIRECTORY STRUCTURE
-------------------
test-corpus/
  pathology/           Standard pathology reports (breast, lung)
  radiology/           Standard radiology reports (CT, mammogram)
  operative-notes/     Operative/surgical notes (lumpectomy, colectomy)
  lab-results/         Laboratory results (CBC, tumor markers)
  referral/            Referral letters (radiation oncology, medical oncology)
  variants/            Alternate document formats that should still classify
                       correctly (e.g., "Cytology Report" classifies as
                       PATHOLOGY_REPORT)
  date-ambiguity/      Documents with multiple dates where the correct
                       event date must be distinguished from report/dictation
                       dates
  edge-cases/          Challenging documents: missing MRN, combined reports

CORPUS INVENTORY (16 documents)
-------------------------------

Standard Documents (10):
  1. pathology/pathology-breast-lumpectomy-01.txt    Breast IDC, ER+/PR+/HER2-
  2. pathology/pathology-lung-biopsy-01.txt          Lung adenocarcinoma biopsy
  3. radiology/radiology-ct-chest-01.txt             CT chest with contrast
  4. radiology/radiology-mammogram-01.txt            Diagnostic mammogram, BI-RADS 4C
  5. operative-notes/operative-lumpectomy-01.txt     Breast lumpectomy + SLNB
  6. operative-notes/operative-colectomy-01.txt      Right hemicolectomy
  7. lab-results/lab-cbc-panel-01.txt                CBC with differential
  8. lab-results/lab-tumor-markers-01.txt            CEA + CA 19-9 panel
  9. referral/referral-radiation-oncology-01.txt     Referral to radiation oncology
 10. referral/referral-medical-oncology-01.txt       Referral for chemo/immuno eval

Format Variants (2):
 11. variants/pathology-alternate-format-01.txt      Cytology report (classifies
                                                     as PATHOLOGY_REPORT)
 12. variants/radiology-alternate-format-01.txt      PET-CT with pathology findings
                                                     in impression (classifies as
                                                     RADIOLOGY_REPORT)

Date Ambiguity Cases (2):
 13. date-ambiguity/operative-note-multiple-dates-01.txt
     Has procedure, dictation, transcription, and report dates.
     Expected event date: 2026-03-20 (procedure date)

 14. date-ambiguity/pathology-delayed-dictation-01.txt
     Biopsy date 2026-02-10, report date 2026-02-20.
     Expected event date: 2026-02-10 (biopsy/procedure date)

Edge Cases (2):
 15. edge-cases/document-no-mrn-01.txt
     Referral from outside facility with NO MRN.
     Expected MRN: null

 16. edge-cases/multi-section-path-rad-01.txt
     Combined pathology and radiology in one document.
     Expected document type: PATHOLOGY_REPORT (primary content)

SYNTHETIC IDENTIFIERS
---------------------
All synthetic patients use MRNs in the format TEST-001 through TEST-016.
Patient names use obviously synthetic surnames: TestPatient, DemoSubject,
SampleCase, MockData, TestCase, SyntheticPt, FakeRecord, PlaceholderPt,
TestSubject, MockPatient, TestVariant, TestAmbiguous, TestMultiDate,
TestDelayed, Garcia-Lopez (edge case: no MRN), TestMultiSection.

REFERENCE DATASET
-----------------
Ground-truth labels for all 16 documents are in:
  src/test/resources/eval/reference-dataset.json

This JSON file maps each document to its expected classification fields
(document type, MRN, patient name, DOB, event type, event date, confidence)
for automated evaluation of AI classification accuracy.

CANCER TYPES COVERED
--------------------
  - Breast cancer: Documents 1, 4, 5, 7, 9, 11, 14, 15, 16
  - Lung cancer:   Documents 2, 3, 10, 13
  - Colorectal:    Documents 6, 8, 12

USAGE
-----
These documents are intended for:
  1. Unit testing document classification service
  2. Integration testing the full extraction pipeline
  3. Evaluating AI classification accuracy (E-01 through E-05 in AI-SPEC)
  4. Regression testing after prompt or model changes

Do NOT use real patient data for testing. Always use this synthetic corpus.

Created: 2026-05-01
Project: Onco-Navigator AI
