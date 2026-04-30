package com.onconavigator.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Daily sweep activity: queries active patients and starts pathway workflows for any without
 * a running workflow.
 *
 * <p>Implements the safety-net side of the dual approach (D-05). The primary pathway monitoring
 * is event-driven via {@link PatientPathwayWorkflow#careEventChanged}, but the daily sweep
 * ensures no patient falls through the cracks due to missed signals or system restarts.
 *
 * <p>This activity uses a longer startToCloseTimeout (5 minutes) than other activities because
 * it must query all active patients and potentially start many workflows sequentially.
 *
 * <p>Activity implementation is provided in Plan 02-03. This interface defines the contract.
 */
@ActivityInterface
public interface SweepActivity {

    /**
     * Finds all active patients in the database and starts a pathway monitoring workflow
     * for any patient that does not already have a running workflow.
     *
     * <p>Uses {@link io.temporal.client.WorkflowClient} to check workflow state and start
     * new workflows as needed. Existing running workflows are not disturbed.
     * Uses {@link com.onconavigator.domain.enums.PatientStatus} to filter active patients.
     */
    @ActivityMethod
    void findAndStartMissingWorkflows();
}
