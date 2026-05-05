package com.onconavigator.ai.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.List;

/**
 * Top-level structured output record for Claude step extraction.
 *
 * <p>Spring AI generates a JSON schema from this record and instructs Claude to respond
 * with matching JSON. Fields marked required=true must always be present in Claude's output.
 *
 * <p>HIPAA note: this record contains only non-PHI care event metadata. Never log instances
 * of this record -- rationale text may re-state clinical content from PHI-bearing documents.
 */
@JsonPropertyOrder({"proposedSteps", "alreadyCoveredEventTypes"})
public record ExtractionResult(
    @JsonProperty(required = true, value = "proposedSteps")
    List<ProposedStep> proposedSteps,
    @JsonProperty(required = true, value = "alreadyCoveredEventTypes")
    List<String> alreadyCoveredEventTypes
) {
    @JsonPropertyOrder({"stepName", "eventType", "estimatedTimeWindowDays",
                         "proposedEdges", "extractionRationale"})
    public record ProposedStep(
        @JsonProperty(required = true, value = "stepName") String stepName,
        @JsonProperty(required = true, value = "eventType") String eventType,
        @JsonProperty(value = "estimatedTimeWindowDays") Integer estimatedTimeWindowDays,
        @JsonProperty(required = true, value = "proposedEdges") List<ProposedEdge> proposedEdges,
        @JsonProperty(required = true, value = "extractionRationale") String extractionRationale
    ) {}

    @JsonPropertyOrder({"predecessorStepName", "predecessorIsExistingStep"})
    public record ProposedEdge(
        @JsonProperty(required = true, value = "predecessorStepName") String predecessorStepName,
        @JsonProperty(required = true, value = "predecessorIsExistingStep") boolean predecessorIsExistingStep
    ) {}
}
