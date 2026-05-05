package com.onconavigator.ai.service;

import com.onconavigator.ai.model.ExtractionResult;
import com.onconavigator.ai.prompt.ExtractionPrompts;
import com.onconavigator.domain.enums.CareEventType;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Claude-powered care step extraction from clinical documents.
 *
 * <p>Reads extracted document text and the patient's current pathway step context, then
 * asks Claude to identify care events mentioned in the document that are not yet tracked.
 * Returns a structured {@link ExtractionResult} with proposed steps and detected duplicates.
 *
 * <p>Gated by {@code onconavigator.ai.step-extraction.enabled} (default: false).
 * Must remain false until an Anthropic BAA is signed — document text contains PHI (D-13).
 *
 * <p>HIPAA note: document text (PHI) is sent to Claude. Never log document text, extracted
 * step names, extractionRationale strings, or existingStepsContext. Log only document UUIDs
 * and counts.
 */
@Service
public class StepExtractionService {

    private static final Logger log = LoggerFactory.getLogger(StepExtractionService.class);

    // Matches DocumentClassificationService — documents are already capped at upload.
    // Re-truncate defensively in case the service is called outside the upload pipeline.
    private static final int MAX_INPUT_TOKENS = 150_000;
    private static final int CHARS_PER_TOKEN_ESTIMATE = 4;

    private final ChatClient stepExtractionClient;
    private final boolean extractionEnabled;

    public StepExtractionService(
            @Qualifier("stepExtractionClient") ChatClient stepExtractionClient,
            @Value("${onconavigator.ai.step-extraction.enabled:false}") boolean extractionEnabled) {
        this.stepExtractionClient = stepExtractionClient;
        this.extractionEnabled = extractionEnabled;
    }

    /**
     * Extract proposed pathway steps from clinical document text.
     *
     * <p>Returns null when:
     * <ul>
     *   <li>Feature flag disabled (BAA not in place)</li>
     *   <li>Extracted text is null or blank (document had no readable content)</li>
     *   <li>Circuit breaker is open (sustained Claude API outage)</li>
     *   <li>Claude returns unparseable or null JSON</li>
     * </ul>
     *
     * @param documentId          UUID of the source document (logging only — never log content)
     * @param extractedText       full document text (may contain PHI — never log)
     * @param existingStepsContext JSON summary of existing ACTIVE/COMPLETED/REJECTED steps (non-PHI:
     *                             step names and event type codes only — no patient identifiers)
     * @return extraction result with valid steps only, or null on any failure
     */
    @CircuitBreaker(name = "claude-api", fallbackMethod = "extractFallback")
    public ExtractionResult extractSteps(UUID documentId, String extractedText,
                                          String existingStepsContext) {
        if (!extractionEnabled) {
            log.info("Step extraction disabled (BAA not in place) for document {}", documentId);
            return null;
        }
        if (extractedText == null || extractedText.isBlank()) {
            log.info("Step extraction skipped — no extractable text in document {}", documentId);
            return null;
        }

        try {
            String truncated = truncateToTokenBudget(extractedText, documentId);

            ExtractionResult result = stepExtractionClient.prompt()
                    .user(u -> u.text(ExtractionPrompts.USER_TEMPLATE)
                                 .param("documentText", truncated)
                                 .param("existingSteps", existingStepsContext))
                    .call()
                    .entity(ExtractionResult.class);

            if (result == null || result.proposedSteps() == null) {
                log.warn("Step extraction returned null result for document {}", documentId);
                return null;
            }

            // Validate eventType strings before returning — prevents invalid enum values
            // from reaching PatientPathwayService and causing database constraint violations.
            // Invalid types are logged (value only, not step name) and filtered out.
            List<ExtractionResult.ProposedStep> validSteps = result.proposedSteps().stream()
                    .filter(step -> isValidCareEventType(step.eventType(), documentId))
                    .toList();

            int filtered = result.proposedSteps().size() - validSteps.size();
            if (filtered > 0) {
                log.warn("Step extraction: {} invalid CareEventType value(s) filtered for document {}",
                        filtered, documentId);
            }

            log.info("Step extraction completed for document {}: {} proposed, {} already covered, {} filtered",
                    documentId,
                    validSteps.size(),
                    result.alreadyCoveredEventTypes() != null ? result.alreadyCoveredEventTypes().size() : 0,
                    filtered);

            return new ExtractionResult(validSteps, result.alreadyCoveredEventTypes());

        } catch (Exception e) {
            // Log message only — never log extractedText or result content
            log.error("Step extraction failed for document {}: {}", documentId, e.getMessage());
            return null;
        }
    }

    /**
     * Circuit breaker fallback — must be public for Resilience4j CGLIB proxy.
     *
     * <p>Called when the circuit breaker is open (sustained Claude API failures).
     * Returns null, triggering silent skip in StepExtractionTriggerService.
     */
    public ExtractionResult extractFallback(UUID documentId, String extractedText,
                                              String existingStepsContext, Exception e) {
        log.warn("Step extraction circuit breaker open for document {}: {}", documentId, e.getMessage());
        return null;
    }

    /**
     * Validate that an eventType string maps to a known CareEventType enum value.
     * Logs only the invalid value — not the step name (which may contain clinical content).
     */
    private boolean isValidCareEventType(String eventType, UUID documentId) {
        if (eventType == null) {
            log.warn("Step extraction returned null eventType for a step in document {}", documentId);
            return false;
        }
        try {
            CareEventType.valueOf(eventType);
            return true;
        } catch (IllegalArgumentException ex) {
            log.warn("Step extraction returned unknown CareEventType '{}' for document {}",
                    eventType, documentId);
            return false;
        }
    }

    private String truncateToTokenBudget(String text, UUID documentId) {
        int maxChars = MAX_INPUT_TOKENS * CHARS_PER_TOKEN_ESTIMATE;
        if (text.length() > maxChars) {
            log.warn("Document {} text truncated from {} to {} chars for token budget",
                    documentId, text.length(), maxChars);
            return text.substring(0, maxChars);
        }
        return text;
    }
}
