# Phase 8: Template Inheritance - Pattern Map

**Mapped:** 2026-05-05
**Files analyzed:** 16 (new/modified)
**Analogs found:** 14 / 16

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `src/.../domain/PathwayTemplate.java` | model | CRUD | self (existing entity) | exact |
| `src/.../domain/dto/TemplateDiff.java` | model (record DTO) | transform | `src/.../domain/dto/PathwayStep.java` | exact |
| `src/.../domain/dto/StepOverride.java` | model (record DTO) | transform | `src/.../domain/dto/PathwayStep.java` | exact |
| `src/.../domain/dto/EdgeChanges.java` | model (record DTO) | transform | `src/.../domain/dto/AnchorType.java` | role-match |
| `src/.../domain/dto/EdgeRef.java` | model (record DTO) | transform | `src/.../domain/dto/AnchorType.java` | role-match |
| `src/.../repository/PathwayTemplateRepository.java` | repository | CRUD | self (existing repository) | exact |
| `src/.../service/TemplateMergeService.java` | service | transform | `src/.../service/PathwayForkService.java` | role-match |
| `src/.../service/PathwayForkService.java` | service | CRUD | self (existing service) | exact |
| `src/.../service/PatientService.java` | service | CRUD | self (existing service) | exact |
| `src/.../web/PathwayTemplateController.java` | controller | request-response | `src/.../web/DashboardController.java` | exact |
| `src/.../web/dto/PathwayTemplateResponse.java` | model (response DTO) | request-response | `src/.../web/dto/AlertResponse.java` | exact |
| `src/.../web/dto/CreatePatientRequest.java` | model (request DTO) | request-response | self (existing DTO) | exact |
| `V19__template_inheritance.sql` | migration | batch | `V18__add_care_event_scheduling_fields.sql` | exact |
| `V20__seed_rectal_template.sql` | migration | batch | `V6__seed_pathway_templates.sql` | exact |
| `frontend/src/features/patients/TemplatePicker.tsx` | component | request-response | self (existing component) | exact |
| `frontend/src/features/patients/PatientWizard.tsx` | component | request-response | self (existing component) | exact |

## Pattern Assignments

### `src/main/java/com/onconavigator/domain/PathwayTemplate.java` (model, CRUD -- MODIFY)

**Analog:** Self -- add fields following existing entity conventions.

**Entity field pattern** (lines 39-66):
```java
@Id
@GeneratedValue(strategy = GenerationType.UUID)
private UUID id;

@Enumerated(EnumType.STRING)
@Column(name = "cancer_type", columnDefinition = "cancer_type", nullable = false, unique = true)
private CancerType cancerType;

@Column(name = "version", nullable = false)
private Integer version = 1;

@JdbcTypeCode(SqlTypes.JSON)
@Column(name = "template_data", columnDefinition = "jsonb", nullable = false)
private String templateData;
```

**New fields to add (follow same `@Column` convention):**
- `parentTemplateId` -- plain UUID column, nullable, no `@ManyToOne` (matches project convention from `ClinicalDocument.careEventId`)
- `name` -- `@Column(name = "name", nullable = false)`, String
- `description` -- `@Column(name = "description")`, String, nullable

**Key note:** Remove `unique = true` from the `@Column` annotation on `cancerType` (line 44). The Flyway migration drops the DB constraint; the JPA annotation must match.

**Lifecycle hooks pattern** (lines 68-78):
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

**Class-level annotations** (lines 34-37):
```java
@Entity
@Table(name = "pathway_templates")
@Audited
public class PathwayTemplate {
```

---

### `src/main/java/com/onconavigator/domain/dto/TemplateDiff.java` (record DTO, transform -- NEW)

**Analog:** `src/main/java/com/onconavigator/domain/dto/PathwayStep.java`

**Record DTO pattern** (lines 1-13, 31-44):
```java
package com.onconavigator.domain.dto;

import com.onconavigator.domain.enums.CareEventType;
import jakarta.annotation.Nullable;

import java.util.List;

public record PathwayStep(
        String stepId,
        int stepNumber,
        String name,
        // ... fields ...
        List<String> prerequisites
) {
}
```

**Key conventions:**
- Package: `com.onconavigator.domain.dto`
- Uses Java records (not classes)
- Jackson deserializes via canonical record constructor (field names match JSONB keys)
- Javadoc on the record describes the JSONB source and field meanings
- Null-safe defaults in compact constructor for collection fields (e.g., `overrides = overrides != null ? overrides : List.of()`)

---

### `src/main/java/com/onconavigator/domain/dto/StepOverride.java` (record DTO, transform -- NEW)

**Analog:** `src/main/java/com/onconavigator/domain/dto/PathwayStep.java`

**Same pattern as TemplateDiff.** Simple record with `stepId` (String) and `fields` (Map<String, Object>). Place in `com.onconavigator.domain.dto` package.

---

### `src/main/java/com/onconavigator/domain/dto/EdgeChanges.java` (record DTO, transform -- NEW)

**Analog:** `src/main/java/com/onconavigator/domain/dto/AnchorType.java` (same package, simple data type)

**Package pattern** (AnchorType line 1):
```java
package com.onconavigator.domain.dto;
```

Record with `List<EdgeRef> remove` and `List<EdgeRef> add`. Null-safe defaults in compact constructor.

---

### `src/main/java/com/onconavigator/domain/dto/EdgeRef.java` (record DTO, transform -- NEW)

**Same package and pattern.** Simple record: `EdgeRef(String from, String to)`.

---

### `src/main/java/com/onconavigator/repository/PathwayTemplateRepository.java` (repository, CRUD -- MODIFY)

**Analog:** Self.

**Current pattern** (lines 1-31):
```java
package com.onconavigator.repository;

import com.onconavigator.domain.PathwayTemplate;
import com.onconavigator.domain.enums.CancerType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PathwayTemplateRepository extends JpaRepository<PathwayTemplate, UUID> {

    Optional<PathwayTemplate> findByCancerType(CancerType cancerType);
}
```

**Changes needed:**
- `findByCancerType` return type changes from `Optional<PathwayTemplate>` to `List<PathwayTemplate>`
- Add `findByCancerTypeAndParentTemplateIdIsNull(CancerType cancerType)` returning `Optional<PathwayTemplate>` (root template lookup)
- Add `findByParentTemplateId(UUID parentTemplateId)` returning `List<PathwayTemplate>` (child template lookup)
- Update Javadoc to remove UNIQUE constraint reference

---

### `src/main/java/com/onconavigator/service/TemplateMergeService.java` (service, transform -- NEW)

**Analog:** `src/main/java/com/onconavigator/service/PathwayForkService.java`

**Service class structure** (PathwayForkService lines 1-62):
```java
package com.onconavigator.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.onconavigator.domain.dto.PathwayStep;
// ... other imports ...
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PathwayForkService {

    private static final Logger log = LoggerFactory.getLogger(PathwayForkService.class);

    private final ObjectMapper objectMapper;
    // ... other dependencies ...

    public PathwayForkService(/* constructor injection */) {
        this.objectMapper = objectMapper;
        // ...
    }
```

**Key conventions from PathwayForkService:**
- `@Service` annotation
- Constructor injection (no `@Autowired`)
- SLF4J `Logger` + `LoggerFactory`
- `ObjectMapper` injected for JSONB processing
- Comprehensive Javadoc on public methods with `@param` / `@return` / `@throws`
- Log statements use UUIDs only (PHI safety)

**JSONB parsing pattern** (PathwayForkService lines 85-93):
```java
List<PathwayStep> templateSteps;
try {
    templateSteps = objectMapper.readValue(
            template.getTemplateData(),
            new TypeReference<List<PathwayStep>>() {});
} catch (Exception e) {
    throw new IllegalStateException(
            "Failed to parse template data for template " + template.getId(), e);
}
```

**TemplateMergeService should follow this pattern but:**
- Inject only `ObjectMapper` (no repositories -- pure function)
- Public method signature: `List<PathwayStep> merge(List<PathwayStep> parentSteps, TemplateDiff diff)`
- No `@Transactional` (stateless computation, not persistence)
- JSONB parsing for the child diff: `objectMapper.readValue(childTemplateData, TemplateDiff.class)`

---

### `src/main/java/com/onconavigator/service/PathwayForkService.java` (service, CRUD -- MODIFY)

**Analog:** Self.

**Current fork pattern** (lines 77-148):
```java
@Transactional
public PatientPathway forkFromTemplate(Patient patient, UUID actorId) {
    // 1. Find template by cancer type
    PathwayTemplate template = templateRepository.findByCancerType(patient.getCancerType())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "No pathway template found for cancer type " + patient.getCancerType()));

    // 2. Parse JSONB template_data into List<PathwayStep>
    List<PathwayStep> templateSteps;
    try {
        templateSteps = objectMapper.readValue(
                template.getTemplateData(),
                new TypeReference<List<PathwayStep>>() {});
    } catch (Exception e) {
        throw new IllegalStateException(
                "Failed to parse template data for template " + template.getId(), e);
    }

    // 3-5: Create pathway, deep copy steps, deep copy edges
    // ...
}
```

**Modifications needed:**
1. Method signature changes: `forkFromTemplate(Patient patient, UUID templateId, UUID actorId)` -- accept explicit template ID
2. Replace `findByCancerType()` with `findById(templateId)`
3. After loading template, check `template.getParentTemplateId() != null`; if child, load parent, parse parent steps, parse child diff as `TemplateDiff`, call `templateMergeService.merge()`
4. Inject `TemplateMergeService` as new constructor dependency
5. Deep copy logic (lines 104-142) remains unchanged -- operates on `List<PathwayStep>` regardless of source

**Error handling pattern** (lines 80-82):
```java
PathwayTemplate template = templateRepository.findByCancerType(patient.getCancerType())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "No pathway template found for cancer type " + patient.getCancerType()));
```

---

### `src/main/java/com/onconavigator/service/PatientService.java` (service, CRUD -- MODIFY)

**Analog:** Self.

**Current fork call** (lines 97-102):
```java
// D-07: Template picker -- fork template or create empty pathway
if ("empty".equals(req.effectivePathwayMode())) {
    pathwayForkService.createEmptyPathway(saved, actorId);
} else {
    pathwayForkService.forkFromTemplate(saved, actorId);
}
```

**Modification:** Pass `req.templateId()` to `forkFromTemplate()`:
```java
pathwayForkService.forkFromTemplate(saved, req.templateId(), actorId);
```

When `templateId` is null (backward compat), look up root template by cancer type and pass its ID.

---

### `src/main/java/com/onconavigator/web/PathwayTemplateController.java` (controller, request-response -- NEW)

**Analog:** `src/main/java/com/onconavigator/web/DashboardController.java`

**Controller class pattern** (DashboardController lines 1-73):
```java
package com.onconavigator.web;

import com.onconavigator.service.AlertService;
import com.onconavigator.service.PatientService;
import com.onconavigator.web.dto.AlertResponse;
import com.onconavigator.web.dto.DashboardStatsResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final AlertService alertService;
    private final PatientService patientService;

    public DashboardController(AlertService alertService, PatientService patientService) {
        this.alertService = alertService;
        this.patientService = patientService;
    }

    @GetMapping("/stats")
    @PreAuthorize("isAuthenticated()")
    public DashboardStatsResponse getStats() {
        // ...
    }
}
```

**New controller should follow this exact structure:**
- `@RestController` + `@RequestMapping("/api/pathway-templates")`
- Constructor injection of service
- `@GetMapping` with `@RequestParam CancerType cancerType`
- `@PreAuthorize("isAuthenticated()")` -- templates are non-PHI config data, all roles can read
- Returns `List<PathwayTemplateResponse>`

**Also reference PatientController for @RequestParam pattern** (PatientController lines 82-89):
```java
@GetMapping
@PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')")
public List<PatientResponse> listPatients(
        @RequestParam(required = false) String mrn) {
    if (mrn != null && !mrn.isBlank()) {
        return patientService.findByMrn(mrn);
    }
    return patientService.findAll();
}
```

For the template controller, `cancerType` is required (not optional):
```java
@GetMapping
@PreAuthorize("isAuthenticated()")
public List<PathwayTemplateResponse> listTemplates(
        @RequestParam CancerType cancerType) {
```

---

### `src/main/java/com/onconavigator/web/dto/PathwayTemplateResponse.java` (response DTO, request-response -- NEW)

**Analog:** `src/main/java/com/onconavigator/web/dto/AlertResponse.java`

**Response DTO pattern** (AlertResponse lines 1-38):
```java
package com.onconavigator.web.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Response DTO for an alert surfaced by the pathway monitoring engine (per ALRT-02).
 * [Javadoc describing fields and HIPAA notes]
 */
public record AlertResponse(
        UUID id,
        UUID patientId,
        String patientName,
        // ...
) {}
```

**New DTO should follow same pattern:**
- Package: `com.onconavigator.web.dto`
- Java record
- Javadoc documenting field semantics
- Fields: `UUID id`, `CancerType cancerType`, `String name`, `String description`, `UUID parentTemplateId`, `int version`, `boolean isRoot`

---

### `src/main/java/com/onconavigator/web/dto/CreatePatientRequest.java` (request DTO, request-response -- MODIFY)

**Analog:** Self.

**Current record** (lines 26-49):
```java
public record CreatePatientRequest(
        @NotBlank(message = "First name is required") String firstName,
        @NotBlank(message = "Last name is required") String lastName,
        // ...
        String pathwayMode  // "template" (default) or "empty" per D-07
) {
    public String effectivePathwayMode() {
        return pathwayMode == null || pathwayMode.isBlank() ? "template" : pathwayMode;
    }
}
```

**Add `UUID templateId` field** (nullable, optional). When provided, overrides cancer-type-based template lookup. When null, falls back to root template for the cancer type (backward compat).

---

### `V19__template_inheritance.sql` (migration, batch -- NEW)

**Analog:** `V18__add_care_event_scheduling_fields.sql`

**Migration pattern** (V18 lines 1-21):
```sql
-- Phase 7: Add referral tracking and scheduling coordination fields.
-- Per D-01/D-02: referral_received_at on patients for pathway clock trigger.
-- Per D-04/D-07/D-10/D-13: scheduling fields on care_events for status-aware evaluation.

-- patients: referral received timestamp (not PHI -- no encryption needed)
ALTER TABLE patients
    ADD COLUMN IF NOT EXISTS referral_received_at TIMESTAMP WITH TIME ZONE;

-- care_events: scheduling coordination fields
ALTER TABLE care_events
    ADD COLUMN IF NOT EXISTS expected_completion_date DATE,
    ADD COLUMN IF NOT EXISTS scheduling_confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS external_facility_name VARCHAR(255);

-- Index for RESULTS_NOT_READY cross-event query (D-08: broad patient-level matching)
CREATE INDEX IF NOT EXISTS idx_care_events_patient_status_expected
    ON care_events(patient_id, status, expected_completion_date)
    WHERE expected_completion_date IS NOT NULL;

GRANT ALL ON patients TO onco_app;
GRANT ALL ON care_events TO onco_app;
```

**Key conventions:**
- Phase/decision reference comments at the top
- `ADD COLUMN IF NOT EXISTS` for safety
- Index creation with `IF NOT EXISTS`
- `GRANT ALL ON <table> TO onco_app` at the bottom
- Clear inline comments explaining each section

**Additional V19 concerns from RESEARCH.md Pitfall 4:** Must ALTER both `pathway_templates` AND `pathway_templates_aud` (Envers audit table). V18 did not need this because care_events is not `@Audited`, but pathway_templates IS `@Audited`.

---

### `V20__seed_rectal_template.sql` (migration, batch -- NEW)

**Analog:** `V6__seed_pathway_templates.sql`

**Seed data INSERT pattern** (V6 lines 12-107):
```sql
INSERT INTO pathway_templates (id, cancer_type, version, template_data, created_at, updated_at, created_by)
VALUES (
    gen_random_uuid(),
    'BREAST',
    1,
    '[
        {
            "stepId": "BREAST_01",
            ...
        }
    ]'::jsonb,
    NOW(),
    NOW(),
    '00000000-0000-0000-0000-000000000000'
);
```

**Key conventions:**
- `gen_random_uuid()` for ID generation
- JSONB literal with `::jsonb` cast
- `NOW()` for timestamps
- `'00000000-0000-0000-0000-000000000000'` as system `created_by`
- V20 adds `parent_template_id`, `name`, `description` columns to the INSERT
- **Pitfall 2 from RESEARCH.md:** Must use subquery for parent reference: `(SELECT id FROM pathway_templates WHERE cancer_type = 'COLORECTAL' AND parent_template_id IS NULL)`

---

### `frontend/src/features/patients/TemplatePicker.tsx` (component, request-response -- REWRITE)

**Analog:** Self (existing component).

**Current RadioGroup pattern** (lines 1-58):
```typescript
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import { Label } from '@/components/ui/label';

interface TemplatePickerProps {
  cancerType: string | null;
  value: 'template' | 'empty';
  onChange: (value: 'template' | 'empty') => void;
}

export function TemplatePicker({ cancerType, value, onChange }: TemplatePickerProps) {
  if (!cancerType) return null;

  return (
    <div className="grid gap-2">
      <Label>Pathway Setup</Label>
      <RadioGroup
        value={value}
        onValueChange={(v) => onChange(v as 'template' | 'empty')}
      >
        <div className="flex items-center space-x-2">
          <RadioGroupItem value="template" id="pathway-template" />
          <Label htmlFor="pathway-template" className="font-normal">
            Start from {templateName}
          </Label>
        </div>
        // ...
      </RadioGroup>
    </div>
  );
}
```

**Modifications:**
- Props change: add `selectedTemplateId` and `onTemplateIdChange` callback
- Fetch templates via new `usePathwayTemplates(cancerType)` hook
- When 1 template: auto-select, show existing simple radio (template vs empty)
- When 2+ templates: show variant radio group with template name and description
- Root template pre-selected as default (D-08)

**TanStack Query conditional fetch pattern** (from `useDocumentAlreadyCovered` in api.ts lines 219-233):
```typescript
export function useDocumentAlreadyCovered(documentId: string | null) {
  return useQuery({
    queryKey: ['documents', documentId, 'already-covered'],
    queryFn: () =>
      apiClient.get<{ alreadyCoveredEventTypes: string | null }>(
        `/documents/${documentId}`),
    enabled: !!documentId,
    staleTime: Infinity,
  });
}
```

This is the exact pattern for `usePathwayTemplates`:
```typescript
export function usePathwayTemplates(cancerType: string | null) {
  return useQuery({
    queryKey: ['pathway-templates', cancerType],
    queryFn: () => apiClient.get<PathwayTemplateResponse[]>(
      `/pathway-templates?cancerType=${encodeURIComponent(cancerType!)}`),
    enabled: !!cancerType,
    staleTime: 5 * 60 * 1000,
  });
}
```

---

### `frontend/src/features/patients/PatientWizard.tsx` (component, request-response -- MODIFY)

**Analog:** Self.

**Cancer type selection handler** (lines 298-302):
```typescript
<Select
  onValueChange={(value) => {
    form2.setValue('cancerType', value, { shouldValidate: true });
    setPathwayMode('template');
  }}
```

**TemplatePicker integration** (lines 326-330):
```typescript
<TemplatePicker
  cancerType={form2.watch('cancerType') || null}
  value={pathwayMode}
  onChange={setPathwayMode}
/>
```

**Payload construction** (lines 151-163):
```typescript
const payload = {
  firstName: step1Data.firstName,
  // ...
  pathwayMode,
};
```

**Modifications:**
- Add `selectedTemplateId` state (useState<string | null>)
- Pass `selectedTemplateId` and `onTemplateIdChange` to TemplatePicker
- Add `templateId: selectedTemplateId` to the payload
- Reset `selectedTemplateId` when cancer type changes (in the Select onValueChange handler)

---

### `frontend/src/features/patients/api.ts` (hook, request-response -- ADD)

**Analog:** Self -- follow existing `useQuery` hook pattern.

**Existing query hook pattern** (api.ts lines 11-18):
```typescript
export function usePatients(mrn?: string) {
  return useQuery({
    queryKey: mrn ? ['patients', { mrn }] : ['patients'],
    queryFn: () => apiClient.get<PatientResponse[]>(
      mrn ? `/patients?mrn=${encodeURIComponent(mrn)}` : '/patients'
    ),
  });
}
```

**Conditional query pattern** (api.ts lines 219-233):
```typescript
export function useDocumentAlreadyCovered(documentId: string | null) {
  return useQuery({
    queryKey: ['documents', documentId, 'already-covered'],
    queryFn: () =>
      apiClient.get<{ alreadyCoveredEventTypes: string | null }>(
        `/documents/${documentId}`),
    enabled: !!documentId,
    staleTime: Infinity,
  });
}
```

**Add `usePathwayTemplates` following the conditional pattern** with `enabled: !!cancerType`.

---

### `frontend/src/features/patients/types.ts` (types, N/A -- ADD)

**Analog:** Self -- follow existing interface pattern.

**Existing type pattern** (types.ts lines 1-16):
```typescript
export interface PatientResponse {
  id: string;
  firstName: string;
  lastName: string;
  dateOfBirth: string;
  mrn: string;
  cancerType: 'BREAST' | 'LUNG' | 'COLORECTAL';
  cancerStage: string;
  // ...
}
```

**Add `PathwayTemplateResponse` interface:**
```typescript
export interface PathwayTemplateResponse {
  id: string;
  cancerType: 'BREAST' | 'LUNG' | 'COLORECTAL';
  name: string;
  description: string | null;
  parentTemplateId: string | null;
  version: number;
}
```

**Also modify `CreatePatientRequest` interface** (types.ts lines 18-29) to add `templateId?: string`.

---

## Shared Patterns

### Authentication / Authorization
**Source:** `src/main/java/com/onconavigator/web/DashboardController.java` lines 54-56
**Apply to:** `PathwayTemplateController.java`
```java
@GetMapping("/stats")
@PreAuthorize("isAuthenticated()")
public DashboardStatsResponse getStats() {
```
Templates are non-PHI configuration data. All authenticated roles can read them. Use `@PreAuthorize("isAuthenticated()")`.

### Error Handling
**Source:** `src/main/java/com/onconavigator/web/GlobalExceptionHandler.java` (full file)
**Apply to:** All controller and service files
```java
// Controllers: throw ResponseStatusException with safe reason strings
throw new ResponseStatusException(HttpStatus.NOT_FOUND,
        "No pathway template found for cancer type " + patient.getCancerType());

// Services: throw IllegalStateException for infrastructure errors
throw new IllegalStateException(
        "Failed to parse template data for template " + template.getId(), e);
```
Both patterns are handled by `GlobalExceptionHandler` -- no controller-level try/catch needed.

### JSONB Parsing
**Source:** `src/main/java/com/onconavigator/service/PathwayForkService.java` lines 85-93
**Apply to:** `TemplateMergeService.java`, modified `PathwayForkService.java`
```java
List<PathwayStep> templateSteps;
try {
    templateSteps = objectMapper.readValue(
            template.getTemplateData(),
            new TypeReference<List<PathwayStep>>() {});
} catch (Exception e) {
    throw new IllegalStateException(
            "Failed to parse template data for template " + template.getId(), e);
}
```
For child templates, parse as `TemplateDiff.class` instead of `new TypeReference<List<PathwayStep>>() {}`.

### Constructor Injection
**Source:** `src/main/java/com/onconavigator/service/PathwayForkService.java` lines 52-62
**Apply to:** All new service and controller classes
```java
public PathwayForkService(PatientPathwayRepository pathwayRepository,
                          PatientPathwayStepRepository stepRepository,
                          PatientPathwayEdgeRepository edgeRepository,
                          PathwayTemplateRepository templateRepository,
                          ObjectMapper objectMapper) {
    this.pathwayRepository = pathwayRepository;
    this.stepRepository = stepRepository;
    this.edgeRepository = edgeRepository;
    this.templateRepository = templateRepository;
    this.objectMapper = objectMapper;
}
```
No `@Autowired`. All dependencies via constructor.

### Logging (PHI Safety)
**Source:** `src/main/java/com/onconavigator/service/PathwayForkService.java` lines 44, 144-145
**Apply to:** All new/modified service files
```java
private static final Logger log = LoggerFactory.getLogger(PathwayForkService.class);

// Log UUIDs only -- never patient names, DOBs, or MRNs
log.info("Forked template {} for patient {} ({} steps, {} edges)",
        template.getId(), patient.getId(), templateSteps.size(), edgeCount);
```

### TanStack Query Convention
**Source:** `frontend/src/features/patients/api.ts` lines 1-9
**Apply to:** New `usePathwayTemplates` hook
```typescript
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';
import type { /* types */ } from './types';
```
All data fetching uses `useQuery`/`useMutation` from TanStack Query v5. No `useEffect` + `fetch`.

### Flyway Migration Conventions
**Source:** `V18__add_care_event_scheduling_fields.sql` (full file)
**Apply to:** `V19__template_inheritance.sql`, `V20__seed_rectal_template.sql`
```sql
-- Phase N: Description of changes.
-- Per D-XX: Decision reference.

ALTER TABLE table_name
    ADD COLUMN IF NOT EXISTS column_name TYPE;

CREATE INDEX IF NOT EXISTS idx_name ON table_name(column);

GRANT ALL ON table_name TO onco_app;
```

### shadcn RadioGroup
**Source:** `frontend/src/features/patients/TemplatePicker.tsx` lines 33-49
**Apply to:** Rewritten `TemplatePicker.tsx`
```typescript
<RadioGroup
  value={value}
  onValueChange={(v) => onChange(v as 'template' | 'empty')}
>
  <div className="flex items-center space-x-2">
    <RadioGroupItem value="template" id="pathway-template" />
    <Label htmlFor="pathway-template" className="font-normal">
      Start from {templateName}
    </Label>
  </div>
</RadioGroup>
```

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `src/.../service/TemplateMergeService.java` | service | transform | No existing pure-function transform services. Closest is `PathwayForkService` for class structure, but the merge algorithm itself (removals -> overrides -> additions -> edge changes) is new logic with no existing codebase analog. Use RESEARCH.md Pattern 2 for the algorithm. |
| `src/.../domain/dto/TemplateDiff.java` | record DTO | transform | No existing diff/patch DTOs. Structure comes from RESEARCH.md Pattern 1 (diff JSONB schema). Record pattern from `PathwayStep.java`. |

Note: While `TemplateMergeService` has no functional analog, its class structure (package, annotations, injection, logging) follows `PathwayForkService` exactly. Only the merge algorithm is novel.

---

## Metadata

**Analog search scope:** `src/main/java/com/onconavigator/`, `src/main/resources/db/migration/`, `frontend/src/features/patients/`, `frontend/src/lib/`
**Files scanned:** 30+ (controllers, services, DTOs, repositories, migrations, frontend components and hooks)
**Pattern extraction date:** 2026-05-05
