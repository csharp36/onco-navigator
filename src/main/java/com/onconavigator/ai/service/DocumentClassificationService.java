package com.onconavigator.ai.service;

import com.onconavigator.ai.model.DocumentClassification;
import com.onconavigator.ai.prompt.ClassificationPrompts;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Claude-powered document classification with circuit breaker fault tolerance.
 *
 * <p>Sends extracted document text to Claude for classification into a document type
 * (PATHOLOGY_REPORT, RADIOLOGY_REPORT, etc.) and extraction of patient identifiers
 * (MRN, patient name, DOB) and care event details (event type, event date, notes).
 *
 * <p>Classification is gated by the {@code onconavigator.ai.document-classification.enabled}
 * feature flag (default: false). When disabled, returns null and the frontend shows a manual
 * classification dropdown. This flag must remain false until an Anthropic BAA is in place,
 * since document text contains PHI (D-13, T-04-07).
 *
 * <p>HIPAA note: Extracted document text (which may contain PHI) is sent to Claude for
 * classification. This requires an Anthropic BAA. The text is NEVER logged by this service.
 * Only non-PHI metadata (text length, error messages) appears in log statements.
 */
@Service
public class DocumentClassificationService {

    private static final Logger log = LoggerFactory.getLogger(DocumentClassificationService.class);

    /**
     * Maximum input tokens for Claude classification. Prevents sending excessively large
     * documents that exceed the model's context window.
     */
    private static final int MAX_INPUT_TOKENS = 150_000;

    /**
     * Approximate characters per token for input truncation.
     */
    private static final int CHARS_PER_TOKEN_ESTIMATE = 4;

    private final ChatClient classificationClient;
    private final boolean classificationEnabled;

    public DocumentClassificationService(
            @Qualifier("documentClassificationClient") ChatClient classificationClient,
            @Value("${onconavigator.ai.document-classification.enabled:false}") boolean classificationEnabled) {
        this.classificationClient = classificationClient;
        this.classificationEnabled = classificationEnabled;
    }

    /**
     * Classify a clinical document and extract patient identifiers and care event details.
     *
     * <p>Returns null when:
     * <ul>
     *   <li>Feature flag is disabled (BAA not in place)</li>
     *   <li>Circuit breaker is open (Claude API sustained outage)</li>
     *   <li>Classification response cannot be parsed (malformed JSON)</li>
     * </ul>
     *
     * <p>When null is returned, the frontend displays a manual classification dropdown (D-16).
     *
     * @param extractedText the document text to classify (may contain PHI -- NEVER log)
     * @return classification result, or null on failure/disabled
     */
    @CircuitBreaker(name = "claude-api", fallbackMethod = "classifyFallback")
    public DocumentClassification classify(String extractedText) {
        if (!classificationEnabled) {
            log.info("Document classification disabled (BAA not in place)");
            return null;
        }

        try {
            String truncated = truncateToTokenBudget(extractedText);
            return classificationClient.prompt()
                    .user(u -> u.text(ClassificationPrompts.USER_TEMPLATE)
                                .param("documentText", truncated))
                    .call()
                    .entity(DocumentClassification.class);
        } catch (Exception e) {
            log.error("Document classification failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Circuit breaker fallback method for document classification.
     *
     * <p>Called by Resilience4j when the circuit breaker is open (sustained Claude API failures).
     * Returns null, which signals the frontend to show a manual classification dropdown.
     *
     * <p>Must be public for Resilience4j CGLIB proxy to invoke it.
     *
     * @param extractedText the document text (unused in fallback -- NEVER log)
     * @param e             the exception that triggered the circuit breaker
     * @return always null
     */
    public DocumentClassification classifyFallback(String extractedText, Exception e) {
        log.warn("Claude classification CB open: {}", e.getMessage());
        return null;
    }

    /**
     * Truncate document text to fit within the model's input token budget.
     *
     * <p>Uses a simple character-to-token estimate (4 chars per token). If the text exceeds
     * the budget, it is truncated with a warning log (containing only the length, not content).
     *
     * @param text the document text to potentially truncate
     * @return the original or truncated text
     */
    private String truncateToTokenBudget(String text) {
        int maxChars = MAX_INPUT_TOKENS * CHARS_PER_TOKEN_ESTIMATE;
        if (text.length() > maxChars) {
            log.warn("Document text truncated from {} to {} chars for token budget", text.length(), maxChars);
            return text.substring(0, maxChars);
        }
        return text;
    }
}
