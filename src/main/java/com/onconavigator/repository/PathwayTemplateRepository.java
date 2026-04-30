package com.onconavigator.repository;

import com.onconavigator.domain.PathwayTemplate;
import com.onconavigator.domain.enums.CancerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link PathwayTemplate} entities.
 *
 * <p>Pathway templates are configuration-as-data: the database contains one template per
 * cancer type (enforced by a UNIQUE constraint on {@code cancer_type}). The pathway engine
 * loads templates by cancer type to drive its deviation detection logic.
 */
@Repository
public interface PathwayTemplateRepository extends JpaRepository<PathwayTemplate, UUID> {

    /**
     * Find the pathway template for a given cancer type.
     *
     * <p>A UNIQUE constraint on {@code pathway_templates.cancer_type} guarantees at most
     * one result per cancer type. Returns empty if no template has been seeded for that type.
     *
     * @param cancerType the cancer type to look up (e.g., {@code CancerType.BREAST})
     * @return the pathway template for that cancer type, or empty if not found
     */
    Optional<PathwayTemplate> findByCancerType(CancerType cancerType);
}
