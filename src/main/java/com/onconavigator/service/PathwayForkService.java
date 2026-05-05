package com.onconavigator.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onconavigator.domain.Patient;
import com.onconavigator.domain.PatientPathway;
import com.onconavigator.domain.PatientPathwayEdge;
import com.onconavigator.domain.PatientPathwayStep;
import com.onconavigator.domain.PathwayTemplate;
import com.onconavigator.domain.dto.PathwayStep;
import com.onconavigator.domain.enums.PathwayStepStatus;
import com.onconavigator.repository.PatientPathwayEdgeRepository;
import com.onconavigator.repository.PatientPathwayRepository;
import com.onconavigator.repository.PatientPathwayStepRepository;
import com.onconavigator.repository.PathwayTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service responsible for forking pathway templates into per-patient relational rows.
 *
 * <p>Implements the template fork operation (D-05): when a patient is enrolled with
 * {@code pathwayMode=template}, this service deep-copies all template steps and prerequisite
 * edges into the per-patient DAG tables ({@code patient_pathways}, {@code patient_pathway_steps},
 * {@code patient_pathway_edges}).
 *
 * <p>Also creates empty pathways for the AI-document-extraction flow ({@code pathwayMode=empty}).
 *
 * <p>PHI safety: Log statements use patient UUIDs and template UUIDs only. No patient names,
 * DOBs, or MRNs appear in any log statement.
 */
@Service
public class PathwayForkService {

    private static final Logger log = LoggerFactory.getLogger(PathwayForkService.class);

    private final PatientPathwayRepository pathwayRepository;
    private final PatientPathwayStepRepository stepRepository;
    private final PatientPathwayEdgeRepository edgeRepository;
    private final PathwayTemplateRepository templateRepository;
    private final ObjectMapper objectMapper;

    public PathwayForkService(PatientPathwayRepository pathwayRepository,
                              PatientPathwayStepRepository stepRepository,
                              PatientPathwayEdgeRepository edgeRepository,
                              PathwayTemplateRepository templateRepository,
                              ObjectMapper objectMapper) {
        this.pathwayRepository = pathwayRepository;
        this.stepRepository = stepRepository;
        this.edgeRepository = edgeRepository;
        this.templateRepository = templateRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Forks a pathway template into per-patient relational rows (D-05).
     *
     * <p>Deep copies all template steps and prerequisite edges, remapping template string step
     * IDs to the newly generated UUIDs. Records the source template ID and version for
     * traceability.
     *
     * @param patient the Patient entity (already saved)
     * @param actorId UUID of the authenticated user creating the pathway
     * @return the created PatientPathway with all steps and edges persisted
     * @throws ResponseStatusException 404 if no template exists for the patient's cancer type
     * @throws IllegalStateException   if the template JSONB data cannot be parsed
     */
    @Transactional
    public PatientPathway forkFromTemplate(Patient patient, UUID actorId) {
        // 1. Find root template by cancer type (child template selection deferred to Phase 8 Plan 2)
        PathwayTemplate template = templateRepository.findByCancerTypeAndParentTemplateIdIsNull(patient.getCancerType())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No pathway template found for cancer type " + patient.getCancerType()));

        // 2. Parse JSONB template_data into List<PathwayStep>
        List<PathwayStep> templateSteps;
        try {
            templateSteps = objectMapper.readValue(
                    template.getTemplateData(),
                    new TypeReference<List<PathwayStep>>() {});
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to parse template data for template " + template.getId(), e);
        }

        // 3. Create PatientPathway header
        PatientPathway pathway = new PatientPathway();
        pathway.setPatient(patient);
        pathway.setSourceTemplateId(template.getId());
        // V1 templates don't track version changes; record as 1 for traceability
        pathway.setSourceTemplateVersion(template.getVersion() != null ? template.getVersion() : 1);
        pathway.setCreatedBy(actorId);
        pathway = pathwayRepository.save(pathway);

        // 4. Deep copy steps, building stepId mapping for edge remapping
        // Map: templateStepId (String) -> new PatientPathwayStep UUID
        Map<String, UUID> stepIdMap = new HashMap<>();
        for (PathwayStep ts : templateSteps) {
            PatientPathwayStep step = new PatientPathwayStep();
            step.setPathway(pathway);
            step.setName(ts.name());
            step.setDescription(ts.description());
            step.setEventType(ts.eventType());
            step.setWindowDays(ts.windowDays());
            step.setRequired(ts.required());
            step.setAlertText(ts.alertText());
            step.setSuggestedAction(ts.suggestedAction());
            step.setStatus(PathwayStepStatus.ACTIVE);
            step.setSourceTemplateStepId(ts.stepId());
            step.setCreatedBy(actorId);
            step = stepRepository.save(step);
            stepIdMap.put(ts.stepId(), step.getId());
        }

        // 5. Deep copy prerequisite edges using remapped UUIDs
        int edgeCount = 0;
        for (PathwayStep ts : templateSteps) {
            if (ts.prerequisites() != null && !ts.prerequisites().isEmpty()) {
                UUID targetId = stepIdMap.get(ts.stepId());
                for (String prereqStepId : ts.prerequisites()) {
                    UUID sourceId = stepIdMap.get(prereqStepId);
                    if (sourceId != null && targetId != null) {
                        PatientPathwayEdge edge = new PatientPathwayEdge();
                        edge.setPathway(pathway);
                        edge.setSourceStepId(sourceId);
                        edge.setTargetStepId(targetId);
                        edge.setCreatedBy(actorId);
                        edgeRepository.save(edge);
                        edgeCount++;
                    }
                }
            }
        }

        log.info("Forked template {} for patient {} ({} steps, {} edges)",
                template.getId(), patient.getId(), templateSteps.size(), edgeCount);

        return pathway;
    }

    /**
     * Creates an empty pathway for the "Build from documents" mode (D-06).
     *
     * <p>No steps or edges are created. The AI document ingestion pipeline (Phase 6)
     * populates steps with PROPOSED status after document analysis.
     *
     * @param patient the Patient entity (already saved)
     * @param actorId UUID of the authenticated user creating the pathway
     * @return the created empty PatientPathway
     */
    @Transactional
    public PatientPathway createEmptyPathway(Patient patient, UUID actorId) {
        PatientPathway pathway = new PatientPathway();
        pathway.setPatient(patient);
        pathway.setCreatedBy(actorId);
        pathway = pathwayRepository.save(pathway);

        log.info("Created empty pathway {} for patient {}", pathway.getId(), patient.getId());
        return pathway;
    }
}
