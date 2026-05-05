package com.onconavigator.ai.config;

import com.onconavigator.ai.prompt.AlertPrompts;
import com.onconavigator.ai.prompt.ClassificationPrompts;
import com.onconavigator.ai.prompt.ExtractionPrompts;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI ChatClient bean configuration for Claude API integration.
 *
 * <p>Defines three distinct ChatClient beans with different system prompts and parameters:
 * <ul>
 *   <li>{@code documentClassificationClient} — Low temperature (0.1) for deterministic
 *       document classification. Max 1024 tokens (classification responses are concise).</li>
 *   <li>{@code alertGenerationClient} — Slightly higher temperature (0.3) for more natural
 *       language alert descriptions. Max 2048 tokens for detailed suggestions.</li>
 *   <li>{@code stepExtractionClient} — Low temperature (0.1) for deterministic step extraction
 *       (Phase 6). Max 2000 tokens bounded JSON output for proposed step list.</li>
 * </ul>
 *
 * <p>Both clients use the model configured via {@code spring.ai.anthropic.chat.options.model}
 * in application properties (defaulting to claude-sonnet-4-20250514). The per-bean options
 * override only temperature and maxTokens.
 *
 * <p>CR-05: Each bean creates its own ChatClient.Builder via {@code ChatClient.builder(chatModel)}
 * to avoid shared builder state mutation. The auto-configured ChatClient.Builder is a singleton;
 * calling {@code .defaultSystem()} on it mutates shared state, causing both clients to receive
 * whichever system prompt was set last.
 *
 * <p>HIPAA note: The classification client processes PHI (document text) — requires Anthropic BAA.
 * The alert generation client uses ZERO-PHI prompts — no BAA required for that call path.
 */
@Configuration
public class AiClientConfig {

    @Bean
    ChatClient documentClassificationClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem(ClassificationPrompts.SYSTEM_PROMPT)
                .defaultOptions(AnthropicChatOptions.builder()
                        .temperature(0.1)
                        .maxTokens(1024)
                        .build())
                .build();
    }

    @Bean
    ChatClient alertGenerationClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem(AlertPrompts.SYSTEM_PROMPT)
                .defaultOptions(AnthropicChatOptions.builder()
                        .temperature(0.3)
                        .maxTokens(2048)
                        .build())
                .build();
    }

    @Bean
    ChatClient stepExtractionClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem(ExtractionPrompts.SYSTEM_PROMPT)
                .defaultOptions(AnthropicChatOptions.builder()
                        .temperature(0.1)   // Deterministic extraction
                        .maxTokens(2000)    // Bounded JSON output for step list
                        .build())
                .build();
    }
}
