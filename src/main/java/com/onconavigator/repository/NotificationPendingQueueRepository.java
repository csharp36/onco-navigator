package com.onconavigator.repository;

import com.onconavigator.domain.NotificationPendingQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link NotificationPendingQueue} entities.
 *
 * <p>Used by the DigestDispatchActivity to drain pending notifications
 * whose hold_until time has elapsed (both QUIET_HOURS and DIGEST types).
 */
@Repository
public interface NotificationPendingQueueRepository extends JpaRepository<NotificationPendingQueue, UUID> {

    /**
     * Find pending queue items ready for dispatch (hold_until has elapsed).
     * Used by the digest sweep to drain items past their hold time.
     *
     * @param status the status to filter by (typically "PENDING")
     * @param now    the current timestamp; items with hold_until before this are ready
     * @return items ready for dispatch
     */
    List<NotificationPendingQueue> findByStatusAndHoldUntilBefore(String status, OffsetDateTime now);

    /**
     * Find pending items for a specific user filtered by status and hold type.
     * Used to check if a user already has pending digest items.
     *
     * @param userId   the user UUID
     * @param status   the status to filter by
     * @param holdType the hold type (QUIET_HOURS or DIGEST)
     * @return matching pending queue items
     */
    List<NotificationPendingQueue> findByUserIdAndStatusAndHoldType(UUID userId, String status, String holdType);
}
