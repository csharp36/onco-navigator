package com.onconavigator.notification;

/**
 * Immutable value object representing the rendered notification content.
 *
 * <p>HIPAA note: Contains PHI (patientName, patientMrn). Must not be logged.
 * Rendered as a string and encrypted before storage in notification_log.
 *
 * @param patientName     decrypted patient name (PHI -- do not log)
 * @param patientMrn      decrypted MRN (PHI -- do not log)
 * @param pathwayStepName the step where the deviation occurred (non-PHI)
 * @param severityLabel   display label e.g. "OVERDUE" (non-PHI)
 * @param missingSummary  the "what is missing" part of the two-part alert (non-PHI)
 * @param suggestedAction the corrective action part (non-PHI)
 * @param deepLink        URL to the patient pathway view in the dashboard
 */
public record NotificationPayload(
        String patientName,
        String patientMrn,
        String pathwayStepName,
        String severityLabel,
        String missingSummary,
        String suggestedAction,
        String deepLink
) {
    /**
     * Renders the payload as a human-readable string for the log-only implementation.
     * Format suitable for Teams/email when real connectors are built.
     */
    public String render() {
        return String.format(
            "[%s] %s (MRN: %s) - %s%n" +
            "What is missing: %s%n" +
            "Suggested action: %s%n" +
            "View: %s",
            severityLabel, patientName, patientMrn, pathwayStepName,
            missingSummary != null ? missingSummary : "(none)",
            suggestedAction != null ? suggestedAction : "(none)",
            deepLink
        );
    }
}
