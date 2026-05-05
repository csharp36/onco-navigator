package com.onconavigator.service;

import com.onconavigator.domain.dto.AnchorType;
import com.onconavigator.domain.dto.EdgeChanges;
import com.onconavigator.domain.dto.EdgeRef;
import com.onconavigator.domain.dto.PathwayStep;
import com.onconavigator.domain.dto.StepOverride;
import com.onconavigator.domain.dto.TemplateDiff;
import com.onconavigator.domain.enums.CareEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TemplateMergeService.
 * Tests the pure-function merge engine that resolves parent + child diff into flat steps.
 * No Spring context needed -- stateless service.
 */
class TemplateMergeServiceTest {

    private TemplateMergeService mergeService;

    @BeforeEach
    void setUp() {
        mergeService = new TemplateMergeService();
    }

    /**
     * Creates sample parent steps mimicking the colorectal template.
     * CRC_01 -> CRC_02 -> CRC_03 -> CRC_04 -> CRC_05 -> CRC_06
     */
    private List<PathwayStep> createColorectalParentSteps() {
        return List.of(
                new PathwayStep("CRC_01", 1, "Surgical Oncology Consultation",
                        "Patient meets with surgical oncologist.", CareEventType.CONSULTATION,
                        14, AnchorType.DIAGNOSIS_DATE, null, true,
                        "No surgical consultation found within 14 days.",
                        "Schedule surgical oncology consult.", List.of()),
                new PathwayStep("CRC_02", 2, "Complete Staging Workup",
                        "Imaging and labs to confirm staging.", CareEventType.IMAGING,
                        21, AnchorType.DIAGNOSIS_DATE, null, true,
                        "Staging workup not completed within 21 days.",
                        "Confirm orders with treating team.", List.of()),
                new PathwayStep("CRC_03", 3, "Surgery (Resection)",
                        "Surgical resection performed.", CareEventType.SURGERY,
                        45, AnchorType.DIAGNOSIS_DATE, null, true,
                        "Surgery not yet performed within 45 days.",
                        "Follow up with surgical team.", List.of("CRC_01")),
                new PathwayStep("CRC_04", 4, "Pathology and MSI/MMR Testing",
                        "Pathology confirms margins; MSI testing performed.", CareEventType.PATHOLOGY_REPORT,
                        21, AnchorType.PREVIOUS_STEP, null, true,
                        "Pathology or MSI not documented within 21 days of surgery.",
                        "Contact pathology.", List.of("CRC_03")),
                new PathwayStep("CRC_05", 5, "Medical Oncology Visit",
                        "Patient meets with medical oncologist.", CareEventType.CONSULTATION,
                        21, AnchorType.PREVIOUS_STEP, null, true,
                        "Medical oncology visit not scheduled within 21 days.",
                        "Schedule post-surgical consultation.", List.of("CRC_03")),
                new PathwayStep("CRC_06", 6, "Treatment Plan Established",
                        "Adjuvant chemo or observation plan documented.", CareEventType.FOLLOW_UP,
                        14, AnchorType.PREVIOUS_STEP, null, true,
                        "No treatment plan within 14 days of oncology visit.",
                        "Follow up with medical oncologist.", List.of("CRC_05"))
        );
    }

    @Test
    void mergeWithEmptyDiffReturnsParentStepsUnchanged() {
        List<PathwayStep> parentSteps = createColorectalParentSteps();
        TemplateDiff emptyDiff = new TemplateDiff(null, null, null, null);

        List<PathwayStep> result = mergeService.merge(parentSteps, emptyDiff);

        assertThat(result).hasSize(6);
        assertThat(result.get(0).stepId()).isEqualTo("CRC_01");
        assertThat(result.get(0).name()).isEqualTo("Surgical Oncology Consultation");
        assertThat(result.get(5).stepId()).isEqualTo("CRC_06");
        // stepNumbers should be renumbered (identity: 1-6)
        for (int i = 0; i < result.size(); i++) {
            assertThat(result.get(i).stepNumber()).isEqualTo(i + 1);
        }
    }

    @Test
    void mergeWithRemovalsFiltersOutSpecifiedSteps() {
        List<PathwayStep> parentSteps = createColorectalParentSteps();
        TemplateDiff diff = new TemplateDiff(
                List.of(), List.of(), List.of("CRC_04"), null);

        List<PathwayStep> result = mergeService.merge(parentSteps, diff);

        assertThat(result).hasSize(5);
        assertThat(result.stream().map(PathwayStep::stepId)).doesNotContain("CRC_04");
        // stepNumbers renumbered 1-5
        for (int i = 0; i < result.size(); i++) {
            assertThat(result.get(i).stepNumber()).isEqualTo(i + 1);
        }
    }

    @Test
    void mergeWithOverridesAppliesFieldLevelChanges() {
        List<PathwayStep> parentSteps = createColorectalParentSteps();
        StepOverride override = new StepOverride("CRC_03", Map.of(
                "windowDays", 60,
                "description", "Modified surgery description."
        ));
        TemplateDiff diff = new TemplateDiff(List.of(override), List.of(), List.of(), null);

        List<PathwayStep> result = mergeService.merge(parentSteps, diff);

        PathwayStep modifiedSurgery = result.stream()
                .filter(s -> s.stepId().equals("CRC_03"))
                .findFirst()
                .orElseThrow();

        // Overridden fields
        assertThat(modifiedSurgery.windowDays()).isEqualTo(60);
        assertThat(modifiedSurgery.description()).isEqualTo("Modified surgery description.");
        // Non-overridden fields preserved
        assertThat(modifiedSurgery.name()).isEqualTo("Surgery (Resection)");
        assertThat(modifiedSurgery.eventType()).isEqualTo(CareEventType.SURGERY);
        assertThat(modifiedSurgery.required()).isTrue();
    }

    @Test
    void mergeWithAdditionsAppendsNewSteps() {
        List<PathwayStep> parentSteps = createColorectalParentSteps();
        PathwayStep newStep = new PathwayStep("RECTAL_01", 3, "Neoadjuvant Chemoradiation",
                "Combined chemo and radiation.", CareEventType.RADIATION,
                30, AnchorType.PREVIOUS_STEP, null, true,
                "Neoadjuvant not started in 30 days.",
                "Coordinate with radiation oncology.",
                List.of("CRC_02"));
        TemplateDiff diff = new TemplateDiff(List.of(), List.of(newStep), List.of(), null);

        List<PathwayStep> result = mergeService.merge(parentSteps, diff);

        assertThat(result).hasSize(7);
        assertThat(result.stream().map(PathwayStep::stepId)).contains("RECTAL_01");
        // Renumbered sequentially
        for (int i = 0; i < result.size(); i++) {
            assertThat(result.get(i).stepNumber()).isEqualTo(i + 1);
        }
    }

    @Test
    void mergeWithEdgeChangesModifiesPrerequisites() {
        List<PathwayStep> parentSteps = createColorectalParentSteps();
        // Remove edge: CRC_01 -> CRC_03 (surgery no longer depends on consultation directly)
        // Add edge: CRC_02 -> CRC_03 (surgery now depends on staging)
        EdgeChanges edgeChanges = new EdgeChanges(
                List.of(new EdgeRef("CRC_01", "CRC_03")),
                List.of(new EdgeRef("CRC_02", "CRC_03"))
        );
        TemplateDiff diff = new TemplateDiff(List.of(), List.of(), List.of(), edgeChanges);

        List<PathwayStep> result = mergeService.merge(parentSteps, diff);

        PathwayStep surgery = result.stream()
                .filter(s -> s.stepId().equals("CRC_03"))
                .findFirst()
                .orElseThrow();

        assertThat(surgery.prerequisites()).doesNotContain("CRC_01");
        assertThat(surgery.prerequisites()).contains("CRC_02");
    }

    @Test
    void mergeWithCombinedOperationsProducesCorrectRectalTemplate() {
        List<PathwayStep> parentSteps = createColorectalParentSteps();

        // Full rectal cancer diff
        StepOverride surgeryOverride = new StepOverride("CRC_03", Map.of(
                "windowDays", 60,
                "description", "Surgical resection performed after neoadjuvant chemoradiation.",
                "alertText", "Surgery not yet performed within 60 days.",
                "suggestedAction", "Verify chemoradiation completion and coordinate surgery scheduling."
        ));

        PathwayStep neoadjuvant = new PathwayStep("RECTAL_01", 3, "Neoadjuvant Chemoradiation",
                "Combined chemo and radiation before surgery.", CareEventType.RADIATION,
                30, AnchorType.PREVIOUS_STEP, null, true,
                "Neoadjuvant not started within 30 days of staging.",
                "Coordinate with radiation oncology.",
                List.of("CRC_02"));

        EdgeChanges edgeChanges = new EdgeChanges(
                List.of(new EdgeRef("CRC_01", "CRC_03")),
                List.of(new EdgeRef("CRC_02", "RECTAL_01"),
                        new EdgeRef("RECTAL_01", "CRC_03"))
        );

        TemplateDiff diff = new TemplateDiff(
                List.of(surgeryOverride),
                List.of(neoadjuvant),
                List.of(),
                edgeChanges
        );

        List<PathwayStep> result = mergeService.merge(parentSteps, diff);

        // 7 steps total (6 parent + 1 addition)
        assertThat(result).hasSize(7);

        // RECTAL_01 is present
        PathwayStep rectalStep = result.stream()
                .filter(s -> s.stepId().equals("RECTAL_01"))
                .findFirst()
                .orElseThrow();
        assertThat(rectalStep.name()).isEqualTo("Neoadjuvant Chemoradiation");
        assertThat(rectalStep.prerequisites()).contains("CRC_02");

        // CRC_03 overridden
        PathwayStep surgery = result.stream()
                .filter(s -> s.stepId().equals("CRC_03"))
                .findFirst()
                .orElseThrow();
        assertThat(surgery.windowDays()).isEqualTo(60);
        assertThat(surgery.prerequisites()).doesNotContain("CRC_01");
        assertThat(surgery.prerequisites()).contains("RECTAL_01");

        // Step numbers are sequential
        for (int i = 0; i < result.size(); i++) {
            assertThat(result.get(i).stepNumber()).isEqualTo(i + 1);
        }
    }

    @Test
    void mergeValidatesEdgeIntegritySkipsDanglingReferences() {
        List<PathwayStep> parentSteps = createColorectalParentSteps();
        // Add an edge referencing a non-existent step
        EdgeChanges edgeChanges = new EdgeChanges(
                List.of(),
                List.of(new EdgeRef("NONEXISTENT_STEP", "CRC_03"))
        );
        TemplateDiff diff = new TemplateDiff(List.of(), List.of(), List.of(), edgeChanges);

        List<PathwayStep> result = mergeService.merge(parentSteps, diff);

        // CRC_03 should NOT have NONEXISTENT_STEP in its prerequisites (dangling ref removed)
        PathwayStep surgery = result.stream()
                .filter(s -> s.stepId().equals("CRC_03"))
                .findFirst()
                .orElseThrow();
        assertThat(surgery.prerequisites()).doesNotContain("NONEXISTENT_STEP");
    }

    @Test
    void mergeRenumbersStepNumbersSequentially() {
        List<PathwayStep> parentSteps = createColorectalParentSteps();
        // Remove step 2, add a new step at the end
        PathwayStep newStep = new PathwayStep("NEW_01", 99, "New Step",
                "A new step.", CareEventType.FOLLOW_UP,
                7, AnchorType.PREVIOUS_STEP, null, false,
                "Alert text.", "Action text.", List.of("CRC_06"));
        TemplateDiff diff = new TemplateDiff(
                List.of(), List.of(newStep), List.of("CRC_02"), null);

        List<PathwayStep> result = mergeService.merge(parentSteps, diff);

        // 5 remaining parent steps + 1 addition = 6 total
        assertThat(result).hasSize(6);
        // Sequential numbering regardless of original stepNumber values
        for (int i = 0; i < result.size(); i++) {
            assertThat(result.get(i).stepNumber()).isEqualTo(i + 1);
        }
    }
}
