package com.onconavigator.ai.service;

import com.onconavigator.ai.model.AlertText;
import com.onconavigator.ai.prompt.AlertPrompts;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

// PHI safety: This service accepts ONLY anonymized clinical context.
// Parameters are: cancerType, pathwayStepName, deviationType, timeWindowDays,
// completedSteps, missingSteps. NO patient name, MRN, DOB, or identifiers.

/**
 * Claude-powered alert text generation for non-standard pathway deviations.
 *
 * <p>Generates plain-language deviation descriptions and suggested corrective actions
 * for pathway deviations that do not match any predefined template text. Template-based
 * alert text remains the primary path (existing Phase 2 behavior); this service is the
 * catch-all enhancement for edge cases (D-15).
 *
 * <p>ZERO-PHI BOUNDARY (D-14): This service accepts ONLY anonymized clinical context:
 * cancer type, pathway step name, deviation type, time window, completed steps, and
 * missing steps. NO patient identifiers (name, MRN, DOB) are accepted or referenced.
 * This is a deliberate security architecture decision -- no Anthropic BAA is required
 * for this call path.
 *
 * <p>When the circuit breaker is open (sustained Claude API failures), the fallback
 * returns null and the caller uses template text instead (AI-04, D-16).
 */
@Service
public class AlertGenerationAiService {

    private static final Logger log = LoggerFactory.getLogger(AlertGenerationAiService.class);

    private final ChatClient alertClient;

    public AlertGenerationAiService(
            @Qualifier("alertGenerationClient") ChatClient alertClient) {
        this.alertClient = alertClient;
    }

    /**
     * Generate a plain-language alert description and suggested action for a non-standard
     * pathway deviation.
     *
     * <p>All parameters are anonymized clinical context -- no PHI. The response is parsed
     * into DESCRIPTION and SUGGESTED_ACTION sections.
     *
     * @param cancerType      the cancer type (e.g., "BREAST", "LUNG", "COLORECTAL")
     * @param pathwayStepName the name of the pathway step where deviation occurred
     * @param deviationType   the type of deviation (e.g., "MISSING_EVENT", "DELAYED_EVENT")
     * @param timeWindowDays  the expected time window in days
     * @param completedSteps  list of completed pathway step names
     * @param missingSteps    list of missing/pending pathway step names
     * @return alert text with description and suggested action, or null on failure
     */
    @CircuitBreaker(name = "claude-api", fallbackMethod = "generateAlertFallback")
    public AlertText generateAlertDescription(String cancerType, String pathwayStepName,
                                              String deviationType, String timeWindowDays,
                                              List<String> completedSteps, List<String> missingSteps) {
        try {
            String response = alertClient.prompt()
                    .user(u -> u.text(AlertPrompts.USER_TEMPLATE)
                                .param("cancerType", cancerType)
                                .param("pathwayStepName", pathwayStepName)
                                .param("deviationType", deviationType)
                                .param("timeWindowDays", timeWindowDays)
                                .param("completedSteps", String.join(", ", completedSteps))
                                .param("missingSteps", String.join(", ", missingSteps)))
                    .call()
                    .content();

            return parseAlertResponse(response);
        } catch (Exception e) {
            log.error("Alert text generation failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parse Claude's response into structured DESCRIPTION and SUGGESTED_ACTION sections.
     *
     * <p>Expected response format:
     * <pre>
     * DESCRIPTION: [2-4 sentence description]
     * SUGGESTED_ACTION: [1-3 bullet point actions]
     * </pre>
     *
     * @param response the raw Claude response text
     * @return parsed AlertText, or null if response format is invalid
     */
    private AlertText parseAlertResponse(String response) {
        if (response == null || response.isBlank()) {
            log.warn("Empty response from Claude alert generation");
            return null;
        }

        String description = null;
        String suggestedAction = null;

        int descIdx = response.indexOf("DESCRIPTION:");
        int actionIdx = response.indexOf("SUGGESTED_ACTION:");

        if (descIdx >= 0 && actionIdx > descIdx) {
            description = response.substring(descIdx + "DESCRIPTION:".length(), actionIdx).strip();
            suggestedAction = response.substring(actionIdx + "SUGGESTED_ACTION:".length()).strip();
        } else if (descIdx >= 0) {
            // Only description found, no suggested action section
            description = response.substring(descIdx + "DESCRIPTION:".length()).strip();
        }

        if (description == null || description.isEmpty()) {
            log.warn("Could not parse DESCRIPTION section from Claude alert response");
            return null;
        }
        if (suggestedAction == null || suggestedAction.isEmpty()) {
            log.warn("Could not parse SUGGESTED_ACTION section from Claude alert response");
            return null;
        }

        return new AlertText(description, suggestedAction, null);
    }

    /**
     * Circuit breaker fallback method for alert text generation.
     *
     * <p>Called by Resilience4j when the circuit breaker is open. Returns null,
     * signaling the caller to use template text instead (AI-04).
     *
     * <p>Must be public for Resilience4j CGLIB proxy to invoke it.
     *
     * @param cancerType      unused in fallback
     * @param pathwayStepName unused in fallback
     * @param deviationType   unused in fallback
     * @param timeWindowDays  unused in fallback
     * @param completedSteps  unused in fallback
     * @param missingSteps    unused in fallback
     * @param e               the exception that triggered the circuit breaker
     * @return always null
     */
    public AlertText generateAlertFallback(String cancerType, String pathwayStepName,
                                           String deviationType, String timeWindowDays,
                                           List<String> completedSteps, List<String> missingSteps,
                                           Exception e) {
        log.warn("Claude alert generation CB open: {}", e.getMessage());
        return null;
    }
}
