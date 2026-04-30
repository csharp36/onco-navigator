package com.onconavigator.workflow;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

import java.util.UUID;

/**
 * Monitors a single patient's care pathway.
 *
 * <p>One workflow instance per patient (D-07). Workflow ID format: pathway-{patientId}.
 *
 * <p>The workflow implements a dual approach (D-05): it evaluates pathway state every 24 hours
 * as a safety net, but also wakes immediately when a care event signal is received. This ensures
 * both timely response to known events and eventual detection of missed signals.
 *
 * <p>PHI safety (SEC-06, T-02-05, T-02-06): All method parameters are UUID and String only.
 * No patient names, DOBs, MRNs, or any PHI enters Temporal's event history via this interface.
 * Activity implementations fetch PHI from the encrypted database as needed.
 */
@WorkflowInterface
public interface PatientPathwayWorkflow {

    /**
     * Starts monitoring a patient's care pathway.
     *
     * <p>Implements the signal+timer loop: evaluates every 24 hours or earlier when
     * a care event signal is received. The workflow runs until all pathway steps are
     * complete (D-09) or a deactivation signal is received (D-08).
     *
     * @param patientId  UUID of the patient — no PHI, only the system identifier
     * @param cancerType cancer type name (e.g., "BREAST", "LUNG", "COLORECTAL") — enum name only
     */
    @WorkflowMethod
    void monitorPathway(UUID patientId, String cancerType);

    /**
     * Signals that a care event has been added or updated for this patient.
     *
     * <p>Causes the workflow to wake from its 24-hour timer immediately and re-evaluate
     * the pathway. This is the event-driven side of the dual approach (D-05).
     *
     * @param careEventId UUID of the care event that changed — carries only the UUID, no PHI (T-02-06)
     */
    @SignalMethod
    void careEventChanged(UUID careEventId);

    /**
     * Signals that the patient has been deactivated (discharged, deceased, or transferred).
     *
     * <p>The workflow will close all open alerts for the patient and then terminate (D-08).
     *
     * @param reason reason for deactivation (clinical summary string, no PHI — use coded values)
     */
    @SignalMethod
    void deactivatePatient(String reason);

    /**
     * Returns the current monitoring status of this patient's pathway workflow.
     *
     * @return "MONITORING" if actively monitoring, "COMPLETE" if all steps finished (D-09),
     *         or "DEACTIVATED" if terminated via signal (D-08)
     */
    @QueryMethod
    String getPathwayStatus();
}
