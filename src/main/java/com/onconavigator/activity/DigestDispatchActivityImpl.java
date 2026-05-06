package com.onconavigator.activity;

import com.onconavigator.domain.NotificationPendingQueue;
import com.onconavigator.notification.NotificationService;
import com.onconavigator.repository.NotificationPendingQueueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Digest dispatch activity implementation.
 *
 * <p>Queries notification_pending_queue for PENDING items whose hold_until has elapsed,
 * dispatches them via NotificationService.dispatchFromQueue (which handles notification_log
 * persistence consistently), and marks them DISPATCHED.
 *
 * <p>PHI safety: The rendered_content is decrypted by JPA's EncryptionConverter when
 * loaded from the pending queue, then passed to NotificationService which re-encrypts
 * when persisting to notification_log. This activity never logs PHI content directly.
 */
@Component
public class DigestDispatchActivityImpl implements DigestDispatchActivity {

    private static final Logger log = LoggerFactory.getLogger(DigestDispatchActivityImpl.class);

    private final NotificationPendingQueueRepository pendingQueueRepository;
    private final NotificationService notificationService;

    public DigestDispatchActivityImpl(NotificationPendingQueueRepository pendingQueueRepository,
                                      NotificationService notificationService) {
        this.pendingQueueRepository = pendingQueueRepository;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public void drainPendingQueue() {
        OffsetDateTime now = OffsetDateTime.now();
        List<NotificationPendingQueue> readyItems =
                pendingQueueRepository.findByStatusAndHoldUntilBefore("PENDING", now);

        if (readyItems.isEmpty()) {
            log.debug("DIGEST_SWEEP: no pending items ready for dispatch");
            return;
        }

        // Separate by hold type
        Map<String, List<NotificationPendingQueue>> byHoldType = readyItems.stream()
                .collect(Collectors.groupingBy(NotificationPendingQueue::getHoldType));

        // QUIET_HOURS items: dispatch individually
        List<NotificationPendingQueue> quietHoursItems = byHoldType.getOrDefault("QUIET_HOURS", List.of());
        for (NotificationPendingQueue item : quietHoursItems) {
            dispatchItem(item, false);
        }

        // DIGEST items: group by user, dispatch as batches
        List<NotificationPendingQueue> digestItems = byHoldType.getOrDefault("DIGEST", List.of());
        Map<UUID, List<NotificationPendingQueue>> byUser = digestItems.stream()
                .collect(Collectors.groupingBy(NotificationPendingQueue::getUserId));

        for (Map.Entry<UUID, List<NotificationPendingQueue>> entry : byUser.entrySet()) {
            UUID userId = entry.getKey();
            List<NotificationPendingQueue> userBatch = entry.getValue();

            log.info("DIGEST_BATCH_DISPATCHED: user={} items={} [LOG-ONLY]", userId, userBatch.size());

            for (NotificationPendingQueue item : userBatch) {
                dispatchItem(item, true);
            }
        }

        int total = readyItems.size();
        log.info("DIGEST_SWEEP: dispatched={} (quietHours={} digest={})",
                total, quietHoursItems.size(), digestItems.size());
    }

    private void dispatchItem(NotificationPendingQueue item, boolean isDigest) {
        // Delegate dispatch to NotificationService for consistent notification_log handling
        // The renderedContentEncrypted is decrypted by JPA EncryptionConverter on entity load
        notificationService.dispatchFromQueue(
                item.getAlertId(),
                item.getUserId(),
                item.getChannel(),
                item.getRenderedContentEncrypted(),
                isDigest
        );

        // Mark as dispatched in the pending queue
        item.setStatus("DISPATCHED");
        pendingQueueRepository.save(item);

        log.debug("NOTIFICATION_DISPATCHED_FROM_QUEUE: alert={} user={} channel={} type={}",
                item.getAlertId(), item.getUserId(), item.getChannel(), item.getHoldType());
    }
}
