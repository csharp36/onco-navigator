# Phase 3: Working Application - Pattern Map

**Mapped:** 2026-04-30
**Files analyzed:** 28 (13 backend, 15 frontend)
**Analogs found:** 28 / 28

---

## File Classification

| New / Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---------------------|------|-----------|----------------|---------------|
| `src/.../web/PatientController.java` | controller | CRUD | `src/.../web/HealthCheckController.java` + SecurityConfig patterns | role-match |
| `src/.../web/CareEventController.java` | controller | CRUD | `src/.../web/HealthCheckController.java` | role-match |
| `src/.../web/AlertController.java` | controller | CRUD + request-response | `src/.../web/HealthCheckController.java` | role-match |
| `src/.../web/DashboardController.java` | controller | request-response | `src/.../web/HealthCheckController.java` | role-match |
| `src/.../web/dto/CreatePatientRequest.java` | DTO (record) | request-response | `src/.../domain/dto/PathwayStep.java` | exact |
| `src/.../web/dto/PatientResponse.java` | DTO (record) | request-response | `src/.../domain/dto/PathwayStep.java` | exact |
| `src/.../web/dto/AlertResponse.java` | DTO (record) | request-response | `src/.../domain/dto/PathwayStep.java` | exact |
| `src/.../web/dto/CareEventResponse.java` | DTO (record) | request-response | `src/.../domain/dto/PathwayStep.java` | exact |
| `src/.../web/dto/ResolveAlertRequest.java` | DTO (record) | request-response | `src/.../domain/dto/PathwayStep.java` | exact |
| `src/.../web/dto/CreateCareEventRequest.java` | DTO (record) | request-response | `src/.../domain/dto/PathwayStep.java` | exact |
| `src/.../web/dto/UpdateCareEventStatusRequest.java` | DTO (record) | request-response | `src/.../domain/dto/PathwayStep.java` | exact |
| `src/.../web/dto/DeactivatePatientRequest.java` | DTO (record) | request-response | `src/.../domain/dto/PathwayStep.java` | exact |
| `src/.../web/dto/DashboardStatsResponse.java` | DTO (record) | request-response | `src/.../domain/dto/PathwayStep.java` | exact |
| `src/.../web/dto/PathwayStatusResponse.java` | DTO (record) | request-response | `src/.../domain/dto/PathwayStep.java` | exact |
| `src/.../service/PatientService.java` | service | CRUD | `src/.../service/AuditService.java` + `AlertGenerationActivityImpl.java` | role-match |
| `src/.../service/AlertService.java` | service | CRUD | `src/.../service/AuditService.java` | role-match |
| `src/.../service/PathwayStatusService.java` | service | transform | `src/.../activity/PathwayEvaluationActivityImpl.java` | role-match |
| `src/.../security/HmacTokenService.java` | service | transform | `src/.../config/EncryptionConfig.java` | partial-match |
| `src/main/resources/db/migration/V8__add_mrn_hmac_token.sql` | migration | batch | `V7__alert_dedup_index.sql` | exact |
| `frontend/src/routes/index.tsx` (rewrite) | route/page | request-response | `frontend/src/routes/index.tsx` (existing placeholder) | exact |
| `frontend/src/routes/patients/index.tsx` | route/page | CRUD | `frontend/src/routes/index.tsx` | role-match |
| `frontend/src/routes/patients/new.tsx` | route/page | CRUD | `frontend/src/routes/login.tsx` | role-match |
| `frontend/src/routes/patients/$patientId.tsx` | route/page | request-response | `frontend/src/routes/login.tsx` | role-match |
| `frontend/src/routes/alerts/index.tsx` | route/page | CRUD | `frontend/src/routes/index.tsx` | role-match |
| `frontend/src/features/patients/api.ts` | hook | CRUD | `frontend/src/lib/api-client.ts` | partial-match |
| `frontend/src/features/alerts/api.ts` | hook | CRUD + event-driven | `frontend/src/lib/api-client.ts` | partial-match |
| `frontend/src/features/dashboard/api.ts` | hook | request-response | `frontend/src/lib/api-client.ts` | partial-match |
| `frontend/src/components/layout/nav-sidebar.tsx` (modify) | component | event-driven | itself | exact |

---

## Pattern Assignments

### `src/main/java/com/onconavigator/web/PatientController.java` (controller, CRUD)

**Analog:** `src/main/java/com/onconavigator/web/HealthCheckController.java` (structure) + `src/main/java/com/onconavigator/security/SecurityConfig.java` (role names)

**Imports pattern** — follow HealthCheckController's package + add Security/Validation imports:
```java
package com.onconavigator.web;

import com.onconavigator.service.PatientService;
import com.onconavigator.web.dto.CreatePatientRequest;
import com.onconavigator.web.dto.PatientResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
```

**Controller skeleton pattern** — `HealthCheckController.java` lines 31-43 show `@RestController` + `@GetMapping` + `ResponseEntity`. Scale to:
```java
@RestController
@RequestMapping("/api/patients")
public class PatientController {

    private static final Logger log = LoggerFactory.getLogger(PatientController.class);

    private final PatientService patientService;

    public PatientController(PatientService patientService) {
        this.patientService = patientService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public PatientResponse createPatient(
            @Valid @RequestBody CreatePatientRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        return patientService.createPatient(request, actorId);
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<PatientResponse> listPatients(
            @RequestParam(required = false) String mrn) {
        if (mrn != null && !mrn.isBlank()) {
            return patientService.findByMrn(mrn);
        }
        return patientService.findAll();
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public PatientResponse getPatient(@PathVariable UUID id) {
        return patientService.findById(id);
    }

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivatePatient(@PathVariable UUID id,
                                  @Valid @RequestBody DeactivatePatientRequest request,
                                  @AuthenticationPrincipal Jwt jwt) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        patientService.deactivatePatient(id, request, actorId);
    }
}
```

**Auth pattern** — from `SecurityConfig.java` lines 40-43: roles in `@PreAuthorize` must NOT include the `ROLE_` prefix when using `hasRole()`. The existing config uses `hasRole("ADMIN")` (line 78), NOT `hasRole("ROLE_ADMIN")`. Spring Security prepends `ROLE_` automatically.

**Error handling pattern** — use `@ControllerAdvice` GlobalExceptionHandler (new file, no existing analog — see Shared Patterns below). Individual controller methods do not catch exceptions; they throw and the advice handles them.

**Log pattern** — from `PathwayService.java` lines 34, 79: use `log.info("Created patient {} and started pathway monitoring", saved.getId())`. UUID only. Never log request body PHI fields (name, DOB, MRN).

---

### `src/main/java/com/onconavigator/web/CareEventController.java` (controller, CRUD)

**Analog:** `src/main/java/com/onconavigator/web/HealthCheckController.java` (structure) + `CareEvent.java` (entity field names)

**Same pattern as PatientController** with these specifics:
```java
@RestController
@RequestMapping("/api/patients/{patientId}/care-events")
public class CareEventController {

    @PostMapping
    @PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public CareEventResponse addCareEvent(
            @PathVariable UUID patientId,
            @Valid @RequestBody CreateCareEventRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        return patientService.addCareEvent(patientId, request, actorId);
    }

    @PatchMapping("/{careEventId}")
    @PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('ADMIN')")
    public CareEventResponse updateCareEventStatus(
            @PathVariable UUID patientId,
            @PathVariable UUID careEventId,
            @Valid @RequestBody UpdateCareEventStatusRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        return patientService.updateCareEventStatus(patientId, careEventId, request, actorId);
    }
}
```

**Care events list endpoint** (for patient detail page right column):
```java
@GetMapping
@PreAuthorize("isAuthenticated()")
public List<CareEventResponse> listCareEvents(@PathVariable UUID patientId) {
    return patientService.listCareEvents(patientId);
}
```

---

### `src/main/java/com/onconavigator/web/AlertController.java` (controller, CRUD)

**Analog:** `src/main/java/com/onconavigator/web/HealthCheckController.java` + `AlertRepository.java`

**Key endpoint: lightweight count for sidebar badge** (D-11):
```java
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    @GetMapping("/count")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Long> getOpenAlertCount() {
        return Map.of("count", alertService.countOpenAlerts());
    }

    @GetMapping
    @PreAuthorize("hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')")
    public List<AlertResponse> listOpenAlerts() {
        return alertService.getOpenAlerts();
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasRole('NURSE_NAVIGATOR') or hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolveAlert(@PathVariable UUID id,
                             @Valid @RequestBody ResolveAlertRequest request,
                             @AuthenticationPrincipal Jwt jwt) {
        UUID actorId = UUID.fromString(jwt.getSubject());
        alertService.resolveAlert(id, request, actorId);
    }
}
```

---

### `src/main/java/com/onconavigator/web/DashboardController.java` (controller, request-response)

**Analog:** `src/main/java/com/onconavigator/web/HealthCheckController.java`

```java
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    @GetMapping("/stats")
    @PreAuthorize("isAuthenticated()")
    public DashboardStatsResponse getStats() {
        return dashboardService.getStats();
    }
}
```

Stats DTO includes: `openAlertCount`, `activePatients`, `onTrackPatients`, `topUrgentAlerts` (List<AlertResponse>, max 5).

---

### `src/main/java/com/onconavigator/web/dto/*.java` (DTOs, records)

**Analog:** `src/main/java/com/onconavigator/domain/dto/PathwayStep.java` (lines 1-45)

`PathwayStep` is a Java record used as an internal DTO. All Phase 3 web DTOs follow the same record pattern. Key excerpt:

```java
// PathwayStep.java lines 31-44 — the record pattern to copy
public record PathwayStep(
        String stepId,
        int stepNumber,
        String name,
        // ... fields
        List<String> prerequisites
) {
}
```

**Apply to all Phase 3 DTOs:** Use `public record` for all request and response DTOs. Bean Validation annotations go on record components for request DTOs:

```java
// CreatePatientRequest.java
package com.onconavigator.web.dto;

import com.onconavigator.domain.enums.CancerType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public record CreatePatientRequest(
    @NotBlank String firstName,
    @NotBlank String lastName,
    @NotBlank String dateOfBirth,
    @NotBlank String mrn,
    @NotNull CancerType cancerType,
    @NotBlank String cancerStage,
    @NotNull LocalDate diagnosisDate,
    UUID assignedNavigatorId,         // nullable — see Open Question 3 in RESEARCH.md
    String treatingPhysician
) {}
```

```java
// AlertResponse.java — includes decrypted PHI + severity display label
public record AlertResponse(
    UUID id,
    UUID patientId,
    String patientName,        // decrypted server-side — NEVER log this field
    String patientMrn,         // decrypted server-side — NEVER log this field
    String alertType,          // raw enum: "DELAYED_EVENT", "MISSING_EVENT", "OUT_OF_ORDER"
    String severityLabel,      // display label: "OVERDUE", "MISSING", "OUT OF ORDER"
    String status,
    String pathwayStepName,
    String deviationDescription,
    String suggestedAction,
    OffsetDateTime createdAt,
    String timeElapsed         // e.g., "3 days ago" — computed in AlertService
) {}
```

```java
// ResolveAlertRequest.java
public record ResolveAlertRequest(
    @NotBlank(message = "Resolution notes are required") String notes
) {}
```

---

### `src/main/java/com/onconavigator/service/PatientService.java` (service, CRUD)

**Analog:** `src/main/java/com/onconavigator/service/AuditService.java` (service structure, constructor injection, logger) + `src/main/java/com/onconavigator/activity/AlertGenerationActivityImpl.java` (repository save + log pattern)

**Service structure** — from `AuditService.java` lines 34-44:
```java
@Service
public class PatientService {

    private static final Logger log = LoggerFactory.getLogger(PatientService.class);

    private final PatientRepository patientRepository;
    private final CareEventRepository careEventRepository;
    private final PathwayService pathwayService;
    private final HmacTokenService hmacTokenService;

    public PatientService(PatientRepository patientRepository,
                          CareEventRepository careEventRepository,
                          PathwayService pathwayService,
                          HmacTokenService hmacTokenService) {
        this.patientRepository = patientRepository;
        this.careEventRepository = careEventRepository;
        this.pathwayService = pathwayService;
        this.hmacTokenService = hmacTokenService;
    }
```

**createPatient pattern** — modeled on `AlertGenerationActivityImpl.java` lines 59-68 (set fields, save, log UUID only):
```java
public PatientResponse createPatient(CreatePatientRequest req, UUID actorId) {
    Patient patient = new Patient();
    patient.setFirstName(req.firstName());        // EncryptionConverter encrypts at persist
    patient.setLastName(req.lastName());
    patient.setDateOfBirth(req.dateOfBirth());
    patient.setMrn(req.mrn());
    patient.setMrnHmacToken(hmacTokenService.computeMrnToken(req.mrn())); // D-04
    patient.setCancerType(req.cancerType());
    patient.setCancerStage(req.cancerStage());
    patient.setDiagnosisDate(req.diagnosisDate());
    patient.setAssignedNavigatorId(req.assignedNavigatorId());
    patient.setTreatingPhysician(req.treatingPhysician());
    patient.setCreatedBy(actorId);

    Patient saved = patientRepository.save(patient); // PHI encrypted here by EncryptionConverter

    // Trigger pathway enrollment AFTER save (D-06 from Phase 2) — no @Transactional wrapper
    pathwayService.startPathwayMonitoring(saved.getId(), saved.getCancerType());

    log.info("Created patient {} and started pathway monitoring", saved.getId()); // UUID only
    return toPatientResponse(saved);
}
```

**Audit logging** — the `AuditLoggingFilter` already covers all `/api/**` requests (see `SecurityConfig.java` lines 86-89). PatientService does NOT call `AuditService` directly — the filter handles it. Only call `AuditService` if a service method needs its own audit record beyond HTTP-level logging.

**toPatientResponse helper** — decrypt PHI for API response (never return entity directly):
```java
private PatientResponse toPatientResponse(Patient p) {
    return new PatientResponse(
        p.getId(),
        p.getFirstName(),   // already decrypted by EncryptionConverter on load
        p.getLastName(),
        p.getDateOfBirth(),
        p.getMrn(),
        p.getCancerType(),
        p.getCancerStage(),
        p.getDiagnosisDate(),
        p.getAssignedNavigatorId(),
        p.getTreatingPhysician(),
        p.getStatus(),
        p.getCreatedAt()
    );
}
```

---

### `src/main/java/com/onconavigator/service/AlertService.java` (service, CRUD)

**Analog:** `src/main/java/com/onconavigator/service/AuditService.java` (structure) + `src/main/java/com/onconavigator/repository/AlertRepository.java` (query methods)

**Severity label mapping** (Pitfall 6 from RESEARCH.md):
```java
private String toSeverityLabel(AlertType alertType) {
    return switch (alertType) {
        case DELAYED_EVENT -> "OVERDUE";
        case MISSING_EVENT -> "MISSING";
        case OUT_OF_ORDER  -> "OUT OF ORDER";
    };
}
```

**resolveAlert pattern** — from `Alert.java` lines 64-66, 143-154 (resolution fields):
```java
public void resolveAlert(UUID alertId, ResolveAlertRequest req, UUID actorId) {
    Alert alert = alertRepository.findById(alertId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found"));

    alert.setStatus(AlertStatus.RESOLVED);
    alert.setResolvedAt(OffsetDateTime.now());
    alert.setResolvedBy(actorId);
    alert.setResolutionNotes(req.notes());
    alertRepository.save(alert);

    log.info("Alert {} resolved by actor {}", alertId, actorId); // UUID only — no PHI
}
```

**Count method** (for D-11 sidebar badge):
```java
public long countOpenAlerts() {
    return alertRepository.countByStatus(AlertStatus.OPEN);
    // Add to AlertRepository: long countByStatus(AlertStatus status);
}
```

---

### `src/main/java/com/onconavigator/service/PathwayStatusService.java` (service, transform)

**Analog:** `src/main/java/com/onconavigator/activity/PathwayEvaluationActivityImpl.java` lines 1-80 (same pattern: load template, load care events, cross-reference per step)

**Same import block as PathwayEvaluationActivityImpl** (lines 1-36) — shares PatientRepository, CareEventRepository, PathwayTemplateRepository, ObjectMapper, same JSONB deserialization approach.

**Derivation approach** (mirrors PathwayEvaluationActivityImpl logic but read-only — no alert creation):
```java
@Service
public class PathwayStatusService {

    public PathwayStatusResponse getPathwayStatus(UUID patientId) {
        Patient patient = patientRepository.findById(patientId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        PathwayTemplate template = templateRepository.findByCancerType(patient.getCancerType())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No template for cancer type"));

        List<PathwayStep> steps = objectMapper.readValue(...); // same JSONB parse as PathwayEvaluationActivityImpl
        List<CareEvent> events = careEventRepository.findByPatient_IdOrderByEventDateDesc(patientId);
        List<Alert> openAlerts = alertRepository.findByPatientIdAndStatus(patientId, AlertStatus.OPEN);

        // Cross-reference per step — return PathwayStepStatus list
        List<PathwayStepStatus> stepStatuses = steps.stream()
            .map(step -> deriveStepStatus(step, events, openAlerts, patient.getDiagnosisDate()))
            .toList();

        return new PathwayStatusResponse(patientId, stepStatuses);
    }
}
```

---

### `src/main/java/com/onconavigator/security/HmacTokenService.java` (service, transform)

**Analog:** `src/main/java/com/onconavigator/config/EncryptionConfig.java` (lines 21-43) — same pattern: `@Value` Base64 key injection, key length validation, crypto operation.

**Copy pattern from EncryptionConfig.java lines 24-43:**
```java
// EncryptionConfig.java lines 24-43 — key injection pattern to replicate for HMAC
@Value("${onconavigator.encryption.key}")
private String encryptionKeyBase64;

@Bean
public SecretKey phiEncryptionKey() {
    byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
    if (keyBytes.length != 32) {
        throw new IllegalArgumentException(
                "PHI encryption key must be 256 bits (32 bytes). ...");
    }
    return new SecretKeySpec(keyBytes, "AES");
}
```

**HmacTokenService — apply same pattern for HMAC key:**
```java
@Service
public class HmacTokenService {

    private final byte[] hmacKey;

    public HmacTokenService(@Value("${onconavigator.hmac.key}") String hmacKeyBase64) {
        this.hmacKey = Base64.getDecoder().decode(hmacKeyBase64);
        if (this.hmacKey.length != 32) {
            throw new IllegalArgumentException("HMAC key must be 256 bits (32 bytes). "
                + "Generate with: openssl rand -base64 32");
        }
    }

    /**
     * Deterministic HMAC-SHA256 of the MRN. Non-reversible without the key.
     * Same plaintext always produces same token — safe to store and index.
     * @return hex-encoded 64-character string
     */
    public String computeMrnToken(String mrn) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hmacKey, "HmacSHA256"));
            byte[] hash = mac.doFinal(mrn.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC computation failed", e);
        }
    }
}
```

**Required imports:**
```java
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
```

---

### `src/main/resources/db/migration/V8__add_mrn_hmac_token.sql` (migration, batch)

**Analog:** `src/main/resources/db/migration/V7__alert_dedup_index.sql` (lines 1-14) — DDL-only migration with explanatory comment header.

**Copy comment style from V7:**
```sql
-- V8__add_mrn_hmac_token.sql
-- Add deterministic HMAC index token for MRN equality search (D-04).
--
-- The MRN column is AES-GCM encrypted with a random IV, making direct equality
-- queries impossible (same plaintext, different ciphertext on each write).
-- A separate HMAC-SHA256 token column (deterministic, non-reversible) enables
-- exact MRN lookup via a standard B-tree index without exposing the plaintext.
--
-- Note: Existing rows will have NULL mrn_hmac_token. For V1 pilot (no pre-existing
-- production data), this is acceptable. Run a backfill script if test data exists.

ALTER TABLE patients ADD COLUMN mrn_hmac_token VARCHAR(64);

-- B-tree index for equality lookup — supports PatientRepository.findByMrnHmacToken()
CREATE INDEX idx_patients_mrn_hmac_token ON patients(mrn_hmac_token);
```

---

### `frontend/src/routes/index.tsx` (rewrite — DashboardPage)

**Analog:** `frontend/src/routes/index.tsx` (lines 1-42) — this is the file being rewritten. Keep the same `createFileRoute('/')` pattern, replace placeholder content with wired stat cards and alert list.

**Route boilerplate** — from `frontend/src/routes/index.tsx` lines 1-6:
```typescript
import { createFileRoute } from '@tanstack/react-router';

export const Route = createFileRoute('/')({
  component: DashboardPage,
});
```

**Stat card pattern** — from `frontend/src/routes/index.tsx` lines 22-39, the placeholder shows the grid layout to keep:
```tsx
<div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
  {/* Replace placeholder with real Card components */}
</div>
```

**Card component usage** — from `frontend/src/components/ui/card.tsx` lines 5-92: use `Card`, `CardHeader`, `CardTitle`, `CardContent`.

---

### `frontend/src/routes/patients/index.tsx` (patient list)

**Analog:** `frontend/src/routes/index.tsx` (route structure) + `frontend/src/components/layout/nav-sidebar.tsx` (role check pattern)

**Route file pattern** — from `login.tsx` lines 1-4:
```typescript
import { createFileRoute } from '@tanstack/react-router';

export const Route = createFileRoute('/patients/')({
  component: PatientListPage,
});
```

**Role-based conditional render** — from `nav-sidebar.tsx` lines 61-63:
```tsx
const visibleItems = NAV_ITEMS.filter(item =>
  item.roles.length === 0 || item.roles.some(role => hasRole(role)),
);
```

---

### `frontend/src/routes/patients/$patientId.tsx` (patient detail)

**Analog:** `frontend/src/routes/login.tsx` (structure) — the closest route file with a simple component. The dynamic `$patientId` segment follows TanStack Router's file-naming convention.

**Dynamic route + param access** (RESEARCH.md Pattern 7):
```typescript
import { createFileRoute } from '@tanstack/react-router';

export const Route = createFileRoute('/patients/$patientId')({
  component: PatientDetailPage,
});

function PatientDetailPage() {
  const { patientId } = Route.useParams(); // type-safe UUID string
  // ...
}
```

---

### `frontend/src/routes/alerts/index.tsx` (alert queue)

**Analog:** `frontend/src/routes/index.tsx`

```typescript
export const Route = createFileRoute('/alerts/')({
  component: AlertQueuePage,
});
```

---

### `frontend/src/features/patients/api.ts` (TanStack Query hooks, CRUD)

**Analog:** `frontend/src/lib/api-client.ts` (lines 35-50) — all hooks wrap `apiClient.*` calls.

**Query hook pattern** — use `apiClient.get` as the `queryFn`:
```typescript
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';

// List patients
export function usePatients(mrn?: string) {
  return useQuery({
    queryKey: mrn ? ['patients', { mrn }] : ['patients'],
    queryFn: () => apiClient.get<PatientResponse[]>(
      mrn ? `/patients?mrn=${encodeURIComponent(mrn)}` : '/patients'
    ),
    staleTime: 30_000,
  });
}

// Single patient
export function usePatient(patientId: string) {
  return useQuery({
    queryKey: ['patients', patientId],
    queryFn: () => apiClient.get<PatientResponse>(`/patients/${patientId}`),
  });
}

// Create patient mutation
export function useCreatePatient() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (data: CreatePatientRequest) =>
      apiClient.post<PatientResponse>('/patients', data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['patients'] });
    },
  });
}

// Care events for patient
export function useCareEvents(patientId: string) {
  return useQuery({
    queryKey: ['patients', patientId, 'care-events'],
    queryFn: () => apiClient.get<CareEventResponse[]>(`/patients/${patientId}/care-events`),
  });
}

// Pathway status
export function usePathwayStatus(patientId: string) {
  return useQuery({
    queryKey: ['patients', patientId, 'pathway-status'],
    queryFn: () => apiClient.get<PathwayStatusResponse>(`/patients/${patientId}/pathway-status`),
  });
}
```

**QueryClient config** — from `main.tsx` lines 9-16: global `staleTime: 30_000` is set. Individual hooks can override per RESEARCH.md Pattern 5 key hierarchy.

---

### `frontend/src/features/alerts/api.ts` (TanStack Query hooks, CRUD + polling)

**Analog:** `frontend/src/lib/api-client.ts`

**Alert count with polling** — D-12, 30s refetch interval:
```typescript
export function useAlertCount() {
  return useQuery({
    queryKey: ['alerts', 'count'],
    queryFn: () => apiClient.get<{ count: number }>('/alerts/count'),
    refetchInterval: 30_000,  // D-12: live count for sidebar badge
    staleTime: 0,             // always refetch on focus
  });
}

export function useAlerts() {
  return useQuery({
    queryKey: ['alerts'],
    queryFn: () => apiClient.get<AlertResponse[]>('/alerts'),
    refetchInterval: 30_000,  // D-12: dashboard polling
  });
}

export function useResolveAlert() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ alertId, notes }: { alertId: string; notes: string }) =>
      apiClient.post<void>(`/alerts/${alertId}/resolve`, { notes }),
    onSuccess: async () => {
      // Pitfall 8: close dialog state is handled in onSuccess caller, NOT here
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['alerts', 'count'] }),
        queryClient.invalidateQueries({ queryKey: ['alerts'] }),
        queryClient.invalidateQueries({ queryKey: ['dashboard', 'stats'] }),
      ]);
    },
  });
}
```

---

### `frontend/src/features/dashboard/api.ts` (TanStack Query hooks, request-response)

**Analog:** `frontend/src/lib/api-client.ts`

```typescript
export function useDashboardStats() {
  return useQuery({
    queryKey: ['dashboard', 'stats'],
    queryFn: () => apiClient.get<DashboardStatsResponse>('/dashboard/stats'),
    refetchInterval: 30_000,  // D-12
  });
}
```

---

### `frontend/src/components/layout/nav-sidebar.tsx` (modify — add alert badge)

**Analog:** itself — `frontend/src/components/layout/nav-sidebar.tsx` (lines 1-89)

**Badge component** — from `frontend/src/components/ui/badge.tsx` lines 29-46:
```tsx
<Badge variant="destructive" className="ml-auto text-xs">
  {count}
</Badge>
```

**Modification** — in the `NavSidebar` component, import `useAlertCount` and add badge to the Alerts nav item:
```tsx
// Add inside NavSidebar, next to the Bell icon link (line 67-84 pattern):
// For the Alerts item (path === '/alerts'), render the count badge inline

// The Link renders:
<Link key={item.path} to={item.path} ...>
  <Icon size={18} />
  {item.label}
  {item.path === '/alerts' && alertCount > 0 && (
    <Badge variant="destructive" className="ml-auto text-xs tabular-nums">
      {alertCount > 99 ? '99+' : alertCount}
    </Badge>
  )}
</Link>
```

**Import `useAlertCount`** inside the component (not at module scope) to avoid circular dependencies with the `features/alerts/api.ts` module.

---

### Alert-related Frontend Components

#### `frontend/src/features/alerts/AlertCard.tsx` (component, request-response)

**Analog:** `frontend/src/components/ui/card.tsx` + `badge.tsx`

**Severity color mapping** (D-05) using Tailwind classes:
```tsx
const severityConfig = {
  OVERDUE:       { variant: 'destructive' as const, label: 'Overdue'       },
  MISSING:       { variant: 'secondary'   as const, label: 'Missing'       },
  'OUT OF ORDER':{ variant: 'outline'     as const, label: 'Out of Order'  },
};
```

**Card structure** — from `card.tsx` lines 5-92: `Card > CardHeader > CardTitle + CardAction + CardDescription + CardContent + CardFooter`.

**Patient name link pattern** — from `nav-sidebar.tsx` lines 70-83 (Link usage):
```tsx
import { Link } from '@tanstack/react-router';
// ...
<Link to="/patients/$patientId" params={{ patientId: alert.patientId }}>
  {alert.patientName}
</Link>
```

#### `frontend/src/features/alerts/ResolveAlertModal.tsx` (component, CRUD)

**Analog:** `frontend/src/components/ui/dialog.tsx` (lines 1-156)

**Dialog usage pattern** — from `dialog.tsx` lines 8-11:
```tsx
import { Dialog, DialogContent, DialogHeader, DialogTitle,
         DialogDescription, DialogFooter } from '@/components/ui/dialog';

<Dialog open={open} onOpenChange={setOpen}>
  <DialogContent showCloseButton={false} onInteractOutside={(e) => e.preventDefault()}>
    {/* Non-dismissible: prevent clicking outside from closing (A5 in RESEARCH.md) */}
    <DialogHeader>
      <DialogTitle>Resolve Alert</DialogTitle>
    </DialogHeader>
    {/* Textarea (new shadcn component) for notes */}
    <DialogFooter>
      <Button variant="outline" onClick={() => setOpen(false)}>Cancel</Button>
      <Button onClick={handleResolve} disabled={isPending}>
        {isPending ? 'Resolving...' : 'Resolve'}
      </Button>
    </DialogFooter>
  </DialogContent>
</Dialog>
```

**Close on success** (Pitfall 8 from RESEARCH.md): call `setOpen(false)` in `onSuccess` of the `useResolveAlert` mutation caller, before `invalidateQueries`.

#### `frontend/src/features/patients/PatientWizard.tsx` (component, CRUD)

**Analog:** `frontend/src/routes/index.tsx` (component structure) + Zod v4 schema pattern from RESEARCH.md

**Zod v4 schema** — use `{ error: '...' }` not `{ message: '...' }`:
```typescript
import { z } from 'zod';
import { zodResolver } from '@hookform/resolvers/zod';
import { useForm } from 'react-hook-form';

const step1Schema = z.object({
  firstName:   z.string().min(1, { error: 'First name is required.' }),
  lastName:    z.string().min(1, { error: 'Last name is required.' }),
  dateOfBirth: z.string().min(1, { error: 'Date of birth is required.' }),
  mrn:         z.string().min(1, { error: 'MRN is required.' }),
});

const step2Schema = z.object({
  cancerType:   z.string().min(1, { error: 'Cancer type is required.' }),
  cancerStage:  z.string().regex(/^(I|II|III|IV)(A|B|C)?$/, { error: 'Invalid stage format.' }),
  diagnosisDate: z.string().min(1, { error: 'Diagnosis date is required.' }),
  treatingPhysician: z.string().optional(),
});

// Each step uses its own useForm instance:
const step1Form = useForm<z.infer<typeof step1Schema>>({
  resolver: zodResolver(step1Schema),
});
```

**Two-step state** — hold `step` (1 | 2) in local `useState`. Step 1 `handleSubmit` validates then advances `step`. Step 2 `handleSubmit` merges both forms and calls `useCreatePatient` mutation.

**Post-create redirect** (D-02) — from TanStack Router navigation:
```typescript
import { useNavigate } from '@tanstack/react-router';
const navigate = useNavigate();
// In mutation onSuccess:
navigate({ to: '/patients/$patientId', params: { patientId: created.id } });
```

---

## Shared Patterns

### 1. Spring Security RBAC — `@PreAuthorize` Role Names

**Source:** `src/main/java/com/onconavigator/security/SecurityConfig.java` line 78
**Apply to:** All four controller files

```java
// CORRECT — Spring Security prepends ROLE_ automatically:
.requestMatchers("/actuator/auditevents").hasRole("ADMIN")

// CORRECT @PreAuthorize variants:
@PreAuthorize("hasRole('NURSE_NAVIGATOR')")
@PreAuthorize("hasRole('CARE_COORDINATOR') or hasRole('ADMIN')")
@PreAuthorize("isAuthenticated()")

// WRONG — double-prefixes to ROLE_ROLE_NURSE_NAVIGATOR:
@PreAuthorize("hasRole('ROLE_NURSE_NAVIGATOR')")
```

**Role mapping:** Frontend stores `ROLE_NURSE_NAVIGATOR` (with prefix) in `auth.ts` line 66. Backend uses `NURSE_NAVIGATOR` (without prefix) in `@PreAuthorize`. Both are correct for their layer.

### 2. PHI Encryption Pattern — JPA Converter

**Source:** `src/main/java/com/onconavigator/security/EncryptionConverter.java` lines 44-55 + `Patient.java` lines 44-57

**Apply to:** PatientService (`toPatientResponse`), AlertService (`toAlertResponse`), any new entity with PHI fields.

```java
// Patient.java lines 44-57 — how to declare encrypted columns:
@Convert(converter = EncryptionConverter.class)
@Column(name = "first_name_encrypted", columnDefinition = "bytea", nullable = false)
private String firstName;

// After JPA loads the entity, firstName is already decrypted — read it directly:
patient.getFirstName()  // returns plaintext string, not BYTEA
// So toPatientResponse() just reads getters — no manual decryption needed
```

### 3. Audit Logging — Do Not Call AuditService from Controllers/Services

**Source:** `src/main/java/com/onconavigator/security/AuditLoggingFilter.java` lines 53-83 + `SecurityConfig.java` lines 85-89

**Apply to:** All controllers and services — they must NOT call AuditService directly.

```java
// SecurityConfig.java lines 85-89 — filter already covers all /api/** requests:
.addFilterAfter(auditLoggingFilter, BearerTokenAuthenticationFilter.class);

// AuditLoggingFilter.java lines 61-82 — runs after every /api/** request automatically
// Controllers and services: just log UUIDs at info level, let the filter handle HIPAA audit
log.info("Resolved alert {} by actor {}", alertId, actorId);  // OK
log.info("Created patient {}", patient.getFirstName());        // HIPAA VIOLATION
```

### 4. Logger Convention

**Source:** `src/main/java/com/onconavigator/service/PathwayService.java` line 34 + `AuditService.java` line 36 + `AlertGenerationActivityImpl.java` line 30

**Apply to:** All four controllers, three services, HmacTokenService.

```java
// Every class with logging uses:
private static final Logger log = LoggerFactory.getLogger(MyClass.class);
// Import: org.slf4j.Logger, org.slf4j.LoggerFactory
```

### 5. @Audited on New Entities

**Source:** `src/main/java/com/onconavigator/domain/Patient.java` line 37 + `Alert.java` line 32

**Apply to:** No new entities in Phase 3 (Patient, CareEvent, Alert already exist and are already `@Audited`). If a new entity were added, copy the `@Audited` annotation pattern from Patient.java lines 35-38.

### 6. Constructor Injection (No @Autowired)

**Source:** `src/main/java/com/onconavigator/service/AuditService.java` lines 40-42 + `PathwayService.java` lines 44-46

**Apply to:** All controllers and services.

```java
// AuditService.java lines 40-42 — no @Autowired, single-constructor injection:
public AuditService(AuditLogRepository auditLogRepository) {
    this.auditLogRepository = auditLogRepository;
}
```

### 7. TanStack Query apiClient Pattern

**Source:** `frontend/src/lib/api-client.ts` lines 35-50

**Apply to:** All three `api.ts` hook files.

```typescript
// api-client.ts lines 35-50 — the four methods available:
export const apiClient = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body: unknown) => request<T>(path, { method: 'POST', body: JSON.stringify(body) }),
  patch: <T>(path: string, body: unknown) => request<T>(path, { method: 'PATCH', body: JSON.stringify(body) }),
  delete: <T>(path: string) => request<T>(path, { method: 'DELETE' }),
};
// ApiError (lines 5-13) is thrown on non-2xx — TanStack Query catches and surfaces it
```

### 8. Global Exception Handler (No Existing Analog — New Pattern)

**Apply to:** New `src/.../web/GlobalExceptionHandler.java` file — no existing analog in the codebase.

Follow RESEARCH.md V7 requirement (error responses must not leak PHI):
```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
            .body(Map.of("error", ex.getReason() != null ? ex.getReason() : "Request failed"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value"
            ));
        return ResponseEntity.badRequest().body(Map.of("errors", fieldErrors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneric(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage()); // no PHI in message
        return ResponseEntity.internalServerError()
            .body(Map.of("error", "An internal error occurred"));
    }
}
```

### 9. TanStack Router `createFileRoute` + Import Convention

**Source:** `frontend/src/routes/index.tsx` lines 1-6 + `frontend/src/routes/login.tsx` lines 1-4

**Apply to:** All four new route files.

```typescript
// Every route file follows this exact boilerplate:
import { createFileRoute } from '@tanstack/react-router';

export const Route = createFileRoute('/your/path')({
  component: YourComponent,
});
```

After adding route files: run `npm run dev` to regenerate `routeTree.gen.ts` before running `tsc -b`.

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `frontend/src/features/patients/PatientListPage.tsx` | page | CRUD | No existing page with TanStack Table — table-heavy list view has no prior example in this codebase |
| `frontend/src/features/patients/PatientDetailPage.tsx` | page | request-response | No existing split-panel detail page; closest is `index.tsx` but structurally different |
| `frontend/src/features/patients/QuickAddCareEventDialog.tsx` | component | CRUD | No existing dialog-with-form component; Dialog primitive exists but no form-inside-dialog example |
| `frontend/src/features/alerts/AlertQueuePage.tsx` | page | CRUD | No existing filterable card list; closest is `index.tsx` placeholder |
| `src/.../web/GlobalExceptionHandler.java` | middleware | request-response | No existing `@ControllerAdvice` in the codebase — must build from Spring Boot 3 patterns per RESEARCH.md V7 |

For these files: use RESEARCH.md Patterns 1-8 plus the shadcn component primitives from `frontend/src/components/ui/` as the implementation reference.

---

## Metadata

**Analog search scope:**
- Backend: `src/main/java/com/onconavigator/` — all 42 Java source files read where relevant
- Frontend: `frontend/src/` — all 24 TypeScript/TSX files read where relevant
- Migrations: `src/main/resources/db/migration/` — 7 existing migration files

**Files scanned:** 33 source files read in full

**Pattern extraction date:** 2026-04-30

**Critical reminders for planner:**
1. `@PreAuthorize("hasRole('X')")` — never include `ROLE_` prefix (Pitfall 7 in RESEARCH.md)
2. Zod v4: use `{ error: '...' }` not `{ message: '...' }` for validation messages (Pitfall 5)
3. `setOpen(false)` in `useMutation.onSuccess`, not in a separate `useEffect` (Pitfall 8)
4. Run `npm run dev` after adding route files to regenerate `routeTree.gen.ts` (Pitfall 4)
5. `onconavigator.hmac.key` must be a separate config property from `onconavigator.encryption.key` (Pitfall 2)
6. Never log decrypted PHI fields (name, DOB, MRN) — log patient UUID only (all service log patterns)
7. `AlertResponse` must include both `alertType` (raw enum for color logic) and `severityLabel` (display text) — mapping happens in `AlertService`, not frontend (Pitfall 6)
