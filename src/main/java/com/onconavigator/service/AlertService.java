package com.onconavigator.service;

import com.onconavigator.domain.Alert;
import com.onconavigator.domain.Patient;
import com.onconavigator.domain.enums.AlertStatus;
import com.onconavigator.domain.enums.AlertType;
import com.onconavigator.repository.AlertRepository;
import com.onconavigator.repository.PatientRepository;
import com.onconavigator.web.dto.AlertResponse;
import com.onconavigator.web.dto.ResolveAlertRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service for alert queries, severity ordering, and resolution.
 *
 * <p>Provides severity-ordered alert retrieval using the
 * {@link AlertRepository#findByStatusOrderedBySeverity} query, which orders
 * DELAYED_EVENT (overdue) first, MISSING_EVENT second, OUT_OF_ORDER third.
 *
 * <p>PHI safety: Log statements contain only alert UUIDs and actor UUIDs.
 * Patient names and MRNs are accessed for DTO mapping only — never logged.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertRepository alertRepository;
    private final PatientRepository patientRepository;

    public AlertService(AlertRepository alertRepository, PatientRepository patientRepository) {
        this.alertRepository = alertRepository;
        this.patientRepository = patientRepository;
    }

    /**
     * Returns all open alerts ordered by clinical severity.
     *
     * <p>Ordering: DELAYED_EVENT (overdue, highest urgency) → MISSING_EVENT → OUT_OF_ORDER.
     * Within each tier, alerts are ordered by creation time ascending (oldest first).
     *
     * @return severity-ordered list of open alerts as response DTOs
     */
    public List<AlertResponse> getOpenAlerts() {
        List<Alert> alerts = alertRepository.findByStatusOrderedBySeverity(AlertStatus.OPEN);
        return alerts.stream()
                .map(this::toAlertResponse)
                .toList();
    }

    /**
     * Returns the count of currently open alerts.
     *
     * <p>Used for the sidebar badge polling endpoint (lightweight COUNT query).
     *
     * @return count of open alerts
     */
    public long countOpenAlerts() {
        return alertRepository.countByStatus(AlertStatus.OPEN);
    }

    /**
     * Resolves an alert by marking it RESOLVED with resolution notes and actor identity.
     *
     * @param alertId alert UUID to resolve
     * @param req     resolution request with notes
     * @param actorId UUID of the nurse navigator performing the resolution
     * @throws ResponseStatusException 404 if alert not found
     */
    public void resolveAlert(UUID alertId, ResolveAlertRequest req, UUID actorId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found"));

        alert.setStatus(AlertStatus.RESOLVED);
        alert.setResolvedAt(OffsetDateTime.now());
        alert.setResolvedBy(actorId);
        alert.setResolutionNotes(req.notes());

        alertRepository.save(alert);

        log.info("Alert {} resolved by actor {}", alertId, actorId);
    }

    // ---- Private helpers ----

    /**
     * Maps an Alert entity to an AlertResponse DTO.
     *
     * <p>Loads the associated patient to obtain decrypted name and MRN.
     * PHI fields are decrypted by EncryptionConverter when loaded from the database —
     * they are accessed here for DTO mapping only, never logged.
     *
     * @param a the alert entity
     * @return response DTO with patient name, MRN, severity label, and time elapsed
     */
    private AlertResponse toAlertResponse(Alert a) {
        Patient patient = patientRepository.findById(a.getPatientId()).orElse(null);

        String patientName = null;
        String patientMrn = null;
        if (patient != null) {
            // PHI decrypted by EncryptionConverter on JPA load — safe for DTO, never log
            patientName = patient.getFirstName() + " " + patient.getLastName();
            patientMrn = patient.getMrn();
        }

        return new AlertResponse(
                a.getId(),
                a.getPatientId(),
                patientName,
                patientMrn,
                a.getAlertType() != null ? a.getAlertType().name() : null,
                toSeverityLabel(a.getAlertType()),
                a.getStatus() != null ? a.getStatus().name() : null,
                a.getPathwayStepName(),
                a.getDeviationDescription(),
                a.getSuggestedAction(),
                a.getCreatedAt(),
                computeTimeElapsed(a.getCreatedAt())
        );
    }

    /**
     * Maps an AlertType enum value to the display severity label used in the nurse dashboard.
     *
     * <ul>
     *   <li>DELAYED_EVENT → "OVERDUE"</li>
     *   <li>MISSING_EVENT → "MISSING"</li>
     *   <li>OUT_OF_ORDER  → "OUT OF ORDER"</li>
     * </ul>
     */
    private String toSeverityLabel(AlertType type) {
        if (type == null) {
            return "UNKNOWN";
        }
        return switch (type) {
            case DELAYED_EVENT -> "OVERDUE";
            case MISSING_EVENT -> "MISSING";
            case OUT_OF_ORDER  -> "OUT OF ORDER";
        };
    }

    /**
     * Computes a human-readable time elapsed string from a creation timestamp to now.
     *
     * <p>Examples: "just now", "5 hours ago", "3 days ago", "2 weeks ago"
     *
     * @param createdAt the timestamp of when the alert was created
     * @return human-readable elapsed time string
     */
    private String computeTimeElapsed(OffsetDateTime createdAt) {
        if (createdAt == null) {
            return "unknown";
        }
        Duration duration = Duration.between(createdAt, OffsetDateTime.now());
        long totalMinutes = duration.toMinutes();

        if (totalMinutes < 1) {
            return "just now";
        } else if (totalMinutes < 60) {
            return totalMinutes + " minutes ago";
        } else if (totalMinutes < 60 * 24) {
            long hours = totalMinutes / 60;
            return hours + (hours == 1 ? " hour ago" : " hours ago");
        } else if (totalMinutes < 60 * 24 * 7) {
            long days = totalMinutes / (60 * 24);
            return days + (days == 1 ? " day ago" : " days ago");
        } else if (totalMinutes < 60 * 24 * 30) {
            long weeks = totalMinutes / (60 * 24 * 7);
            return weeks + (weeks == 1 ? " week ago" : " weeks ago");
        } else {
            long months = totalMinutes / (60 * 24 * 30);
            return months + (months == 1 ? " month ago" : " months ago");
        }
    }
}
