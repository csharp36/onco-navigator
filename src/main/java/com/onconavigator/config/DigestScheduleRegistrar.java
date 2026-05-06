package com.onconavigator.config;

import com.onconavigator.workflow.DigestDispatchWorkflow;
import io.temporal.api.enums.v1.ScheduleOverlapPolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.schedules.Schedule;
import io.temporal.client.schedules.ScheduleActionStartWorkflow;
import io.temporal.client.schedules.ScheduleAlreadyRunningException;
import io.temporal.client.schedules.ScheduleClient;
import io.temporal.client.schedules.ScheduleIntervalSpec;
import io.temporal.client.schedules.ScheduleOptions;
import io.temporal.client.schedules.SchedulePolicy;
import io.temporal.client.schedules.ScheduleSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;

/**
 * Registers the digest dispatch Temporal Schedule on application startup.
 *
 * <p>The schedule runs DigestDispatchWorkflow every 30 minutes to drain the
 * notification_pending_queue (quiet-hours holds + digest batches).
 *
 * <p>Registration is idempotent: if the schedule already exists (app restart),
 * the ScheduleAlreadyRunningException is caught and logged.
 *
 * <p>Uses ScheduleOverlapPolicy.SCHEDULE_OVERLAP_POLICY_SKIP so if a previous
 * run is still executing when the next interval fires, it skips rather than queuing.
 */
@Component
public class DigestScheduleRegistrar implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DigestScheduleRegistrar.class);

    private final WorkflowClient workflowClient;

    public DigestScheduleRegistrar(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @Override
    public void run(ApplicationArguments args) {
        ScheduleClient scheduleClient = ScheduleClient.newInstance(
                workflowClient.getWorkflowServiceStubs());

        Schedule schedule = Schedule.newBuilder()
                .setAction(ScheduleActionStartWorkflow.newBuilder()
                        .setWorkflowType(DigestDispatchWorkflow.class)
                        .setOptions(WorkflowOptions.newBuilder()
                                .setWorkflowId("digest-dispatch-run")
                                .setTaskQueue(TemporalConfig.TASK_QUEUE)
                                .build())
                        .build())
                .setSpec(ScheduleSpec.newBuilder()
                        .setIntervals(Collections.singletonList(
                                new ScheduleIntervalSpec(Duration.ofMinutes(30))))
                        .build())
                .setPolicy(SchedulePolicy.newBuilder()
                        .setOverlap(ScheduleOverlapPolicy.SCHEDULE_OVERLAP_POLICY_SKIP)
                        .build())
                .build();

        try {
            scheduleClient.createSchedule(TemporalConfig.DIGEST_SCHEDULE_ID, schedule,
                    ScheduleOptions.newBuilder().build());
            log.info("DIGEST_SCHEDULE_REGISTERED: id={} interval=30min",
                    TemporalConfig.DIGEST_SCHEDULE_ID);
        } catch (ScheduleAlreadyRunningException e) {
            log.info("DIGEST_SCHEDULE_EXISTS: id={} (already registered, skipping)",
                    TemporalConfig.DIGEST_SCHEDULE_ID);
        }
    }
}
