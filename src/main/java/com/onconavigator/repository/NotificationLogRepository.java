package com.onconavigator.repository;

import com.onconavigator.domain.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link NotificationLog} entities.
 *
 * <p>Provides query methods for the immutable notification dispatch audit trail.
 * Used for debugging delivery issues and HIPAA audit compliance.
 */
@Repository
public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    /**
     * Find all notification log entries for a specific alert.
     *
     * @param alertId the alert UUID
     * @return notification log entries for the alert
     */
    List<NotificationLog> findByAlertId(UUID alertId);

    /**
     * Find all notification log entries for a user, ordered by most recent first.
     *
     * @param userId the user UUID
     * @return notification log entries for the user, newest first
     */
    List<NotificationLog> findByUserIdOrderBySentAtDesc(UUID userId);
}
