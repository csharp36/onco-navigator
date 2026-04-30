package com.onconavigator.config;

import org.springframework.context.annotation.Configuration;

/**
 * Temporal configuration constants.
 *
 * <p>Worker auto-discovery is configured in application-local.yml via:
 * <pre>
 * spring.temporal:
 *   workers-auto-discovery:
 *     packages:
 *       - com.onconavigator.workflow
 *       - com.onconavigator.activity
 * </pre>
 *
 * <p>These constants are shared between the workflow implementations and PathwayService
 * to ensure consistent task queue names and workflow ID formats across the application.
 */
@Configuration(proxyBeanMethods = false)
public class TemporalConfig {

    /**
     * Temporal task queue name for all pathway-related workflows and activities.
     * Workers in the {@code com.onconavigator.workflow} and {@code com.onconavigator.activity}
     * packages are registered to poll this queue.
     */
    public static final String TASK_QUEUE = "onco-pathway-queue";

    /**
     * Prefix for patient pathway workflow IDs. Full ID format: "pathway-{patientId}".
     * Using this prefix ensures workflow IDs are human-readable and consistent.
     * WorkflowIdReusePolicy.ALLOW_DUPLICATE allows re-enrollment after deactivation (D-08).
     */
    public static final String PATHWAY_WORKFLOW_ID_PREFIX = "pathway-";

    /**
     * Fixed workflow ID for the daily sweep cron workflow.
     * Only one sweep workflow runs at a time; Temporal deduplicates by workflow ID.
     */
    public static final String SWEEP_WORKFLOW_ID = "daily-pathway-sweep";

    /**
     * Cron schedule for the daily sweep workflow: runs at 6 AM every day.
     * Temporal uses standard cron syntax. This is a safety-net sweep — pathway monitoring
     * is event-driven (D-05), but cron ensures any patient missed by signals is caught daily.
     */
    public static final String CRON_SCHEDULE = "0 6 * * *";

    private TemporalConfig() {
        // Utility class with constants only — no instantiation needed.
        // Spring @Configuration allows this to be a Spring-managed bean if needed.
    }
}
