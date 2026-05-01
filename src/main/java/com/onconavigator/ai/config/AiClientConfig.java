package com.onconavigator.ai.config;

import com.onconavigator.ai.prompt.AlertPrompts;
import com.onconavigator.ai.prompt.ClassificationPrompts;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI ChatClient bean configuration for Claude API integration.
 *
 * <p>Defines two distinct ChatClient beans with different system prompts and parameters:
 * <ul>
 *   <li>{@code documentClassificationClient} — Low temperature (0.1) for deterministic
 *       document classification. Max 1024 tokens (classification responses are concise).</li>
 *   <li>{@code alertGenerationClient} — Slightly higher temperature (0.3) for more natural
 *       language alert descriptions. Max 2048 tokens for detailed suggestions.</li>
 * </ul>
 *
 * <p>Both clients use the model configured via {@code spring.ai.anthropic.chat.options.model}
 * in application properties (defaulting to claude-sonnet-4-20250514). The per-bean options
 * override only temperature and maxTokens.
 *
 * <p>HIPAA note: The classification client processes PHI (document text) — requires Anthropic BAA.
 * The alert generation client uses ZERO-PHI prompts — no BAA required for that call path.
 */
@Configuration
public class AiClientConfig {

    @Bean
    ChatClient documentClassificationClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem(ClassificationPrompts.SYSTEM_PROMPT)
                .defaultOptions(AnthropicChatOptions.builder()
                        .temperature(0.1)
                        .maxTokens(1024)
                        .build())
                .build();
    }

    @Bean
    ChatClient alertGenerationClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem(AlertPrompts.SYSTEM_PROMPT)
                .defaultOptions(AnthropicChatOptions.builder()
                        .temperature(0.3)
                        .maxTokens(2048)
                        .build())
                .build();
    }
}
