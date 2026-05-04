-- V15__migrate_patients_to_per_patient_pathways.sql
-- Data migration: converts all existing patients from JSONB pathway templates to
-- per-patient relational pathway rows (D-08).
--
-- Strategy:
--   1. For each patient, find the matching pathway_template by cancer_type
--   2. Create one patient_pathways row per patient
--   3. Expand JSONB template steps into patient_pathway_steps rows
--   4. Reconstruct prerequisite edges from the template prerequisites arrays
--   5. Apply physician_overrides as SKIPPED status on the corresponding steps
--   6. Mark steps COMPLETED where care events exist (matched by event_type)
--
-- Edge cases handled:
--   - Patient with no matching template: pathway created with NULL source, 0 steps
--   - Template with NULL/empty template_data: pathway with 0 steps
--   - INACTIVE/DECEASED patients: still migrated (audit trail retention per D-08)
--   - Multiple care events of same type: assigned to steps in step-number order
--
-- Transaction: entire migration is atomic (Flyway default single transaction).
-- created_by: '00000000-0000-0000-0000-000000000000' (system migration actor)

DO $$
DECLARE
    v_system_actor UUID := '00000000-0000-0000-0000-000000000000';
    v_patient      RECORD;
    v_template     RECORD;
    v_pathway_id   UUID;
    v_step         RECORD;
    v_step_id      UUID;
    v_prereq_id    VARCHAR(100);
    v_source_id    UUID;
    v_override     RECORD;
    v_event        RECORD;
    v_step_cursor  REFCURSOR;
BEGIN
    -- -----------------------------------------------------------------------
    -- PHASE 1: Create patient_pathways rows + patient_pathway_steps rows
    -- -----------------------------------------------------------------------
    FOR v_patient IN
        SELECT id, cancer_type FROM patients ORDER BY created_at
    LOOP
        -- Find matching template for this patient's cancer type
        SELECT id, version, template_data
          INTO v_template
          FROM pathway_templates
         WHERE cancer_type = v_patient.cancer_type
         LIMIT 1;

        -- Insert the pathway row (template may be NULL)
        INSERT INTO patient_pathways (
            patient_id,
            source_template_id,
            source_template_version,
            created_at,
            updated_at,
            created_by
        )
        VALUES (
            v_patient.id,
            CASE WHEN v_template.id IS NOT NULL THEN v_template.id ELSE NULL END,
            CASE WHEN v_template.id IS NOT NULL THEN v_template.version ELSE NULL END,
            NOW(),
            NOW(),
            v_system_actor
        )
        RETURNING id INTO v_pathway_id;

        -- Skip step insertion if no template or empty template_data
        IF v_template.id IS NULL
           OR v_template.template_data IS NULL
           OR jsonb_array_length(v_template.template_data::jsonb) = 0
        THEN
            CONTINUE;
        END IF;

        -- Insert a step row for each element in the template_data JSONB array
        FOR v_step IN
            SELECT
                step_elem,
                step_elem->>'stepId'      AS step_id_str,
                step_elem->>'name'        AS step_name,
                step_elem->>'description' AS step_description,
                step_elem->>'eventType'   AS event_type_str,
                (step_elem->>'windowDays')::INTEGER AS window_days_val,
                (step_elem->>'required')::BOOLEAN   AS required_val,
                step_elem->>'alertText'       AS alert_text_val,
                step_elem->>'suggestedAction' AS suggested_action_val,
                (step_elem->>'stepNumber')::INTEGER AS step_number_val
            FROM jsonb_array_elements(v_template.template_data::jsonb) AS step_elem
            ORDER BY (step_elem->>'stepNumber')::INTEGER
        LOOP
            INSERT INTO patient_pathway_steps (
                pathway_id,
                name,
                description,
                event_type,
                window_days,
                required,
                status,
                alert_text,
                suggested_action,
                source_template_step_id,
                created_at,
                updated_at,
                created_by,
                version
            )
            VALUES (
                v_pathway_id,
                v_step.step_name,
                v_step.step_description,
                CASE
                    WHEN v_step.event_type_str IS NOT NULL
                    THEN v_step.event_type_str::care_event_type
                    ELSE NULL
                END,
                v_step.window_days_val,
                COALESCE(v_step.required_val, true),
                'ACTIVE'::pathway_step_status,
                v_step.alert_text_val,
                v_step.suggested_action_val,
                v_step.step_id_str,
                NOW(),
                NOW(),
                v_system_actor,
                0
            );
        END LOOP;
    END LOOP;

    -- -----------------------------------------------------------------------
    -- PHASE 2: Build prerequisite edges
    -- For each step that has prerequisites, find both the source and target
    -- per-patient step rows and insert edges.
    -- -----------------------------------------------------------------------
    INSERT INTO patient_pathway_edges (
        pathway_id,
        source_step_id,
        target_step_id,
        created_at,
        created_by
    )
    SELECT DISTINCT
        target_step.pathway_id,
        source_step.id AS source_step_id,
        target_step.id AS target_step_id,
        NOW(),
        v_system_actor
    FROM patient_pathway_steps AS target_step
    JOIN pathway_templates pt ON (
        EXISTS (
            SELECT 1 FROM patient_pathways pp
            WHERE pp.id = target_step.pathway_id
              AND pp.source_template_id = pt.id
        )
    )
    JOIN LATERAL jsonb_array_elements(pt.template_data::jsonb) AS tmpl ON
        tmpl->>'stepId' = target_step.source_template_step_id
    JOIN LATERAL jsonb_array_elements_text(tmpl->'prerequisites') AS prereq_id ON true
    JOIN patient_pathway_steps AS source_step ON
        source_step.pathway_id = target_step.pathway_id
        AND source_step.source_template_step_id = prereq_id
    WHERE
        -- Only process steps that have prerequisites (non-empty array)
        jsonb_array_length(tmpl->'prerequisites') > 0
        -- Avoid self-referential edges (safety check)
        AND target_step.id <> source_step.id;

    -- -----------------------------------------------------------------------
    -- PHASE 3: Apply physician_overrides -> SKIPPED status
    -- Match by (patient_id, pathway_step_id = source_template_step_id)
    -- -----------------------------------------------------------------------
    UPDATE patient_pathway_steps AS pps
    SET
        status      = 'SKIPPED'::pathway_step_status,
        skip_reason = po.override_reason,
        updated_at  = NOW()
    FROM physician_overrides po
    JOIN patient_pathways pp ON pp.patient_id = po.patient_id
    WHERE pps.pathway_id = pp.id
      AND pps.source_template_step_id = po.pathway_step_id;

    -- -----------------------------------------------------------------------
    -- PHASE 4: Mark steps COMPLETED where matching care events exist
    -- Use a window function to assign care events to steps in step-number order
    -- when multiple events share the same event_type.
    -- -----------------------------------------------------------------------
    WITH ranked_events AS (
        -- Rank completed care events per patient per event_type by event_date
        SELECT
            ce.id            AS event_id,
            ce.patient_id,
            ce.event_type,
            ce.event_date,
            ROW_NUMBER() OVER (
                PARTITION BY ce.patient_id, ce.event_type
                ORDER BY ce.event_date ASC
            ) AS event_rank
        FROM care_events ce
        WHERE ce.status = 'COMPLETED'
    ),
    ranked_steps AS (
        -- Rank ACTIVE steps per pathway per event_type by source step number
        -- (using source_template_step_id suffix numeric part as a proxy for order)
        SELECT
            pps.id          AS step_id,
            pps.pathway_id,
            pps.event_type,
            pp.patient_id,
            ROW_NUMBER() OVER (
                PARTITION BY pps.pathway_id, pps.event_type
                ORDER BY pps.id ASC  -- insertion order preserves template step order
            ) AS step_rank
        FROM patient_pathway_steps pps
        JOIN patient_pathways pp ON pp.id = pps.pathway_id
        WHERE pps.status = 'ACTIVE'
          AND pps.event_type IS NOT NULL
    )
    UPDATE patient_pathway_steps AS pps
    SET
        status                  = 'COMPLETED'::pathway_step_status,
        completed_at            = (re.event_date::TIMESTAMP WITH TIME ZONE),
        completed_care_event_id = re.event_id,
        updated_at              = NOW()
    FROM ranked_events re
    JOIN ranked_steps rs ON
        rs.patient_id   = re.patient_id
        AND rs.event_type   = re.event_type
        AND rs.step_rank    = re.event_rank
    WHERE pps.id = rs.step_id;

END $$;
