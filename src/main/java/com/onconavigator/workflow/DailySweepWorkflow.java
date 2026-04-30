package com.onconavigator.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Daily safety-net workflow that ensures all active patients have a running pathway workflow.
 *
 * <p>Scheduled via Temporal cron (see {@link com.onconavigator.config.TemporalConfig#CRON_SCHEDULE}).
 * Runs once per day at 6 AM as a safety net to catch any patients that missed event-driven signals.
 *
 * <p>The actual sweep logic (querying active patients, checking for running workflows, starting
 * missing workflows) is delegated to {@link com.onconavigator.activity.SweepActivity}. The
 * workflow itself is intentionally minimal — Temporal's cron scheduling handles repetition.
 */
@WorkflowInterface
public interface DailySweepWorkflow {

    /**
     * Executes the daily sweep: finds all active patients without a running pathway workflow
     * and starts one for each.
     *
     * <p>Called on the cron schedule by Temporal. Each invocation is a complete, independent
     * execution — Temporal cron workflows do not carry state between runs.
     */
    @WorkflowMethod
    void sweep();
}
