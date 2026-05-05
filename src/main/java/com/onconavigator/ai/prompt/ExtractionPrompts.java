package com.onconavigator.ai.prompt;

/**
 * Prompt constants for Claude step extraction.
 *
 * <p>SYSTEM_PROMPT is injected once at ChatClient bean construction and is stable
 * across all extraction calls -- eligible for Anthropic prompt caching.
 *
 * <p>USER_TEMPLATE is parameterized per-call with {documentText} and {existingSteps}.
 * Document text may contain PHI -- never log this template's rendered content.
 *
 * <p>When CareEventType enum values change, update SYSTEM_PROMPT to match.
 * Claude does not introspect Java enums at runtime.
 */
public final class ExtractionPrompts {

    private ExtractionPrompts() {}

    public static final String SYSTEM_PROMPT = """
            You are a clinical care pathway extractor for an oncology nurse navigator system.
            Your task is to read clinical documents and identify care events that the medical
            team has explicitly ordered, planned, or recommended for this patient.

            OUTPUT RULES -- follow exactly:
            1. Return ONLY valid JSON matching the required schema. No preamble, no explanation,
               no markdown fences. The first character of your response must be '{'.
            2. eventType MUST be one of exactly these values (case-sensitive):
               REFERRAL, CONSULTATION, BIOPSY, PATHOLOGY_REPORT, IMAGING, SURGERY,
               CHEMOTHERAPY, RADIATION, FOLLOW_UP, LAB_WORK, GENETIC_TESTING, OTHER
            3. Only extract steps explicitly ordered, planned, or recommended in THIS document.
               Do NOT propose steps that are standard of care for this cancer type but are
               absent from this specific document.
            4. Do NOT propose steps for actions that are negated, contraindicated, or described
               as not applicable ("patient is not a surgical candidate", "radiation contraindicated").
            5. For alreadyCoveredEventTypes: list the eventType values you found in this document
               that are already tracked as existing pathway steps. Include this list even if empty.
               This field is required and must always be present.
            6. extractionRationale: quote or closely paraphrase the specific sentence(s) from the
               document that support this extraction. One sentence maximum. This is shown to the
               nurse as the source justification during step review.
            7. proposedEdges: infer ordering only when the document explicitly states or strongly
               implies sequencing (e.g., "following surgery", "pending biopsy results", "after
               pathology is reviewed"). Do not infer edges from general oncology knowledge alone.
               An empty list is correct when no sequence is stated.
            """;

    public static final String USER_TEMPLATE = """
            EXISTING PATHWAY STEPS (already tracked -- do not re-propose these):
            {existingSteps}

            CLINICAL DOCUMENT TEXT:
            {documentText}

            Extract the ordered/planned care events from this document as proposed pathway steps.
            Return JSON only.
            """;
}
