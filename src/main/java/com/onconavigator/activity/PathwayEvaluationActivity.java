package com.onconavigator.activity;

import com.onconavigator.domain.dto.PathwayEvaluationResult;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

import java.util.UUID;

/**
 * Pathway evaluation activity.
 *
 * <p>Spring bean with JPA repository access. Fetches patient data from the encrypted database
 * and evaluates whether all required pathway steps have been completed within their expected
 * time windows.
 *
 * <p>PHI safety: No PHI enters Temporal. This activity receives only the patient UUID and
 * retrieves clinical data directly from the encrypted PostgreSQL database. The evaluation
 * result contains no PHI — only step completion status and alert summary strings.
 *
 * <p>Activity implementation is provided in Plan 02-03. This interface defines the contract.
 */
@ActivityInterface
public interface PathwayEvaluationActivity {

    /**
     * Evaluates all pathway steps for the specified patient, detects deviations, and
     * creates alerts for any missing, delayed, or out-of-order care events.
     *
     * <p>Idempotent: existing OPEN alerts with the same key are not duplicated (PATH-06).
     * This activity may be retried by Temporal on transient failures.
     *
     * @param patientId UUID of the patient to evaluate — no PHI
     * @return evaluation result indicating whether all steps are complete and listing
     *         any alerts generated during this evaluation
     */
    @ActivityMethod
    PathwayEvaluationResult evaluate(UUID patientId);

    /**
     * Closes all OPEN alerts for a patient who has been deactivated (D-08).
     *
     * <p>Called during the deactivation branch of the workflow before the workflow terminates.
     * Ensures the alert queue is not polluted with unresolvable alerts for inactive patients.
     *
     * @param patientId UUID of the patient being deactivated — no PHI
     */
    @ActivityMethod
    void closeOpenAlerts(UUID patientId);
}
