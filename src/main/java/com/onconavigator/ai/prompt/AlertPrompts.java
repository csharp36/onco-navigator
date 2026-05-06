package com.onconavigator.ai.prompt;

/**
 * Prompt constants for Claude alert text generation.
 *
 * <p>These prompts are used by the alert generation ChatClient to instruct Claude
 * on generating plain-language deviation descriptions and suggested corrective actions
 * for non-standard pathway deviations.
 *
 * <p>HIPAA note: Alert generation uses a ZERO-PHI boundary. The prompts accept ONLY
 * anonymized clinical context (cancer type, pathway step name, deviation type, time windows).
 * NO patient identifiers (name, MRN, DOB) are ever included in alert generation prompts.
 * This is a deliberate security architecture decision (D-14).
 */
public final class AlertPrompts {
    private AlertPrompts() {}

    public static final String SYSTEM_PROMPT = """
        You are a clinical care pathway assistant for an oncology practice.
        You help nurse navigators understand care pathway deviations.
        Generate clear, concise descriptions of what deviated and why \
        it matters clinically.
        Suggest specific corrective actions the navigator can take.
        Use clinical language appropriate for an experienced oncology nurse.
        Do NOT invent clinical details not provided in the context.
        Do NOT prescribe treatment decisions -- suggest coordination actions only.
        Keep descriptions to 2-4 sentences. Keep actions to 1-3 bullet points.
        """;

    public static final String USER_TEMPLATE = """
        A care pathway deviation has been detected.

        Cancer type: {cancerType}
        Current pathway step: {pathwayStepName}
        Deviation type: {deviationType}
        Expected time window: {timeWindowDays} days
        Completed steps: {completedSteps}
        Missing/pending steps: {missingSteps}

        Generate:
        1. DESCRIPTION: A 2-4 sentence plain-language description of what \
           deviated and why it matters clinically.
        2. SUGGESTED_ACTION: 1-3 specific coordination actions the nurse \
           navigator should take (max 150 characters total).
        3. MISSING_SUMMARY: A single sentence (max 150 characters) stating \
           specifically what is missing or overdue. This is the primary content \
           of nurse notifications.

        Format your response exactly as:
        DESCRIPTION: [your description]
        SUGGESTED_ACTION: [your suggested actions]
        MISSING_SUMMARY: [your one-sentence summary]
        """;
}
