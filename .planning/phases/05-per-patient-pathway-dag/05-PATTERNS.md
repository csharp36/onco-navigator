# Phase 5: Per-Patient Pathway Instances + DAG Foundation - Pattern Map

**Mapped:** 2026-05-04
**Files analyzed:** 28 new/modified files
**Analogs found:** 28 / 28

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/.../domain/PatientPathway.java` | model | CRUD | `src/.../domain/PathwayTemplate.java` | exact |
| `src/.../domain/PatientPathwayStep.java` | model | CRUD | `src/.../domain/CareEvent.java` | exact |
| `src/.../domain/PatientPathwayEdge.java` | model | CRUD | `src/.../domain/PhysicianOverride.java` | exact |
| `src/.../domain/enums/PathwayStepStatus.java` | model | N/A | `src/.../domain/enums/CareEventType.java` | exact |
| `src/.../repository/PatientPathwayRepository.java` | repository | CRUD | `src/.../repository/PathwayTemplateRepository.java` | exact |
| `src/.../repository/PatientPathwayStepRepository.java` | repository | CRUD | `src/.../repository/CareEventRepository.java` | exact |
| `src/.../repository/PatientPathwayEdgeRepository.java` | repository | CRUD | `src/.../repository/PhysicianOverrideRepository.java` | exact |
| `src/.../service/PathwayForkService.java` | service | CRUD | `src/.../service/PatientService.java` | role-match |
| `src/.../service/PatientPathwayService.java` | service | CRUD | `src/.../service/PatientService.java` | exact |
| `src/.../service/PathwayStatusService.java` (modified) | service | transform | `src/.../service/PathwayStatusService.java` | self |
| `src/.../service/PatientService.java` (modified) | service | CRUD | `src/.../service/PatientService.java` | self |
| `src/.../service/PathwayService.java` (modified) | service | event-driven | `src/.../service/PathwayService.java` | self |
| `src/.../web/PatientPathwayController.java` | controller | request-response | `src/.../web/CareEventController.java` | exact |
| `src/.../web/dto/PathwayStepRequest.java` | dto | request-response | `src/.../web/dto/CreateCareEventRequest.java` | exact |
| `src/.../web/dto/PathwayEdgeRequest.java` | dto | request-response | `src/.../web/dto/CreateCareEventRequest.java` | role-match |
| `src/.../web/dto/PathwayStepResponse.java` | dto | request-response | `src/.../web/dto/CareEventResponse.java` | exact |
| `src/.../web/dto/PathwayEdgeResponse.java` | dto | request-response | `src/.../web/dto/CareEventResponse.java` | role-match |
| `src/.../web/dto/PathwayStepStatus.java` (modified) | dto | request-response | `src/.../web/dto/PathwayStepStatus.java` | self |
| `src/.../activity/PathwayEvaluationActivityImpl.java` (rewrite) | activity | transform | `src/.../activity/PathwayEvaluationActivityImpl.java` | self |
| `src/.../workflow/PatientPathwayWorkflow.java` (modified) | workflow | event-driven | `src/.../workflow/PatientPathwayWorkflow.java` | self |
| `src/.../workflow/PatientPathwayWorkflowImpl.java` (modified) | workflow | event-driven | `src/.../workflow/PatientPathwayWorkflowImpl.java` | self |
| `V13__create_per_patient_pathway_tables.sql` | migration | CRUD | `V5__create_physician_overrides.sql` | exact |
| `V14__create_pathway_step_status_enum.sql` | migration | CRUD | `V1__create_base_schema.sql` (enum section) | exact |
| `V15__migrate_patients_to_per_patient_pathways.sql` | migration | batch/transform | `V6__seed_pathway_templates.sql` | partial |
| `frontend/src/features/patients/PathwayDAGView.tsx` | component | request-response | `frontend/src/routes/patients/$patientId.tsx` (pathway section) | exact |
| `frontend/src/features/patients/PathwayEditor.tsx` | component | CRUD | `frontend/src/features/patients/QuickAddCareEventDialog.tsx` | role-match |
| `frontend/src/features/patients/TemplatePicker.tsx` | component | request-response | `frontend/src/features/patients/PatientWizard.tsx` (Step 2 section) | exact |
| `frontend/src/features/patients/types.ts` (modified) | utility | N/A | `frontend/src/features/patients/types.ts` | self |
| `frontend/src/features/patients/api.ts` (modified) | utility | request-response | `frontend/src/features/patients/api.ts` | self |

## Pattern Assignments

### `src/main/java/com/onconavigator/domain/PatientPathway.java` (model, CRUD)

**Analog:** `src/main/java/com/onconavigator/domain/PathwayTemplate.java`

**Imports pattern** (lines 1-20):
```java
package com.onconavigator.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.envers.Audited;

import java.time.OffsetDateTime;
import java.util.UUID;
```

**Entity declaration pattern** (PathwayTemplate.java lines 34-37):
```java
@Entity
@Table(name = "patient_pathways")
@Audited
public class PatientPathway {
```

**Timestamp lifecycle pattern** (PathwayTemplate.java lines 59-73):
```java
@Column(name = "created_at", nullable = false, updatable = false)
private OffsetDateTime createdAt;

@Column(name = "updated_at", nullable = false)
private OffsetDateTime updatedAt;

@Column(name = "created_by", nullable = false, updatable = false)
private UUID createdBy;

@PrePersist
void prePersist() {
    OffsetDateTime now = OffsetDateTime.now();
    this.createdAt = now;
    this.updatedAt = now;
}

@PreUpdate
void preUpdate() {
    this.updatedAt = OffsetDateTime.now();
}
```

**FK relationship pattern** (CareEvent.java lines 44-46 -- for patient reference):
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "patient_id", nullable = false)
private Patient patient;
```

---

### `src/main/java/com/onconavigator/domain/PatientPathwayStep.java` (model, CRUD)

**Analog:** `src/main/java/com/onconavigator/domain/CareEvent.java`

**Enum column mapping pattern** (CareEvent.java lines 48-50):
```java
@Enumerated(EnumType.STRING)
@Column(name = "event_type", columnDefinition = "care_event_type", nullable = false)
private CareEventType eventType;
```

**ManyToOne parent reference pattern** (CareEvent.java lines 44-46):
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "patient_id", nullable = false)
private Patient patient;
```

**Optimistic locking pattern** (from RESEARCH.md -- new to codebase):
```java
@Version
@Column(name = "version")
private Integer version = 0;
```

**New: Status enum column** (combine CareEvent enum pattern with new PostgreSQL type):
```java
@Enumerated(EnumType.STRING)
@Column(name = "status", columnDefinition = "pathway_step_status", nullable = false)
private PathwayStepStatus status = PathwayStepStatus.ACTIVE;
```

---

### `src/main/java/com/onconavigator/domain/PatientPathwayEdge.java` (model, CRUD)

**Analog:** `src/main/java/com/onconavigator/domain/PhysicianOverride.java`

**Write-once entity pattern** (PhysicianOverride.java lines 32-78):
```java
@Entity
@Table(name = "patient_pathway_edges")
@Audited
public class PatientPathwayEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pathway_id", nullable = false)
    private PatientPathway pathway;

    @Column(name = "source_step_id", nullable = false, updatable = false)
    private UUID sourceStepId;

    @Column(name = "target_step_id", nullable = false, updatable = false)
    private UUID targetStepId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @PrePersist
    void prePersist() {
        this.createdAt = OffsetDateTime.now();
    }
}
```

**Note:** Edges are write-once like PhysicianOverride -- only `@PrePersist`, no `@PreUpdate`. Deletion is the only mutation (cascade on step removal per D-10).

---

### `src/main/java/com/onconavigator/domain/enums/PathwayStepStatus.java` (model, enum)

**Analog:** `src/main/java/com/onconavigator/domain/enums/CareEventType.java`

**Enum pattern** (CareEventType.java lines 1-20):
```java
package com.onconavigator.domain.enums;

/**
 * Status of a per-patient pathway step.
 * Maps to the pathway_step_status PostgreSQL enum.
 */
public enum PathwayStepStatus {
    ACTIVE,
    PROPOSED,
    COMPLETED,
    SKIPPED
}
```

---

### `src/main/java/com/onconavigator/repository/PatientPathwayRepository.java` (repository, CRUD)

**Analog:** `src/main/java/com/onconavigator/repository/PathwayTemplateRepository.java`

**Repository pattern** (PathwayTemplateRepository.java lines 1-31):
```java
package com.onconavigator.repository;

import com.onconavigator.domain.PatientPathway;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PatientPathwayRepository extends JpaRepository<PatientPathway, UUID> {

    Optional<PatientPathway> findByPatientId(UUID patientId);
}
```

---

### `src/main/java/com/onconavigator/repository/PatientPathwayStepRepository.java` (repository, CRUD)

**Analog:** `src/main/java/com/onconavigator/repository/CareEventRepository.java`

**Repository with FK query pattern** (CareEventRepository.java lines 1-24):
```java
package com.onconavigator.repository;

import com.onconavigator.domain.PatientPathwayStep;
import com.onconavigator.domain.enums.PathwayStepStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PatientPathwayStepRepository extends JpaRepository<PatientPathwayStep, UUID> {

    List<PatientPathwayStep> findByPathway_Id(UUID pathwayId);

    List<PatientPathwayStep> findByPathway_IdAndStatus(UUID pathwayId, PathwayStepStatus status);
}
```

---

### `src/main/java/com/onconavigator/repository/PatientPathwayEdgeRepository.java` (repository, CRUD)

**Analog:** `src/main/java/com/onconavigator/repository/PhysicianOverrideRepository.java`

**Repository with compound query pattern** (PhysicianOverrideRepository.java lines 1-58):
```java
package com.onconavigator.repository;

import com.onconavigator.domain.PatientPathwayEdge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PatientPathwayEdgeRepository extends JpaRepository<PatientPathwayEdge, UUID> {

    List<PatientPathwayEdge> findByPathway_Id(UUID pathwayId);

    void deleteBySourceStepIdOrTargetStepId(UUID sourceStepId, UUID targetStepId);
}
```

---

### `src/main/java/com/onconavigator/service/PathwayForkService.java` (service, CRUD)

**Analog:** `src/main/java/com/onconavigator/service/PatientService.java`

**Service declaration + logging pattern** (PatientService.java lines 1-63):
```java
package com.onconavigator.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PathwayForkService {

    private static final Logger log = LoggerFactory.getLogger(PathwayForkService.class);

    // Constructor injection of repositories
}
```

**Transaction + entity creation pattern** (PatientService.java lines 77-103):
```java
@Transactional
public PatientPathway forkFromTemplate(UUID patientId, UUID templateId, UUID actorId) {
    // 1. Load template or throw
    PathwayTemplate template = templateRepository.findById(templateId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Template not found"));
    // 2. Create parent entity
    // 3. Iterate and create child entities
    // 4. Log with UUID only
    log.info("Forked template {} for patient {} ({} steps, {} edges)",
            templateId, patientId, stepCount, edgeCount);
    return pathway;
}
```

---

### `src/main/java/com/onconavigator/service/PatientPathwayService.java` (service, CRUD)

**Analog:** `src/main/java/com/onconavigator/service/PatientService.java`

**Service CRUD + Temporal signal pattern** (PatientService.java lines 176-206):
```java
public CareEventResponse addCareEvent(UUID patientId, CreateCareEventRequest req, UUID actorId) {
    Patient patient = patientRepository.findById(patientId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

    CareEvent event = new CareEvent();
    // ... set fields from request ...
    CareEvent saved = careEventRepository.save(event);

    // Signal Temporal workflow after mutation
    pathwayService.signalCareEventChanged(patientId, saved.getId());

    log.info("Added care event {} for patient {}", saved.getId(), patientId);
    return toCareEventResponse(saved);
}
```

**Note:** PatientPathwayService will follow the same pattern -- persist step/edge, then signal `pathwayStepsChanged`.

---

### `src/main/java/com/onconavigator/web/PatientPathwayController.java` (controller, request-response)

**Analog:** `src/main/java/com/onconavigator/web/CareEventController.java`

**Nested resource controller pattern** (CareEventController.java lines 1-104):
```java
@RestController
@RequestMapping("/api/patients/{patientId}/pathway")
public class PatientPathwayController {

    private final PatientPathwayService patientPathwayService;

    public PatientPathwayController(PatientPathwayService patientPathwayService) {
        this.patientPathwayService = patientPathwayService;
    }

    @GetMapping("/steps")
    @PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')")
    public List<PathwayStepResponse> getSteps(@PathVariable UUID patientId) {
        return patientPathwayService.getSteps(patientId);
    }

    @PostMapping("/steps")
    @PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public PathwayStepResponse createStep(
            @PathVariable UUID patientId,
            @Valid @RequestBody PathwayStepRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        return patientPathwayService.createStep(patientId, request, actorId);
    }

    @PutMapping("/steps/{stepId}")
    @PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')")
    public PathwayStepResponse updateStep(
            @PathVariable UUID patientId,
            @PathVariable UUID stepId,
            @Valid @RequestBody PathwayStepRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        return patientPathwayService.updateStep(patientId, stepId, request, actorId);
    }

    @DeleteMapping("/steps/{stepId}")
    @PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStep(@PathVariable UUID patientId, @PathVariable UUID stepId) {
        patientPathwayService.deleteStep(patientId, stepId);
    }

    @PostMapping("/edges")
    @PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public PathwayEdgeResponse createEdge(
            @PathVariable UUID patientId,
            @Valid @RequestBody PathwayEdgeRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        return patientPathwayService.createEdge(patientId, request, actorId);
    }

    @DeleteMapping("/edges/{edgeId}")
    @PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEdge(@PathVariable UUID patientId, @PathVariable UUID edgeId) {
        patientPathwayService.deleteEdge(patientId, edgeId);
    }
}
```

**Key differences from CareEventController:** D-03 specifies ALL clinical roles can edit pathway steps, so `@PreAuthorize` includes NURSE_NAVIGATOR for write operations (unlike CareEventController which restricts writes to CARE_COORDINATOR + ADMIN only).

---

### `src/main/java/com/onconavigator/web/dto/PathwayStepRequest.java` (dto, request-response)

**Analog:** `src/main/java/com/onconavigator/web/dto/CreateCareEventRequest.java`

**Request record with validation pattern** (CreateCareEventRequest.java lines 1-end):
```java
package com.onconavigator.web.dto;

import com.onconavigator.domain.enums.CareEventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PathwayStepRequest(
        @NotBlank(message = "Step name is required") String name,
        String description,
        CareEventType eventType,
        Integer windowDays,
        boolean required,
        String alertText,
        String suggestedAction
) {}
```

---

### `src/main/java/com/onconavigator/web/dto/PathwayEdgeRequest.java` (dto, request-response)

**Analog:** `src/main/java/com/onconavigator/web/dto/CreateCareEventRequest.java`

```java
package com.onconavigator.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record PathwayEdgeRequest(
        @NotNull(message = "Source step ID is required") UUID sourceStepId,
        @NotNull(message = "Target step ID is required") UUID targetStepId
) {}
```

---

### `src/main/java/com/onconavigator/workflow/PatientPathwayWorkflow.java` (modified -- add signal)

**Analog:** Self -- existing `careEventChanged` signal method (lines 47-48)

**Signal method declaration pattern** (PatientPathwayWorkflow.java lines 47-48):
```java
@SignalMethod
void careEventChanged(UUID careEventId);
```

**New signal (no parameters per RESEARCH.md -- signal just wakes the workflow):**
```java
/**
 * Signals that the patient's pathway steps or edges have been modified.
 * Causes the workflow to re-evaluate the pathway immediately.
 */
@SignalMethod
void pathwayStepsChanged();
```

---

### `src/main/java/com/onconavigator/workflow/PatientPathwayWorkflowImpl.java` (modified -- handle signal)

**Analog:** Self -- existing `careEventChanged` handler (lines 132-134)

**Signal handler pattern** (PatientPathwayWorkflowImpl.java lines 132-134):
```java
@Override
public void careEventChanged(UUID careEventId) {
    signalReceived = true;
}
```

**New signal handler (identical mechanism -- sets same flag):**
```java
@Override
public void pathwayStepsChanged() {
    signalReceived = true;
}
```

**Note:** The main loop `Workflow.await(Duration.ofHours(24), () -> signalReceived || deactivated)` does NOT change. The new signal uses the same `signalReceived` boolean. This is replay-safe per Temporal docs.

---

### `src/main/java/com/onconavigator/service/PathwayService.java` (modified -- add signal method)

**Analog:** Self -- existing `signalCareEventChanged` (lines 96-104)

**Temporal signal sending pattern** (PathwayService.java lines 96-104):
```java
public void signalCareEventChanged(UUID patientId, UUID careEventId) {
    PatientPathwayWorkflow workflow = workflowClient.newWorkflowStub(
            PatientPathwayWorkflow.class,
            TemporalConfig.PATHWAY_WORKFLOW_ID_PREFIX + patientId);

    workflow.careEventChanged(careEventId);

    log.info("Sent careEventChanged signal for patient {} careEvent {}", patientId, careEventId);
}
```

**New method (same pattern, no payload):**
```java
public void signalPathwayStepsChanged(UUID patientId) {
    PatientPathwayWorkflow workflow = workflowClient.newWorkflowStub(
            PatientPathwayWorkflow.class,
            TemporalConfig.PATHWAY_WORKFLOW_ID_PREFIX + patientId);

    workflow.pathwayStepsChanged();

    log.info("Sent pathwayStepsChanged signal for patient {}", patientId);
}
```

---

### Flyway Migrations

#### `V13__create_per_patient_pathway_tables.sql` (migration, CRUD)

**Analog:** `src/main/resources/db/migration/V5__create_physician_overrides.sql`

**Table creation pattern** (V5 lines 1-25):
```sql
-- V13__create_per_patient_pathway_tables.sql
-- Per-patient pathway instances + steps + DAG edges (Phase 5)
-- No PHI stored: step names are clinical process data, not patient-identifying.

CREATE TABLE patient_pathways (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id UUID NOT NULL REFERENCES patients(id),
    source_template_id UUID REFERENCES pathway_templates(id),
    source_template_version INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by UUID NOT NULL,
    CONSTRAINT uq_patient_pathways_patient UNIQUE (patient_id)
);

-- ... steps and edges tables ...

-- Index convention (V1 pattern):
CREATE INDEX idx_patient_pathway_steps_pathway ON patient_pathway_steps(pathway_id);

-- Grant convention (V5 pattern):
GRANT ALL ON patient_pathways TO onco_app;
GRANT ALL ON patient_pathway_steps TO onco_app;
GRANT ALL ON patient_pathway_edges TO onco_app;
```

#### `V14__create_pathway_step_status_enum.sql` (migration, enum)

**Analog:** `src/main/resources/db/migration/V1__create_base_schema.sql` (lines 1-15)

**PostgreSQL enum creation pattern** (V1 lines 6-15):
```sql
-- V14__create_pathway_step_status_enum.sql
CREATE TYPE pathway_step_status AS ENUM ('ACTIVE', 'PROPOSED', 'COMPLETED', 'SKIPPED');
```

---

### `src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java` (rewrite)

**Analog:** Self (current implementation being rewritten)

**Activity structure pattern** (PathwayEvaluationActivityImpl.java lines 58-63):
```java
@Component
public class PathwayEvaluationActivityImpl implements PathwayEvaluationActivity {

    private static final Logger log = LoggerFactory.getLogger(PathwayEvaluationActivityImpl.class);

    // Constructor-injected repositories
}
```

**Core evaluation method structure** (lines 100-253 -- rewrite replaces JSONB with per-patient queries):
```java
@Override
@Transactional
public PathwayEvaluationResult evaluate(UUID patientId) {
    // 1. Fetch patient
    // 2. Query per-patient steps (ACTIVE only) -- REPLACES template lookup
    // 3. Query per-patient edges -- NEW
    // 4. Build DAG adjacency list -- NEW
    // 5. Topological sort (Kahn's algorithm) -- NEW
    // 6. Identify "ready" steps (all prerequisites COMPLETED)
    // 7. For each ready step, check care events by eventType -- SAME LOGIC
    // 8. Detect MISSING/DELAYED/OUT_OF_ORDER -- SAME LOGIC with new anchor resolution
    // 9. Return PathwayEvaluationResult
}
```

**Alert dedup check pattern** (PathwayEvaluationActivityImpl.java lines 176-178):
```java
boolean isDuplicate = alertRepository.existsByPatientIdAndPathwayStepNameAndStatus(
        patientId, step.name(), AlertStatus.OPEN);
if (!isDuplicate) {
    // create alert...
}
```

**Key change:** The anchor date resolution changes from `resolveAnchorDate(step, patient, completedByStepId, steps)` with AnchorType enum to edge-based: "anchor = MAX(completion date of all prerequisite steps); root steps anchor to diagnosis date."

---

### Frontend: `frontend/src/features/patients/types.ts` (modified)

**Analog:** Self (existing type definitions)

**Type definition pattern** (types.ts lines 56-69):
```typescript
// New types to add:
export type PathwayStepStatusEnum = 'ACTIVE' | 'PROPOSED' | 'COMPLETED' | 'SKIPPED';

export interface PatientPathwayStep {
  id: string;
  pathwayId: string;
  name: string;
  description: string | null;
  eventType: string | null;
  windowDays: number | null;
  required: boolean;
  status: PathwayStepStatusEnum;
  skipReason: string | null;
  alertText: string | null;
  suggestedAction: string | null;
  completedAt: string | null;
  completedCareEventId: string | null;
  createdAt: string;
}

export interface PatientPathwayEdge {
  id: string;
  pathwayId: string;
  sourceStepId: string;
  targetStepId: string;
  createdAt: string;
}

// Modified PathwayStepStatus -- add depth and status enum
export interface PathwayStepStatus {
  stepId: string;
  stepName: string;
  status: PathwayStepStatusEnum;
  depth: number;
  completionDate: string | null;
  timingInfo: string;
  hasActiveAlert: boolean;
  prerequisites: string[];  // step IDs
}
```

---

### Frontend: `frontend/src/features/patients/api.ts` (modified)

**Analog:** Self (existing hook patterns)

**Query hook pattern** (api.ts lines 9-16):
```typescript
export function usePathwaySteps(patientId: string) {
  return useQuery({
    queryKey: ['patients', patientId, 'pathway-steps'],
    queryFn: () => apiClient.get<PatientPathwayStep[]>(
      `/patients/${patientId}/pathway/steps`),
  });
}
```

**Mutation hook with invalidation pattern** (api.ts lines 58-72):
```typescript
export function useCreateStep(patientId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateStepRequest) =>
      apiClient.post<PatientPathwayStep>(
        `/patients/${patientId}/pathway/steps`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-steps'] });
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-status'] });
    },
  });
}

export function useDeleteStep(patientId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (stepId: string) =>
      apiClient.delete<void>(`/patients/${patientId}/pathway/steps/${stepId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-steps'] });
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-status'] });
    },
  });
}

export function useCreateEdge(patientId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreateEdgeRequest) =>
      apiClient.post<PatientPathwayEdge>(
        `/patients/${patientId}/pathway/edges`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-steps'] });
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-status'] });
    },
  });
}

export function useDeleteEdge(patientId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (edgeId: string) =>
      apiClient.delete<void>(`/patients/${patientId}/pathway/edges/${edgeId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-steps'] });
      queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-status'] });
    },
  });
}
```

---

### Frontend: `frontend/src/features/patients/PathwayDAGView.tsx` (component)

**Analog:** `frontend/src/routes/patients/$patientId.tsx` (pathway section, lines 304-347)

**Existing pathway list rendering pattern** ($patientId.tsx lines 325-346):
```typescript
<ol className="space-y-1">
  {pathwayStatus.steps.map((step) => (
    <li
      key={step.stepId}
      className={`flex items-start gap-3 rounded-md p-3 min-h-[44px] ${
        step.hasActiveAlert ? 'bg-amber-50' : ''
      }`}
    >
      <PathwayStepIcon step={step} />
      <div className="min-w-0">
        <p className="font-medium text-sm leading-snug">
          {step.stepNumber}. {step.stepName}
        </p>
        <p className="text-xs text-muted-foreground mt-0.5">
          {step.timingInfo}
        </p>
      </div>
    </li>
  ))}
</ol>
```

**Enhancement for DAG depth:** Add `paddingLeft` based on `step.depth` for indentation, and branching indicators (characters or SVG lines) for parallel steps at the same depth.

---

### Frontend: `frontend/src/features/patients/PathwayEditor.tsx` (component, CRUD)

**Analog:** `frontend/src/features/patients/QuickAddCareEventDialog.tsx`

**Form + mutation pattern** (QuickAddCareEventDialog.tsx lines 1-33):
```typescript
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';

import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select';

const stepSchema = z.object({
  name: z.string().min(1, { error: 'Step name is required.' }),
  eventType: z.string().optional(),
  windowDays: z.coerce.number().int().positive().optional(),
  required: z.boolean().default(true),
});
```

**Mutation error display pattern** (QuickAddCareEventDialog.tsx lines 208-212):
```typescript
{createStep.isError && (
  <p className="text-destructive text-sm">
    An error occurred while saving. Your changes were not saved. Please try again.
  </p>
)}
```

---

### Frontend: `frontend/src/features/patients/TemplatePicker.tsx` (component)

**Analog:** `frontend/src/features/patients/PatientWizard.tsx` (Step 2, lines 289-368)

**Select/RadioGroup form pattern** (PatientWizard.tsx Step 2 cancer type select):
```typescript
<div className="grid gap-2">
  <Label htmlFor="pathwayMode">Pathway Setup</Label>
  <RadioGroup
    defaultValue="template"
    onValueChange={(value) => setPathwayMode(value)}
  >
    <div className="flex items-center space-x-2">
      <RadioGroupItem value="template" id="template" />
      <Label htmlFor="template">Start from template</Label>
    </div>
    <div className="flex items-center space-x-2">
      <RadioGroupItem value="empty" id="empty" />
      <Label htmlFor="empty">Build from documents</Label>
    </div>
  </RadioGroup>
</div>
```

**Integration point:** TemplatePicker is embedded in PatientWizard Step 2 -- the wizard passes the selected pathwayMode to the create patient mutation.

---

## Shared Patterns

### Authentication / Authorization
**Source:** `src/main/java/com/onconavigator/web/CareEventController.java` lines 54-56, 71-72
**Apply to:** All new controller endpoints

```java
// D-03: All clinical roles can edit pathway steps
@PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')")
```

### Actor ID Extraction
**Source:** `src/main/java/com/onconavigator/web/PatientController.java` lines 68-69
**Apply to:** All POST/PUT/DELETE controller endpoints that need `createdBy`

```java
@AuthenticationPrincipal Jwt jwt
// ...
UUID actorId = UUID.fromString(jwt.getSubject());
```

### Error Handling (Backend)
**Source:** `src/main/java/com/onconavigator/web/GlobalExceptionHandler.java`
**Apply to:** All controllers (already global via @RestControllerAdvice)

Service layer throws `ResponseStatusException` for domain errors:
```java
// PatientService.java lines 125-127
Patient patient = patientRepository.findById(patientId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));
```

### Temporal Signal After Mutation
**Source:** `src/main/java/com/onconavigator/service/PatientService.java` lines 202-203
**Apply to:** All step/edge CRUD operations in PatientPathwayService

```java
// After persisting change:
pathwayService.signalPathwayStepsChanged(patientId);
log.info("Step/edge mutation for patient {}, signaled workflow", patientId);
```

### Entity Timestamp Lifecycle
**Source:** `src/main/java/com/onconavigator/domain/Patient.java` lines 92-102
**Apply to:** PatientPathway, PatientPathwayStep (entities with update operations)

```java
@PrePersist
void prePersist() {
    OffsetDateTime now = OffsetDateTime.now();
    this.createdAt = now;
    this.updatedAt = now;
}

@PreUpdate
void preUpdate() {
    this.updatedAt = OffsetDateTime.now();
}
```

### Hibernate Envers
**Source:** All domain entities (`@Audited` annotation)
**Apply to:** PatientPathway, PatientPathwayStep, PatientPathwayEdge

```java
@Entity
@Table(name = "...")
@Audited  // Creates _AUD revision table for HIPAA audit
public class ... {
```

### Frontend API Client
**Source:** `frontend/src/lib/api-client.ts`
**Apply to:** All new TanStack Query hooks

```typescript
import { apiClient } from '@/lib/api-client';
// apiClient.get<T>(path), .post<T>(path, body), .put<T>(path, body), .delete<T>(path)
```

### Frontend Query Invalidation
**Source:** `frontend/src/features/patients/api.ts` lines 60-70
**Apply to:** All step/edge mutation hooks

```typescript
onSuccess: () => {
  queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-steps'] });
  queryClient.invalidateQueries({ queryKey: ['patients', patientId, 'pathway-status'] });
},
```

### Frontend Form Pattern (Zod v4 + react-hook-form)
**Source:** `frontend/src/features/patients/QuickAddCareEventDialog.tsx` lines 1-34
**Apply to:** AddStepForm, EdgeEditor, SkipStepDialog

```typescript
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';

const schema = z.object({
  fieldName: z.string().min(1, { error: 'Field is required.' }),
});
type FormValues = z.infer<typeof schema>;

const form = useForm<FormValues>({
  resolver: zodResolver(schema),
  defaultValues: { fieldName: '' },
});
```

### PHI Logging Safety
**Source:** `src/main/java/com/onconavigator/service/PatientService.java` class Javadoc
**Apply to:** All new services and activities

```java
/**
 * PHI safety: Log statements contain ONLY patient UUIDs and step UUIDs.
 * Step names are NOT PHI (clinical process data), but maintain UUID-only convention.
 */
```

### Flyway Migration Conventions
**Source:** `V1__create_base_schema.sql`, `V5__create_physician_overrides.sql`
**Apply to:** All new migration files

- Comment header explaining purpose
- `gen_random_uuid()` for UUID PKs
- `TIMESTAMP WITH TIME ZONE` for all timestamps
- `DEFAULT NOW()` for created_at
- `REFERENCES` for FK constraints
- Named constraints for UNIQUE (`CONSTRAINT uq_...`) and CHECK (`CONSTRAINT chk_...`)
- Indexes with `idx_` prefix convention
- `GRANT ALL ON ... TO onco_app;` for all new tables

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `V15__migrate_patients_to_per_patient_pathways.sql` | migration | batch/transform | No existing data migration (JSONB-to-relational) exists in the codebase. Use `jsonb_array_elements()` + `INSERT...SELECT` pattern from RESEARCH.md. Closest reference is V6 seed data migration, but that inserts static data rather than transforming existing records. |

---

## Metadata

**Analog search scope:** `src/main/java/com/onconavigator/`, `src/main/resources/db/migration/`, `frontend/src/features/patients/`, `frontend/src/routes/patients/`, `frontend/src/lib/`
**Files scanned:** 85+ (all Java sources, all migrations, all patient feature frontend files)
**Pattern extraction date:** 2026-05-04
