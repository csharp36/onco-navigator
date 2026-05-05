-- V20__seed_rectal_template.sql
-- Phase 8: Seed rectal cancer child template inheriting from colorectal root.
-- Per PW-CR-004: Rectal cancer requires neoadjuvant chemoradiation BEFORE surgery,
-- reversing the colon cancer treatment sequence.

INSERT INTO pathway_templates (id, cancer_type, version, parent_template_id, name, description, template_data, created_at, updated_at, created_by)
VALUES (
    gen_random_uuid(),
    'COLORECTAL',
    1,
    (SELECT id FROM pathway_templates WHERE cancer_type = 'COLORECTAL' AND parent_template_id IS NULL),
    'Rectal Cancer Pathway',
    'Includes neoadjuvant chemoradiation before surgery',
    '{
        "overrides": [
            {
                "stepId": "CRC_03",
                "fields": {
                    "windowDays": 60,
                    "description": "Surgical resection performed after neoadjuvant chemoradiation.",
                    "alertText": "Surgery not yet performed within 60 days. Confirm neoadjuvant therapy completion status.",
                    "suggestedAction": "Verify chemoradiation completion and coordinate surgery scheduling with surgical team."
                }
            }
        ],
        "additions": [
            {
                "stepId": "RECTAL_01",
                "stepNumber": 3,
                "name": "Neoadjuvant Chemoradiation",
                "description": "Combined chemotherapy and radiation therapy before surgical resection. Standard for locally advanced rectal cancer.",
                "eventType": "RADIATION",
                "windowDays": 30,
                "anchorType": "PREVIOUS_STEP",
                "anchorStepId": null,
                "required": true,
                "alertText": "Neoadjuvant chemoradiation not started within 30 days of staging workup.",
                "suggestedAction": "Coordinate with radiation oncology for treatment planning and scheduling.",
                "prerequisites": ["CRC_02"]
            }
        ],
        "removals": [],
        "edgeChanges": {
            "remove": [
                {"from": "CRC_01", "to": "CRC_03"}
            ],
            "add": [
                {"from": "CRC_02", "to": "RECTAL_01"},
                {"from": "RECTAL_01", "to": "CRC_03"}
            ]
        }
    }'::jsonb,
    NOW(),
    NOW(),
    '00000000-0000-0000-0000-000000000000'
);
