package com.onconavigator.domain.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.onconavigator.domain.enums.CareEventType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TemplateDiff and related DTO records.
 * Verifies JSON deserialization and null-safety of compact constructor defaults.
 */
class TemplateDiffTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserializesFullDiffFromJson() throws Exception {
        String json = """
                {
                    "overrides": [
                        {
                            "stepId": "CRC_03",
                            "fields": {
                                "windowDays": 60,
                                "description": "Modified surgery step."
                            }
                        }
                    ],
                    "additions": [
                        {
                            "stepId": "RECTAL_01",
                            "stepNumber": 3,
                            "name": "Neoadjuvant Chemoradiation",
                            "description": "Combined chemo and radiation before surgery.",
                            "eventType": "RADIATION",
                            "windowDays": 30,
                            "anchorType": "PREVIOUS_STEP",
                            "anchorStepId": null,
                            "required": true,
                            "alertText": "Neoadjuvant not started in 30 days.",
                            "suggestedAction": "Coordinate with radiation oncology.",
                            "prerequisites": ["CRC_02"]
                        }
                    ],
                    "removals": ["STEP_X"],
                    "edgeChanges": {
                        "remove": [{"from": "CRC_02", "to": "CRC_03"}],
                        "add": [{"from": "RECTAL_01", "to": "CRC_03"}]
                    }
                }
                """;

        TemplateDiff diff = objectMapper.readValue(json, TemplateDiff.class);

        assertThat(diff.overrides()).hasSize(1);
        assertThat(diff.overrides().get(0).stepId()).isEqualTo("CRC_03");
        assertThat(diff.overrides().get(0).fields()).containsEntry("windowDays", 60);

        assertThat(diff.additions()).hasSize(1);
        assertThat(diff.additions().get(0).stepId()).isEqualTo("RECTAL_01");
        assertThat(diff.additions().get(0).eventType()).isEqualTo(CareEventType.RADIATION);
        assertThat(diff.additions().get(0).prerequisites()).containsExactly("CRC_02");

        assertThat(diff.removals()).containsExactly("STEP_X");

        assertThat(diff.edgeChanges().remove()).hasSize(1);
        assertThat(diff.edgeChanges().remove().get(0).from()).isEqualTo("CRC_02");
        assertThat(diff.edgeChanges().remove().get(0).to()).isEqualTo("CRC_03");

        assertThat(diff.edgeChanges().add()).hasSize(1);
        assertThat(diff.edgeChanges().add().get(0).from()).isEqualTo("RECTAL_01");
    }

    @Test
    void handlesNullAndEmptySectionsGracefully() throws Exception {
        // JSON with all sections as null
        String jsonNull = """
                {
                    "overrides": null,
                    "additions": null,
                    "removals": null,
                    "edgeChanges": null
                }
                """;

        TemplateDiff diff = objectMapper.readValue(jsonNull, TemplateDiff.class);

        assertThat(diff.overrides()).isNotNull().isEmpty();
        assertThat(diff.additions()).isNotNull().isEmpty();
        assertThat(diff.removals()).isNotNull().isEmpty();
        assertThat(diff.edgeChanges()).isNotNull();
        assertThat(diff.edgeChanges().remove()).isNotNull().isEmpty();
        assertThat(diff.edgeChanges().add()).isNotNull().isEmpty();

        // JSON with empty object (missing fields entirely)
        String jsonEmpty = "{}";
        TemplateDiff diffEmpty = objectMapper.readValue(jsonEmpty, TemplateDiff.class);

        assertThat(diffEmpty.overrides()).isNotNull().isEmpty();
        assertThat(diffEmpty.additions()).isNotNull().isEmpty();
        assertThat(diffEmpty.removals()).isNotNull().isEmpty();
        assertThat(diffEmpty.edgeChanges()).isNotNull();
    }

    @Test
    void pathwayTemplateEntityHasNewFields() {
        // This test verifies the entity has the new fields via compilation.
        // If the fields don't exist, this test fails to compile.
        var template = new com.onconavigator.domain.PathwayTemplate();
        template.setParentTemplateId(java.util.UUID.randomUUID());
        template.setName("Rectal Cancer Pathway");
        template.setDescription("Includes neoadjuvant chemoradiation");

        assertThat(template.getParentTemplateId()).isNotNull();
        assertThat(template.getName()).isEqualTo("Rectal Cancer Pathway");
        assertThat(template.getDescription()).isEqualTo("Includes neoadjuvant chemoradiation");
    }
}
