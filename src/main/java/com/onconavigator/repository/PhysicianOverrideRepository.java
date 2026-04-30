package com.onconavigator.repository;

import com.onconavigator.domain.PhysicianOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link PhysicianOverride} entities.
 *
 * <p>Provides override existence checks used by the pathway engine before alert generation,
 * and lookup methods for displaying and managing overrides per patient.
 *
 * <p>The {@code UNIQUE} index on {@code (patient_id, pathway_step_id)} guarantees that
 * {@link #existsByPatientIdAndPathwayStepId} returns a definitive answer with no ambiguity
 * about duplicate records.
 */
@Repository
public interface PhysicianOverrideRepository extends JpaRepository<PhysicianOverride, UUID> {

    /**
     * Check whether a physician override exists for a specific patient and pathway step.
     *
     * <p>Called by the pathway engine before creating an alert. If an override is present,
     * alert generation for that {@code (patient_id, pathway_step_id)} pair is suppressed.
     *
     * @param patientId     the patient UUID
     * @param pathwayStepId the pathway step identifier (e.g., "BREAST_01")
     * @return {@code true} if an override record exists for this patient + step combination
     */
    boolean existsByPatientIdAndPathwayStepId(UUID patientId, String pathwayStepId);

    /**
     * Retrieve all active physician overrides for a patient.
     *
     * <p>Used when displaying the patient's full pathway status in the nurse dashboard,
     * to annotate overridden steps with their recorded reason.
     *
     * @param patientId the patient UUID
     * @return all override records for the patient, or an empty list if none exist
     */
    List<PhysicianOverride> findByPatientId(UUID patientId);

    /**
     * Retrieve the override record for a specific patient and pathway step.
     *
     * <p>Used to display the override reason when a step is suppressed, and to support
     * override removal if the physician rescinds the override decision.
     *
     * @param patientId     the patient UUID
     * @param pathwayStepId the pathway step identifier (e.g., "BREAST_01")
     * @return the override record, or empty if no override exists for this combination
     */
    Optional<PhysicianOverride> findByPatientIdAndPathwayStepId(UUID patientId, String pathwayStepId);
}
