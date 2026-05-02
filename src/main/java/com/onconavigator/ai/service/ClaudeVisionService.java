package com.onconavigator.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeType;

import java.util.List;

/**
 * Claude vision API fallback for text extraction from scanned document images.
 *
 * <p>When Tesseract OCR confidence is below the threshold, this service sends the
 * document image to Claude's vision API for higher-quality text extraction. Uses
 * the synchronous {@code .call()} method (not {@code .stream()}) because streaming
 * with images has known issues (GitHub #5011).
 *
 * <p>HIPAA note: Document images sent to Claude may contain PHI. This service
 * requires Anthropic BAA coverage for production use. Error messages logged contain
 * no document content or patient information.
 */
@Service
public class ClaudeVisionService {

    private static final Logger log = LoggerFactory.getLogger(ClaudeVisionService.class);

    private static final String VISION_EXTRACTION_PROMPT =
            "Extract ALL text from this scanned clinical document image. " +
            "Preserve the original structure as much as possible. " +
            "Include headers, patient information fields, dates, and clinical content. " +
            "If any text is illegible, indicate it with [illegible]. " +
            "Return only the extracted text, no commentary.";

    private final AnthropicChatModel chatModel;
    private final String modelId;

    public ClaudeVisionService(
            AnthropicChatModel chatModel,
            @Value("${spring.ai.anthropic.chat.options.model:claude-sonnet-4-20250514}") String modelId) {
        this.chatModel = chatModel;
        this.modelId = modelId;
    }

    /**
     * Extract text from a scanned document image using Claude's vision capability.
     *
     * <p>Sends the image bytes to Claude with a structured extraction prompt.
     * Returns the extracted text or an empty string if extraction fails
     * (triggers the blank wizard path per D-04).
     *
     * @param imageBytes the raw image bytes (JPEG or PNG)
     * @param mimeType   the MIME type of the image (e.g., image/png, image/jpeg)
     * @return extracted text from the image, or empty string on failure
     */
    public String extractTextFromImage(byte[] imageBytes, MimeType mimeType) {
        try {
            var imageResource = new ByteArrayResource(imageBytes);
            var media = new Media(mimeType, imageResource);
            var userMessage = UserMessage.builder()
                    .text(VISION_EXTRACTION_PROMPT)
                    .media(media)
                    .build();

            var response = chatModel.call(new Prompt(
                    List.of(userMessage),
                    AnthropicChatOptions.builder()
                            .model(modelId)
                            .temperature(0.1)
                            .maxTokens(4096)
                            .build()
            ));

            return response.getResult().getOutput().getText();
        } catch (Exception e) {
            log.error("Claude vision extraction failed: {}", e.getMessage());
            return "";
        }
    }
}
