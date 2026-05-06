package com.onconavigator.activity;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Digest dispatch activity: drains the notification_pending_queue table.
 *
 * <p>Dispatches items whose hold_until <= NOW():
 * <ul>
 *   <li>QUIET_HOURS items: dispatch individually via NotificationService.dispatchFromQueue</li>
 *   <li>DIGEST items: group by user_id, dispatch batch via NotificationService.dispatchFromQueue</li>
 * </ul>
 *
 * <p>Called by DigestDispatchWorkflowImpl on the Temporal Schedule (every 30 minutes).
 */
@ActivityInterface
public interface DigestDispatchActivity {

    @ActivityMethod
    void drainPendingQueue();
}
