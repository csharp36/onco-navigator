# Phase 3: Working Application - Research

**Researched:** 2026-04-30
**Domain:** Spring Boot REST API + React dashboard (full-stack CRUD, HMAC search, alert management)
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Patient creation uses a two-step wizard — Step 1: demographics (first name, last name, DOB, MRN). Step 2: clinical info (cancer type, cancer stage, diagnosis date, assigned navigator, treating physician). Validates per-step before advancing.
- **D-02:** After saving a new patient, redirect to the patient detail page.
- **D-03:** Care event recording has two entry points — a quick-add dialog from the patient list AND a full "Add Care Event" button on the patient detail page. Same backend endpoint, two frontend paths.
- **D-04:** Patient search uses HMAC index token for MRN lookup — a deterministic HMAC-SHA256 of MRN stored as `mrn_hmac_token` column alongside encrypted MRN. Exact MRN search hits the HMAC column. Name search uses in-memory decryption and client-side filtering.
- **D-05:** Alert queue uses card layout with filter bar, grouped by severity level (OVERDUE first, MISSING second, OUT_OF_ORDER third).
- **D-06:** Alert resolution uses a modal dialog with required resolution notes textarea. On resolve, alert disappears from queue via TanStack Query invalidation.
- **D-07:** Patient name on alert cards is a clickable link to the patient detail page.
- **D-08:** Patient pathway visualization uses a vertical stepped list.
- **D-09:** Patient detail page uses a side-by-side split layout, stacking vertically on tablet/mobile.
- **D-10:** Dashboard is alert-focused — three summary stat cards at top, followed by top ~5 most urgent alerts.
- **D-11:** Persistent open alert count displayed as a sidebar nav badge on the "Alerts" nav item.
- **D-12:** Dashboard stats and alert count use 30-second polling via TanStack Query `refetchInterval: 30_000`.

### Claude's Discretion

- REST API controller structure and endpoint naming conventions
- DTO design for request/response objects (records vs classes)
- Bean validation annotations and error response format
- TanStack Query key structure and cache invalidation strategy
- Whether patient list uses pagination or loads all (pilot scale decision)
- Zod schema design for frontend form validation
- HMAC key management (same AES key or separate HMAC key)
- How pathway auto-enrollment (D-06 from Phase 2) is triggered from the patient creation endpoint

### Deferred Ideas (OUT OF SCOPE)

- Pathway template admin UI (editing pathways through the dashboard) — V2 requirement ADV-01
- Manual re-scan trigger (F3-07 from feature spec)
- Bulk patient import
</user_constraints>

---

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| DATA-01 | Care coordinator can add a new patient with name, DOB, MRN, diagnosis, navigator, physician | Two-step wizard (D-01), POST /api/patients, Flyway V8 migration for mrn_hmac_token column |
| DATA-02 | Care coordinator can add a care event with event type, date, status, notes | POST /api/patients/{id}/care-events, react-hook-form + Zod v4 dialog form |
| DATA-03 | Care coordinator can update the status of an existing care event | PATCH /api/patients/{id}/care-events/{eventId}, signal PathwayService on update |
| DATA-04 | Care coordinator can deactivate a patient record | PATCH /api/patients/{id}/deactivate, PathwayService.deactivatePatient() signal |
| DATA-05 | All data entry actions are logged with staff identity and timestamp | AuditLoggingFilter already covers /api/**, Hibernate Envers @Audited on all ePHI entities |
| ALRT-01 | Dashboard displays all open alerts sorted by severity | GET /api/alerts with severity ordering in AlertRepository query |
| ALRT-02 | Each alert shows patient name, MRN, type, step, description, suggested action, time elapsed | AlertResponse DTO with denormalized patient fields (name decrypted server-side) |
| ALRT-03 | Nurse can view patient's full pathway status | GET /api/patients/{id}/pathway-status, PathwayTemplate JSONB + CareEvent cross-reference |
| ALRT-04 | Nurse can mark alert as Resolved with free-text note | POST /api/alerts/{id}/resolve, AlertStatus.RESOLVED, modal with required notes |
| ALRT-05 | Dashboard shows count of open alerts, always visible | GET /api/alerts/count lightweight endpoint, sidebar badge with 30s polling |
| ALRT-06 | Dashboard shows all patients with pathway and summary status | GET /api/patients includes summary status derived from open alerts |
</phase_requirements>

---

## Summary

Phase 3 is the integration layer: it wires the pathway engine (Phase 2) to the frontend dashboard (Phase 1 scaffold) through REST controllers and React pages. The backend work centers on five Spring MVC controllers, a Flyway migration adding the HMAC index token to the patients table, and a PatientService that orchestrates persistence + Temporal lifecycle. The frontend work is the largest piece — four new routes, six new components/pages, five shadcn component additions, and the alert count badge wired into the nav sidebar.

The critical technical constraint unique to this phase is the HMAC MRN search (D-04). The existing `findByMrn` stub in PatientRepository cannot work with AES-GCM because each encryption uses a random IV — ciphertexts are never equal for the same plaintext. The solution is a separate HMAC-SHA256 token column: deterministic (same input → same output), non-reversible without the key, and searchable via a standard B-tree index. The HMAC key should be separate from the AES key (defense-in-depth), stored in the same `onconavigator.encryption.*` config namespace.

The frontend uses libraries already installed and confirmed in package.json (Zod 4.4.1, @hookform/resolvers 5.2.2, TanStack Query 5.100.6, TanStack Router 1.168.26, react-hook-form 7.74.0, date-fns 4.1.0). Zod 4 has breaking API changes from Zod 3 that must be followed exactly — `z.string().email()` is deprecated in favor of `z.email()`, and error messages use `{ error: "..." }` not `{ message: "..." }`. The @hookform/resolvers v5 handles both Zod 3 and Zod 4 transparently via duck-typing.

**Primary recommendation:** Build in three backend waves (Flyway+PatientService, Controllers, AlertService) then two frontend waves (routes+query hooks, components). The alert count badge endpoint is lightweight and should be built in the first controller wave so frontend polling works immediately.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Patient CRUD (create, deactivate) | API / Backend | Database | Business logic (Temporal enrollment trigger, HMAC token generation) belongs in the API layer; DB stores encrypted PHI |
| Care event record and update | API / Backend | Database | Must signal Temporal workflow after persist — business logic owned by API |
| Alert resolution | API / Backend | Database | Updates alert status + resolvedBy + notes, triggers TanStack Query invalidation on frontend |
| Alert severity ordering | API / Backend | — | Sort by alert_type in JPQL: OVERDUE/DELAYED first, then MISSING, then OUT_OF_ORDER — avoids sending unsorted data to frontend |
| Patient name in alert response | API / Backend | — | Decrypt PHI server-side, return plaintext name in response DTO; never expose encrypted BYTEA to frontend |
| HMAC MRN token computation | API / Backend | Database | HMAC computed in PatientService before persist; stored as VARCHAR column; indexed for equality lookup |
| Pathway status derivation | API / Backend | — | Cross-reference PathwayTemplate JSONB steps against CareEvents in service layer; return structured DTO |
| Patient list filtering / search | API / Backend | Frontend | Exact MRN search via HMAC index (server-side). Name search: load all patients server-side, filter in-memory (acceptable at <500 pilot scale) |
| Alert count badge (polling) | Frontend Browser | API | TanStack Query refetchInterval:30_000 drives polling; lightweight GET /api/alerts/count endpoint |
| Two-step wizard state | Frontend Browser | — | react-hook-form multi-step state held in browser memory; no server round-trip between steps |
| Optimistic alert removal | Frontend Browser | — | TanStack Query invalidateQueries after successful resolve mutation; no optimistic update needed (modal blocks UI) |
| Responsive layout (SEC-07) | Frontend Browser | — | Tailwind v4 responsive classes; side-by-side stacks at <lg breakpoint |

---

## Standard Stack

### Core (already installed — confirmed from package.json and pom.xml)

#### Backend
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 3.5.0 | Application framework + BOM | Already in pom.xml [VERIFIED: pom.xml] |
| Spring MVC | via Boot BOM | REST controllers | Already in spring-boot-starter-web [VERIFIED: pom.xml] |
| Spring Security 6 | via Boot BOM | JWT RBAC on all controllers | SecurityConfig already configured [VERIFIED: SecurityConfig.java] |
| spring-boot-starter-validation | via Boot BOM | Bean validation @Valid on request bodies | Already in pom.xml [VERIFIED: pom.xml] |
| Hibernate Envers | via Boot BOM | @Audited revision tables for ePHI | Already configured on Patient, Alert, CareEvent [VERIFIED: domain entities] |
| Flyway | via Boot BOM | V8 migration for mrn_hmac_token column | V7 is current latest migration [VERIFIED: db/migration/] |
| Java HMAC-SHA256 | JDK built-in | MRN index token computation | `javax.crypto.Mac` with "HmacSHA256" — no external library needed [VERIFIED: JDK standard library] |

#### Frontend
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| TanStack Query | 5.100.6 | Server state, caching, polling | Already installed [VERIFIED: package.json] |
| TanStack Router | 1.168.26 | File-based routing, dynamic `$id` routes | Already installed [VERIFIED: package.json] |
| TanStack Table | 8.21.3 | Patient list table with sort/filter | Already installed [VERIFIED: package.json] |
| react-hook-form | 7.74.0 | Wizard and dialog form state | Already installed [VERIFIED: package.json] |
| Zod | 4.4.1 | Schema validation — Zod v4 API | Already installed [VERIFIED: node_modules] |
| @hookform/resolvers | 5.2.2 | Bridge RHF + Zod v4 | Already installed, v5 handles Zod v4 via duck-typing [VERIFIED: node_modules zod.ts source] |
| date-fns | 4.1.0 | Time elapsed calculations for alert age | Already installed [VERIFIED: package.json] |
| shadcn/ui | new-york / neutral | Component primitives | Already initialized, 11 components present [VERIFIED: components.json, /components/ui/] |
| lucide-react | 1.14.0 | Icons (CheckCircle2, Clock, AlertTriangle, Circle, Bell) | Already installed [VERIFIED: package.json] |

### Components to Add via shadcn CLI

The UI-SPEC.md identifies 8 additional shadcn components not yet installed:

```bash
cd frontend
npx shadcn add select textarea label form table tabs progress popover
```

All are from the official shadcn registry — no third-party registries needed. [VERIFIED: 03-UI-SPEC.md Registry Safety section]

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Java HMAC-SHA256 (javax.crypto.Mac) | pgcrypto HMAC in PostgreSQL | Application-layer HMAC keeps key management in one place (EncryptionConfig); pgcrypto would require key injection into PostgreSQL, adding operational complexity |
| HMAC separate key | Same AES key for HMAC | Using a separate HMAC key follows key separation principle — compromise of one key does not expose both encryption and search index |
| In-memory name search | PostgreSQL full-text search on decrypted view | At pilot scale (<500 patients), load-all-decrypt-filter is fast enough; avoids complex pg_trgm/FTS setup on encrypted data |
| Java records for DTOs | POJO classes | Records are immutable and concise — use records for response DTOs (read-only). Use regular classes for request DTOs if setters are needed by frameworks, but Jackson deserializes into records fine with `@JsonCreator` or all-args constructor |

---

## Architecture Patterns

### System Architecture Diagram

```
Browser (Nurse/Coordinator)
        |
        | JWT Bearer token (Keycloak)
        v
[React SPA — TanStack Router routes]
    /                                   /patients         /patients/$id     /alerts
    DashboardPage                       PatientListPage   PatientDetailPage AlertQueuePage
    - stat cards (useQuery)             - Table           - Demographics    - Alert cards
    - top-5 alerts (useQuery)           - search bar      - PathwayStatus   - FilterBar
    - badge count (refetchInterval)     - QuickAddDialog  - CareEventList   - ResolveModal
          |                                   |                 |                |
          | apiClient (fetch + JWT)            |                 |                |
          v                                   v                 v                v
[Spring Boot REST API — /api/**]
    GET /api/dashboard/stats            GET/POST /api/patients  GET /api/patients/{id}/pathway-status
    GET /api/alerts?status=OPEN         PATCH /api/patients/{id}/deactivate
    GET /api/alerts/count               POST /api/patients/{id}/care-events
    GET /api/alerts/{id}                PATCH /api/patients/{id}/care-events/{ceId}
    POST /api/alerts/{id}/resolve
          |
          | @PreAuthorize (RBAC)
          v
[Spring Services]
    PatientService          AlertService            PathwayStatusService
    - createPatient()       - getOpenAlerts()       - getPathwayStatus()
    - computeHmacToken()    - resolveAlert()        - crossRef(template,events)
    - deactivatePatient()   - getAlertCount()
          |                       |
          v                       v
[PathwayService (existing)]   [Repositories (existing)]
    startPathwayMonitoring()      PatientRepository (+ findByMrnHmacToken)
    signalCareEventChanged()      AlertRepository (findByStatusOrderByCreatedAtDesc)
    deactivatePatient()           CareEventRepository
          |
          v
[Temporal Worker — already running from Phase 2]
    PatientPathwayWorkflowImpl → evaluates deviations → creates Alert records
          |
          v
[PostgreSQL 16]
    patients (+ mrn_hmac_token VARCHAR(64) after V8 migration)
    care_events
    alerts (partial UNIQUE index on OPEN per patient+step — V7)
    pathway_templates (JSONB seed data from Phase 2)
```

### Recommended Project Structure — New Files

```
src/main/java/com/onconavigator/
├── web/
│   ├── PatientController.java           # CRUD + deactivate
│   ├── CareEventController.java         # add + update status
│   ├── AlertController.java             # list + count + resolve
│   ├── DashboardController.java         # stats + top-5 urgent alerts
│   └── dto/
│       ├── CreatePatientRequest.java     # record — validated input
│       ├── CreateCareEventRequest.java   # record
│       ├── UpdateCareEventStatusRequest  # record (status field only)
│       ├── DeactivatePatientRequest.java # record (reason enum)
│       ├── ResolveAlertRequest.java      # record (notes string)
│       ├── PatientResponse.java          # record — includes decrypted name, summaryStatus
│       ├── CareEventResponse.java        # record
│       ├── AlertResponse.java            # record — includes patientName (decrypted)
│       ├── DashboardStatsResponse.java   # record
│       └── PathwayStatusResponse.java    # record — list of step statuses
├── service/
│   ├── PatientService.java              # orchestrates persist + Temporal + HMAC
│   ├── AlertService.java                # alert queries + resolution
│   └── PathwayStatusService.java        # cross-reference template steps vs events
└── security/
    └── HmacTokenService.java            # HMAC-SHA256 token computation

src/main/resources/db/migration/
└── V8__add_mrn_hmac_token.sql           # ALTER TABLE + index + backfill note

frontend/src/
├── routes/
│   ├── patients/
│   │   ├── index.tsx                    # /patients — patient list
│   │   ├── new.tsx                      # /patients/new — wizard (or modal)
│   │   └── $patientId.tsx              # /patients/$patientId — detail page
│   └── alerts/
│       └── index.tsx                    # /alerts — alert queue
├── features/
│   ├── patients/
│   │   ├── api.ts                       # useQuery/useMutation hooks
│   │   ├── PatientListPage.tsx
│   │   ├── PatientDetailPage.tsx
│   │   ├── PatientWizard.tsx            # two-step form
│   │   └── QuickAddCareEventDialog.tsx
│   └── alerts/
│       ├── api.ts                       # useQuery/useMutation hooks
│       ├── AlertQueuePage.tsx
│       ├── AlertCard.tsx
│       └── ResolveAlertModal.tsx
└── features/dashboard/
    ├── api.ts
    └── DashboardPage.tsx
```

### Pattern 1: Spring MVC REST Controller with RBAC

**What:** Standard Spring MVC controller slice — `@RestController`, `@PreAuthorize`, `@Valid` on request body, records as DTOs.

**When to use:** All five controllers in this phase.

```java
// Source: Spring Boot 3.5 + Spring Security 6 established patterns
@RestController
@RequestMapping("/api/patients")
public class PatientController {

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
}
```

**Key notes:**
- Use `@AuthenticationPrincipal Jwt jwt` to extract actor UUID from JWT subject claim for `createdBy` fields
- Role names in `@PreAuthorize` must match Keycloak realm roles WITHOUT the `ROLE_` prefix when using `hasRole()` — Spring Security prepends it automatically
- `@Valid` triggers Bean Validation on the request body before the controller method runs

### Pattern 2: HMAC-SHA256 MRN Token

**What:** Deterministic, non-reversible token computed from MRN plaintext. Same plaintext always produces same token. Stored as VARCHAR(64) (hex-encoded 32-byte digest), indexed for B-tree equality lookup.

**When to use:** Patient create (compute and store), patient search by MRN (compute and query).

```java
// Source: JDK javax.crypto.Mac [VERIFIED: JDK standard API]
@Service
public class HmacTokenService {

    private final byte[] hmacKey;

    public HmacTokenService(@Value("${onconavigator.hmac.key}") String hmacKeyBase64) {
        this.hmacKey = Base64.getDecoder().decode(hmacKeyBase64);
        if (this.hmacKey.length != 32) {
            throw new IllegalArgumentException("HMAC key must be 256 bits (32 bytes)");
        }
    }

    /**
     * Computes deterministic HMAC-SHA256 of the MRN.
     * Safe to store: non-reversible without the key.
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

**Flyway V8 migration:**

```sql
-- V8__add_mrn_hmac_token.sql
ALTER TABLE patients ADD COLUMN mrn_hmac_token VARCHAR(64);

-- Index for deterministic MRN equality search (D-04)
CREATE INDEX idx_patients_mrn_hmac_token ON patients(mrn_hmac_token);

-- Note: Existing rows will have NULL mrn_hmac_token.
-- Phase 3 PatientService backfills on first access if needed.
-- For V1 pilot (no existing patients), NULL rows are not a concern.
```

**HMAC key configuration in application-local.yml:**

```yaml
onconavigator:
  encryption:
    key: HqmsJAlCUqgAmHAsahP7Y4as/V5ChavBN2horjFffpw=  # existing AES key
  hmac:
    key: <separate-32-byte-base64-key>  # generate: openssl rand -base64 32
```

**PatientRepository addition:**

```java
// In PatientRepository.java
Optional<Patient> findByMrnHmacToken(String mrnHmacToken);
```

### Pattern 3: Alert Severity Ordering via JPQL CASE

**What:** Alerts must be sorted OVERDUE > MISSING > OUT_OF_ORDER (ALRT-01). AlertType enum has three values: MISSING_EVENT, DELAYED_EVENT, OUT_OF_ORDER. The "OVERDUE" severity in the UI maps to DELAYED_EVENT in the domain model.

**When to use:** AlertRepository query for the open alert list.

```java
// In AlertRepository.java
@Query("""
    SELECT a FROM Alert a
    WHERE a.status = :status
    ORDER BY
        CASE a.alertType
            WHEN 'DELAYED_EVENT' THEN 1
            WHEN 'MISSING_EVENT' THEN 2
            WHEN 'OUT_OF_ORDER'  THEN 3
            ELSE 4
        END ASC,
        a.createdAt ASC
    """)
List<Alert> findByStatusOrderedBySeverity(@Param("status") AlertStatus status);
```

**Note:** The AlertResponse DTO that the frontend receives should include a `severity` field mapping AlertType to the display label used by the UI: `DELAYED_EVENT → "OVERDUE"`, `MISSING_EVENT → "MISSING"`, `OUT_OF_ORDER → "OUT_OF_ORDER"`. This mapping belongs in PatientService or AlertService, not in the frontend.

### Pattern 4: Alert Response DTO with Decrypted Patient PHI

**What:** The frontend needs patient name and MRN to display on alert cards (ALRT-02). These are encrypted in the database. The controller must decrypt them server-side and include them in the response DTO — never expose BYTEA to the frontend.

**When to use:** AlertController list and detail endpoints.

```java
// AlertResponse.java — Java record
public record AlertResponse(
    UUID id,
    UUID patientId,
    String patientName,        // decrypted: "Jane Smith"
    String patientMrn,         // decrypted: "MRN-12345"
    String alertType,          // "OVERDUE", "MISSING", "OUT_OF_ORDER" (display label)
    String status,
    String pathwayStepName,
    String deviationDescription,
    String suggestedAction,
    OffsetDateTime createdAt,
    String timeElapsed         // "3 days ago" — computed in service
) {}
```

**HIPAA note:** Decrypted patient name and MRN are transmitted over TLS from the API to the browser. This is acceptable because the browser connection is authenticated (JWT required) and TLS is enforced. Never log the `patientName` or `patientMrn` fields.

### Pattern 5: TanStack Query Key Hierarchy

**What:** Consistent query key structure enables targeted cache invalidation without over-invalidating.

**When to use:** All frontend API hooks.

```typescript
// Recommended key structure — [resource, scope, id]
['patients']                          // all patients list
['patients', patientId]               // single patient detail
['patients', patientId, 'care-events'] // care events for patient
['patients', patientId, 'pathway-status'] // pathway steps status
['alerts']                            // all open alerts
['alerts', alertId]                   // single alert
['alerts', 'count']                   // open alert count (sidebar badge)
['dashboard', 'stats']                // dashboard stat cards

// After resolve alert mutation:
queryClient.invalidateQueries({ queryKey: ['alerts'] })
queryClient.invalidateQueries({ queryKey: ['alerts', 'count'] })
queryClient.invalidateQueries({ queryKey: ['dashboard', 'stats'] })
```

### Pattern 6: Zod v4 Schema (Breaking Changes from v3)

**What:** Zod 4.4.1 is installed. Several API changes from Zod 3 must be followed.

**When to use:** All frontend form validation schemas.

```typescript
// Source: https://zod.dev/v4/changelog [VERIFIED: node_modules/zod]

// Zod v4: top-level format validators (z.string().email() is DEPRECATED)
import { z } from 'zod';

// Patient Step 1 schema
const patientStep1Schema = z.object({
  firstName: z.string().min(1, { error: 'First name is required.' }),
  lastName: z.string().min(1, { error: 'Last name is required.' }),
  dateOfBirth: z.string().min(1, { error: 'Date of birth is required.' }),
  mrn: z.string().min(1, { error: 'MRN is required.' }),
});

// Error messages use { error: '...' } not { message: '...' } in Zod v4
// z.string().min(1, { message: '...' }) still works in 4.x but emits deprecation warning
```

### Pattern 7: TanStack Router Dynamic Routes

**What:** Patient detail page needs a dynamic route `/patients/$patientId`.

**When to use:** New routes for patient detail and any ID-based navigation.

```typescript
// Source: TanStack Router docs [CITED: github.com/tanstack/router]
// File: frontend/src/routes/patients/$patientId.tsx
import { createFileRoute } from '@tanstack/react-router';

export const Route = createFileRoute('/patients/$patientId')({
  component: PatientDetailPage,
});

function PatientDetailPage() {
  const { patientId } = Route.useParams();
  // patientId is type-safe UUID string
}
```

**File-based routing convention:** TanStack Router auto-generates `routeTree.gen.ts` from the file structure. New route files must follow the `$paramName` convention for dynamic segments. After adding route files, run `vite build` or `npm run dev` to regenerate `routeTree.gen.ts`.

### Pattern 8: Pathway Status Derivation

**What:** ALRT-03 requires showing all pathway steps with their current status. The PathwayTemplate JSONB contains step definitions; CareEvents contain what actually happened. The service cross-references them.

**When to use:** `PathwayStatusService.getPathwayStatus(patientId)`

```java
// PathwayStatusResponse — per-step status
public record PathwayStepStatus(
    String stepId,
    int stepNumber,
    String stepName,
    String status,            // "COMPLETED", "OVERDUE", "MISSING", "UPCOMING"
    LocalDate completionDate, // null if not completed
    String timingInfo,        // "Completed Day 8 of 14-day window" or "6 days overdue"
    boolean hasActiveAlert
) {}
```

The derivation logic:
1. Load PathwayTemplate for patient's cancer type
2. Load all CareEvents for patient
3. For each pathway step: find matching completed CareEvent by eventType
4. If completed: status = COMPLETED, compute timing relative to window
5. If no completed event and window expired: status = OVERDUE or MISSING
6. If no completed event and window not expired: status = UPCOMING
7. If has open Alert for this step: set `hasActiveAlert = true`

### Anti-Patterns to Avoid

- **Calling PathwayService.signalCareEventChanged() inside a JPA @Transactional boundary:** If the DB transaction rolls back, the Temporal signal has already been sent. The signal is idempotent (workflow re-evaluates regardless), but the timing is misleading. Call PathwayService AFTER the transaction commits. Use `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` or structure the service to call Temporal after `repository.save()` returns without a surrounding transaction on the service method. Alternatively: accept the minor inconsistency — for a pilot, it's not a correctness problem because the next daily sweep will correct any transient state.
- **Returning Patient entity directly from controller:** Patient has BYTEA fields that serialize as base64 blobs to the frontend. Always return a DTO with decrypted string fields.
- **Using `z.string().email()` in Zod v4:** Deprecated. Use `z.email()` at the top level. Wrong usage will produce TypeScript deprecation warnings and may not produce correct error messages.
- **Logging decrypted patient name in service layer:** Immediate HIPAA violation. Log only patient UUID and operation type.
- **Sending all care events for a patient in the pathway status endpoint:** The pathway status endpoint (ALRT-03) should return step statuses derived from events, not a raw dump of all care events. The care events list (for the patient detail right column) is a separate endpoint.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| HMAC-SHA256 computation | Custom hash function | `javax.crypto.Mac` with "HmacSHA256" | JDK standard, FIPS-compliant, no external dependency |
| Form validation with error display | Custom validation logic | Zod v4 schema + @hookform/resolvers v5 + react-hook-form | Already installed; handles async validation, field-level errors, type inference |
| Optimistic UI for alert removal | Manual DOM manipulation | TanStack Query `invalidateQueries` after mutation | Automatic refetch provides correct state; optimistic update adds complexity without benefit here |
| Alert severity sort | Frontend sort logic | JPQL ORDER BY CASE in AlertRepository | Server-side sort is authoritative; frontend doesn't need to re-sort |
| Patient table with sort/filter | Custom HTML table | @tanstack/react-table v8 | Already installed; handles column sorting, filtering, virtual scrolling |
| API client boilerplate | Per-endpoint fetch calls | `apiClient` wrapper in `lib/api-client.ts` | Already exists with JWT injection, error handling, and 204 handling |
| shadcn component code | Manual Radix UI wrapping | `npx shadcn add <component>` | Official CLI installs into `/components/ui/` with correct Tailwind v4 token usage |

---

## Common Pitfalls

### Pitfall 1: `@TransactionalEventListener` Complexity for Temporal Signals

**What goes wrong:** Developers try to use Spring's `@TransactionalEventListener` to ensure Temporal signals only fire after DB commit. This adds event publishing complexity and can cause signals to be dropped if the ApplicationEvent is not published correctly.

**Why it happens:** The concern is valid — if the DB transaction rolls back, the Temporal signal has already been sent. But Temporal workflows handle re-evaluation gracefully (they fetch from DB on next evaluation anyway).

**How to avoid:** For Phase 3 pilot, call `PathwayService.signalCareEventChanged()` directly after `repository.save()` without wrapping the service method in `@Transactional`. The repository save will auto-commit. If a subsequent error occurs, the Temporal signal is benign — the workflow will re-evaluate with the correctly committed or rolled-back data on next evaluation. The simpler approach is correct for V1. [ASSUMED — this design decision is acceptable for pilot scale but should be reviewed if production SLA requires strict consistency]

**Warning signs:** If you see `@TransactionalEventListener` being added, stop and verify the complexity is justified.

### Pitfall 2: HMAC Key Not Separate from AES Key

**What goes wrong:** Using the same `onconavigator.encryption.key` for both AES-GCM encryption and HMAC-SHA256 index token. Cryptographically acceptable but violates key separation.

**Why it happens:** There's only one configured key today, and using it for HMAC saves configuration changes.

**How to avoid:** Add `onconavigator.hmac.key` as a separate Base64 32-byte value in `application-local.yml` and the AWS Secrets Manager config. Generate with `openssl rand -base64 32`. The `HmacTokenService` reads from this dedicated property. [ASSUMED — HIPAA doesn't explicitly require key separation here, but it is standard cryptographic practice and aligns with the project's "build it right" philosophy]

**Warning signs:** `HmacTokenService` injecting `@Value("${onconavigator.encryption.key}")` instead of `${onconavigator.hmac.key}`.

### Pitfall 3: mrn_hmac_token NULL for Existing Patients

**What goes wrong:** The V8 Flyway migration adds the column but existing patients (created before Phase 3) will have NULL `mrn_hmac_token`. MRN search returns no results for those patients.

**Why it happens:** Phase 2 test data may have created Patient rows. The V8 migration doesn't backfill.

**How to avoid:** For V1 pilot, document that patient records created before Phase 3 go live will need to be re-entered (or a backfill script run). The V8 migration comment should note this. Alternatively, a `@PostConstruct` or admin endpoint can backfill NULL tokens on startup. [ASSUMED — the migration strategy for pre-existing data is undocumented; confirm with user whether test data patients need backfilling]

**Warning signs:** `findByMrnHmacToken` returning empty even for MRNs that are known to exist.

### Pitfall 4: TanStack Router routeTree.gen.ts Not Regenerated

**What goes wrong:** New route files are created but `routeTree.gen.ts` is not regenerated. TypeScript compilation fails with "Cannot find module" for the new route.

**Why it happens:** `routeTree.gen.ts` is auto-generated by the `@tanstack/router-plugin` Vite plugin at dev server start or build time. If the developer edits route files and runs `tsc -b` directly without starting the dev server first, the gen file is stale.

**How to avoid:** Always run `npm run dev` (starts Vite + router plugin) before running `tsc -b` independently. In the plan, include an explicit task step: "Run `npm run dev` and confirm `routeTree.gen.ts` is updated" after adding new route files. This was already noted in STATE.md as a Phase 1 pattern. [VERIFIED: STATE.md [01-04] decision]

**Warning signs:** TypeScript errors mentioning `routeTree.gen.ts` or route type inference failures.

### Pitfall 5: Zod v4 `error` vs `message` in Validation Messages

**What goes wrong:** Using `{ message: "..." }` for validation error messages instead of `{ error: "..." }`. The schema parses but emits deprecation warnings and the error may not surface correctly with @hookform/resolvers v5.

**Why it happens:** All Zod v3 examples (Stack Overflow, blog posts) use `message`. Zod v4 changed this.

**How to avoid:** Always use `{ error: "..." }` in validation constraints. The UI-SPEC.md copywriting contract specifies exact validation messages — follow those. [VERIFIED: node_modules/zod via Context7 /websites/zod_dev_v4]

### Pitfall 6: Alert Severity Display Label Mismatch

**What goes wrong:** The backend AlertType enum has `DELAYED_EVENT`, `MISSING_EVENT`, `OUT_OF_ORDER` but the UI requires `"OVERDUE"`, `"MISSING"`, `"OUT OF ORDER"` as display labels (from REQUIREMENTS.md ALRT-01 and UI-SPEC.md copywriting contract). If the backend sends `DELAYED_EVENT` as the severity string and the frontend displays it directly, the UI shows wrong text.

**Why it happens:** The enum names were chosen for technical precision; the UI labels use clinical language.

**How to avoid:** The AlertResponse DTO includes a `severity` field with the display label computed in `AlertService`. The mapping is: `DELAYED_EVENT → "OVERDUE"`, `MISSING_EVENT → "MISSING"`, `OUT_OF_ORDER → "OUT OF ORDER"`. Also include the raw `alertType` enum value if the frontend needs it for color logic. [VERIFIED: REQUIREMENTS.md, UI-SPEC.md]

### Pitfall 7: @PreAuthorize Role Names

**What goes wrong:** Using `hasRole('ROLE_NURSE_NAVIGATOR')` in `@PreAuthorize` annotations. Spring Security automatically prepends `ROLE_` when using `hasRole()`, so the effective check becomes `ROLE_ROLE_NURSE_NAVIGATOR` which never matches.

**Why it happens:** The frontend `auth.ts` stores roles with the `ROLE_` prefix (e.g., `ROLE_NURSE_NAVIGATOR`) which creates confusion about the correct syntax.

**How to avoid:** Use `hasRole('NURSE_NAVIGATOR')` — without the `ROLE_` prefix — in all `@PreAuthorize` annotations. Alternatively use `hasAuthority('ROLE_NURSE_NAVIGATOR')` which does NOT prepend. The existing `SecurityConfigTest` demonstrates the correct pattern using `jwt()` post-processor. [VERIFIED: SecurityConfig.java — uses hasRole("ADMIN") for /actuator/auditevents]

### Pitfall 8: Dialog Not Closing on Successful Mutation (React State Leak)

**What goes wrong:** The ResolveAlertModal and QuickAddCareEventDialog manage their `open` state in the parent component. After a successful mutation, the developer forgets to call `setOpen(false)`. The dialog stays open.

**Why it happens:** TanStack Query `onSuccess` callback runs asynchronously; if the `setOpen` call is in a separate `useEffect` watching query state, timing issues arise.

**How to avoid:** Put `setOpen(false)` directly in the `useMutation` `onSuccess` callback, before `queryClient.invalidateQueries`. This is the authoritative close trigger — not a `useEffect`. [ASSUMED — standard React pattern, but easy to get wrong]

---

## Code Examples

### Backend: PatientService.createPatient skeleton

```java
// Established project patterns: @Service, @Transactional, PathwayService injection
@Service
public class PatientService {

    private final PatientRepository patientRepository;
    private final PathwayService pathwayService;
    private final HmacTokenService hmacTokenService;

    // ... constructor injection

    public PatientResponse createPatient(CreatePatientRequest req, UUID actorId) {
        Patient patient = new Patient();
        patient.setFirstName(req.firstName());        // EncryptionConverter handles at persist
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

        Patient saved = patientRepository.save(patient); // PHI encrypted here

        // D-06 from Phase 2 Context: automatic pathway enrollment on patient creation
        pathwayService.startPathwayMonitoring(saved.getId(), saved.getCancerType());

        log.info("Created patient {} and started pathway monitoring", saved.getId()); // UUID only
        return toResponse(saved);
    }
}
```

### Frontend: Alert count badge hook with polling

```typescript
// Source: TanStack Query v5 refetchInterval [CITED: tanstack.com/query/v5]
// frontend/src/features/alerts/api.ts
import { useQuery } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';

export function useAlertCount() {
  return useQuery({
    queryKey: ['alerts', 'count'],
    queryFn: () => apiClient.get<{ count: number }>('/alerts/count'),
    refetchInterval: 30_000,  // D-12: 30-second polling
    staleTime: 0,             // always consider stale for live count
  });
}
```

### Frontend: Resolve alert mutation with cache invalidation

```typescript
// Source: TanStack Query v5 useMutation pattern [CITED: tanstack.com/query/v5]
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient } from '@/lib/api-client';

export function useResolveAlert() {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: ({ alertId, notes }: { alertId: string; notes: string }) =>
      apiClient.post(`/alerts/${alertId}/resolve`, { notes }),
    onSuccess: async () => {
      // Invalidate in dependency order — count first (lightweight), then list
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['alerts', 'count'] }),
        queryClient.invalidateQueries({ queryKey: ['alerts'] }),
        queryClient.invalidateQueries({ queryKey: ['dashboard', 'stats'] }),
      ]);
    },
  });
}
```

### Frontend: Two-step wizard form with Zod v4

```typescript
// Source: react-hook-form + Zod v4 [VERIFIED: @hookform/resolvers 5.2.2 supports Zod v4]
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';

// Zod v4: use { error: '...' } not { message: '...' }
const step1Schema = z.object({
  firstName: z.string().min(1, { error: 'First name is required.' }),
  lastName:  z.string().min(1, { error: 'Last name is required.' }),
  dateOfBirth: z.string().min(1, { error: 'Date of birth is required.' }),
  mrn: z.string().min(1, { error: 'MRN is required.' }),
});
type Step1Data = z.infer<typeof step1Schema>;

// Inside component:
const { register, handleSubmit, formState: { errors } } = useForm<Step1Data>({
  resolver: zodResolver(step1Schema),
});
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Zod v3 `z.string().email()` | Zod v4 `z.email()` | Zod v4 release | All new schemas must use v4 API; v3 method forms are deprecated |
| Zod `{ message: "..." }` | Zod `{ error: "..." }` | Zod v4 | Error config key changed — subtle bug if not updated |
| @hookform/resolvers v4 (Zod 3 only) | @hookform/resolvers v5 (Zod 3 + 4) | v5.0.0 | v5 auto-detects Zod v3 vs v4 schemas via duck-typing |
| TanStack Query `cacheTime` | `gcTime` | v5 | Renamed in v5 migration — do not use `cacheTime` |
| React Router v6 `useParams` | TanStack Router `Route.useParams()` | v1.x | Type-safe route params; already adopted in Phase 1 |
| Tailwind `tailwind.config.ts` | Tailwind v4 `@theme` in app.css | v4.0 | No config file — already adopted in Phase 1 [VERIFIED: STATE.md] |

**Deprecated/outdated:**
- `cacheTime` in TanStack Query options: renamed `gcTime` in v5. Using `cacheTime` silently does nothing.
- Zod `z.string().email()`: deprecated in v4, will produce runtime warning. Use `z.email()`.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Calling `PathwayService.signalCareEventChanged()` after `repository.save()` without `@TransactionalEventListener` is acceptable for V1 pilot | Pattern 1, Pitfall 1 | If DB transaction rolls back after Temporal signal is sent, the workflow re-evaluates against stale/missing data. At pilot scale this is acceptable; in production it could cause spurious alerts. |
| A2 | A separate `onconavigator.hmac.key` is the right approach (not reusing the AES key) | Pattern 2, Pitfall 2 | If wrong, requires refactoring `HmacTokenService` — low risk to change early, high risk to change after data is indexed |
| A3 | Existing Phase 2 test data patients with NULL `mrn_hmac_token` do not need a backfill script for V1 pilot | Pitfall 3 | If wrong, MRN search returns no results for any patient enrolled before Phase 3 |
| A4 | Loading all patients in memory for name search (no pagination) is acceptable at pilot scale (<500 patients) | Architecture | If pilot grows faster than expected, name search could be slow. A server-side search endpoint can be added in V2 without breaking the API contract. |
| A5 | `Dialog` component from shadcn can be made non-dismissible by removing `onInteractOutside` default behavior | Pitfall 8 | Standard shadcn Dialog has `onInteractOutside` prop; setting it to `(e) => e.preventDefault()` achieves non-dismissible behavior without custom code |

---


## Open Questions (RESOLVED)

1. **HMAC key provisioning for existing patients** — RESOLVED
   - What we know: V8 migration adds the column; new patients will have HMAC tokens computed at create time
   - Resolution: For V1 pilot with no pre-existing production patients, NULL tokens are acceptable. Dev environment resets cleanly via `docker compose down -v && docker compose up`. No backfill script needed for pilot.

2. **AlertType -> severity display label mapping location** — RESOLVED
   - What we know: UI needs "OVERDUE", "MISSING", "OUT OF ORDER"; backend has `DELAYED_EVENT`, `MISSING_EVENT`, `OUT_OF_ORDER`
   - Resolution: AlertService performs the mapping server-side in `toAlertResponse()`. The `AlertResponse` DTO includes both `alertType` (raw enum name for frontend color logic) and `severityLabel` (display string). Frontend does not need to know backend enum names.

3. **Assigned navigator dropdown data source** — RESOLVED
   - What we know: Patient wizard Step 2 has an "assigned navigator" field. No user directory or Keycloak admin API is in scope for V1.
   - Resolution: Freetext input field (navigator name, optional). The backend field is `assignedNavigator` (String, nullable) rather than a UUID foreign key. Since there is no user directory in V1 pilot, the care coordinator types the navigator's name as free text. This satisfies DATA-01 without requiring infrastructure that does not exist.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 21 | Spring Boot 3.5 backend | ✓ | Java 21 (per pom.xml) | — |
| Maven wrapper | Build | ✓ | ./mvnw in project | — |
| Node.js + npm | Frontend build | ✓ | Confirmed (node_modules present) | — |
| PostgreSQL (local) | V8 Flyway migration | ✓ | 16.x via Docker Compose | — |
| Temporal Server (local) | PathwayService signals | ✓ | v1.28.x via Docker Compose | — |
| Keycloak (local) | JWT auth during dev/test | ✓ | v26.x via Docker Compose | — |
| `npx shadcn add` CLI | Installing new shadcn components | ✓ | Confirmed (components.json present, 11 components installed) | — |

**All dependencies available.** No blocking gaps for Phase 3 execution.

---

## Security Domain

`security_enforcement: true` — required.

### Applicable ASVS Categories (Level 1)

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes (indirect) | Keycloak JWT via Spring Security oauth2ResourceServer — already configured in SecurityConfig.java |
| V3 Session Management | yes | Stateless JWT — STATELESS session policy already set in SecurityConfig.java |
| V4 Access Control | yes | `@PreAuthorize("hasRole('CARE_COORDINATOR')")` on write endpoints; `@PreAuthorize("hasRole('NURSE_NAVIGATOR')")` on resolve endpoint |
| V5 Input Validation | yes | `@Valid` + Bean Validation on all request bodies; Zod v4 schemas on frontend |
| V6 Cryptography | yes | HMAC-SHA256 via `javax.crypto.Mac` (JDK); AES-GCM already in EncryptionConverter — do not hand-roll |
| V7 Error Handling | yes | Error responses must not leak PHI — Spring `@ControllerAdvice` for exception handling; log UUID only |

### Known Threat Patterns for Spring Boot REST + React

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| PHI in API error responses | Information Disclosure | `@ControllerAdvice` GlobalExceptionHandler — return generic error codes, not field values |
| PHI in application logs from new controllers | Information Disclosure | Never log request body fields containing name/DOB/MRN; log only patient UUID |
| Broken object-level authorization (BOLA) | Tampering | For `GET /api/patients/{id}`, verify the requesting user has access to that patient (in V1: any authenticated user with the right role — NURSE or COORDINATOR) |
| CSRF | Spoofing | CSRF disabled in SecurityConfig (SPA uses Bearer token) — correct for stateless JWT |
| JWT not validated on each request | Elevation of Privilege | Spring Security validates JWT signature against Keycloak JWKS on every request — no session caching |
| Unencrypted PHI in Alert `deviationDescription` | Information Disclosure | Alert entity does NOT store PHI in description fields — confirmed in Alert.java Javadoc ("Text fields contain non-PHI clinical guidance") |
| HMAC token revealing MRN structure via timing | Information Disclosure | Use constant-time comparison for HMAC token lookup — `MessageDigest.isEqual()` or Spring's `DigestUtils.securelyEquals()` if doing application-layer comparison; for DB lookup via index, timing is not a concern |

---

## Sources

### Primary (HIGH confidence)
- Codebase read — `Patient.java`, `Alert.java`, `CareEvent.java`, `SecurityConfig.java`, `PathwayService.java`, `EncryptionConverter.java`, `PatientRepository.java`, `AlertRepository.java`, `AuditService.java`, `EncryptionConfig.java` — all domain behavior verified
- `pom.xml` — confirmed backend dependencies
- `frontend/package.json` + `node_modules` — confirmed all frontend dependencies and exact versions
- `frontend/node_modules/@hookform/resolvers/zod/src/zod.ts` — confirmed Zod v4 duck-typing support in resolvers v5
- `db/migration/V1__create_base_schema.sql` through `V7__alert_dedup_index.sql` — confirmed schema state
- `03-CONTEXT.md`, `03-UI-SPEC.md` — user decisions and UI contract
- Context7 `/websites/zod_dev_v4` — Zod v4 breaking changes: `error` vs `message`, deprecated `z.string().email()` [HIGH]
- Context7 `/tanstack/query` — `useQuery`, `useMutation`, `invalidateQueries`, `refetchInterval` patterns [HIGH]
- Context7 `/tanstack/router` — `createFileRoute`, `Route.useParams()`, `$paramName` dynamic routes [HIGH]

### Secondary (MEDIUM confidence)
- `STATE.md` accumulated decisions — TanStack Router gen file behavior, Tailwind v4 config approach, @MockitoBean pattern for tests
- `02-CONTEXT.md` Phase 2 decisions — PathwayService API, auto-enrollment trigger, deactivation signal

### Tertiary (LOW confidence — flagged as ASSUMED in Assumptions Log)
- Acceptable scope of `@TransactionalEventListener` complexity for V1 pilot (A1)
- HMAC backfill strategy for pre-Phase-3 test data (A3)
- Assigned navigator dropdown data source design (A4/Open Question 3)

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all library versions verified from node_modules and pom.xml
- Architecture: HIGH — based on reading all existing domain code; patterns follow established project conventions
- HMAC approach: HIGH — JDK standard Mac API, well-understood cryptographic primitive
- Zod v4 API: HIGH — verified from installed node_modules source and Context7 docs
- Pitfalls: MEDIUM/HIGH — most derived from existing code patterns and STATE.md decisions; A1/A3 are flagged assumptions

**Research date:** 2026-04-30
**Valid until:** 2026-05-30 (stable libraries; Zod v4 and TanStack are active but breaking changes unlikely in 30 days)
