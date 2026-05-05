package com.onconavigator.ai.model;

/**
 * Structured output record for Claude alert text generation.
 *
 * <p>Contains a human-readable deviation description, suggested corrective action,
 * and a concise missing summary for nurse notifications.
 *
 * <p>Phase 9 (PW-ALL-007): Three-component model:
 * <ul>
 *   <li>{@code deviationDescription} — detailed internal description (no length constraint)</li>
 *   <li>{@code suggestedAction} — corrective action (service-layer cap at 150 chars)</li>
 *   <li>{@code missingSummary} — notification-friendly "what is missing" (service-layer cap at 150 chars)</li>
 * </ul>
 *
 * <p>HIPAA note: This record is generated from zero-PHI prompts and should NOT contain
 * any patient identifiers. It describes the clinical process deviation only.
 */
public record AlertText(String deviationDescription, String suggestedAction, String missingSummary) {}
