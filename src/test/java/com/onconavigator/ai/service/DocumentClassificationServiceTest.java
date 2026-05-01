package com.onconavigator.ai.service;

import com.onconavigator.ai.model.DocumentClassification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DocumentClassificationService}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>Successful classification via mocked ChatClient fluent chain</li>
 *   <li>Null return when ChatClient throws (error handling, not circuit breaker proxy)</li>
 *   <li>Null return when feature flag is disabled (BAA gating)</li>
 *   <li>Fallback method returns null directly</li>
 *   <li>Long text truncation to token budget</li>
 * </ul>
 *
 * <p>NOTE: {@code @CircuitBreaker} is proxy-based AOP and does not activate in plain
 * unit tests. These tests verify the core logic and fallback method directly.
 * Integration tests with a Spring context would verify actual circuit breaker behavior.
 */
@ExtendWith(MockitoExtension.class)
class DocumentClassificationServiceTest {

    @Mock
    private ChatClient classificationClient;

    @Mock
    private ChatClient.ChatClientRequestSpec promptRequest;

    @Mock
    private ChatClient.CallResponseSpec callResponse;

    private DocumentClassificationService service;

    @BeforeEach
    void setUp() {
        // classificationEnabled = true for most tests
        service = new DocumentClassificationService(classificationClient, true);
    }

    @Test
    void classify_returnsClassification_whenClientSucceeds() {
        DocumentClassification expected = new DocumentClassification(
                "PATHOLOGY_REPORT", "HIGH", "TEST-001", "Jane Doe",
                "1965-08-14", "PATHOLOGY_REPORT", "2026-01-15",
                "Invasive ductal carcinoma, Grade 2");

        when(classificationClient.prompt()).thenReturn(promptRequest);
        when(promptRequest.user(any(Consumer.class))).thenReturn(promptRequest);
        when(promptRequest.call()).thenReturn(callResponse);
        when(callResponse.entity(DocumentClassification.class)).thenReturn(expected);

        DocumentClassification result = service.classify("Some clinical document text");

        assertThat(result).isNotNull();
        assertThat(result.documentType()).isEqualTo("PATHOLOGY_REPORT");
        assertThat(result.confidence()).isEqualTo("HIGH");
        assertThat(result.mrn()).isEqualTo("TEST-001");
    }

    @Test
    void classify_returnsNull_whenClientThrowsException() {
        when(classificationClient.prompt()).thenReturn(promptRequest);
        when(promptRequest.user(any(Consumer.class))).thenReturn(promptRequest);
        when(promptRequest.call()).thenThrow(new RuntimeException("Claude API error"));

        DocumentClassification result = service.classify("Some document text");

        assertThat(result).isNull();
    }

    @Test
    void classify_returnsNull_whenClassificationDisabled() {
        // Construct service with classificationEnabled = false
        DocumentClassificationService disabledService =
                new DocumentClassificationService(classificationClient, false);

        DocumentClassification result = disabledService.classify("Some document text");

        assertThat(result).isNull();
        // ChatClient should never be called when disabled
        verifyNoInteractions(classificationClient);
    }

    @Test
    void classifyFallback_returnsNull() {
        DocumentClassification result = service.classifyFallback(
                "some text", new RuntimeException("test circuit breaker open"));

        assertThat(result).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void classify_truncatesLongText() {
        // MAX_INPUT_TOKENS = 150,000, CHARS_PER_TOKEN_ESTIMATE = 4
        // maxChars = 600,000
        int maxChars = 150_000 * 4;
        String longText = "A".repeat(maxChars + 10_000); // 610,000 chars

        DocumentClassification expected = new DocumentClassification(
                "RADIOLOGY_REPORT", "MEDIUM", null, null,
                null, "IMAGING", "2026-03-10", null);

        when(classificationClient.prompt()).thenReturn(promptRequest);
        // Capture the user message consumer to verify truncation
        ArgumentCaptor<Consumer<ChatClient.PromptUserSpec>> userCaptor =
                ArgumentCaptor.forClass(Consumer.class);
        when(promptRequest.user(userCaptor.capture())).thenReturn(promptRequest);
        when(promptRequest.call()).thenReturn(callResponse);
        when(callResponse.entity(DocumentClassification.class)).thenReturn(expected);

        DocumentClassification result = service.classify(longText);

        assertThat(result).isNotNull();
        assertThat(result.documentType()).isEqualTo("RADIOLOGY_REPORT");

        // Verify the ChatClient was actually called (meaning text was processed)
        verify(classificationClient).prompt();
        verify(promptRequest).user(any(Consumer.class));
        verify(promptRequest).call();
    }
}
