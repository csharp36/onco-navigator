package com.onconavigator.repository;

import com.onconavigator.domain.Alert;
import com.onconavigator.domain.enums.AlertStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Find all alerts for a patient's specific pathway step with a given status.
     *
     * <p>Used when removing or skipping a step from a patient's pathway: any OPEN alerts
     * for that step must be resolved so they don't appear as actionable items after the
     * step is gone (per deleteStep and skipStep cascade-resolve requirement).
     *
     * @param patientId       the patient UUID
     * @param pathwayStepName the name of the pathway step
     * @param status          the alert status to filter by (typically OPEN)
     * @return matching alerts
     */
    List<Alert> findByPatientIdAndPathwayStepNameAndStatus(
            UUID patientId, String pathwayStepName, AlertStatus status);

    /**
     * Find alerts by status, ordered by clinical severity then creation time.
     *
     * <p>Severity ordering (per ALRT-01): DELAYED_EVENT (overdue, highest urgency) → 1,
     * MISSING_EVENT (not recorded, moderate urgency) → 2, OUT_OF_ORDER (sequencing issue) → 3.
     * Within the same severity tier, older alerts appear first (most urgent within tier).
     *
     * <p>Uses JPQL CASE WHEN with string literals for the AlertType enum values. String
     * literals are more reliable than full enum class paths in CASE expressions with
     * Hibernate 6 and PostgreSQL custom enum types.
     *
     * @param status the alert status to filter by (typically OPEN for the nurse dashboard)
     * @return alerts ordered by severity then creation time ascending
     */
    @Query("""
            SELECT a FROM Alert a
            WHERE a.status = :status
            ORDER BY
                CASE a.alertType
                    WHEN 'DELAYED_EVENT' THEN 1
                    WHEN 'MISSING_EVENT' THEN 2
                    WHEN 'OUT_OF_ORDER'  THEN 3
                    ELSE 4
                END ASC,
                a.createdAt ASC
            """)
    List<Alert> findByStatusOrderedBySeverity(@Param("status") AlertStatus status);

    /**
     * Count alerts by status.
     *
     * <p>Used by the dashboard stats endpoint (per ALRT-05) to display the total
     * open alert count in the dashboard header without loading all alert records.
     *
     * @param status the alert status to count
     * @return number of alerts with that status
     */
    long countByStatus(AlertStatus status);
}
