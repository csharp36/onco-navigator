package com.onconavigator.config;

import org.springframework.context.annotation.Configuration;

/**
 * Temporal configuration constants.
 *
 * <p>Worker and activity registration is configured in application-local.yml.
 * The {@code activity-beans} key explicitly lists Spring bean names to register
 * on the worker, since auto-discovery does not reliably register activities.
 */
@Configuration(proxyBeanMethods = false)
public class TemporalConfig {

    /**
     * Temporal task queue name for all pathway-related workflows and activities.
     */
    public static final String TASK_QUEUE = "onco-pathway-queue";

    /**
     * Prefix for patient pathway workflow IDs. Full ID format: "pathway-{patientId}".
     * WorkflowIdReusePolicy.ALLOW_DUPLICATE allows re-enrollment after deactivation (D-08).
     */
    public static final String PATHWAY_WORKFLOW_ID_PREFIX = "pathway-";

    /**
     * Fixed workflow ID for the daily sweep cron workflow.
     */
    public static final String SWEEP_WORKFLOW_ID = "daily-pathway-sweep";

    /**
     * Cron schedule for the daily sweep workflow: runs at 6 AM every day.
     */
    public static final String CRON_SCHEDULE = "0 6 * * *";

    /**
     * Fixed Schedule ID for the digest dispatch periodic schedule.
     * Registered idempotently by DigestScheduleRegistrar on startup.
     */
    public static final String DIGEST_SCHEDULE_ID = "digest-dispatch-schedule";

    private TemporalConfig() {
    }
}
