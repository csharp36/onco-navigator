-- V12__update_pathway_time_windows.sql
-- Apply oncologist-validated time window corrections from clinical review (2026-05-04).
-- Decision references: PW-BR-004, PW-BR-005, PW-LU-007, PW-CR-007
--
-- Changes:
--   BREAST_03 (Pathology Report):    windowDays 21 -> 7
--   BREAST_05 (Med Onc Visit):       windowDays 14 -> 7
--   LUNG_02   (Staging Imaging):     windowDays 21 -> 14
--   LUNG_05   (Med Onc Visit):       windowDays 14 -> 7
--   CRC_02    (Staging Workup):      windowDays 21 -> 14
--   CRC_04    (Pathology/MSI):       windowDays 21 -> 7
--   BREAST_01 suggestedAction updated per oncologist (PW-BR-005)

-- BREAST pathway corrections
UPDATE pathway_templates
SET template_data = (
    SELECT jsonb_agg(
        CASE
            WHEN step->>'stepId' = 'BREAST_01' THEN
                jsonb_set(step, '{suggestedAction}',
                    '"Contact the surgeon''s office to request the date of surgery then contact the patient to inform."')
            WHEN step->>'stepId' = 'BREAST_03' THEN
                jsonb_set(
                    jsonb_set(step, '{windowDays}', '7'),
                    '{alertText}', '"No pathology report found within 7 days of surgery date."')
            WHEN step->>'stepId' = 'BREAST_05' THEN
                jsonb_set(
                    jsonb_set(step, '{windowDays}', '7'),
                    '{alertText}', '"Oncotype result not available before medical oncology visit."')
            ELSE step
        END
        ORDER BY (step->>'stepNumber')::int
    )
    FROM jsonb_array_elements(template_data) AS step
),
updated_at = NOW()
WHERE cancer_type = 'BREAST';

-- LUNG pathway corrections
UPDATE pathway_templates
SET template_data = (
    SELECT jsonb_agg(
        CASE
            WHEN step->>'stepId' = 'LUNG_02' THEN
                jsonb_set(
                    jsonb_set(step, '{windowDays}', '14'),
                    '{alertText}', '"Staging imaging not completed within 14 days of diagnosis."')
            WHEN step->>'stepId' = 'LUNG_05' THEN
                jsonb_set(
                    jsonb_set(step, '{windowDays}', '7'),
                    '{alertText}', '"Medical oncology visit not scheduled within 7 days of tumor board review."')
            ELSE step
        END
        ORDER BY (step->>'stepNumber')::int
    )
    FROM jsonb_array_elements(template_data) AS step
),
updated_at = NOW()
WHERE cancer_type = 'LUNG';

-- COLORECTAL pathway corrections
UPDATE pathway_templates
SET template_data = (
    SELECT jsonb_agg(
        CASE
            WHEN step->>'stepId' = 'CRC_02' THEN
                jsonb_set(
                    jsonb_set(step, '{windowDays}', '14'),
                    '{alertText}', '"Staging workup not completed within 14 days of diagnosis."')
            WHEN step->>'stepId' = 'CRC_04' THEN
                jsonb_set(
                    jsonb_set(step, '{windowDays}', '7'),
                    '{alertText}', '"Pathology or MSI not documented within 7 days of surgery."')
            ELSE step
        END
        ORDER BY (step->>'stepNumber')::int
    )
    FROM jsonb_array_elements(template_data) AS step
),
updated_at = NOW()
WHERE cancer_type = 'COLORECTAL';
