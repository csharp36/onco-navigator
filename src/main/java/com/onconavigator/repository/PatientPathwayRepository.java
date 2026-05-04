package com.onconavigator.repository;

import com.onconavigator.domain.PatientPathway;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link PatientPathway} entities.
 *
 * <p>Each patient has exactly one pathway (enforced by the database UNIQUE constraint
 * on {@code patient_id}). The lookup methods here reflect that invariant.
 */
@Repository
public interface PatientPathwayRepository extends JpaRepository<PatientPathway, UUID> {

    /**
     * Find the pathway for a specific patient.
     *
     * @param patientId the patient UUID
     * @return the patient's pathway, or empty if not yet enrolled
     */
    Optional<PatientPathway> findByPatient_Id(UUID patientId);

    /**
     * Check whether a pathway exists for a specific patient.
     *
     * <p>Used before creating a new pathway to prevent duplicate enrollment.
     *
     * @param patientId the patient UUID
     * @return {@code true} if a pathway already exists for this patient
     */
    boolean existsByPatient_Id(UUID patientId);
}
