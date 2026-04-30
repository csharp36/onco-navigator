package com.onconavigator.repository;

import com.onconavigator.domain.Alert;
import com.onconavigator.domain.enums.AlertStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link Alert} entities.
 *
 * <p>Includes deduplication check method used by the pathway engine to avoid creating
 * duplicate OPEN alerts for the same patient and pathway step.
 */
@Repository
public interface AlertRepository extends JpaRepository<Alert, UUID> {

    /**
     * Find all alerts in a given status, ordered by creation time descending (newest first).
     * Used for the nurse navigator dashboard alert queue.
     *
     * @param status the alert status to filter by
     * @return alerts ordered most recent first
     */
    List<Alert> findByStatusOrderByCreatedAtDesc(AlertStatus status);

    /**
     * Find all alerts for a specific patient with a given status.
     *
     * @param patientId the patient UUID
     * @param status    the alert status to filter by
     * @return matching alerts
     */
    List<Alert> findByPatientIdAndStatus(UUID patientId, AlertStatus status);

    /**
     * Check whether an open (or acknowledged) alert already exists for a patient's
     * specific pathway step. Used to prevent duplicate alert creation when the pathway
     * engine re-evaluates on each care event.
     *
     * @param patientId       the patient UUID
     * @param pathwayStepName the name of the pathway step in deviation
     * @param status          the alert status to check against (typically OPEN)
     * @return true if a matching alert already exists
     */
    boolean existsByPatientIdAndPathwayStepNameAndStatus(
            UUID patientId, String pathwayStepName, AlertStatus status);
}
