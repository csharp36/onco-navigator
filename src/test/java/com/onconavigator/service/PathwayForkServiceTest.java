package com.onconavigator.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onconavigator.domain.Patient;
import com.onconavigator.domain.PatientPathway;
import com.onconavigator.domain.PatientPathwayEdge;
import com.onconavigator.domain.PatientPathwayStep;
import com.onconavigator.domain.PathwayTemplate;
import com.onconavigator.domain.dto.AnchorType;
import com.onconavigator.domain.dto.PathwayStep;
import com.onconavigator.domain.dto.TemplateDiff;
import com.onconavigator.domain.enums.CancerType;
import com.onconavigator.domain.enums.CareEventType;
import com.onconavigator.repository.PatientPathwayEdgeRepository;
import com.onconavigator.repository.PatientPathwayRepository;
import com.onconavigator.repository.PatientPathwayStepRepository;
import com.onconavigator.repository.PathwayTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for PathwayForkService.
 * Tests template fork logic including merge integration for child templates.
 */
@ExtendWith(MockitoExtension.class)
class PathwayForkServiceTest {

    @Mock
    private PatientPathwayRepository pathwayRepository;

    @Mock
    private PatientPathwayStepRepository stepRepository;

    @Mock
    private PatientPathwayEdgeRepository edgeRepository;

    @Mock
    private PathwayTemplateRepository templateRepository;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private TemplateMergeService templateMergeService;

    private PathwayForkService forkService;

    private Patient patient;
    private UUID actorId;
    private UUID templateId;

    @BeforeEach
    void setUp() {
        forkService = new PathwayForkService(
                pathwayRepository, stepRepository, edgeRepository,
                templateRepository, objectMapper, templateMergeService);

        patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setCancerType(CancerType.COLORECTAL);

        actorId = UUID.randomUUID();
        templateId = UUID.randomUUID();
    }

    @Test
    void forkFromTemplate_rootTemplate_parsesStepsDirectly() throws Exception {
        // Given: a root template (parentTemplateId is null)
        PathwayTemplate rootTemplate = new PathwayTemplate();
        rootTemplate.setId(templateId);
        rootTemplate.setParentTemplateId(null);
        rootTemplate.setVersion(1);
        rootTemplate.setTemplateData("[{\"stepId\":\"CRC_01\"}]");

        List<PathwayStep> steps = List.of(
                new PathwayStep("CRC_01", 1, "Step 1", "Desc", CareEventType.CONSULTATION,
                        14, AnchorType.DIAGNOSIS_DATE, null, true,
                        "Alert", "Action", List.of())
        );

        when(templateRepository.findById(templateId)).thenReturn(Optional.of(rootTemplate));
        when(objectMapper.readValue(eq(rootTemplate.getTemplateData()), any(TypeReference.class)))
                .thenReturn(steps);
        when(pathwayRepository.save(any(PatientPathway.class)))
                .thenAnswer(invocation -> {
                    PatientPathway pw = invocation.getArgument(0);
                    pw.setId(UUID.randomUUID());
                    return pw;
                });
        when(stepRepository.save(any(PatientPathwayStep.class)))
                .thenAnswer(invocation -> {
                    PatientPathwayStep s = invocation.getArgument(0);
                    s.setId(UUID.randomUUID());
                    return s;
                });

        // When
        PatientPathway result = forkService.forkFromTemplate(patient, templateId, actorId);

        // Then: steps parsed directly, merge service NOT called
        assertThat(result).isNotNull();
        assertThat(result.getSourceTemplateId()).isEqualTo(templateId);
        verify(templateMergeService, never()).merge(any(), any());
        verify(objectMapper).readValue(eq(rootTemplate.getTemplateData()), any(TypeReference.class));
        verify(stepRepository, times(1)).save(any(PatientPathwayStep.class));
    }

    @Test
    void forkFromTemplate_childTemplate_mergesWithParent() throws Exception {
        // Given: a child template referencing a parent
        UUID parentId = UUID.randomUUID();

        PathwayTemplate childTemplate = new PathwayTemplate();
        childTemplate.setId(templateId);
        childTemplate.setParentTemplateId(parentId);
        childTemplate.setVersion(1);
        childTemplate.setTemplateData("{\"overrides\":[],\"additions\":[],\"removals\":[],\"edgeChanges\":{\"remove\":[],\"add\":[]}}");

        PathwayTemplate parentTemplate = new PathwayTemplate();
        parentTemplate.setId(parentId);
        parentTemplate.setParentTemplateId(null);
        parentTemplate.setVersion(1);
        parentTemplate.setTemplateData("[{\"stepId\":\"CRC_01\"}]");

        List<PathwayStep> parentSteps = List.of(
                new PathwayStep("CRC_01", 1, "Step 1", "Desc", CareEventType.CONSULTATION,
                        14, AnchorType.DIAGNOSIS_DATE, null, true,
                        "Alert", "Action", List.of()),
                new PathwayStep("CRC_02", 2, "Step 2", "Desc", CareEventType.IMAGING,
                        21, AnchorType.DIAGNOSIS_DATE, null, true,
                        "Alert", "Action", List.of())
        );

        TemplateDiff diff = new TemplateDiff(List.of(), List.of(), List.of(), null);

        List<PathwayStep> mergedSteps = List.of(
                new PathwayStep("CRC_01", 1, "Step 1", "Desc", CareEventType.CONSULTATION,
                        14, AnchorType.DIAGNOSIS_DATE, null, true,
                        "Alert", "Action", List.of()),
                new PathwayStep("CRC_02", 2, "Step 2", "Desc", CareEventType.IMAGING,
                        21, AnchorType.DIAGNOSIS_DATE, null, true,
                        "Alert", "Action", List.of()),
                new PathwayStep("RECTAL_01", 3, "Neoadjuvant", "Desc", CareEventType.RADIATION,
                        30, AnchorType.PREVIOUS_STEP, null, true,
                        "Alert", "Action", List.of("CRC_02"))
        );

        when(templateRepository.findById(templateId)).thenReturn(Optional.of(childTemplate));
        when(templateRepository.findById(parentId)).thenReturn(Optional.of(parentTemplate));
        when(objectMapper.readValue(eq(parentTemplate.getTemplateData()), any(TypeReference.class)))
                .thenReturn(parentSteps);
        when(objectMapper.readValue(eq(childTemplate.getTemplateData()), eq(TemplateDiff.class)))
                .thenReturn(diff);
        when(templateMergeService.merge(parentSteps, diff)).thenReturn(mergedSteps);
        when(pathwayRepository.save(any(PatientPathway.class)))
                .thenAnswer(invocation -> {
                    PatientPathway pw = invocation.getArgument(0);
                    pw.setId(UUID.randomUUID());
                    return pw;
                });
        when(stepRepository.save(any(PatientPathwayStep.class)))
                .thenAnswer(invocation -> {
                    PatientPathwayStep s = invocation.getArgument(0);
                    s.setId(UUID.randomUUID());
                    return s;
                });

        // When
        PatientPathway result = forkService.forkFromTemplate(patient, templateId, actorId);

        // Then: parent loaded, ObjectMapper called twice, merge service called
        assertThat(result).isNotNull();
        verify(templateRepository).findById(parentId);
        verify(objectMapper).readValue(eq(parentTemplate.getTemplateData()), any(TypeReference.class));
        verify(objectMapper).readValue(eq(childTemplate.getTemplateData()), eq(TemplateDiff.class));
        verify(templateMergeService).merge(parentSteps, diff);
        // 3 merged steps = 3 save calls
        verify(stepRepository, times(3)).save(any(PatientPathwayStep.class));
        // 1 edge (RECTAL_01 depends on CRC_02)
        verify(edgeRepository, times(1)).save(any(PatientPathwayEdge.class));
    }

    @Test
    void forkFromTemplate_templateNotFound_throws404() {
        // Given: templateId not in repository
        when(templateRepository.findById(templateId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> forkService.forkFromTemplate(patient, templateId, actorId))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("No pathway template found with ID");
    }

    @Test
    void forkFromTemplate_parentNotFound_throwsIllegalState() {
        // Given: child template references non-existent parent
        UUID parentId = UUID.randomUUID();

        PathwayTemplate childTemplate = new PathwayTemplate();
        childTemplate.setId(templateId);
        childTemplate.setParentTemplateId(parentId);
        childTemplate.setVersion(1);
        childTemplate.setTemplateData("{}");

        when(templateRepository.findById(templateId)).thenReturn(Optional.of(childTemplate));
        when(templateRepository.findById(parentId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> forkService.forkFromTemplate(patient, templateId, actorId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Parent template not found");
    }
}
