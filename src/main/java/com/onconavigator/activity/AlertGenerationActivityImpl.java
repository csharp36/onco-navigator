package com.onconavigator.activity;

import com.onconavigator.domain.Alert;
import com.onconavigator.domain.Patient;
import com.onconavigator.domain.enums.AlertStatus;
import com.onconavigator.domain.enums.AlertType;
import com.onconavigator.notification.NotificationService;
import com.onconavigator.repository.AlertRepository;
import com.onconavigator.repository.PatientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Standalone alert creation activity with built-in deduplication (PATH-06).
 *
 * <p>This activity can be called independently from workflow code or other activity orchestration
 * patterns. It checks for an existing OPEN alert with the same (patient, step) key before
 * inserting — making it safe to call multiple times (idempotent).
 *
 * <p>Note: {@link PathwayEvaluationActivityImpl} creates alerts directly during evaluation for
 * efficiency (single-pass evaluation and creation). This class exists as a standalone activity
 * for future workflow code paths that need to generate a single alert without a full evaluation.
 *
 * <p>PHI safety: All parameters are non-PHI. The {@code deviationDescription} and
 * {@code suggestedAction} parameters contain pathway template text (admin-controlled JSONB),
 * not patient-specific data.
 */
@Component
public class AlertGenerationActivityImpl implements AlertGenerationActivity {

    private static final Logger log = LoggerFactory.getLogger(AlertGenerationActivityImpl.class);

    private final AlertRepository alertRepository;
    private final NotificationService notificationService;
    private final PatientRepository patientRepository;

    public AlertGenerationActivityImpl(AlertRepository alertRepository,
                                       NotificationService notificationService,
                                       PatientRepository patientRepository) {
        this.alertRepository = alertRepository;
        this.notificationService = notificationService;
        this.patientRepository = patientRepository;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Deduplication key: OPEN alert with matching (patientId, pathwayStepName). If one
     * already exists, this method logs the skip and returns without creating a duplicate.
     * If no duplicate exists, a new Alert entity is persisted and the creation is logged.
     */
    @Override
    public void generateAlert(UUID patientId, String pathwayStepName, String alertTypeStr,
                              String deviationDescription, String suggestedAction,
                              String missingSummary, String workflowRunId) {

        // Dedup check (PATH-06): do not create a second OPEN alert for the same (patient, step)
        boolean duplicateExists = alertRepository.existsByPatientIdAndPathwayStepNameAndStatus(
                patientId, pathwayStepName, AlertStatus.OPEN);
        if (duplicateExists) {
            log.debug("Duplicate alert skipped: patient={} step={} type={} (OPEN alert already exists)",
                    patientId, pathwayStepName, alertTypeStr);
            return;
        }

        Alert alert = new Alert();
        alert.setPatientId(patientId);
        alert.setAlertType(AlertType.valueOf(alertTypeStr));
        alert.setPathwayStepName(pathwayStepName);
        alert.setDeviationDescription(deviationDescription);
        alert.setSuggestedAction(cap150(suggestedAction, "suggestedAction", patientId));
        alert.setMissingSummary(cap150(missingSummary, "missingSummary", patientId));
        alert.setWorkflowRunId(workflowRunId);
        alertRepository.save(alert);

        // Dispatch notification (D-06)
        Patient patient = patientRepository.findById(patientId).orElse(null);
        if (patient != null) {
            notificationService.dispatchForAlert(alert,
                    patient.getFirstName() + " " + patient.getLastName(),
                    patient.getMrn());
        }

        log.info("ALERT_GENERATED: patient={} step={} type={}", patientId, pathwayStepName, alertTypeStr);
    }

    /**
     * Truncates a string to 150 characters with a warning log if truncation occurs.
     * Enforces the PW-ALL-007 constraint.
     */
    private String cap150(String value, String fieldName, UUID patientId) {
        if (value == null) return null;
        if (value.length() > 150) {
            log.warn("ALERT_FIELD_TRUNCATED: field={} patient={}", fieldName, patientId);
            return value.substring(0, 150);
        }
        return value;
    }
}
