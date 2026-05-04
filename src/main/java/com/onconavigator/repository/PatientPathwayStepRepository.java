package com.onconavigator.repository;

import com.onconavigator.domain.PatientPathwayStep;
import com.onconavigator.domain.enums.PathwayStepStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link PatientPathwayStep} entities.
 *
 * <p>Provides the query methods needed by the pathway evaluation engine to retrieve
 * steps by status, and by the nurse dashboard to display the full step list for a pathway.
 */
@Repository
public interface PatientPathwayStepRepository extends JpaRepository<PatientPathwayStep, UUID> {

    /**
     * Retrieve all steps for a pathway.
     *
     * @param pathwayId the pathway UUID
     * @return all steps in insertion order (insertion order preserves template step order)
     */
    List<PatientPathwayStep> findByPathway_Id(UUID pathwayId);

    /**
     * Retrieve steps for a pathway filtered by a single status.
     *
     * <p>Used by the evaluation engine to retrieve only ACTIVE steps for evaluation,
     * or to count COMPLETED/SKIPPED steps for progress reporting.
     *
     * @param pathwayId the pathway UUID
     * @param status    the status to filter by
     * @return matching steps in insertion order
     */
    List<PatientPathwayStep> findByPathway_IdAndStatus(UUID pathwayId, PathwayStepStatus status);

    /**
     * Retrieve steps for a pathway filtered by a set of statuses.
     *
     * <p>Used to retrieve both ACTIVE and PROPOSED steps simultaneously for evaluation,
     * or to combine COMPLETED and SKIPPED for reporting.
     *
     * @param pathwayId the pathway UUID
     * @param statuses  the set of statuses to include
     * @return matching steps in insertion order
     */
    List<PatientPathwayStep> findByPathway_IdAndStatusIn(UUID pathwayId, List<PathwayStepStatus> statuses);
}
