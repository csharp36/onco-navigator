package com.onconavigator.ai.service;

import com.onconavigator.ai.model.ExtractionResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link StepExtractionService}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Feature flag disabled — returns null without calling Claude</li>
 *   <li>Blank/null/whitespace extracted text — returns null without calling Claude</li>
 *   <li>Invalid CareEventType values filtered from Claude response</li>
 *   <li>Null result from Claude — returns null</li>
 *   <li>Circuit breaker fallback — returns null</li>
 * </ul>
 *
 * <p>PHI safety: Tests use synthetic document text only. Zero patient identifiers in any
 * assertion or log output. The extractedText parameter is never stored as a field in
 * StepExtractionService — it is passed directly to the ChatClient (D-13, T-06-17).
 *
 * <p>NOTE: {@code @CircuitBreaker} is proxy-based AOP and does not activate in plain
 * unit tests. The fallback method is tested directly.
 */
@ExtendWith(MockitoExtension.class)
class StepExtractionServiceTest {

    @Mock
    private ChatClient stepExtractionClient;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    private static final UUID DOC_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

    // ---- Feature flag tests ----

    @Test
    void extractSteps_featureFlagDisabled_returnsNull() {
        StepExtractionService service = new StepExtractionService(stepExtractionClient, false);

        ExtractionResult result = service.extractSteps(DOC_ID, "some clinical text", "[]");

        assertThat(result).isNull();
        verifyNoInteractions(stepExtractionClient);
    }

    // ---- Blank / null text tests ----

    @Test
    void extractSteps_blankText_returnsNull() {
        StepExtractionService service = new StepExtractionService(stepExtractionClient, true);

        ExtractionResult resultEmpty = service.extractSteps(DOC_ID, "", "[]");
        ExtractionResult resultWhitespace = service.extractSteps(DOC_ID, "   ", "[]");

        assertThat(resultEmpty).isNull();
        assertThat(resultWhitespace).isNull();
        verifyNoInteractions(stepExtractionClient);
    }

    @Test
    void extractSteps_nullText_returnsNull() {
        StepExtractionService service = new StepExtractionService(stepExtractionClient, true);

        ExtractionResult result = service.extractSteps(DOC_ID, null, "[]");

        assertThat(result).isNull();
        verifyNoInteractions(stepExtractionClient);
    }

    // ---- Enum validation / filtering tests ----

    /**
     * Verifies that invalid CareEventType values are filtered from the Claude response.
     *
     * <p>Claude returns 3 proposed steps: SURGERY (valid), TUMOR_BOARD_REVIEW (invalid --
     * not in CareEventType enum), CHEMOTHERAPY (valid). The service must return only the 2
     * valid steps. TUMOR_BOARD_REVIEW must not appear in the result.
     */
    @Test
    @SuppressWarnings("unchecked")
    void extractSteps_filtersInvalidCareEventTypes() {
        StepExtractionService service = new StepExtractionService(stepExtractionClient, true);

        ExtractionResult.ProposedStep validSurgery = new ExtractionResult.ProposedStep(
                "Surgical Resection", "SURGERY", 14, List.of(), "Mentioned in op note");
        ExtractionResult.ProposedStep invalidType = new ExtractionResult.ProposedStep(
                "Tumor Board Review", "TUMOR_BOARD_REVIEW", 7, List.of(), "Mentioned in referral");
        ExtractionResult.ProposedStep validChemo = new ExtractionResult.ProposedStep(
                "Chemotherapy Initiation", "CHEMOTHERAPY", 21, List.of(), "Mentioned in plan");

        ExtractionResult mockResult = new ExtractionResult(
                List.of(validSurgery, invalidType, validChemo),
                List.of()
        );

        when(stepExtractionClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(any(Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.entity(ExtractionResult.class)).thenReturn(mockResult);

        ExtractionResult result = service.extractSteps(DOC_ID, "clinical document text", "[]");

        assertThat(result).isNotNull();
        assertThat(result.proposedSteps()).hasSize(2);
        assertThat(result.proposedSteps()).extracting(ExtractionResult.ProposedStep::eventType)
                .containsExactlyInAnyOrder("SURGERY", "CHEMOTHERAPY")
                .doesNotContain("TUMOR_BOARD_REVIEW");
    }

    // ---- Null result from Claude ----

    @Test
    @SuppressWarnings("unchecked")
    void extractSteps_nullResult_returnsNull() {
        StepExtractionService service = new StepExtractionService(stepExtractionClient, true);

        when(stepExtractionClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(any(Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.entity(ExtractionResult.class)).thenReturn(null);

        ExtractionResult result = service.extractSteps(DOC_ID, "clinical document text", "[]");

        assertThat(result).isNull();
    }

    // ---- Circuit breaker fallback ----

    /**
     * Verifies that the circuit breaker fallback method returns null.
     *
     * <p>In unit tests, {@code @CircuitBreaker} AOP proxy does not activate. The fallback
     * is tested by calling it directly with a synthetic exception to verify its contract.
     */
    @Test
    void extractFallback_returnsNull() {
        StepExtractionService service = new StepExtractionService(stepExtractionClient, true);

        ExtractionResult result = service.extractFallback(
                DOC_ID, "some text", "[]", new RuntimeException("Claude API unavailable"));

        assertThat(result).isNull();
        verifyNoInteractions(stepExtractionClient);
    }

    // ---- PHI boundary structural check ----

    /**
     * Zero-PHI boundary enforcement for StepExtractionService (T-06-17).
     *
     * <p>Structural verification that StepExtractionService does NOT store extractedText
     * as a field. The extractedText is passed directly to the ChatClient prompt and is
     * never retained in the service. Log statements in the service use only documentId
     * (a UUID) and count integers — never extractedText, step names, or rationale strings.
     *
     * <p>This is verified by inspecting declared fields: no field should store the
     * extractedText parameter (only chatClient and extractionEnabled are stored).
     */
    @Test
    void extractSteps_neverStoresDocumentText_structuralCheck() {
        // Verify that StepExtractionService has exactly 2 declared instance fields:
        // stepExtractionClient (ChatClient) and extractionEnabled (boolean).
        // If a developer accidentally stores extractedText as a field, this test catches it.
        var fields = StepExtractionService.class.getDeclaredFields();

        // Filter out static fields (LOG, MAX_INPUT_TOKENS, CHARS_PER_TOKEN_ESTIMATE)
        var instanceFields = java.util.Arrays.stream(fields)
                .filter(f -> !java.lang.reflect.Modifier.isStatic(f.getModifiers()))
                .toList();

        assertThat(instanceFields).hasSize(2);

        var fieldNames = instanceFields.stream()
                .map(java.lang.reflect.Field::getName)
                .toList();
        assertThat(fieldNames).containsExactlyInAnyOrder("stepExtractionClient", "extractionEnabled");
    }
}
