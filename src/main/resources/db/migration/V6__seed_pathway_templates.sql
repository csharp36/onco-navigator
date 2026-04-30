-- V6__seed_pathway_templates.sql
-- Seed the three cancer pathway templates for V1 PoC.
-- Templates match the pathway definitions in the V1 Feature Specification v2.
--
-- JSONB structure per D-04: stepId, stepNumber, name, description, eventType,
-- windowDays, anchorType, anchorStepId, required, alertText, suggestedAction, prerequisites.
--
-- anchorType values: DIAGNOSIS_DATE, PREVIOUS_STEP, SPECIFIC_STEP
-- eventType values must match CareEventType enum.
-- System created_by: '00000000-0000-0000-0000-000000000000'

-- PATHWAY 1: Newly Diagnosed Breast Cancer (Stage I–III)
INSERT INTO pathway_templates (id, cancer_type, version, template_data, created_at, updated_at, created_by)
VALUES (
    gen_random_uuid(),
    'BREAST',
    1,
    '[
        {
            "stepId": "BREAST_01",
            "stepNumber": 1,
            "name": "Surgeon Consultation",
            "description": "Patient meets with surgical oncologist.",
            "eventType": "CONSULTATION",
            "windowDays": 14,
            "anchorType": "DIAGNOSIS_DATE",
            "anchorStepId": null,
            "required": true,
            "alertText": "No surgeon visit found within 14 days of diagnosis date.",
            "suggestedAction": "Schedule surgical oncology consultation.",
            "prerequisites": []
        },
        {
            "stepId": "BREAST_02",
            "stepNumber": 2,
            "name": "Surgery",
            "description": "Surgical procedure performed; specimen obtained.",
            "eventType": "SURGERY",
            "windowDays": 30,
            "anchorType": "PREVIOUS_STEP",
            "anchorStepId": null,
            "required": true,
            "alertText": "Surgery not yet completed within 30 days of surgeon consult.",
            "suggestedAction": "Follow up with surgical team on scheduling.",
            "prerequisites": ["BREAST_01"]
        },
        {
            "stepId": "BREAST_03",
            "stepNumber": 3,
            "name": "Pathology Report",
            "description": "Final pathology confirms diagnosis and receptor status.",
            "eventType": "PATHOLOGY_REPORT",
            "windowDays": 21,
            "anchorType": "PREVIOUS_STEP",
            "anchorStepId": null,
            "required": true,
            "alertText": "No pathology report found within 21 days of surgery date.",
            "suggestedAction": "Contact pathology department.",
            "prerequisites": ["BREAST_02"]
        },
        {
            "stepId": "BREAST_04",
            "stepNumber": 4,
            "name": "Genomic Testing (Oncotype DX)",
            "description": "Tumor specimen sent for genomic analysis.",
            "eventType": "GENETIC_TESTING",
            "windowDays": 14,
            "anchorType": "PREVIOUS_STEP",
            "anchorStepId": null,
            "required": true,
            "alertText": "Oncotype DX not ordered within 14 days of pathology report.",
            "suggestedAction": "Review with treating physician to confirm order.",
            "prerequisites": ["BREAST_03"]
        },
        {
            "stepId": "BREAST_05",
            "stepNumber": 5,
            "name": "Medical Oncology Visit",
            "description": "Patient meets with medical oncologist.",
            "eventType": "CONSULTATION",
            "windowDays": 14,
            "anchorType": "SPECIFIC_STEP",
            "anchorStepId": "BREAST_04",
            "required": true,
            "alertText": "Oncotype result not available before medical oncology visit.",
            "suggestedAction": "Confirm Oncotype DX result timing. If result will not be available before the visit, consider rescheduling the medical oncology visit to avoid a wasted appointment.",
            "prerequisites": ["BREAST_04"]
        },
        {
            "stepId": "BREAST_06",
            "stepNumber": 6,
            "name": "Treatment Plan Established",
            "description": "Chemo, hormone therapy, or observation plan documented.",
            "eventType": "FOLLOW_UP",
            "windowDays": 14,
            "anchorType": "PREVIOUS_STEP",
            "anchorStepId": null,
            "required": true,
            "alertText": "No treatment plan documented within 14 days of medical oncology visit.",
            "suggestedAction": "Follow up with medical oncologist.",
            "prerequisites": ["BREAST_05"]
        }
    ]'::jsonb,
    NOW(),
    NOW(),
    '00000000-0000-0000-0000-000000000000'
);

-- PATHWAY 2: Newly Diagnosed Lung Cancer (Stage I–III)
INSERT INTO pathway_templates (id, cancer_type, version, template_data, created_at, updated_at, created_by)
VALUES (
    gen_random_uuid(),
    'LUNG',
    1,
    '[
        {
            "stepId": "LUNG_01",
            "stepNumber": 1,
            "name": "Pulmonology / Thoracic Consultation",
            "description": "Initial evaluation by pulmonologist or thoracic surgeon.",
            "eventType": "CONSULTATION",
            "windowDays": 14,
            "anchorType": "DIAGNOSIS_DATE",
            "anchorStepId": null,
            "required": true,
            "alertText": "No thoracic consultation found within 14 days of diagnosis.",
            "suggestedAction": "Schedule thoracic surgery or pulmonology consult.",
            "prerequisites": []
        },
        {
            "stepId": "LUNG_02",
            "stepNumber": 2,
            "name": "Staging Imaging (CT/PET)",
            "description": "CT chest and/or PET scan to determine staging.",
            "eventType": "IMAGING",
            "windowDays": 21,
            "anchorType": "DIAGNOSIS_DATE",
            "anchorStepId": null,
            "required": true,
            "alertText": "Staging imaging not completed within 21 days of diagnosis.",
            "suggestedAction": "Confirm imaging order with treating team.",
            "prerequisites": []
        },
        {
            "stepId": "LUNG_03",
            "stepNumber": 3,
            "name": "Molecular / Biomarker Testing",
            "description": "Tumor tissue sent for EGFR, ALK, PD-L1, and other biomarkers.",
            "eventType": "GENETIC_TESTING",
            "windowDays": 14,
            "anchorType": "PREVIOUS_STEP",
            "anchorStepId": null,
            "required": true,
            "alertText": "Biomarker testing not ordered within 14 days of biopsy confirmation.",
            "suggestedAction": "Review with treating physician.",
            "prerequisites": ["LUNG_01"]
        },
        {
            "stepId": "LUNG_04",
            "stepNumber": 4,
            "name": "Multidisciplinary Tumor Board Review",
            "description": "Case presented to tumor board for treatment recommendation.",
            "eventType": "CONSULTATION",
            "windowDays": 30,
            "anchorType": "DIAGNOSIS_DATE",
            "anchorStepId": null,
            "required": true,
            "alertText": "Tumor board review not documented within 30 days of diagnosis.",
            "suggestedAction": "Schedule case presentation.",
            "prerequisites": ["LUNG_01"]
        },
        {
            "stepId": "LUNG_05",
            "stepNumber": 5,
            "name": "Medical Oncology Visit",
            "description": "Patient meets with medical oncologist to discuss systemic therapy.",
            "eventType": "CONSULTATION",
            "windowDays": 14,
            "anchorType": "PREVIOUS_STEP",
            "anchorStepId": null,
            "required": true,
            "alertText": "Medical oncology visit not scheduled within 14 days of tumor board review.",
            "suggestedAction": "Schedule consultation.",
            "prerequisites": ["LUNG_04"]
        },
        {
            "stepId": "LUNG_06",
            "stepNumber": 6,
            "name": "Treatment Plan Established",
            "description": "Treatment plan (chemo, targeted therapy, immunotherapy, or surgery) documented.",
            "eventType": "FOLLOW_UP",
            "windowDays": 14,
            "anchorType": "PREVIOUS_STEP",
            "anchorStepId": null,
            "required": true,
            "alertText": "No treatment plan documented within 14 days of medical oncology visit.",
            "suggestedAction": "Follow up with medical oncologist.",
            "prerequisites": ["LUNG_05"]
        }
    ]'::jsonb,
    NOW(),
    NOW(),
    '00000000-0000-0000-0000-000000000000'
);

-- PATHWAY 3: Newly Diagnosed Colorectal Cancer (Stage I–III)
INSERT INTO pathway_templates (id, cancer_type, version, template_data, created_at, updated_at, created_by)
VALUES (
    gen_random_uuid(),
    'COLORECTAL',
    1,
    '[
        {
            "stepId": "CRC_01",
            "stepNumber": 1,
            "name": "Surgical Oncology Consultation",
            "description": "Patient meets with colorectal or surgical oncologist.",
            "eventType": "CONSULTATION",
            "windowDays": 14,
            "anchorType": "DIAGNOSIS_DATE",
            "anchorStepId": null,
            "required": true,
            "alertText": "No surgical consultation found within 14 days of diagnosis.",
            "suggestedAction": "Schedule surgical oncology consult.",
            "prerequisites": []
        },
        {
            "stepId": "CRC_02",
            "stepNumber": 2,
            "name": "Complete Staging Workup (CT abdomen/pelvis, CEA)",
            "description": "Imaging and labs to confirm staging before surgery.",
            "eventType": "IMAGING",
            "windowDays": 21,
            "anchorType": "DIAGNOSIS_DATE",
            "anchorStepId": null,
            "required": true,
            "alertText": "Staging workup not completed within 21 days of diagnosis.",
            "suggestedAction": "Confirm orders with treating team.",
            "prerequisites": []
        },
        {
            "stepId": "CRC_03",
            "stepNumber": 3,
            "name": "Surgery (Resection)",
            "description": "Surgical resection performed.",
            "eventType": "SURGERY",
            "windowDays": 45,
            "anchorType": "DIAGNOSIS_DATE",
            "anchorStepId": null,
            "required": true,
            "alertText": "Surgery not yet performed within 45 days of diagnosis.",
            "suggestedAction": "Follow up with surgical team on scheduling.",
            "prerequisites": ["CRC_01"]
        },
        {
            "stepId": "CRC_04",
            "stepNumber": 4,
            "name": "Pathology and MSI/MMR Testing",
            "description": "Pathology confirms margins; microsatellite instability (MSI) testing performed.",
            "eventType": "PATHOLOGY_REPORT",
            "windowDays": 21,
            "anchorType": "PREVIOUS_STEP",
            "anchorStepId": null,
            "required": true,
            "alertText": "Pathology or MSI not documented within 21 days of surgery.",
            "suggestedAction": "Contact pathology.",
            "prerequisites": ["CRC_03"]
        },
        {
            "stepId": "CRC_05",
            "stepNumber": 5,
            "name": "Medical Oncology Visit",
            "description": "Patient meets with medical oncologist to discuss adjuvant chemotherapy.",
            "eventType": "CONSULTATION",
            "windowDays": 21,
            "anchorType": "PREVIOUS_STEP",
            "anchorStepId": null,
            "required": true,
            "alertText": "Medical oncology visit not scheduled within 21 days of surgery.",
            "suggestedAction": "Schedule post-surgical oncology consultation.",
            "prerequisites": ["CRC_03"]
        },
        {
            "stepId": "CRC_06",
            "stepNumber": 6,
            "name": "Treatment Plan Established",
            "description": "Adjuvant chemotherapy plan or observation plan documented.",
            "eventType": "FOLLOW_UP",
            "windowDays": 14,
            "anchorType": "PREVIOUS_STEP",
            "anchorStepId": null,
            "required": true,
            "alertText": "No treatment plan documented within 14 days of medical oncology visit.",
            "suggestedAction": "Follow up with medical oncologist.",
            "prerequisites": ["CRC_05"]
        }
    ]'::jsonb,
    NOW(),
    NOW(),
    '00000000-0000-0000-0000-000000000000'
);
