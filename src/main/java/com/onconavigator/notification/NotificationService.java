package com.onconavigator.notification;

import com.onconavigator.domain.Alert;
import com.onconavigator.domain.enums.NotificationChannel;

import java.util.UUID;

/**
 * Dispatches alert notifications to eligible users based on their channel preferences.
 * Called immediately after alertRepository.save() on every alert creation path (D-06).
 *
 * <p>Implementations MUST be PHI-safe: log only alert UUIDs and user UUIDs.
 * Never log patientName or patientMrn parameters.
 */
public interface NotificationService {

    /**
     * Dispatch notifications for a newly created alert to all eligible users.
     *
     * @param alert       the newly persisted Alert entity (non-PHI: patientId UUID only)
     * @param patientName decrypted patient name (render in payload only -- do not log)
     * @param patientMrn  decrypted patient MRN (render in payload only -- do not log)
     */
    void dispatchForAlert(Alert alert, String patientName, String patientMrn);

    /**
     * Dispatch a single pending queue item that has been held (quiet hours or digest).
     * Called by DigestDispatchActivityImpl when draining the pending queue.
     *
     * @param alertId         the alert this notification is for
     * @param userId          the recipient user
     * @param channel         the notification channel
     * @param renderedContent the already-rendered notification text (decrypted from queue)
     * @param isDigest        true if this is part of a digest batch
     */
    void dispatchFromQueue(UUID alertId, UUID userId,
                           NotificationChannel channel,
                           String renderedContent, boolean isDigest);
}
