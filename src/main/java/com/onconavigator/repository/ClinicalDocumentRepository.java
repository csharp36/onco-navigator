package com.onconavigator.repository;

import com.onconavigator.domain.ClinicalDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link ClinicalDocument} entities.
 *
 * <p>HIPAA note: Query results may contain encrypted PHI fields.
 * Never log entity field values — log only document IDs (UUIDs).
 */
@Repository
public interface ClinicalDocumentRepository extends JpaRepository<ClinicalDocument, UUID> {

    /**
     * Find all documents for a patient, ordered by creation date descending.
     *
     * @param patientId the patient UUID
     * @return documents ordered most recent first
     */
    List<ClinicalDocument> findByPatient_IdOrderByCreatedAtDesc(UUID patientId);

    /**
     * Find a document linked to a specific care event.
     *
     * @param careEventId the care event UUID
     * @return the linked document, if any
     */
    Optional<ClinicalDocument> findByCareEventId(UUID careEventId);
}
