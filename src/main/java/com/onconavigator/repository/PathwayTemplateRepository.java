package com.onconavigator.repository;

import com.onconavigator.domain.PathwayTemplate;
import com.onconavigator.domain.enums.CancerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link PathwayTemplate} entities.
 *
 * <p>Pathway templates are configuration-as-data. Multiple templates may exist per cancer type
 * (a root template and zero or more child templates that inherit from it via parent_template_id).
 *
 * <p>Use {@code findByCancerType} to retrieve all templates (root + children) for a cancer type.
 * Use {@code findByCancerTypeAndParentTemplateIdIsNull} to retrieve only the root template.
 * Use {@code findByParentTemplateId} to retrieve all children of a given parent.
 */
@Repository
public interface PathwayTemplateRepository extends JpaRepository<PathwayTemplate, UUID> {

    /**
     * Find all pathway templates for a given cancer type (root + children).
     *
     * @param cancerType the cancer type to look up (e.g., {@code CancerType.COLORECTAL})
     * @return all templates for that cancer type, or an empty list if none seeded
     */
    List<PathwayTemplate> findByCancerType(CancerType cancerType);

    /**
     * Find the root pathway template for a given cancer type (no parent).
     *
     * @param cancerType the cancer type to look up
     * @return the root template, or empty if no root template has been seeded
     */
    Optional<PathwayTemplate> findByCancerTypeAndParentTemplateIdIsNull(CancerType cancerType);

    /**
     * Find all child templates that inherit from a given parent template.
     *
     * @param parentTemplateId the UUID of the parent template
     * @return child templates inheriting from the specified parent
     */
    List<PathwayTemplate> findByParentTemplateId(UUID parentTemplateId);
}
