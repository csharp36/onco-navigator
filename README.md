# Onco-Navigator AI

A HIPAA-compliant care pathway monitoring system for medical oncology practices.

## The Problem

Medical oncology practices are coordination hubs where key care events — surgery, imaging, biopsy, pathology reports — happen at external facilities. The practice must track completion, detect delays, and ensure nothing falls through the cracks. Today this tracking is done manually, from memory, by nurse navigators juggling dozens of patients simultaneously.

Patients get lost. Referrals go unanswered. Biopsy results sit in a queue for days. A surgeon operates without the pathology report. These aren't rare edge cases — they're the daily reality of multi-facility cancer care coordination.

## The Solution

Onco-Navigator AI systematically watches every patient's care pathway and surfaces deviations before they become wasted visits, delayed treatments, or invisible gaps. It provides:

- **Pathway Monitoring** — Each patient is enrolled in a cancer-specific care pathway (breast, lung, colorectal). The system tracks expected steps, time windows, and prerequisites using durable Temporal.io workflows that survive restarts and run for weeks/months.

- **Deviation Detection** — Three types of alerts are generated automatically:
  - **Missing Event** — A required pathway step has no completed care event within the configured time window
  - **Delayed Event** — Elapsed time since the previous step exceeds the pathway's threshold
  - **Out of Order** — A care event is recorded before its prerequisite steps are completed

- **Alert Management** — Nurse navigators see all open alerts sorted by clinical severity (overdue first, then missing, then out-of-order). They can drill into patient pathway status, see exactly which step is affected, and resolve alerts with documentation notes.

- **Document Ingestion** — Clinical documents (pathology reports, radiology reports, operative notes, lab results, referral letters) can be dragged and dropped onto the dashboard or patient detail page. The system extracts text from PDFs, classifies the document type using Claude AI, matches it to a patient via HMAC MRN lookup or name+DOB fallback, and pre-fills a care event form with the extracted data. If Claude is unavailable, the system gracefully falls back to manual classification.

- **Human-in-the-Loop** — The AI monitors and suggests. Nurses and physicians decide and act. This is non-negotiable for clinical safety, regulatory simplicity, and trust-building.

## Architecture

| Layer | Technology | Purpose |
|-------|-----------|---------|
| Backend | Java 21 + Spring Boot 3.5 | REST API, business logic, security |
| Workflow Engine | Temporal.io (self-hosted) | Durable pathway monitoring, timers, crash recovery |
| AI | Spring AI 1.1.5 + Claude API | Document classification, alert text generation |
| Database | PostgreSQL 16 | Patient data, events, alerts (column-encrypted PHI) |
| Identity | Keycloak 26 | OIDC authentication, role-based access |
| Frontend | React 19 + TypeScript + Vite | Nurse dashboard, patient management, document upload |
| UI | shadcn/ui + Tailwind CSS v4 | Component library, responsive design |

### Security (HIPAA)

- AES-256-GCM column encryption for all PHI fields (names, DOB, MRN)
- HMAC-SHA256 index tokens for deterministic encrypted field search
- Hibernate Envers immutable audit trail on all ePHI entities
- Keycloak JWT authentication with method-level RBAC (`@PreAuthorize`)
- PHI redaction in all log output (UUID-only logging)
- Separate encryption and HMAC keys (key separation principle)
- Zero-PHI boundary on AI alert generation (no patient data sent to Claude)
- Resilience4j circuit breakers on all Claude API calls with graceful fallbacks

## Getting Started

### Prerequisites

- Java 21 (JDK)
- Node.js 20+
- Docker Desktop

### 1. Start Infrastructure

```bash
# Copy environment file
cp .env.example .env

# Start PostgreSQL, Temporal, Keycloak
docker compose up -d
```

Wait ~30 seconds for all services to initialize. Keycloak imports the realm automatically.

### 2. Start Backend

```bash
SPRING_PROFILES_ACTIVE=local JASYPT_ENCRYPTOR_PASSWORD=localdev_jasypt_master_key_change_me ./mvnw spring-boot:run
```

The backend starts on port **8081**. Flyway runs migrations automatically on startup.

### 3. Start Frontend

```bash
cd frontend
npm install
npm run dev
```

The frontend starts on port **5173** with a proxy to the backend API.

### 4. Log In

Navigate to http://localhost:5173. You'll be redirected to Keycloak for authentication.

| Username | Password | Role | Can Do |
|----------|----------|------|--------|
| coordinator1 | coordinator1 | Care Coordinator | Create patients, record care events, view pathways |
| nurse1 | nurse1 | Nurse Navigator | View/resolve alerts, view patient pathways |
| admin1 | admin1 | Administrator | All of the above |

## Development Ports

| Service | Port | URL |
|---------|------|-----|
| Frontend (Vite) | 5173 | http://localhost:5173 |
| Backend (Spring Boot) | 8081 | http://localhost:8081 |
| Keycloak Admin | 9090 | http://localhost:9090/admin (admin/see .env) |
| Temporal Web UI | 8080 | http://localhost:8080 |
| PostgreSQL | 5432 | `psql -h localhost -U postgres` |
| Temporal gRPC | 7233 | Used by Spring Boot internally |

## Project Structure

```
.
├── src/main/java/com/onconavigator/
│   ├── domain/           # JPA entities (Patient, Alert, CareEvent, PathwayTemplate)
│   ├── repository/       # Spring Data JPA repositories
│   ├── service/          # Business logic (PatientService, AlertService, PathwayStatusService)
│   ├── web/              # REST controllers + DTOs + GlobalExceptionHandler
│   ├── workflow/         # Temporal workflow implementations
│   ├��─ activity/         # Temporal activity implementations (deviation detection)
│   ├── ai/              # Claude AI integration
│   │   ├── config/      # ChatClient bean configuration
│   │   ├── model/       # Structured output records (DocumentClassification, AlertText)
│   │   ├── prompt/      # System prompt constants
│   │   └── service/     # Classification, alert generation, vision services
│   ├── security/         # SecurityConfig, HmacTokenService, EncryptionConverter
│   └── config/           # Spring configuration classes
├── src/main/resources/
│   ├── db/migration/     # Flyway SQL migrations (V1-V10)
│   └── application-local.yml  # Local dev configuration
├── frontend/
│   ├── src/
│   │   ├── routes/       # TanStack Router file-based routes
│   │   ├── features/     # Feature modules (patients/, alerts/, documents/)
│   │   ├── components/   # shadcn/ui components + layout
│   │   └── lib/          # API client, auth utilities
├── test-corpus/          # 16 synthetic de-identified clinical documents for evaluation
│   └── package.json
├── docker-compose.yml    # Local dev infrastructure
├── keycloak/             # Realm export for auto-import
├── docker/               # DB initialization scripts
└── docs/                 # Concept brief and feature specification
```

## Key Workflows

### Patient Enrollment

1. Care coordinator creates patient via two-step wizard (demographics + clinical info)
2. Backend computes HMAC token for MRN search, encrypts PHI, saves to PostgreSQL
3. Temporal pathway workflow starts automatically based on cancer type
4. Workflow begins monitoring for expected care events with configurable time windows

### Deviation Detection

1. Temporal workflow timers fire when expected care events are overdue
2. Activity evaluates patient's care events against pathway template
3. If deviation found: alert created with type, severity, description, and suggested action
4. Duplicate detection prevents redundant alerts for the same patient/step

### Document Ingestion

1. Staff drags a clinical PDF onto the dashboard or patient detail page
2. Backend extracts text (PDFBox), falls back to OCR (Tesseract) if PDF has no selectable text
3. Claude classifies the document: type, cancer type, patient name, MRN, event date, key findings
4. System matches document to a patient via HMAC MRN lookup or name+DOB fuzzy match
5. Pre-filled care event form opens with extracted data; staff reviews and saves
6. Document is stored as a blob linked to the care event for audit trail
7. If Claude API is unavailable, circuit breaker trips and staff classifies manually via dropdown

### Alert Resolution

1. Nurse navigator views alert queue sorted by severity
2. Clicks through to patient pathway visualization to understand context
3. Takes corrective action (calls external facility, reschedules, etc.)
4. Resolves alert with documentation notes (min 10 characters)

## Cancer Pathways

Three pathways are pre-configured as JSONB templates in the database:

- **Breast Cancer** — Referral, consultation, biopsy, pathology, imaging, surgery, chemo/radiation, follow-up
- **Lung Cancer** — Referral, imaging, biopsy, pathology, staging, treatment planning, surgery/chemo/radiation
- **Colorectal Cancer** — Referral, colonoscopy, biopsy, pathology, imaging, surgery, chemo, follow-up

Each step defines: expected event type, time window (days), prerequisites, and whether it's required.

## HIPAA Compliance

Onco-Navigator is designed for HIPAA compliance from day one — not retrofitted. The system implements the technical safeguards required by the HIPAA Security Rule (45 CFR Part 164, Subpart C) and addresses the Privacy Rule's minimum necessary standard through role-based access control.

### Technical Safeguards Implementation

| HIPAA Requirement | CFR Reference | Implementation |
|-------------------|---------------|----------------|
| **Encryption at rest** | 45 CFR 164.312(a)(2)(iv) | AES-256-GCM column-level encryption on all PHI fields (patient names, DOB, MRN) via JPA `@Convert` attribute converter. Each encryption operation uses a unique random IV. Database stores only ciphertext (`bytea` columns). |
| **Encryption in transit** | 45 CFR 164.312(e)(1) | TLS between all components. Local dev uses Vite proxy; production uses AWS ALB with ACM certificates for TLS 1.3 termination. |
| **Access control** | 45 CFR 164.312(a)(1) | Keycloak OIDC + Spring Security JWT validation. Three distinct roles enforced at method level via `@PreAuthorize`: Care Coordinator (data entry), Nurse Navigator (alert management), Administrator (full access). |
| **Audit controls** | 45 CFR 164.312(b) | Hibernate Envers `@Audited` on all ePHI entities creates immutable revision history. Dedicated `AuditLoggingFilter` records every API access with actor UUID, timestamp, endpoint, and success/failure. |
| **Integrity controls** | 45 CFR 164.312(c)(1) | Bean validation (`@Valid`) on all request DTOs. Database CHECK constraints on clinical fields. HMAC-SHA256 tokens for deterministic MRN integrity verification. |
| **Authentication** | 45 CFR 164.312(d) | Keycloak issues signed JWTs with PKCE (S256). Tokens validated against Keycloak's JWKS endpoint on every request. No session state on the server (stateless JWT). |
| **Automatic logoff** | 45 CFR 164.312(a)(2)(iii) | Keycloak session timeout + frontend token refresh with forced re-authentication on expiry. |

### PHI Protection Details

**What is encrypted:**
- `first_name_encrypted` (bytea) — Patient first name
- `last_name_encrypted` (bytea) — Patient last name
- `date_of_birth_encrypted` (bytea) — Date of birth
- `mrn_encrypted` (bytea) — Medical Record Number

**What is NOT encrypted (non-PHI):**
- Cancer type, cancer stage, diagnosis date — clinical data that doesn't identify the patient alone
- Alert descriptions, pathway step names — operational data
- Patient UUID — random identifier with no intrinsic meaning

**PHI in logs — prevented by design:**
- `GlobalExceptionHandler` logs only `ex.getClass().getSimpleName()`, never exception messages that could contain field values
- All service-layer log statements reference patients by UUID only
- Logback configuration blocks known PHI field name patterns

**Key separation:**
- AES-256 encryption key (`onconavigator.encryption.key`) — used for column encryption/decryption
- HMAC-SHA256 key (`onconavigator.hmac.key`) — used for deterministic MRN search tokens
- Keys are separate Base64-encoded 256-bit values; compromise of the HMAC key does not expose encrypted data

### Searchable Encryption Pattern

Standard AES-GCM uses a random IV per encryption, making equality searches impossible (encrypting "MRN-123" twice produces different ciphertext). Onco-Navigator solves this with a blind index pattern:

1. On patient creation, compute `HMAC-SHA256(mrn, hmac_key)` → deterministic 64-char hex token
2. Store token in `mrn_hmac_token` column (indexed, not reversible)
3. On MRN search, compute the same HMAC and query the token column

This satisfies both the encryption-at-rest requirement (MRN is AES-GCM encrypted) and the functional search requirement (HMAC enables exact-match lookup). Reference: [Blind Index pattern for searchable encryption](https://paragonie.com/blog/2017/05/building-searchable-encrypted-databases-with-php-and-sql).

### Audit Trail

Every data access is recorded in the `audit_log` table:

| Field | Purpose |
|-------|---------|
| `actor_id` | UUID of the authenticated user (from JWT `sub` claim) |
| `actor_role` | Role at time of access (CARE_COORDINATOR, NURSE_NAVIGATOR, ADMIN) |
| `http_method` | GET, POST, PATCH, DELETE |
| `request_path` | API endpoint accessed |
| `resource_type` | Entity type (PATIENT, ALERT, CARE_EVENT) |
| `resource_id` | UUID of the accessed resource |
| `success` | Whether the operation succeeded |
| `timestamp` | ISO timestamp of the access |
| `detail_hash` | SHA-256 hash of request details (integrity verification) |

Audit entries are written in a `REQUIRES_NEW` transaction — even if the business operation rolls back, the audit record persists. The audit table uses `BIGSERIAL` primary keys for guaranteed sequential ordering.

Additionally, Hibernate Envers maintains a complete revision history for all `@Audited` entities (patients, alerts, care events). Every field change is recorded with the revision timestamp and actor identity in `_AUD` shadow tables.

### Business Associate Agreements (BAAs)

A BAA is required with any third-party vendor that processes, stores, or transmits ePHI on behalf of a covered entity. Here's when each BAA is needed for Onco-Navigator:

| Vendor | BAA Required? | When | Why |
|--------|--------------|------|-----|
| **AWS** | Yes — before any ePHI enters the account | Day one of production deployment | RDS stores encrypted patient data, ECS runs the application, CloudWatch Logs may contain access patterns |
| **Anthropic (Claude API)** | Yes — if document classification sends PHI | Before enabling AI document classification in production | The classification service sends clinical document text (which contains patient identifiers) to Claude for analysis. The zero-PHI alert generation path does NOT require a BAA since it only sends de-identified deviation descriptions. |
| **Keycloak** | No (self-hosted) | N/A | Keycloak runs on your own infrastructure — no third-party data processing |
| **Temporal** | No (self-hosted) | N/A | Temporal Server runs on your own infrastructure with your own PostgreSQL persistence |

**Key distinction:** Onco-Navigator has two separate Claude API call paths with different BAA implications:

1. **Document Classification** (`DocumentClassificationService`) — Sends clinical document text to Claude, which **contains PHI** (patient names, MRN, dates, diagnoses). **Requires Anthropic BAA.** The classification feature is gated behind a feature flag (`onconavigator.ai.document-classification.enabled`) that defaults to `false` — it must be explicitly enabled after the BAA is in place.

2. **Alert Text Generation** (`AlertGenerationAiService`) — Sends only de-identified deviation context (pathway step name, event type, time window) to Claude. **No PHI is included** in these prompts by design (zero-PHI boundary). This path does not require a BAA, though having one provides defense-in-depth.

### Production Deployment (AWS)

For production HIPAA compliance on AWS:

- **BAA required** — AWS Business Associate Agreement must be executed before any ePHI enters the account
- **Anthropic BAA** — Required before enabling document classification (see BAA table above)
- **RDS encryption** — KMS Customer Managed Key (CMK) for storage-level encryption
- **Secrets Manager** — All keys and credentials injected via `spring.config.import: aws-secretsmanager:/`
- **CloudTrail** — Mandatory for infrastructure-level audit (all AWS API calls logged)
- **VPC** — All services in private subnets; no direct public internet access to database or Temporal
- **HIPAA-eligible services only** — RDS, ECS, Secrets Manager, KMS, CloudTrail, ALB, S3, CloudWatch Logs

### References

- [45 CFR Part 164 — Security and Privacy](https://www.ecfr.gov/current/title-45/subtitle-A/subchapter-C/part-164) — The HIPAA Security Rule and Privacy Rule
- [45 CFR 164.312 — Technical Safeguards](https://www.ecfr.gov/current/title-45/subtitle-A/subchapter-C/part-164/subpart-C/section-164.312) — Specific technical requirements referenced above
- [NIST SP 800-111 — Guide to Storage Encryption](https://csrc.nist.gov/pubs/sp/800/111/final) — AES-GCM for data at rest
- [NIST SP 800-66 Rev. 2 — Implementing the HIPAA Security Rule](https://csrc.nist.gov/pubs/sp/800/66/r2/final) — Implementation guidance for covered entities
- [HHS Guidance on Encryption](https://www.hhs.gov/hipaa/for-professionals/breach-notification/guidance/index.html) — Encryption as safe harbor for breach notification
- [Blind Index Pattern](https://paragonie.com/blog/2017/05/building-searchable-encrypted-databases-with-php-and-sql) — Searchable encryption without sacrificing confidentiality
- [AWS HIPAA Eligible Services](https://aws.amazon.com/compliance/hipaa-eligible-services-reference/) — Services covered under the AWS BAA

## Roadmap

- **Phase 1** (Complete) — HIPAA foundation: encryption, audit, RBAC, infrastructure
- **Phase 2** (Complete) — Pathway engine: Temporal workflows, deviation detection, pathway templates
- **Phase 3** (Complete) — Working application: REST API, dashboard, patient management, alert queue
- **Phase 4** (Complete) — AI document ingestion: PDF classification, patient matching, event pre-fill, Claude alert generation, circuit breakers

## License

Private — all rights reserved.
