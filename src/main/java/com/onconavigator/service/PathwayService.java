package com.onconavigator.service;

import com.onconavigator.config.TemporalConfig;
import com.onconavigator.domain.enums.CancerType;
import com.onconavigator.workflow.DailySweepWorkflow;
import com.onconavigator.workflow.PatientPathwayWorkflow;
import io.temporal.api.common.v1.WorkflowExecution;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Spring service providing the public API for pathway workflow lifecycle management.
 *
 * <p>This service orchestrates {@link WorkflowClient} calls to start, signal, and deactivate
 * patient pathway workflows. It is the sole point of contact between Spring application code
 * and the Temporal workflow layer.
 *
 * <p>PHI safety: This service passes only UUIDs and enum names to Temporal. No PHI enters
 * workflow inputs or signals. Clinical data remains in the encrypted PostgreSQL database and
 * is accessed only by activity implementations (T-02-07, T-02-08).
 *
 * <p>Access control: This service is an internal Spring bean. Phase 3 REST controllers
 * enforce RBAC via {@code @PreAuthorize} before calling these methods (T-02-07).
 */
@Service
public class PathwayService {

    private static final Logger log = LoggerFactory.getLogger(PathwayService.class);

    private final WorkflowClient workflowClient;

    /**
     * Constructor injection — {@code WorkflowClient} is provided by the
     * {@code temporal-spring-boot-starter} autoconfiguration.
     *
     * @param workflowClient Temporal client autoconfigured by Spring Boot starter
     */
    public PathwayService(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    /**
     * Starts a pathway monitoring workflow for the given patient.
     *
     * <p>Uses {@link WorkflowIdReusePolicy#WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE} so that
     * a patient can be re-enrolled after deactivation (D-08). A new run is started even if a
     * previous run with the same workflow ID has completed or been deactivated.
     *
     * <p>If a workflow with this ID is already running (e.g., called twice by mistake), Temporal
     * returns a {@link io.temporal.client.WorkflowExecutionAlreadyStarted} exception. Callers
     * should handle this case to make the start operation idempotent.
     *
     * @param patientId  UUID of the patient — no PHI
     * @param cancerType cancer type for pathway selection — passed as enum name string to Temporal
     * @return Temporal run ID of the newly started workflow execution
     */
    public String startPathwayMonitoring(UUID patientId, CancerType cancerType) {
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(TemporalConfig.PATHWAY_WORKFLOW_ID_PREFIX + patientId)
                .setTaskQueue(TemporalConfig.TASK_QUEUE)
                .setWorkflowIdReusePolicy(
                        WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE)
                .build();

        PatientPathwayWorkflow workflow = workflowClient.newWorkflowStub(
                PatientPathwayWorkflow.class, options);

        // Start asynchronously — does not block until workflow completes
        WorkflowExecution execution = WorkflowClient.start(
                workflow::monitorPathway, patientId, cancerType.name());

        log.info("Started pathway workflow for patient {} with runId {}",
                patientId, execution.getRunId());

        return execution.getRunId();
    }

    /**
     * Sends a care event signal to a running patient pathway workflow.
     *
     * <p>The signal causes the workflow to wake from its 24-hour timer immediately and
     * re-evaluate the patient's pathway state (D-05 event-driven side).
     *
     * <p>If no workflow is running for this patient, the signal is silently dropped by Temporal.
     * Callers should ensure the workflow is started before sending signals.
     *
     * @param patientId   UUID of the patient whose workflow should be signaled — no PHI
     * @param careEventId UUID of the care event that changed — carried as UUID only (T-02-06)
     */
    public void signalCareEventChanged(UUID patientId, UUID careEventId) {
        PatientPathwayWorkflow workflow = workflowClient.newWorkflowStub(
                PatientPathwayWorkflow.class,
                TemporalConfig.PATHWAY_WORKFLOW_ID_PREFIX + patientId);

        workflow.careEventChanged(careEventId);

        log.info("Sent careEventChanged signal for patient {} careEvent {}", patientId, careEventId);
    }

    /**
     * Sends a deactivation signal to a running patient pathway workflow.
     *
     * <p>The workflow will close all OPEN alerts for the patient and then terminate (D-08).
     * The {@code reason} parameter should use coded values, not free-text PHI.
     *
     * @param patientId UUID of the patient to deactivate — no PHI
     * @param reason    coded reason for deactivation (e.g., "PATIENT_DECEASED", "CARE_COMPLETE")
     */
    public void deactivatePatient(UUID patientId, String reason) {
        PatientPathwayWorkflow workflow = workflowClient.newWorkflowStub(
                PatientPathwayWorkflow.class,
                TemporalConfig.PATHWAY_WORKFLOW_ID_PREFIX + patientId);

        workflow.deactivatePatient(reason);

        log.info("Sent deactivatePatient signal for patient {} reason {}", patientId, reason);
    }

    /**
     * Starts the daily sweep cron workflow.
     *
     * <p>This method should be called once at application startup (or triggered manually by
     * an administrator). Temporal's cron scheduling handles subsequent daily executions.
     * If the sweep workflow is already running (e.g., application restarted), Temporal
     * returns a {@link io.temporal.client.WorkflowExecutionAlreadyStarted} exception —
     * callers may safely ignore it.
     *
     * <p>The cron schedule is defined in {@link TemporalConfig#CRON_SCHEDULE} (daily at 6 AM).
     */
    public void startDailySweep() {
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(TemporalConfig.SWEEP_WORKFLOW_ID)
                .setTaskQueue(TemporalConfig.TASK_QUEUE)
                .setCronSchedule(TemporalConfig.CRON_SCHEDULE)
                .build();

        DailySweepWorkflow sweepWorkflow = workflowClient.newWorkflowStub(
                DailySweepWorkflow.class, options);

        WorkflowClient.start(sweepWorkflow::sweep);

        log.info("Started daily sweep workflow with id {}", TemporalConfig.SWEEP_WORKFLOW_ID);
    }
}
