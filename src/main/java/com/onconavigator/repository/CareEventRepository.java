package com.onconavigator.repository;

import com.onconavigator.domain.CareEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link CareEvent} entities.
 */
@Repository
public interface CareEventRepository extends JpaRepository<CareEvent, UUID> {

    /**
     * Find all care events for a patient, ordered by event date descending.
     * Uses the patient.id field traversal via the ManyToOne relationship.
     *
     * @param patientId the patient UUID
     * @return care events ordered most recent first
     */
    List<CareEvent> findByPatient_IdOrderByEventDateDesc(UUID patientId);
}
