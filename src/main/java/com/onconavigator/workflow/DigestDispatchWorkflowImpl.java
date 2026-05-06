package com.onconavigator.workflow;

import com.onconavigator.activity.DigestDispatchActivity;
import com.onconavigator.config.TemporalConfig;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.spring.boot.WorkflowImpl;
import io.temporal.workflow.Workflow;

import java.time.Duration;

/**
 * Digest dispatch workflow implementation.
 *
 * <p>Runs on the 30-minute schedule registered by DigestScheduleRegistrar.
 * Each execution is independent -- no state carried between runs.
 *
 * <p>Complexity is delegated to DigestDispatchActivity. The workflow class
 * remains minimal so determinism constraints are trivially satisfied.
 */
@WorkflowImpl(taskQueues = TemporalConfig.TASK_QUEUE)
public class DigestDispatchWorkflowImpl implements DigestDispatchWorkflow {

    private final DigestDispatchActivity digestActivity = Workflow.newActivityStub(
            DigestDispatchActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(5))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(3)
                            .setInitialInterval(Duration.ofSeconds(10))
                            .setBackoffCoefficient(2.0)
                            .build())
                    .build());

    @Override
    public void runDigestPass() {
        digestActivity.drainPendingQueue();
    }
}
