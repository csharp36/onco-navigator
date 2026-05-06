package com.onconavigator.workflow;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Digest dispatch workflow: drains the notification_pending_queue for items
 * whose hold_until time has elapsed.
 *
 * <p>Dispatches both quiet-hours held items (type=QUIET_HOURS) and digest
 * batched items (type=DIGEST) that are past their hold_until timestamp.
 *
 * <p>Scheduled via Temporal Schedules API (ScheduleClient.createSchedule)
 * running every 30 minutes. A single shared schedule, not per-user workflows (D-13).
 *
 * <p>CRITICAL -- Determinism constraints:
 * <ul>
 *   <li>NO database access -- delegated to DigestDispatchActivity</li>
 *   <li>NO Spring/JPA imports -- workflow classes run in Temporal's context</li>
 * </ul>
 */
@WorkflowInterface
public interface DigestDispatchWorkflow {

    @WorkflowMethod
    void runDigestPass();
}
