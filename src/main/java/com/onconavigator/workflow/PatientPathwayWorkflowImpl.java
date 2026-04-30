package com.onconavigator.workflow;

import com.onconavigator.activity.AlertGenerationActivity;
import com.onconavigator.activity.PathwayEvaluationActivity;
import com.onconavigator.domain.dto.PathwayEvaluationResult;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.util.UUID;

/**
 * Core patient pathway workflow implementation.
 *
 * <p>Implements the dual approach (D-05): evaluates pathway state every 24 hours as a safety
 * net, but also wakes immediately when a care event signal is received. The workflow continues
 * until all pathway steps are complete (D-09) or a deactivation signal is received (D-08).
 *
 * <p>CRITICAL — Determinism constraints (required by Temporal):
 * <ul>
 *   <li>NO database access — all DB operations are delegated to activities</li>
 *   <li>NO {@code System.currentTimeMillis()} — use {@code Workflow.currentTimeMillis()} only</li>
 *   <li>NO {@code new Random()} or {@code Math.random()} — use {@code Workflow.newRandom()}</li>
 *   <li>NO {@code Thread.sleep()} — use {@code Workflow.await()} or {@code Workflow.newTimer()}</li>
 *   <li>NO Spring/JPA imports — workflow classes run in Temporal's context, not Spring's</li>
 * </ul>
 *
 * <p>PHI safety: No PHI in any field or method. Patient is identified by UUID only.
 * Activity implementations access encrypted DB without exposing PHI to Temporal.
 */
public class PatientPathwayWorkflowImpl implements PatientPathwayWorkflow {

    /**
     * Flag set by {@link #careEventChanged} signal. Causes the 24-hour timer to wake early.
     * Reset to false at the start of each evaluation loop iteration (D-05).
     */
    private boolean signalReceived = false;

    /**
     * Flag set by {@link #deactivatePatient} signal. Causes the main loop to exit
     * and triggers open alert cleanup (D-08).
     */
    private boolean deactivated = false;

    /**
     * Flag set when all pathway steps are confirmed complete (D-09).
     * Causes the main loop to exit naturally.
     */
    private boolean pathwayComplete = false;

    /**
     * Patient UUID — stored when monitorPathway is called. UUID only, no PHI.
     */
    private UUID patientId;

    // Retry policy shared by evaluation and alert activities.
    // IllegalArgumentException (bad input) is not retried — it is a programming error.
    private static final RetryOptions ACTIVITY_RETRY_OPTIONS = RetryOptions.newBuilder()
            .setMaximumAttempts(3)
            .setInitialInterval(Duration.ofSeconds(5))
            .setBackoffCoefficient(2.0)
            .setMaximumInterval(Duration.ofMinutes(1))
            .setDoNotRetry(IllegalArgumentException.class.getName())
            .build();

    private final PathwayEvaluationActivity evaluationActivity = Workflow.newActivityStub(
            PathwayEvaluationActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(2))
                    .setRetryOptions(ACTIVITY_RETRY_OPTIONS)
                    .build());

    private final AlertGenerationActivity alertActivity = Workflow.newActivityStub(
            AlertGenerationActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(2))
                    .setRetryOptions(ACTIVITY_RETRY_OPTIONS)
                    .build());

    /**
     * {@inheritDoc}
     *
     * <p>Main workflow loop (D-05 dual approach):
     * <ol>
     *   <li>Reset signal flag</li>
     *   <li>Wait 24 hours OR until a care event signal wakes us early</li>
     *   <li>If deactivated: close open alerts and exit (D-08)</li>
     *   <li>Evaluate pathway — create alerts for any deviations</li>
     *   <li>If all steps complete: exit naturally (D-09)</li>
     *   <li>Repeat</li>
     * </ol>
     */
    @Override
    public void monitorPathway(UUID patientId, String cancerType) {
        this.patientId = patientId;

        while (!deactivated && !pathwayComplete) {
            // Reset per-iteration signal flag before waiting
            signalReceived = false;

            // D-05: dual approach — wait up to 24h, but wake early on any signal
            Workflow.await(Duration.ofHours(24), () -> signalReceived || deactivated);

            if (deactivated) {
                // D-08: graceful termination — close all OPEN alerts before exiting
                evaluationActivity.closeOpenAlerts(patientId);
                break;
            }

            // Evaluate pathway state — creates/updates alerts via evaluation activity
            PathwayEvaluationResult result = evaluationActivity.evaluate(patientId);

            // D-09: natural completion when all steps are confirmed done
            pathwayComplete = result.allStepsComplete();
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sets the signal flag to wake the 24-hour timer early. The care event UUID is
     * not stored — it is not needed for the evaluation (activities query the DB directly).
     */
    @Override
    public void careEventChanged(UUID careEventId) {
        signalReceived = true;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Sets the deactivation flag. The reason string is not stored — it is not
     * needed for cleanup and must not be logged (SEC-06 compliance).
     */
    @Override
    public void deactivatePatient(String reason) {
        deactivated = true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPathwayStatus() {
        return pathwayComplete ? "COMPLETE" : (deactivated ? "DEACTIVATED" : "MONITORING");
    }
}
