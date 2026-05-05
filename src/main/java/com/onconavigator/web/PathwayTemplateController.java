package com.onconavigator.web;

import com.onconavigator.domain.PathwayTemplate;
import com.onconavigator.domain.enums.CancerType;
import com.onconavigator.repository.PathwayTemplateRepository;
import com.onconavigator.web.dto.PathwayTemplateResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for pathway template listing.
 *
 * <p>Provides endpoints for retrieving available pathway templates by cancer type.
 * Templates are non-PHI clinical protocol configuration data -- all authenticated
 * clinical roles can read all templates (T-08-05, T-08-06).
 *
 * <p>Used by the frontend TemplatePicker to show variant selection when multiple
 * templates exist for a cancer type (D-07).
 */
@RestController
@RequestMapping("/api/pathway-templates")
public class PathwayTemplateController {

    private final PathwayTemplateRepository templateRepository;

    public PathwayTemplateController(PathwayTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    /**
     * Lists all pathway templates for a given cancer type, including children.
     * Templates are non-PHI configuration data -- all authenticated roles can read.
     * Root template listed first, children after (D-08: root is default).
     *
     * @param cancerType the cancer type to list templates for (validated by Spring MVC enum binding)
     * @return sorted list of templates: root first, then children alphabetically by name
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<PathwayTemplateResponse> listTemplates(
            @RequestParam CancerType cancerType) {
        List<PathwayTemplate> templates = templateRepository.findByCancerType(cancerType);
        // Sort: root first, then children alphabetically by name
        return templates.stream()
                .sorted((a, b) -> {
                    if (a.getParentTemplateId() == null && b.getParentTemplateId() != null) return -1;
                    if (a.getParentTemplateId() != null && b.getParentTemplateId() == null) return 1;
                    return a.getName().compareTo(b.getName());
                })
                .map(t -> new PathwayTemplateResponse(
                        t.getId(),
                        t.getCancerType(),
                        t.getName(),
                        t.getDescription(),
                        t.getParentTemplateId(),
                        t.getVersion(),
                        t.getParentTemplateId() == null
                ))
                .toList();
    }
}
