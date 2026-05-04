package com.onconavigator.repository;

import com.onconavigator.domain.PatientPathwayEdge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for {@link PatientPathwayEdge} entities.
 *
 * <p>Provides the edge retrieval and deletion methods needed to build the DAG adjacency
 * structure for pathway evaluation and to clean up edges when steps are removed.
 */
@Repository
public interface PatientPathwayEdgeRepository extends JpaRepository<PatientPathwayEdge, UUID> {

    /**
     * Retrieve all edges for a pathway.
     *
     * <p>Used by the pathway evaluation engine to build the full DAG adjacency list
     * for a patient's pathway in a single query.
     *
     * @param pathwayId the pathway UUID
     * @return all directed edges for the pathway
     */
    List<PatientPathwayEdge> findByPathway_Id(UUID pathwayId);

    /**
     * Delete all edges that reference a specific step as either source or target.
     *
     * <p>Used when removing a step from a pathway (e.g., AI-proposed step rejected).
     * Deletes both incoming and outgoing edges for the step to maintain DAG consistency.
     *
     * @param sourceStepId the step UUID to match as source
     * @param targetStepId the step UUID to match as target
     */
    void deleteBySourceStepIdOrTargetStepId(UUID sourceStepId, UUID targetStepId);
}
