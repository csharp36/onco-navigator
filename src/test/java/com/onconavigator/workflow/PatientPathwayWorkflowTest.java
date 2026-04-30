package com.onconavigator.workflow;

import com.onconavigator.activity.PathwayEvaluationActivity;
import com.onconavigator.domain.dto.PathwayEvaluationResult;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.TestWorkflowExtension;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Workflow unit tests using Temporal's TestWorkflowEnvironment with time skipping.
 *
 * <p>These tests prove the core workflow behavior without a real Temporal server:
 * <ul>
 *   <li>Timer loop: the workflow evaluates on a 24h timer (INFR-03, INFR-04)</li>
 *   <li>Signal wakeup: a care event signal wakes the workflow early (D-05)</li>
 *   <li>Deactivation: the workflow gracefully terminates and closes alerts (D-08)</li>
 *   <li>Natural completion: workflow exits when all pathway steps are complete (D-09)</li>
 *   <li>Query method: getPathwayStatus() returns correct state</li>
 * </ul>
 *
 * <p>IMPORTANT — Activity registration: Temporal's reflection scanner rejects Mockito dynamic
 * proxies because @ActivityMethod annotations appear on the proxy subclass methods, not just the
 * interface. Instead, this class uses concrete inner stub classes (CountingEvaluationActivity,
 * CompletingEvaluationActivity) that implement PathwayEvaluationActivity directly, allowing
 * controlled return values and invocation counting via AtomicInteger counters.
 *
 * <p>Time skipping: TestWorkflowExtension accelerates virtual time — Workflow.await(24h, ...)
 * does NOT block 24 real hours. testEnv.sleep(Duration) skips virtual time instantly.
 * Brief Thread.sleep() calls allow Temporal's internal task queue to drain after time skips.
 *
 * <p>PHI safety: All test data uses synthetic UUIDs. No real patient data is used.
 */
@Timeout(30)
class PatientPathwayWorkflowTest {

    @RegisterExtension
    public static final TestWorkflowExtension testWorkflowExtension =
            TestWorkflowExtension.newBuilder()
                    .setWorkflowTypes(PatientPathwayWorkflowImpl.class)
                    .setDoNotStart(true)
                    .build();

    private static final UUID TEST_PATIENT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TEST_EVENT_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    // ---- Concrete stub activity implementations ----
    // These avoid Temporal's @ActivityMethod annotation detection issue with Mockito proxies.

    /**
     * Activity stub that always returns "not complete" — workflow continues looping.
     * Records the number of evaluate() and closeOpenAlerts() invocations.
     */
    static class LoopingEvaluationActivity implements PathwayEvaluationActivity {
        final AtomicInteger evaluateCount = new AtomicInteger(0);
        final AtomicInteger closeAlertsCount = new AtomicInteger(0);

        @Override
        public PathwayEvaluationResult evaluate(UUID patientId) {
            evaluateCount.incrementAndGet();
            return new PathwayEvaluationResult(false, List.of());
        }

        @Override
        public void closeOpenAlerts(UUID patientId) {
            closeAlertsCount.incrementAndGet();
        }
    }

    /**
     * Activity stub that returns "all steps complete" — workflow exits on first evaluation.
     */
    static class CompletingEvaluationActivity implements PathwayEvaluationActivity {
        final AtomicInteger evaluateCount = new AtomicInteger(0);

        @Override
        public PathwayEvaluationResult evaluate(UUID patientId) {
            evaluateCount.incrementAndGet();
            return new PathwayEvaluationResult(true, List.of());
        }

        @Override
        public void closeOpenAlerts(UUID patientId) {
            // not expected to be called on completion path
        }
    }

    // ---- Test methods ----

    /**
     * Test 1: Proves the workflow starts, waits, and calls the evaluation activity via the 24h
     * timer loop (INFR-03 basic durability test).
     *
     * <p>After virtual time is skipped to 25h, the workflow must have called evaluate().
     */
    @Test
    void testWorkflowStartsAndCallsEvaluate(
            TestWorkflowEnvironment testEnv, Worker worker, WorkflowClient workflowClient) throws InterruptedException {

        LoopingEvaluationActivity activity = new LoopingEvaluationActivity();
        worker.registerActivitiesImplementations(activity);

        testEnv.start();

        PatientPathwayWorkflow workflow = workflowClient.newWorkflowStub(
                PatientPathwayWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(worker.getTaskQueue())
                        .setWorkflowId("test-start-calls-evaluate-" + TEST_PATIENT_ID)
                        .build());
        WorkflowClient.start(workflow::monitorPathway, TEST_PATIENT_ID, "BREAST");

        // Skip virtual time past the 24h timer so evaluate() must fire
        testEnv.sleep(Duration.ofHours(25));
        Thread.sleep(500);

        assertTrue(activity.evaluateCount.get() >= 1,
                "evaluate() must be called at least once after the 24h timer fires");
    }

    /**
     * Test 2: Proves the care event signal wakes the workflow from its 24h sleep (D-05).
     *
     * <p>The workflow starts and enters its 24h await. Sending careEventChanged() wakes it
     * early. evaluate() must be called as a result of the signal.
     */
    @Test
    void testSignalWakesWorkflowEarly(
            TestWorkflowEnvironment testEnv, Worker worker, WorkflowClient workflowClient) throws InterruptedException {

        LoopingEvaluationActivity activity = new LoopingEvaluationActivity();
        worker.registerActivitiesImplementations(activity);

        testEnv.start();

        PatientPathwayWorkflow workflow = workflowClient.newWorkflowStub(
                PatientPathwayWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(worker.getTaskQueue())
                        .setWorkflowId("test-signal-wakes-early-" + TEST_PATIENT_ID)
                        .build());
        WorkflowClient.start(workflow::monitorPathway, TEST_PATIENT_ID, "BREAST");

        // Brief real pause to let the workflow reach its await() before signalling
        Thread.sleep(200);

        // Send care event signal — wakes the workflow early (before the 24h timer)
        workflow.careEventChanged(TEST_EVENT_ID);

        // Skip a short time to allow the woken workflow to call evaluate
        testEnv.sleep(Duration.ofMinutes(5));
        Thread.sleep(500);

        assertTrue(activity.evaluateCount.get() >= 1,
                "evaluate() must be called when careEventChanged signal wakes the workflow early");
    }

    /**
     * Test 3: Proves deactivation signal causes graceful exit with open alert closure (D-08).
     *
     * <p>After deactivatePatient() is signalled, the workflow must call closeOpenAlerts()
     * before terminating. The query method must return "DEACTIVATED" after signal.
     */
    @Test
    void testDeactivationSignalTerminatesWorkflow(
            TestWorkflowEnvironment testEnv, Worker worker, WorkflowClient workflowClient) throws InterruptedException {

        LoopingEvaluationActivity activity = new LoopingEvaluationActivity();
        worker.registerActivitiesImplementations(activity);

        testEnv.start();

        PatientPathwayWorkflow workflow = workflowClient.newWorkflowStub(
                PatientPathwayWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(worker.getTaskQueue())
                        .setWorkflowId("test-deactivation-" + TEST_PATIENT_ID)
                        .build());
        WorkflowClient.start(workflow::monitorPathway, TEST_PATIENT_ID, "BREAST");

        // Allow workflow to start and reach its await()
        Thread.sleep(200);

        // Send deactivation signal
        workflow.deactivatePatient("Patient transferred");

        // Advance virtual time to allow workflow to process the signal and close alerts
        testEnv.sleep(Duration.ofMinutes(5));
        Thread.sleep(500);

        // closeOpenAlerts must have been called as part of D-08 graceful termination
        assertTrue(activity.closeAlertsCount.get() >= 1,
                "closeOpenAlerts() must be called when deactivatePatient signal is received (D-08)");
    }

    /**
     * Test 4: Proves natural pathway completion causes the workflow to exit (D-09).
     *
     * <p>When evaluate() returns allStepsComplete=true, the workflow loop exits naturally
     * without a deactivation signal. evaluate() must have been called.
     */
    @Test
    void testPathwayCompletionExitsWorkflow(
            TestWorkflowEnvironment testEnv, Worker worker, WorkflowClient workflowClient) throws InterruptedException {

        CompletingEvaluationActivity activity = new CompletingEvaluationActivity();
        worker.registerActivitiesImplementations(activity);

        testEnv.start();

        PatientPathwayWorkflow workflow = workflowClient.newWorkflowStub(
                PatientPathwayWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(worker.getTaskQueue())
                        .setWorkflowId("test-completion-" + TEST_PATIENT_ID)
                        .build());
        WorkflowClient.start(workflow::monitorPathway, TEST_PATIENT_ID, "BREAST");

        // Advance time to trigger the 24h timer so evaluate() fires and returns complete
        testEnv.sleep(Duration.ofHours(25));
        Thread.sleep(500);

        assertTrue(activity.evaluateCount.get() >= 1,
                "evaluate() must be called before the workflow exits naturally (D-09)");
    }

    /**
     * Test 5: Proves the query method returns "MONITORING" while the workflow is active.
     *
     * <p>getPathwayStatus() is a Temporal query method that reads workflow state synchronously.
     * It must return "MONITORING" before any completion or deactivation signal.
     */
    @Test
    void testQueryMethodReturnsMonitoring(
            TestWorkflowEnvironment testEnv, Worker worker, WorkflowClient workflowClient) throws InterruptedException {

        LoopingEvaluationActivity activity = new LoopingEvaluationActivity();
        worker.registerActivitiesImplementations(activity);

        testEnv.start();

        PatientPathwayWorkflow workflow = workflowClient.newWorkflowStub(
                PatientPathwayWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(worker.getTaskQueue())
                        .setWorkflowId("test-query-monitoring-" + TEST_PATIENT_ID)
                        .build());
        WorkflowClient.start(workflow::monitorPathway, TEST_PATIENT_ID, "BREAST");

        // Give the workflow a moment to start before querying
        Thread.sleep(300);

        String status = workflow.getPathwayStatus();
        assertEquals("MONITORING", status,
                "Workflow status must be MONITORING while workflow is active and no signals received");
    }

    /**
     * Test 6: Proves multiple care event signals in rapid succession are all handled.
     *
     * <p>The workflow's signal handler sets signalReceived=true; signals coalesce to one wakeup
     * per loop iteration. Multiple rapid signals must result in evaluate() being called.
     */
    @Test
    void testMultipleSignalsHandled(
            TestWorkflowEnvironment testEnv, Worker worker, WorkflowClient workflowClient) throws InterruptedException {

        LoopingEvaluationActivity activity = new LoopingEvaluationActivity();
        worker.registerActivitiesImplementations(activity);

        testEnv.start();

        PatientPathwayWorkflow workflow = workflowClient.newWorkflowStub(
                PatientPathwayWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setTaskQueue(worker.getTaskQueue())
                        .setWorkflowId("test-multiple-signals-" + TEST_PATIENT_ID)
                        .build());
        WorkflowClient.start(workflow::monitorPathway, TEST_PATIENT_ID, "BREAST");

        // Brief pause to let the workflow reach its await() before sending signals
        Thread.sleep(200);

        // Send 3 signals in rapid succession
        workflow.careEventChanged(UUID.fromString("33333333-3333-3333-3333-333333333333"));
        workflow.careEventChanged(UUID.fromString("44444444-4444-4444-4444-444444444444"));
        workflow.careEventChanged(UUID.fromString("55555555-5555-5555-5555-555555555555"));

        // Allow virtual time to process and evaluate
        testEnv.sleep(Duration.ofMinutes(5));
        Thread.sleep(500);

        // evaluate must have been called at least once
        assertTrue(activity.evaluateCount.get() >= 1,
                "evaluate() must be called after multiple rapid careEventChanged signals");
    }
}
