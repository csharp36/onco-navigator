package com.onconavigator.activity;

import com.onconavigator.config.TemporalConfig;
import com.onconavigator.domain.Patient;
import com.onconavigator.domain.enums.PatientStatus;
import com.onconavigator.repository.PatientRepository;
import com.onconavigator.workflow.PatientPathwayWorkflow;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Daily sweep activity that ensures all active patients have a running pathway workflow.
 *
 * <p>Queries active patients, then attempts to start a pathway monitoring workflow for each one.
 * Uses {@link WorkflowIdReusePolicy#WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE} (matching
 * {@link com.onconavigator.service.PathwayService#startPathwayMonitoring}) so that a patient
 * who completed their pathway and was re-enrolled receives a new workflow from the sweep as well.
 * The start attempt throws {@link WorkflowExecutionAlreadyStarted} if a workflow is already
 * running for that patient — allowing this activity to efficiently skip patients that are
 * already monitored. With ALLOW_DUPLICATE, completed (not running) executions are not rejected,
 * so re-enrolled patients are correctly started rather than silently skipped.
 *
 * <p>This try-to-start pattern is idempotent: calling this activity multiple times produces
 * the same outcome (all active patients have a running workflow) without creating duplicate
 * workflows or overwriting running state.
 *
 * <p>PHI safety: This activity logs only patient UUIDs and summary counts. No patient names,
 * DOBs, or MRNs appear in any log statement.
 */
@Component
public class SweepActivityImpl implements SweepActivity {

    private static final Logger log = LoggerFactory.getLogger(SweepActivityImpl.class);

    private final PatientRepository patientRepository;
    private final WorkflowClient workflowClient;

    /**
     * Constructor injection.
     *
     * @param patientRepository JPA repository for querying active patients
     * @param workflowClient    Temporal client for starting pathway workflows;
     *                          autoconfigured by {@code temporal-spring-boot-starter}
     */
    public SweepActivityImpl(PatientRepository patientRepository, WorkflowClient workflowClient) {
        this.patientRepository = patientRepository;
        this.workflowClient = workflowClient;
    }

    /**
     * {@inheritDoc}
     *
     * <p>For each active patient, attempts to start a workflow with
     * {@link WorkflowIdReusePolicy#WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE}. If the workflow
     * is already running, Temporal throws {@link WorkflowExecutionAlreadyStarted}, which is
     * caught and counted as "skipped" (not an error). Successfully started workflows are counted
     * separately. ALLOW_DUPLICATE (rather than REJECT_DUPLICATE) is intentional: it matches the
     * policy in PathwayService and allows re-enrolled patients whose previous workflow completed
     * to receive a new workflow from the sweep instead of being silently skipped.
     *
     * <p>The summary log message (DAILY_SWEEP) records the total active patient count, how many
     * workflows were newly started, and how many were already running.
     */
    @Override
    public void findAndStartMissingWorkflows() {
        List<Patient> activePatients = patientRepository.findByStatus(PatientStatus.ACTIVE);

        int started = 0;
        int skipped = 0;

        for (Patient patient : activePatients) {
            String workflowId = TemporalConfig.PATHWAY_WORKFLOW_ID_PREFIX + patient.getId();

            try {
                WorkflowOptions options = WorkflowOptions.newBuilder()
                        .setWorkflowId(workflowId)
                        .setTaskQueue(TemporalConfig.TASK_QUEUE)
                        .setWorkflowIdReusePolicy(
                                WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE)
                        .build();

                PatientPathwayWorkflow workflow = workflowClient.newWorkflowStub(
                        PatientPathwayWorkflow.class, options);

                // Pass cancerType as String enum name — no PHI crosses to Temporal (SEC-06)
                WorkflowClient.start(workflow::monitorPathway, patient.getId(),
                        patient.getCancerType().name());

                started++;
                log.info("SWEEP_STARTED: Started missing workflow for patient {}", patient.getId());

            } catch (WorkflowExecutionAlreadyStarted e) {
                // Workflow already running for this patient — this is expected, not an error
                skipped++;
            }
        }

        log.info("DAILY_SWEEP: activePatients={} workflowsStarted={} alreadyRunning={}",
                activePatients.size(), started, skipped);
    }
}
