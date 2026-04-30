package com.onconavigator.web.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for an alert surfaced by the pathway monitoring engine (per ALRT-02).
 *
 * <p>Contains both the raw {@code alertType} (enum name for programmatic use) and
 * {@code severityLabel} (display text for the nurse navigator dashboard):
 * <ul>
 *   <li>DELAYED_EVENT → "OVERDUE"</li>
 *   <li>MISSING_EVENT → "MISSING"</li>
 *   <li>OUT_OF_ORDER → "OUT OF ORDER"</li>
 * </ul>
 *
 * <p>Fields {@code patientName} and {@code patientMrn} are decrypted server-side from
 * their {@code BYTEA} columns. Transmitted over TLS to authenticated JWT holders only.
 *
 * <p>HIPAA note: Never log {@code patientName} or {@code patientMrn} in backend logs.
 * The frontend must not store these in plaintext logs or local storage beyond the session.
 *
 * @param timeElapsed human-readable elapsed time since alert was created (e.g., "3 days ago")
 */
public record AlertResponse(
        UUID id,
        UUID patientId,
        String patientName,
        String patientMrn,
        String alertType,
        String severityLabel,
        String status,
        String pathwayStepName,
        String deviationDescription,
        String suggestedAction,
        OffsetDateTime createdAt,
        String timeElapsed
) {}
