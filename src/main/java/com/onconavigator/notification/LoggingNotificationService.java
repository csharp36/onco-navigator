package com.onconavigator.notification;

import com.onconavigator.domain.Alert;
import com.onconavigator.domain.NotificationLog;
import com.onconavigator.domain.NotificationPendingQueue;
import com.onconavigator.domain.NotificationPreference;
import com.onconavigator.domain.enums.NotificationChannel;
import com.onconavigator.repository.NotificationLogRepository;
import com.onconavigator.repository.NotificationPendingQueueRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Log-only implementation of {@link NotificationService} (D-05).
 *
 * <p>Implements the full notification routing pipeline: loads preferences,
 * applies severity filtering (D-11), checks quiet hours (D-11), queues digest
 * items (D-13), and logs immediate dispatches. No real Teams or email connectors
 * are invoked -- all dispatches are logged at INFO level.
 *
 * <p>Real {@code TeamsNotificationService} and {@code EmailNotificationService}
 * implementations will be added in a future phase. This implementation validates
 * the entire pipeline routing logic.
 *
 * <p>PHI safety: Only alert UUIDs and user UUIDs are logged. Patient names and
 * MRNs are used solely for payload rendering and encrypted storage.
 */
@Service
public class LoggingNotificationService implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationService.class);

    private final NotificationPreferenceService preferenceService;
    private final NotificationLogRepository notificationLogRepository;
    private final NotificationPendingQueueRepository pendingQueueRepository;

    @Value("${onconavigator.notification.base-url:http://localhost:5173}")
    private String baseUrl;

    public LoggingNotificationService(NotificationPreferenceService preferenceService,
                                      NotificationLogRepository notificationLogRepository,
                                      NotificationPendingQueueRepository pendingQueueRepository) {
        this.preferenceService = preferenceService;
        this.notificationLogRepository = notificationLogRepository;
        this.pendingQueueRepository = pendingQueueRepository;
    }

    @Override
    public void dispatchForAlert(Alert alert, String patientName, String patientMrn) {
        // D-07: Dashboard is always-on; this dispatches to EXTERNAL channels only
        for (NotificationChannel channel : NotificationChannel.values()) {
            dispatchForChannel(alert, patientName, patientMrn, channel);
        }
    }

    @Override
    public void dispatchFromQueue(UUID alertId, UUID userId, NotificationChannel channel,
                                  String renderedContent, boolean isDigest) {
        // Log-only dispatch for queue-drained items
        log.info("NOTIFICATION_DISPATCHED_FROM_QUEUE: alert={} user={} channel={} digest={} [LOG-ONLY]",
                alertId, userId, channel, isDigest);

        // Persist to notification_log (D-09)
        NotificationLog logEntry = new NotificationLog();
        logEntry.setAlertId(alertId);
        logEntry.setUserId(userId);
        logEntry.setChannel(channel);
        logEntry.setRenderedContent(renderedContent);
        logEntry.setDigest(isDigest);
        logEntry.setStatus("SENT");
        notificationLogRepository.save(logEntry);
    }

    private void dispatchForChannel(Alert alert, String patientName, String patientMrn,
                                    NotificationChannel channel) {
        // Load all enabled preferences for this channel
        var preferences = preferenceService.getAllEnabledPreferences(channel);

        for (NotificationPreference pref : preferences) {
            UUID userId = pref.getUserId();
            if (userId == null) continue; // Skip admin default rows (no user to notify)

            // Severity filter check (D-11)
            if (!preferenceService.passesSeverityFilter(pref, alert.getAlertType().name())) {
                log.debug("NOTIFICATION_FILTERED: alert={} user={} channel={} reason=severity",
                        alert.getId(), userId, channel);
                continue;
            }

            // Build the notification payload (D-08)
            String deepLink = baseUrl + "/patients/" + alert.getPatientId();
            NotificationPayload payload = new NotificationPayload(
                    patientName,
                    patientMrn,
                    alert.getPathwayStepName(),
                    alert.getAlertType().name(),
                    alert.getMissingSummary(),
                    alert.getSuggestedAction(),
                    deepLink
            );
            String renderedContent = payload.render();

            // Digest mode: queue for batch dispatch (D-13)
            if (pref.isDigestEnabled()) {
                queuePending(alert, userId, channel, "DIGEST",
                        pref.getNextDigestAt() != null
                                ? pref.getNextDigestAt()
                                : OffsetDateTime.now().plusHours(pref.getDigestIntervalHours()),
                        renderedContent);
                log.info("NOTIFICATION_QUEUED_DIGEST: alert={} user={} channel={}",
                        alert.getId(), userId, channel);
                continue;
            }

            // Quiet hours check (D-11)
            if (preferenceService.isInQuietHours(pref)) {
                OffsetDateTime holdUntil = preferenceService.computeQuietHoursEnd(pref);
                queuePending(alert, userId, channel, "QUIET_HOURS", holdUntil, renderedContent);
                log.info("NOTIFICATION_HELD_QUIET: alert={} user={} channel={} holdUntil={}",
                        alert.getId(), userId, channel, holdUntil);
                continue;
            }

            // Immediate dispatch (log-only per D-05)
            log.info("NOTIFICATION_DISPATCHED: alert={} user={} channel={} [LOG-ONLY]",
                    alert.getId(), userId, channel);

            // Persist to notification_log (D-09)
            NotificationLog logEntry = new NotificationLog();
            logEntry.setAlertId(alert.getId());
            logEntry.setUserId(userId);
            logEntry.setChannel(channel);
            logEntry.setRenderedContent(renderedContent);
            logEntry.setDigest(false);
            logEntry.setStatus("SENT");
            notificationLogRepository.save(logEntry);
        }
    }

    private void queuePending(Alert alert, UUID userId, NotificationChannel channel,
                              String holdType, OffsetDateTime holdUntil, String renderedContent) {
        NotificationPendingQueue entry = new NotificationPendingQueue();
        entry.setAlertId(alert.getId());
        entry.setUserId(userId);
        entry.setChannel(channel);
        entry.setHoldType(holdType);
        entry.setHoldUntil(holdUntil);
        entry.setRenderedContentEncrypted(renderedContent);
        entry.setStatus("PENDING");
        pendingQueueRepository.save(entry);
    }
}
