package com.onconavigator.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.UUID;

/**
 * Alert generation activity with built-in deduplication (PATH-06).
 *
 * <p>Creates Alert entities when pathway deviations are detected. Checks for existing OPEN
 * alerts with the same (patient, step) key before inserting to prevent duplicate alert spam.
 *
 * <p>PHI safety (T-02-05): Parameters are non-PHI only — patient UUID, step name (a pathway
 * process label, not a patient attribute), alert type code, and clinical process text.
 * The {@code deviationDescription} and {@code suggestedAction} parameters contain pathway
 * template text (admin-controlled JSONB from the database), never patient-specific clinical data.
 *
 * <p>Note on {@code alertTypeStr}: The alert type is passed as a String (not
 * {@link com.onconavigator.domain.enums.AlertType} enum) because Temporal serializes and
 * deserializes activity parameters. Using the enum name string is more robust against
 * schema evolution. The implementation converts with {@code AlertType.valueOf(alertTypeStr)}.
 *
 * <p>Activity implementation is provided in Plan 02-03. This interface defines the contract.
 */
@ActivityInterface
public interface AlertGenerationActivity {

    /**
     * Creates an Alert entity for the specified pathway deviation if no duplicate already exists.
     *
     * <p>Deduplication key: OPEN alert with matching (patient, pathwayStepName). If such an
     * alert already exists, this method is a no-op (PATH-06). This activity may be retried
     * by Temporal on transient failures — idempotency is enforced by the deduplication check.
     *
     * @param patientId           UUID of the patient — no PHI
     * @param pathwayStepName     name of the pathway step that triggered the alert (process label)
     * @param alertTypeStr        alert type as String: "MISSING_EVENT", "DELAYED_EVENT", or "OUT_OF_ORDER"
     * @param deviationDescription plain-language description of the deviation (from pathway template)
     * @param suggestedAction     suggested corrective action for the nurse navigator (from pathway template)
     * @param workflowRunId       Temporal workflow run ID for traceability (not PHI)
     */
    @ActivityMethod
    void generateAlert(UUID patientId, String pathwayStepName, String alertTypeStr,
                       String deviationDescription, String suggestedAction, String workflowRunId);
}
