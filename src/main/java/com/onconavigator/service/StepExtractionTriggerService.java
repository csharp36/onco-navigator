package com.onconavigator.service;

import com.onconavigator.ai.model.ExtractionResult;
import com.onconavigator.ai.service.StepExtractionService;
import com.onconavigator.domain.ClinicalDocument;
import com.onconavigator.repository.ClinicalDocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Async wrapper for the step extraction pipeline.
 *
 * <p>Decouples the Claude API call from the document upload transaction to avoid
 * holding a HikariCP connection during the 2-8 second API wait.
 *
 * <p>The @Async annotation runs this method on a virtual thread (via AsyncConfig +
 * spring.threads.virtual.enabled=true) AFTER the upload transaction commits. The
 * @Transactional annotation starts a NEW transaction for the DB reads and writes
 * inside this method.
 *
 * <p>HIPAA note: extractedText parameter contains PHI — never log it.
 */
@Service
public class StepExtractionTriggerService {

    private static final Logger log = LoggerFactory.getLogger(StepExtractionTriggerService.class);

    private final StepExtractionService extractionService;
    private final PatientPathwayService pathwayService;
    private final ClinicalDocumentRepository documentRepository;

    public StepExtractionTriggerService(StepExtractionService extractionService,
                                         PatientPathwayService pathwayService,
                                         ClinicalDocumentRepository documentRepository) {
        this.extractionService = extractionService;
        this.pathwayService = pathwayService;
        this.documentRepository = documentRepository;
    }

    /**
     * Trigger step extraction asynchronously after the upload transaction commits.
     *
     * <p>Orchestrates the full extraction pipeline:
     * <ol>
     *   <li>Builds existing steps context for Claude dedup (non-PHI)</li>
     *   <li>Calls Claude via StepExtractionService (circuit-breaker-protected)</li>
     *   <li>Persists alreadyCoveredEventTypes on ClinicalDocument (D-10 transparency)</li>
     *   <li>Creates PROPOSED pathway steps via PatientPathwayService (dedup-checked)</li>
     *   <li>Signals pathway steps changed to Temporal evaluation engine</li>
     * </ol>
     *
     * @param documentId    source document UUID
     * @param patientId     linked patient UUID
     * @param extractedText document text (PHI — never log)
     */
    @Async
    @Transactional
    public void triggerAsync(UUID documentId, UUID patientId, String extractedText) {
        try {
            String existingStepsContext = pathwayService.buildExistingStepsContext(patientId);
            ExtractionResult result =
                    extractionService.extractSteps(documentId, extractedText, existingStepsContext);
            if (result != null) {
                // D-10: Persist alreadyCoveredEventTypes on the ClinicalDocument for transparency display
                if (result.alreadyCoveredEventTypes() != null && !result.alreadyCoveredEventTypes().isEmpty()) {
                    documentRepository.findById(documentId).ifPresent(doc -> {
                        doc.setAlreadyCoveredEventTypes(
                                String.join(",", result.alreadyCoveredEventTypes()));
                        documentRepository.save(doc);
                    });
                }

                if (result.proposedSteps() != null && !result.proposedSteps().isEmpty()) {
                    pathwayService.createProposedSteps(patientId, documentId, result);
                    pathwayService.signalPathwayStepsChanged(patientId);
                    log.info("Step extraction pipeline completed for document {} patient {}: {} proposed steps",
                            documentId, patientId, result.proposedSteps().size());
                } else {
                    log.info("Step extraction pipeline returned no steps for document {} patient {}",
                            documentId, patientId);
                }
            } else {
                log.info("Step extraction pipeline returned null result for document {} patient {}",
                        documentId, patientId);
            }
        } catch (Exception e) {
            log.error("Step extraction pipeline failed for document {} patient {}: {}",
                    documentId, patientId, e.getMessage());
        }
    }
}
