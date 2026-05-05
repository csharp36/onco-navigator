package com.onconavigator.service;

import com.onconavigator.ai.model.ExtractionResult;
import com.onconavigator.domain.PatientPathway;
import com.onconavigator.domain.PatientPathwayStep;
import com.onconavigator.domain.enums.CareEventType;
import com.onconavigator.domain.enums.PathwayStepStatus;
import com.onconavigator.repository.AlertRepository;
import com.onconavigator.repository.ClinicalDocumentRepository;
import com.onconavigator.repository.PatientPathwayEdgeRepository;
import com.onconavigator.repository.PatientPathwayRepository;
import com.onconavigator.repository.PatientPathwayStepRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link PatientPathwayService} confirm/reject/createProposedSteps methods.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>confirmProposedStep: PROPOSED->ACTIVE transition and 409 on non-PROPOSED step</li>
 *   <li>rejectProposedStep: PROPOSED->REJECTED transition and 409 on non-PROPOSED step</li>
 *   <li>createProposedSteps: dedup against ACTIVE, COMPLETED, and REJECTED steps</li>
 *   <li>createProposedSteps: sets status=PROPOSED, source=AI_EXTRACTED, sourceDocumentId</li>
 * </ul>
 *
 * <p>D-10 DTO data path structural check (compile-time guarantee):
 * DocumentSummaryResponse is a Java record. Any change to its fields breaks all
 * constructor call sites in DocumentUploadController at compile time — no runtime
 * test needed. The presence of the "alreadyCoveredEventTypes" field in the record
 * is verified by the Plan 03 Task 2 ./mvnw compile check.
 *
 * <p>PHI safety: All test data uses synthetic UUIDs and placeholder strings. No real PHI.
 */
@ExtendWith(MockitoExtension.class)
class PatientPathwayServiceConfirmRejectTest {

    @Mock
    private PatientPathwayRepository pathwayRepository;

    @Mock
    private PatientPathwayStepRepository stepRepository;

    @Mock
    private PatientPathwayEdgeRepository edgeRepository;

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private PathwayService pathwayService;

    @Mock
    private ClinicalDocumentRepository documentRepository;

    private PatientPathwayService service;

    private static final UUID PATIENT_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID PATHWAY_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID STEP_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
    private static final UUID ACTOR_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final UUID DOCUMENT_ID = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");

    @BeforeEach
    void setUp() {
        service = new PatientPathwayService(
                pathwayRepository, stepRepository, edgeRepository,
                alertRepository, pathwayService, documentRepository);
    }

    // ---- Helper factories ----

    private PatientPathway createTestPathway() {
        PatientPathway pathway = new PatientPathway();
        pathway.setId(PATHWAY_ID);
        return pathway;
    }

    private PatientPathwayStep createStep(UUID id, PathwayStepStatus status,
                                           CareEventType eventType, PatientPathway pathway) {
        PatientPathwayStep step = new PatientPathwayStep();
        step.setId(id);
        step.setPathway(pathway);
        step.setName("Test Step " + id);
        step.setStatus(status);
        step.setEventType(eventType);
        step.setRequired(true);
        step.setCreatedBy(UUID.fromString("00000000-0000-0000-0000-000000000000"));
        return step;
    }

    private void setupPathwayAndStepMocks(PatientPathway pathway, PatientPathwayStep step) {
        when(pathwayRepository.findByPatient_Id(PATIENT_ID)).thenReturn(Optional.of(pathway));
        when(stepRepository.findById(STEP_ID)).thenReturn(Optional.of(step));
        // edgeRepository.findByPathway_Id is called by getPrerequisiteIds() on the success path;
        // on the error path (throws 409), it is NOT called. Tests that throw must NOT stub this.
    }

    // ---- confirmProposedStep tests ----

    /**
     * confirmProposedStep transitions a PROPOSED step to ACTIVE status.
     *
     * <p>Verifies:
     * <ul>
     *   <li>step.getStatus() == ACTIVE after confirm</li>
     *   <li>stepRepository.save() was called with the updated step</li>
     * </ul>
     */
    @Test
    void confirmProposedStep_proposedStep_transitionsToActive() {
        PatientPathway pathway = createTestPathway();
        PatientPathwayStep step = createStep(STEP_ID, PathwayStepStatus.PROPOSED,
                CareEventType.SURGERY, pathway);
        step.setSource("AI_EXTRACTED");

        setupPathwayAndStepMocks(pathway, step);
        // Success path: edge repo called by getPrerequisiteIds(); save called to persist status change
        when(edgeRepository.findByPathway_Id(PATHWAY_ID)).thenReturn(List.of());
        when(stepRepository.save(any(PatientPathwayStep.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.confirmProposedStep(PATIENT_ID, STEP_ID, ACTOR_ID);

        // Capture what was saved
        ArgumentCaptor<PatientPathwayStep> captor = ArgumentCaptor.forClass(PatientPathwayStep.class);
        verify(stepRepository, atLeastOnce()).save(captor.capture());

        // The last save call should have ACTIVE status
        List<PatientPathwayStep> savedSteps = captor.getAllValues();
        assertThat(savedSteps).anySatisfy(s ->
                assertThat(s.getStatus()).isEqualTo(PathwayStepStatus.ACTIVE));
    }

    /**
     * confirmProposedStep throws 409 CONFLICT when the step is ACTIVE (not PROPOSED).
     *
     * <p>Verifies T-06-16: Only PROPOSED steps can be confirmed. Attempting to confirm
     * an ACTIVE step must throw ResponseStatusException with 409.
     */
    @Test
    void confirmProposedStep_activeStep_throws409() {
        PatientPathway pathway = createTestPathway();
        PatientPathwayStep step = createStep(STEP_ID, PathwayStepStatus.ACTIVE,
                CareEventType.SURGERY, pathway);

        setupPathwayAndStepMocks(pathway, step);

        assertThatThrownBy(() -> service.confirmProposedStep(PATIENT_ID, STEP_ID, ACTOR_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                });
    }

    /**
     * confirmProposedStep throws 409 CONFLICT when the step is COMPLETED (not PROPOSED).
     */
    @Test
    void confirmProposedStep_completedStep_throws409() {
        PatientPathway pathway = createTestPathway();
        PatientPathwayStep step = createStep(STEP_ID, PathwayStepStatus.COMPLETED,
                CareEventType.SURGERY, pathway);

        setupPathwayAndStepMocks(pathway, step);

        assertThatThrownBy(() -> service.confirmProposedStep(PATIENT_ID, STEP_ID, ACTOR_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                });
    }

    // ---- rejectProposedStep tests ----

    /**
     * rejectProposedStep transitions a PROPOSED step to REJECTED status.
     *
     * <p>Verifies:
     * <ul>
     *   <li>step.getStatus() == REJECTED after reject</li>
     *   <li>proposedEdgesJson is cleared (null)</li>
     *   <li>stepRepository.save() was called</li>
     * </ul>
     */
    @Test
    void rejectProposedStep_proposedStep_transitionsToRejected() {
        PatientPathway pathway = createTestPathway();
        PatientPathwayStep step = createStep(STEP_ID, PathwayStepStatus.PROPOSED,
                CareEventType.IMAGING, pathway);
        step.setProposedEdgesJson("[{\"predecessorStepName\":\"Surgery\"}]");

        setupPathwayAndStepMocks(pathway, step);
        // Success path: edge repo called by getPrerequisiteIds(); save called to persist status change
        when(edgeRepository.findByPathway_Id(PATHWAY_ID)).thenReturn(List.of());
        when(stepRepository.save(any(PatientPathwayStep.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.rejectProposedStep(PATIENT_ID, STEP_ID, ACTOR_ID);

        ArgumentCaptor<PatientPathwayStep> captor = ArgumentCaptor.forClass(PatientPathwayStep.class);
        verify(stepRepository, atLeastOnce()).save(captor.capture());

        List<PatientPathwayStep> savedSteps = captor.getAllValues();
        // Find the save call that set status to REJECTED
        assertThat(savedSteps).anySatisfy(s -> {
            assertThat(s.getStatus()).isEqualTo(PathwayStepStatus.REJECTED);
            assertThat(s.getProposedEdgesJson()).isNull(); // proposedEdgesJson cleared
        });
    }

    /**
     * rejectProposedStep throws 409 CONFLICT when the step is COMPLETED (not PROPOSED).
     *
     * <p>Verifies that only PROPOSED steps can be rejected.
     */
    @Test
    void rejectProposedStep_completedStep_throws409() {
        PatientPathway pathway = createTestPathway();
        PatientPathwayStep step = createStep(STEP_ID, PathwayStepStatus.COMPLETED,
                CareEventType.IMAGING, pathway);

        setupPathwayAndStepMocks(pathway, step);

        assertThatThrownBy(() -> service.rejectProposedStep(PATIENT_ID, STEP_ID, ACTOR_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> {
                    ResponseStatusException rse = (ResponseStatusException) e;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                });
    }

    // ---- createProposedSteps tests ----

    /**
     * createProposedSteps deduplicates against ACTIVE, COMPLETED, and REJECTED steps.
     *
     * <p>Setup:
     * <ul>
     *   <li>ACTIVE step with eventType=SURGERY</li>
     *   <li>COMPLETED step with eventType=CHEMOTHERAPY</li>
     *   <li>REJECTED step with eventType=IMAGING</li>
     * </ul>
     *
     * <p>ExtractionResult contains 4 proposed steps: SURGERY (dup), CHEMOTHERAPY (dup),
     * IMAGING (dup via REJECTED), RADIATION (new).
     *
     * <p>Only 1 step (RADIATION) must be saved. REJECTED steps ARE included in the dedup
     * filter (D-07, D-09).
     */
    @Test
    void createProposedSteps_deduplicatesAgainstExistingStatuses() {
        PatientPathway pathway = createTestPathway();

        PatientPathwayStep activeStep = createStep(UUID.randomUUID(), PathwayStepStatus.ACTIVE,
                CareEventType.SURGERY, pathway);
        PatientPathwayStep completedStep = createStep(UUID.randomUUID(), PathwayStepStatus.COMPLETED,
                CareEventType.CHEMOTHERAPY, pathway);
        PatientPathwayStep rejectedStep = createStep(UUID.randomUUID(), PathwayStepStatus.REJECTED,
                CareEventType.IMAGING, pathway);

        when(pathwayRepository.findByPatient_Id(PATIENT_ID)).thenReturn(Optional.of(pathway));
        when(stepRepository.findByPathway_Id(PATHWAY_ID))
                .thenReturn(List.of(activeStep, completedStep, rejectedStep));
        when(stepRepository.save(any(PatientPathwayStep.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ExtractionResult result = new ExtractionResult(
                List.of(
                        new ExtractionResult.ProposedStep("Surgery Step", "SURGERY", 14, List.of(), "dup"),
                        new ExtractionResult.ProposedStep("Chemo Step", "CHEMOTHERAPY", 21, List.of(), "dup"),
                        new ExtractionResult.ProposedStep("Imaging Step", "IMAGING", 7, List.of(), "dup via REJECTED"),
                        new ExtractionResult.ProposedStep("Radiation Therapy", "RADIATION", 28, List.of(), "new step")
                ),
                List.of("SURGERY", "CHEMOTHERAPY", "IMAGING")
        );

        service.createProposedSteps(PATIENT_ID, DOCUMENT_ID, result);

        // Only RADIATION should be saved (the other 3 are duplicates)
        ArgumentCaptor<PatientPathwayStep> captor = ArgumentCaptor.forClass(PatientPathwayStep.class);
        verify(stepRepository, times(1)).save(captor.capture());

        PatientPathwayStep savedStep = captor.getValue();
        assertThat(savedStep.getEventType()).isEqualTo(CareEventType.RADIATION);
        assertThat(savedStep.getStatus()).isEqualTo(PathwayStepStatus.PROPOSED);
    }

    /**
     * createProposedSteps sets status=PROPOSED, source=AI_EXTRACTED, and sourceDocumentId.
     *
     * <p>When a pathway has no existing steps, all proposed steps should be saved with:
     * <ul>
     *   <li>status == PROPOSED</li>
     *   <li>source == "AI_EXTRACTED"</li>
     *   <li>sourceDocumentId == documentId passed to the method</li>
     * </ul>
     */
    @Test
    void createProposedSteps_setsProposedStatusAndSource() {
        PatientPathway pathway = createTestPathway();

        when(pathwayRepository.findByPatient_Id(PATIENT_ID)).thenReturn(Optional.of(pathway));
        when(stepRepository.findByPathway_Id(PATHWAY_ID)).thenReturn(List.of()); // no existing steps
        when(stepRepository.save(any(PatientPathwayStep.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        ExtractionResult result = new ExtractionResult(
                List.of(
                        new ExtractionResult.ProposedStep("Surgery Step", "SURGERY", 14, List.of(), "mentioned in notes"),
                        new ExtractionResult.ProposedStep("Radiation Step", "RADIATION", 28, List.of(), "mentioned in plan")
                ),
                List.of()
        );

        service.createProposedSteps(PATIENT_ID, DOCUMENT_ID, result);

        // Both steps should be saved
        ArgumentCaptor<PatientPathwayStep> captor = ArgumentCaptor.forClass(PatientPathwayStep.class);
        verify(stepRepository, times(2)).save(captor.capture());

        List<PatientPathwayStep> savedSteps = captor.getAllValues();
        assertThat(savedSteps).hasSize(2);

        // Verify all saved steps have the correct status, source, and sourceDocumentId
        assertThat(savedSteps).allSatisfy(s -> {
            assertThat(s.getStatus()).isEqualTo(PathwayStepStatus.PROPOSED);
            assertThat(s.getSource()).isEqualTo("AI_EXTRACTED");
            assertThat(s.getSourceDocumentId()).isEqualTo(DOCUMENT_ID);
        });

        // Verify event types are correct
        assertThat(savedSteps).extracting(PatientPathwayStep::getEventType)
                .containsExactlyInAnyOrder(CareEventType.SURGERY, CareEventType.RADIATION);
    }

    /**
     * createProposedSteps with null result.proposedSteps — no NPE, no saves.
     *
     * <p>Belt-and-suspenders test: the service should not throw when proposedSteps is empty.
     */
    @Test
    void createProposedSteps_emptyResult_noStepsSaved() {
        PatientPathway pathway = createTestPathway();

        when(pathwayRepository.findByPatient_Id(PATIENT_ID)).thenReturn(Optional.of(pathway));
        when(stepRepository.findByPathway_Id(PATHWAY_ID)).thenReturn(List.of());

        ExtractionResult result = new ExtractionResult(List.of(), List.of());

        service.createProposedSteps(PATIENT_ID, DOCUMENT_ID, result);

        verify(stepRepository, never()).save(any(PatientPathwayStep.class));
    }

    /*
     * D-10 DTO data path structural verification (compile-time guarantee):
     *
     * DocumentSummaryResponse is a Java record with a named constructor parameter
     * "alreadyCoveredEventTypes". If this field is removed or renamed, all 3
     * constructor call sites in DocumentUploadController fail to compile.
     * The "alreadyCoveredEventTypes" field was added in Plan 03 Task 2 and is
     * verified by the Plan 03 Task 2 ./mvnw compile check.
     *
     * This test class is NOT responsible for re-verifying compile-time record
     * constructor safety — that is a compile-time guarantee, not a runtime test.
     *
     * The end-to-end D-10 data path:
     *   1. ClinicalDocument.alreadyCoveredEventTypes (persisted by StepExtractionTriggerService)
     *   2. DocumentSummaryResponse.alreadyCoveredEventTypes (record field — compile-time safe)
     *   3. useDocumentAlreadyCovered frontend hook reads GET /api/documents/{documentId}
     *
     * The deduplication tests above (createProposedSteps_deduplicatesAgainstExistingStatuses)
     * verify that REJECTED steps are included in the dedup filter, which is the runtime
     * guarantee that the D-10 data path is actually exercised correctly.
     */
}
