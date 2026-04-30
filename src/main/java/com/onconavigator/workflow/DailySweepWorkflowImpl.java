package com.onconavigator.workflow;

import com.onconavigator.activity.SweepActivity;
import com.onconavigator.config.TemporalConfig;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

import java.time.Duration;

/**
 * Daily sweep workflow implementation.
 *
 * <p>Runs on the cron schedule defined in {@link com.onconavigator.config.TemporalConfig#CRON_SCHEDULE}
 * (6 AM daily). Each execution is independent — Temporal cron workflows do not carry state
 * between runs.
 *
 * <p>Complexity is intentionally delegated to {@link SweepActivity}. The workflow class
 * remains simple so that determinism constraints are trivially satisfied.
 *
 * <p>CRITICAL — Determinism constraints (required by Temporal):
 * <ul>
 *   <li>NO database access — delegated to SweepActivity</li>
 *   <li>NO Spring/JPA imports — workflow classes run in Temporal's context, not Spring's</li>
 * </ul>
 */
@WorkflowImpl(taskQueues = TemporalConfig.TASK_QUEUE)
public class DailySweepWorkflowImpl implements DailySweepWorkflow {

    // Longer timeout (5 minutes) — sweep may query many patients and start multiple workflows
    private final SweepActivity sweepActivity = Workflow.newActivityStub(
            SweepActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(5))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(3)
                            .setInitialInterval(Duration.ofSeconds(10))
                            .setBackoffCoefficient(2.0)
                            .build())
                    .build());

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to {@link SweepActivity#findAndStartMissingWorkflows()}, which handles
     * querying active patients and starting any missing pathway workflows.
     */
    @Override
    public void sweep() {
        sweepActivity.findAndStartMissingWorkflows();
    }
}
